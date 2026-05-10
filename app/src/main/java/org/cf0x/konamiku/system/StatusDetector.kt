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
import java.io.File

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
            val providerDeferred  = async { scanProcForProvider() }
            val availableDeferred = async { checkSuAvailable() }
            RootStatus(
                available = availableDeferred.await(),
                provider  = providerDeferred.await()
            )
        }
    }

    private fun scanProcForProvider(): RootProvider {
        val pids = File("/proc").list()
            ?.filter { it.all { c -> c.isDigit() } }
            ?: return RootProvider.UNKNOWN

        for (pid in pids) {
            val cmdline = runCatching {
                File("/proc/$pid/cmdline")
                    .readBytes()
                    .map { b -> if (b == 0.toByte()) ' ' else b.toInt().toChar() }
                    .joinToString("")
                    .trim()
            }.getOrNull() ?: continue

            when {
                "magiskd" in cmdline -> return RootProvider.MAGISK
                "ksud"    in cmdline -> return RootProvider.KERNELSU
                "apd"     in cmdline -> return RootProvider.APATCH
            }
        }
        return RootProvider.UNKNOWN
    }

    private fun checkSuAvailable(): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val output  = process.inputStream.bufferedReader().readLine() ?: ""
        process.waitFor() == 0 && "uid=0" in output
    }.getOrDefault(false)
}