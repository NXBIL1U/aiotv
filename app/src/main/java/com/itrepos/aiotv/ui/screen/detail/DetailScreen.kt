package com.itrepos.aiotv.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(state.meta?.name ?: id, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        state.meta?.let { meta ->
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    meta.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        if (state.streams.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No streams found. Add a Stremio addon in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item { Text("Streams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
            items(state.streams) { stream ->
                StreamRow(stream = stream, onClick = {
                    viewModel.resolveStream(stream) { url ->
                        onPlayStream(url, stream.title ?: state.meta?.name ?: "")
                    }
                })
            }
        }
    }
}

@Composable
private fun StreamRow(stream: Stream, onClick: () -> Unit) {
    Row(
        Modifier
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
        IconButton(onClick = onClick) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
