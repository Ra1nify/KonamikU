package org.cf0x.konamiku.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.NfcFCardEmulation
import android.util.Log
import org.cf0x.konamiku.data.EmuMode

private const val TAG = "KonamikU-NfcUtils"

/**
 * Re-points the HCE-F service at a new IDm / emulation mode and re-enables it.
 *
 * Callers previously had to remember to call enableService() themselves, and
 * two of them (NotificationActionReceiver on card switch and on mode switch)
 * did not - switching from the notification left the service registered but
 * disabled, so emulation silently stopped working.
 *
 * [host] is the Activity handed to enableService(), which requires an
 * Activity (its signature is `enableService(Activity, ComponentName)`). When
 * no Activity is available - the notification path runs from a
 * BroadcastReceiver - the service is left enabled as-is: the registration
 * above already applies to an enabled service, and calling enableService is
 * only needed to recover from a disabled state.
 *
 * @return true when both the IDm and system code registration succeeded.
 */
fun updateHceRegistration(context: Context, idm: String, mode: EmuMode, host: Activity? = null): Boolean {
    return runCatching {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        val emulation = NfcFCardEmulation.getInstance(adapter) ?: return false
        val component = ComponentName(context, EmuCard::class.java)
        val activeIdm = resolveActiveIdm(idm, mode)

        // Re-registering while enabled can be rejected, so cycle the service.
        runCatching { host?.let { emulation.disableService(it) } }

        val idmOk = emulation.setNfcid2ForService(component, activeIdm)
        val sysOk = emulation.registerSystemCodeForService(component, SYSTEM_CODE_FELICA)

        if (!idmOk || !sysOk) {
            Log.w(TAG, "Registration rejected: idm=$idmOk sys=$sysOk (idm=$activeIdm, mode=$mode)")
        }

        if (host != null) {
            runCatching { emulation.enableService(host, component) }
                .onFailure { Log.w(TAG, "enableService failed: ${it.message}") }
        } else {
            Log.d(TAG, "No Activity available; leaving the service enabled")
        }

        EmuCard.invalidateCard()
        idmOk && sysOk
    }.onFailure {
        Log.w(TAG, "updateHceRegistration failed: ${it.message}")
    }.getOrDefault(false)
}
