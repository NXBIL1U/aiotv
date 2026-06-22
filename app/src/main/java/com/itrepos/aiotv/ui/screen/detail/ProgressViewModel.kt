package com.itrepos.aiotv.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.WatchProgressStore
import com.itrepos.aiotv.domain.model.WatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes all stored watch progress as a single StateFlow so the series UI can look up per-episode
 * resume fractions synchronously without each row spawning its own Flow collection.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    store: WatchProgressStore,
) : ViewModel() {
    val progress: StateFlow<List<WatchProgress>> =
        store.getAllProgress().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
