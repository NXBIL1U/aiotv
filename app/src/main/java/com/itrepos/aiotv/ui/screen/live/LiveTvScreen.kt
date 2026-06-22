package com.itrepos.aiotv.ui.screen.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.ui.components.FocusableCard
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceCard
import com.itrepos.aiotv.ui.theme.SurfaceElevated
import kotlinx.coroutines.delay

// 10-foot overscan-safe insets, matching the other TV screens.
private val TV_OVERSCAN_H = 48.dp
private val TV_OVERSCAN_V = 27.dp

// Short labels for the region caption shown below the category trigger.
private val REGION_LABELS = mapOf(
    "US"    to "US",
    "UK"    to "UK",
    "EN"    to "EN",
    "LATAM" to "LATAM",
    "EU"    to "EU",
    "MENA"  to "MENA",
    "OTHER" to "Other",
)

@Composable
fun LiveTvScreen(
    isTv: Boolean,
    onPlayChannel: (url: String, title: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Only the category picker is shown on the landing; region is now in Settings.
    var showCategoryPicker by remember { mutableStateOf(false) }

    if (state.isLoading) {
        LoadingState()
        return
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = if (isTv) TV_OVERSCAN_H else 0.dp,
                vertical   = if (isTv) TV_OVERSCAN_V else 0.dp,
            ),
    ) {
        // Empty/error states — no source configured or provider unreachable.
        if (state.categories.size <= 1 && state.channels.isEmpty()
            && state.favChannels.isEmpty() && state.recent.isEmpty()
        ) {
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
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { viewModel.retry() }) { Text("Retry") }
                } else {
                    Text(
                        "No IPTV source yet. Add one in Settings to watch Live TV.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@BoxWithConstraints
        }

        val wide = maxWidth >= 600.dp

        // Derive favourite category ids inline (no need to add to state separately).
        val favCategoryIds = remember(state.favCategories) {
            state.favCategories.map { it.id }.toSet()
        }

        // Label for the single Sky-style category trigger: "All Channels" or the category name.
        val categoryLabel = remember(state.selectedCategoryId, state.categories) {
            when (state.selectedCategoryId) {
                ALL_CATEGORY_ID -> "All Channels"
                else -> state.categories.find { it.id == state.selectedCategoryId }?.name
                    ?: "All Channels"
            }
        }

        // Non-interactive region caption: "UK · US · EN" (up to 4, then "+N more").
        val regionCaption = remember(state.selectedRegions) {
            val sorted = state.selectedRegions.mapNotNull { REGION_LABELS[it] }.sorted()
            when {
                sorted.isEmpty() -> ""
                sorted.size <= 4 -> sorted.joinToString(" · ")
                else -> "${sorted.take(4).joinToString(" · ")} +${sorted.size - 4}"
            }
        }

        if (wide) {
            // ── Wide layout: left rail with Sky-style category trigger + fav-categories ──
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(240.dp)
                        .fillMaxHeight()
                        .background(SurfaceCard)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Sky-style single category trigger: "All Channels ▾" or "<Category> ▾"
                    AssistChip(
                        onClick = { showCategoryPicker = true },
                        label = { Text(categoryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Open category picker",
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Non-interactive region caption — scope reminder; change regions in Settings.
                    if (regionCaption.isNotEmpty()) {
                        Text(
                            regionCaption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    // Favourite category quick chips
                    if (state.favCategories.isNotEmpty()) {
                        Text(
                            "Favourite categories",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        state.favCategories.forEach { cat ->
                            val isSelected = cat.id == state.selectedCategoryId
                            Text(
                                cat.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) AccentPrimary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.selectCategory(cat.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                // Right pane: search + content
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                ) {
                    SearchField(
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                    )
                    ForYouContent(
                        state = state,
                        favCategoryIds = favCategoryIds,
                        onPlayChannel = onPlayChannel,
                        viewModel = viewModel,
                        onOpenCategoryPicker = { showCategoryPicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            // ── Phone layout: search → single Sky-style category trigger → content ──
            Column(Modifier.fillMaxSize()) {
                // Search field (primary control)
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )

                // Single category trigger: "All Channels ▾" or "<Category> ▾"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { showCategoryPicker = true },
                        label = { Text(categoryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Open category picker",
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                    // Non-interactive region caption — scope reminder; change regions in Settings.
                    if (regionCaption.isNotEmpty()) {
                        Text(
                            regionCaption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                ForYouContent(
                    state = state,
                    favCategoryIds = favCategoryIds,
                    onPlayChannel = onPlayChannel,
                    viewModel = viewModel,
                    onOpenCategoryPicker = { showCategoryPicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Pickers (shown as ModalBottomSheets over the content) ──
        // Note: Region selection is now in Settings; only the category picker remains here.
        if (showCategoryPicker) {
            CategoryPicker(
                categories = state.categories,
                selectedId = state.selectedCategoryId,
                favCategoryIds = favCategoryIds,
                onSelectCategory = { id ->
                    viewModel.selectCategory(id)
                    // onDismiss will be called by CategoryPicker after tap
                },
                onToggleFav = { id -> viewModel.toggleFavCategory(id) },
                onDismiss = { showCategoryPicker = false },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// "For You" content area
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ForYouContent(
    state: LiveTvState,
    favCategoryIds: Set<String>,
    onPlayChannel: (url: String, title: String) -> Unit,
    viewModel: LiveTvViewModel,
    onOpenCategoryPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.query.isNotBlank()) {
        // ── Search mode: global results only ──
        SearchResultsList(
            state = state,
            onPlayChannel = onPlayChannel,
            viewModel = viewModel,
            modifier = modifier,
        )
    } else {
        // ── Browse mode: For You landing ──
        BrowseLanding(
            state = state,
            favCategoryIds = favCategoryIds,
            onPlayChannel = onPlayChannel,
            viewModel = viewModel,
            onOpenCategoryPicker = onOpenCategoryPicker,
            modifier = modifier,
        )
    }
}

@Composable
private fun SearchResultsList(
    state: LiveTvState,
    onPlayChannel: (url: String, title: String) -> Unit,
    viewModel: LiveTvViewModel,
    modifier: Modifier = Modifier,
) {
    if (state.channels.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No channels match \"${state.query}\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.channels, key = { it.id }) { channel ->
            LaunchedEffect(channel.id) { viewModel.onChannelVisible(channel) }
            ChannelRow(
                channel = channel,
                nowNext = state.epg[channel.id],
                isFavourite = channel.id in state.favChannelIds,
                onToggleFavourite = { viewModel.toggleFavChannel(channel.id) },
                onClick = {
                    viewModel.onChannelPlayed(channel.id)
                    onPlayChannel(channel.streamUrl, channel.name)
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun BrowseLanding(
    state: LiveTvState,
    favCategoryIds: Set<String>,
    onPlayChannel: (url: String, title: String) -> Unit,
    viewModel: LiveTvViewModel,
    onOpenCategoryPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasFavChannels = state.favChannels.isNotEmpty()
    val hasFavCategories = state.favCategories.isNotEmpty()
    val hasRecent = state.recent.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── (a) Favourite categories: quick scope chips ──
        if (hasFavCategories) {
            item(key = "fav_cats_header") {
                SectionHeader("Favourite Categories")
            }
            item(key = "fav_cats_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.favCategories, key = { "fav_cat_${it.id}" }) { cat ->
                        val isSelected = cat.id == state.selectedCategoryId
                        SuggestionChip(
                            onClick = { viewModel.selectCategory(cat.id) },
                            label = { Text(cat.name, maxLines = 1) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) AccentPrimary.copy(alpha = 0.15f)
                                                 else SurfaceCard,
                                labelColor = if (isSelected) AccentPrimary
                                             else Color.White.copy(alpha = 0.85f),
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) AccentPrimary
                                              else MaterialTheme.colorScheme.outline,
                            ),
                        )
                    }
                }
            }
            item(key = "fav_cats_spacer") { Spacer(Modifier.height(8.dp)) }
        }

        // ── (b) Favourite channels: horizontal card row ──
        if (hasFavChannels) {
            item(key = "fav_channels_header") {
                SectionHeader("Favourites")
            }
            item(key = "fav_channels_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.favChannels, key = { "fav_ch_${it.id}" }) { channel ->
                        LaunchedEffect(channel.id) { viewModel.onChannelVisible(channel) }
                        ChannelCard(
                            channel = channel,
                            onClick = {
                                viewModel.onChannelPlayed(channel.id)
                                onPlayChannel(channel.streamUrl, channel.name)
                            },
                        )
                    }
                }
            }
            item(key = "fav_channels_spacer") { Spacer(Modifier.height(8.dp)) }
        }

        // ── (c) Recently watched: horizontal card row ──
        if (hasRecent) {
            item(key = "recent_header") {
                SectionHeader("Recently Watched")
            }
            item(key = "recent_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.recent, key = { "recent_ch_${it.id}" }) { channel ->
                        LaunchedEffect(channel.id) { viewModel.onChannelVisible(channel) }
                        ChannelCard(
                            channel = channel,
                            onClick = {
                                viewModel.onChannelPlayed(channel.id)
                                onPlayChannel(channel.streamUrl, channel.name)
                            },
                        )
                    }
                }
            }
            item(key = "recent_spacer") { Spacer(Modifier.height(8.dp)) }
        }

        // ── (d) Scoped channel list (region + category filtered) ──
        val sectionTitle = when {
            state.selectedCategoryId == ALL_CATEGORY_ID ->
                "Channels · ${state.selectedRegions.mapNotNull { REGION_LABELS[it] }.sorted().joinToString(", ")}"
            else -> state.categories.find { it.id == state.selectedCategoryId }?.name ?: "Channels"
        }

        if (state.channels.isEmpty()) {
            item(key = "no_channels") {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "No channels in this region/category.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onOpenCategoryPicker) {
                        Text("Try another category")
                    }
                }
            }
        } else {
            item(key = "channels_header") {
                SectionHeader(sectionTitle)
            }

            // Subtle hint when no favourites or recents have been set yet.
            if (!hasFavChannels && !hasRecent && !hasFavCategories) {
                item(key = "fav_hint") {
                    Text(
                        "★ a channel or category to build your favourites list.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            items(state.channels, key = { it.id }) { channel ->
                LaunchedEffect(channel.id) { viewModel.onChannelVisible(channel) }
                ChannelRow(
                    channel = channel,
                    nowNext = state.epg[channel.id],
                    isFavourite = channel.id in state.favChannelIds,
                    onToggleFavourite = { viewModel.toggleFavChannel(channel.id) },
                    onClick = {
                        viewModel.onChannelPlayed(channel.id)
                        onPlayChannel(channel.streamUrl, channel.name)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        // Bottom padding
        item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small reusable composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Compact logo card used in horizontal favourite/recent rows. */
@Composable
private fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableCard(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 80.dp, max = 100.dp)
            .height(72.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
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
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceElevated),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
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
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

/**
 * Loading spinner with a message that appears after ~4 s and escalates the longer it takes.
 * The ViewModel keeps retrying underneath; this is purely the on-screen status.
 */
@Composable
private fun LoadingState() {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
        }
    }
    val message = when {
        elapsed < 15 -> "Loading channels…"
        elapsed < 30 -> "Still loading — your provider is taking a while…"
        else -> "Your provider is slow to respond. Still trying…"
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        if (elapsed >= 4) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
