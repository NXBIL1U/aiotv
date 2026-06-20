package com.itrepos.aiotv.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itrepos.aiotv.ui.screen.addons.AddonsScreen
import com.itrepos.aiotv.ui.screen.detail.DetailScreen
import com.itrepos.aiotv.ui.screen.guide.TvGuideScreen
import com.itrepos.aiotv.ui.screen.home.HomeScreen
import com.itrepos.aiotv.ui.screen.player.PlayerScreen
import com.itrepos.aiotv.ui.screen.search.SearchScreen
import com.itrepos.aiotv.ui.screen.settings.SettingsScreen
import java.net.URLDecoder

@Composable
fun AppNavigation(isTv: Boolean, windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.Home.route) }

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            selectedScreen = Screen.Home.route
            HomeScreen(
                isTv = isTv,
                windowSizeClass = windowSizeClass,
                selectedScreen = selectedScreen,
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(Screen.Search.route) {
            selectedScreen = Screen.Search.route
            SearchScreen(
                isTv = isTv,
                onNavigate = { navController.navigate(it) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Guide.route) {
            selectedScreen = Screen.Guide.route
            TvGuideScreen(
                isTv = isTv,
                onNavigate = { navController.navigate(it) },
                onPlayChannel = { url, title ->
                    navController.navigate(Screen.Player.createRoute(url, title))
                },
            )
        }
        composable(Screen.Addons.route) {
            selectedScreen = Screen.Addons.route
            AddonsScreen(
                isTv = isTv,
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(Screen.Live.route) {
            selectedScreen = Screen.Live.route
            com.itrepos.aiotv.ui.screen.guide.TvGuideScreen(
                isTv = isTv,
                onNavigate = { navController.navigate(it) },
                onPlayChannel = { url, title ->
                    navController.navigate(Screen.Player.createRoute(url, title))
                },
            )
        }
        composable(Screen.Watchlist.route) {
            selectedScreen = Screen.Watchlist.route
            com.itrepos.aiotv.ui.screen.addons.AddonsScreen(isTv = isTv, onNavigate = { navController.navigate(it) })
        }
        composable(Screen.Settings.route) {
            selectedScreen = Screen.Settings.route
            SettingsScreen(
                isTv = isTv,
                onNavigate = { navController.navigate(it) },
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
