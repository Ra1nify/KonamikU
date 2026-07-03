package org.cf0x.konamiku.nfc

import android.content.Intent
import android.nfc.cardemulation.HostNfcFService
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.loadActiveCard
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.notification.ScanReceiver

class EmuCard : HostNfcFService() {

    companion object {
        private const val TAG = "KonamikU-EmuCard"
        const val EXTRA_AUTO_ACTIVATE = "org.cf0x.konamiku.EXTRA_AUTO_ACTIVATE"
        const val EXTRA_AUTO_MODE     = "org.cf0x.konamiku.EXTRA_AUTO_MODE"

        private const val WAKE_LOCK_TAG    = "KonamikU::EmuCard"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
    }

    private var felicaCard: FelicaCard? = null
    private var initFailed = false

    /** Whether we've attempted card initialization since service start. */
    private var initAttempted = false

    private lateinit var wakeLock: PowerManager.WakeLock
    private var lastPacketElapsedMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        acquireWakeLock()
    }

    override fun processNfcFPacket(commandPacket: ByteArray, extras: Bundle?): ByteArray? {
        // Lazy init: initialize card on first packet instead of blocking onCreate
        if (!initAttempted) {
            initAttempted = true
            felicaCard = tryInitCard()
            initFailed = felicaCard == null
        }

        val card = felicaCard ?: return null
        // NATIVE mode: pass through to the real NFC controller
        if (card.emuMode == EmuMode.NATIVE) {
            return null
        }

        // Validate packet length per FeliCa spec
        if (commandPacket.size < 1 + 1 + 8 || commandPacket.size.toByte() != commandPacket[0]) {
            Log.w(TAG, "Invalid packet length: ${commandPacket.size}")
            return null
        }

        // Refresh wake lock on each active packet
        refreshWakeLock()

        return when (commandPacket[1].toInt() and 0xFF) {
            0x06 -> handleRead(commandPacket)
            0x08 -> handleWrite(commandPacket)
            else -> null
        }
    }

    // ── Card init (lazy, runs on the calling thread) ──

    private fun tryInitCard(): FelicaCard? = runCatching {
        val active = runBlocking(Dispatchers.IO) {
            applicationContext.loadActiveCard()
        } ?: return@runCatching null.also { Log.w(TAG, "No active card found for emulation") }

        val realIdm   = active.card.idm.uppercase()
        val activeIdm = resolveActiveIdm(active.card.idm, active.mode)
        Log.i(TAG, "Initialized: IDm=$activeIdm, real=$realIdm, mode=${active.mode}")
        FelicaCard(this, activeIdm = activeIdm, realIdm = realIdm, emuMode = active.mode)
    }.getOrElse { e ->
        Log.e(TAG, "Failed to init emulation card: ${e.message}")
        null
    }

    // ── Wake lock management ──

    private fun acquireWakeLock() {
        if (!::wakeLock.isInitialized) return
        runCatching {
            if (!wakeLock.isHeld) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
            else wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS) // refresh timeout
        }.onFailure { Log.w(TAG, "WakeLock acquire failed: ${it.message}") }
    }

    private fun refreshWakeLock() {
        if (!::wakeLock.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        // Only refresh if more than 30s have passed to avoid excessive calls
        if (now - lastPacketElapsedMs > 30_000L) {
            acquireWakeLock()
            lastPacketElapsedMs = now
        }
    }

    // ── FeliCa command handlers ──

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
        return resp
    }

    private fun fireScanBroadcast() {
        runCatching {
            sendBroadcast(Intent(ScanReceiver.ACTION_SCAN).setPackage(packageName))
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated reason=$reason")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            runCatching { wakeLock.release() }
        }
    }
}

