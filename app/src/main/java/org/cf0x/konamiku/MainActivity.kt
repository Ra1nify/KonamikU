package org.cf0x.konamiku

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.ColorSource
import org.cf0x.konamiku.data.ThemeMode
import org.cf0x.konamiku.ui.layout.MainLayout
import org.cf0x.konamiku.ui.theme.KonamikuTheme
import org.cf0x.konamiku.util.CardIdConverter

class MainActivity : ComponentActivity() {

    private lateinit var dataStore: AppDataStore
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dataStore    = AppDataStore(applicationContext)
        nfcAdapter   = NfcAdapter.getDefaultAdapter(this)

        setContent {
            val themeMode       by dataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val colorSource     by dataStore.colorSource.collectAsState(initial = ColorSource.MONET)
            val presetColor     by dataStore.presetColor.collectAsState(initial = Color(0xFF6750A4))
            val themeExpressive by dataStore.themeExpressive.collectAsState(initial = true)
            val paletteStyle    by dataStore.paletteStyle.collectAsState(initial = PaletteStyle.TonalSpot)

            KonamikuTheme(
                themeMode    = themeMode,
                colorSource  = colorSource,
                seedColor    = presetColor,
                isExpressive = themeExpressive,
                paletteStyle = paletteStyle,
            ) {
                MainLayout(dataStore = dataStore)
            }
        }

        handleNfcIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action in listOf(NfcAdapter.ACTION_TAG_DISCOVERED, NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_NDEF_DISCOVERED)) {
            val tag = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            tag?.let {
                val idm = it.id.joinToString("") { byte -> "%02X".format(byte) }
                Toast.makeText(this, "IDm:$idm", Toast.LENGTH_SHORT).show()
                setIntent(Intent())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // HCE card emulation requires the NFC chip in idle/passive mode.
        // Reader mode would force the chip into active polling, preventing
        // the emulated card from responding to external readers.
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }
}
