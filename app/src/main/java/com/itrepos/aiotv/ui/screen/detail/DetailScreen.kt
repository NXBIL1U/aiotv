package com.itrepos.aiotv.ui.screen.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DetailScreen(
    type: String,
    id: String,
    isTv: Boolean,
    onPlayStream: (url: String, title: String, progressId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(type, id) { viewModel.load(type, id) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    when (state.kind) {
        DetailKind.MOVIE -> MovieDetail(
            state = state,
            fallbackTitle = id,
            onPlayStream = { stream ->
                viewModel.resolveStream(stream) { url ->
                    onPlayStream(url, stream.title ?: state.meta?.name ?: id, url)
                }
            },
            onBack = onBack,
        )

        DetailKind.SERIES -> {
            SeriesDetail(
                state = state,
                isTv = isTv,
                onPlayEpisode = { ep -> viewModel.playEpisode(ep, onPlayStream) },
                onShowSources = { ep -> viewModel.showSources(ep) },
                onSelectSeason = { viewModel.selectSeason(it) },
                onBack = onBack,
            )

            val sourcesEpisode = state.sourcesForEpisode
            if (sourcesEpisode != null) {
                SourcesSheet(
                    streams = state.episodeStreams,
                    onPick = { stream ->
                        viewModel.playSpecificStream(stream, sourcesEpisode, onPlayStream)
                    },
                    onDismiss = { viewModel.dismissSources() },
                )
            }
        }
    }
}
