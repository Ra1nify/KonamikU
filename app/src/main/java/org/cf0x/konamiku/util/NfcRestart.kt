package org.cf0x.konamiku.util

import android.content.Context
import android.nfc.NfcAdapter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object NfcRestart {

    private const val TAG = "KonamikU-NfcRestart"

    sealed class Result {
        /**
         * Whether the HCE-F proxy answered a probe after the caches were
         * dropped. False means the restart finished but this process still
         * cannot reach the service, which is the one case that genuinely needs
         * an app restart.
         */
        abstract val proxyReachable: Boolean

        /** com.android.nfc was already dead before pkill */
        data class WasDead(
            val newPid: Int?,
            override val proxyReachable: Boolean = false
        ) : Result()

        /** pkill ran but PID didn't change — kill failed */
        data class KillFailed(
            val pid: Int,
            override val proxyReachable: Boolean = false
        ) : Result()

        /** pkill succeeded; system may need to restart NFC */
        data class Killed(
            val oldPid: Int,
            val newPid: Int?,
            override val proxyReachable: Boolean = false
        ) : Result()

        /** NFC restarted cleanly — new PID differs from old */
        data class Restarted(
            val oldPid: Int,
            val newPid: Int,
            override val proxyReachable: Boolean = false
        ) : Result()
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

        // 5. Drop the binder caches our process still holds from the old NFC
        //    service. The wait loop below needs a working adapter, and every
        //    card-emulation call goes through the static proxies cleared here.
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

        // 7. Probe the HCE-F proxy once. Clearing dropped a proxy that would
        //    otherwise stay dead; this distinguishes "the restart looked
        //    finished" from "the app can talk to the service again". One retry
        //    covers the case where the first call lands while the rebind is
        //    still in flight.
        val proxyOk = canReachNfcFService(context) || canReachNfcFService(context)
        Log.i(TAG, "HCE-F proxy reachable after restart: $proxyOk")

        // 7. Capture new PID
        val newPid = getPid()
        onStep(Step.CHECK_PID, oldPid, newPid)

        // 8. Build and return result
        val result = when {
            oldPid == null                     -> Result.WasDead(newPid, proxyOk)
            newPid != null && newPid == oldPid -> Result.KillFailed(oldPid, proxyOk)
            newPid == null                     -> Result.Killed(oldPid, null, proxyOk)
            else                               -> Result.Restarted(oldPid, newPid, proxyOk)
        }
        onStep(Step.DONE, oldPid, newPid)
        result
    }

    /**
     * Drops the binder caches that survive a NFC process restart.
     *
     * After `pkill com.android.nfc` the framework rebinds its own proxies, but
     * three things in *our* process still point at the dead binder:
     *
     *   NfcFCardEmulation.sCardEmus  HashMap<Context, NfcFCardEmulation>
     *   CardEmulation.sCardEmus      same shape
     *   *.sService                   the static INfc*CardEmulation proxy
     *
     * getInstance() only constructs a new manager when sCardEmus has no entry
     * for the context, so a cached manager survives forever - it keeps handing
     * back an object whose calls go through a dead sService. Clearing the map
     * alone is not enough either: sService is static and shared, so it has to
     * be nulled for the next call to take the recoverService() path and rebind.
     *
     * The field names are verified against AOSP: sCardEmus/sService have been
     * present since Android 13 with this exact shape, and they are still there
     * on Android 17 (checked against framework-nfc.jar from the device). The
     * previous implementation cleared sServiceCache/sAdapter, which do not
     * exist on any recent release - every attempt logged "No field" and the
     * caches were never actually dropped, which is why a full app restart
     * appeared to be required.
     *
     * @return true when at least one cache was dropped.
     */
    fun clearNfcFCache(): Boolean {
        Log.i(TAG, "Clearing NFC binder caches…")
        var cleared = false

        fun clearMap(clsName: String, fieldName: String) {
            runCatching {
                val field = Class.forName(clsName).getDeclaredField(fieldName)
                field.isAccessible = true
                val map = field.get(null) as? MutableMap<*, *> ?: return@runCatching
                val size = map.size
                map.clear()
                Log.i(TAG, "  $clsName.$fieldName cleared ($size entries)")
            }.onSuccess { cleared = true }
                .onFailure { Log.w(TAG, "  clearMap $clsName.$fieldName: ${it.message}") }
        }

        fun clearStatic(clsName: String, fieldName: String) {
            runCatching {
                val field = Class.forName(clsName).getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(null, null)
                Log.i(TAG, "  $clsName.$fieldName nulled")
            }.onSuccess { cleared = true }
                .onFailure { Log.w(TAG, "  clearStatic $clsName.$fieldName: ${it.message}") }
        }

        clearMap("android.nfc.cardemulation.NfcFCardEmulation", "sCardEmus")
        clearMap("android.nfc.cardemulation.CardEmulation", "sCardEmus")

        // The static proxies: nulling them is what forces a rebind, because
        // every API call dereferences them and recoverService() repopulates
        // them on the resulting RemoteException.
        clearStatic("android.nfc.cardemulation.NfcFCardEmulation", "sService")
        clearStatic("android.nfc.cardemulation.CardEmulation", "sService")
        clearStatic("android.nfc.NfcAdapter", "sNfcFCardEmulationService")
        clearStatic("android.nfc.NfcAdapter", "sCardEmulationService")

        Log.i(TAG, "NFC binder caches cleared (cleared=$cleared)")
        return cleared
    }

    /**
     * Probes whether the HCE-F proxy is live again after [clearNfcFCache].
     *
     * Rebinding is lazy - it happens on the first call that touches the proxy -
     * so a successful clear is not by itself proof that the app can talk to the
     * restored NFC service. This issues one cheap call and reports whether it
     * came back without throwing.
     */
    fun canReachNfcFService(context: Context): Boolean = runCatching {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        val emulation = android.nfc.cardemulation.NfcFCardEmulation.getInstance(adapter)
            ?: return false
        // getMaxNumOfRegisterableSystemCodes() is a plain binder round-trip and
        // has no side effects; -1 means the framework reported a failure.
        emulation.maxNumOfRegisterableSystemCodes != -1
    }.onFailure {
        Log.w(TAG, "HCE-F proxy still unreachable after cache clear: ${it.message}")
    }.getOrDefault(false)
}
