package com.itrepos.aiotv.ui.screen.player

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    progressId: String = url,
    isTv: Boolean,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }

    // Up-next countdown state
    var ended by remember { mutableStateOf(false) }
    var showUpNext by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(5) }
    // FocusRequester for TV: Play-now button gets initial D-pad focus
    val playNowFocus = remember { FocusRequester() }

    // The now-playing session (null for live channels / direct plays).
    // We latch sessionActive=true the moment the controller holds a session for THIS play
    // (progressId matches the route arg at entry). Once latched, we follow the controller's
    // emitted state even as progressId changes (e.g. after advanceToNextEpisode()). This
    // means the player correctly swaps to the next episode's URL/progressId without falling
    // back to the original route URL.
    //
    // Live channels / direct plays: controller state is null or has a different progressId at
    // entry, so sessionActive stays false and route url is used (no session, no failover/next).
    val playbackState by viewModel.playbackState.collectAsState()
    var sessionActive by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (playbackState?.progressId == progressId) sessionActive = true
    }
    val session = if (sessionActive) playbackState else null
    val playUrl = session?.currentUrl ?: url
    val playProgressId = session?.progressId ?: progressId
    // The error listener is registered once (keyed on exoPlayer); read the latest session via this.
    val hasSession by rememberUpdatedState(session != null)

    // Resume rule: a failover keeps the position (same progressId), a new episode starts fresh.
    val lastPositionMs = remember { mutableStateOf(0L) }
    val lastProgressId = remember { mutableStateOf<String?>(null) }

    // Helper: advance to the next episode (or surface an error on failure)
    fun triggerAdvance() {
        scope.launch {
            showUpNext = false
            val ok = viewModel.advanceToNextEpisode()
            if (!ok) {
                errorMsg = "Couldn't load next episode"
            }
            // On success, playbackState emits a new progressId → the media LaunchedEffect picks
            // it up and loads the next episode; ended resets to false on STATE_READY.
        }
    }

    // Show overlay when episode ends AND there is a next episode; hide when no upNext.
    LaunchedEffect(ended, session?.upNext) {
        if (ended && session?.upNext != null) {
            showUpNext = true
        } else {
            showUpNext = false
        }
    }

    // Countdown: 5 → 0 then auto-advance
    LaunchedEffect(showUpNext) {
        if (!showUpNext) return@LaunchedEffect
        secondsLeft = 5
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        triggerAdvance()
    }

    // Grant initial focus to Play-now on TV when the overlay appears
    LaunchedEffect(showUpNext, isTv) {
        if (showUpNext && isTv) {
            runCatching { playNowFocus.requestFocus() }
        }
    }

    val exoPlayer = remember {
        // Many IPTV / Xtream / VOD servers reject the default ExoPlayer User-Agent
        // and frequently 302 between http/https, so we present a VLC-like UA and
        // allow cross-protocol redirects — this is what makes most streams play.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // Fail fast: a dead URL surfaces onPlayerError quickly so failover can swap sources
        // instead of ExoPlayer retrying the same broken source many times.
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(/* minLoadableRetryCount = */ 1))

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { setWakeMode(C.WAKE_MODE_NETWORK) }
    }

    // Surface playback errors and buffering state instead of a silent black screen.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Remember where we were so a failover resumes at the same spot.
                lastPositionMs.value = exoPlayer.currentPosition
                if (hasSession) {
                    // Silent mid-play failover: try the next candidate source for the same item.
                    isBuffering = true
                    scope.launch {
                        val advanced = viewModel.failover()
                        if (!advanced) {
                            // Sources exhausted — surface the error + Retry (Back returns to Detail).
                            errorMsg = error.localizedMessage ?: error.errorCodeName
                            isBuffering = false
                        }
                    }
                } else {
                    errorMsg = error.localizedMessage ?: error.errorCodeName
                    isBuffering = false
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        errorMsg = null
                        // Reset ended when new media is ready (e.g. after advance to next episode)
                        ended = false
                        showUpNext = false
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        ended = true
                    }
                    else -> Unit
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Set the media item, hinting the container type (extension-less IPTV/redirect
    // URLs otherwise get misdetected as progressive). Re-keyed on (playUrl, playProgressId) so a
    // failover (new url, same progressId) and a next-episode (new url + progressId) both re-run.
    LaunchedEffect(playUrl, playProgressId) {
        val mime = when {
            playUrl.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            playUrl.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            // Xtream live is raw MPEG-TS (.ts) — hint it so ExoPlayer picks the TS extractor
            // instead of sniffing/misdetecting the continuous progressive stream.
            playUrl.contains(".ts", ignoreCase = true) -> MimeTypes.VIDEO_MP2T
            else -> null
        }
        // Resume rule: failover (same progressId) keeps the captured position; a new episode
        // (changed progressId) starts from its own saved progress.
        val startPositionMs = if (playProgressId == lastProgressId.value) {
            lastPositionMs.value
        } else {
            viewModel.getStartPosition(playProgressId)
        }
        lastProgressId.value = playProgressId
        val mediaItem = MediaItem.Builder()
            .setUri(playUrl)
            .apply { mime?.let { setMimeType(it) } }
            .build()
        exoPlayer.setMediaItem(mediaItem, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Periodically persist watch progress (and track the live position for the resume rule).
    LaunchedEffect(exoPlayer, playProgressId) {
        while (true) {
            delay(5_000)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            if (pos > 0) {
                lastPositionMs.value = pos
                viewModel.saveProgress(playProgressId, pos, dur)
            }
        }
    }

    // Pause when the app is backgrounded (HOME / recents) so audio doesn't keep
    // playing and the codec/surface isn't held.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            // Use the most recently played progressId (tracked by the media LaunchedEffect) so a
            // post-advance dispose saves against the right episode, not the route's original arg.
            if (pos > 0) viewModel.saveProgress(lastProgressId.value ?: playProgressId, pos, dur)
            exoPlayer.release()
            // Clear the singleton session so a subsequent navigation with the same progressId
            // (e.g. manual source pick from Sources sheet, or a movie whose progressId==url)
            // always starts from the URL it was actually given — never from a stale session.
            viewModel.clearSession()
        }
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
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

        if (isBuffering && errorMsg == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        errorMsg?.let { msg ->
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
                    errorMsg = null
                    isBuffering = true
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }) {
                    Text("Retry")
                }
            }
        }

        // Up-next countdown overlay — shown bottom-end when an episode ends and upNext exists.
        val upNextEpisode = session?.upNext
        if (showUpNext && upNextEpisode != null) {
            UpNextOverlay(
                episode = upNextEpisode,
                secondsLeft = secondsLeft,
                onPlayNow = {
                    // Cancel the countdown coroutine implicitly by setting showUpNext = false
                    // (the LaunchedEffect key changes and the old coroutine is cancelled).
                    showUpNext = false
                    triggerAdvance()
                },
                onCancel = {
                    showUpNext = false
                    ended = false
                },
                playNowFocusRequester = if (isTv) playNowFocus else null,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}
