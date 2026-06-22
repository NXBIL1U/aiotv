package com.itrepos.aiotv.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.itrepos.aiotv.ui.components.MiniPlayerBar
import com.itrepos.aiotv.ui.screen.addons.AddonsScreen
import com.itrepos.aiotv.ui.screen.catalog.CatalogScreen
import com.itrepos.aiotv.ui.screen.detail.DetailScreen
import com.itrepos.aiotv.ui.screen.home.HomeScreen
import com.itrepos.aiotv.ui.screen.live.LiveScreen
import com.itrepos.aiotv.ui.screen.mirror.MirrorScreen
import com.itrepos.aiotv.ui.screen.player.MiniPlayerViewModel
import com.itrepos.aiotv.ui.screen.player.PlayerScreen
import com.itrepos.aiotv.ui.screen.search.SearchScreen
import com.itrepos.aiotv.ui.screen.settings.SettingsScreen
import java.net.URLDecoder

private val topLevelRoutes = setOf(
    Screen.Home.route,
    Screen.Search.route,
    Screen.Movies.route,
    Screen.Series.route,
    Screen.Live.route,
    Screen.Mirror.route,
    Screen.Addons.route,
    Screen.Settings.route,
)

@Composable
fun AppNavigation(isTv: Boolean, windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedScreen = backStackEntry?.destination?.route ?: Screen.Home.route

    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val playbackManager = miniPlayerViewModel.playbackManager
    val playbackState by playbackManager.state.collectAsState()

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

    val isPlayerActive = selectedScreen == Screen.Player.route
    val showMiniPlayer = playbackState.url.isNotEmpty() && !isPlayerActive

    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.weight(1f),
        ) {
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
            composable("catalog/{type}") { back ->
                val type = back.arguments?.getString("type") ?: "movie"
                val title = if (type == "movie") "Movies" else "Series"
                CatalogScreen(title = title, type = type, onNavigate = onNavigate)
            }
            composable(Screen.Live.route) {
                LiveScreen(
                    onPlayChannel = { url, title ->
                        navController.navigate(Screen.Player.createRoute(url, title))
                    },
                )
            }
            composable(Screen.Mirror.route) {
                MirrorScreen()
            }
            composable(Screen.Addons.route) {
                AddonsScreen(
                    isTv = isTv,
                    onNavigate = onNavigate,
                )
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

        if (showMiniPlayer) {
            MiniPlayerBar(
                state = playbackState,
                onExpand = {
                    navController.navigate(
                        Screen.Player.createRoute(playbackState.url, playbackState.title)
                    )
                },
                onPlayPause = {
                    if (playbackManager.player.isPlaying) playbackManager.player.pause()
                    else playbackManager.player.play()
                },
                onStop = { playbackManager.stop() },
            )
        }
    }
}
