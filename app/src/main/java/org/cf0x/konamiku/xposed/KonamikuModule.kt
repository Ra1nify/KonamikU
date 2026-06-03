package org.cf0x.konamiku.xposed

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

    private var pmmLoaded = false

    private fun hookNfcValidation(param: PackageLoadedParam) {
        runCatching {
            val cls = param.defaultClassLoader.loadClass("android.nfc.cardemulation.NfcFCardEmulation")

            hook(cls.getDeclaredMethod("isValidSystemCode", String::class.java)).intercept {
                if (!pmmLoaded) {
                    pmmLoaded = injectPmmtool(param)
                    val ctx = Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null) as? android.content.Context
                    sendHookedBroadcast(ctx, pmmLoaded)
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
        val candidates = listOf("com.android.nfc.NfcApplication", "com.miui.nfc.MiuiNfcApplication", "com.android.nfc.NfcService")
        var hooked = false
        
        for (className in candidates) {
            val result = runCatching {
                val cls = param.defaultClassLoader.loadClass(className)
                hook(cls.getDeclaredMethod("onCreate")).intercept { chain ->
                    val res = chain.proceed()
                    if (!pmmLoaded) pmmLoaded = injectPmmtool(param)
                    sendHookedBroadcast(chain.thisObject as? android.content.Context, pmmLoaded)
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
                    if (!pmmLoaded) pmmLoaded = injectPmmtool(param)
                    sendHookedBroadcast(chain.thisObject as? android.content.Context, pmmLoaded)
                    res
                }
            }
        }
    }

    private fun sendHookedBroadcast(ctx: android.content.Context?, pmmOk: Boolean) {
        ctx?.sendBroadcast(android.content.Intent("org.cf0x.konamiku.ACTION_NFC_HOOKED")
            .setPackage("org.cf0x.konamiku").putExtra("pmmtool_active", pmmOk))
    }

    private fun injectPmmtool(param: PackageLoadedParam): Boolean = runCatching {
        val soPath = getModuleApplicationInfo().nativeLibraryDir + "/libpmm.so"
        Runtime::class.java.getDeclaredMethod("nativeLoad", String::class.java, ClassLoader::class.java).apply {
            isAccessible = true
            invoke(Runtime.getRuntime(), soPath, param.defaultClassLoader)
        }
        Log.i("KonamikU", "pmmtool injected")
        true
    }.getOrElse { false }
}
