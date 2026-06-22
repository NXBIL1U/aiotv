package com.itrepos.aiotv.ui.screen.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceElevated

/**
 * A single episode row: thumbnail, "N. Name", a released/overview snippet, and a resume bar when
 * the episode is partially watched. Used in the phone & two-pane vertical episode lists.
 */
@Composable
fun EpisodeRow(
    episode: Episode,
    progressFraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallback = ColorPainter(SurfaceElevated)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 120.dp, height = 68.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            AsyncImage(
                model = episode.thumbnail,
                contentDescription = episode.name,
                contentScale = ContentScale.Crop,
                placeholder = fallback,
                error = fallback,
                fallback = fallback,
                modifier = Modifier.size(width = 120.dp, height = 68.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${episode.number}. ${episode.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val snippet = episode.overview?.takeIf { it.isNotBlank() } ?: episode.released
            if (!snippet.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progressFraction > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = AccentPrimary,
                    trackColor = SurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }
    }
}
