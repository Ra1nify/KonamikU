package org.cf0x.konamiku.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
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
enum class AppLocale(val tag: String) {
    SYSTEM(""),
    ZH_CN("zh-CN"),
    EN_US("en-US")
}

class AppDataStore(private val context: Context) {

    private object Keys {
        val NAVIGATION_MODE = stringPreferencesKey("navigation_mode")
        val THEME_MODE      = stringPreferencesKey("theme_mode")
        val COLOR_SOURCE    = stringPreferencesKey("color_source")
        val PRESET_COLOR    = intPreferencesKey("preset_color")
        val ACTIVE_CARD_ID  = stringPreferencesKey("active_card_id")
        val COMPAT_MODE     = booleanPreferencesKey("compat_mode")
        val LOAD_PMMTOOL    = booleanPreferencesKey("load_pmmtool")
        val APP_LOCALE = stringPreferencesKey("app_locale")
    }

    init {
        val sp = context.getSharedPreferences("KonamikU", Context.MODE_PRIVATE)
        if (!sp.contains("load_pmmtool")) {
            sp.edit { putBoolean("load_pmmtool", true) }
        }
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

    val compatMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.COMPAT_MODE] ?: false
    }

    val loadPmmtool: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOAD_PMMTOOL] ?: true
    }

    val appLocale: Flow<AppLocale> = context.dataStore.data.map { prefs ->
        val tag = prefs[Keys.APP_LOCALE] ?: ""
        AppLocale.entries.firstOrNull { it.tag == tag } ?: AppLocale.SYSTEM
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

    suspend fun saveCompatMode(enabled: Boolean) =
        context.dataStore.edit { it[Keys.COMPAT_MODE] = enabled }

    suspend fun saveLoadPmmtool(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOAD_PMMTOOL] = enabled }
        context.getSharedPreferences("KonamikU", Context.MODE_PRIVATE)
            .edit {
                putBoolean("load_pmmtool", enabled)
            }
    }

    suspend fun saveAppLocale(locale: AppLocale) =
        context.dataStore.edit { it[Keys.APP_LOCALE] = locale.tag }
}