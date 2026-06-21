package com.itrepos.aiotv.ui.screen.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceCard

/**
 * A Material3 ModalBottomSheet that shows a searchable list of categories within the currently
 * selected regions. Each row has:
 *  - a ★ toggle to favourite/unfavourite the category
 *  - a tap handler that scopes the channel list to that category and dismisses the sheet
 *
 * @param categories      region-scoped category list from [LiveTvState.categories] (includes the
 *                        synthetic "All" entry with id [ALL_CATEGORY_ID]).
 * @param selectedId      the currently selected category id.
 * @param favCategoryIds  set of category ids that are favourited.
 * @param onSelectCategory called when the user taps a row (with the category id).
 * @param onToggleFav     called when the user taps the ★ for a category id.
 * @param onDismiss       called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    categories: List<ChannelCategory>,
    selectedId: String,
    favCategoryIds: Set<String>,
    onSelectCategory: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterQuery by remember { mutableStateOf("") }

    val filtered = remember(categories, filterQuery) {
        if (filterQuery.isBlank()) categories
        else categories.filter { it.name.contains(filterQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Search / filter field
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                label = { Text("Filter categories") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                // Leave room for the bottom-sheet drag handle + system nav
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(filtered, key = { it.id }) { category ->
                    val isSelected = category.id == selectedId
                    val isFav = category.id in favCategoryIds
                    // "All" is synthetic — never show a fav toggle for it
                    val showFavToggle = category.id != ALL_CATEGORY_ID

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) AccentPrimary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                onSelectCategory(category.id)
                                onDismiss()
                            }
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (showFavToggle) {
                            IconButton(
                                onClick = { onToggleFav(category.id) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Star
                                                  else Icons.Outlined.StarBorder,
                                    contentDescription = if (isFav) "Remove category from favourites"
                                                         else "Add category to favourites",
                                    tint = if (isFav) AccentPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // Spacer to keep row height consistent
                            Spacer(Modifier.width(40.dp))
                        }
                    }
                }
            }
        }
    }
}
