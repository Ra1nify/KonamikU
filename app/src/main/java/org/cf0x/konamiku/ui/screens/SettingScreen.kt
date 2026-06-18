package org.cf0x.konamiku.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

    // 系统语言接管检测（修复版：只在我们未设置时才算锁定）
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

        // --- Group 6: OOBE ---
        SettingGroup {
            OobeRerunItem(dataStore)
        }

        // --- Group 7: System ---
        SettingGroup {
            NfcSettingsItem(context)
        }

        // --- Group 8: Developer ---
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
