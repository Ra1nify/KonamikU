package org.cf0x.konamiku.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
        return ComponentName(context.packageName, "org.cf0x.konamiku.nfc.DummyPaymentService").flattenToString()
    }

    data class PaymentAppInfo(val label: String, val componentName: String, val packageName: String)

    fun getAvailablePaymentApps(context: Context): List<PaymentAppInfo> {
        val pm = context.packageManager
        val intent = Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE")
        val services = pm.queryIntentServices(intent, 0)
        return services.mapNotNull { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
            val packageName = serviceInfo.packageName
            if (packageName == context.packageName) return@mapNotNull null
            
            val label = serviceInfo.loadLabel(pm).toString()
            val componentName = ComponentName(packageName, serviceInfo.name).flattenToString()
            PaymentAppInfo(label, componentName, packageName)
        }.sortedBy { it.label }
    }

    suspend fun isComponentValid(context: Context, componentName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cn = ComponentName.unflattenFromString(componentName) ?: return@runCatching false
            // Use pm query via root to be extra sure or just standard PM if possible
            val intent = Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE")
            intent.setComponent(cn)
            val services = context.packageManager.queryIntentServices(intent, 0)
            services.isNotEmpty()
        }.getOrDefault(false)
    }
}
