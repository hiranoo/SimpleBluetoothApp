package com.example.simplebluetoothapp.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EnableBluetoothScreen(
    modifier: Modifier = Modifier,
    isBluetoothSupported: Boolean,
    isBluetoothEnabled: Boolean,
    hasBluetoothConnectPermission: Boolean, // Added for more clarity
    onEnableBluetoothClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isBluetoothSupported) {
            Text(
                text = "Bluetooth is not supported on this device.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = "Bluetooth Support: Available",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            val statusText = if (isBluetoothEnabled) "Bluetooth Status: Enabled" else "Bluetooth Status: Disabled"
            val statusColor = if (isBluetoothEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!isBluetoothEnabled) {
                // Determine the button text and action based on permission status for Android 12+
                val needsConnectPermission = !hasBluetoothConnectPermission

                if (needsConnectPermission) {
                    Text(
                        text = "This app needs permission to connect to Bluetooth devices to enable Bluetooth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(onClick = onEnableBluetoothClick) {
                        Text("Grant Permission & Enable Bluetooth")
                    }
                } else {
                    // Either permission is granted on S+ or it's a pre-S device where permission is handled differently for enabling
                    Button(onClick = onEnableBluetoothClick) {
                        Text("Enable Bluetooth")
                    }
                }
            } else {
                Text(
                    text = "Bluetooth is active.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}