package org.cf0x.konamiku.ui.layout

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.NavigationMode
import org.cf0x.konamiku.navigation.Screen
import org.cf0x.konamiku.navigation.navDestinations
import org.cf0x.konamiku.ui.screens.MainScreen
import org.cf0x.konamiku.ui.screens.SettingScreen
import org.cf0x.konamiku.ui.screens.SetupScreen
import org.cf0x.konamiku.ui.screens.ToolsScreen
import org.cf0x.konamiku.ui.viewmodels.StatusViewModel

/** How long taps are swallowed while a screen transition plays. */
private const val NAV_DEBOUNCE_MS = 400L

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun MainLayout(
    dataStore: AppDataStore,
    navController: NavHostController = rememberNavController()
) {
    val navMode       by dataStore.navigationMode.collectAsState(initial = NavigationMode.AUTO)
    val configuration = LocalConfiguration.current
    val statusViewModel: StatusViewModel = viewModel(LocalContext.current as ComponentActivity)
    val isWideScreen  = configuration.screenWidthDp >= 600

    val showBottomBar = when (navMode) {
        NavigationMode.AUTO   -> !isWideScreen
        NavigationMode.BOTTOM -> true
        NavigationMode.RAIL   -> false
    }

    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Wait for DataStore to return the actual value before making navigation decisions
    // (prevents mis-navigation caused by initial = -1L)
    var setupVersionReady by remember { mutableStateOf(false) }
    var setupVersion by remember { mutableStateOf(-1L) }

    LaunchedEffect(Unit) {
        setupVersion = dataStore.setupVersion.first()
        setupVersionReady = true
    }

    // Don't render anything before data is ready to avoid flicker
    if (!setupVersionReady) return

    val isOobe = currentDestination?.route == Screen.SetupNew.route || setupVersion == -1L

    // OOBE mode: fullscreen render without bottom/rail bars
    if (isOobe) {
        SetupScreen(dataStore = dataStore)
        return
    }

    // Transient, deliberately NOT rememberSaveable.
    //
    // This flag is only meant to swallow taps during the 400 ms navigation
    // animation. Saving it meant a rotation inside that window restored
    // `true` on the new instance while the coroutine that clears it had been
    // cancelled with the old scope - navigation stayed locked forever, and the
    // pointerInput below kept consuming every touch. `remember` resets it on
    // recreation, which is exactly the desired behaviour for a short-lived
    // debounce.
    var navLocked by remember { mutableStateOf(false) }

    // If the scope dies mid-animation the flag would otherwise stay set.
    LaunchedEffect(navLocked) {
        if (navLocked) {
            delay(NAV_DEBOUNCE_MS)
            navLocked = false
        }
    }

    fun navigate(screen: Screen) {
        if (navLocked) return
        if (currentDestination?.route == screen.route) return

        navLocked = true
        navController.navigate(screen.route) {
            popUpTo(navController.graph.id) {
                inclusive = true
            }
            launchSingleTop = true
            restoreState    = false
        }
    }

    val labelRes: Map<String, Int> = mapOf(
        Screen.Main.route     to R.string.nav_cards,
        Screen.Tools.route    to R.string.nav_tools,
        Screen.Settings.route to R.string.nav_settings
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    navDestinations.forEach { screen ->
                        val isSelected =
                            currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = isSelected,
                            onClick  = { navigate(screen) },
                            icon     = {
                                Icon(
                                    imageVector        = if (isSelected) screen.selectedIcon
                                    else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = {
                                Text(
                                    text  = stringResource(labelRes[screen.route] ?: R.string.app_name),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!showBottomBar) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    header = {
                        Spacer(Modifier.height(8.dp))
                    }
                ) {
                    navDestinations.forEach { screen ->
                        val isSelected =
                            currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationRailItem(
                            selected = isSelected,
                            onClick  = { navigate(screen) },
                            icon     = {
                                Icon(
                                    imageVector        = if (isSelected) screen.selectedIcon
                                    else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = {
                                Text(stringResource(labelRes[screen.route] ?: R.string.app_name))
                            }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (navLocked) Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        } else Modifier
                    )
            ) {
                NavHost(
                    navController    = navController,
                    startDestination = Screen.Main.route,
                    enterTransition = { 
                        fadeIn(tween(300)) + slideInHorizontally(tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { 40 } + scaleIn(initialScale = 0.95f, animationSpec = tween(400))
                    },
                    exitTransition  = { 
                        fadeOut(tween(250)) + slideOutHorizontally(tween(300)) { -40 }
                    }
                ) {
                    composable(Screen.Main.route)     { MainScreen(dataStore = dataStore, statusViewModel = statusViewModel) }
                    composable(Screen.Tools.route)    { ToolsScreen() }
                    composable(Screen.Settings.route) { SettingScreen(dataStore = dataStore) }
                }
            }
        }
    }
}