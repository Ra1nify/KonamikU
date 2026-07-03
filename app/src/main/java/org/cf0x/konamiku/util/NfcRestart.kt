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
        android.util.Log.i("KonamikU", "Clearing NFC binder caches…")

        // Each reflective access is individually caught so one failure
        // doesn't prevent the others from being attempted.
        fun clearCache(clsName: String, fieldName: String) {
            runCatching {
                val cls = Class.forName(clsName)
                val field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                (field.get(null) as? MutableMap<*, *>)?.clear()
            }.onFailure { e ->
                android.util.Log.w("KonamikU", "clearCache $clsName.$fieldName: ${e.message}")
            }
        }

        fun setNull(clsName: String, fieldName: String) {
            runCatching {
                val cls = Class.forName(clsName)
                val field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(null, null)
            }.onFailure { e ->
                android.util.Log.w("KonamikU", "setNull $clsName.$fieldName: ${e.message}")
            }
        }

        clearCache("android.nfc.cardemulation.NfcFCardEmulation", "sServiceCache")
        clearCache("android.nfc.cardemulation.CardEmulation", "sServiceCache")
        clearCache("android.nfc.NfcAdapter", "sServiceCache")
        setNull("android.nfc.NfcAdapter", "sAdapter")

        android.util.Log.i("KonamikU", "NFC binder caches cleared")
    }
}
