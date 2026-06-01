package org.cf0x.konamiku.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class NavigationMode { AUTO, BOTTOM, RAIL }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorSource { MONET, PRESET, CUSTOM }
enum class EmuMode { NORMAL, COMPAT, NATIVE }
enum class AppLocale(val tag: String) {
    SYSTEM(""),
    ZH_CN("zh-CN"),
    EN_US("en-US")
}

class AppDataStore(private val context: Context) {

    private val sp = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    var autoExclusiveModeSync = sp.getBoolean("auto_exclusive_mode", false)
        private set
    var devModeForceEmuSync = sp.getBoolean("dev_mode_force_emu", false)
        private set

    private object Keys {
        val NAVIGATION_MODE = stringPreferencesKey("navigation_mode")
        val THEME_MODE      = stringPreferencesKey("theme_mode")
        val COLOR_SOURCE    = stringPreferencesKey("color_source")
        val PRESET_COLOR    = intPreferencesKey("preset_color")
        val ACTIVE_CARD_ID  = stringPreferencesKey("active_card_id")
        val EMU_MODE            = stringPreferencesKey("emu_mode")
        val APP_LOCALE          = stringPreferencesKey("app_locale")
        val DEV_MODE_FORCE_EMU  = booleanPreferencesKey("dev_mode_force_emu")
        val AUTO_EXCLUSIVE_MODE = booleanPreferencesKey("auto_exclusive_mode")
        val LAST_PAYMENT_APP    = stringPreferencesKey("last_payment_app")
    }

    init {
        val sp = context.getSharedPreferences("KonamikU", Context.MODE_PRIVATE)
    }

    val navigationMode: Flow<NavigationMode> = context.dataStore.data.map { prefs ->
        NavigationMode.valueOf(prefs[Keys.NAVIGATION_MODE] ?: NavigationMode.AUTO.name)
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    val colorSource: Flow<ColorSource> = context.dataStore.data.map { prefs ->
        ColorSource.valueOf(prefs[Keys.COLOR_SOURCE] ?: ColorSource.MONET.name)
    }

    val presetColor: Flow<Color> = context.dataStore.data.map { prefs ->
        Color(prefs[Keys.PRESET_COLOR] ?: 0xFF6750A4.toInt())
    }

    val activeCardId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_CARD_ID]
    }

    val emuMode: Flow<EmuMode> = context.dataStore.data.map { prefs ->
        EmuMode.valueOf(prefs[Keys.EMU_MODE] ?: EmuMode.NORMAL.name)
    }

    val appLocale: Flow<AppLocale> = context.dataStore.data.map { prefs ->
        val tag = prefs[Keys.APP_LOCALE] ?: ""
        AppLocale.entries.firstOrNull { it.tag == tag } ?: AppLocale.SYSTEM
    }

    val devModeForceEmu: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEV_MODE_FORCE_EMU] ?: false
    }

    val autoExclusiveMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_EXCLUSIVE_MODE] ?: false
    }

    val lastPaymentApp: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_PAYMENT_APP]
    }

    suspend fun saveNavigationMode(mode: NavigationMode) =
        context.dataStore.edit { it[Keys.NAVIGATION_MODE] = mode.name }

    suspend fun saveThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun saveColorSource(source: ColorSource) =
        context.dataStore.edit { it[Keys.COLOR_SOURCE] = source.name }

    suspend fun savePresetColor(color: Int) =
        context.dataStore.edit { it[Keys.PRESET_COLOR] = color }

    suspend fun saveActiveCardId(id: String?) =
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.ACTIVE_CARD_ID)
            else prefs[Keys.ACTIVE_CARD_ID] = id
        }

    suspend fun saveEmuMode(mode: EmuMode) =
        context.dataStore.edit { it[Keys.EMU_MODE] = mode.name }

    suspend fun saveAppLocale(locale: AppLocale) =
        context.dataStore.edit { it[Keys.APP_LOCALE] = locale.tag }

    suspend fun saveDevModeForceEmu(enabled: Boolean) =
        context.dataStore.edit {
            it[Keys.DEV_MODE_FORCE_EMU] = enabled
            devModeForceEmuSync = enabled
            sp.edit().putBoolean("dev_mode_force_emu", enabled).apply()
        }

    suspend fun saveAutoExclusiveMode(enabled: Boolean) =
        context.dataStore.edit {
            it[Keys.AUTO_EXCLUSIVE_MODE] = enabled
            autoExclusiveModeSync = enabled
            sp.edit().putBoolean("auto_exclusive_mode", enabled).apply()
        }

    suspend fun saveLastPaymentApp(component: String?) =
        context.dataStore.edit { prefs ->
            if (component == null) prefs.remove(Keys.LAST_PAYMENT_APP)
            else prefs[Keys.LAST_PAYMENT_APP] = component
        }
}
