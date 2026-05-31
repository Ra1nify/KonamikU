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
    suspend fun restart(): Result = withContext(Dispatchers.IO) {
        val oldPid = getPid()

        runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "pkill -f com.android.nfc"))
                .waitFor()
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
     * Tries to bring up com.android.nfc via "svc nfc enable".
     * Returns the new PID if successful, null otherwise.
     */
    suspend fun tryBringUp(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "svc nfc enable"))
                .waitFor()
        }
        
        // Wait for service to be ready (adapter enabled)
        var retry = 0
        while (retry < 10) {
            val adapter = NfcAdapter.getDefaultAdapter(null) // Context-less often works for state check
            if (adapter?.isEnabled == true) break
            delay(500)
            retry++
        }
        
        delay(1000)
        getPid()
    }

    /**
     * Clears the static cache in NfcFCardEmulation via reflection.
     * This forces the app to re-obtain the service binder.
     */
    fun clearNfcFCache() {
        runCatching {
            val cls = Class.forName("android.nfc.cardemulation.NfcFCardEmulation")
            val field = cls.getDeclaredField("sServiceCache")
            field.isAccessible = true
            val cache = field.get(null) as? MutableMap<*, *>
            cache?.clear()
            
            // Also clear NfcAdapter's sServiceCache if it exists
            val adapterCls = Class.forName("android.nfc.NfcAdapter")
            val adapterCacheField = adapterCls.getDeclaredField("sServiceCache")
            adapterCacheField.isAccessible = true
            val adapterCache = adapterCacheField.get(null) as? MutableMap<*, *>
            adapterCache?.clear()
        }.onFailure {
            android.util.Log.e("KonamikU", "Failed to clear NFC cache: ${it.message}")
        }
    }
}
