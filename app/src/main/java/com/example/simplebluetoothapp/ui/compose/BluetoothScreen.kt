package com.example.simplebluetoothapp.ui.compose

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.icu.util.Calendar
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.simplebluetoothapp.model.DiscoveredBluetoothDevice
import java.sql.Time

@SuppressLint("DefaultLocale", "MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    modifier: Modifier = Modifier,
    isBluetoothSupported: Boolean,
    isBluetoothEnabled: Boolean,
    hasBluetoothPermissions: Boolean,
    areScanPermissionsGranted: Boolean,
    pairedDevices: List<DiscoveredBluetoothDevice>,
    discoveredDevices: SnapshotStateList<DiscoveredBluetoothDevice>,
    isScanning: Boolean,
    isConnecting: Boolean, // New state for connection attempt
    isConnectionEstablished: Boolean, // New state for successful connection
    connectedDeviceName: String?, // Name of the successfully connected device
    onEnableBluetoothClick: () -> Unit,
    onStartScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDeviceSelectedToConnect: (DiscoveredBluetoothDevice) -> Unit, // Renamed for clarity
    onDisconnectClick: () -> Unit, // New callback for disconnecting
    onSendDataToDeviceClick: (String) -> Unit
) {
    var selectedDevice by remember { mutableStateOf<DiscoveredBluetoothDevice?>(null) }
    var selectedTime by remember { mutableStateOf<TimeToSend?>(null) } // State for the text field
    var showTimePicker by remember { mutableStateOf(false) }
    var showBluetoothStatus by remember { mutableStateOf(false) }
    Log.d("BluetoothScreen", discoveredDevices.toString())

    // Initialize with current time or a default
    val calendar = Calendar.getInstance()
    val initialHour = calendar.get(Calendar.HOUR_OF_DAY) // 24-hour format for state
    val initialMinute = calendar.get(Calendar.MINUTE)
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        showTimePicker = false
                        // Format the selected time
                        val hour24 = timePickerState.hour
                        val minute = timePickerState.minute
                        selectedTime = TimeToSend(hour24, minute)
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            title = "Select Time"
        ) {
            TimePicker(
                state = timePickerState,
                layoutType = TimePickerLayoutType.Vertical // This enables the "drum roll" or vertical picker style
                // colors = TimePickerDefaults.colors(...) // Optional: for custom theming
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showBluetoothStatus) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showBluetoothStatus = false }
            ) {
                Text(
                    text = "Alarm Controller",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showBluetoothStatus = true }
            ) {
                Text(
                    text = "Alarm Controller",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (showBluetoothStatus || !isConnectionEstablished) {
            // --- Bluetooth Manage Section ---
            if (!isBluetoothSupported) {
                Text(
                    "Bluetooth is not supported on this device.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                return
            }
            StatusText("Bluetooth Support: Available")
            Spacer(modifier = Modifier.height(8.dp))

            val btStatusText =
                if (isBluetoothEnabled) "Bluetooth Status: Enabled" else "Bluetooth Status: Disabled"
            val btStatusColor =
                if (isBluetoothEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            StatusText(btStatusText, color = btStatusColor)
            Spacer(modifier = Modifier.height(16.dp))

            if (!isBluetoothEnabled && connectedDeviceName == null && !isConnecting) {
                val needsPerms = !hasBluetoothPermissions
                val buttonText =
                    if (needsPerms) "Grant Permissions & Enable Bluetooth" else "Enable Bluetooth"
                Button(onClick = onEnableBluetoothClick) { Text(buttonText) }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Connection Status and Controls ---
            if (isConnecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connecting to ${selectedDevice?.name ?: selectedDevice?.address ?: "device"}...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!isConnectionEstablished) {
                // --- Paired Devices Section ---
                if (isBluetoothEnabled && hasBluetoothPermissions && pairedDevices.isNotEmpty()) {
                    Text(
                        "Paired Devices:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .height(150.dp)
                            .fillMaxWidth()
                    ) { // Give it a fixed or weighted height
                        items(pairedDevices, key = { "paired-${it.address}" }) { device ->
                            DeviceListItem(
                                device = device,
                                isSelected = device.address == selectedDevice?.address,
                                onClick = {
                                    selectedDevice =
                                        if (selectedDevice?.address == device.address) null else device
                                    Log.d(
                                        "BluetoothScreen",
                                        "Paired device selected: ${device.address}, Name: ${device.name}"
                                    )
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (isBluetoothEnabled && hasBluetoothPermissions && pairedDevices.isEmpty() && !isScanning) {
                    // Only show "No paired devices" if not also actively scanning for new ones
                    Text(
                        "No paired devices found.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // --- Discovery Section ---
                if (isBluetoothEnabled && connectedDeviceName == null && !isConnecting) {
                    if (!areScanPermissionsGranted && !isScanning) {
                        Text(
                            "BLUETOOTH_SCAN permission is required to start discovery on Android 12+.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isScanning && connectedDeviceName == null && !isConnecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Discovering devices...", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (discoveredDevices.isEmpty() && !isScanning && connectedDeviceName == null && !isConnecting) {
                    Text(
                        "No devices found. Click 'Start Discovery'.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (discoveredDevices.isNotEmpty() && connectedDeviceName == null && !isConnecting) {
                    Text(
                        "Discovered Devices:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(discoveredDevices, key = { it.address }) { device ->
                            DeviceListItem(
                                device = device,
                                isSelected = device.address == selectedDevice?.address,
                                onClick = {
                                    selectedDevice = if (selectedDevice?.address == device.address) null else device
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = onStartScanClick,
                            enabled = !isScanning && areScanPermissionsGranted && selectedDevice == null,
                        ) { Text("Start Discovery") }
                        Button(
                            onClick = onStopScanClick,
                            enabled = isScanning && selectedDevice == null,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) { Text("Stop Discovery") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if ((pairedDevices.isNotEmpty() || discoveredDevices.isNotEmpty()) && connectedDeviceName == null && !isConnecting) {
                        Button(
                            onClick = {
                                showBluetoothStatus = false
                                selectedDevice?.let { onDeviceSelectedToConnect(it) }
                            },
                            enabled = selectedDevice != null && !isConnecting && !isConnectionEstablished
                        ) {
                            Text("Connect to Selected")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                StatusText(
                    "Connected to: $connectedDeviceName",
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDisconnectClick) {
                    Text("Disconnect from $connectedDeviceName")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            // --- Send Data Section (appears when connected) ---
            // Display the selected time (or manually entered text if you want to keep OutlinedTextField)
            Spacer(modifier = Modifier.height(256.dp))
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Selected Time: $selectedTime",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { showTimePicker = true }) {
                    Text("Change Time")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    selectedTime?.let { onSendDataToDeviceClick(it.toSendData("Alarm")) }
                },
                enabled = selectedTime != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Selected Time Data")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatusText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = color)
}

@Composable
fun DeviceListItem(
    device: DiscoveredBluetoothDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(
                vertical = 12.dp,
                horizontal = 16.dp
            ), // Added horizontal padding for better spacing
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) contentColor.copy(alpha = 0.7f) else Color.Gray // Slightly dim address when selected
            )
        }
    }
}

@SuppressLint("DefaultLocale")
data class TimeToSend(val hour: Int, val minute: Int) {
    override fun toString(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    fun toSendData(tag: String): String {
        val timeString = String.format("%02d%02d", hour, minute)
        return "$tag, $timeString;"
    }
}