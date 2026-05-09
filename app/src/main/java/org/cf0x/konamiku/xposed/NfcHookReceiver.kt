package org.cf0x.konamiku.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NfcHookReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NFC_HOOKED) return
        val pmmOk = intent.getBooleanExtra("pmmtool_active", false)
        Log.i("KonamikU", "NFC hook confirmed via broadcast — pmmtool=$pmmOk")
        XposedState.activationState = XposedActivationState.ACTIVE
        XposedState.pmmActive       = pmmOk
        // Persist so app restart can restore state without waiting for next broadcast
        context.getSharedPreferences("KonamikU_xposed", Context.MODE_PRIVATE)
            .edit().putBoolean("pmmtool_active", pmmOk).apply()
    }

    companion object {
        const val ACTION_NFC_HOOKED = "org.cf0x.konamiku.ACTION_NFC_HOOKED"
    }
}