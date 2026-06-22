package com.itrepos.aiotv.ui.screen.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MirrorScreen(viewModel: MirrorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            "Tesla Mirror",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "ExoPlayer decodes the stream on your phone and sends video frames to Tesla's browser as JPEG — works with any codec, IPTV, or TorBox stream.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        if (state.isRunning) {
            // Live dot + status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(0xFF4CAF50), CircleShape))
                Text(
                    "  Casting to Tesla",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4CAF50),
                )
            }

            Spacer(Modifier.height(20.dp))

            // Primary URL via mDNS hostname — always works regardless of IP
            val url = "http://${MirrorService.MDNS_HOST}.local:${MirrorService.PORT}"

            Text(
                "Open in Tesla browser:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    url,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }

            if (state.localIp.isNotEmpty()) {
                Text(
                    "Fallback IP: http://${state.localIp}:${MirrorService.PORT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            // What's casting
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (state.castTitle.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(16.dp),
            ) {
                if (state.castTitle.isNotEmpty()) {
                    Column {
                        Text(
                            "Currently casting",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        Text(
                            state.castTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        "No stream loaded yet. Press Play on any IPTV channel, movie, or episode — then tap Cast below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Cast Now Playing button (if something is playing on phone)
            if (state.nowPlaying != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.castNowPlaying() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cast: ${state.nowPlaying!!.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { viewModel.stopCasting() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Stop Casting")
            }
        } else {
            // Not running — show setup guide
            Text(
                "How it works",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            listOf(
                "1. Enable Wi-Fi hotspot on your phone",
                "2. Connect your Tesla to the hotspot",
                "3. Play any content in AIO TV (IPTV, movie, episode)",
                "4. Come back here and tap Cast — phone decodes the stream and sends video frames to Tesla",
                "5. Open the shown URL in Tesla's browser",
                "6. Switch to other apps freely — casting keeps running",
            ).forEach { step ->
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                )
            }

            if (state.nowPlaying != null) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.castNowPlaying() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cast: ${state.nowPlaying!!.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Nothing playing yet. Play something in the app first, then come back to cast it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
