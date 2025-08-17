package com.example.simplebluetoothapp

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.simplebluetoothapp.ui.theme.SimpleBluetoothAppTheme
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.example.simplebluetoothapp.model.DiscoveredBluetoothDevice
import com.example.simplebluetoothapp.ui.compose.BluetoothScreen
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private const val TAG = "MainActivityBluetooth"
// Standard SPP UUID for Bluetooth Classic devices (Serial Port Profile)
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class MainActivity : ComponentActivity() {
    // Correct way: Initialize lazily
    private val bluetoothManager: BluetoothManager by lazy {
        applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        // Ensure bluetoothManager is accessed only after it's initialized
        // which lazy delegate handles for us.
        bluetoothManager.adapter
    }

    // --- Permission States (Android 12+) ---
    private var hasBluetoothConnectPermission by mutableStateOf(false)
    private var hasBluetoothScanPermission by mutableStateOf(false)
    // --- End Permission States ---

    // --- Bluetooth State
    private var isBluetoothCurrentlyEnabled by mutableStateOf(false) // Initialized in onCreate
    private var isDiscovering by mutableStateOf(false)
    val pairedDevices = mutableStateListOf<DiscoveredBluetoothDevice>()
    val discoveredDevices = mutableStateListOf<DiscoveredBluetoothDevice>()
    // --- End Bluetooth State

    // --- Connection State ---
    private var isConnecting by mutableStateOf(false)
    private var isConnectionEstablished by mutableStateOf(false)
    private var connectedDeviceName by mutableStateOf<String?>(null)
    private var connectedDeviceAddress by mutableStateOf<String?>(null)
    private var connectedThread: ConnectedThread? = null
    private var connectThread: ConnectThread? = null
    // --- End Connection State ---

    // ActivityResultLauncher for requesting Bluetooth enable
    private val requestEnableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                showToast("Bluetooth enabled")
                isBluetoothCurrentlyEnabled = true
                if (hasBluetoothConnectPermission) {
                    fetchPairedDevices()
                }
                if (areDiscoveryPermissionsGranted()) {
                    startDiscovery() // Start discovery if permissions are now good
                }
            } else {
                showToast("Bluetooth enabling cancelled by user.")
                isBluetoothCurrentlyEnabled = false
                if (pairedDevices.isNotEmpty()) {
                    pairedDevices.clear() // Clear if BT enabling was cancelled
                }
            }
        }

    // ActivityResultLauncher for requesting permissions
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasBluetoothConnectPermission = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: hasBluetoothConnectPermission
            hasBluetoothScanPermission = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: hasBluetoothScanPermission

            val permissionsSummary = "Permissions: CONNECT=${hasBluetoothConnectPermission}, SCAN=${hasBluetoothScanPermission}"
            Log.d(TAG, permissionsSummary)


            if (hasBluetoothScanPermission && hasBluetoothConnectPermission) { // Check both for full functionality
                showToast("Bluetooth permissions granted.")
                fetchPairedDevices()
            } else {
                if (pairedDevices.isNotEmpty()) {
                    pairedDevices.clear()
                }
                if (hasBluetoothConnectPermission && !hasBluetoothScanPermission) {
                    showToast("Connect permission granted. Scan permission needed for discovery.")
                }
                if (!hasBluetoothConnectPermission && hasBluetoothScanPermission) {
                    showToast("Scan permission granted. Connect permission needed for connecting.")
                }
            }

            updateInitialPermissionStates()
        }
    // --- End ActivityResultLaunchers ---

    @SuppressLint("MissingPermission")
    private fun fetchPairedDevices() {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "fetchPairedDevices: BluetoothAdapter is null")
            return
        }
        if (!hasBluetoothConnectPermission) { // BLUETOOTH_CONNECT is needed for bondedDevices on S+
            Log.w(TAG, "fetchPairedDevices: Missing BLUETOOTH_CONNECT permission.")
            // Optionally, prompt for permission or show a message
            // For now, we just won't fetch them.
            pairedDevices.clear()
            return
        }

        try {
            val currentlyPairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            pairedDevices.clear()
            currentlyPairedDevices?.forEach { device ->
                pairedDevices.add(
                    DiscoveredBluetoothDevice(
                        name = device.name ?: "Unknown Paired Device", // Name can be null
                        address = device.address
                    )
                )
            }
            Log.d(TAG, "Fetched paired devices: ${pairedDevices.joinToString { it.name ?: it.address }}")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException fetching paired devices. Did BLUETOOTH_CONNECT permission get revoked?", se)
            showToast("Permission issue fetching paired devices.")
            pairedDevices.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching paired devices", e)
            showToast("Error fetching paired devices.")
            pairedDevices.clear()
        }
    }

    // --- BroadcastReceiver for Bluetooth Discovery ---
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission") // Permissions checked before starting discovery
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received intent: ${intent.action}")
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    device?.let {
                        // BLUETOOTH_CONNECT is needed to get device name for unpaired devices on S+
                        // If not granted, name might be null.
                        val deviceName = try { it.name } catch (e: SecurityException) { null }
                        val deviceAddress = it.address
                        val discoveredDevice =
                            DiscoveredBluetoothDevice(name = deviceName, address = deviceAddress)
                        if (!discoveredDevices.any { d -> d.address == discoveredDevice.address }) {
                            discoveredDevices.add(discoveredDevice)
                            Log.d(TAG, "Device found: ${deviceName ?: "Unknown"} ($deviceAddress)")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    showToast("Discovery started...")
                    Log.d(TAG, "Discovery started.")
                    isDiscovering = true
                    discoveredDevices.clear()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    showToast("Discovery finished.")
                    Log.d(TAG, "Discovery finished.")
                    isDiscovering = false
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    // This intent is broadcast when the bonding (pairing) state of a remote device is changed.
                    // For example, if a pairing dialog is shown and the user accepts or cancels.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    // val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)

                    device?.let {
                        Log.d(TAG, "Bond state changed for device ${it.address}: ${bondStateToString(bondState)}")
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            // Device successfully bonded. If we were in the middle of a connection attempt
                            // that triggered bonding, the system might automatically continue the connection,
                            // or we might need to re-initiate the socket connection logic.
                            // For simplicity, we'll let the ongoing connection attempt (if any) proceed.
                            // If the connection was waiting for bonding, it might now succeed.
                            if (hasBluetoothConnectPermission && isBluetoothCurrentlyEnabled) {
                                fetchPairedDevices()
                            }
                            if (it.address == connectedDeviceAddress && isConnecting) {
                                showToast("${it.name ?: it.address} bonded. Attempting to finalize connection.")
                                // The original connectToDevice might still be running or might need a retry.
                                // For robust apps, you might need a more sophisticated state machine here.
                            }
                        } else if (bondState == BluetoothDevice.BOND_NONE) {
                            showToast("Bonding with ${it.name ?: it.address} failed or was cancelled.")
                            if (it.address == connectedDeviceAddress && isConnecting) {
                                // If bonding failed during a connection attempt, update UI
                                isConnecting = false
                                // Consider resetting connectedDeviceAddress here if necessary
                            }
                            if (hasBluetoothConnectPermission && isBluetoothCurrentlyEnabled) {
                                fetchPairedDevices()
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            Log.d(TAG, "Bluetooth turned OFF")
                            isBluetoothCurrentlyEnabled = false
                            if (pairedDevices.isNotEmpty()) {
                                pairedDevices.clear()
                            }
                            if (discoveredDevices.isNotEmpty()) {
                                discoveredDevices.clear()
                            }
                            isDiscovering = false // Stop discovery indication
                            isConnecting = false    // Reset connection attempt
                            connectedDeviceName = null
                            connectedDeviceAddress = null
                            disconnectFromDevice()
                            showToast("Bluetooth is OFF")
                        }
                        BluetoothAdapter.STATE_ON -> {
                            Log.d(TAG, "Bluetooth turned ON")
                            isBluetoothCurrentlyEnabled = true
                            // Fetch paired devices now that Bluetooth is ON
                            if (hasBluetoothConnectPermission) {
                                fetchPairedDevices()
                            } else {
                                Log.w(TAG, "Bluetooth is ON but BLUETOOTH_CONNECT permission is missing. Cannot fetch paired devices yet.")
                                if (pairedDevices.isNotEmpty()) {
                                    pairedDevices.clear() // Clear stale data if any
                                }
                            }
                            showToast("Bluetooth is ON")
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            Log.d(TAG, "Bluetooth TURNING ON")
                            // You might want to update UI to show "Turning on..."
                        }
                        // You can add more states if needed, e.g., BLE states if your app uses BLE
                        // BluetoothAdapter.STATE_BLE_ON -> Log.d(TAG, "Bluetooth BLE is ON")
                        // etc.
                        BluetoothAdapter.ERROR -> {
                            Log.e(TAG, "BluetoothAdapter.ACTION_STATE_CHANGED: Error state received.")
                        }
                        else -> {
                            Log.d(TAG, "BluetoothAdapter.ACTION_STATE_CHANGED: Unhandled state: $state")
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    showToast("Disconnected from device.")
                    connectedDeviceName = null
                    connectedDeviceAddress = null
                    isConnecting = false
                    isConnectionEstablished = false
                }
            }
        }
    }
    // --- End BroadcastReceiver ---

    private fun bondStateToString(bondState: Int): String {
        return when (bondState) {
            BluetoothDevice.BOND_NONE -> "BOND_NONE"
            BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
            BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
            else -> "ERROR/UNKNOWN ($bondState)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        updateInitialPermissionStates()
        isBluetoothCurrentlyEnabled = bluetoothAdapter?.isEnabled == true

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "This device doesn't support Bluetooth.", Toast.LENGTH_LONG).show()
        } else {
            if (isBluetoothCurrentlyEnabled && hasBluetoothConnectPermission) {
                fetchPairedDevices()
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) // Listen for pairing changes
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED) // Listen for BT on/off
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(discoveryReceiver, filter)

        Log.d(TAG, "Broadcast receiver registered")

        setContent {
            SimpleBluetoothAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothScreen(
                        modifier = Modifier.padding(innerPadding),
                        isBluetoothSupported = bluetoothAdapter != null,
                        isBluetoothEnabled = isBluetoothCurrentlyEnabled,
                        hasBluetoothPermissions = hasBluetoothScanPermission && hasBluetoothConnectPermission,
                        areScanPermissionsGranted = areDiscoveryPermissionsGranted(),
                        pairedDevices = pairedDevices,
                        discoveredDevices = discoveredDevices,
                        isScanning = isDiscovering,
                        isConnecting = isConnecting,
                        isConnectionEstablished = isConnectionEstablished,
                        connectedDeviceName = connectedDeviceName,
                        onEnableBluetoothClick = ::requestBluetoothPermissionsOrEnable,
                        onStartScanClick = ::requestPermissionsAndStartDiscovery,
                        onStopScanClick = ::stopDiscovery,
                        onDeviceSelectedToConnect = ::connectToDevice, // Connect this to the new function
                        onDisconnectClick = ::disconnectFromDevice,      // Connect this to the new function
                        onSendDataToDeviceClick = ::sendDataToDevice
                    )
                }
            }
        }
    }

    private fun updateInitialPermissionStates() {
        // For Android 12 (API 31) and higher
        hasBluetoothConnectPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        hasBluetoothScanPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun areDiscoveryPermissionsGranted(): Boolean {
        val hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        return hasScan
    }

    private fun requestBluetoothPermissionsOrEnable() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            checkAndEnableBluetooth()
        }
    }

    private fun requestPermissionsAndStartDiscovery() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        // BLUETOOTH_CONNECT is good to have if you intend to get names of unpaired devices or connect.
        // Request it if not already granted.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            // All necessary permissions are already granted
            checkAndEnableBluetoothThenDiscover()
        }
    }


    private fun checkAndEnableBluetooth() {
        if (bluetoothAdapter == null) {
            showToast("Bluetooth not supported on this device.")
            return
        }
        // Permission check specifically for S+ before attempting to enable
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            showToast("BLUETOOTH_CONNECT permission is required to enable Bluetooth.")
            // To avoid loops, do not directly call requestBluetoothPermissionsOrEnable() here.
            // The UI should prompt the user to click the enable/grant button again.
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // BLUETOOTH_CONNECT is required to launch this intent on S+ (checked above)
            try {
                requestEnableBluetooth.launch(enableBtIntent)
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException trying to launch ACTION_REQUEST_ENABLE. Missing BLUETOOTH_CONNECT?", se)
                showToast("Permission error enabling Bluetooth. Please grant BLUETOOTH_CONNECT.")
            }
        } else {
            showToast("Bluetooth is already enabled.")
            isBluetoothCurrentlyEnabled = true // Ensure state is synced
        }
    }

    private fun checkAndEnableBluetoothThenDiscover() {
        if (bluetoothAdapter == null) {
            showToast("Bluetooth not supported.")
            return
        }
        if (bluetoothAdapter?.isEnabled == true) {
            startDiscovery()
        } else {
            showToast("Bluetooth is not enabled. Please enable it first.")
            // Optionally, trigger the enable flow
            checkAndEnableBluetooth() // This will pop up the system dialog to enable BT
            // The result of that will then trigger startDiscovery if successful
        }
    }


    @SuppressLint("MissingPermission") // Permissions are checked by areDiscoveryPermissionsGranted
    private fun startDiscovery() {
        if (bluetoothAdapter == null) {
            showToast("Bluetooth not supported.")
            return
        }
        if (!areDiscoveryPermissionsGranted()) {
            showToast("Required permissions for discovery are missing.")
            // Optionally re-request permissions:
            // requestPermissionsAndStartDiscovery()
            return
        }

        if (isDiscovering) {
            showToast("Already discovering devices.")
            return
        }

        // Check if Bluetooth is enabled one last time before starting discovery
        if (bluetoothAdapter?.isEnabled == false) {
            showToast("Bluetooth is not enabled. Cannot start discovery.")
            checkAndEnableBluetooth() // Prompt to enable
            return
        }


        showToast("Starting discovery...")
        discoveredDevices.clear() // Clear previous results
        // isDiscovering state will be set by the BroadcastReceiver's ACTION_DISCOVERY_STARTED
        bluetoothAdapter?.startDiscovery()
        // Note: ACTION_DISCOVERY_STARTED might take a moment to fire.
        // You could optimistically set isDiscovering = true here if preferred,
        // but relying on the broadcast is more accurate.
    }

    @SuppressLint("MissingPermission")
    private fun stopDiscovery() {
        if (bluetoothAdapter == null) return

        val hasPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            showToast("BLUETOOTH_SCAN permission needed to stop discovery.")
            return
        }

        if (isDiscovering) { // Use our app's state
            bluetoothAdapter?.cancelDiscovery()
            showToast("Stopping discovery...")
            // isDiscovering will be set to false by BroadcastReceiver
        } else {
            // If our state says not discovering, but adapter *is* (e.g. state mismatch)
            // This is a fallback, ideally our 'isDiscovering' state is always accurate.
            if(bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
                showToast("Stopping discovery (adapter state).")
            } else {
                showToast("Not currently discovering.")
            }
        }
    }

    // --- Bluetooth Connection Logic ---
    private fun connectToDevice(device: DiscoveredBluetoothDevice) {
        if (!hasBluetoothConnectPermission) {
            showToast("BLUETOOTH_CONNECT permission required to connect.")
            return
        }
        // Set UI state to indicate connection attempt is starting
        isConnecting = true
        isConnectionEstablished = false
        connectedDeviceName = device.name ?: device.address // Optimistic
        connectedDeviceAddress = device.address

        showToast("Connecting to ${device.name ?: device.address}...")

        // Close any previous connection attempts or active connections
        connectThread?.cancel() // Cancels the thread and closes its socket
        connectThread = null
        connectedThread?.cancel() // Ensure any old connected thread is also stopped
        connectedThread = null


        val btDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        if (btDevice == null) {
            showToast("Failed to get BluetoothDevice.")
            isConnecting = false
            isConnectionEstablished = false
            connectedDeviceName = null
            connectedDeviceAddress = null
            return
        }
        connectThread = ConnectThread(btDevice)
        connectThread?.start()
    }

    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        }

        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                try {
                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    socket.connect()

                    // The connection attempt succeeded.
                    // Update UI/state on the Main thread
                    runOnUiThread { // Or use lifecycleScope.launch(Dispatchers.Main)
                        isConnecting = false
                        isConnectionEstablished = true
                        // connectedDeviceName should already be set when connectToDevice was called
                        showToast("Connected to ${connectedDeviceName ?: "device"}")
                        // Any other UI updates indicating connection success
                    }

                    // The connection attempt succeeded. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(socket)
                } catch (connectException: IOException) {
                    // Unable to connect; close the socket and return.
                    Log.e(TAG, "ConnectThread: Could not connect to the client socket", connectException)
                    try {
                        socket.close()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "ConnectThread: Could not close the client socket", closeException)
                    }
                    // Update UI/state on the Main thread about the failure
                    runOnUiThread { // Or use lifecycleScope.launch(Dispatchers.Main)
                        isConnecting = false
                        isConnectionEstablished = false
                        connectedDeviceName = null // Clear the name as connection failed
                        connectedDeviceAddress = null
                        showToast("Connection Failed: ${connectException.localizedMessage}")
                        // Any other UI updates indicating connection failure
                    }
                    return@run // Exit the run method as connection failed
                } catch (se: SecurityException) {
                    Log.e(TAG, "ConnectThread: SecurityException during connect", se)
                    runOnUiThread {
                        isConnecting = false
                        isConnectionEstablished = false
                        connectedDeviceName = null
                        connectedDeviceAddress = null
                        showToast("Connection Failed: Permission issue.")
                    }
                    // It's good to close the socket here too if it was created
                    try { mmSocket?.close() } catch (e: IOException) { /* Log */ }
                    return@run
                }
            }
            // If mmSocket was null in the first place (shouldn't happen if device is valid)
            if (mmSocket == null) {
                runOnUiThread {
                    isConnecting = false
                    isConnectionEstablished = false
                    connectedDeviceName = null
                    connectedDeviceAddress = null
                    showToast("Connection Failed: Invalid device or socket.")
                }
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(
        private val mmSocket: BluetoothSocket,
    ) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
                return
            }
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    private fun disconnectFromDevice() {
        if (connectThread != null || connectedThread != null) {
            connectedThread?.cancel()
            connectThread?.cancel()
            connectThread = null
            connectedThread = null
            connectedDeviceName = null
            connectedDeviceAddress = null
            isConnecting = false // Ensure this is reset
            isConnectionEstablished = false
            showToast("Disconnected from ${connectedDeviceName ?: "device"}")
        } else {
            showToast("Not currently connected to a device.")
        }
    }

    private fun sendDataToDevice(dataToSend: String) {
        if (connectedThread == null) {
            showToast("Cannot send message. Not connected to a device.")
            return
        }
        connectedThread?.write(dataToSend.toByteArray())
        showToast("Message sent: $dataToSend")
    }
    
    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh states as they might change while the app is paused
        updateInitialPermissionStates()
        isBluetoothCurrentlyEnabled = bluetoothAdapter?.isEnabled == true
        if (isBluetoothCurrentlyEnabled && hasBluetoothConnectPermission) {
            fetchPairedDevices()
        } else {
            // If BT is off or no permission, clear the list to reflect current state
            if (pairedDevices.isNotEmpty()) {
                pairedDevices.clear()
            }
        }
//        isConnecting = false
//        isConnectionEstablished = false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister broadcast receiver to avoid memory leaks
        try {
            unregisterReceiver(discoveryReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was probably not registered or already unregistered
            e.printStackTrace()
        }
        // Ensure discovery is cancelled if activity is destroyed
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        if (bluetoothAdapter?.isDiscovering == true) {
            stopDiscovery()
        }
    }
}

