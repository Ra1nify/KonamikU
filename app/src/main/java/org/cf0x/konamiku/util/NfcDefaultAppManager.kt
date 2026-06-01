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
        val cmd = if (component == null) {
            "settings delete secure $SETTING_KEY"
        } else {
            "settings put secure $SETTING_KEY $component"
        }
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor() == 0
        }.getOrDefault(false)
    }

    fun getOurComponent(context: Context): String {
        return ComponentName(context, "org.cf0x.konamiku.nfc.DummyPaymentService").flattenToString()
    }
}
