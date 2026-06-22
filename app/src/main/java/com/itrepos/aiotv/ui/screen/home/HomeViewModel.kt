package com.itrepos.aiotv.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.WatchProgressStore
import com.itrepos.aiotv.domain.model.ContentSection
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.WatchProgress
import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.domain.usecase.GetCatalogUseCase
import com.itrepos.aiotv.domain.usecase.GetChannelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val featuredItem: MediaItem? = null,
    val movieSections: List<ContentSection> = emptyList(),
    val seriesSections: List<ContentSection> = emptyList(),
    val liveGroups: List<String> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getChannels: GetChannelsUseCase,
    private val getCatalog: GetCatalogUseCase,
    private val watchProgressStore: WatchProgressStore,
    private val appDataStore: AppDataStore,
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
        viewModelScope.launch {
            appDataStore.channelGroupFilter.distinctUntilChanged().collect {
                loadContent()
            }
        }
    }

    fun loadContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val moviesDeferred = async {
                try { getCatalog.sections("movie") } catch (_: Exception) { emptyList() }
            }
            val seriesDeferred = async {
                try { getCatalog.sections("series") } catch (_: Exception) { emptyList() }
            }

            val movieSections = moviesDeferred.await()
            val seriesSections = seriesDeferred.await()
            val featured = movieSections.firstOrNull()?.items?.firstOrNull()
                ?: seriesSections.firstOrNull()?.items?.firstOrNull()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                movieSections = movieSections,
                seriesSections = seriesSections,
                featuredItem = featured,
            )
        }
        // Live groups load separately in background
        viewModelScope.launch {
            try {
                val channels = getChannels()
                val groups = channels.map { it.groupTitle }.distinct()
                _uiState.value = _uiState.value.copy(liveGroups = groups)
            } catch (_: Exception) {}
        }
    }
}
