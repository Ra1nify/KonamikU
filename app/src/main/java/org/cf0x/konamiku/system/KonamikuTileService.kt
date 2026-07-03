package org.cf0x.konamiku.system

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cf0x.konamiku.MainActivity
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.JsonManager
import org.cf0x.konamiku.nfc.EmuCard
import org.cf0x.konamiku.notification.LiveUpdateManager

class KonamikuTileService : TileService() {

    companion object {
        private const val TAG = "KonamikU-Tile"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onClick() {
        super.onClick()
        scope.launch {
            val dataStore   = AppDataStore(applicationContext)
            val jsonManager = JsonManager(applicationContext)

            // DataStore reads are memory-cached and instant on subsequent calls
            val activeId = withContext(Dispatchers.IO) { dataStore.activeCardId.first() }

            if (activeId != null) {
                // Already active → deactivate
                dataStore.saveActiveCardId(null)
                LiveUpdateManager.cancel(applicationContext)
                updateTileState()
                return@launch
            }

            // Card list read from file — run on IO
            val cards = withContext(Dispatchers.IO) { jsonManager.loadCards() }
            if (cards.isEmpty()) return@launch

            if (cards.size == 1) {
                activateAndLaunch(cards[0], dataStore)
            } else {
                showCardPicker(cards, dataStore)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun activateAndLaunch(card: org.cf0x.konamiku.data.NfcCard, dataStore: AppDataStore) {
        scope.launch {
            dataStore.saveActiveCardId(card.id)
            val mode = dataStore.emuMode.first()
            LiveUpdateManager.postActive(applicationContext, card.name, mode)
            updateTileState()
        }
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    putExtra(EmuCard.EXTRA_AUTO_ACTIVATE, card.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun showCardPicker(cards: List<org.cf0x.konamiku.data.NfcCard>, dataStore: AppDataStore) {
        val names = cards.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Card")
            .setItems(names) { _, which ->
                activateAndLaunch(cards[which], dataStore)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()  // TileService.showDialog is deprecated on API 35+ but still works
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        scope.launch {
            try {
                val dataStore = AppDataStore(applicationContext)
                val activeId = withContext(Dispatchers.IO) { dataStore.activeCardId.first() }
                val tile = qsTile ?: return@launch
                tile.state = if (activeId != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.updateTile()
            } catch (e: Exception) {
                Log.w(TAG, "updateTileState failed: ${e.message}")
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }
}
