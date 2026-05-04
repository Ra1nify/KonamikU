package org.cf0x.konamiku

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.AppLocale
import org.cf0x.konamiku.data.ColorSource
import org.cf0x.konamiku.data.ThemeMode
import org.cf0x.konamiku.ui.layout.MainLayout
import org.cf0x.konamiku.ui.theme.KonamikuTheme
import org.cf0x.konamiku.util.CardIdConverter

class MainActivity : ComponentActivity() {

    private lateinit var dataStore: AppDataStore

    override fun attachBaseContext(newBase: Context) {
        val store = AppDataStore(newBase)
        val locale = runCatching {
            runBlocking { AppDataStore(newBase).appLocale.first() }
        }.getOrDefault(AppLocale.SYSTEM)
        if (locale == AppLocale.SYSTEM) {
            super.attachBaseContext(newBase)
        } else {
            val config = newBase.resources.configuration
            config.setLocale(java.util.Locale.forLanguageTag(locale.tag))
            val ctx = newBase.createConfigurationContext(config)
            super.attachBaseContext(ctx)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dataStore = AppDataStore(applicationContext)

        if (BuildConfig.DEBUG) {
            if (!CardIdConverter.selfTest()) {
                android.util.Log.e("MainActivity", "CardIdConverter self-test failed")
            }
        }

        setContent {
            val themeMode   by dataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val colorSource by dataStore.colorSource.collectAsState(initial = ColorSource.MONET)
            val presetColor by dataStore.presetColor.collectAsState(initial = Color(0xFF6750A4))

            KonamikuTheme(
                themeMode   = themeMode,
                colorSource = colorSource,
                seedColor   = presetColor
            ) {
                MainLayout(dataStore = dataStore)
            }
        }
    }
}