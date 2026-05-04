package org.cf0x.konamiku.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.NavigationMode
import org.cf0x.konamiku.navigation.Screen
import org.cf0x.konamiku.navigation.navDestinations
import org.cf0x.konamiku.ui.screens.MainScreen
import org.cf0x.konamiku.ui.screens.SettingScreen
import org.cf0x.konamiku.ui.screens.ToolsScreen

@Composable
fun MainLayout(
    dataStore: AppDataStore,
    navController: NavHostController = rememberNavController()
) {
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

    fun navigate(screen: Screen) {
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

            Box(modifier = Modifier.weight(1f)) {
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