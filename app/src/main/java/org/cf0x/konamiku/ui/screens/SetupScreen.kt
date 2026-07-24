package org.cf0x.konamiku.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.cf0x.konamiku.BuildConfig
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.AppLocale
import org.cf0x.konamiku.system.StatusDetector
import org.cf0x.konamiku.util.applyLocale
import org.cf0x.konamiku.xposed.NfcHookProber

@Composable
private fun localizedString(locale: AppLocale?, resId: Int): String {
    val context = LocalContext.current
    return remember(resId, locale, context.resources.configuration) {
        if (locale != null && locale != AppLocale.SYSTEM) {
            val cfg = Configuration(context.resources.configuration).apply {
                setLocale(locale.toLocale())
            }
            context.createConfigurationContext(cfg).getString(resId)
        } else {
            context.getString(resId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(dataStore: AppDataStore) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val pagerState  = rememberPagerState(pageCount = { 4 })

    // --- Load greetings for all locales ---
    val allGreetings = remember {
        AppLocale.entries.filter { it != AppLocale.SYSTEM }.map { locale ->
            val config = Configuration(context.resources.configuration).apply {
                setLocale(locale.toLocale())
            }
            locale to context.createConfigurationContext(config).getString(R.string.greeting)
        }
    }

    // --- Detect system-managed locale (locked if per-app locale is set and mismatches saved) ---
    val systemLangLocked by produceState(initialValue = false) {
        if (Build.VERSION.SDK_INT >= 33) {
            value = runCatching {
                val lm = context.getSystemService(android.app.LocaleManager::class.java)
                    ?: return@runCatching false
                val sysTags = lm.applicationLocales.toLanguageTags()
                if (sysTags.isBlank()) {
                    false // System per-app locale not set
                } else {
                    val saved = dataStore.appLocale.first()
                    // System per-app locale set and mismatches saved -> system managed
                    saved == AppLocale.SYSTEM || saved.tag != sysTags
                }
            }.getOrDefault(false)
        }
    }

    // --- User selected locale (null = follow current) ---
    var pendingLocale by remember { mutableStateOf<AppLocale?>(null) }
    val effectiveLocale = pendingLocale

    // Auto-transition for page 4
    var startFinish by remember { mutableStateOf(false) }

    // Trigger transition when reaching page 4
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 3) {
            startFinish = true
        }
    }

    // Auto-navigate after transition
    LaunchedEffect(startFinish) {
        if (startFinish) {
            delay(1400)
            // Persist locale selection
            if (pendingLocale != null && !systemLangLocked) {
                dataStore.saveAppLocale(pendingLocale!!)
                context.applyLocale(pendingLocale!!.tag)
            }
            // Mark OOBE as finished
            dataStore.saveSetupVersion(BuildConfig.VERSION_CODE.toLong())
            // Restart Activity to enter main screen
            (context as? Activity)?.let { act ->
                act.finishAffinity()
                act.startActivity(
                    Intent(act, act::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                )
            }
        }
    }

    // --- Bottom bar (visible for pages 1-3, hidden for page 4) ---
    val showBottomBar = pagerState.currentPage < 3

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        // Previous (hidden on first page, left-aligned)
                        if (pagerState.currentPage > 0) {
                            TextButton(
                                modifier = Modifier.align(Alignment.CenterStart),
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            ) {
                                Text(localizedString(effectiveLocale, R.string.oobe_previous))
                            }
                        }

                        // Page indicator (centered)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                                        .background(
                                            color = if (pagerState.currentPage == index)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        // Next (right-aligned)
                        TextButton(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        ) {
                            Text(
                                localizedString(effectiveLocale, R.string.oobe_next)
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        HorizontalPager(
            state      = pagerState,
            modifier   = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            userScrollEnabled = startFinish.not()
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                when (page) {
                    0 -> WelcomePage(allGreetings)
                    1 -> LanguagePage(
                        systemLocked = systemLangLocked,
                        dataStore    = dataStore,
                        currentSelection = pendingLocale,
                        onSelect     = { pendingLocale = it },
                        effectiveLocale = effectiveLocale
                    )
                    2 -> StatusPage(effectiveLocale)
                    3 -> FinishPage(visible = startFinish, effectiveLocale = effectiveLocale)
                }
            }
        }
    }
}

// --- Page 1: Welcome ---

@Composable
private fun WelcomePage(greetings: List<Pair<AppLocale, String>>) {
    val greetingsTexts = remember { greetings.map { it.second } }

    // Rotate greetings
    var currentIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2500)
            currentIndex = (currentIndex + 1) % greetingsTexts.size
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        // --- App Icon ---
        Surface(
            modifier = Modifier.size(100.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.padding(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(12.dp))

        // --- App Name ---
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(64.dp))

        // --- Rotating Greetings (Crossfade) ---
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = currentIndex,
                animationSpec = tween(500),
                label = "greeting_crossfade"
            ) { index ->
                Text(
                    text = greetingsTexts[index],
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))
    }
}

