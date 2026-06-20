package com.itrepos.aiotv.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.WatchProgressStore
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.WatchProgress
import com.itrepos.aiotv.domain.usecase.GetCatalogUseCase
import com.itrepos.aiotv.domain.usecase.GetChannelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val featuredItem: MediaItem? = null,
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val liveChannels: List<Channel> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getChannels: GetChannelsUseCase,
    private val getCatalog: GetCatalogUseCase,
    private val watchProgressStore: WatchProgressStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadContent()
        viewModelScope.launch {
            watchProgressStore.getAllProgress().collect { progress ->
                _uiState.value = _uiState.value.copy(continueWatching = progress.take(20))
            }
        }
    }

    fun loadContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val moviesDeferred = async { getCatalog("movie") }
                val seriesDeferred = async { getCatalog("series") }
                val channelsDeferred = async { getChannels() }

                val movies = moviesDeferred.await()
                val series = seriesDeferred.await()
                val channels = channelsDeferred.await()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    movies = movies,
                    series = series,
                    liveChannels = channels,
                    featuredItem = movies.firstOrNull(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
