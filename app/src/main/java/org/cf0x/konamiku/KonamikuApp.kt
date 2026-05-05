package org.cf0x.konamiku

import android.app.Application
import androidx.core.os.LocaleListCompat
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.AppLocale
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
        val prefs = runCatching {
            service.getRemotePreferences("KonamikU")
        }.getOrNull()

        val nfcHooked = prefs?.getBoolean("nfc_hooked", false) ?: false

        XposedState.activationState = if (nfcHooked)
            XposedActivationState.ACTIVE
        else
            XposedActivationState.NEEDS_RESTART

        XposedState.frameworkName    = service.frameworkName
        XposedState.frameworkVersion = service.frameworkVersion
    }

    override fun onServiceDied(service: XposedService) {
        XposedState.activationState  = XposedActivationState.INACTIVE
        XposedState.frameworkName    = ""
        XposedState.frameworkVersion = ""
    }
}