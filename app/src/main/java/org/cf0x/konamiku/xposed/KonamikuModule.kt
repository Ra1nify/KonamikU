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

    /** Current hook state; published to the app via the hooked broadcast. */
    @Volatile
    var pmmState: PmmState = PmmState.NOT_ATTEMPTED
        private set

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
                val before = pmmState
                ensurePmm(param)
                // Report only on a state transition, not on every card tap.
                if (pmmState != before) {
                    sendHookedBroadcast(nfcContext(), pmmState == PmmState.INSTALLED)
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
                    sendHookedBroadcast(chain.thisObject as? Context, pmmState == PmmState.INSTALLED)
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
                    sendHookedBroadcast(chain.thisObject as? Context, pmmState == PmmState.INSTALLED)
                    res
                }
            }
        }
    }

    /**
     * Hook state as reported by libpmm.so. Values mirror the PMM_STATE_*
     * constants in bridge.c; changing either side requires changing both.
     *
     * These are deliberately not collapsed into a boolean: WAITING_NCI clears
     * itself on the next tag contact, NO_SYMBOL never will, and the UI has to
     * say which one the user is looking at.
     */
    enum class PmmState(val code: Int) {
        /** libpmm.so has not been loaded yet. */
        NOT_ATTEMPTED(0),
        /** Hook is live. */
        INSTALLED(1),
        /** Loader has no NCI stack mapped yet; retries resolve this. */
        WAITING_NCI(2),
        /** Both known libraries were searched and neither exports the symbol. */
        NO_SYMBOL(3),
        /** Symbol resolved but DobbyHook refused. */
        HOOK_FAILED(4);

        companion object {
            fun fromCode(code: Int): PmmState =
                entries.firstOrNull { it.code == code } ?: NOT_ATTEMPTED
        }
    }

    /**
     * Makes sure libpmm.so is loaded and its hook is installed, retrying the
     * hook on later calls when the first attempt found the NCI stack missing.
     *
     * Safe and cheap to call repeatedly: once INSTALLED this returns
     * immediately, and after a temporary miss it only re-resolves the symbol.
     */
    private fun ensurePmm(param: PackageLoadedParam) {
        if (pmmState == PmmState.INSTALLED) return

        pmmState = if (!pmmInjected) {
            pmmInjected = true
            val enabled = isPmmEnabled()
            Log.i("KonamikU", "ensurePmm: pmmEnabled=$enabled")
            if (enabled) {
                // Library loading and hook install are separate outcomes:
                // loading may succeed while the symbol is not yet resolvable.
                injectPmmtool(param)
                tryInstallPmmHook()
            } else {
                Log.i("KonamikU", "pmmtool disabled by user config")
                PmmState.NOT_ATTEMPTED
            }
        } else {
            tryInstallPmmHook().also { state ->
                if (state == PmmState.INSTALLED) {
                    Log.i("KonamikU", "pmmtool hook went live on retry")
                }
            }
        }
    }

    private fun sendHookedBroadcast(ctx: Context?, pmmOk: Boolean) {
        ctx?.sendBroadcast(
            android.content.Intent("org.cf0x.konamiku.ACTION_NFC_HOOKED")
                .setPackage("org.cf0x.konamiku")
                .putExtra("pmmtool_active", pmmOk)
                .putExtra("pmmtool_state", pmmState.code)
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

        // The load itself is what this function reports. Hook state is queried
        // separately: letting that query decide the result conflated "JNI
        // method missing" with "library did not load", and retries would then
        // never re-resolve the symbol.
        runCatching { nativePmmHooked() }.onSuccess { code ->
            Log.i("KonamikU", "pmmtool state after load: ${PmmState.fromCode(code)}")
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
     * Retries the hook once the library should be loaded. Called on each card
     * activation, so a device that only maps the NCI stack on first use still
     * ends up hooked; WAITING_NCI is the normal answer until then.
     */
    private fun tryInstallPmmHook(): PmmState = runCatching {
        PmmState.fromCode(nativePmmHooked())
    }.getOrDefault(PmmState.NOT_ATTEMPTED)

    /** Implemented in bridge.c; returns a PMM_STATE_* code. */
    private external fun nativePmmHooked(): Int
}
