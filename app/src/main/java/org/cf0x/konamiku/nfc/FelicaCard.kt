package org.cf0x.konamiku.nfc

import org.cf0x.konamiku.data.EmuMode

class FelicaCard(
    val activeIdm: String,
    val realIdm:   String,
    val emuMode:   EmuMode
) {
    val activeIdmBytes: ByteArray = activeIdm.uppercase()
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    val realIdmBytes: ByteArray = realIdm.uppercase()
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun readBlock(blockNumber: Int): ByteArray =
        when (blockNumber) {
            0x82 -> ByteArray(16).also { realIdmBytes.copyInto(it, 0, 0, 8) }
            else -> ByteArray(16)
        }
}

fun String.toCompatIdm(): String = "02FE" + this.uppercase().substring(4)