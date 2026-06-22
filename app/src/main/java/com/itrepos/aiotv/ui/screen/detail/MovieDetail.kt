package com.itrepos.aiotv.ui.screen.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.ui.theme.CachedBadge

/**
 * Movie detail UI — extracted verbatim from the original DetailScreen. Renders header + meta +
 * the ranked stream list; tapping a stream resolves it and plays. Behaviour is unchanged; the
 * play callback is routed through the 3-arg [onPlay] with `progressId = url`.
 */
@Composable
fun MovieDetail(
    state: DetailState,
    fallbackTitle: String,
    onPlayStream: (Stream) -> Unit,
    onBack: () -> Unit,
) {
    // Focus the first stream row once streams are available.
    val firstStreamFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.streams.isNotEmpty()) {
        if (state.streams.isNotEmpty()) {
            runCatching { firstStreamFocusRequester.requestFocus() }
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 840.dp

        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                // Left pane: header + meta/description.
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    DetailHeader(title = state.meta?.name ?: fallbackTitle, onBack = onBack)
                    state.meta?.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    ErrorAndResolving(error = state.error, resolving = state.resolving)
                }
                // Right pane: scrollable stream list.
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    StreamsList(
                        state = state,
                        firstStreamFocusRequester = firstStreamFocusRequester,
                        onResolve = onPlayStream,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") { DetailHeader(title = state.meta?.name ?: fallbackTitle, onBack = onBack) }
                state.meta?.description?.let { desc ->
                    item(key = "description") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                item(key = "status") { ErrorAndResolving(error = state.error, resolving = state.resolving) }
                if (state.streams.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No streams found. Add a Stremio addon in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    item(key = "streams_header") {
                        Text("Streams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    }
                    items(state.streams, key = { streamKey(it) }) { stream ->
                        StreamRow(
                            stream = stream,
                            enabled = !state.resolving,
                            modifier = if (stream === state.streams.firstOrNull()) {
                                Modifier.focusRequester(firstStreamFocusRequester)
                            } else {
                                Modifier
                            },
                            onClick = { onPlayStream(stream) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun ErrorAndResolving(error: String?, resolving: Boolean) {
    if (error != null) {
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    if (resolving) {
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
}

@Composable
private fun StreamsList(
    state: DetailState,
    firstStreamFocusRequester: FocusRequester,
    onResolve: (Stream) -> Unit,
) {
    if (state.streams.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No streams found. Add a Stremio addon in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "streams_header") {
            Text("Streams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        }
        items(state.streams, key = { streamKey(it) }) { stream ->
            StreamRow(
                stream = stream,
                enabled = !state.resolving,
                modifier = if (stream === state.streams.firstOrNull()) {
                    Modifier.focusRequester(firstStreamFocusRequester)
                } else {
                    Modifier
                },
                onClick = { onResolve(stream) },
            )
        }
    }
}

private fun streamKey(stream: Stream): String =
    stream.url ?: stream.infoHash?.let { "hash:$it:${stream.fileIdx ?: 0}" } ?: (stream.title ?: "stream")

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
