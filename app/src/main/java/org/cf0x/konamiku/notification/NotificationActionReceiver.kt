package org.cf0x.konamiku.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.data.JsonManager
import org.cf0x.konamiku.data.loadActiveCard
import org.cf0x.konamiku.nfc.updateHceRegistration

/**
 * Handles notification action buttons while the emulation service is active.
 * Uses a [SupervisorJob]-scoped coroutine so that individual action failures
 * don't cancel sibling actions.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "KonamikU-NotifAct"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
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
                        val active = context.loadActiveCard() ?: return@launch
                        LiveUpdateManager.postActive(context, active.card.name, next)
                        updateHceRegistration(context, active.card.idm, next)
                    }
                    LiveUpdateManager.ACTION_NEXT_CARD -> {
                        val cards = jsonManager.loadCards()
                        if (cards.isEmpty()) return@launch
                        val activeId = dataStore.activeCardId.first()
                        val currentIndex = cards.indexOfFirst { it.id == activeId }
                        val nextIndex = (currentIndex + 1) % cards.size
                        val nextCard = cards[nextIndex]

                        dataStore.saveActiveCardId(nextCard.id)
                        val mode = dataStore.emuMode.first()
                        LiveUpdateManager.postActive(context, nextCard.name, mode)
                        updateHceRegistration(context, nextCard.idm, mode)
                    }
                    LiveUpdateManager.ACTION_DISMISSED -> {
                        dataStore.saveActiveCardId(null)
                    }
                    else -> {} // unknown action, ignore
                }
            } finally {
                pending.finish()
            }
        }
    }
}
