package org.cf0x.konamiku.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.cf0x.konamiku.R

/**
 * "Restart the app" prompt, shown whenever the NFC stack was restarted.
 *
 * Deliberately not dismissable by tapping outside: the NFC service has already
 * been killed at this point, so the app is in a state where it cannot reliably
 * talk to the framework until it is restarted. The only way out is the button.
 *
 * Shared by the status panel and the settings screen - both reach the same
 * situation through different flows and used to carry their own copy.
 */
@Composable
fun RestartAppDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title   = { Text(stringResource(R.string.restart_app_dialog_confirm)) },
        text    = { Text(stringResource(R.string.restart_app_dialog_msg)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.restart_app_dialog_confirm))
            }
        }
    )
}
