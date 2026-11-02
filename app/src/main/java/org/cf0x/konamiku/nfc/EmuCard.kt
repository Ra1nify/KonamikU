package org.cf0x.konamiku.nfc

import android.content.Intent
import android.nfc.cardemulation.HostNfcFService
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cf0x.konamiku.data.loadActiveCard
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.notification.ScanReceiver

class EmuCard : HostNfcFService() {

    companion object {
        private const val TAG = "KonamikU-EmuCard"
        const val EXTRA_AUTO_ACTIVATE = "org.cf0x.konamiku.EXTRA_AUTO_ACTIVATE"
        const val EXTRA_AUTO_MODE     = "org.cf0x.konamiku.EXTRA_AUTO_MODE"

        /**
         * Bumped whenever the active card or emulation mode changes.
         *
         * The system keeps a HostNfcFService instance alive for as long as the
         * HCE-F route is registered, so loading the card once in onCreate()
         * left the service answering with a stale IDm and stale block data
         * after every switch - the reader would match on the new nfcid2 but
         * reject the IDm inside the response. A counter is cheaper than
         * re-reading DataStore on every packet, which matters because the
         * FeliCa interaction window is only a few hundred milliseconds.
         */
        @Volatile
        private var cardGeneration: Int = 0

        /** Called by the switch paths so the running service reloads. */
        fun invalidateCard() {
            cardGeneration++
        }
    }

    private var felicaCard: FelicaCard? = null

    /** Generation the cached [felicaCard] was built from. */
    @Volatile
    private var loadedGeneration: Int = -1

    /**
     * Serialises reloads so a background prefetch cannot race the first
     * packet, and so two invalidations collapse into one read.
     */
    private val reloadLock = Mutex()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Warm the cache off the main thread. onCreate runs on the main thread
        // and a DataStore first read parses a file; blocking here used to eat
        // into the FeliCa response window when the system bound the service on
        // first tag contact.
        serviceScope.launch { reloadCard() }
    }

    override fun processNfcFPacket(commandPacket: ByteArray, extras: Bundle?): ByteArray? {
        // A switch happened since the last packet: kick off a reload but answer
        // this frame from the cached card. processNfcFPacket is a synchronous
        // binder callback with a few hundred milliseconds of budget, so it must
        // never wait on disk. Answering one frame with the previous card is
        // strictly better than a timeout, and the next frame is already correct.
        if (loadedGeneration != cardGeneration) {
            serviceScope.launch { reloadCard() }
        }

        val card = felicaCard ?: return null

        // NATIVE mode: pass through to the real NFC controller.
        if (card.emuMode == EmuMode.NATIVE) {
            return null
        }

        // Validate packet length per FeliCa spec.
        // The length byte is unsigned: comparing ByteArray.size.toByte()
        // against it fails for any frame of 128+ bytes (0x80 becomes -128),
        // which silently rejected large multi-block reads.
        val declared = commandPacket[0].toInt() and 0xFF
        if (commandPacket.size < 1 + 1 + 8 || commandPacket.size != declared) {
            Log.w(TAG, "Invalid packet length: size=${commandPacket.size} declared=$declared")
            return null
        }

        return when (commandPacket[1].toInt() and 0xFF) {
            0x06 -> handleRead(commandPacket)
            0x08 -> handleWrite(commandPacket)
            else -> null
        }
    }

    // ── Card init ──

    /**
     * Rebuilds the cached card. Suspending, so it only ever runs on
     * [serviceScope]; a generation is recorded *after* the read so a switch
     * that lands mid-read is not silently marked as loaded.
     */
    private suspend fun reloadCard() = reloadLock.withLock {
        val generation = cardGeneration
        val card = tryInitCard()
        felicaCard = card
        loadedGeneration = generation
    }

    private suspend fun tryInitCard(): FelicaCard? = runCatching {
        val active = applicationContext.loadActiveCard()
            ?: return@runCatching null.also { Log.w(TAG, "No active card found for emulation") }

        val realIdm   = active.card.idm.uppercase()
        val activeIdm = resolveActiveIdm(active.card.idm, active.mode)
        Log.i(TAG, "Initialized: IDm=$activeIdm, real=$realIdm, mode=${active.mode}")
        FelicaCard(this, activeIdm = activeIdm, realIdm = realIdm, emuMode = active.mode)
    }.getOrElse { e ->
        Log.e(TAG, "Failed to init emulation card: ${e.message}")
        null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── FeliCa command handlers ──

    private fun handleRead(cmd: ByteArray): ByteArray? {
        // 1 len + 1 cmd + 8 IDm + 1 service count + 1 block count
        if (cmd.size < 12) return null
        val card = felicaCard ?: return null

        return runCatching {
            val serviceCount = cmd[10].toInt() and 0xFF
            // The service list occupies 2 bytes per entry.
            var pos = 11 + (serviceCount * 2)
            if (pos >= cmd.size) {
                Log.w(TAG, "Service list overruns frame: count=$serviceCount size=${cmd.size}")
                return null
            }
            val blockCount = cmd[pos++].toInt() and 0xFF

            val blockData = mutableListOf<ByteArray>()
            repeat(blockCount) {
                if (pos >= cmd.size) {
                    Log.w(TAG, "Block list overruns frame at block #$it")
                    return null
                }
                val b0 = cmd[pos].toInt() and 0xFF
                // Bit 7 set = 1-byte element block number; clear = 2-byte.
                val blockNumber: Int
                if ((b0 and 0x80) != 0) {
                    blockNumber = b0 and 0x7F
                    pos += 1
                } else {
                    if (pos + 1 >= cmd.size) {
                        Log.w(TAG, "Truncated 2-byte block number at block #$it")
                        return null
                    }
                    // 2-byte form: 6 bits of index in b0 (bits 5..0) plus a
                    // full b1. The old code took only b1 and dropped the high
                    // bits, mapping block numbers above 0xFF onto low ones.
                    val b1 = cmd[pos + 1].toInt() and 0xFF
                    blockNumber = ((b0 and 0x3F) shl 8) or b1
                    pos += 2
                }
                blockData.add(card.readBlock(blockNumber))
            }

            val responseLen = 1 + 1 + 8 + 1 + 1 + 1 + blockData.size * 16
            if (responseLen > 255) {
                // A FeliCa response frame is limited to one length byte.
                Log.w(TAG, "Response too large: $blockCount blocks -> $responseLen bytes")
                return null
            }

            val response = ByteArray(responseLen).also { r ->
                var idx = 0
                r[idx++] = responseLen.toByte()
                r[idx++] = 0x07
                card.activeIdmBytes.copyInto(r, idx); idx += 8
                r[idx++] = 0x00   // status flag 1
                r[idx++] = 0x00   // status flag 2
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
}
