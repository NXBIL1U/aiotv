package com.itrepos.aiotv.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search movies, series, channels…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { viewModel.onQueryChange(state.query) }
            ),
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (maxWidth >= 600.dp) 3 else 1

            when {
                state.isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.query.length >= 2 &&
                    state.mediaResults.isEmpty() &&
                    state.channelResults.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No results for \"${state.query}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }

                columns <= 1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp),
                    ) {
                        if (state.channelResults.isNotEmpty()) {
                            item(key = "header_channels") {
                                Text(
                                    "Channels",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            items(
                                items = state.channelResults,
                                key = { ch -> "ch_${ch.id}" },
                            ) { ch ->
                                MediaCard(
                                    title = ch.name,
                                    imageUrl = ch.logoUrl,
                                    aspectRatio = 16f / 9f,
                                    onClick = { onNavigate(Screen.Player.createRoute(ch.streamUrl, ch.name)) },
                                )
                            }
                        }
                        if (state.mediaResults.isNotEmpty()) {
                            item(key = "header_media") {
                                Text(
                                    "Movies & Series",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            items(
                                items = state.mediaResults,
                                key = { item -> "media_${item.id}" },
                            ) { item ->
                                MediaCard(
                                    title = item.name,
                                    imageUrl = item.posterUrl,
                                    onClick = { onNavigate(Screen.Detail.createRoute(item.type, item.id)) },
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.channelResults.isNotEmpty()) {
                            item(key = "header_channels", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Channels",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            items(
                                items = state.channelResults,
                                key = { ch -> "ch_${ch.id}" },
                            ) { ch ->
                                MediaCard(
                                    title = ch.name,
                                    imageUrl = ch.logoUrl,
                                    aspectRatio = 16f / 9f,
                                    onClick = { onNavigate(Screen.Player.createRoute(ch.streamUrl, ch.name)) },
                                )
                            }
                        }
                        if (state.mediaResults.isNotEmpty()) {
                            item(key = "header_media", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Movies & Series",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            items(
                                items = state.mediaResults,
                                key = { item -> "media_${item.id}" },
                            ) { item ->
                                MediaCard(
                                    title = item.name,
                                    imageUrl = item.posterUrl,
                                    onClick = { onNavigate(Screen.Detail.createRoute(item.type, item.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
