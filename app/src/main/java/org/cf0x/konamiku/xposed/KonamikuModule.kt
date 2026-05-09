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

        Log.i("KonamikU", ">>> onPackageLoaded: com.android.nfc pid=${android.os.Process.myPid()}")

        hookNfcValidation(param)
        hookNfcAppForPmmtool(param)
    }

    private fun hookNfcValidation(param: PackageLoadedParam) {
        runCatching {
            val cls = param.defaultClassLoader
                .loadClass("android.nfc.cardemulation.NfcFCardEmulation")

            val isValidSystemCode = cls.getDeclaredMethod("isValidSystemCode", String::class.java)
            hook(isValidSystemCode).intercept { _ -> true }

            val isValidNfcid2 = cls.getDeclaredMethod("isValidNfcid2", String::class.java)
            hook(isValidNfcid2).intercept { _ -> true }

            Log.i("KonamikU", "NFC validation hooks installed OK")
        }.onFailure { e ->
            Log.e("KonamikU", "hook failed: ${e.message}")
        }
    }

    private fun hookNfcAppForPmmtool(param: PackageLoadedParam) {
        // Try known NFC Application class names — MIUI may use a different one
        val candidates = listOf(
            "com.android.nfc.NfcApplication",
            "com.miui.nfc.MiuiNfcApplication",
            "com.android.nfc.NfcService",       // some ROMs embed init here
        )

        var hooked = false
        for (className in candidates) {
            val result = runCatching {
                val cls     = param.defaultClassLoader.loadClass(className)
                val onCreate = cls.getDeclaredMethod("onCreate")
                hook(onCreate).intercept { chain ->
                    Log.i("KonamikU", ">>> $className.onCreate() fired")
                    val result = chain.proceed()
                    val pmmOk  = injectPmmtool(param)
                    sendHookedBroadcast(chain.thisObject as? android.content.Context, pmmOk)
                    result
                }
                Log.i("KonamikU", "hooked $className.onCreate OK")
                true
            }
            if (result.getOrDefault(false)) {
                hooked = true
                break
            } else {
                Log.d("KonamikU", "class not found or hook failed: $className — ${result.exceptionOrNull()?.message}")
            }
        }

        if (!hooked) {
            // Last resort: hook android.app.Application.onCreate scoped to this process
            Log.w("KonamikU", "all NfcApplication candidates failed — hooking Application.onCreate as fallback")
            runCatching {
                val appClass = android.app.Application::class.java
                val onCreate = appClass.getDeclaredMethod("onCreate")
                hook(onCreate).intercept { chain ->
                    Log.i("KonamikU", ">>> Application.onCreate fired (fallback), class=${chain.thisObject?.javaClass?.name}")
                    val result = chain.proceed()
                    val pmmOk  = injectPmmtool(param)
                    sendHookedBroadcast(chain.thisObject as? android.content.Context, pmmOk)
                    result
                }
                Log.i("KonamikU", "fallback Application.onCreate hook installed")
            }.onFailure { e ->
                Log.e("KonamikU", "fallback hook also failed: ${e.message}")
            }
        }
    }

    private fun sendHookedBroadcast(ctx: android.content.Context?, pmmOk: Boolean) {
        if (ctx == null) {
            Log.w("KonamikU", "sendHookedBroadcast: context is null")
            return
        }
        runCatching {
            ctx.sendBroadcast(
                android.content.Intent("org.cf0x.konamiku.ACTION_NFC_HOOKED")
                    .setPackage("org.cf0x.konamiku")
                    .putExtra("pmmtool_active", pmmOk)
            )
            Log.i("KonamikU", "NFC_HOOKED broadcast sent (pmmOk=$pmmOk)")
        }.onFailure { e ->
            Log.w("KonamikU", "broadcast failed: ${e.message}")
        }
    }

    private fun injectPmmtool(param: PackageLoadedParam): Boolean {
        return runCatching {
            val soPath = getModuleApplicationInfo().nativeLibraryDir + "/libpmm.so"
            Runtime::class.java
                .getDeclaredMethod("nativeLoad", String::class.java, ClassLoader::class.java)
                .also { it.isAccessible = true }
                .invoke(Runtime.getRuntime(), soPath, param.defaultClassLoader)
            Log.i("KonamikU", "pmmtool injected from $soPath")
            true
        }.getOrElse { e ->
            Log.e("KonamikU", "pmmtool inject failed: ${e.message}")
            false
        }
    }
}