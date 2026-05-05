package org.cf0x.konamiku.ui.layout

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.NavigationMode
import org.cf0x.konamiku.navigation.Screen
import org.cf0x.konamiku.navigation.navDestinations
import org.cf0x.konamiku.ui.screens.MainScreen
import org.cf0x.konamiku.ui.screens.SettingScreen
import org.cf0x.konamiku.ui.screens.ToolsScreen

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun MainLayout(
    dataStore: AppDataStore,
    navController: NavHostController = rememberNavController()
) {
    val scope         = rememberCoroutineScope()
    val navMode       by dataStore.navigationMode.collectAsState(initial = NavigationMode.AUTO)
    val configuration = LocalConfiguration.current
    val isWideScreen  = configuration.screenWidthDp >= 600

    val showBottomBar = when (navMode) {
        NavigationMode.AUTO   -> !isWideScreen
        NavigationMode.BOTTOM -> true
        NavigationMode.RAIL   -> false
    }

    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var navLocked by remember { mutableStateOf(false) }

    fun navigate(screen: Screen) {
        if (navLocked) return
        navLocked = true
        scope.launch {
            delay(350)
            navLocked = false
        }
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    val labelRes: Map<String, Int> = mapOf(
        Screen.Main.route     to R.string.nav_cards,
        Screen.Tools.route    to R.string.nav_tools,
        Screen.Settings.route to R.string.nav_settings
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
                                Text(stringResource(labelRes[screen.route] ?: R.string.app_name))
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
                NavigationRail {
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
                                    awaitPointerEvent(PointerEventPass.Initial)
                                        .changes.forEach { it.consume() }
                                }
                            }
                        } else Modifier
                    )
            ) {
                NavHost(
                    navController    = navController,
                    startDestination = Screen.Main.route
                ) {
                    composable(Screen.Main.route)     { MainScreen(dataStore = dataStore) }
                    composable(Screen.Tools.route)    { ToolsScreen() }
                    composable(Screen.Settings.route) { SettingScreen(dataStore = dataStore) }
                }
            }
        }
    }
}