package org.cf0x.konamiku.nfc

import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.NfcFCardEmulation
import android.util.Log
import org.cf0x.konamiku.data.EmuMode

/** Registers IDm + system code for the HCE-F service without enabling (caller handles enable/disable). */
fun updateHceRegistration(context: Context, idm: String, mode: EmuMode) {
    runCatching {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return
        val emulation = NfcFCardEmulation.getInstance(adapter) ?: return
        val component = ComponentName(context, EmuCard::class.java)
        val activeIdm = resolveActiveIdm(idm, mode)
        emulation.setNfcid2ForService(component, activeIdm)
        emulation.registerSystemCodeForService(component, SYSTEM_CODE_FELICA)
    }.onFailure {
        Log.w("KonamikU-NfcUtils", "updateHceRegistration failed: ${it.message}")
    }
}
