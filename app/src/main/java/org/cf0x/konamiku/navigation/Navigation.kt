package org.cf0x.konamiku.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Main : Screen(
        route          = "main",
        label          = "Cards",
        selectedIcon   = Icons.Filled.CreditCard,
        unselectedIcon = Icons.Outlined.CreditCard
    )
    object Tools : Screen(
        route          = "tools",
        label          = "Tools",
        selectedIcon   = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build
    )
    object Settings : Screen(
        route          = "settings",
        label          = "Settings",
        selectedIcon   = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

val navDestinations = listOf(Screen.Main, Screen.Tools, Screen.Settings)