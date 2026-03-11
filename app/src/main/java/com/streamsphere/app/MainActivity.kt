package com.streamsphere.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.streamsphere.app.ui.navigation.*
import com.streamsphere.app.ui.screens.*
import com.streamsphere.app.ui.theme.StreamSphereTheme
import dagger.hilt.android.AndroidEntryPoint







@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHANNEL_ID  = "extra_channel_id"
        const val EXTRA_STREAM_URL  = "extra_stream_url"
        const val EXTRA_FULLSCREEN  = "extra_fullscreen"  // from widget tap
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startChannelId  = intent?.getStringExtra(EXTRA_CHANNEL_ID)
        val startFullscreen = intent?.getBooleanExtra(EXTRA_FULLSCREEN, false) ?: false
        
        val darkMode by settingsViewModel.darkMode.collectAsState()
        
        setContent {
            StreamSphereTheme(darkTheme = darkMode) {
                StreamSphereUI(
                    startChannelId  = startChannelId,
                    startFullscreen = startFullscreen
                )
            }
        }
    }
}

@Composable
fun StreamSphereUI(
    startChannelId: String? = null,
    startFullscreen: Boolean = false
) {
    val navController = rememberNavController()

    LaunchedEffect(startChannelId) {
        if (!startChannelId.isNullOrBlank()) {
            navController.navigate(Screen.Detail.createRoute(startChannelId))
        }
    }

    Scaffold(
        modifier  = Modifier.fillMaxSize(),
        bottomBar = { StreamSphereBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Home.route,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 } },
            exitTransition   = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 10 } },
            popEnterTransition  = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 10 } },
            popExitTransition   = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 10 } }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onChannelClick = { id -> navController.navigate(Screen.Detail.createRoute(id)) })
            }
            composable(Screen.Search.route) {
                SearchScreen(onChannelClick = { id -> navController.navigate(Screen.Detail.createRoute(id)) })
            }
            composable(Screen.Favourites.route) {
                FavouritesScreen(onChannelClick = { id -> navController.navigate(Screen.Detail.createRoute(id)) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route     = Screen.Detail.route,
                arguments = listOf(androidx.navigation.navArgument("channelId") {
                    type = androidx.navigation.NavType.StringType
                })
            ) { backStack ->
                val channelId = backStack.arguments?.getString("channelId") ?: return@composable
                DetailScreen(
                    channelId        = channelId,
                    autoPlay         = channelId == startChannelId,
                    startInFullscreen = startFullscreen && channelId == startChannelId,
                    onBack           = navController::popBackStack
                )
            }
        }
    }
}

@Composable
fun StreamSphereBottomBar(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar      = currentDestination?.route != Screen.Detail.route

    AnimatedVisibility(
        visible = showBottomBar,
        enter   = slideInVertically { it } + fadeIn(),
        exit    = slideOutVertically { it } + fadeOut()
    ) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            bottomNavItems.forEach { item ->
                val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick  = {
                        navController.navigate(item.screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    icon   = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, item.label) },
                    label  = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = MaterialTheme.colorScheme.primary,
                        selectedTextColor   = MaterialTheme.colorScheme.primary,
                        indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
