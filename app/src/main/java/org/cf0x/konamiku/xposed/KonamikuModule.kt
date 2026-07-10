package org.cf0x.konamiku.xposed

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class KonamikuModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Log.i("KonamikU", "Loaded in ${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName == "com.android.nfc") {
            Log.i("KonamikU", "NFC process found: pid=${android.os.Process.myPid()}")
            hookNfcValidation(param)
            hookNfcAppForPmmtool(param)
        }
    }

    @Volatile
    private var pmmLoaded = false

    /**
     * Queries the main app's [PmmConfigProvider] for the PMm Tool enabled state.
     * Called on each card activation (via isValidSystemCode hook).
     * Defaults to `true` if the provider is not yet available.
     */
    private fun isPmmEnabled(): Boolean = runCatching {
        val ctx = Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication").invoke(null) as? Context
            ?: return@runCatching true
        val cursor = ctx.contentResolver.query(
            Uri.parse("content://org.cf0x.konamiku.pmmconfig/pmm_enabled"),
            null, null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return@runCatching it.getInt(0) == 1
            }
        }
        true
    }.getOrDefault(true)

    /** @return the Context of the current process (com.android.nfc) */
    private fun nfcContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()

    private fun hookNfcValidation(param: PackageLoadedParam) {
        runCatching {
            val cls = param.defaultClassLoader.loadClass("android.nfc.cardemulation.NfcFCardEmulation")

            hook(cls.getDeclaredMethod("isValidSystemCode", String::class.java)).intercept {
                if (!pmmLoaded) {
                    pmmLoaded = if (isPmmEnabled()) {
                        injectPmmtool(param)
                    } else {
                        Log.i("KonamikU", "pmmtool disabled by user config")
                        false
                    }
                    sendHookedBroadcast(nfcContext(), pmmLoaded)
                }
                true
            }

            hook(cls.getDeclaredMethod("isValidNfcid2", String::class.java)).intercept { true }
            Log.i("KonamikU", "Validation hooks installed")
        }.onFailure { e ->
            Log.e("KonamikU", "Validation hook fail: ${e.message}")
        }
    }

    private fun hookNfcAppForPmmtool(param: PackageLoadedParam) {
        val candidates = listOf(
            "com.android.nfc.NfcApplication",
            "com.miui.nfc.MiuiNfcApplication",
            "com.android.nfc.NfcService"
        )
        var hooked = false

        for (className in candidates) {
            val result = runCatching {
                val cls = param.defaultClassLoader.loadClass(className)
                hook(cls.getDeclaredMethod("onCreate")).intercept { chain ->
                    val res = chain.proceed()
                    if (!pmmLoaded) {
                        pmmLoaded = if (isPmmEnabled()) {
                            injectPmmtool(param)
                        } else {
                            Log.i("KonamikU", "pmmtool disabled by user config")
                            false
                        }
                    }
                    sendHookedBroadcast(chain.thisObject as? Context, pmmLoaded)
                    res
                }
                true
            }
            if (result.getOrDefault(false)) {
                hooked = true
                break
            }
        }

        if (!hooked) {
            runCatching {
                hook(android.app.Application::class.java.getDeclaredMethod("onCreate")).intercept { chain ->
                    val res = chain.proceed()
                    if (!pmmLoaded) {
                        pmmLoaded = if (isPmmEnabled()) {
                            injectPmmtool(param)
                        } else {
                            Log.i("KonamikU", "pmmtool disabled by user config")
                            false
                        }
                    }
                    sendHookedBroadcast(chain.thisObject as? Context, pmmLoaded)
                    res
                }
            }
        }
    }

    private fun sendHookedBroadcast(ctx: Context?, pmmOk: Boolean) {
        ctx?.sendBroadcast(
            android.content.Intent("org.cf0x.konamiku.ACTION_NFC_HOOKED")
                .setPackage("org.cf0x.konamiku")
                .putExtra("pmmtool_active", pmmOk)
        )
    }

    private fun injectPmmtool(param: PackageLoadedParam): Boolean = runCatching {
        val soPath = getModuleApplicationInfo().nativeLibraryDir + "/libpmm.so"
        Runtime::class.java.getDeclaredMethod(
            "nativeLoad", String::class.java, ClassLoader::class.java
        ).apply {
            isAccessible = true
            invoke(Runtime.getRuntime(), soPath, param.defaultClassLoader)
        }
        Log.i("KonamikU", "pmmtool injected")
        true
    }.getOrElse { false }
}
