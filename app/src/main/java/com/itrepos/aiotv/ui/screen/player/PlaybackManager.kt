package com.itrepos.aiotv.ui.screen.player

import android.content.Context
import androidx.annotation.OptIn
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val url: String = "",
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
)

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val player: ExoPlayer by lazy { buildPlayer() }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
        val dsFactory = DefaultDataSource.Factory(context, httpFactory)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _state.value = _state.value.copy(
                            isBuffering = playbackState == Player.STATE_BUFFERING,
                            error = if (playbackState == Player.STATE_READY) null else _state.value.error,
                        )
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = _state.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _state.value = _state.value.copy(
                            error = error.localizedMessage ?: error.errorCodeName,
                            isBuffering = false,
                        )
                    }
                })
            }
    }

    fun load(url: String, title: String, startPositionMs: Long = 0L) {
        val mime = when {
            url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            url.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .apply { mime?.let { setMimeType(it) } }
            .build()
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        _state.value = PlaybackState(url = url, title = title, isBuffering = true)
    }

    fun stop() {
        player.stop()
        _state.value = PlaybackState()
    }

    val hasActiveStream: Boolean get() = _state.value.url.isNotEmpty()
}
