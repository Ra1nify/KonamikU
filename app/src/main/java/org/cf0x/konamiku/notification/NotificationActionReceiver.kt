package org.cf0x.konamiku.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.data.JsonManager

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStore   = AppDataStore(context)
                val jsonManager = JsonManager(context)
                when (intent.action) {
                    LiveUpdateManager.ACTION_TOGGLE_ACTIVATE -> {
                        dataStore.saveActiveCardId(null)
                        LiveUpdateManager.cancel(context)
                    }
                    LiveUpdateManager.ACTION_TOGGLE_MODE -> {
                        val current = dataStore.emuMode.first()
                        val next = when (current) {
                            EmuMode.NORMAL -> EmuMode.COMPAT
                            EmuMode.COMPAT -> EmuMode.NATIVE
                            EmuMode.NATIVE -> EmuMode.NORMAL
                        }
                        dataStore.saveEmuMode(next)
                        val activeId = dataStore.activeCardId.first() ?: return@launch
                        val card     = jsonManager.loadCards().find { it.id == activeId }
                            ?: return@launch
                        LiveUpdateManager.postActive(context, card.name, next)
                    }
                    LiveUpdateManager.ACTION_DISMISSED -> {
                        dataStore.saveActiveCardId(null)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}