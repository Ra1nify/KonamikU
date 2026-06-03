package org.cf0x.konamiku.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    fun getAppLabel(context: Context, componentName: String): String {
        return runCatching {
            val cn = ComponentName.unflattenFromString(componentName) ?: return componentName
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(cn.packageName, 0)
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(componentName)
    }

    fun getOurComponent(context: Context): String {
        return ComponentName(context.packageName, "org.cf0x.konamiku.nfc.DummyPaymentService").flattenToString()
    }

    data class PaymentAppInfo(val label: String, val componentName: String, val packageName: String)

    suspend fun getAvailablePaymentApps(context: Context): List<PaymentAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        // No filter: list all installed applications
        val packages = pm.getInstalledPackages(0)
        
        packages.mapNotNull { pkg ->
            val packageName = pkg.packageName
            if (packageName == context.packageName) return@mapNotNull null
            
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val label = appInfo.loadLabel(pm).toString()
            
            // For general apps, try to find a default HCE service if it exists to make it a valid component.
            // Some systems require the full component name for nfc_payment_default_component.
            val intent = Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE")
            intent.setPackage(packageName)
            val services = pm.queryIntentServices(intent, 0)
            
            val componentName = if (services.isNotEmpty()) {
                ComponentName(packageName, services[0].serviceInfo.name).flattenToString()
            } else {
                // Fallback to a common naming convention or just package name
                // Note: Settings put secure usually expects pkg/class.
                packageName
            }
            
            PaymentAppInfo(label, componentName, packageName)
        }.distinctBy { it.packageName }.sortedBy { it.label }
    }

    suspend fun isComponentValid(context: Context, componentName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!componentName.contains("/")) {
                context.packageManager.getPackageInfo(componentName, 0) != null
            } else {
                val cn = ComponentName.unflattenFromString(componentName) ?: return@runCatching false
                context.packageManager.getServiceInfo(cn, 0).isEnabled
            }
        }.getOrDefault(false)
    }
}
