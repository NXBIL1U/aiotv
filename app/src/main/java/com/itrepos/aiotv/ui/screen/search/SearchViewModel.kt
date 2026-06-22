package com.itrepos.aiotv.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.domain.model.MediaItem
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
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchVod: SearchVodUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

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
