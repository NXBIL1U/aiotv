package com.itrepos.aiotv.ui.screen.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// 10-foot overscan-safe insets, matching the other TV screens.
private val TV_OVERSCAN_H = 48.dp
private val TV_OVERSCAN_V = 27.dp

@Composable
fun LiveTvScreen(
    isTv: Boolean,
    onPlayChannel: (url: String, title: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = if (isTv) TV_OVERSCAN_H else 0.dp,
                vertical = if (isTv) TV_OVERSCAN_V else 0.dp,
            ),
    ) {
        // Empty: distinguish "no source configured" from "source configured but unreachable".
        if (state.categories.size <= 1 && state.channels.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (state.hasSource) {
                    Text(
                        "Couldn't reach your IPTV provider. It may be offline or its address may have changed — check Settings, then try again.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { viewModel.retry() }) { Text("Retry") }
                } else {
                    Text(
                        "No IPTV source yet. Add one in Settings to watch Live TV.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            return@BoxWithConstraints
        }

        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                CategoryPane(
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = viewModel::selectCategory,
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                )
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp)) {
                    SearchField(state.query, viewModel::setQuery)
                    ChannelList(state, onPlayChannel, viewModel)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SearchField(state.query, viewModel::setQuery, Modifier.padding(horizontal = 12.dp))
                CategoryChips(
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = viewModel::selectCategory,
                )
                ChannelList(state, onPlayChannel, viewModel)
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search channels") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun ChannelList(
    state: LiveTvState,
    onPlayChannel: (url: String, title: String) -> Unit,
    viewModel: LiveTvViewModel,
) {
    if (state.channels.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                if (state.query.isNotBlank()) "No channels match \"${state.query}\"."
                else "No channels in this category.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.channels, key = { it.id }) { channel ->
            // First composition ≈ becomes visible → lazily load now/next.
            LaunchedEffect(channel.id) { viewModel.onChannelVisible(channel) }
            ChannelRow(
                channel = channel,
                nowNext = state.epg[channel.id],
                onClick = { onPlayChannel(channel.streamUrl, channel.name) },
            )
        }
    }
}
