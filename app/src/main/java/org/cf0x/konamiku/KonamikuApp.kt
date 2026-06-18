package org.cf0x.konamiku

import android.app.Application
import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.NfcFCardEmulation
import android.os.Build
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.AppLocale
import org.cf0x.konamiku.nfc.EmuCard
import org.cf0x.konamiku.util.applyLocale
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

        // Sync with system per-app language (API 33+). If the user changed it via
        // system Settings → Apps → Language, our DataStore should follow.
        val systemTag = if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val lm = getSystemService(android.app.LocaleManager::class.java) ?: return@runCatching ""
                lm.applicationLocales.toLanguageTags() ?: ""
            }.getOrDefault("")
        } else ""

        val effectiveTag = if (systemTag.isNotBlank()) {
            val matched = AppLocale.entries.firstOrNull { systemTag.startsWith(it.tag) && it.tag.isNotBlank() }
            if (matched != null) runBlocking { dataStore.saveAppLocale(matched) }
            systemTag
        } else {
            runCatching {
                runBlocking { dataStore.appLocale.first().tag }
            }.getOrDefault(AppLocale.EN_US.tag)
        }

        applyLocale(effectiveTag)

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
