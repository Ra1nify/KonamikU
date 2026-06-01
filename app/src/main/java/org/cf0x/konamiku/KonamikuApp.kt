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
        
        // Reset active state on fresh start
        runBlocking {
            dataStore.saveActiveCardId(null)
            
            // Validate Auto-Exclusive state: A 为开关, B 为记录的包名, C 为目前的包名
            val autoExclusive = dataStore.autoExclusiveMode.first()
            if (!autoExclusive) {
                // A 未激活，则 B 清空
                dataStore.saveLastPaymentApp(null)
            } else {
                // A 激活，则执行 B = C 的赋值操作 (排除 KonamikU 自身以防覆盖备份)
                val current = org.cf0x.konamiku.util.NfcDefaultAppManager.getCurrentDefault()
                val isUs    = org.cf0x.konamiku.util.NfcDefaultAppManager.isOurComponent(this@KonamikuApp, current)
                
                if (!isUs && !current.isNullOrBlank()) {
                    dataStore.saveLastPaymentApp(current)
                }
            }
        }

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

        val hooked = NfcHookProber.probe(this)
        XposedState.activationState = if (hooked)
            XposedActivationState.ACTIVE
        else
            XposedActivationState.NEEDS_RESTART

        Log.i("KonamikU", "service bound — state=${XposedState.activationState} hooked=$hooked")
    }

    override fun onServiceDied(service: XposedService) {
        XposedState.reset()
    }
}
