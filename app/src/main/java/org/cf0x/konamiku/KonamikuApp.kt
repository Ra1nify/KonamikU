package org.cf0x.konamiku

import android.app.Application
import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.NfcFCardEmulation
import android.util.Log
import androidx.core.os.LocaleListCompat
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.AppLocale
import org.cf0x.konamiku.nfc.EmuCard
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

class KonamikuApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()

        val dataStore = AppDataStore(this)
        val locale = runBlocking { dataStore.appLocale.first() }
        if (locale != AppLocale.SYSTEM) {
            LocaleListCompat.forLanguageTags(locale.tag)
        }
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        XposedState.frameworkName    = service.frameworkName
        XposedState.frameworkVersion = service.frameworkVersion

        // Framework is connected → at minimum NEEDS_RESTART.
        // Probe to check if com.android.nfc is already hooked:
        // registerSystemCodeForService("88B4") throws IllegalArgumentException
        // unless isValidSystemCode hook is active.
        val hooked = probeNfcHookActive()
        XposedState.activationState = if (hooked)
            XposedActivationState.ACTIVE
        else
            XposedActivationState.NEEDS_RESTART

        Log.i("KonamikU", "service bound — state=${XposedState.activationState} hooked=$hooked")
    }

    override fun onServiceDied(service: XposedService) {
        XposedState.activationState  = XposedActivationState.INACTIVE
        XposedState.frameworkName    = ""
        XposedState.frameworkVersion = ""
    }

    /**
     * Probes whether the isValidSystemCode hook is live by attempting to register
     * system code "88B4" (rejected natively, accepted only when hook returns true).
     * Does not require an Activity — only ComponentName is needed.
     */
    private fun probeNfcHookActive(): Boolean {
        val adapter   = NfcAdapter.getDefaultAdapter(this) ?: return false
        val emulation = runCatching { NfcFCardEmulation.getInstance(adapter) }.getOrNull()
            ?: return false
        val component = ComponentName(this, EmuCard::class.java)

        return runCatching {
            val ok = emulation.registerSystemCodeForService(component, "88B4")
            if (ok) emulation.unregisterSystemCodeForService(component)
            ok
        }.getOrDefault(false)
    }
}