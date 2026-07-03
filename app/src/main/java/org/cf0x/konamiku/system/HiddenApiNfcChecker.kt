package org.cf0x.konamiku.system

import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Fallback NFC hidden-API checker that uses [HiddenApiBypass] to call
 * system-internal NFC methods **without** requiring Xposed.
 *
 * On Android 9+ the platform blocks reflection against hidden APIs
 * (methods annotated with `@hide`).  HiddenApiBypass works around this
 * by calling the methods through an unsupported internal path.
 *
 * This lets the UI show whether the device would *natively* accept an
 * NFCID2 or system code — even before (or without) installing the
 * Xposed module.
 */
object HiddenApiNfcChecker {

    private const val TAG = "KonamikU-HiddenApi"

    private const val NFC_CARD_EMULATION =
        "android.nfc.cardemulation.NfcFCardEmulation"

    /** System code used by FeliCa HCE-F (88B4). */
    private const val SYSTEM_CODE = "88B4"

    /** Example NFCID2 that should be accepted (02FE prefix). */
    private const val EXAMPLE_NFCID2 = "02FE000000000001"

    // ---- Result types ----

    data class NfcHiddenApiResult(
        /** Whether the hidden API bridge is available at all. */
        val bridgeAvailable: Boolean,
        /** Whether [SYSTEM_CODE] passes the hidden [isValidSystemCode] check. */
        val systemCodeValid: Boolean? = null,
        /** Whether [EXAMPLE_NFCID2] passes the hidden [isValidNfcid2] check. */
        val nfcid2Valid: Boolean? = null,
        /** Human-readable error message if something failed. */
        val error: String? = null
    )

    // ---- Public API ----

    /**
     * Run all available hidden-API checks and return a combined result.
     * When HiddenApiBypass is not available the result still reports
     * [bridgeAvailable] = false so callers can decide how to render it.
     */
    fun checkAll(): NfcHiddenApiResult {
        if (!isBypassAvailable()) {
            return NfcHiddenApiResult(bridgeAvailable = false)
        }

        val sysCodeResult = runCatching {
            invokeStatic("isValidSystemCode", SYSTEM_CODE) as Boolean
        }
        val nfcid2Result = runCatching {
            invokeStatic("isValidNfcid2", EXAMPLE_NFCID2) as Boolean
        }

        val errors = listOfNotNull(
            sysCodeResult.exceptionOrNull()?.let { "isValidSystemCode: ${it.message}" },
            nfcid2Result.exceptionOrNull()?.let { "isValidNfcid2: ${it.message}" }
        )

        return NfcHiddenApiResult(
            bridgeAvailable  = true,
            systemCodeValid  = sysCodeResult.getOrNull(),
            nfcid2Valid      = nfcid2Result.getOrNull(),
            error            = errors.joinToString("; ").ifEmpty { null }
        )
    }

    /**
     * Quick check: does the system natively accept system code [code]?
     * Returns `null` when the bridge is unavailable.
     */
    fun isSystemCodeValid(code: String = SYSTEM_CODE): Boolean? {
        if (!isBypassAvailable()) return null
        return runCatching {
            invokeStatic("isValidSystemCode", code) as Boolean
        }.getOrNull()
    }

    /**
     * Quick check: does the system natively accept NFCID2 [idm]?
     * Returns `null` when the bridge is unavailable.
     */
    fun isNfcid2Valid(idm: String = EXAMPLE_NFCID2): Boolean? {
        if (!isBypassAvailable()) return null
        return runCatching {
            invokeStatic("isValidNfcid2", idm) as Boolean
        }.getOrNull()
    }

    // ---- Internals ----

    /** Whether HiddenApiBypass is loadable on this runtime. */
    private fun isBypassAvailable(): Boolean = runCatching {
        Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass")
        true
    }.getOrDefault(false)

    /**
     * Call a static hidden method on [NfcFCardEmulation] via HiddenApiBypass.
     *
     * For Android < P we fall back to plain reflection since the restriction
     * didn't exist yet.
     */
    private fun invokeStatic(methodName: String, arg: String): Any? {
        val clazz = Class.forName(NFC_CARD_EMULATION)
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val method = clazz.getDeclaredMethod(methodName, String::class.java)
            method.invoke(null, arg)
        } else {
            HiddenApiBypass.invoke(clazz, null, methodName, arg)
        }
    }

    // ---- Diagnostic logging ----

    /** Log full hidden-API status for debugging. */
    fun logStatus() {
        val r = checkAll()
        Log.i(TAG, "HiddenApiNfcChecker — bridgeAvailable=${r.bridgeAvailable}")
        if (r.bridgeAvailable) {
            Log.i(TAG, "  systemCodeValid=$SYSTEM_CODE → ${r.systemCodeValid}")
            Log.i(TAG, "  nfcid2Valid=$EXAMPLE_NFCID2 → ${r.nfcid2Valid}")
            r.error?.let { Log.w(TAG, "  errors: $it") }
        }
    }
}
