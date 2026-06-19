package org.cf0x.konamiku.nfc

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.widget.Toast
import org.cf0x.konamiku.R

class DummyPaymentService : HostApduService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(applicationContext, getString(R.string.toast_nfc_loaded), Toast.LENGTH_SHORT).show()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        return null
    }

    override fun onDeactivated(reason: Int) {
        android.util.Log.i("KonamikU", "DummyPaymentService: onDeactivated (reason: $reason)")
    }
}