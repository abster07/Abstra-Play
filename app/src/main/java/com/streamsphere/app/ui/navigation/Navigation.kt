package com.streamsphere.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Home       : Screen("home")
    object Favourites : Screen("favourites")
    object Search     : Screen("search")
    object Settings   : Screen("settings")
    object Dlna       : Screen("dlna")
    object Detail     : Screen("detail/{channelId}") {
        fun createRoute(id: String) = "detail/$id"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home,       "Home",       Icons.Filled.Home,          Icons.Outlined.Home),
    BottomNavItem(Screen.Search,     "Discover",   Icons.Filled.Explore,       Icons.Outlined.Explore),
    BottomNavItem(Screen.Favourites, "Favourites", Icons.Filled.Favorite,      Icons.Outlined.FavoriteBorder),
    BottomNavItem(Screen.Dlna,       "Cast",       Icons.Filled.Cast,          Icons.Outlined.Cast),
    BottomNavItem(Screen.Settings,   "Settings",   Icons.Filled.Settings,      Icons.Outlined.Settings)
)
