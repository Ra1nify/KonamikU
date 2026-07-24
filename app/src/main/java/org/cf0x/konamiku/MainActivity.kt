package org.cf0x.konamiku

import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.NfcFCardEmulation
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
import org.cf0x.konamiku.data.AppLocale
import org.cf0x.konamiku.data.ColorSource
import org.cf0x.konamiku.data.ThemeMode
import org.cf0x.konamiku.notification.LiveUpdateManager
import org.cf0x.konamiku.ui.layout.MainLayout
import org.cf0x.konamiku.ui.theme.KonamikuTheme

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
                Toast.makeText(this, getString(R.string.toast_idm_scanned, idm), Toast.LENGTH_SHORT).show()
                setIntent(Intent())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val lm = getSystemService(android.app.LocaleManager::class.java) ?: return@runCatching
                val sysTags = lm.applicationLocales.toLanguageTags()
                if (sysTags.isNotBlank()) {
                    val matched = AppLocale.fromSystemTag(sysTags)
                    if (matched != null) runBlocking { dataStore.saveAppLocale(matched) }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 每次 onPause 实时读取开关值，确保用户刚改的设置即时生效
        if (!runBlocking { dataStore.backgroundEmulation.first() }) {
            stopEmulation()
        }
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }

    /** 同 MainScreen.deactivateCard()：断路由 + 清激活状态 + 取消通知 */
    private fun stopEmulation() {
        val activeId = runBlocking { dataStore.activeCardId.first() } ?: return

        // 1. 禁用 HCE-F 路由
        runCatching {
            nfcAdapter?.let { NfcFCardEmulation.getInstance(it) }?.disableService(this)
        }

        // 2. 清除激活状态（MainScreen 会响应并更新 UI）
        runBlocking { dataStore.saveActiveCardId(null) }

        // 3. 取消通知栏
        LiveUpdateManager.cancel(this)
    }
}
