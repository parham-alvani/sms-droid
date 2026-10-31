package me.parham1995.sms.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Asks before senders are blocked, since what they sent goes with the next backup. */
@Composable
fun BlockDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                "Their messages arrive without a notification, one-time codes too, and the next backup keeps " +
                    "them under blocked/ and deletes them from the phone. A call from a blocked number is " +
                    "rejected without ringing when call screening is on. Settings lists what is blocked.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Block") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
