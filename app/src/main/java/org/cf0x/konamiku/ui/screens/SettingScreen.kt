package org.cf0x.konamiku.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.AppLocale
import org.cf0x.konamiku.data.CardListRefreshEvent
import org.cf0x.konamiku.data.Changelog
import org.cf0x.konamiku.data.ColorSource
import org.cf0x.konamiku.data.NavigationMode
import org.cf0x.konamiku.data.ThemeMode
import org.cf0x.konamiku.data.JsonManager
import org.cf0x.konamiku.data.NfcCard
import org.cf0x.konamiku.data.UpdateInterval
import org.cf0x.konamiku.system.UpdateChecker
import org.cf0x.konamiku.system.UpdateCheckWorker
import org.cf0x.konamiku.ui.components.ColorPickerWheel
import org.cf0x.konamiku.ui.components.SegmentSwitch
import org.cf0x.konamiku.util.applyLocale

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
    val appLocale    by dataStore.appLocale.collectAsState(initial = AppLocale.SYSTEM)
    var devModeForce by remember { mutableStateOf(dataStore.devModeForceEmuSync) }
    var isExpressive by remember { mutableStateOf(true) }
    var paletteStyle by remember { mutableStateOf(PaletteStyle.TonalSpot) }

    // Detect system-managed locale (locked only if app locale is not set)
    val systemLangLocked by produceState(initialValue = false) {
        if (Build.VERSION.SDK_INT >= 33) {
            value = runCatching {
                val lm = context.getSystemService(android.app.LocaleManager::class.java)
                    ?: return@runCatching false
                val sysTags = lm.applicationLocales.toLanguageTags()
                if (sysTags.isBlank()) {
                    false
                } else {
                    val saved = dataStore.appLocale.first()
                    saved == AppLocale.SYSTEM || saved.tag != sysTags
                }
            }.getOrDefault(false)
        }
    }

    LaunchedEffect(Unit) {
        navMode      = dataStore.navigationMode.first()
        themeMode    = dataStore.themeMode.first()
        colorSource  = dataStore.colorSource.first()
        savedColor   = dataStore.presetColor.first()
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
            SettingHeader(icon = Icons.Outlined.Dashboard, title = stringResource(R.string.setting_nav_layout))
            SegmentSwitch(
                options       = listOf(stringResource(R.string.setting_nav_auto), stringResource(R.string.setting_nav_bottom), stringResource(R.string.setting_nav_rail)),
                selectedIndex = navMode.ordinal,
                onSelect      = { scope.launch { dataStore.saveNavigationMode(NavigationMode.entries[it]) }; navMode = NavigationMode.entries[it] }
            )
        }

        // --- Group 3: Theme Color & Style ---
        SettingGroup {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingHeader(icon = Icons.Outlined.Palette, title = stringResource(R.string.setting_color_source))
                val colorOptions = if (supportsMonet) listOf(stringResource(R.string.setting_color_system), stringResource(R.string.setting_color_custom)) else listOf(stringResource(R.string.setting_color_custom))
                val selectedIndex = if (supportsMonet && colorSource == ColorSource.MONET) 0 else 1

                SegmentSwitch(
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
            SettingHeader(icon = Icons.Filled.DarkMode, title = stringResource(R.string.setting_theme_mode))
            SegmentSwitch(
                options       = listOf(stringResource(R.string.setting_theme_system), stringResource(R.string.setting_theme_light), stringResource(R.string.setting_theme_dark)),
                selectedIndex = themeMode.ordinal,
                onSelect      = { scope.launch { dataStore.saveThemeMode(ThemeMode.entries[it]) }; themeMode = ThemeMode.entries[it] }
            )
        }

        // --- Group 5: Language ---
        SettingGroup {
            LanguageItem(dataStore, appLocale, systemLocked = systemLangLocked)
        }

        // --- Group 6: System ---
        SettingGroup {
            NfcSettingsItem(context)
        }

        // --- Group 7: Debug ---
        SettingGroup {
            DebugSection(
                dataStore = dataStore,
                devMode = devModeForce,
                onDevModeToggle = { v -> devModeForce = v; scope.launch { dataStore.saveDevModeForceEmu(v) } }
            )
        }

        // --- Group 8: Export / Import ---
        SettingGroup {
            ExportImportSection(context)
        }

        // --- Group 9: Update ---
        SettingGroup {
            UpdateSection(dataStore = dataStore, context = context, scope = scope)
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
private fun SettingHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ExpressiveToggleItem(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.RoundedCorner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Colorize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
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
private fun LanguageItem(dataStore: AppDataStore, appLocale: AppLocale, systemLocked: Boolean) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var pending  by remember(appLocale) { mutableStateOf(appLocale) }
    val options  = AppLocale.entries.filter { it != AppLocale.SYSTEM }.map { 
        stringResource(it.labelRes) to it 
    }

    if (systemLocked && expanded) expanded = false

    val contentAlpha = if (systemLocked) 0.38f else 1f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!systemLocked) Modifier.clickable { expanded = !expanded } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.setting_language),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                if (systemLocked) {
                    Text(
                        stringResource(R.string.setting_language_system_managed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                } else if (!expanded) {
                    Text(
                        options.firstOrNull { it.second == appLocale }?.first ?: stringResource(R.string.setting_language_en),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded && !systemLocked,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
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
                        context.applyLocale(pending.tag)
                    }) { Text(stringResource(R.string.card_add_confirm)) }
                }
            }
        }
    }
}

