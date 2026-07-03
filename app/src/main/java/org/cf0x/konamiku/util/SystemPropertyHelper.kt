package org.cf0x.konamiku.util

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log

/**
 * Read-only access to Android system properties via reflection.
 *
 * This calls `android.os.SystemProperties.get(String)` which is a hidden API
 * on Android 9+.  The reflection works because the method still exists in the
 * runtime even though it is not part of the public SDK.
 *
 * This is useful for diagnostics — for example reading NFC-related system
 * properties or identifying vendor-specific builds.
 */
object SystemPropertyHelper {

    private const val TAG = "KonamikU-SysProp"

    private const val SYSTEM_PROPERTIES_CLASS = "android.os.SystemProperties"

    // Cache the reflected method so we only look it up once.
    private val getMethod by lazy {
        runCatching {
            val clazz = Class.forName(SYSTEM_PROPERTIES_CLASS)
            @Suppress("PrivateApi")
            val method = clazz.getDeclaredMethod("get", String::class.java)
            method.isAccessible = true
            method
        }.onFailure {
            Log.w(TAG, "Cannot find SystemProperties.get: ${it.message}")
        }.getOrNull()
    }

    /**
     * Read a system property by key.
     *
     * @param key the property name, e.g. `"ro.build.version.sdk"`.
     * @param default value returned when the property is not set or the bridge fails.
     * @return the property value, or [default] on failure.
     */
    @SuppressLint("PrivateApi")
    fun get(key: String, default: String = ""): String {
        val method = getMethod ?: return default
        return runCatching {
            val clazz = Class.forName(SYSTEM_PROPERTIES_CLASS)
            method.invoke(clazz, key) as? String ?: default
        }.getOrDefault(default)
    }

    /**
     * Read a boolean system property.
     *
     * @param key the property name.
     * @param default value when unset or the bridge fails.
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean {
        val value = get(key)
        if (value.isEmpty()) return default
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    // ---- Convenience accessors ----

    /** Fingerprint / build description of the ROM. */
    val buildFingerprint: String
        get() = get("ro.build.fingerprint", "")

    /** OEM name, e.g. "Xiaomi", "Samsung", "OnePlus". */
    val oemName: String
        get() = get("ro.product.manufacturer", Build.MANUFACTURER)

    /** Device model, e.g. "M2101K9AG". */
    val deviceModel: String
        get() = get("ro.product.model", Build.MODEL)

    /** NFC vendor chipset (may be empty). */
    val nfcChipset: String
        get() = get("ro.nfc.chip", get("vendor.nfc.chip", ""))

    /** Whether the NFC HAL is in "offhost" (eSE) or "host" (HCE) mode. */
    val nfcControllerType: String
        get() = get("ro.nfc.controller", get("vendor.nfc.controller", ""))
}
