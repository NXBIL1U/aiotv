package com.itrepos.aiotv.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.data.repository.SeriesMeta
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceElevated

/**
 * Netflix-style series detail: hero backdrop + scrim, meta line, resume-aware Play button,
 * overview, season selector, and a season's episode list. Two-pane at >= 840dp; TV uses a
 * horizontal focusable episode rail and requests first focus on Play.
 */
@Composable
fun SeriesDetail(
    state: DetailState,
    isTv: Boolean,
    onPlayEpisode: (Episode) -> Unit,
    onShowSources: (Episode) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onBack: () -> Unit,
    progressProvider: ProgressProvider = rememberProgressProvider(),
) {
    val series = state.series
    if (series == null) {
        // Empty-meta state: title + "episodes unavailable".
        UnavailableState(error = state.error, onBack = onBack)
        return
    }

    val season = state.selectedSeason ?: series.seasons.firstOrNull() ?: 0
    val episodes = series.episodesIn(season)

    // Resume-aware Play: first episode in the show with partial progress, else first episode.
    val resumeEpisode = remember(series, progressProvider.version) {
        series.episodes.firstOrNull { progressProvider.fractionFor(it.id) in 0.001f..0.999f }
            ?: episodes.firstOrNull()
            ?: series.episodes.firstOrNull()
    }
    val resumeFraction = resumeEpisode?.let { progressProvider.fractionFor(it.id) } ?: 0f
    val playLabel = when {
        resumeEpisode == null -> "Play"
        resumeFraction > 0.001f -> "Resume S${resumeEpisode.season}·E${resumeEpisode.number}"
        else -> "Play S${resumeEpisode.season}·E${resumeEpisode.number}"
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) runCatching { playFocus.requestFocus() }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 840.dp

        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Hero(
                        series = series,
                        playLabel = playLabel,
                        resumeEpisode = resumeEpisode,
                        playFocus = playFocus,
                        onPlay = { resumeEpisode?.let(onPlayEpisode) },
                        onSources = { resumeEpisode?.let(onShowSources) },
                        onBack = onBack,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    SeasonSelector(series.seasons, season, onSelectSeason)
                    EpisodeVerticalList(episodes, progressProvider, onPlayEpisode, onShowSources)
                }
            }
        } else if (isTv) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Hero(
                    series = series,
                    playLabel = playLabel,
                    resumeEpisode = resumeEpisode,
                    playFocus = playFocus,
                    onPlay = { resumeEpisode?.let(onPlayEpisode) },
                    onSources = { resumeEpisode?.let(onShowSources) },
                    onBack = onBack,
                )
                SeasonSelector(series.seasons, season, onSelectSeason)
                EpisodeHorizontalRail(episodes, progressProvider, onPlayEpisode)
                Spacer(Modifier.height(24.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "hero") {
                    Hero(
                        series = series,
                        playLabel = playLabel,
                        resumeEpisode = resumeEpisode,
                        playFocus = playFocus,
                        onPlay = { resumeEpisode?.let(onPlayEpisode) },
                        onSources = { resumeEpisode?.let(onShowSources) },
                        onBack = onBack,
                    )
                }
                item(key = "seasons") { SeasonSelector(series.seasons, season, onSelectSeason) }
                items(episodes, key = { it.id }) { ep ->
                    EpisodeRow(
                        episode = ep,
                        progressFraction = progressProvider.fractionFor(ep.id),
                        onClick = { onPlayEpisode(ep) },
                    )
                }
                item(key = "spacer") { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (state.resolvingEpisode != null) {
            FindingSourceOverlay()
        }
    }
}

@Composable
private fun Hero(
    series: SeriesMeta,
    playLabel: String,
    resumeEpisode: Episode?,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onSources: () -> Unit,
    onBack: () -> Unit,
) {
    val item = series.item
    val fallback = ColorPainter(SurfaceElevated)
    Box(Modifier.fillMaxWidth().height(280.dp)) {
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            placeholder = fallback,
            error = fallback,
            fallback = fallback,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.4f),
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.95f),
                )
            )
        )
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = metaLine(series),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onPlay,
                enabled = resumeEpisode != null,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                modifier = Modifier.focusRequester(playFocus),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(playLabel)
            }
            OutlinedButton(onClick = onSources, enabled = resumeEpisode != null) {
                Text("Sources")
            }
        }
        item.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SeasonSelector(seasons: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    if (seasons.size <= 1) {
        Text(
            text = seasonLabel(selected),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(seasonLabel(selected)) },
            colors = AssistChipDefaults.assistChipColors(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            seasons.forEach { s ->
                DropdownMenuItem(
                    text = { Text(seasonLabel(s)) },
                    onClick = {
                        onSelect(s)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeVerticalList(
    episodes: List<Episode>,
    progressProvider: ProgressProvider,
    onPlayEpisode: (Episode) -> Unit,
    onShowSources: (Episode) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(episodes, key = { it.id }) { ep ->
            EpisodeRow(
                episode = ep,
                progressFraction = progressProvider.fractionFor(ep.id),
                onClick = { onPlayEpisode(ep) },
            )
        }
    }
}

@Composable
private fun EpisodeHorizontalRail(
    episodes: List<Episode>,
    progressProvider: ProgressProvider,
    onPlayEpisode: (Episode) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        items(episodes, key = { it.id }) { ep ->
            EpisodeRailCard(
                episode = ep,
                progressFraction = progressProvider.fractionFor(ep.id),
                onClick = { onPlayEpisode(ep) },
            )
        }
    }
}

@Composable
private fun EpisodeRailCard(episode: Episode, progressFraction: Float, onClick: () -> Unit) {
    val fallback = ColorPainter(SurfaceElevated)
    Column(
        Modifier
            .width(200.dp)
            .focusable()
            .clickable { onClick() },
    ) {
        Box(Modifier.width(200.dp).height(112.dp).clip(RoundedCornerShape(6.dp))) {
            AsyncImage(
                model = episode.thumbnail,
                contentDescription = episode.name,
                contentScale = ContentScale.Crop,
                placeholder = fallback,
                error = fallback,
                fallback = fallback,
                modifier = Modifier.fillMaxSize(),
            )
            if (progressFraction > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progressFraction)
                        .height(3.dp)
                        .background(AccentPrimary),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${episode.number}. ${episode.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FindingSourceOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                enabled = true,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), color = AccentPrimary)
            Spacer(Modifier.width(12.dp))
            Text("Finding a working source…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UnavailableState(error: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailHeader(title = "Series", onBack = onBack)
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                error ?: "Episodes unavailable — try again later",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun metaLine(series: SeriesMeta): String {
    val item = series.item
    return buildList {
        item.year?.let { add(it.toString()) }
        item.genres.firstOrNull()?.let { add(it) }
        item.imdbRating?.let { add("★$it") }
    }.joinToString(" · ")
}

private fun seasonLabel(season: Int): String = if (season == 0) "Specials" else "Season $season"

/**
 * Provides watch-progress fractions to the series UI. Backed by [WatchProgressStore]; reads all
 * progress once and exposes a synchronous lookup so episode rows don't each spawn a Flow.
 */
class ProgressProvider(
    private val fractions: Map<String, Float>,
) {
    val version: Int = fractions.hashCode()
    fun fractionFor(id: String): Float = fractions[id] ?: 0f
}

@Composable
fun rememberProgressProvider(): ProgressProvider {
    val vm = hiltViewModel<ProgressViewModel>()
    val all by vm.progress.collectAsState()
    return remember(all) {
        ProgressProvider(all.associate { it.id to it.fraction })
    }
}
