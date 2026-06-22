package com.itrepos.aiotv.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.usecase.GetCatalogUseCase
import com.itrepos.aiotv.domain.usecase.GetChannelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val mediaResults: List<MediaItem> = emptyList(),
    val channelResults: List<Channel> = emptyList(),
    val isSearching: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getCatalog: GetCatalogUseCase,
    private val getChannels: GetChannelsUseCase,
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
                .collect { q -> if (q.length >= 2) search(q) else clearResults() }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        _queryFlow.value = q
    }

    private suspend fun search(query: String) {
        _state.value = _state.value.copy(isSearching = true)
        // Search Stremio addons (movies + series via search endpoint or local filter)
        val media = try { getCatalog.search(query) } catch (_: Exception) { emptyList() }
        // Search channels locally by name
        val channels = try {
            getChannels().filter { it.name.contains(query, ignoreCase = true) }
        } catch (_: Exception) { emptyList() }
        _state.value = _state.value.copy(
            isSearching = false,
            mediaResults = media,
            channelResults = channels,
        )
    }

    private fun clearResults() {
        _state.value = _state.value.copy(mediaResults = emptyList(), channelResults = emptyList())
    }
}
