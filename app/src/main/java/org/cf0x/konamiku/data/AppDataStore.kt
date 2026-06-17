package org.cf0x.konamiku.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class NavigationMode { AUTO, BOTTOM, RAIL }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorSource { MONET, PRESET, CUSTOM }
enum class EmuMode { NORMAL, COMPAT, NATIVE }
enum class AppLocale(val tag: String) {
    SYSTEM(""), ZH_CN("zh-CN"), EN_US("en-US")
}

class AppDataStore(private val context: Context) {
    private val sp = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    var devModeForceEmuSync = sp.getBoolean("dev_mode_force_emu", false)
        private set

    private object Keys {
        val NAV_MODE           = stringPreferencesKey("navigation_mode")
        val THEME_MODE         = stringPreferencesKey("theme_mode")
        val COLOR_SOURCE       = stringPreferencesKey("color_source")
        val PRESET_COLOR       = intPreferencesKey("preset_color")
        val ACTIVE_CARD_ID     = stringPreferencesKey("active_card_id")
        val EMU_MODE           = stringPreferencesKey("emu_mode")
        val APP_LOCALE         = stringPreferencesKey("app_locale")
        val DEV_FORCE_EMU      = booleanPreferencesKey("dev_mode_force_emu")
        val THEME_EXPRESSIVE   = booleanPreferencesKey("theme_expressive")
        val PALETTE_STYLE      = stringPreferencesKey("palette_style")
    }

    val navigationMode: Flow<NavigationMode> = context.dataStore.data.map { p ->
        runCatching { NavigationMode.valueOf(p[Keys.NAV_MODE] ?: "") }.getOrDefault(NavigationMode.AUTO)
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    }

    val colorSource: Flow<ColorSource> = context.dataStore.data.map { p ->
        runCatching { ColorSource.valueOf(p[Keys.COLOR_SOURCE] ?: "") }.getOrDefault(ColorSource.MONET)
    }

    val presetColor: Flow<Color> = context.dataStore.data.map { p ->
        Color(p[Keys.PRESET_COLOR] ?: 0xFF6750A4.toInt())
    }

    val activeCardId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_CARD_ID] }

    val emuMode: Flow<EmuMode> = context.dataStore.data.map { p ->
        runCatching { EmuMode.valueOf(p[Keys.EMU_MODE] ?: "") }.getOrDefault(EmuMode.NORMAL)
    }

    val appLocale: Flow<AppLocale> = context.dataStore.data.map { p ->
        val tag = p[Keys.APP_LOCALE] ?: ""
        AppLocale.entries.find { it.tag == tag } ?: AppLocale.SYSTEM
    }

    val devModeForceEmu: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEV_FORCE_EMU] ?: false }
    val themeExpressive: Flow<Boolean> = context.dataStore.data.map { it[Keys.THEME_EXPRESSIVE] ?: true }

    val paletteStyle: Flow<PaletteStyle> = context.dataStore.data.map { p ->
        runCatching { PaletteStyle.valueOf(p[Keys.PALETTE_STYLE] ?: "") }
            .getOrDefault(PaletteStyle.TonalSpot)
    }

    suspend fun saveNavigationMode(m: NavigationMode) = context.dataStore.edit { it[Keys.NAV_MODE] = m.name }
    suspend fun saveThemeMode(m: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = m.name }
    suspend fun saveColorSource(s: ColorSource) = context.dataStore.edit { it[Keys.COLOR_SOURCE] = s.name }
    suspend fun savePresetColor(c: Int) = context.dataStore.edit { it[Keys.PRESET_COLOR] = c }

    suspend fun saveActiveCardId(id: String?) = context.dataStore.edit { p ->
        if (id == null) p.remove(Keys.ACTIVE_CARD_ID) else p[Keys.ACTIVE_CARD_ID] = id
    }

    suspend fun saveEmuMode(m: EmuMode) = context.dataStore.edit { it[Keys.EMU_MODE] = m.name }
    suspend fun saveAppLocale(l: AppLocale) = context.dataStore.edit { it[Keys.APP_LOCALE] = l.tag }

    suspend fun saveDevModeForceEmu(v: Boolean) = context.dataStore.edit {
        it[Keys.DEV_FORCE_EMU] = v
        devModeForceEmuSync = v
        sp.edit().putBoolean("dev_mode_force_emu", v).apply()
    }

    suspend fun saveThemeExpressive(v: Boolean) = context.dataStore.edit { it[Keys.THEME_EXPRESSIVE] = v }

    suspend fun savePaletteStyle(s: PaletteStyle) = context.dataStore.edit { it[Keys.PALETTE_STYLE] = s.name }
}
