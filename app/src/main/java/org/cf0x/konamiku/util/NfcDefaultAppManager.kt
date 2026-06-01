package org.cf0x.konamiku.util

import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NfcDefaultAppManager {
    private const val SETTING_KEY = "nfc_payment_default_component"

    suspend fun getCurrentDefault(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("settings", "get", "secure", SETTING_KEY))
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (out == "null" || out.isEmpty()) null else out
        }.getOrNull()
    }

    suspend fun setDefault(component: String?): Boolean = withContext(Dispatchers.IO) {
        if (component.isNullOrBlank() || component == "null") {
            // If we try to set null, maybe it's better to just delete the key
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings delete secure $SETTING_KEY")).waitFor() == 0
            }.getOrDefault(false)
        } else {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure $SETTING_KEY $component")).waitFor() == 0
            }.getOrDefault(false)
        }
    }

    fun isOurComponent(context: Context, component: String?): Boolean {
        if (component == null) return false
        val our = getOurComponent(context)
        return component.contains(context.packageName) || component == our
    }

    fun getOurComponent(context: Context): String {
        return ComponentName(context, "org.cf0x.konamiku.nfc.DummyPaymentService").flattenToString()
    }
}
