package com.itrepos.aiotv.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.data.remote.stremio.StremioVideo
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.ui.theme.CachedBadge

@Composable
fun DetailScreen(
    type: String,
    id: String,
    isTv: Boolean,
    onPlayStream: (url: String, title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(type, id) { viewModel.load(type, id) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val firstStreamFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.streams.isNotEmpty()) {
        if (state.streams.isNotEmpty()) runCatching { firstStreamFocusRequester.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 840.dp
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                ) {
                    DetailHeader(title = state.meta?.name ?: id, onBack = onBack)
                    MetaInfo(state)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    SeriesOrMovieContent(state, firstStreamFocusRequester, viewModel, onPlayStream)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") { DetailHeader(title = state.meta?.name ?: id, onBack = onBack) }
                item(key = "meta") { MetaInfo(state) }
                item(key = "content") {
                    SeriesOrMovieContent(state, firstStreamFocusRequester, viewModel, onPlayStream)
                }
            }
        }
    }
}

@Composable
private fun MetaInfo(state: DetailState) {
    val meta = state.meta ?: return
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (!meta.description.isNullOrBlank()) {
            Text(
                meta.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            meta.year?.let { Text("$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            meta.imdbRating?.let { Text("⭐ $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SeriesOrMovieContent(
    state: DetailState,
    firstStreamFocusRequester: FocusRequester,
    viewModel: DetailViewModel,
    onPlayStream: (String, String) -> Unit,
) {
    if (state.type == "series" && state.seasons.isNotEmpty()) {
        SeriesContent(state, firstStreamFocusRequester, viewModel, onPlayStream)
    } else {
        StreamsPanel(state, firstStreamFocusRequester, viewModel, onPlayStream)
    }
}

@Composable
private fun SeriesContent(
    state: DetailState,
    firstStreamFocusRequester: FocusRequester,
    viewModel: DetailViewModel,
    onPlayStream: (String, String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // Season picker
        Text(
            "Season",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.seasons, key = { it }) { season ->
                val selected = state.selectedSeason == season
                if (selected) {
                    Button(onClick = {}) { Text("S$season") }
                } else {
                    OutlinedButton(onClick = { viewModel.selectSeason(season) }) { Text("S$season") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Episode list
        if (state.episodesInSeason.isNotEmpty()) {
            Text(
                "Episodes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            state.episodesInSeason.forEach { ep ->
                EpisodeRow(
                    episode = ep,
                    isSelected = state.selectedEpisode?.id == ep.id,
                    onClick = { viewModel.selectEpisode(ep) },
                )
            }
        }

        // Streams for selected episode
        if (state.selectedEpisode != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Streams — ${state.selectedEpisode.title ?: "Episode ${state.selectedEpisode.episode}"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            StreamsPanel(state, firstStreamFocusRequester, viewModel, onPlayStream)
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: StremioVideo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "E${episode.episode ?: "?"}",
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = episode.title ?: "Episode ${episode.episode}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        episode.released?.let { released ->
            Text(
                text = released.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StreamsPanel(
    state: DetailState,
    firstStreamFocusRequester: FocusRequester,
    viewModel: DetailViewModel,
    onPlayStream: (String, String) -> Unit,
) {
    when {
        state.loadingStreams -> Box(
            Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.error != null -> Text(
            state.error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        state.streams.isEmpty() && !state.loadingStreams && state.type != "series" -> Box(
            Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No streams found. Add a stream addon in Settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.streams.isNotEmpty() -> Column {
            if (state.resolving) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Text(
                        "Preparing stream…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            state.streams.forEachIndexed { idx, stream ->
                StreamRow(
                    stream = stream,
                    enabled = !state.resolving,
                    modifier = if (idx == 0) Modifier.focusRequester(firstStreamFocusRequester) else Modifier,
                    onClick = {
                        viewModel.resolveStream(stream) { url ->
                            onPlayStream(url, stream.title ?: state.meta?.name ?: "")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StreamRow(
    stream: Stream,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(12.dp)) {
            Text(
                text = stream.title ?: stream.url?.take(60) ?: "Stream",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (stream.isCached) {
                Text("CACHED", style = MaterialTheme.typography.labelSmall, color = CachedBadge)
            }
        }
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun streamKey(stream: Stream): String =
    stream.url ?: stream.infoHash?.let { "hash:$it:${stream.fileIdx ?: 0}" } ?: (stream.title ?: "stream")
