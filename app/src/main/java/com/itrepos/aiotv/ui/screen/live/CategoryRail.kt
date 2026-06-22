package com.itrepos.aiotv.ui.screen.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceCard

/**
 * Persistent, D-pad-focusable category list for wide / TV layouts — the always-on category
 * navigation that replaces the modal picker on big screens (the picker stays for phone).
 * [categories] should already include the synthetic "All" entry first.
 */
@Composable
fun CategoryRail(
    categories: List<ChannelCategory>,
    selectedId: String,
    favouriteIds: Set<String>,
    regionCaption: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(SurfaceCard)) {
        if (regionCaption.isNotEmpty()) {
            Text(
                regionCaption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                CategoryRailItem(
                    name = cat.name,
                    favourite = cat.id in favouriteIds,
                    selected = cat.id == selectedId,
                    onClick = { onSelect(cat.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRailItem(
    name: String,
    favourite: Boolean,
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
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
            (if (favourite) "★ " else "") + name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected || focused) AccentPrimary else Color.White.copy(alpha = 0.85f),
        )
    }
}
