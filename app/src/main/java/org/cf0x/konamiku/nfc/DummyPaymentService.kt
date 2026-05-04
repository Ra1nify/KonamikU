package org.cf0x.konamiku.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

class DummyPaymentService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        return null
    }

    override fun onDeactivated(reason: Int) {
    }
}