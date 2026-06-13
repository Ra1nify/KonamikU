package org.cf0x.konamiku.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.AppLocale
import org.cf0x.konamiku.data.ColorSource
import org.cf0x.konamiku.data.NavigationMode
import org.cf0x.konamiku.data.ThemeMode
import org.cf0x.konamiku.ui.components.ColorPickerWheel
import org.cf0x.konamiku.ui.components.SegmentSwitch

@Composable
fun SettingScreen(dataStore: AppDataStore) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val navMode      by dataStore.navigationMode.collectAsState(initial = NavigationMode.AUTO)
    val themeMode    by dataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val colorSource  by dataStore.colorSource.collectAsState(initial = if (supportsMonet) ColorSource.MONET else ColorSource.PRESET)
    val savedColor   by dataStore.presetColor.collectAsState(initial = Color(0xFF6750A4))
    val appLocale    by dataStore.appLocale.collectAsState(initial = AppLocale.SYSTEM)
    val devModeForce by dataStore.devModeForceEmu.collectAsState(initial = dataStore.devModeForceEmuSync)
    val isExpressive by dataStore.themeExpressive.collectAsState(initial = true)

    var previewColor by remember(savedColor) { mutableStateOf(savedColor) }
    var showPicker   by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { showPicker = false }
    LaunchedEffect(colorSource) { if (colorSource != ColorSource.PRESET) showPicker = false }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- Navigation Layout ---
        SegmentSwitch(
            label         = stringResource(R.string.setting_nav_layout),
            options       = listOf(stringResource(R.string.setting_nav_auto), stringResource(R.string.setting_nav_bottom), stringResource(R.string.setting_nav_rail)),
            selectedIndex = navMode.ordinal,
            onSelect      = { scope.launch { dataStore.saveNavigationMode(NavigationMode.entries[it]) } }
        )

        // --- Color System ---
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val colorOptions = if (supportsMonet) listOf(stringResource(R.string.setting_color_system), stringResource(R.string.setting_color_custom)) else listOf(stringResource(R.string.setting_color_custom))
            val selectedIndex = if (supportsMonet && colorSource == ColorSource.MONET) 0 else 1

            SegmentSwitch(
                label         = stringResource(R.string.setting_color_source),
                options       = colorOptions,
                selectedIndex = selectedIndex,
                onSelect      = { index ->
                    val next = if (supportsMonet && index == 0) ColorSource.MONET else ColorSource.PRESET
                    scope.launch { dataStore.saveColorSource(next) }
                    if (next == ColorSource.PRESET && colorSource == ColorSource.PRESET) showPicker = !showPicker
                }
            )

            AnimatedVisibility(visible = colorSource == ColorSource.PRESET && showPicker, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorPickerWheel(initialColor = previewColor, onColorChanged = { previewColor = it }, modifier = Modifier.padding(top = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { previewColor = savedColor; showPicker = false }) { Text(stringResource(R.string.card_add_cancel)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { scope.launch { dataStore.savePresetColor(previewColor.toArgb()) }; showPicker = false }) { Text(stringResource(R.string.card_add_confirm)) }
                    }
                }
            }

            AnimatedVisibility(visible = colorSource == ColorSource.PRESET && !showPicker, enter = fadeIn(), exit = fadeOut()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(modifier = Modifier.size(28.dp), shape = MaterialTheme.shapes.small, color = savedColor, tonalElevation = 2.dp) {}
                    Text(text = "#%06X".format(savedColor.toArgb() and 0xFFFFFF), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showPicker = true }) { Text(stringResource(R.string.setting_color_custom)) }
                }
            }
        }

        // --- Dark Mode ---
        SegmentSwitch(
            label         = stringResource(R.string.setting_theme_mode),
            options       = listOf(stringResource(R.string.setting_theme_system), stringResource(R.string.setting_theme_light), stringResource(R.string.setting_theme_dark)),
            selectedIndex = themeMode.ordinal,
            onSelect      = { scope.launch { dataStore.saveThemeMode(ThemeMode.entries[it]) } }
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // --- Expressive Style Toggle ---
        ExpressiveToggleItem(isExpressive) { scope.launch { dataStore.saveThemeExpressive(it) } }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // --- Language ---
        LanguageItem(dataStore, appLocale)

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // --- System & NFC ---
        NfcSettingsItem(context)

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // --- Developer ---
        DevModeItem(dataStore, devModeForce, scope)
    }
}

@Composable
private fun ExpressiveToggleItem(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_theme_expressive), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setting_theme_expressive_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun LanguageItem(dataStore: AppDataStore, appLocale: AppLocale) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var pending  by remember(appLocale) { mutableStateOf(appLocale) }
    val options  = listOf(stringResource(R.string.setting_language_system) to AppLocale.SYSTEM, stringResource(R.string.setting_language_zh) to AppLocale.ZH_CN, stringResource(R.string.setting_language_en) to AppLocale.EN_US)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_language), style = MaterialTheme.typography.bodyLarge)
                if (!expanded) Text(options.first { it.second == appLocale }.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                options.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().clickable { pending = value }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = pending == value, onClick = { pending = value })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { pending = appLocale; expanded = false }) { Text(stringResource(R.string.card_add_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        expanded = false
                        if (pending == appLocale) return@Button
                        scope.launch { dataStore.saveAppLocale(pending) }
                        AppCompatDelegate.setApplicationLocales(if (pending == AppLocale.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(pending.tag))
                        (context as? Activity)?.let { it.finish(); it.startActivity(it.intent) }
                    }) { Text(stringResource(R.string.card_add_confirm)) }
                }
            }
        }
    }
}

@Composable
private fun NfcSettingsItem(context: android.content.Context) {
    Row(Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_nfc_default), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setting_nfc_default_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DevModeItem(dataStore: AppDataStore, devMode: Boolean, scope: kotlinx.coroutines.CoroutineScope) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_dev_force_emu), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setting_dev_force_emu_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = devMode, onCheckedChange = { scope.launch { dataStore.saveDevModeForceEmu(it) } })
    }
}
