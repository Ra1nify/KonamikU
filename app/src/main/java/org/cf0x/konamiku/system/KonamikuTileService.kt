package org.cf0x.konamiku.system

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.JsonManager
import org.cf0x.konamiku.notification.LiveUpdateManager

class KonamikuTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        scope.launch {
            val dataStore = AppDataStore(applicationContext)
            val activeId = dataStore.activeCardId.first()
            val tile = qsTile ?: return@launch
            tile.state = if (activeId != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val dataStore = AppDataStore(applicationContext)
            val jsonManager = JsonManager(applicationContext)
            val activeId = dataStore.activeCardId.first()

            if (activeId != null) {
                // Deactivate
                dataStore.saveActiveCardId(null)
                LiveUpdateManager.cancel(applicationContext)
            } else {
                // Activate first card
                val cards = jsonManager.loadCards()
                if (cards.isNotEmpty()) {
                    val firstCard = cards[0]
                    dataStore.saveActiveCardId(firstCard.id)
                    val mode = dataStore.emuMode.first()
                    LiveUpdateManager.postActive(applicationContext, firstCard.name, mode)
                    
                    // Note: We cannot easily enableService here because we need an Activity.
                    // However, if the user has previously activated it, the system might
                    // still route the NFC-F polling to us if EmuCard is the registered service.
                }
            }
            updateTileState()
        }
    }
}
