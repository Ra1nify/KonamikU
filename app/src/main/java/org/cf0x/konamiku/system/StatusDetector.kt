package org.cf0x.konamiku.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.cf0x.konamiku.nfc.EmuCard
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

object StatusDetector {

    enum class RootProvider { UNKNOWN, MAGISK, KERNELSU, APATCH }

    data class RootStatus(
        val available: Boolean,
        val provider: RootProvider = RootProvider.UNKNOWN
    )

    data class NfcStatus(
        val rfEnabled: Boolean,
        val hcefSupported: Boolean,
        val defaultPaymentIsUs: Boolean,
        val defaultPaymentLabel: String
    )

    data class XposedStatus(
        val active: Boolean,
        val needsRestart: Boolean,
        val provider: String,
        val pmmActive: Boolean
    )

    data class AllStatus(
        val nfc: NfcStatus,
        val xposed: XposedStatus,
        val root: RootStatus = RootStatus(available = false)
    )

    suspend fun detectAll(context: Context): AllStatus = withContext(Dispatchers.IO) {
        coroutineScope {
            val nfcDeferred    = async { detectNfc(context) }
            val xposedDeferred = async { detectXposed() }
            val rootDeferred   = async { detectRoot() }
            AllStatus(
                nfc    = nfcDeferred.await(),
                xposed = xposedDeferred.await(),
                root   = rootDeferred.await()
            )
        }
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
                val cn = m?.invoke(cardEmulation, CardEmulation.CATEGORY_PAYMENT) as? ComponentName
                cn?.packageName ?: "None"
            }.getOrDefault("Unknown")
        }
        return NfcStatus(rfEnabled, hcefSupported, defaultIsUs, defaultLabel)
    }

    fun detectXposed(): XposedStatus {
        val state = XposedState.activationState
        val provider = when (state) {
            XposedActivationState.INACTIVE      -> ""
            XposedActivationState.NEEDS_RESTART,
            XposedActivationState.ACTIVE        ->
                "${XposedState.frameworkName} ${XposedState.frameworkVersion}".trim()
        }
        return XposedStatus(
            active       = state == XposedActivationState.ACTIVE,
            needsRestart = state == XposedActivationState.NEEDS_RESTART,
            provider     = provider,
            pmmActive    = XposedState.pmmActive
        )
    }

    suspend fun detectRoot(): RootStatus = withContext(Dispatchers.IO) {
        coroutineScope {
            val availableDeferred = async { checkSuAvailable() }
            val dirDeferred       = async { checkAdbDirectories() }
            val procDeferred      = async { checkDaemonProcesses() }

            val available = availableDeferred.await()
            val dirResult  = dirDeferred.await()
            val procResult = procDeferred.await()

            // Cross-validate: directory check is primary, process is fallback.
            // If both agree → confident match; if one is UNKNOWN → use the other.
            val provider = when {
                dirResult != RootProvider.UNKNOWN && dirResult == procResult -> dirResult
                dirResult  != RootProvider.UNKNOWN                          -> dirResult
                procResult != RootProvider.UNKNOWN                          -> procResult
                else                                                         -> RootProvider.UNKNOWN
            }

            RootStatus(available = available, provider = provider)
        }
    }

    /**
     * Checks /data/adb/ subdirectories to identify the root provider.
     * Magisk → "magisk/", KernelSU → "ksu/", APatch → "ap/".
     * Requires root — run via su.
     */
    private fun checkAdbDirectories(): RootProvider = runCatching {
        val proc = Runtime.getRuntime()
            .exec(arrayOf("su", "-c", "ls /data/adb/"))
        val lines = proc.inputStream.bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        proc.waitFor()

        when {
            "magisk" in lines -> RootProvider.MAGISK
            "ksu"    in lines -> RootProvider.KERNELSU
            "ap"     in lines -> RootProvider.APATCH
            else              -> RootProvider.UNKNOWN
        }
    }.getOrDefault(RootProvider.UNKNOWN)

    /**
     * Cross-verifies root provider by checking if the corresponding daemon is alive.
     * Uses pgrep -x for exact process name match via su.
     *   magiskd  → Magisk
     *   ksud     → KernelSU
     *   apd      → APatch
     */
    private fun checkDaemonProcesses(): RootProvider {
        val candidates = listOf(
            "magiskd" to RootProvider.MAGISK,
            "ksud"    to RootProvider.KERNELSU,
            "apd"     to RootProvider.APATCH,
        )
        for ((daemon, provider) in candidates) {
            val alive = runCatching {
                val proc = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "pgrep -x $daemon"))
                val out = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                out.isNotBlank()
            }.getOrDefault(false)
            if (alive) return provider
        }
        return RootProvider.UNKNOWN
    }

    private fun checkSuAvailable(): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val output  = process.inputStream.bufferedReader().readLine() ?: ""
        process.waitFor() == 0 && "uid=0" in output
    }.getOrDefault(false)
}