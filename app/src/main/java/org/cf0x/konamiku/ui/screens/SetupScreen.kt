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
import org.cf0x.konamiku.util.applyLocale

/**
 * 读取指定 Locale 下的字符串资源。
 * 如果 locale == null 则使用系统当前 locale。
 */
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

    // ── 读取所有语言的 greeting ──
    val allGreetings = remember {
        AppLocale.entries.filter { it != AppLocale.SYSTEM }.map { locale ->
            val config = Configuration(context.resources.configuration).apply {
                setLocale(locale.toLocale())
            }
            locale to context.createConfigurationContext(config).getString(R.string.greeting)
        }
    }

    // ── 系统语言接管检测（修复版：只在我们未设置时才算锁定） ──
    val systemLangLocked by produceState(initialValue = false) {
        if (Build.VERSION.SDK_INT >= 33) {
            value = runCatching {
                val lm = context.getSystemService(android.app.LocaleManager::class.java)
                    ?: return@runCatching false
                val sysTags = lm.applicationLocales.toLanguageTags()
                if (sysTags.isBlank()) {
                    false // 系统没有设置 per-app 语言
                } else {
                    val saved = dataStore.appLocale.first()
                    // 系统设置了 per-app 语言，且和我们保存的不一致 → 系统管理
                    saved == AppLocale.SYSTEM || saved.tag != sysTags
                }
            }.getOrDefault(false)
        }
    }

    // ── 用户选择的语言（null = 沿用当前） ──
    var pendingLocale by remember { mutableStateOf<AppLocale?>(null) }
    val effectiveLocale = pendingLocale

    // 第 4 页自动过渡
    var startFinish by remember { mutableStateOf(false) }

    // 当翻到第 4 页时自动触发过渡动画
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 3) {
            startFinish = true
        }
    }

    // 过渡动画结束后自动导航
    LaunchedEffect(startFinish) {
        if (startFinish) {
            delay(1400)
            // 持久化语言选择
            if (pendingLocale != null && !systemLangLocked) {
                dataStore.saveAppLocale(pendingLocale!!)
                context.applyLocale(pendingLocale!!.tag)
            }
            // 标记 OOBE 已完成
            dataStore.saveSetupVersion(BuildConfig.VERSION_CODE.toLong())
            // 重启 Activity 进入主界面
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

    // ── 底部按钮栏（第 1-3 页显示，第 4 页隐藏） ──
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
                        // ← 上一步（首页隐藏，始终靠左）
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

                        // 圆点指示器（始终居中）
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

                        // 下一步（始终靠右）
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
                    2 -> FeaturesPage(effectiveLocale)
                    3 -> FinishPage(visible = startFinish, effectiveLocale = effectiveLocale)
                }
            }
        }
    }
}

// ────── 页面 1：欢迎 ──────

@Composable
private fun WelcomePage(greetings: List<Pair<AppLocale, String>>) {
    val greetingsTexts = remember { greetings.map { it.second } }

    // 轮换问候语
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

        // ── App 图标 ──
        Surface(
            modifier = Modifier.size(100.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                painter = painterResource(R.mipmap.ic_launcher_round),
                contentDescription = null,
                modifier = Modifier.padding(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── 名称：KonamikU ──
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(64.dp))

        // ── 轮换问候语（Crossfade，固定宽度防跳动） ──
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

// ────── 页面 2：语言选择 ──────

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

// ────── 页面 3：功能介绍（占位） ──────

@Composable
private fun FeaturesPage(effectiveLocale: AppLocale?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = localizedString(effectiveLocale, R.string.oobe_features_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = localizedString(effectiveLocale, R.string.oobe_features_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))

        // Hello World 占位
        Surface(
            modifier = Modifier.size(200.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = localizedString(effectiveLocale, R.string.oobe_features_hello),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// ────── 页面 4：过渡页面 ──────

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
                painter = painterResource(R.mipmap.ic_launcher_round),
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
