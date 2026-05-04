package org.cf0x.konamiku.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.JsonManager

class ScanReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCAN = "org.cf0x.konamiku.ACTION_SCAN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCAN) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStore   = AppDataStore(context)
                val jsonManager = JsonManager(context)
                val activeId    = dataStore.activeCardId.first() ?: return@launch
                val compatMode  = dataStore.compatMode.first()
                val card        = jsonManager.loadCards().find { it.id == activeId }
                                  ?: return@launch
                val modeLabel   = context.getString(
                    if (compatMode) R.string.mode_compat else R.string.mode_normal
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_card_activated, card.name, modeLabel),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                LiveUpdateManager.pulse(context, card.name, compatMode, this)
            } finally {
                pending.finish()
            }
        }
    }
}