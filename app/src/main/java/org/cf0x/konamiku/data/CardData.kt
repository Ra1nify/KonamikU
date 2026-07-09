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
    }

    private val fileName  = "cards.json"
    private val jsonFile: File get() = File(context.filesDir, fileName)
    private val mutex     = Mutex()

    private val jsonConfig = Json {
        prettyPrint        = true
        ignoreUnknownKeys  = true
    }
    suspend fun loadCards(): List<NfcCard> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (jsonFile.exists()) {
                runCatching {
                    jsonConfig.decodeFromString<List<NfcCard>>(jsonFile.readText())
                }.getOrElse { e ->
                    Log.w(TAG, "Failed to load cards.json: ${e.message}")
                    emptyList()
                }
            } else {
                emptyList<NfcCard>().also { saveCardsInternal(it) }
            }
        }
    }

    suspend fun saveCards(cards: List<NfcCard>) = mutex.withLock {
        saveCardsInternal(cards)
    }

    private suspend fun saveCardsInternal(cards: List<NfcCard>) =
        withContext(Dispatchers.IO) {
            runCatching {
                jsonFile.writeText(jsonConfig.encodeToString(cards))
            }.onFailure { e ->
                Log.w(TAG, "Failed to save cards.json: ${e.message}")
            }
        }
}