package com.itrepos.aiotv.ui.screen.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    val playbackManager: PlaybackManager,
) : ViewModel()
