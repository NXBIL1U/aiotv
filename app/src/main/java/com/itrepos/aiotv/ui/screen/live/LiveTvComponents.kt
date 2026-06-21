package com.itrepos.aiotv.ui.screen.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.domain.model.EpgNowNext
import com.itrepos.aiotv.ui.components.FocusableCard
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceCard
import com.itrepos.aiotv.ui.theme.SurfaceElevated
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A live-channel row: logo + name + now/next programme + ★ favourite toggle. */
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
    FocusableCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                when {
                    now != null -> Text(
                        "● ${now.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentPrimary,
                    )
                    next == null -> Text(
                        "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (next != null) {
                    Text(
                        "Next ${timeFmt.format(Date(next.startMs))} · ${next.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // ★ favourite toggle
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavourite) AccentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Vertical, focusable category list for wide layouts (TV / tablet / unfolded). */
@Composable
fun CategoryPane(
    categories: List<ChannelCategory>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight().background(SurfaceCard),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(categories, key = { it.id }) { cat ->
            CategoryItem(
                name = cat.name,
                selected = cat.id == selectedId,
                onClick = { onSelect(cat.id) },
            )
        }
    }
}

@Composable
private fun CategoryItem(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        focused -> AccentPrimary.copy(alpha = 0.30f)
        selected -> AccentPrimary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, AccentPrimary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected || focused) AccentPrimary else Color.White.copy(alpha = 0.85f),
        )
    }
}

/** Horizontal category chips for compact layouts (phone / folded). */
@Composable
fun CategoryChips(
    categories: List<ChannelCategory>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { cat ->
            val selected = cat.id == selectedId
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) AccentPrimary else SurfaceElevated)
                    .clickable { onSelect(cat.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    cat.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
