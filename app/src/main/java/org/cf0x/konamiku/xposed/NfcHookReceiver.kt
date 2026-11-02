package org.cf0x.konamiku.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives the "hook is live" ping from the Xposed module.
 *
 * The sender is the module running inside `com.android.nfc`, which has a
 * different uid and signing certificate than us, so the manifest cannot
 * restrict who delivers this action. Instead of trying to authenticate the
 * sender (impossible without a shared secret), this receiver treats the ping
 * as *advisory*:
 *
 *  - it always records the pmms state that the module reported, and
 *  - it only promotes [XposedActivationState] to ACTIVE when the Xposed
 *    framework itself reports our module as loaded for com.android.nfc,
 *    which is authoritative and cannot be faked by a third-party app.
 */
class NfcHookReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NFC_HOOKED) return

        val pmmOk = intent.getBooleanExtra("pmmtool_active", false)
        Log.i("KonamikU", "NFC hook ping — pmmtool=$pmmOk")

        // This action cannot be authenticated (the sender is com.android.nfc,
        // a different uid and certificate), so the ping is only honoured once
        // the Xposed framework has confirmed our module is loaded there.
        // Without that gate any app could broadcast this and flip pmmActive,
        // which is half of the condition that unlocks NORMAL/COMPAT mode.
        if (!XposedState.frameworkConfirmed) {
            Log.w("KonamikU", "Ignoring hook ping: framework has not confirmed the module")
            return
        }

        XposedState.pmmActive = pmmOk
        XposedState.activationState = XposedActivationState.ACTIVE

        context.getSharedPreferences("KonamikU_xposed", Context.MODE_PRIVATE)
            .edit().putBoolean("pmmtool_active", pmmOk).apply()
    }

    companion object {
        const val ACTION_NFC_HOOKED = "org.cf0x.konamiku.ACTION_NFC_HOOKED"
    }
}
