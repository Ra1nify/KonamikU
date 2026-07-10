package org.cf0x.konamiku.util

import android.content.Context
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

    /** Steps emitted by [restart] so callers can show progress toasts.
     *  Each step is followed by a 500 ms delay before the next operation. */
    enum class Step {
        /** Initial PID captured (or null if not running). */
        CAPTURE_PID,
        /** pkill sent to com.android.nfc. */
        KILL_PROCESS,
        /** svc nfc disable completed. */
        DISABLE_NFC,
        /** svc nfc enable completed. */
        ENABLE_NFC,
        /** Binder caches cleared. */
        CLEAR_CACHE,
        /** NFC adapter reported enabled. */
        NFC_READY,
        /** New PID captured after restart. */
        CHECK_PID,
        /** Entire sequence finished — [Result] is the return value. */
        DONE
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
     * Unified NFC restart sequence — the single public method for all NFC
     * restart needs.
     *
     * Steps (each followed by 500 ms delay):
     * 1. Capture old PID           → [Step.CAPTURE_PID]
     * 2. pkill com.android.nfc     → [Step.KILL_PROCESS]
     * 3. svc nfc disable           → [Step.DISABLE_NFC]
     * 4. svc nfc enable            → [Step.ENABLE_NFC]
     * 5. Clear binder caches       → [Step.CLEAR_CACHE]
     * 6. Wait for NFC ready        → [Step.NFC_READY]
     * 7. Capture new PID           → [Step.CHECK_PID]
     * 8. Return [Result]           → [Step.DONE]
     *
     * @param context  Application context for NfcAdapter lookup.
     * @param onStep   Suspend callback invoked after each step; the 500 ms
     *                 delay runs **after** this callback returns, so callers
     *                 can emit a toast (or perform other UI) before the
     *                 next operation begins.
     * @return [Result] describing the outcome.
     */
    suspend fun restart(
        context: Context,
        onStep: suspend (step: Step, oldPid: Int?, newPid: Int?) -> Unit = { _, _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        // 1. Capture old PID
        val oldPid = getPid()
        onStep(Step.CAPTURE_PID, oldPid, null)
        delay(500)

        // 2. Kill the NFC process
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f com.android.nfc")).waitFor()
        }
        onStep(Step.KILL_PROCESS, oldPid, null)
        delay(500)

        // 3. Disable NFC
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "svc nfc disable")).waitFor()
        }
        onStep(Step.DISABLE_NFC, oldPid, null)
        delay(500)

        // 4. Enable NFC
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "svc nfc enable")).waitFor()
        }
        onStep(Step.ENABLE_NFC, oldPid, null)
        delay(500)

        // 5. Clear stale binder cache so the wait loop below gets a fresh
        //    adapter instance reflecting the actual NFC state.
        clearNfcFCache()
        onStep(Step.CLEAR_CACHE, oldPid, null)
        delay(500)

        // 6. Wait for NFC service to be ready
        var retry = 0
        while (retry < 15) {
            val adapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
            if (adapter?.isEnabled == true) break
            delay(500)
            retry++
        }
        onStep(Step.NFC_READY, oldPid, null)
        delay(500)

        // 7. Capture new PID
        val newPid = getPid()
        onStep(Step.CHECK_PID, oldPid, newPid)

        // 8. Build and return result
        val result = when {
            oldPid == null                     -> Result.WasDead(newPid)
            newPid != null && newPid == oldPid -> Result.KillFailed(oldPid)
            newPid == null                     -> Result.Killed(oldPid, null)
            else                               -> Result.Restarted(oldPid, newPid)
        }
        onStep(Step.DONE, oldPid, newPid)
        result
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
