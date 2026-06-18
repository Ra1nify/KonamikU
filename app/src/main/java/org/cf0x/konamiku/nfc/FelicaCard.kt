package org.cf0x.konamiku.nfc

import android.content.Context
import org.cf0x.konamiku.data.EmuMode

/**
 * FeliCa card data model backed by a block template (felica_template.json).
 *
 * The template defines all standard FeliCa system blocks (0x00–0x92).
 * Block 0x82 (IDm mirror) is overwritten at runtime with the user's real IDm
 * so the reader sees the correct card identity.
 */
class FelicaCard(
    context: Context,
    val activeIdm: String,
    val realIdm: String,
    val emuMode: EmuMode
) {
    val activeIdmBytes: ByteArray = activeIdm.uppercase()
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    val realIdmBytes: ByteArray = realIdm.uppercase()
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // --- Block template ---

    /** All blocks from felica_template.json, keyed by block number (0x00–0x92). */
    private val blocks: Map<Int, ByteArray>

    init {
        val rawMap = mutableMapOf<Int, ByteArray>()

        runCatching {
            val json = context.assets.open("felica_template.json")
                .bufferedReader().use { it.readText() }

            // Simple parser — the JSON is flat {"HH": "HH HH ...", ...}
            val hexKey = Regex("\"([0-9A-Fa-f]{2})\"\\s*:\\s*\"([^\"]*)\"")
            hexKey.findAll(json).forEach { match ->
                val blockNum = match.groupValues[1].toInt(16)
                val hexStr   = match.groupValues[2].replace(" ", "")
                val data     = hexStr.chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
                rawMap[blockNum] = ByteArray(16).also { data.copyInto(it, 0, 0, minOf(data.size, 16)) }
            }
        }.onFailure {
            android.util.Log.e("KonamikU", "Failed to load felica_template.json: ${it.message}")
        }

        // Override block 0x82 with the real IDm (mirrored in first 8 bytes)
        rawMap[0x82] = ByteArray(16).also { realIdmBytes.copyInto(it, 0, 0, 8) }

        blocks = rawMap
    }

    // --- Public API ---

    /** Returns 16 bytes for the block, or all-zeroes if unknown. */
    fun readBlock(blockNumber: Int): ByteArray =
        blocks[blockNumber] ?: ByteArray(16)
}

/** Converts an IDm to "compat" format: "02FE" prefix + last 12 hex digits. */
fun String.toCompatIdm(): String = "02FE" + this.uppercase().substring(4)
