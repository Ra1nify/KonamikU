package org.cf0x.konamiku.data

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.cf0x.konamiku.R

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class NavigationMode { AUTO, BOTTOM, RAIL }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorSource { MONET, PRESET, CUSTOM }
enum class EmuMode { NORMAL, COMPAT, NATIVE }
enum class UpdateInterval(val millis: Long) {
    OFF(0),
    MIN_30(30 * 60 * 1000L),
    HOUR_1(60 * 60 * 1000L),
    HOUR_2(2 * 60 * 60 * 1000L),
    HOUR_6(6 * 60 * 60 * 1000L),
    HOUR_12(12 * 60 * 60 * 1000L),
    HOUR_24(24 * 60 * 60 * 1000L),
    DAY_3(3 * 24 * 60 * 60 * 1000L),
    DAY_7(7 * 24 * 60 * 60 * 1000L);
}

enum class UpdateMode { GITHUB, PROXY }

enum class AppLocale(val tag: String, val labelRes: Int) {
    SYSTEM("", R.string.setting_language_system),
    ZH_CN("zh-CN", R.string.setting_language_zh),
    ZH_TW("zh-TW", R.string.setting_language_zh_tw),
    JA("ja", R.string.setting_language_ja),
    KO("ko", R.string.setting_language_ko),
    FR("fr", R.string.setting_language_fr),
    EN_US("en-US", R.string.setting_language_en);

    fun toLocale(): java.util.Locale = when (this) {
        SYSTEM -> java.util.Locale.getDefault()
        ZH_CN  -> java.util.Locale.CHINA
        ZH_TW  -> java.util.Locale.TAIWAN
        JA     -> java.util.Locale.JAPAN
        KO     -> java.util.Locale.KOREA
        FR     -> java.util.Locale.FRANCE
        EN_US  -> java.util.Locale.US
    }
}

class AppDataStore(private val context: Context) {

