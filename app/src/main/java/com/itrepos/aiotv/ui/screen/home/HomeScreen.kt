package com.itrepos.aiotv.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.R
import com.itrepos.aiotv.ui.components.ContentRail
import com.itrepos.aiotv.ui.components.HeroSection
import com.itrepos.aiotv.ui.components.MediaCard
import com.itrepos.aiotv.ui.components.PhoneBottomNav
import com.itrepos.aiotv.ui.components.TvNavRail
import com.itrepos.aiotv.ui.navigation.Screen

@Composable
fun HomeScreen(
    isTv: Boolean,
    windowSizeClass: WindowSizeClass,
    selectedScreen: String,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    if (isTv) {
        TvHomeLayout(state, selectedScreen, onNavigate)
    } else {
        PhoneHomeLayout(state, windowSizeClass, selectedScreen, onNavigate)
    }
}

@Composable
private fun TvHomeLayout(
    state: HomeUiState,
    selectedScreen: String,
    onNavigate: (String) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        TvNavRail(selectedRoute = selectedScreen, onNavigate = onNavigate)
        HomeContent(state = state, onNavigate = onNavigate, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PhoneHomeLayout(
    state: HomeUiState,
    windowSizeClass: WindowSizeClass,
    selectedScreen: String,
    onNavigate: (String) -> Unit,
) {
    Scaffold(
        bottomBar = { PhoneBottomNav(selectedRoute = selectedScreen, onNavigate = onNavigate) }
    ) { padding ->
        HomeContent(state = state, onNavigate = onNavigate, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.movies.isEmpty() && state.liveChannels.isEmpty() -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.empty_no_source),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        else -> LazyColumn(modifier.fillMaxSize()) {
            item {
                HeroSection(item = state.featuredItem, modifier = Modifier.fillMaxWidth())
            }
            if (state.continueWatching.isNotEmpty()) {
                item {
                    ContentRail(
                        title = stringResource(R.string.continue_watching),
                        items = state.continueWatching,
                    ) { progress ->
                        MediaCard(
                            title = progress.id,
                            imageUrl = null,
                            onClick = {},
                            progress = progress.fraction,
                        )
                    }
                }
            }
            if (state.liveChannels.isNotEmpty()) {
                item {
                    ContentRail(
                        title = stringResource(R.string.live_now),
                        items = state.liveChannels,
                    ) { channel ->
                        MediaCard(
                            title = channel.name,
                            imageUrl = channel.logoUrl,
                            aspectRatio = 16f / 9f,
                            onClick = {
                                onNavigate(Screen.Player.createRoute(channel.streamUrl, channel.name))
                            },
                        )
                    }
                }
            }
            if (state.movies.isNotEmpty()) {
                item {
                    ContentRail(
                        title = stringResource(R.string.top_picks),
                        items = state.movies.take(20),
                    ) { item ->
                        MediaCard(
                            title = item.name,
                            imageUrl = item.posterUrl,
                            onClick = {
                                onNavigate(Screen.Detail.createRoute(item.type, item.id))
                            },
                        )
                    }
                }
            }
            if (state.series.isNotEmpty()) {
                item {
                    ContentRail(
                        title = "Series",
                        items = state.series.take(20),
                    ) { item ->
                        MediaCard(
                            title = item.name,
                            imageUrl = item.posterUrl,
                            onClick = {
                                onNavigate(Screen.Detail.createRoute(item.type, item.id))
                            },
                        )
                    }
                }
            }
        }
    }
}
