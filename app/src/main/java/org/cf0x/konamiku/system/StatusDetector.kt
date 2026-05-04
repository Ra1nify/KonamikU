package org.cf0x.konamiku.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cf0x.konamiku.nfc.EmuCard
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

object StatusDetector {

    data class RootStatus(val available: Boolean, val provider: String)

    data class AdbStatus(val active: Boolean, val authorized: Boolean, val provider: String) {
        val available: Boolean get() = active && authorized
    }

    data class DeviceOwnerStatus(val isOwner: Boolean, val provider: String)
    data class NfcStatus(
        val rfEnabled: Boolean,
        val hcefSupported: Boolean,
        val defaultPaymentIsUs: Boolean,
        val defaultPaymentLabel: String
    )

    data class XposedStatus(val active: Boolean, val needsRestart: Boolean, val provider: String, val pmmActive: Boolean)

    data class AllStatus(
        val root: RootStatus,
        val adb: AdbStatus,
        val deviceOwner: DeviceOwnerStatus,
        val nfc: NfcStatus,
        val xposed: XposedStatus
    ) {
        val isPrivileged: Boolean get() = root.available || adb.available
    }

    suspend fun detectAll(context: Context): AllStatus = withContext(Dispatchers.IO) {
        AllStatus(
            root        = detectRoot(),
            adb         = detectAdb(),
            deviceOwner = detectDeviceOwner(context),
            nfc         = detectNfc(context),
            xposed      = detectXposed()
        )
    }

    fun detectRoot(): RootStatus = when {
        isMagisk()    -> RootStatus(true, "Magisk ${magiskVersion()}")
        isKernelSU()  -> RootStatus(true, "KernelSU ${kernelSuVersion()}")
        isAPatch()    -> RootStatus(true, "APatch")
        hasSuBinary() -> RootStatus(true, "su (unknown)")
        else          -> RootStatus(false, "")
    }

    fun requestRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root_granted"))
            val out = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            out == "root_granted"
        } catch (e: Exception) {
            false
        }
    }

    private fun isMagisk()    = runCommand("magisk -c") != null
    private fun isKernelSU()  = runCommand("ksud --version") != null
    private fun isAPatch()    = runCommand("apd --version") != null
    private fun hasSuBinary() = runCommand("which su")?.isNotEmpty() == true

    private fun magiskVersion() =
        runCommand("magisk -v")?.substringBefore(":")?.trim() ?: ""

    private fun kernelSuVersion() =
        runCommand("ksud --version")?.trim()?.substringAfterLast(" ") ?: ""

    private fun runCommand(cmd: String): String? = runCatching {
        val p   = Runtime.getRuntime().exec(cmd)
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        if (p.exitValue() == 0 && out.isNotEmpty()) out else null
    }.getOrNull()

    fun detectAdb(): AdbStatus {
        return AdbStatus(active = false, authorized = false, provider = "")
    }
    fun detectDeviceOwner(context: Context): DeviceOwnerStatus {
        val dpm     = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
        val isOwner = dpm.isDeviceOwnerApp(context.packageName)
        if (!isOwner) return DeviceOwnerStatus(false, "")
        val provider = runCatching {
            val info = context.packageManager.getPackageInfo("com.android.dhizuku", 0)
            "Dhizuku ${info.versionName}"
        }.getOrDefault("Manual (ADB)")
        return DeviceOwnerStatus(true, provider)
    }

    fun detectNfc(context: Context): NfcStatus {
        val adapter      = NfcAdapter.getDefaultAdapter(context)
        val rfEnabled    = adapter?.isEnabled ?: false
        val hcefSupported = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF
        )

        val cardEmulation = runCatching { CardEmulation.getInstance(adapter) }.getOrNull()
        val ourComponent  = ComponentName(context, EmuCard::class.java)

        val defaultIsUs = runCatching {
            cardEmulation?.isDefaultServiceForCategory(
                ourComponent, CardEmulation.CATEGORY_PAYMENT
            ) ?: false
        }.getOrDefault(false)

        val defaultLabel = if (defaultIsUs) "KonamikU" else {
            runCatching {
                val m  = cardEmulation?.javaClass
                    ?.getMethod("getDefaultServiceForCategory", String::class.java)
                val cn = m?.invoke(cardEmulation, CardEmulation.CATEGORY_PAYMENT)
                        as? ComponentName
                cn?.packageName ?: "None"
            }.getOrDefault("Unknown")
        }

        return NfcStatus(rfEnabled, hcefSupported, defaultIsUs, defaultLabel)
    }

    fun detectXposed(): XposedStatus {
        val state        = XposedState.activationState
        val isActive     = state == XposedActivationState.ACTIVE
        val needsRestart = state == XposedActivationState.NEEDS_RESTART

        val provider = when (state) {
            XposedActivationState.INACTIVE      -> ""
            XposedActivationState.NEEDS_RESTART -> "${XposedState.frameworkName} ${XposedState.frameworkVersion}".trim()
            XposedActivationState.ACTIVE        -> "${XposedState.frameworkName} ${XposedState.frameworkVersion}".trim()
        }

        val pmmActive = runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
                .invoke(null, "debug.konamiku.pmmtool", "0") == "1"
        }.getOrDefault(false)

        return XposedStatus(isActive, needsRestart, provider, pmmActive)
    }
}