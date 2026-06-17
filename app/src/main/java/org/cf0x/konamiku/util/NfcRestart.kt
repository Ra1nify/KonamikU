package org.cf0x.konamiku.util

import android.nfc.NfcAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object NfcRestart {

    sealed class Result {
        /** com.android.nfc was already dead before pkill */
        data class WasDead(val newPid: Int?)            : Result()
        /** pkill ran but PID didn't change — kill failed */
        data class KillFailed(val pid: Int)             : Result()
        /** pkill succeeded; system may need to restart NFC */
        data class Killed(val oldPid: Int, val newPid: Int?) : Result()
        /** NFC restarted cleanly — new PID differs from old */
        data class Restarted(val oldPid: Int, val newPid: Int) : Result()
    }

    /** Gets the PID of com.android.nfc via root. Returns null if not running. */
    fun getPid(): Int? = runCatching {
        val proc = Runtime.getRuntime()
            .exec(arrayOf("su", "-c", "pgrep -f com.android.nfc"))
        val out = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        out.lines().firstOrNull { it.isNotBlank() }?.trim()?.toIntOrNull()
    }.getOrNull()

    /**
     * Records the current PID, runs pkill, waits, checks new PID,
     * and returns a [Result] describing what happened.
     */
    suspend fun restart(context: android.content.Context): Result = withContext(Dispatchers.IO) {
        val oldPid = getPid()

        runCatching {
            // pkill for immediate process death
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "pkill -f com.android.nfc"))
                .waitFor()

            // Hard toggle NFC state to force full re-initialization and module loading
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "svc nfc disable"))
                .waitFor()

            delay(500)

            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "svc nfc enable"))
                .waitFor()
        }

        // Clear stale binder cache NOW so the wait loop below gets a fresh
        // adapter instance reflecting the actual NFC state. Doing this after
        // restart() returns (as the caller used to do) is too late — the loop
        // would have already timed out using the dead binder.
        clearNfcFCache()

        // Wait for service to be ready
        var retry = 0
        while (retry < 15) {
            val adapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
            if (adapter?.isEnabled == true) break
            delay(500)
            retry++
        }

        delay(800)

        val newPid = getPid()

        when {
            oldPid == null                           -> Result.WasDead(newPid)
            newPid != null && newPid == oldPid       -> Result.KillFailed(oldPid)
            newPid == null                           -> Result.Killed(oldPid, null)
            else                                     -> Result.Restarted(oldPid, newPid)
        }
    }

    /**
     * Clears the static cache in NfcFCardEmulation, CardEmulation, and NfcAdapter via reflection.
     * This forces the app to re-obtain the service binder and prevents DeadObjectException.
     */
    fun clearNfcFCache() {
        runCatching {
            // 1. NfcFCardEmulation cache
            val nfcFCls = Class.forName("android.nfc.cardemulation.NfcFCardEmulation")
            runCatching {
                val nfcFField = nfcFCls.getDeclaredField("sServiceCache")
                nfcFField.isAccessible = true
                (nfcFField.get(null) as? MutableMap<*, *>)?.clear()
            }

            // 2. CardEmulation cache (for NFC-A/B)
            val nfcACls = Class.forName("android.nfc.cardemulation.CardEmulation")
            runCatching {
                val nfcAField = nfcACls.getDeclaredField("sServiceCache")
                nfcAField.isAccessible = true
                (nfcAField.get(null) as? MutableMap<*, *>)?.clear()
            }

            // 3. NfcAdapter cache and singleton
            val adapterCls = Class.forName("android.nfc.NfcAdapter")
            runCatching {
                val sServiceCacheField = adapterCls.getDeclaredField("sServiceCache")
                sServiceCacheField.isAccessible = true
                (sServiceCacheField.get(null) as? MutableMap<*, *>)?.clear()
            }

            runCatching {
                val sAdapterField = adapterCls.getDeclaredField("sAdapter")
                sAdapterField.isAccessible = true
                sAdapterField.set(null, null)
            }

            android.util.Log.i("KonamikU", "NFC binder caches cleared and adapter singleton reset")
        }.onFailure {
            android.util.Log.e("KonamikU", "Failed to clear NFC cache: ${it.message}")
        }
    }
}
