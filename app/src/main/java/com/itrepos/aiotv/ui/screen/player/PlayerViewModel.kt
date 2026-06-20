package com.itrepos.aiotv.ui.screen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.WatchProgressStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val watchProgressStore: WatchProgressStore,
) : ViewModel() {

    fun saveProgress(id: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            watchProgressStore.saveProgress(id, positionMs, durationMs)
        }
    }
}
