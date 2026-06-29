package org.cf0x.konamiku.nfc

import android.content.Intent
import android.nfc.cardemulation.HostNfcFService
import android.os.Bundle
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.loadActiveCard
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.notification.ScanReceiver

class EmuCard : HostNfcFService() {

    companion object {
        const val EXTRA_AUTO_ACTIVATE = "org.cf0x.konamiku.EXTRA_AUTO_ACTIVATE"
        const val EXTRA_AUTO_MODE     = "org.cf0x.konamiku.EXTRA_AUTO_MODE"
    }

    private var felicaCard: FelicaCard? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KonamikU::EmuCard",
        ).also { it.acquire(10 * 60 * 1000L) }

        val ctx: android.content.Context = this
        runBlocking(Dispatchers.IO) {
            val active = applicationContext.loadActiveCard() ?: return@runBlocking
            val realIdm = active.card.idm.uppercase()
            val activeIdm = resolveActiveIdm(active.card.idm, active.mode)
            android.util.Log.i("KonamikU", "EmuCard active: IDm=$activeIdm, real=$realIdm, mode=${active.mode}")
            felicaCard = FelicaCard(ctx, activeIdm = activeIdm, realIdm = realIdm, emuMode = active.mode)
        }
    }

    override fun processNfcFPacket(commandPacket: ByteArray, extras: Bundle?): ByteArray? {
        // Validate packet length per FeliCa spec
        if (commandPacket.size < 1 + 1 + 8 || commandPacket.size.toByte() != commandPacket[0]) return null

        val card = felicaCard ?: return null
        if (card.emuMode == EmuMode.NATIVE) return null
        return when (commandPacket[1].toInt() and 0xFF) {
            0x06 -> handleRead(commandPacket)
            0x08 -> handleWrite(commandPacket)
            else -> null
        }
    }

    private fun handleRead(cmd: ByteArray): ByteArray? {
        if (cmd.size < 12) return null
        val card = felicaCard ?: return null

        return runCatching {
            val serviceCount = cmd[10].toInt() and 0xFF
            var pos = 11 + (serviceCount * 2)
            if (pos >= cmd.size) return null
            val blockCount = cmd[pos++].toInt() and 0xFF

            val blockData = mutableListOf<ByteArray>()
            repeat(blockCount) {
                if (pos >= cmd.size) return null
                val b0 = cmd[pos].toInt() and 0xFF
                val isTwoByte = (b0 and 0x80) == 0
                val blockNumber: Int
                if (isTwoByte) {
                    if (pos + 1 >= cmd.size) return null
                    blockNumber = cmd[pos + 1].toInt() and 0xFF
                    pos += 2
                } else {
                    blockNumber = b0 and 0x7F
                    pos += 1
                }
                blockData.add(card.readBlock(blockNumber))
            }

            val responseLen = 1 + 1 + 8 + 1 + 1 + 1 + blockData.size * 16
            val response = ByteArray(responseLen).also { r ->
                var idx = 0
                r[idx++] = responseLen.toByte()
                r[idx++] = 0x07
                card.activeIdmBytes.copyInto(r, idx); idx += 8
                r[idx++] = 0x00
                r[idx++] = 0x00
                r[idx++] = blockData.size.toByte()
                for (b in blockData) { b.copyInto(r, idx); idx += 16 }
            }

            fireScanBroadcast()
            response
        }.getOrNull()
    }

    private fun handleWrite(cmd: ByteArray): ByteArray? {
        val card = felicaCard ?: return null
        val resp = ByteArray(1 + 1 + 8 + 2)  // len + 0x09 + IDm + 2 status flags
        resp[0] = resp.size.toByte()
        resp[1] = 0x09
        card.activeIdmBytes.copyInto(resp, 2)
        // status flags already 0x00 by default
        return resp
    }

    private fun fireScanBroadcast() {
        sendBroadcast(Intent(ScanReceiver.ACTION_SCAN).setPackage(packageName))
    }

    override fun onDeactivated(reason: Int) = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    }
}
