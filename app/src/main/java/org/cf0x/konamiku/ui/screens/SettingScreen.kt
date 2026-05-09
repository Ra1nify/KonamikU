package org.cf0x.konamiku.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

    val navMode     by dataStore.navigationMode.collectAsState(initial = NavigationMode.AUTO)
    val themeMode   by dataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val colorSource by dataStore.colorSource.collectAsState(initial = ColorSource.PRESET)
    val savedColor  by dataStore.presetColor.collectAsState(initial = Color(0xFF6750A4))
    val appLocale   by dataStore.appLocale.collectAsState(initial = AppLocale.SYSTEM)
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var previewColor by remember(savedColor) { mutableStateOf(savedColor) }
    var showPicker   by remember { mutableStateOf(false) }

    LaunchedEffect(colorSource) {
        if (colorSource != ColorSource.PRESET) showPicker = false
    }

    data class LocaleOption(val label: String, val value: AppLocale)

    var localeExpanded by remember { mutableStateOf(false) }
    var pendingLocale  by remember(appLocale) { mutableStateOf(appLocale) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            stringResource(R.string.setting_appearance),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        SegmentSwitch(
            label         = stringResource(R.string.setting_nav_layout),
            options       = listOf(
                stringResource(R.string.setting_nav_auto),
                stringResource(R.string.setting_nav_bottom),
                stringResource(R.string.setting_nav_rail)
            ),
            selectedIndex = navMode.ordinal,
            onSelect      = { scope.launch { dataStore.saveNavigationMode(NavigationMode.entries[it]) } }
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val colorOptions = if (supportsMonet)
                listOf(
                    stringResource(R.string.setting_color_system),
                    stringResource(R.string.setting_color_custom)
                )
            else
                listOf(stringResource(R.string.setting_color_custom))

            val selectedColorIndex = if (supportsMonet && colorSource == ColorSource.MONET) 0 else 1

            SegmentSwitch(
                label         = stringResource(R.string.setting_color_source),
                options       = colorOptions,
                selectedIndex = selectedColorIndex,
                onSelect      = { index ->
                    val newSource = if (supportsMonet && index == 0) ColorSource.MONET
                    else ColorSource.PRESET
                    scope.launch { dataStore.saveColorSource(newSource) }
                    if (newSource == ColorSource.PRESET && colorSource == ColorSource.PRESET) {
                        showPicker = !showPicker
                    }
                }
            )

            AnimatedVisibility(
                visible = colorSource == ColorSource.PRESET && showPicker,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorPickerWheel(
                        initialColor   = previewColor,
                        onColorChanged = { previewColor = it },
                        modifier       = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { previewColor = savedColor; showPicker = false }) {
                            Text(stringResource(R.string.card_add_cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            scope.launch { dataStore.savePresetColor(previewColor.toArgb()) }
                            showPicker = false
                        }) {
                            Text(stringResource(R.string.card_add_confirm))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = colorSource == ColorSource.PRESET && !showPicker,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier       = Modifier.size(28.dp),
                        shape          = MaterialTheme.shapes.small,
                        color          = savedColor,
                        tonalElevation = 2.dp
                    ) {}
                    Text(
                        text  = "#%06X".format(savedColor.toArgb() and 0xFFFFFF),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showPicker = true }) {
                        Text(stringResource(R.string.setting_color_custom))
                    }
                }
            }
        }

        SegmentSwitch(
            label         = stringResource(R.string.setting_theme_mode),
            options       = listOf(
                stringResource(R.string.setting_theme_system),
                stringResource(R.string.setting_theme_light),
                stringResource(R.string.setting_theme_dark)
            ),
            selectedIndex = themeMode.ordinal,
            onSelect      = { scope.launch { dataStore.saveThemeMode(ThemeMode.entries[it]) } }
        )

        HorizontalDivider()

        Text(
            stringResource(R.string.setting_language),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        val localeOptions = listOf(
            LocaleOption(stringResource(R.string.setting_language_system), AppLocale.SYSTEM),
            LocaleOption(stringResource(R.string.setting_language_zh),     AppLocale.ZH_CN),
            LocaleOption(stringResource(R.string.setting_language_en),     AppLocale.EN_US),
        )

        AnimatedVisibility(visible = !localeExpanded, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text  = localeOptions.first { it.value == appLocale }.label,
                    style = MaterialTheme.typography.bodyLarge
                )
                TextButton(onClick = { localeExpanded = true }) {
                    Text(stringResource(R.string.setting_language_change))
                }
            }
        }

        AnimatedVisibility(
            visible = localeExpanded,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                localeOptions.forEach { option ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { pendingLocale = option.value }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = pendingLocale == option.value,
                            onClick  = { pendingLocale = option.value }
                        )
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        pendingLocale  = appLocale
                        localeExpanded = false
                    }) { Text(stringResource(R.string.card_add_cancel)) }

                    Spacer(Modifier.width(8.dp))

                    Button(onClick = {
                        localeExpanded = false
                        if (pendingLocale == appLocale) return@Button
                        scope.launch { dataStore.saveAppLocale(pendingLocale) }
                        val localeList = if (pendingLocale == AppLocale.SYSTEM)
                            LocaleListCompat.getEmptyLocaleList()
                        else
                            LocaleListCompat.forLanguageTags(pendingLocale.tag)
                        AppCompatDelegate.setApplicationLocales(localeList)
                        (context as? Activity)?.let { activity ->
                            activity.finish()
                            activity.startActivity(activity.intent)
                            activity.overridePendingTransition(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                            )
                        }
                    }) { Text(stringResource(R.string.card_add_confirm)) }
                }
            }
        }

        HorizontalDivider()

        Text(
            stringResource(R.string.setting_nfc_module),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                .padding(vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.setting_nfc_default),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    stringResource(R.string.setting_nfc_default_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}