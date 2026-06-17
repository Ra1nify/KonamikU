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

        // Use a system code that Android would always reject without our hook.
        // "88B4" can succeed on stock Android (it's a normal valid code), giving a
        // false positive on non-hooked devices. "FFFF" is guaranteed to be rejected
        // by isValidSystemCode on stock ROMs, so it only succeeds when our hook
        // makes isValidSystemCode always return true.
        return runCatching {
            val ok = emulation.registerSystemCodeForService(component, "FFFF")
            if (ok) emulation.unregisterSystemCodeForService(component)
            ok
        }.getOrDefault(false)
    }
}
