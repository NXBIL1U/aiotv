package com.itrepos.aiotv.ui.screen.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.EpgProgram
import com.itrepos.aiotv.ui.navigation.Screen
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.LiveDot
import com.itrepos.aiotv.ui.theme.SurfaceCard
import com.itrepos.aiotv.ui.theme.SurfaceElevated
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CHANNEL_COL_W = 140.dp
private val HOUR_W = 240.dp
private val ROW_H = 56.dp
private val HEADER_H = 40.dp

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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Add an IPTV source in Settings to see the TV Guide.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val hScroll = rememberScrollState()
    val nowMs = state.nowMs
    val startMs = remember { nowMs - (nowMs % (60 * 60 * 1000)) - 60 * 60 * 1000 }
    val endMs = startMs + 8 * 60 * 60 * 1000L
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header row: time labels
        Row(Modifier.height(HEADER_H)) {
            Box(Modifier.width(CHANNEL_COL_W).fillMaxHeight().background(SurfaceCard))
            Row(Modifier.horizontalScroll(hScroll).fillMaxHeight()) {
                var t = startMs
                while (t < endMs) {
                    Box(
                        Modifier.width(HOUR_W).fillMaxHeight().padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(timeFmt.format(Date(t)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    t += 60 * 60 * 1000
                }
            }
        }

        // Channel rows
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.channels) { channel ->
                val progs = viewModel.epgForChannel(channel.tvgId ?: channel.id)
                Row(Modifier.height(IntrinsicSize.Min)) {
                    // Channel label
                    Box(
                        Modifier
                            .width(CHANNEL_COL_W)
                            .height(ROW_H)
                            .background(SurfaceCard)
                            .clickable { onPlayChannel(channel.streamUrl, channel.name) }
                            .padding(8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                    // Programme cells
                    Row(Modifier.horizontalScroll(hScroll).height(ROW_H).weight(1f)) {
                        val totalMs = (endMs - startMs).toFloat()
                        val fullWidth = HOUR_W * 8
                        if (progs.isEmpty()) {
                            Box(
                                Modifier.width(fullWidth).fillMaxHeight()
                                    .background(SurfaceElevated)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.1f))
                                    .padding(4.dp)
                            )
                        } else {
                            progs.filter { it.endMs > startMs && it.startMs < endMs }.forEach { prog ->
                                val clampStart = prog.startMs.coerceAtLeast(startMs)
                                val clampEnd = prog.endMs.coerceAtMost(endMs)
                                val fraction = (clampEnd - clampStart).toFloat() / totalMs
                                val isNow = prog.startMs <= nowMs && prog.endMs > nowMs
                                Box(
                                    Modifier
                                        .width(fullWidth * fraction)
                                        .fillMaxHeight()
                                        .background(if (isNow) AccentPrimary.copy(alpha = 0.2f) else SurfaceElevated)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.1f))
                                        .padding(4.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(prog.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
