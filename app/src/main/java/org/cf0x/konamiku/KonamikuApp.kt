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
import org.cf0x.konamiku.xposed.NfcHookProber
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

class KonamikuApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()

        val dataStore = AppDataStore(this)
        
        // Reset state on start
        runBlocking {
            dataStore.saveActiveCardId(null)
        }

        val tag = runCatching {
            runBlocking { dataStore.appLocale.first().tag }
        }.getOrDefault(AppLocale.EN_US.tag)

        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(tag)
        )

        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        XposedState.frameworkName    = service.frameworkName    ?: ""
        XposedState.frameworkVersion = service.frameworkVersion ?: ""

        // Restore last known pmm state
        XposedState.pmmActive = getSharedPreferences("KonamikU_xposed", MODE_PRIVATE)
            .getBoolean("pmmtool_active", false)

        val hooked = NfcHookProber.probe(this)
        XposedState.activationState = if (hooked) XposedActivationState.ACTIVE 
                                      else XposedActivationState.NEEDS_RESTART

        Log.i("KonamikU", "Bound: state=${XposedState.activationState} hooked=$hooked")
    }

    override fun onServiceDied(service: XposedService) {
        XposedState.reset()
    }
}
