package org.cf0x.konamiku.nfc

import android.content.Intent
import android.nfc.cardemulation.HostNfcFService
import android.os.Bundle
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.JsonManager
import org.cf0x.konamiku.notification.ScanReceiver

class EmuCard : HostNfcFService() {

    private var felicaCard: FelicaCard? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KonamikU::EmuCard"
        ).also { it.acquire(10 * 60 * 1000L) }

        runBlocking(Dispatchers.IO) {
            val dataStore   = AppDataStore(applicationContext)
            val jsonManager = JsonManager(applicationContext)
            val activeId    = dataStore.activeCardId.first() ?: return@runBlocking
            val compatMode  = dataStore.compatMode.first()
            val card        = jsonManager.loadCards().find { it.id == activeId }
                ?: return@runBlocking
            val idm         = if (compatMode) "02fe000000000000" else card.idm
            felicaCard      = FelicaCard(idm)
        }
    }

    override fun processNfcFPacket(commandPacket: ByteArray, extras: Bundle?): ByteArray? {
        if (commandPacket.size < 2) return null
        return when (commandPacket[1].toInt() and 0xFF) {
            0x04 -> handlePolling()
            0x06 -> handleRead(commandPacket)
            else -> null
        }
    }

    private fun handlePolling(): ByteArray? {
        val card = felicaCard ?: return null
        return ByteArray(18).also { r ->
            r[0] = 18
            r[1] = 0x01
            card.idmBytes.copyInto(r, 2)
            card.pmmBytes.copyInto(r, 10)
        }
    }

    private fun handleRead(cmd: ByteArray): ByteArray? {
        if (cmd.size < 12) return null
        val card = felicaCard ?: return null

        return runCatching {
            val serviceCount = cmd[10].toInt() and 0xFF
            var pos = 11 + serviceCount * 2
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
                blockData.add(card.readBlock(blockNumber) ?: return null)
            }

            val responseLen = 1 + 1 + 8 + 1 + 1 + 1 + blockData.size * 16
            val response = ByteArray(responseLen).also { r ->
                var idx = 0
                r[idx++] = responseLen.toByte()
                r[idx++] = 0x07
                card.idmBytes.copyInto(r, idx); idx += 8
                r[idx++] = 0x00
                r[idx++] = 0x00
                r[idx++] = blockData.size.toByte()
                for (b in blockData) { b.copyInto(r, idx); idx += 16 }
            }

            sendBroadcast(Intent(ScanReceiver.ACTION_SCAN).setPackage(packageName))
            response
        }.getOrNull()
    }

    override fun onDeactivated(reason: Int) = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    }
}