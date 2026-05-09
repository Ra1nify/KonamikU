package org.cf0x.konamiku.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class KonamikuModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        // onModuleLoaded fires in every process — do NOT set nfc_hooked here
        Log.i("KonamikU", "module loaded in ${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != "com.android.nfc") return

        hookNfcValidation(param)

        // nfc_hooked is set here — only when we're actually in the NFC process
        runCatching {
            getRemotePreferences("KonamikU").edit()
                .putBoolean("nfc_hooked", true)
                .putString("framework_name", getFrameworkName())
                .putString("framework_version", getFrameworkVersion())
                .apply()
        }.onFailure { e ->
            Log.e("KonamikU", "failed to write hook flag: ${e.message}")
        }

        val prefs = getRemotePreferences("KonamikU")
        if (prefs.getBoolean("load_pmmtool", true)) {
            hookNfcAppForPmmtool(param)
        }
    }

    private fun hookNfcValidation(param: PackageLoadedParam) {
        runCatching {
            // Bug fix: correct package is android.nfc.cardemulation, not android.nfc
            val cls = param.defaultClassLoader
                .loadClass("android.nfc.cardemulation.NfcFCardEmulation")

            // Bug fix: isValidSystemCode takes String, not Int
            val isValidSystemCode = cls.getDeclaredMethod("isValidSystemCode", String::class.java)
            hook(isValidSystemCode).intercept { _ ->
                true // skip original, always allow any system code (e.g. 88B4)
            }

            val isValidNfcid2 = cls.getDeclaredMethod("isValidNfcid2", String::class.java)
            hook(isValidNfcid2).intercept { _ ->
                true // skip original, allow any NFCID2 prefix (e.g. 012E)
            }

            Log.i("KonamikU", "NFC validation hooks installed")
        }.onFailure { e ->
            Log.e("KonamikU", "hook failed: ${e.message}")
        }
    }

    private fun hookNfcAppForPmmtool(param: PackageLoadedParam) {
        runCatching {
            val nfcAppClass = param.defaultClassLoader
                .loadClass("com.android.nfc.NfcApplication")
            val onCreate = nfcAppClass.getDeclaredMethod("onCreate")
            hook(onCreate).intercept { chain ->
                val result = chain.proceed()
                injectPmmtool(param)
                // Notify the app that NFC process is alive and hooked.
                // getRemotePreferences is read-only in hooked processes, so we use a broadcast.
                runCatching {
                    val ctx = chain.getThisObject() as android.content.Context
                    ctx.sendBroadcast(
                        android.content.Intent("org.cf0x.konamiku.ACTION_NFC_HOOKED")
                            .setPackage("org.cf0x.konamiku")
                    )
                    Log.i("KonamikU", "NFC_HOOKED broadcast sent")
                }.onFailure { e ->
                    Log.w("KonamikU", "broadcast failed: ${e.message}")
                }
                result
            }
        }.onFailure { e ->
            Log.e("KonamikU", "NfcApplication hook failed: ${e.message}")
        }
    }

    private fun injectPmmtool(param: PackageLoadedParam) {
        runCatching {
            val soPath = getModuleApplicationInfo().nativeLibraryDir + "/libpmm.so"
            Runtime::class.java
                .getDeclaredMethod("nativeLoad", String::class.java, ClassLoader::class.java)
                .also { it.isAccessible = true }
                .invoke(Runtime.getRuntime(), soPath, param.defaultClassLoader)
            Log.i("KonamikU", "pmmtool injected from $soPath")
        }.onFailure { e ->
            Log.e("KonamikU", "pmmtool inject failed: ${e.message}")
        }
    }
}