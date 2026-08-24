package com.sybbox.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.sybbox.R

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Servers : Screen("servers")
    data object Routing : Screen("routing")
    data object Settings : Screen("settings")
    data object Logs : Screen("logs")
    data object PerApp : Screen("per_app")
    data object Scanner : Screen("scanner")
}

data class BottomNavItem(
    val screen: Screen,
    @StringRes val title: Int,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, R.string.nav_home, Icons.Rounded.Home),
    BottomNavItem(Screen.Servers, R.string.nav_servers, Icons.Rounded.Storage),
    BottomNavItem(Screen.Settings, R.string.nav_settings, Icons.Rounded.Tune),
)
