package com.itrepos.aiotv.ui.screen.mirror

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NowPlaying {
    private val _stream = MutableStateFlow<NowPlayingState?>(null)
    val stream: StateFlow<NowPlayingState?> = _stream.asStateFlow()

    fun update(url: String, title: String) {
        _stream.value = NowPlayingState(url, title)
    }

    fun clear() {
        _stream.value = null
    }
}

data class NowPlayingState(val url: String, val title: String)
