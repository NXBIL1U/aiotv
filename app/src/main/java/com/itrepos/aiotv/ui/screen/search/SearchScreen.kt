package com.itrepos.aiotv.ui.screen.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.ui.components.MediaCard
import com.itrepos.aiotv.ui.navigation.Screen

@Composable
fun SearchScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search movies, series, channels…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        LazyColumn(contentPadding = PaddingValues(top = 12.dp)) {
            if (state.channelResults.isNotEmpty()) {
                item { Text("Channels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                items(state.channelResults) { ch ->
                    MediaCard(
                        title = ch.name,
                        imageUrl = ch.logoUrl,
                        aspectRatio = 16f / 9f,
                        onClick = { onNavigate(Screen.Player.createRoute(ch.streamUrl, ch.name)) },
                    )
                }
            }
            if (state.mediaResults.isNotEmpty()) {
                item { Text("Movies & Series", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                items(state.mediaResults) { item ->
                    MediaCard(
                        title = item.name,
                        imageUrl = item.posterUrl,
                        onClick = { onNavigate(Screen.Detail.createRoute(item.type, item.id)) },
                    )
                }
            }
            if (state.query.length >= 2 && state.mediaResults.isEmpty() && state.channelResults.isEmpty() && !state.isSearching) {
                item { Text("No results for \"${state.query}\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
