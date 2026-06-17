package org.cf0x.konamiku.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.cf0x.konamiku.BuildConfig
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

    var loaded by remember { mutableStateOf(false) }

    var navMode      by remember { mutableStateOf(NavigationMode.AUTO) }
    var themeMode    by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var colorSource  by remember { mutableStateOf(if (supportsMonet) ColorSource.MONET else ColorSource.PRESET) }
    var savedColor   by remember { mutableStateOf(Color(0xFF6750A4)) }
    var appLocale    by remember { mutableStateOf(AppLocale.SYSTEM) }
    var devModeForce by remember { mutableStateOf(dataStore.devModeForceEmuSync) }
    var isExpressive by remember { mutableStateOf(true) }
    var paletteStyle by remember { mutableStateOf(PaletteStyle.TonalSpot) }

    LaunchedEffect(Unit) {
        navMode      = dataStore.navigationMode.first()
        themeMode    = dataStore.themeMode.first()
        colorSource  = dataStore.colorSource.first()
        savedColor   = dataStore.presetColor.first()
        appLocale    = dataStore.appLocale.first()
        devModeForce = dataStore.devModeForceEmu.first()
        isExpressive = dataStore.themeExpressive.first()
        paletteStyle = dataStore.paletteStyle.first()
        loaded       = true
    }

    var previewColor by remember { mutableStateOf(savedColor) }
    var showPicker   by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { showPicker = false }
    LaunchedEffect(colorSource) { if (colorSource != ColorSource.PRESET) showPicker = false }

    if (!loaded) return

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Group 1: About ---
        SettingGroup {
            AboutItem(context)
        }

        // --- Group 2: Navigation ---
        SettingGroup {
            SegmentSwitch(
                label         = stringResource(R.string.setting_nav_layout),
                options       = listOf(stringResource(R.string.setting_nav_auto), stringResource(R.string.setting_nav_bottom), stringResource(R.string.setting_nav_rail)),
                selectedIndex = navMode.ordinal,
                onSelect      = { scope.launch { dataStore.saveNavigationMode(NavigationMode.entries[it]) }; navMode = NavigationMode.entries[it] }
            )
        }

        // --- Group 3: Theme Color & Style ---
        SettingGroup {
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
                        colorSource = next
                        if (next == ColorSource.PRESET) showPicker = !showPicker
                    }
                )

                AnimatedVisibility(visible = colorSource == ColorSource.PRESET && showPicker, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ColorPickerWheel(initialColor = previewColor, onColorChanged = { previewColor = it }, modifier = Modifier.padding(top = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { previewColor = savedColor; showPicker = false }) { Text(stringResource(R.string.card_add_cancel)) }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { scope.launch { dataStore.savePresetColor(previewColor.toArgb()) }; savedColor = previewColor; showPicker = false }) { Text(stringResource(R.string.card_add_confirm)) }
                        }
                    }
                }

                AnimatedVisibility(visible = colorSource == ColorSource.PRESET && !showPicker, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showPicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(modifier = Modifier.size(28.dp), shape = MaterialTheme.shapes.small, color = savedColor, tonalElevation = 2.dp) {}
                            Text(text = "#%06X".format(savedColor.toArgb() and 0xFFFFFF), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // --- Palette Style selector (decoupled from expressive shapes) ---
                PaletteStyleItem(paletteStyle) { paletteStyle = it; scope.launch { dataStore.savePaletteStyle(it) } }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // --- Expressive shapes toggle (now only controls corners + typography) ---
                ExpressiveToggleItem(isExpressive) { isExpressive = it; scope.launch { dataStore.saveThemeExpressive(it) } }
            }
        }

        // --- Group 4: Dark Mode ---
        SettingGroup {
            SegmentSwitch(
                label         = stringResource(R.string.setting_theme_mode),
                options       = listOf(stringResource(R.string.setting_theme_system), stringResource(R.string.setting_theme_light), stringResource(R.string.setting_theme_dark)),
                selectedIndex = themeMode.ordinal,
                onSelect      = { scope.launch { dataStore.saveThemeMode(ThemeMode.entries[it]) }; themeMode = ThemeMode.entries[it] }
            )
        }

        // --- Group 5: Language ---
        SettingGroup {
            LanguageItem(dataStore, appLocale)
        }

        // --- Group 6: System ---
        SettingGroup {
            NfcSettingsItem(context)
        }

        // --- Group 7: Developer ---
        SettingGroup {
            DevModeItem(devMode = devModeForce, onToggle = { v -> devModeForce = v; scope.launch { dataStore.saveDevModeForceEmu(v) } })
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            content  = content
        )
    }
}

@Composable
private fun AboutItem(context: android.content.Context) {
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    val openGitHub: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/C-F0x/KonamikU"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Column(
        modifier = Modifier.clickable { openGitHub() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    painter = painterResource(R.mipmap.ic_launcher_round),
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "C-F0x@Github",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ExpressiveToggleItem(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
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
private fun PaletteStyleItem(current: PaletteStyle, onSelect: (PaletteStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val options = listOf(
        PaletteStyle.TonalSpot  to stringResource(R.string.setting_palette_style_tonalspot),
        PaletteStyle.Neutral    to stringResource(R.string.setting_palette_style_neutral),
        PaletteStyle.Vibrant    to stringResource(R.string.setting_palette_style_vibrant),
        PaletteStyle.Expressive to stringResource(R.string.setting_palette_style_expressive),
        PaletteStyle.Monochrome to stringResource(R.string.setting_palette_style_monochrome),
        PaletteStyle.Fidelity   to stringResource(R.string.setting_palette_style_fidelity),
        PaletteStyle.Rainbow    to stringResource(R.string.setting_palette_style_rainbow),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_palette_style), style = MaterialTheme.typography.bodyLarge)
                if (!expanded) {
                    Text(
                        options.firstOrNull { it.first == current }?.second ?: current.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                options.forEach { (style, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = false; onSelect(style) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = current == style,
                            onClick = { expanded = false; onSelect(style) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
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
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_language), style = MaterialTheme.typography.bodyLarge)
                if (!expanded) Text(options.first { it.second == appLocale }.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                options.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().clickable { pending = value }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = pending == value, onClick = { pending = value })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { pending = appLocale; expanded = false }) { Text(stringResource(R.string.card_add_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        expanded = false
                        if (pending == appLocale) return@Button
                        runBlocking { dataStore.saveAppLocale(pending) }
                        AppCompatDelegate.setApplicationLocales(if (pending == AppLocale.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(pending.tag))
                        // Activity must recreate to pick up new locale resources.
                        (context as? Activity)?.let { act ->
                            if (Build.VERSION.SDK_INT >= 33) act.recreate()
                            else { act.finish(); act.startActivity(act.intent) }
                        }
                    }) { Text(stringResource(R.string.card_add_confirm)) }
                }
            }
        }
    }
}

@Composable
private fun NfcSettingsItem(context: android.content.Context) {
    Row(Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_nfc_default), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setting_nfc_default_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DevModeItem(devMode: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_dev_force_emu), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setting_dev_force_emu_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = devMode, onCheckedChange = onToggle)
    }
}
