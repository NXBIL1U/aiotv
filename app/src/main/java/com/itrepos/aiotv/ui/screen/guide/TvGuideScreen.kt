package com.itrepos.aiotv.ui.screen.guide

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceCard
import com.itrepos.aiotv.ui.theme.SurfaceElevated
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CHANNEL_COL_W = 140.dp
private val HOUR_W = 240.dp
private val ROW_H = 56.dp
private val HEADER_H = 40.dp

// TV overscan-safe insets (10-foot UI).
private val TV_OVERSCAN_H = 48.dp
private val TV_OVERSCAN_V = 27.dp

@Composable
fun TvGuideScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onPlayChannel: (url: String, title: String) -> Unit,
    viewModel: TvGuideViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.channels.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    horizontal = if (isTv) TV_OVERSCAN_H else 24.dp,
                    vertical = if (isTv) TV_OVERSCAN_V else 24.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No channels yet. Add an IPTV source in Settings to see the TV Guide.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val hScroll = rememberScrollState()
    val nowMs = state.nowMs
    val startMs = remember(nowMs) { nowMs - (nowMs % (60 * 60 * 1000)) - 60 * 60 * 1000 }
    val endMs = startMs + 8 * 60 * 60 * 1000L
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Focus requester for the first reachable program cell so the D-pad has an entry point.
    val firstCellFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Channels are non-empty here; request focus on the first cell.
        runCatching { firstCellFocusRequester.requestFocus() }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = if (isTv) TV_OVERSCAN_H else 0.dp,
                vertical = if (isTv) TV_OVERSCAN_V else 0.dp,
            ),
    ) {
        // Adaptive: on wider canvases give channel labels & the time grid more room.
        val wide = maxWidth >= 600.dp
        val channelColW = if (wide) CHANNEL_COL_W + 40.dp else CHANNEL_COL_W
        val hourW = if (wide) HOUR_W + 60.dp else HOUR_W
        val fullWidth = hourW * 8

        Column(Modifier.fillMaxSize()) {
            // Header row: time labels
            Row(Modifier.height(HEADER_H)) {
                Box(Modifier.width(channelColW).fillMaxHeight().background(SurfaceCard))
                Row(Modifier.horizontalScroll(hScroll).fillMaxHeight()) {
                    var t = startMs
                    while (t < endMs) {
                        Box(
                            Modifier.width(hourW).fillMaxHeight().padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                timeFmt.format(Date(t)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        t += 60 * 60 * 1000
                    }
                }
            }

            // Channel rows
            LazyColumn(Modifier.fillMaxSize()) {
                items(
                    items = state.channels,
                    key = { channel -> channel.id },
                ) { channel ->
                    val isFirstChannel = state.channels.firstOrNull()?.id == channel.id
                    val progs = viewModel.epgForChannel(channel.tvgId ?: channel.id)
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        // Channel label
                        Box(
                            Modifier
                                .width(channelColW)
                                .height(ROW_H)
                                .background(SurfaceCard)
                                .clickable { onPlayChannel(channel.streamUrl, channel.name) }
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                channel.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                        // Programme cells
                        Row(Modifier.horizontalScroll(hScroll).height(ROW_H).weight(1f)) {
                            val totalMs = (endMs - startMs).toFloat()
                            val visibleProgs = progs.filter { it.endMs > startMs && it.startMs < endMs }
                            if (visibleProgs.isEmpty()) {
                                // Single focusable "no info" cell so the row is still reachable by D-pad.
                                ProgramCell(
                                    title = "No programme info",
                                    width = fullWidth,
                                    isNow = false,
                                    onClick = { onPlayChannel(channel.streamUrl, channel.name) },
                                    focusRequester = if (isFirstChannel) firstCellFocusRequester else null,
                                )
                            } else {
                                visibleProgs.forEachIndexed { index, prog ->
                                    val clampStart = prog.startMs.coerceAtLeast(startMs)
                                    val clampEnd = prog.endMs.coerceAtMost(endMs)
                                    val fraction = (clampEnd - clampStart).toFloat() / totalMs
                                    val isNow = prog.startMs <= nowMs && prog.endMs > nowMs
                                    ProgramCell(
                                        title = prog.title,
                                        width = fullWidth * fraction,
                                        isNow = isNow,
                                        onClick = { onPlayChannel(channel.streamUrl, channel.name) },
                                        focusRequester =
                                            if (isFirstChannel && index == 0) firstCellFocusRequester else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single focusable + clickable EPG cell with a 10-foot-visible focus indicator and
 * bring-into-view behaviour so D-pad navigation scrolls the focused cell into the viewport.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgramCell(
    title: String,
    width: Dp,
    isNow: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(targetValue = if (focused) 1.04f else 1f, label = "cellScale")

    val baseBg = if (isNow) AccentPrimary.copy(alpha = 0.2f) else SurfaceElevated
    val bg = if (focused) AccentPrimary.copy(alpha = 0.45f) else baseBg
    val borderColor = if (focused) Color.White else Color.White.copy(alpha = 0.1f)
    val borderWidth = if (focused) 2.dp else 0.5.dp

    var cellModifier = Modifier
        .width(width)
        .fillMaxHeight()
        .scale(scale)
        .clip(RoundedCornerShape(4.dp))
        .bringIntoViewRequester(bringIntoViewRequester)

    if (focusRequester != null) {
        cellModifier = cellModifier.focusRequester(focusRequester)
    }

    cellModifier = cellModifier
        .onFocusChanged { focusState ->
            focused = focusState.isFocused
            if (focusState.isFocused) {
                scope.launch { bringIntoViewRequester.bringIntoView() }
            }
        }
        .focusable(interactionSource = interactionSource)
        .clickable(interactionSource = interactionSource, indication = null) { onClick() }
        .background(bg)
        .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
        .padding(4.dp)

    Box(cellModifier, contentAlignment = Alignment.CenterStart) {
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}
