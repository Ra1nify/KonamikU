package org.cf0x.konamiku.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.cf0x.konamiku.R

/**
 * Last-resort prompt: the NFC service was restarted and this process still
 * cannot reach it after the binder caches were dropped.
 *
 * This should be rare now. The caches are cleared after a restart, which lets
 * the framework rebind in-process, and both call sites probe the proxy before
 * getting here. Seeing this dialog means that path did not work on the device.
 *
 * Deliberately not dismissable by tapping outside: the app cannot talk to the
 * NFC framework in this state, so leaving the dialog would strand the user on a
 * screen where nothing works. The button is the only way out.
 */
@Composable
fun RestartAppDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title   = { Text(stringResource(R.string.restart_app_dialog_title)) },
        text    = { Text(stringResource(R.string.restart_app_dialog_msg)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.restart_app_dialog_confirm))
            }
        }
    )
}
