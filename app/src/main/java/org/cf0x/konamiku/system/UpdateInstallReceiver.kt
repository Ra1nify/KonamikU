package org.cf0x.konamiku.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Receives the [PackageInstaller] commit result.
 *
 * A successful install normally kills and restarts our process before this
 * broadcast is handled, so the success path is mostly a safety net; the
 * failure paths are what actually matter to the user.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The installer wants confirmation from the user. On API 26+
                // with REQUEST_INSTALL_PACKAGES granted this should not happen
                // for a full install, but OEM builds do raise it.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                Log.i(UpdateInstaller.TAG, "Install needs user action")
                confirm?.let {
                    runCatching {
                        context.startActivity(
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { e ->
                        Log.e(UpdateInstaller.TAG, "Cannot launch confirm intent: ${e.message}")
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(UpdateInstaller.TAG, "Install succeeded")
            }

            else -> {
                Log.e(UpdateInstaller.TAG, "Install failed: status=$status msg=$message")
                UpdateDownloadService.notifyInstallFailure(context, message)
            }
        }
    }
}
