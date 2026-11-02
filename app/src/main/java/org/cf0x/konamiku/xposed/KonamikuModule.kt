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

    /** True once libpmm.so was loaded and the hook was attempted at least once. */
    @Volatile
    private var pmmInjected = false

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
            // Loaded from the boot class path; resolve through the same loader
            // the framework uses so we hook the instance the server calls.
            val cls = Class.forName(
                "android.nfc.cardemulation.NfcFCardEmulation",
                false,
                param.defaultClassLoader
            )
            Log.i("KonamikU", "validation hook: class loader=${cls.classLoader}")

            hook(cls.getDeclaredMethod("isValidSystemCode", String::class.java)).intercept { chain ->
                val arg = chain.args.firstOrNull() as? String
                Log.i("KonamikU", "isValidSystemCode($arg) -> true (hooked)")
                val wasLoaded = pmmLoaded
                ensurePmm(param)
                if (pmmLoaded != wasLoaded) {
                    sendHookedBroadcast(nfcContext(), pmmLoaded)
                }
                true
            }

            hook(cls.getDeclaredMethod("isValidNfcid2", String::class.java)).intercept { chain ->
                Log.i("KonamikU", "isValidNfcid2(${chain.args.firstOrNull()}) -> true (hooked)")
                true
            }
            Log.i("KonamikU", "Validation hooks installed")
        }.onFailure { e ->
            Log.e("KonamikU", "Validation hook fail: ${e.javaClass.simpleName}: ${e.message}")
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
                    ensurePmm(param)
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
                    ensurePmm(param)
                    sendHookedBroadcast(chain.thisObject as? Context, pmmLoaded)
                    res
                }
            }
        }
    }

    /**
     * Makes sure libpmm.so is loaded and its hook is installed, retrying the
     * hook on later calls when the first attempt found the NCI stack missing.
     *
     * Safe and cheap to call repeatedly: once [pmmLoaded] is true this returns
     * immediately, and after a failed attempt it only re-resolves the symbol.
     */
    private fun ensurePmm(param: PackageLoadedParam) {
        if (pmmLoaded) return

        pmmLoaded = if (!pmmInjected) {
            pmmInjected = true
            val enabled = isPmmEnabled()
            Log.i("KonamikU", "ensurePmm: pmmEnabled=$enabled")
            if (enabled) {
                // The library loading and the hook install are separate
                // outcomes: loading may succeed while the target symbol is not
                // yet resolvable, in which case later calls retry the hook.
                injectPmmtool(param)
                tryInstallPmmHook()
            } else {
                Log.i("KonamikU", "pmmtool disabled by user config")
                false
            }
        } else {
            tryInstallPmmHook().also {
                if (it) Log.i("KonamikU", "pmmtool hook went live on retry")
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

    /**
     * Loads libpmm.so and asks it whether the hook actually took.
     *
     * `nativeLoad` only reports that the library was dlopen'd — JNI_OnLoad
     * still succeeds when the target symbol is missing. Reporting `true` on
     * dlopen alone made a dead hook look active in the UI, so we trust only
     * [nativePmmHooked].
     *
     * The NCI stack is loaded lazily: on Android 14+ it lives in
     * libnfc_nci_jni.so, which the NFC process may not have mapped yet at the
     * moment we inject. A first miss is therefore not fatal - the next
     * [tryInstallPmmHook] re-resolves once the library is present.
     */
    private fun injectPmmtool(param: PackageLoadedParam): Boolean {
        val soPath = runCatching {
            getModuleApplicationInfo()?.nativeLibraryDir
        }.getOrNull()
        Log.i("KonamikU", "injectPmmtool: module nativeLibraryDir=$soPath")
        if (soPath.isNullOrBlank()) {
            Log.e("KonamikU", "injectPmmtool: cannot resolve the module's native library dir")
            return false
        }

        val fullPath = "$soPath/libpmm.so"
        // Do NOT test for the file's existence.
        //
        // The APK is built with extractNativeLibs=false, so libpmm.so is never
        // unpacked into nativeLibraryDir - it stays inside the (uncompressed)
        // APK and is mapped straight from there. Runtime.nativeLoad() can load
        // such a library; a File.exists() guard cannot see it and would refuse
        // to load a library that is in fact available.
        Log.i("KonamikU", "injectPmmtool: loading $fullPath")

        // Load against *this* module's class loader.
        //
        // Runtime.nativeLoad(path, param.defaultClassLoader) binds the library
        // to the target app's loader, but KonamikuModule itself is defined by
        // the Xposed module loader, so the natives registered for this class
        // are not found and every external fun throws:
        //   "No implementation found for int
        //    org.cf0x.konamiku.xposed.KonamikuModule.nativePmmHooked()
        //    - is the library loaded, e.g. System.loadLibrary?"
        // nativeLoad is tried first because it resolves the APK-embedded path;
        // System.load is the fallback for builds that do extract.
        val nativeError = runCatching {
            Runtime::class.java.getDeclaredMethod(
                "nativeLoad", String::class.java, ClassLoader::class.java
            ).apply {
                isAccessible = true
                invoke(Runtime.getRuntime(), fullPath, param.defaultClassLoader)
            }
        }.exceptionOrNull()?.let { it.cause ?: it }

        if (nativeError == null) {
            Log.i("KonamikU", "pmmtool library loaded via nativeLoad: $fullPath")
        } else {
            Log.w(
                "KonamikU",
                "injectPmmtool: nativeLoad failed (${nativeError.javaClass.simpleName}: " +
                    "${nativeError.message}); trying System.load"
            )
            val directError = runCatching { System.load(fullPath) }.exceptionOrNull()
            if (directError != null) {
                Log.e(
                    "KonamikU",
                    "injectPmmtool: could not load libpmm.so: " +
                        "${directError.javaClass.simpleName}: ${directError.message}"
                )
                return false
            }
            Log.i("KonamikU", "pmmtool library loaded via System.load: $fullPath")
        }

        // The load itself is what this function reports, exactly as the
        // original did. Hook state is queried separately: letting that query
        // decide the result conflated "JNI method missing" with "library did
        // not load", and retries would then never re-resolve the symbol.
        runCatching { nativePmmHooked() == 1 }.onSuccess { hooked ->
            if (hooked) {
                Log.i("KonamikU", "pmmtool hook installed")
            } else {
                Log.w("KonamikU", "pmmtool loaded but hook not installed yet (will retry)")
            }
        }.onFailure { e ->
            Log.e(
                "KonamikU",
                "injectPmmtool: nativePmmHooked unavailable: " +
                    "${e.javaClass.simpleName}: ${e.message}"
            )
        }
        return true
    }

    /**
     * Retries the hook once the library should be loaded, returning whether it
     * is now live. Called on each card activation, so a device that only maps
     * the NCI stack on first use still ends up hooked.
     */
    private fun tryInstallPmmHook(): Boolean = runCatching {
        nativePmmHooked() == 1
    }.getOrDefault(false)

    /** Implemented in bridge.c; returns 1 when DobbyHook succeeded. */
    private external fun nativePmmHooked(): Int
}
