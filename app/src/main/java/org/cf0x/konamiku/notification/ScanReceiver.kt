package org.cf0x.konamiku.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.*
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.loadActiveCard

/**
 * Dispatched when the active card is scanned by a reader.
 * Shows a toast + vibration and pulses the notification.
 */
class ScanReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "KonamikU-ScanRcvr"
        const val ACTION_SCAN = "org.cf0x.konamiku.ACTION_SCAN"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCAN) return
        val pending = goAsync()
        scope.launch {
            try {
                val active = context.loadActiveCard() ?: return@launch
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_card_scanned, active.card.name),
                        Toast.LENGTH_SHORT
                    ).show()

                    runCatching {
                        val vibrator = context.getSystemService(android.os.Vibrator::class.java)
                        vibrator?.vibrate(
                            android.os.VibrationEffect.createOneShot(
                                100, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }
                }
                LiveUpdateManager.pulse(context, active.card.name, active.mode, this)
            } finally {
                pending.finish()
            }
        }
    }
}
