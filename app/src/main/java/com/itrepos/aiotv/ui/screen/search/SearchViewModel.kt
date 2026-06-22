package com.itrepos.aiotv.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.usecase.PlayDirectUseCase
import com.itrepos.aiotv.domain.usecase.SearchVodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val mediaResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val resolvingId: String? = null,
    val directError: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchVod: SearchVodUseCase,
    private val playDirectUseCase: PlayDirectUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** ▶-on-card: resolve + auto-pick a source and hand the player route back via [onResolved]. */
    fun playDirect(item: MediaItem, onResolved: (url: String, title: String, progressId: String) -> Unit) {
        if (_state.value.resolvingId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(resolvingId = item.id, directError = null)
            val result = playDirectUseCase(item)
            _state.value = _state.value.copy(resolvingId = null)
            if (result != null) onResolved(result.url, result.title, result.progressId)
            else _state.value = _state.value.copy(directError = "Couldn't find a playable source")
        }
    }

    fun clearDirectError() { _state.value = _state.value.copy(directError = null) }

    private val _queryFlow = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { q -> if (q.length >= 2) search(q) else clearResults() }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        _queryFlow.value = q
    }

    private suspend fun search(query: String) {
        _state.value = _state.value.copy(isSearching = true, error = null)
        try {
            val results = searchVod(query)
            _state.value = _state.value.copy(isSearching = false, mediaResults = results)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isSearching = false,
                mediaResults = emptyList(),
                error = "Search unavailable — check your connection.",
            )
        }
    }

    private fun clearResults() {
        _state.value = _state.value.copy(mediaResults = emptyList(), error = null)
    }
}
