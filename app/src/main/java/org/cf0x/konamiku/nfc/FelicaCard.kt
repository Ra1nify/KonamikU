package org.cf0x.konamiku.nfc

class FelicaCard(idm: String) {

    val idmBytes: ByteArray = idm.chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()

    val pmmBytes: ByteArray = byteArrayOf(
        0x00, 0xF1.toByte(), 0x00, 0x00, 0x00, 0x01, 0x43, 0x00
    )

    fun readBlock(blockNumber: Int): ByteArray = ByteArray(16)
}