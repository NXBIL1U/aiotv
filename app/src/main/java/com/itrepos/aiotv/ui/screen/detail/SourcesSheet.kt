package com.itrepos.aiotv.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.ui.theme.CachedBadge

/** Phone variant: a Material3 ModalBottomSheet listing the ranked sources. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(
    streams: List<Stream>,
    onPick: (Stream) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "Sources",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        if (streams.isEmpty()) {
            Text(
                "No sources found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(streams, key = { sourceKey(it) }) { stream ->
                    SourceRow(stream = stream, onClick = { onPick(stream) })
                }
            }
        }
    }
}

/** TV / inline variant: a side list of sources (no bottom sheet). */
@Composable
fun SourcesList(
    streams: List<Stream>,
    onPick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            "Sources",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp),
        )
        if (streams.isEmpty()) {
            Text(
                "No sources found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(streams, key = { sourceKey(it) }) { stream ->
                    SourceRow(stream = stream, onClick = { onPick(stream) })
                }
            }
        }
    }
}

@Composable
private fun SourceRow(stream: Stream, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Quality badge
        Text(
            text = qualityLabel(stream.quality),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Cached [TB+] badge in green
        if (stream.isCached) {
            Box(
                Modifier
                    .background(CachedBadge.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("[TB+]", style = MaterialTheme.typography.labelSmall, color = CachedBadge)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stream.title ?: stream.name ?: "Source",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                formatSize(stream.sizeBytes)?.let { add(it) }
                stream.seeders?.let { add("$it seeders") }
            }.joinToString(" · ")
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun sourceKey(stream: Stream): String =
    stream.url ?: stream.infoHash?.let { "hash:$it:${stream.fileIdx ?: 0}" } ?: (stream.title ?: "stream")

private fun qualityLabel(q: Quality): String = when (q) {
    Quality.UHD_2160 -> "4K"
    Quality.HD_1080 -> "1080p"
    Quality.HD_720 -> "720p"
    Quality.SD -> "SD"
    Quality.UNKNOWN -> "—"
}

internal fun formatSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1) return String.format("%.1f GB", gb)
    val mb = bytes / 1_000_000.0
    return String.format("%.0f MB", mb)
}
