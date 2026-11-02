package org.cf0x.konamiku.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

val CardListRefreshEvent = MutableStateFlow(0L)

data class ActiveCard(val card: NfcCard, val mode: EmuMode)

@Serializable
data class NfcCard(
    val id: String,
    val name: String,
    val idm: String,
    val emuMode: EmuMode = EmuMode.NORMAL
)

/** Loads the currently active card with its mode, or null if none is selected. */
suspend fun Context.loadActiveCard(): ActiveCard? {
    val dataStore = AppDataStore(this)
    val jsonManager = JsonManager(this)
    val activeId = dataStore.activeCardId.first() ?: return null
    val mode = dataStore.emuMode.first()
    val card = jsonManager.loadCards().find { it.id == activeId } ?: return null
    return ActiveCard(card, mode)
}

class JsonManager(private val context: Context) {

    companion object {
        private const val TAG = "KonamikU-JsonMgr"

        /**
         * Process-wide lock for cards.json.
         *
         * This lives on the companion, not the instance: JsonManager is
         * constructed in five places (MainScreen, SettingScreen, TileService,
         * NotificationActionReceiver, loadActiveCard), so a per-instance mutex
         * would let any two of them read and write the file concurrently:
         * a half-written read decodes to an empty list, which the next
         * loadCards() would then persist, wiping the user's cards.
         */
        private val FILE_LOCK = Mutex()
    }

    private val fileName  = "cards.json"
    private val jsonFile: File get() = File(context.filesDir, fileName)

    private val jsonConfig = Json {
        prettyPrint        = true
        ignoreUnknownKeys  = true
    }
    suspend fun loadCards(): List<NfcCard> = FILE_LOCK.withLock {
        withContext(Dispatchers.IO) {
            if (jsonFile.exists()) {
                runCatching {
                    jsonConfig.decodeFromString<List<NfcCard>>(jsonFile.readText())
                }.getOrElse { e ->
                    // Do NOT overwrite the file here. A parse failure usually
                    // means the content is damaged, and writing an empty list
                    // back would turn a recoverable read error into permanent
                    // data loss. Return empty in memory only.
                    Log.e(TAG, "Failed to parse cards.json, keeping file intact: ${e.message}")
                    emptyList()
                }
            } else {
                emptyList<NfcCard>().also { saveCardsInternal(it) }
            }
        }
    }

    suspend fun saveCards(cards: List<NfcCard>) = FILE_LOCK.withLock {
        saveCardsInternal(cards)
    }

    private suspend fun saveCardsInternal(cards: List<NfcCard>) =
        withContext(Dispatchers.IO) {
            runCatching {
                // Write to a sibling file and rename, so a concurrent reader
                // (or a process death mid-write) never sees a partial file.
                val tmp = File(jsonFile.parentFile, "$fileName.tmp")
                tmp.writeText(jsonConfig.encodeToString(cards))
                if (!tmp.renameTo(jsonFile)) {
                    // rename can fail on some file systems; fall back to a
                    // direct write so the data is not silently dropped.
                    jsonFile.writeText(tmp.readText())
                    tmp.delete()
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to save cards.json: ${e.message}")
            }
        }
}
