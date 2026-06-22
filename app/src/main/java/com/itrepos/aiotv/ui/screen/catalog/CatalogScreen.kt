package com.itrepos.aiotv.ui.screen.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.ui.components.ContentRail
import com.itrepos.aiotv.ui.components.MediaCard
import com.itrepos.aiotv.ui.navigation.Screen

@Composable
fun CatalogScreen(
    title: String,
    type: String,
    onNavigate: (String) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    LaunchedEffect(type) { viewModel.load(type) }
    val state by viewModel.state.collectAsState()

    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        state.error != null || state.sections.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (state.sections.isEmpty() && state.error == null)
                        "No content — add a Stremio catalog addon in Settings"
                    else
                        "Failed to load: ${state.error}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                Button(onClick = { viewModel.load(type) }) { Text("Retry") }
            }
        }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            state.sections.forEachIndexed { idx, section ->
                item(key = "section_$idx") {
                    ContentRail(
                        title = section.title,
                        items = section.items,
                        key = { it.id },
                    ) { item ->
                        MediaCard(
                            title = item.name,
                            imageUrl = item.posterUrl,
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
