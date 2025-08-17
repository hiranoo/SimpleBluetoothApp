package com.example.simplebluetoothapp.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String = "Select Time",
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit, // Changed to pass the state directly
) {
    // This state would typically be hoisted to where TimePickerDialog is called
    // For this generic dialog, we assume the caller manages the TimePickerState
    // and passes the TimePicker composable configured with that state via the `content` lambda.
    // However, the prompt implies the content IS the TimePicker, so let's adjust.

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Allow custom width
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min) // Snap to content width
                .height(IntrinsicSize.Min) // Snap to content height
                .background(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = containerColor
                ),
            color = containerColor
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = title,
                    style = MaterialTheme.typography.labelMedium
                )
                // The content lambda will be the TimePicker composable
                // This means the TimePickerState needs to be created and remembered
                // in the calling Composable (BluetoothScreen in this case)
                // and the TimePicker configured with it should be passed here.
                // The original implementation was trying to have content accept TimePickerState,
                // which is less flexible if content isn't *just* the TimePicker.
                // Let's stick to the convention where 'content' is the main body.
                // The TimePicker itself will be the content.

                // The TimePicker composable is passed directly as the content
                content()


                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End // Align buttons to the end
                ) {
                    dismissButton?.invoke() // Render dismiss button if provided
                    Spacer(modifier = Modifier.width(8.dp)) // Spacing between buttons
                    confirmButton()
                }
            }
        }
    }
}
