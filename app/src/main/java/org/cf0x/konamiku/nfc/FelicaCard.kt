package org.cf0x.konamiku.nfc

import android.content.Context
import android.util.Log
import org.cf0x.konamiku.data.EmuMode
import org.json.JSONObject

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
    companion object {
        private const val TAG = "KonamikU-FelicaCard"
        private const val BLOCK_SIZE = 16
        private const val HEX_DIGITS = "0123456789abcdefABCDEF"
    }

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

            // Parsed with org.json rather than a regex over the raw text.
            //
            // The regex matched any two-hex-digit key followed by a string, so
            // a future metadata field would have been absorbed as a block
            // without a word, and an odd-length value like "ABC" decoded into
            // the wrong bytes instead of failing. Both are now explicit.
            val root = JSONObject(json)
            for (key in root.keys()) {
                val blockNumber = if (key.length == 2) key.toIntOrNull(16) else null
                if (blockNumber == null) {
                    Log.w(TAG, "Ignoring non-block key in felica_template.json: '$key'")
                    continue
                }

                val hex = root.optString(key).replace(" ", "")
                if (hex.length % 2 != 0 || hex.any { it !in HEX_DIGITS }) {
                    Log.e(TAG, "Block $key has malformed hex '$hex'; skipping")
                    continue
                }

                val data = ByteArray(hex.length / 2) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                if (data.size > BLOCK_SIZE) {
                    Log.e(TAG, "Block $key is ${data.size} bytes, truncating to $BLOCK_SIZE")
                }
                rawMap[blockNumber] = ByteArray(BLOCK_SIZE).also {
                    data.copyInto(it, 0, 0, minOf(data.size, BLOCK_SIZE))
                }
            }
            Log.i(TAG, "Loaded ${rawMap.size} blocks from felica_template.json")
        }.onFailure {
            Log.e(TAG, "Failed to load felica_template.json: ${it.message}")
        }

        // Override block 0x82 with the real IDm (mirrored in first 8 bytes)
        rawMap[0x82] = ByteArray(BLOCK_SIZE).also { realIdmBytes.copyInto(it, 0, 0, 8) }

        blocks = rawMap
    }

    // --- Public API ---

    /** Returns 16 bytes for the block, or all-zeroes if unknown. */
    fun readBlock(blockNumber: Int): ByteArray =
        blocks[blockNumber] ?: ByteArray(BLOCK_SIZE)
}

/**
 * Converts an IDm to "compat" format: "02FE" prefix + last 12 hex digits.
 *
 * A well-formed IDm is 16 hex characters. Shorter input is padded rather than
 * throwing: substring(4) used to raise StringIndexOutOfBoundsException, and
 * this is called from the card-activation path where an exception surfaced as
 * a silent failure to activate.
 */
fun String.toCompatIdm(): String {
    val upper = uppercase()
    val tail = if (upper.length > 4) upper.substring(4) else upper
    return "02FE" + tail.padStart(12, '0').takeLast(12)
}

/** FeliCa system code registered for HCE-F routing. */
const val SYSTEM_CODE_FELICA = "88B4"

/** Resolves the active IDm bytes to register based on emulation mode. */
fun resolveActiveIdm(idm: String, mode: EmuMode): String {
    val real = idm.uppercase()
    return when (mode) {
        EmuMode.NORMAL -> real
        EmuMode.COMPAT, EmuMode.NATIVE -> real.toCompatIdm()
    }
}
