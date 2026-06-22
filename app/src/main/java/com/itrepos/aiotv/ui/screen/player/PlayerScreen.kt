package com.itrepos.aiotv.ui.screen.player

import android.app.Activity
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.itrepos.aiotv.ui.screen.mirror.MirrorViewModel
import com.itrepos.aiotv.ui.screen.mirror.NowPlaying
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    isTv: Boolean,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    mirrorViewModel: MirrorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playbackManager = viewModel.playbackManager
    val playbackState by playbackManager.state.collectAsState()

    // Immersive fullscreen — hides status bar and nav bar while the player is shown
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.let {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller?.let {
                it.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    LaunchedEffect(url) {
        val startPos = viewModel.getStartPosition(url)
        playbackManager.load(url, title, startPos)
        NowPlaying.update(url, title)
    }

    // Periodically persist watch progress
    LaunchedEffect(url) {
        while (true) {
            delay(5_000)
            val pos = playbackManager.player.currentPosition
            val dur = playbackManager.player.duration.takeIf { it > 0 } ?: 0L
            if (pos > 0) viewModel.saveProgress(url, pos, dur)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = playbackManager.player.currentPosition
            val dur = playbackManager.player.duration.takeIf { it > 0 } ?: 0L
            if (pos > 0) viewModel.saveProgress(url, pos, dur)
            // Do NOT release the player — it keeps playing in background
            // NowPlaying stays set so the mini player bar remains visible
        }
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playbackManager.player
                    useController = true
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        )

        IconButton(
            onClick = { mirrorViewModel.castStream(url, title) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(Icons.Default.Cast, contentDescription = "Cast to Tesla", tint = Color.White)
        }

        if (playbackState.isBuffering && playbackState.error == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        playbackState.error?.let { msg ->
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Playback error: $msg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red,
                )
                Button(onClick = {
                    playbackManager.load(url, title)
                }) {
                    Text("Retry")
                }
            }
        }
    }
}
