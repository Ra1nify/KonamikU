package org.cf0x.konamiku

import android.app.Application
import android.os.Build
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.AppLocale
import org.cf0x.konamiku.system.UpdateManager
import org.cf0x.konamiku.util.applyLocale
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

class KonamikuApp : Application(), XposedServiceHelper.OnServiceListener {

    private companion object {
        private const val TAG = "KonamikU"
    }

    /**
     * Application-scoped worker for writes that must not block startup.
     *
     * Application.onCreate runs on the main thread before the first frame, so
     * anything awaited there is pure startup latency. Reads whose value is
     * needed immediately (the locale, used to configure resources before the
     * first screen) still block once, but every write is fire-and-forget.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val dataStore = AppDataStore(this)

        // A process restart is not a normal lifecycle event - it means the app
        // was killed, crashed, or updated. Runtime state does not survive that:
        // the HCE-F routing the previous process registered is gone with it, so
        // keeping `activeCardId` would show a card as active while nothing is
        // registered, and the reader would silently time out.
        //
        // Every runtime-only value is therefore reset here rather than resumed:
        // the active card, the Xposed activation snapshot, and the scheduled
        // update check are all re-derived from persisted configuration.
        XposedState.reset()
        appScope.launch {
            dataStore.saveActiveCardId(null)
            val interval = dataStore.updateInterval.first()
            UpdateManager.schedule(this@KonamikuApp, interval)
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
            val matched = AppLocale.fromSystemTag(systemTag)
            if (matched != null) appScope.launch { dataStore.saveAppLocale(matched) }
            systemTag
        } else {
            // The stored locale decides which resources load for the first
            // screen, so this one read has to complete before applyLocale().
            runCatching {
                runBlocking { dataStore.appLocale.first().tag }
            }.getOrDefault(AppLocale.EN_US.tag)
        }

        applyLocale(effectiveTag)

        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        // Wrapped whole: this is a framework callback, and an exception thrown
        // out of it aborts the bind rather than just losing one status field.
        // Individual calls below are still guarded so a partial failure keeps
        // whatever did work.
        runCatching {
            XposedState.frameworkName    = service.frameworkName
            XposedState.frameworkVersion = service.frameworkVersion
        }.onFailure { Log.w(TAG, "Cannot read framework identity: ${it.message}") }

        runCatching {
            // Restore the last reported state so the panel is correct before
            // the next ping arrives. Older builds wrote only the boolean, so
            // both forms are honoured.
            val prefs = getSharedPreferences("KonamikuU_xposed", MODE_PRIVATE)
            XposedState.pmmState = when {
                prefs.contains("pmmtool_state") ->
                    KonamikuModule.PmmState.fromCode(prefs.getInt("pmmtool_state", 0))
                prefs.getBoolean("pmmtool_active", false) ->
                    KonamikuModule.PmmState.INSTALLED
                else -> KonamikuModule.PmmState.NOT_ATTEMPTED
            }
        }

        val hooked = runCatching<Boolean> {
            service.getRunningTargets().any { it.processName == "com.android.nfc" }
        }.getOrDefault(false)

        XposedState.activationState = if (hooked) XposedActivationState.ACTIVE
                                      else XposedActivationState.NEEDS_RESTART
        // Authoritative confirmation from the framework, not from a broadcast.
        // NfcHookReceiver only trusts its ping once this is set.
        if (hooked) XposedState.markFrameworkConfirmed()

        Log.i(TAG, "Bound: state=${XposedState.activationState} hooked=$hooked")
    }

    override fun onServiceDied(service: XposedService) {
        XposedState.reset()
    }
}
