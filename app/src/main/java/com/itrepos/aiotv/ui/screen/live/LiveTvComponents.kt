package com.itrepos.aiotv.ui.screen.live

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.EpgNowNext
import com.itrepos.aiotv.ui.components.FocusableCard
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceElevated
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A Sky-style live-channel row: channel number + logo, then now (HH:mm · title) with a progress
 * bar, then next (HH:mm · title), and a trailing ★ favourite toggle.
 */
@Composable
fun ChannelRow(
    channel: Channel,
    nowNext: EpgNowNext?,
    onClick: () -> Unit,
    isFavourite: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FocusableCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel number (Sky-style), fixed-width so logos line up.
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                if (channel.channelNo > 0) {
                    Text(
                        channel.channelNo.toString(),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                    )
                }
            }
            val fallback = ColorPainter(SurfaceElevated)
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                placeholder = fallback,
                error = fallback,
                fallback = fallback,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceElevated),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                val now = nowNext?.now
                val next = nowNext?.next
                if (now != null) {
                    Text(
                        "${timeFmt.format(Date(now.startMs))} · ${now.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                    // Progress bar through the current programme.
                    val nowMs = System.currentTimeMillis()
                    val span = (now.endMs - now.startMs).toFloat()
                    if (span > 0f) {
                        val frac = ((nowMs - now.startMs) / span).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp, bottom = 1.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.20f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(frac)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AccentPrimary),
                            )
                        }
                    }
                }
                if (next != null) {
                    Text(
                        "${timeFmt.format(Date(next.startMs))} · ${next.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
                if (now == null && next == null) {
                    Text("—", style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
            // ★ favourite toggle
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavourite) AccentPrimary else muted,
                )
            }
        }
    }
}
