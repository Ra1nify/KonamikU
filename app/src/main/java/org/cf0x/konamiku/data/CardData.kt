package org.cf0x.konamiku.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class NfcCard(
    val id: String,
    val name: String,
    val idm: String
)

class JsonManager(private val context: Context) {

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
                }.getOrElse {
                    it.printStackTrace()
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
            }.onFailure { it.printStackTrace() }
        }
}