@Composable
private fun NfcSettingsItem(context: android.content.Context) {
    Row(Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = Icons.Outlined.AccountBalanceWallet,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_nfc_default), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setting_nfc_default_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DebugSection(
    dataStore: AppDataStore,
    devMode: Boolean,
    onDevModeToggle: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_debug), style = MaterialTheme.typography.bodyLarge)
                if (!expanded) {
                    Text(
                        "${stringResource(R.string.setting_oobe_rerun)} · ${stringResource(R.string.setting_dev_force_emu)}",
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
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OobeRerunItem(dataStore)
                Spacer(Modifier.height(8.dp))
                DevModeItem(devMode = devMode, onToggle = onDevModeToggle)
            }
        }
    }
}

// ────── Update section ──────

@Composable
private fun UpdateSection(
    dataStore: AppDataStore,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    // State
    var showResultDialog by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf("") }
    var resultMsg by remember { mutableStateOf("") }
    var resultUrl by remember { mutableStateOf("") }
    var resultIsError by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    val customBase by dataStore.updateCustomBase.collectAsState(initial = "")
    val updateMode by dataStore.updateMode.collectAsState(initial = 0)
    val interval by dataStore.updateInterval.collectAsState(initial = UpdateInterval.OFF)

    // Interval selector state
    var intervalExpanded by remember { mutableStateOf(false) }

    // URL editor state
    var urlExpanded by remember { mutableStateOf(false) }

    // Interval labels
    val intervalOptions = listOf(
        UpdateInterval.OFF    to stringResource(R.string.setting_update_interval_off),
        UpdateInterval.MIN_30 to stringResource(R.string.setting_update_interval_30min),
        UpdateInterval.HOUR_1 to stringResource(R.string.setting_update_interval_1h),
        UpdateInterval.HOUR_2 to stringResource(R.string.setting_update_interval_2h),
        UpdateInterval.HOUR_6 to stringResource(R.string.setting_update_interval_6h),
        UpdateInterval.HOUR_12 to stringResource(R.string.setting_update_interval_12h),
        UpdateInterval.HOUR_24 to stringResource(R.string.setting_update_interval_24h),
        UpdateInterval.DAY_3  to stringResource(R.string.setting_update_interval_3d),
        UpdateInterval.DAY_7  to stringResource(R.string.setting_update_interval_7d),
    )

    // Changelog paging state
    var currentChangelog by remember { mutableStateOf<Changelog?>(null) }
    var currentChangelogUrl by remember { mutableStateOf<String?>(null) }
    var loadingPage by remember { mutableStateOf(false) }
    var latestMirrorPrefix by remember { mutableStateOf("") }

    // Result dialog
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { if (!checking && !loadingPage) showResultDialog = false },
            title = { Text(
                when {
                    checking            -> stringResource(R.string.oobe_checking)
                    resultIsError       -> stringResource(R.string.oobe_check_failed)
                    resultUrl.isNotBlank() -> stringResource(R.string.setting_update_new_title)
                    else                -> stringResource(R.string.setting_update_no_update)
                }
            ) },
            text = {
                if (checking) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.oobe_checking), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else when {
                    resultIsError -> Text(resultMsg.ifBlank { "Unknown error" })
                    else -> {
                        val maxH = LocalConfiguration.current.screenHeightDp * 0.7f
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxH.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (resultUrl.isNotBlank()) {
                                Text(stringResource(R.string.setting_update_new_msg, resultMsg))
                            }
                            val changelog = currentChangelog
                            if (changelog != null) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(4.dp))
                                Text("${changelog.version_name} · ${changelog.date}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                changelog.commits.forEach { commit ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        tonalElevation = 1.dp
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            val rawTitle = commit.title.trim('\n', ' ', '|')
                                            val rawBody = commit.body.trim('\n', ' ', '|')
                                            val title = if (rawTitle.isNotBlank()) rawTitle
                                                        else rawBody.lineSequence().firstOrNull()?.trim() ?: ""
                                            val body = if (rawTitle.isNotBlank()) rawBody
                                                       else rawBody.lineSequence().drop(1).joinToString("\n").trim()
                                            if (title.isNotBlank()) { Text(title, style = MaterialTheme.typography.bodyMedium) }
                                            if (body.isNotBlank()) { Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                            if (resultUrl.isBlank() && currentChangelog == null) {
                                Text(stringResource(R.string.setting_update_no_update))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (checking || loadingPage) {
                    // No buttons while loading
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val changelog = currentChangelog
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                enabled = changelog?.previous_changelog != null && !loadingPage,
                                onClick = {
                                    loadingPage = true
                                    scope.launch {
                                        val url = changelog!!.previous_changelog!!
                                        val finalUrl = if (latestMirrorPrefix.isNotBlank())
                                            "${latestMirrorPrefix.trimEnd('/')}/${url.trimStart('/')}" else url
                                        val prev = UpdateChecker.fetchChangelog(finalUrl)
                                        if (prev != null) { currentChangelog = prev; currentChangelogUrl = url }
                                        loadingPage = false
                                    }
                                }
                            ) { Text(stringResource(R.string.oobe_previous)) }
                            TextButton(
                                enabled = changelog?.next_changelog != null && !loadingPage,
                                onClick = {
                                    loadingPage = true
                                    scope.launch {
                                        val url = changelog!!.next_changelog!!
                                        val finalUrl = if (latestMirrorPrefix.isNotBlank())
                                            "${latestMirrorPrefix.trimEnd('/')}/${url.trimStart('/')}" else url
                                        val next = UpdateChecker.fetchChangelog(finalUrl)
                                        if (next != null) { currentChangelog = next; currentChangelogUrl = url }
                                        loadingPage = false
                                    }
                                }
                            ) { Text(stringResource(R.string.oobe_next)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (resultUrl.isNotBlank()) {
                                TextButton(onClick = {
                                    showResultDialog = false
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(resultUrl))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }) { Text(stringResource(R.string.setting_update_download)) }
                            }
                            if (resultUrl.isNotBlank()) {
                                TextButton(onClick = { showResultDialog = false }) { Text(stringResource(R.string.setting_update_later)) }
                            } else {
                                TextButton(onClick = { showResultDialog = false }) { Text(stringResource(R.string.card_add_confirm)) }
                            }
                        }
                    }
                }
            },
            dismissButton = {}
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── 1. Check Now ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !checking) {
                    showResultDialog = true
                    checking = true
                    scope.launch {
                        val gh = "https://raw.githubusercontent.com/C-F0x/KonamikU/info/update.json"
                        val mirror = "https://gh-proxy.com/"
                        latestMirrorPrefix = mirror
                        val state = UpdateChecker.check(
                            githubUrl = gh,
                            mirrorPrefix = mirror
                        )
                        if (state.error != null) {
                            resultIsError = true
                            resultMsg = state.error
                            resultUrl = ""
                            currentChangelog = null
                            currentChangelogUrl = null
                        } else if (state.hasUpdate) {
                            resultIsError = false
                            resultMsg = state.latestVersion
                            resultUrl = state.downloadUrl
                            currentChangelog = state.changelog
                            currentChangelogUrl = state.changelogUrl
                        } else {
                            resultIsError = false
                            resultMsg = state.latestVersion
                            resultUrl = ""
                            currentChangelog = state.changelog
                            currentChangelogUrl = state.changelogUrl
                        }
                        checking = false
                        dataStore.saveUpdateLastCheck(System.currentTimeMillis())
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_update_check_now), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (checking) "..." else stringResource(R.string.setting_update_check_now_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // ── 2. Check Interval ──
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { intervalExpanded = !intervalExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_update_interval), style = MaterialTheme.typography.bodyLarge)
                    if (!intervalExpanded) {
                        Text(
                            intervalOptions.firstOrNull { it.first == interval }?.second
                                ?: stringResource(R.string.setting_update_interval_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Icon(
                    imageVector = if (intervalExpanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = intervalExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    intervalOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    intervalExpanded = false
                                    scope.launch { dataStore.saveUpdateInterval(value); UpdateCheckWorker.schedule(context, value) }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = interval == value,
                                onClick = {
                                    intervalExpanded = false
                                    scope.launch { dataStore.saveUpdateInterval(value); UpdateCheckWorker.schedule(context, value) }
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // ── 3. Update URLs ──
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { urlExpanded = !urlExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_update_urls), style = MaterialTheme.typography.bodyLarge)
                    if (!urlExpanded) {
                        val modeLabel = when (updateMode) {
                            1 -> "GH-Proxy"
                            else -> "GitHub"
                        }
                        Text(modeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Icon(
                    imageVector = if (urlExpanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = urlExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Mode selector: GitHub / GH-Proxy
                    val modeOptions = listOf(
                        "GitHub"  to 0,
                        "GH-Proxy" to 1
                    )
                    val currentMode = updateMode.let { if (it in 0..1) it else 0 }
                    var selectedMode by remember { mutableStateOf(currentMode) }

                    // Per-mode latency results
                    var latencyGitHub  by remember { mutableStateOf(-1L) }
                    var latencyProxy   by remember { mutableStateOf(-1L) }
                    var validGitHub    by remember { mutableStateOf(false) }
                    var validProxy     by remember { mutableStateOf(false) }
                    var testingLatency by remember { mutableStateOf(false) }
                    var cooldown       by remember { mutableStateOf(0) }

                    LaunchedEffect(cooldown) { if (cooldown > 0) { delay(1000); cooldown-- } }

                    // Build URLs once
                    val gitHubUrl = "https://raw.githubusercontent.com/C-F0x/KonamikU/info/update.json"
                    val mirrorPrefix = "https://gh-proxy.com/"
                    val proxiedUrl = "${mirrorPrefix.trimEnd('/')}/${gitHubUrl.trimStart('/')}"

                    // Render each mode row
                    listOf(
                        Triple("GitHub", 0, latencyGitHub) to validGitHub,
                        Triple("GH-Proxy", 1, latencyProxy) to validProxy
                    ).forEach { (triple, isValid) ->
                        val (label, idx, latMs) = triple
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = idx }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedMode == idx, onClick = { selectedMode = idx })
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                color = if (testingLatency || latMs < 0 || isValid) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.error
                            )

                            // Latency or invalid flag on the right
                            if (testingLatency || latMs >= 0 || (latMs <= -1 && isValid)) {
                                Text(
                                    text = when {
                                        testingLatency          -> "…"
                                        latMs >= 0 && isValid   -> "${latMs}ms"
                                        latMs >= 0 && !isValid  -> "invalid"
                                        latMs < 0 && isValid    -> ""
                                        else                    -> "invalid"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        testingLatency          -> MaterialTheme.colorScheme.onSurfaceVariant
                                        latMs >= 0 && !isValid  -> MaterialTheme.colorScheme.error
                                        latMs >= 0 && latMs > 5000 -> MaterialTheme.colorScheme.error
                                        latMs >= 0 && latMs > 200  -> MaterialTheme.colorScheme.tertiary
                                        latMs >= 0              -> MaterialTheme.colorScheme.primary
                                        else                    -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Check Latency (tests all modes)
                        TextButton(
                            onClick = {
                                testingLatency = true; cooldown = 5
                                scope.launch {
                                    val gh = UpdateChecker.testAndValidate(gitHubUrl)
                                    val pr = UpdateChecker.testAndValidate(proxiedUrl)
                                    latencyGitHub = gh?.first ?: -1; validGitHub = gh?.second ?: false
                                    latencyProxy  = pr?.first ?: -1; validProxy  = pr?.second ?: false
                                    testingLatency = false
                                }
                            },
                            enabled = !testingLatency && cooldown == 0
                        ) { Text(stringResource(R.string.setting_update_latency)) }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { urlExpanded = false }) { Text(stringResource(R.string.card_add_cancel)) }
                            Button(onClick = {
                                urlExpanded = false
                                scope.launch {
                                    dataStore.saveUpdateMode(selectedMode)
                                }
                            }) { Text(stringResource(R.string.card_add_confirm)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OobeRerunItem(dataStore: AppDataStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.mipmap.ic_launcher_round),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(stringResource(R.string.setting_oobe_rerun_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    scope.launch {
                        dataStore.saveSetupVersion(-1L)
                        (context as? Activity)?.let { act ->
                            act.finishAffinity()
                            act.startActivity(
                                Intent(act, act::class.java).addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                )
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.card_add_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.card_add_cancel))
                }
            }
        )
    }

    Row(
        Modifier.fillMaxWidth().clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Dashboard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_oobe_rerun), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setting_oobe_rerun_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LatencyRow(label: String, ms: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = when {
                ms < 0   -> ""
                ms > 5000 -> "timeout"
                else     -> "${ms}ms"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                ms < 0    -> MaterialTheme.colorScheme.onSurfaceVariant
                ms > 5000 -> MaterialTheme.colorScheme.error
                ms > 200  -> MaterialTheme.colorScheme.tertiary
                else      -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun DevModeItem(devMode: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = Icons.Filled.Build,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_dev_force_emu), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setting_dev_force_emu_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = devMode, onCheckedChange = onToggle)
    }
}

// ────── Export / Import section ──────

@Composable
private fun ExportImportSection(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    val jsonManager = remember { JsonManager(context) }

    var expanded by remember { mutableStateOf(false) }

    // Toast helper
    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // --- Export launcher ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val cards = withContext(Dispatchers.IO) { jsonManager.loadCards() }
                if (cards.isEmpty()) {
                    withContext(Dispatchers.Main) { toast(context.getString(R.string.toast_export_empty)) }
                    return@launch
                }
                val json = Json { prettyPrint = true }
                val content = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(NfcCard.serializer()), cards)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                }
                withContext(Dispatchers.Main) { toast(context.getString(R.string.toast_export_success, cards.size)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(context.getString(R.string.toast_export_fail)) }
            }
        }
    }

    // --- Import launcher ---
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val rawJson = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                }
                if (rawJson.isBlank()) {
                    withContext(Dispatchers.Main) { toast(context.getString(R.string.toast_import_fail_parse)) }
                    return@launch
                }
                val json = Json { ignoreUnknownKeys = true }
                val importedCards: List<NfcCard> = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(NfcCard.serializer()), rawJson
                )

                // Validate: IDm must be 16 hex chars
                val invalidCount = importedCards.count { card ->
                    !(card.idm.length == 16 && card.idm.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' })
                }
                val validCards = importedCards.filter { card ->
                    card.idm.length == 16 && card.idm.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
                }

                if (validCards.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        toast(if (invalidCount > 0) context.getString(R.string.toast_import_fail_validation, invalidCount)
                        else context.getString(R.string.toast_import_fail_parse))
                    }
                    return@launch
                }

                // Load existing cards and merge (skip duplicates by IDm, case-insensitive)
                val existingCards = withContext(Dispatchers.IO) { jsonManager.loadCards() }
                val existingIdms = existingCards.map { it.idm.uppercase() }.toSet()

                val newCards = validCards.filter { it.idm.uppercase() !in existingIdms }.map {
                    it.copy(id = UUID.randomUUID().toString())
                }
                val duplicateCount = validCards.size - newCards.size

                if (newCards.isEmpty()) {
                    val msg = buildString {
                        if (invalidCount > 0) append(context.getString(R.string.toast_import_fail_validation, invalidCount))
                        if (duplicateCount > 0) {
                            if (invalidCount > 0) append("  ")
                            append(context.getString(R.string.toast_import_duplicate, duplicateCount))
                        }
                    }
                    withContext(Dispatchers.Main) { toast(msg.ifBlank { context.getString(R.string.toast_import_fail_parse) }) }
                    return@launch
                }

                val merged = existingCards + newCards
                withContext(Dispatchers.IO) { jsonManager.saveCards(merged) }

                // Notify MainScreen to refresh
                CardListRefreshEvent.value = System.currentTimeMillis()

                val msg = buildString {
                    append(context.getString(R.string.toast_import_success, newCards.size))
                    if (invalidCount > 0) append("  ").append(context.getString(R.string.toast_import_fail_validation, invalidCount))
                    if (duplicateCount > 0) append("  ").append(context.getString(R.string.toast_import_duplicate, duplicateCount))
                }
                withContext(Dispatchers.Main) { toast(msg) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(context.getString(R.string.toast_import_fail_parse)) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_export_import), style = MaterialTheme.typography.bodyLarge)
                if (!expanded) {
                    Text(
                        "${stringResource(R.string.setting_export)} · ${stringResource(R.string.setting_import)}",
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
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                // Export row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportLauncher.launch("konamiku_cards.json") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.setting_export), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.setting_export_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Import row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { importLauncher.launch(arrayOf("application/json")) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.setting_import), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.setting_import_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
