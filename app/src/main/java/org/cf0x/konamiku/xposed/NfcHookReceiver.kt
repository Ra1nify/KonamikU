package org.cf0x.konamiku.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives a broadcast sent by KonamikuModule from inside com.android.nfc process
 * the moment NfcApplication.onCreate is hooked — confirming the NFC hook is live.
 */
class NfcHookReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NFC_HOOKED) return
        val pmmOk = intent.getBooleanExtra("pmmtool_active", false)
        Log.i("KonamikU", "NFC hook confirmed via broadcast — pmmtool=$pmmOk")
        XposedState.activationState = XposedActivationState.ACTIVE
        XposedState.pmmActive       = pmmOk
    }

    companion object {
        const val ACTION_NFC_HOOKED = "org.cf0x.konamiku.ACTION_NFC_HOOKED"
    }
}