package com.itrepos.aiotv.ui.screen.addons

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AddonsScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    viewModel: AddonsViewModel = hiltViewModel(),
) {
    val urls by viewModel.addonUrls.collectAsState()
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(urls.isNotEmpty()) {
        if (urls.isNotEmpty()) {
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Addons", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        if (urls.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No addons configured. Add a Stremio addon manifest URL in Settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            val urlList = urls.toList()
            LazyColumn {
                itemsIndexed(
                    items = urlList,
                    key = { _, url -> url },
                ) { index, url ->
                    val itemModifier = if (index == 0) {
                        Modifier
                            .focusRequester(firstItemFocus)
                            .focusable()
                            .padding(vertical = 6.dp)
                    } else {
                        Modifier
                            .focusable()
                            .padding(vertical = 6.dp)
                    }
                    Text(
                        url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = itemModifier,
                    )
                }
            }
        }
    }
}
