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
    data class NfcStatus(
        val rfEnabled: Boolean,
        val hcefSupported: Boolean,
        val defaultPaymentIsUs: Boolean,
        val defaultPaymentLabel: String
    )

    data class XposedStatus(val active: Boolean, val needsRestart: Boolean, val provider: String, val pmmActive: Boolean)

    data class AllStatus(
        val nfc: NfcStatus,
        val xposed: XposedStatus
    )

    suspend fun detectAll(context: Context): AllStatus = withContext(Dispatchers.IO) {
        AllStatus(
            nfc         = detectNfc(context),
            xposed      = detectXposed()
        )
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

        val pmmActive = XposedState.pmmActive

        return XposedStatus(isActive, needsRestart, provider, pmmActive)
    }
}