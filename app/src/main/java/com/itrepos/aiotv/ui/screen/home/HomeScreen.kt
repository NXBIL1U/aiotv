package com.itrepos.aiotv.ui.screen.home

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.R
import com.itrepos.aiotv.ui.components.ContentRail
import com.itrepos.aiotv.ui.components.HeroSection
import com.itrepos.aiotv.ui.components.MediaCard
import com.itrepos.aiotv.ui.components.PhoneBottomNav
import com.itrepos.aiotv.ui.components.SideNavRail
import com.itrepos.aiotv.ui.components.TvNavRail
import com.itrepos.aiotv.ui.navigation.Screen

// TV overscan-safe margins so edge content isn't cropped by the display bezel.
private val TvOverscanH = 48.dp
private val TvOverscanV = 27.dp

@Composable
fun HomeScreen(
    isTv: Boolean,
    windowSizeClass: WindowSizeClass,
    selectedScreen: String,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    when {
        isTv -> TvHomeLayout(state, selectedScreen, onNavigate)
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact ->
            PhoneHomeLayout(state, selectedScreen, onNavigate)
        else -> MediumHomeLayout(state, selectedScreen, onNavigate)
    }
}

@Composable
private fun TvHomeLayout(
    state: HomeUiState,
    selectedScreen: String,
    onNavigate: (String) -> Unit,
) {
    val firstCardFocus = remember { FocusRequester() }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvOverscanH, vertical = TvOverscanV)
    ) {
        TvNavRail(selectedRoute = selectedScreen, onNavigate = onNavigate)
        HomeContent(
            state = state,
            onNavigate = onNavigate,
            modifier = Modifier.weight(1f),
            firstCardFocus = firstCardFocus,
        )
    }
    // Land D-pad focus on the first content card once data is available.
    LaunchedEffect(state.isLoading, state.movies.size, state.liveChannels.size) {
        if (!state.isLoading) runCatching { firstCardFocus.requestFocus() }
    }
}

@Composable
private fun MediumHomeLayout(
    state: HomeUiState,
    selectedScreen: String,
    onNavigate: (String) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        SideNavRail(selectedRoute = selectedScreen, onNavigate = onNavigate)
        HomeContent(state = state, onNavigate = onNavigate, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PhoneHomeLayout(
    state: HomeUiState,
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
    firstCardFocus: FocusRequester? = null,
) {
    // Only the first visible rail's first card gets the focus requester.
    val firstRail = when {
        state.continueWatching.isNotEmpty() -> "cw"
        state.liveChannels.isNotEmpty() -> "live"
        state.movies.isNotEmpty() -> "movies"
        state.series.isNotEmpty() -> "series"
        else -> null
    }
    fun focusMod(isFirst: Boolean): Modifier =
        if (isFirst && firstCardFocus != null) Modifier.focusRequester(firstCardFocus) else Modifier

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.movies.isEmpty() && state.liveChannels.isEmpty() && state.continueWatching.isEmpty() -> {
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
            item(key = "hero") {
                HeroSection(item = state.featuredItem, modifier = Modifier.fillMaxWidth())
            }
            if (state.continueWatching.isNotEmpty()) {
                item(key = "rail_cw") {
                    ContentRail(
                        title = stringResource(R.string.continue_watching),
                        items = state.continueWatching,
                        key = { it.id },
                    ) { progress ->
                        MediaCard(
                            title = stringResource(R.string.continue_watching),
                            imageUrl = null,
                            aspectRatio = 16f / 9f,
                            progress = progress.fraction,
                            modifier = focusMod(firstRail == "cw" && progress == state.continueWatching.first()),
                            onClick = {
                                val pid = progress.id
                                if (Regex("^tt\\d+:\\d+:\\d+$").matches(pid)) {
                                    onNavigate(Screen.Detail.createRoute("series", pid.substringBefore(":")))
                                } else {
                                    onNavigate(Screen.Player.createRoute(pid, "Resume"))
                                }
                            },
                        )
                    }
                }
            }
            if (state.liveChannels.isNotEmpty()) {
                item(key = "rail_live") {
                    ContentRail(
                        title = stringResource(R.string.live_now),
                        items = state.liveChannels,
                        key = { it.id },
                    ) { channel ->
                        MediaCard(
                            title = channel.name,
                            imageUrl = channel.logoUrl,
                            aspectRatio = 16f / 9f,
                            modifier = focusMod(firstRail == "live" && channel == state.liveChannels.first()),
                            onClick = {
                                onNavigate(Screen.Player.createRoute(channel.streamUrl, channel.name))
                            },
                        )
                    }
                }
            }
            if (state.movies.isNotEmpty()) {
                val movies = state.movies.take(20)
                item(key = "rail_movies") {
                    ContentRail(
                        title = stringResource(R.string.top_picks),
                        items = movies,
                        key = { it.id },
                    ) { item ->
                        MediaCard(
                            title = item.name,
                            imageUrl = item.posterUrl,
                            modifier = focusMod(firstRail == "movies" && item == movies.first()),
                            onClick = {
                                onNavigate(Screen.Detail.createRoute(item.type, item.id))
                            },
                        )
                    }
                }
            }
            if (state.series.isNotEmpty()) {
                val series = state.series.take(20)
                item(key = "rail_series") {
                    ContentRail(
                        title = stringResource(R.string.series),
                        items = series,
                        key = { it.id },
                    ) { item ->
                        MediaCard(
                            title = item.name,
                            imageUrl = item.posterUrl,
                            modifier = focusMod(firstRail == "series" && item == series.first()),
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
