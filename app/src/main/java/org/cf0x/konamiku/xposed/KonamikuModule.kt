package org.cf0x.konamiku.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class KonamikuModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        runCatching {
            getRemotePreferences("KonamikU").edit()
                .putBoolean("nfc_hooked", true)
                .putString("framework_name", getFrameworkName())
                .putString("framework_version", getFrameworkVersion())
                .apply()
        }.onFailure { e ->
            Log.e("KonamikU", "failed to write hook flag: ${e.message}")
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != "com.android.nfc") return
        hookNfcValidation(param)
        val prefs = getRemotePreferences("KonamikU")
        if (prefs.getBoolean("load_pmmtool", true)) injectPmmtool(param)
    }

    private fun hookNfcValidation(param: PackageLoadedParam) {
        runCatching {
            val cls = param.defaultClassLoader.loadClass("android.nfc.NfcFCardEmulation")

            hook(cls.getDeclaredMethod("isValidSystemCode", Int::class.java))
                .intercept(object : Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        return true
                    }
                })

            hook(cls.getDeclaredMethod("isValidNfcid2", String::class.java))
                .intercept(object : Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        return true
                    }
                })
        }.onFailure { e ->
            Log.e("KonamikU", "hook failed: ${e.message}")
        }
    }

    private fun injectPmmtool(param: PackageLoadedParam) {
        runCatching {
            val soPath = getModuleApplicationInfo().nativeLibraryDir + "/libpmm.so"
            Runtime::class.java
                .getDeclaredMethod("nativeLoad", String::class.java, ClassLoader::class.java)
                .also { it.isAccessible = true }
                .invoke(Runtime.getRuntime(), soPath, param.defaultClassLoader)
            Log.i("KonamikU", "pmmtool injected")
        }.onFailure { e ->
            Log.e("KonamikU", "pmmtool inject failed: ${e.message}")
        }
    }
}