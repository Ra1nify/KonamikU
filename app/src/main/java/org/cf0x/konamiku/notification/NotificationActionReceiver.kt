package org.cf0x.konamiku.notification

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.NfcFCardEmulation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.data.JsonManager
import org.cf0x.konamiku.nfc.EmuCard
import org.cf0x.konamiku.nfc.toCompatIdm

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
                        updateNfcIdm(context, card.idm, next)
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
                        updateNfcIdm(context, nextCard.idm, mode)
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

    /**
     * Updates the NFC IDm for the active HCE-F service without needing an Activity.
     * setNfcid2ForService and registerSystemCodeForService are both Context-only calls.
     * enableService is intentionally omitted — it requires a foreground Activity and the
     * service is already registered in the manifest for 88B4, so routing persists.
     */
    private fun updateNfcIdm(context: Context, idm: String, mode: EmuMode) {
        runCatching {
            val adapter   = NfcAdapter.getDefaultAdapter(context) ?: return@runCatching
            val emulation = NfcFCardEmulation.getInstance(adapter) ?: return@runCatching
            val component = ComponentName(context, EmuCard::class.java)
            val activeIdm = when (mode) {
                EmuMode.NATIVE, EmuMode.COMPAT -> idm.uppercase().toCompatIdm()
                EmuMode.NORMAL                 -> idm.uppercase()
            }
            emulation.setNfcid2ForService(component, activeIdm)
            emulation.registerSystemCodeForService(component, "88B4")
        }
    }
}