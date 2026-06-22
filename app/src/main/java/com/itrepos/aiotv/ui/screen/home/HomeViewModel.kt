package com.itrepos.aiotv.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.WatchProgressStore
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.WatchProgress
import com.itrepos.aiotv.domain.usecase.GetCatalogUseCase
import com.itrepos.aiotv.domain.usecase.PlayDirectUseCase
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
    val continueWatching: List<WatchProgress> = emptyList(),
    val error: String? = null,
    val resolvingId: String? = null,
    val directError: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCatalog: GetCatalogUseCase,
    private val watchProgressStore: WatchProgressStore,
    private val playDirectUseCase: PlayDirectUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** ▶-on-card: resolve + auto-pick a source and hand the player route back via [onResolved]. */
    fun playDirect(item: MediaItem, onResolved: (url: String, title: String, progressId: String) -> Unit) {
        if (_uiState.value.resolvingId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingId = item.id, directError = null)
            val result = playDirectUseCase(item)
            _uiState.value = _uiState.value.copy(resolvingId = null)
            if (result != null) onResolved(result.url, result.title, result.progressId)
            else _uiState.value = _uiState.value.copy(directError = "Couldn't find a playable source")
        }
    }

    fun clearDirectError() { _uiState.value = _uiState.value.copy(directError = null) }

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
                // runCatching per branch: a failure in one source (bad addon / IPTV)
                // must not fail the sibling async and crash the parent scope.
                val moviesDeferred = async { runCatching { getCatalog("movie") }.getOrDefault(emptyList()) }
                val seriesDeferred = async { runCatching { getCatalog("series") }.getOrDefault(emptyList()) }

                val movies = moviesDeferred.await()
                val series = seriesDeferred.await()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    movies = movies,
                    series = series,
                    featuredItem = movies.firstOrNull(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