    var devModeForceEmuSync = false
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
        val SETUP_VERSION      = longPreferencesKey("setup_version")
        val UPDATE_GITHUB_BASE = stringPreferencesKey("update_github_base")
        val UPDATE_MIRROR_BASE = stringPreferencesKey("update_mirror_base")
        val UPDATE_CUSTOM_BASE = stringPreferencesKey("update_custom_base")
        val UPDATE_LAST_CHECK  = longPreferencesKey("update_last_check")
        val UPDATE_INTERVAL    = stringPreferencesKey("update_interval")
        val UPDATE_MODE        = stringPreferencesKey("update_mode")
    }

    val navigationMode: Flow<NavigationMode> = context.dataStore.data.map { p ->
        runCatching { NavigationMode.valueOf(p[Keys.NAV_MODE] ?: "") }
            .getOrDefault(NavigationMode.AUTO)
            .also { if (it == NavigationMode.AUTO && p[Keys.NAV_MODE] != null && p[Keys.NAV_MODE] != NavigationMode.AUTO.name) Log.w("AppDataStore", "Unknown NAV_MODE: ${p[Keys.NAV_MODE]}") }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
            .also { if (it == ThemeMode.SYSTEM && p[Keys.THEME_MODE] != null && p[Keys.THEME_MODE] != ThemeMode.SYSTEM.name) Log.w("AppDataStore", "Unknown THEME_MODE: ${p[Keys.THEME_MODE]}") }
    }

    val colorSource: Flow<ColorSource> = context.dataStore.data.map { p ->
        runCatching { ColorSource.valueOf(p[Keys.COLOR_SOURCE] ?: "") }
            .getOrDefault(ColorSource.MONET)
            .also { if (it == ColorSource.MONET && p[Keys.COLOR_SOURCE] != null && p[Keys.COLOR_SOURCE] != ColorSource.MONET.name) Log.w("AppDataStore", "Unknown COLOR_SOURCE: ${p[Keys.COLOR_SOURCE]}") }
    }

    val presetColor: Flow<Color> = context.dataStore.data.map { p ->
        Color(p[Keys.PRESET_COLOR] ?: 0xFF6750A4.toInt())
    }

    val activeCardId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_CARD_ID] }

    val emuMode: Flow<EmuMode> = context.dataStore.data.map { p ->
        runCatching { EmuMode.valueOf(p[Keys.EMU_MODE] ?: "") }
            .getOrDefault(EmuMode.NORMAL)
            .also { if (it == EmuMode.NORMAL && p[Keys.EMU_MODE] != null && p[Keys.EMU_MODE] != EmuMode.NORMAL.name) Log.w("AppDataStore", "Unknown EMU_MODE: ${p[Keys.EMU_MODE]}") }
    }

    val appLocale: Flow<AppLocale> = context.dataStore.data.map { p ->
        val tag = p[Keys.APP_LOCALE] ?: ""
        AppLocale.entries.find { it.tag == tag } ?: detectSystemLocale()
    }

    private fun detectSystemLocale(): AppLocale {
        val locale = java.util.Locale.getDefault()
        return when (locale.language) {
            "zh" -> if (locale.country in listOf("TW", "HK", "MO")) AppLocale.ZH_TW
                    else AppLocale.ZH_CN  // includes SG, MY and any other zh variant → simplified
            "ja" -> AppLocale.JA
            "ko" -> AppLocale.KO
            "fr" -> AppLocale.FR
            else -> AppLocale.EN_US
        }
    }

    val devModeForceEmu: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEV_FORCE_EMU] ?: false }
    val themeExpressive: Flow<Boolean> = context.dataStore.data.map { it[Keys.THEME_EXPRESSIVE] ?: true }

    val paletteStyle: Flow<PaletteStyle> = context.dataStore.data.map { p ->
        runCatching { PaletteStyle.valueOf(p[Keys.PALETTE_STYLE] ?: "") }
            .getOrDefault(PaletteStyle.TonalSpot)
            .also { if (it == PaletteStyle.TonalSpot && p[Keys.PALETTE_STYLE] != null && p[Keys.PALETTE_STYLE] != PaletteStyle.TonalSpot.name) Log.w("AppDataStore", "Unknown PALETTE_STYLE: ${p[Keys.PALETTE_STYLE]}") }
    }

    val setupVersion: Flow<Long> = context.dataStore.data.map { it[Keys.SETUP_VERSION] ?: -1L }

    val updateGithubBase: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_GITHUB_BASE] ?: "" }
    val updateMirrorBase: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_MIRROR_BASE] ?: "" }
    val updateCustomBase: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_CUSTOM_BASE] ?: "" }
    val updateLastCheck: Flow<Long> = context.dataStore.data.map { it[Keys.UPDATE_LAST_CHECK] ?: 0L }
    val updateInterval: Flow<UpdateInterval> = context.dataStore.data.map { p ->
        runCatching { UpdateInterval.valueOf(p[Keys.UPDATE_INTERVAL] ?: "") }
            .getOrDefault(UpdateInterval.OFF)
    }
    val updateMode: Flow<UpdateMode> = context.dataStore.data.map { p ->
        runCatching { UpdateMode.valueOf(p[Keys.UPDATE_MODE] ?: "") }
            .getOrDefault(UpdateMode.GITHUB)
            .also { if (it == UpdateMode.GITHUB && p[Keys.UPDATE_MODE] != null && p[Keys.UPDATE_MODE] != UpdateMode.GITHUB.name) Log.w("AppDataStore", "Unknown UPDATE_MODE: ${p[Keys.UPDATE_MODE]}") }
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
    }

    suspend fun saveThemeExpressive(v: Boolean) = context.dataStore.edit { it[Keys.THEME_EXPRESSIVE] = v }

    suspend fun savePaletteStyle(s: PaletteStyle) = context.dataStore.edit { it[Keys.PALETTE_STYLE] = s.name }
    suspend fun saveSetupVersion(v: Long) = context.dataStore.edit { it[Keys.SETUP_VERSION] = v }
    suspend fun saveUpdateGithubBase(v: String) = context.dataStore.edit { it[Keys.UPDATE_GITHUB_BASE] = v }
    suspend fun saveUpdateMirrorBase(v: String) = context.dataStore.edit { it[Keys.UPDATE_MIRROR_BASE] = v }
    suspend fun saveUpdateCustomBase(v: String) = context.dataStore.edit { it[Keys.UPDATE_CUSTOM_BASE] = v }
    suspend fun saveUpdateLastCheck(v: Long) = context.dataStore.edit { it[Keys.UPDATE_LAST_CHECK] = v }
    suspend fun saveUpdateInterval(v: UpdateInterval) = context.dataStore.edit { it[Keys.UPDATE_INTERVAL] = v.name }
    suspend fun saveUpdateMode(v: UpdateMode) = context.dataStore.edit { it[Keys.UPDATE_MODE] = v.name }
}
