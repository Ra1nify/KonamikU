package org.cf0x.konamiku

import android.app.Application
import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.NfcFCardEmulation
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
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
        val locale = runCatching {
            runBlocking { dataStore.appLocale.first() }
        }.getOrDefault(AppLocale.SYSTEM)

        if (locale != AppLocale.SYSTEM) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(locale.tag)
            )
        }

        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        XposedState.frameworkName    = service.frameworkName    ?: ""
        XposedState.frameworkVersion = service.frameworkVersion ?: ""

        // Restore pmmActive from last known state (persisted by NfcHookReceiver)
        XposedState.pmmActive = getSharedPreferences("KonamikU_xposed", MODE_PRIVATE)
            .getBoolean("pmmtool_active", false)

        val hooked = probeNfcHookActive()
        XposedState.activationState = if (hooked)
            XposedActivationState.ACTIVE
        else
            XposedActivationState.NEEDS_RESTART

        Log.i("KonamikU", "service bound — state=${XposedState.activationState} hooked=$hooked")
    }

    override fun onServiceDied(service: XposedService) {
        XposedState.reset()
    }

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