// --- Page 2: Language Selection ---

@Composable
private fun LanguagePage(
    systemLocked: Boolean,
    dataStore: AppDataStore,
    currentSelection: AppLocale?,
    onSelect: (AppLocale) -> Unit,
    effectiveLocale: AppLocale?
) {
    val context = LocalContext.current
    val currentAppLocale by dataStore.appLocale.collectAsState(initial = AppLocale.EN_US)
    val selected = currentSelection ?: currentAppLocale

    val options = AppLocale.entries.filter { it != AppLocale.SYSTEM }.map {
        stringResource(it.labelRes) to it
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = localizedString(effectiveLocale, R.string.oobe_language_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        if (systemLocked) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = localizedString(effectiveLocale, R.string.oobe_language_system_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (label, value) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected == value)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = { onSelect(value) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected == value,
                                onClick = { onSelect(value) }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Page 3: Status Check ---

@Composable
private fun StatusPage(effectiveLocale: AppLocale?) {
    val context = LocalContext.current

    // Hardware status: synchronous fetch
    val hardware = remember { StatusDetector.detectHardware(context) }

    // Deep status: asynchronous fetch with timeout/polling
    var rootStatus by remember { mutableStateOf<StatusDetector.RootStatus?>(null) }
    var xposedActive by remember { mutableStateOf(false) }
    var xposedNeedsRestart by remember { mutableStateOf(false) }
    var xposedProvider by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Root detection (3s timeout)
        rootStatus = kotlinx.coroutines.withTimeoutOrNull(3_000) {
            StatusDetector.detectRoot()
        } ?: StatusDetector.RootStatus(available = false)

        // Xposed detection (5 retries, 1.5s interval)
        var hooked = false
        repeat(5) {
            hooked = NfcHookProber.probe(context)
            if (hooked) return@repeat
            delay(1500)
        }
        xposedActive = hooked
        xposedProvider = if (hooked) {
            "${org.cf0x.konamiku.xposed.XposedState.frameworkName} ${org.cf0x.konamiku.xposed.XposedState.frameworkVersion}".trim()
        } else ""

        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = localizedString(effectiveLocale, R.string.oobe_status_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // NFC Hardware
        StatusCheckRow(
            icon = "📡",
            label = localizedString(effectiveLocale, R.string.status_nfc_rf),
            ok = hardware.hasNfc && hardware.nfcEnabled,
            detail = if (hardware.hasNfc)
                localizedString(effectiveLocale, if (hardware.nfcEnabled) R.string.status_rf_on else R.string.status_rf_off)
            else
                localizedString(effectiveLocale, R.string.status_unavailable)
        )

        Spacer(Modifier.height(8.dp))

        // HCE-F
        StatusCheckRow(
            icon = "💳",
            label = localizedString(effectiveLocale, R.string.status_hcef),
            ok = hardware.hcefSupported,
            detail = localizedString(effectiveLocale,
                if (hardware.hcefSupported) R.string.status_available else R.string.status_unavailable
            )
        )

        Spacer(Modifier.height(8.dp))

        // Root
        StatusCheckRow(
            icon = "🛡️",
            label = localizedString(effectiveLocale, R.string.status_root),
            ok = rootStatus?.available == true,
            detail = if (!loading && rootStatus != null)
                (rootStatus?.available == true).let { ok ->
                    if (ok) rootStatus?.provider?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                    else localizedString(effectiveLocale, R.string.status_unavailable)
                }
            else
                localizedString(effectiveLocale, R.string.status_unavailable)
        )

        Spacer(Modifier.height(8.dp))

        // Xposed
        StatusCheckRow(
            icon = "🔌",
            label = localizedString(effectiveLocale, R.string.status_lsposed),
            ok = xposedActive,
            detail = if (!loading) {
                if (xposedActive) xposedProvider.ifBlank {
                    localizedString(effectiveLocale, R.string.status_available)
                } else if (xposedNeedsRestart) {
                    localizedString(effectiveLocale, R.string.status_xposed_needs_restart)
                } else {
                    localizedString(effectiveLocale, R.string.status_unavailable)
                }
            } else {
                localizedString(effectiveLocale, R.string.oobe_status_loading)
            }
        )
    }
}

@Composable
private fun StatusCheckRow(icon: String, label: String, ok: Boolean, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (ok) "✓" else "✗",
                color = if (ok) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinishPage(visible: Boolean, effectiveLocale: AppLocale?) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800),
        label = "finish_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.padding(24.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = localizedString(effectiveLocale, R.string.oobe_finish_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = localizedString(effectiveLocale, R.string.oobe_finish_subtitle),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
