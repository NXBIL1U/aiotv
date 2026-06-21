package com.itrepos.aiotv.ui.screen.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.repository.LiveTvRepository
import com.itrepos.aiotv.data.repository.toDomain
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.domain.model.EpgNowNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/** Sentinel id for the synthetic "All" category that shows every channel. */
const val ALL_CATEGORY_ID = "__all__"

data class LiveTvState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasSource: Boolean = false,
    val categories: List<ChannelCategory> = emptyList(),
    val selectedCategoryId: String = ALL_CATEGORY_ID,
    val channels: List<Channel> = emptyList(),
    val query: String = "",
    val epg: Map<String, EpgNowNext> = emptyMap(),
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveTvRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    // Keep all channels in memory for local category filtering and search
    // (Room Flows push updates; we filter locally to avoid extra DB round-trips per gesture)
    private var allChannels: List<Channel> = emptyList()

    private val epgSemaphore = Semaphore(4)
    private val epgInFlight = mutableSetOf<String>()
    private var queryJob: Job? = null

    init {
        observeRoomFlows()
        backgroundRefresh()
    }

    /**
     * Subscribe to Room Flows for channels and categories. Room pushes updates whenever the
     * DB changes — no manual polling needed. All channels have regionTag="" in Phase 1 so we
     * query for that to get everything; Phase 2 introduces real region filtering.
     */
    private fun observeRoomFlows() {
        // Phase 1: regionTag for all rows is ""; observe with that tag to get all channels.
        // When Phase 2 lands, this becomes the selected-region set from DataStore.
        val regionTags = listOf("")

        combine(
            repository.observeChannels(regionTags, categoryId = null),
            repository.observeAllCategories(),
        ) { channelEntities, categoryEntities ->
            Pair(channelEntities, categoryEntities)
        }
            .catch { e ->
                // Don't crash the UI if Room throws (shouldn't happen, but be safe)
                android.util.Log.e("LiveTvViewModel", "Room flow error", e)
            }
            .onEach { (channelEntities, categoryEntities) ->
                val channels = channelEntities.map { it.toDomain() }
                val categories = categoryEntities.map { it.toDomain() }

                allChannels = channels

                val currentQuery = _state.value.query
                val currentCategoryId = _state.value.selectedCategoryId

                val filteredChannels = when {
                    currentQuery.isNotBlank() ->
                        channels.filter { it.name.contains(currentQuery, ignoreCase = true) }
                    else -> filterByCategory(currentCategoryId, channels)
                }

                _state.update { s ->
                    s.copy(
                        // Once we have any data from Room we are no longer in the initial loading state.
                        isLoading = false,
                        hasSource = channels.isNotEmpty() || categories.isNotEmpty(),
                        channels = filteredChannels,
                        categories = listOf(ChannelCategory(ALL_CATEGORY_ID, "All")) + categories,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Trigger a background network refresh. If Room already has fresh data (within TTL) this
     * is a no-op; otherwise channels/categories are updated and the Flows above push the new
     * data to the UI automatically.
     */
    private fun backgroundRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val fetched = repository.refresh(force = false)
            // If no network fetch occurred and Room is still empty, we have no source configured
            if (!fetched && allChannels.isEmpty()) {
                _state.update { it.copy(isLoading = false, hasSource = false, isRefreshing = false) }
            } else {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Force a network refresh (e.g. pull-to-refresh or manual Retry). */
    fun retry() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            repository.refresh(force = true)
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectCategory(id: String) {
        val filtered = filterByCategory(id, allChannels)
        _state.update { it.copy(selectedCategoryId = id, query = "", channels = filtered) }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(250)
            val result = if (q.isBlank()) {
                filterByCategory(_state.value.selectedCategoryId, allChannels)
            } else {
                allChannels.filter { it.name.contains(q, ignoreCase = true) }
            }
            _state.update { it.copy(channels = result) }
        }
    }

    /** Lazily fetch now/next EPG for a channel as its row first becomes visible. */
    fun onChannelVisible(channel: Channel) {
        if (_state.value.epg.containsKey(channel.id)) return
        synchronized(epgInFlight) {
            if (!epgInFlight.add(channel.id)) return
        }
        viewModelScope.launch {
            try {
                val nowNext = epgSemaphore.withPermit { repository.epgFor(channel.id) }
                    ?: EpgNowNext(null, null)
                _state.update { it.copy(epg = it.epg + (channel.id to nowNext)) }
            } finally {
                synchronized(epgInFlight) { epgInFlight.remove(channel.id) }
            }
        }
    }

    private fun filterByCategory(id: String, channels: List<Channel>): List<Channel> =
        if (id == ALL_CATEGORY_ID) channels
        else channels.filter { it.categoryKey == id }
}
