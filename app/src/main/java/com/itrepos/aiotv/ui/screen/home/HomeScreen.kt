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
import com.itrepos.aiotv.domain.model.ContentSection
import com.itrepos.aiotv.ui.components.ContentRail
import com.itrepos.aiotv.ui.components.HeroSection
import com.itrepos.aiotv.ui.components.MediaCard
import com.itrepos.aiotv.ui.components.PhoneBottomNav
import com.itrepos.aiotv.ui.components.SideNavRail
import com.itrepos.aiotv.ui.components.TvNavRail
import com.itrepos.aiotv.ui.navigation.Screen

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
    LaunchedEffect(state.isLoading, state.movieSections.size, state.liveGroups.size) {
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
    val hasContent = state.movieSections.isNotEmpty() || state.seriesSections.isNotEmpty() ||
        state.liveGroups.isNotEmpty() || state.continueWatching.isNotEmpty()

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        !hasContent -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.empty_no_source),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
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
                            modifier = if (firstCardFocus != null && progress == state.continueWatching.first())
                                Modifier.focusRequester(firstCardFocus) else Modifier,
                            onClick = { onNavigate(Screen.Player.createRoute(progress.id, "Resume")) },
                        )
                    }
                }
            }

            // Live TV groups rail (capped at 8 to keep the home page tidy)
            if (state.liveGroups.isNotEmpty()) {
                item(key = "rail_live_groups") {
                    ContentRail(
                        title = stringResource(R.string.live_now),
                        items = state.liveGroups.take(8),
                        key = { it },
                    ) { group ->
                        MediaCard(
                            title = group,
                            imageUrl = null,
                            aspectRatio = 16f / 9f,
                            onClick = { onNavigate(Screen.Live.route) },
                        )
                    }
                }
            }

            // Movies header
            if (state.movieSections.isNotEmpty()) {
                item(key = "header_movies") {
                    Text(
                        text = "Movies",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            // Movie sections — one rail per catalog source (Netflix, Prime, etc.)
            state.movieSections.forEachIndexed { idx, section ->
                item(key = "rail_movies_$idx") {
                    CatalogSectionRail(
                        section = section,
                        focusFirst = firstCardFocus != null && state.continueWatching.isEmpty() &&
                            state.liveGroups.isEmpty() && idx == 0,
                        firstCardFocus = firstCardFocus,
                        onNavigate = onNavigate,
                    )
                }
            }

            // Series header
            if (state.seriesSections.isNotEmpty()) {
                item(key = "header_series") {
                    Text(
                        text = "Series",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            // Series sections — one rail per catalog source
            state.seriesSections.forEachIndexed { idx, section ->
                item(key = "rail_series_$idx") {
                    CatalogSectionRail(
                        section = section,
                        focusFirst = false,
                        firstCardFocus = null,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionRail(
    section: ContentSection,
    focusFirst: Boolean,
    firstCardFocus: FocusRequester?,
    onNavigate: (String) -> Unit,
) {
    val items = section.items.take(20)
    ContentRail(
        title = section.title,
        items = items,
        key = { it.id },
    ) { item ->
        MediaCard(
            title = item.name,
            imageUrl = item.posterUrl,
            modifier = if (focusFirst && firstCardFocus != null && item == items.first())
                Modifier.focusRequester(firstCardFocus) else Modifier,
            onClick = { onNavigate(Screen.Detail.createRoute(item.type, item.id)) },
        )
    }
}
