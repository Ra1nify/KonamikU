package org.cf0x.konamiku.xposed

import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.NfcFCardEmulation
import org.cf0x.konamiku.nfc.EmuCard

object NfcHookProber {
    /**
     * Probes if the NFC hook (isValidSystemCode) is active by attempting to register
     * a system code that would normally be rejected (or just calling the method).
     */
    fun probe(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        val emulation = runCatching { NfcFCardEmulation.getInstance(adapter) }.getOrNull()
            ?: return false
        val component = ComponentName(context, EmuCard::class.java)
        
        // We use registerSystemCodeForService because it internally calls isValidSystemCode.
        // If our hook is active, it returns true even for codes that would normally be rejected.
        // "88B4" is a valid code for our app, so we might want to test an "invalid" one if we wanted
        // to be 100% sure, but register followed by unregister is what we've been using.
        return runCatching {
            val ok = emulation.registerSystemCodeForService(component, "88B4")
            if (ok) emulation.unregisterSystemCodeForService(component)
            ok
        }.getOrDefault(false)
    }
}
