package org.cf0x.konamiku.nfc

import org.cf0x.konamiku.data.EmuMode

class FelicaCard(
    val activeIdm: String,   // NFCID2 として登録される IDm
    val realIdm:   String,   // block 0x82 に書き込む実 IDm
    val emuMode:   EmuMode
) {
    val activeIdmBytes: ByteArray = activeIdm.uppercase()
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    val realIdmBytes: ByteArray = realIdm.uppercase()
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    val pmmBytes: ByteArray = byteArrayOf(
        0x00, 0xF1.toByte(), 0x00, 0x00, 0x00, 0x01, 0x43, 0x00
    )

    fun readBlock(blockNumber: Int): ByteArray =
        when (blockNumber) {
            0x82 -> ByteArray(16).also { realIdmBytes.copyInto(it, 0, 0, 8) }
            else -> ByteArray(16) // unimplemented blocks → zeros
        }
}

/** 00 を 02FE に置換して compat/native 用 IDm を派生する */
fun String.toCompatIdm(): String = "02FE" + this.uppercase().substring(4)