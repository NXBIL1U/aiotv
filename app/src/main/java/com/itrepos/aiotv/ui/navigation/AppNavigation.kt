package com.itrepos.aiotv.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.itrepos.aiotv.ui.screen.addons.AddonsScreen
import com.itrepos.aiotv.ui.screen.detail.DetailScreen
import com.itrepos.aiotv.ui.screen.home.HomeScreen
import com.itrepos.aiotv.ui.screen.live.LiveTvScreen
import com.itrepos.aiotv.ui.screen.player.PlayerScreen
import com.itrepos.aiotv.ui.screen.search.SearchScreen
import com.itrepos.aiotv.ui.screen.settings.SettingsScreen
import java.net.URLDecoder

// Top-level destinations reachable from the nav rail / bottom bar. Navigating
// between these should switch tabs (single instance, saved state) rather than
// stack duplicates; everything else (Detail, Player) is pushed on top.
private val topLevelRoutes = setOf(
    Screen.Home.route,
    Screen.Search.route,
    Screen.Guide.route,
    Screen.Live.route,
    Screen.Watchlist.route,
    Screen.Addons.route,
    Screen.Settings.route,
)

@Composable
fun AppNavigation(isTv: Boolean, windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedScreen = backStackEntry?.destination?.route ?: Screen.Home.route

    val onNavigate: (String) -> Unit = { route ->
        if (route in topLevelRoutes) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route)
        }
    }

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                isTv = isTv,
                windowSizeClass = windowSizeClass,
                selectedScreen = selectedScreen,
                onNavigate = onNavigate,
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                isTv = isTv,
                onNavigate = onNavigate,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Guide.route) {
            LiveTvScreen(
                isTv = isTv,
                onPlayChannel = { url, title ->
                    navController.navigate(Screen.Player.createRoute(url, title))
                },
            )
        }
        composable(Screen.Addons.route) {
            AddonsScreen(
                isTv = isTv,
                onNavigate = onNavigate,
            )
        }
        composable(Screen.Live.route) {
            LiveTvScreen(
                isTv = isTv,
                onPlayChannel = { url, title ->
                    navController.navigate(Screen.Player.createRoute(url, title))
                },
            )
        }
        composable(Screen.Watchlist.route) {
            AddonsScreen(isTv = isTv, onNavigate = onNavigate)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                isTv = isTv,
                onNavigate = onNavigate,
            )
        }
        composable(Screen.Player.route) { back ->
            val url = URLDecoder.decode(back.arguments?.getString("url") ?: "", "UTF-8")
            val title = URLDecoder.decode(back.arguments?.getString("title") ?: "", "UTF-8")
            PlayerScreen(
                url = url,
                title = title,
                isTv = isTv,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Detail.route) { back ->
            val type = back.arguments?.getString("type") ?: "movie"
            val id = back.arguments?.getString("id") ?: ""
            DetailScreen(
                type = type,
                id = id,
                isTv = isTv,
                onPlayStream = { url, t ->
                    navController.navigate(Screen.Player.createRoute(url, t))
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
