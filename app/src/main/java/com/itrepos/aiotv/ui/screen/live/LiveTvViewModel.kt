package com.itrepos.aiotv.ui.screen.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.repository.LiveTvRepository
import com.itrepos.aiotv.data.repository.toDomain
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.domain.model.EpgNowNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
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
    val selectedRegions: Set<String> = AppDataStore.DEFAULT_LIVE_REGIONS,
    val categories: List<ChannelCategory> = emptyList(),
    val selectedCategoryId: String = ALL_CATEGORY_ID,
    val channels: List<Channel> = emptyList(),
    val query: String = "",
    val epg: Map<String, EpgNowNext> = emptyMap(),
    // Phase 3: favourites + recently-watched
    val favChannels: List<Channel> = emptyList(),
    val favCategories: List<ChannelCategory> = emptyList(),
    val recent: List<Channel> = emptyList(),
    /** Set of channel ids that are currently favourited — used for O(1) ★ rendering per row. */
    val favChannelIds: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveTvRepository,
    private val appDataStore: AppDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    private val epgSemaphore = Semaphore(4)
    private val epgInFlight = mutableSetOf<String>()

    /** Current search query (debounced into the channel-list flow). */
    private val queryFlow = MutableStateFlow("")

    /** Current category selection ([ALL_CATEGORY_ID] = no category filter). */
    private val categoryIdFlow = MutableStateFlow(ALL_CATEGORY_ID)

    init {
        observeRoomFlows()
        observeFavAndRecentFlows()
        backgroundRefresh()
    }

    /**
     * Subscribe to Room Flows for channels and categories.
     *
     * The channel list is driven by ONE combined flow over three inputs:
     *  - the persisted [AppDataStore.liveRegions],
     *  - the current [categoryIdFlow] selection, and
     *  - a 250ms-debounced [queryFlow].
     *
     * [flatMapLatest] picks the right Room Flow for the latest inputs: a blank query yields the
     * region+category-scoped flow; a non-blank query yields the global search flow (which ignores
     * region/category so every channel stays reachable). A single collector means no leaked
     * per-keystroke collectors, and clearing the query naturally switches back to the scoped flow.
     */
    private fun observeRoomFlows() {
        // Keep selectedRegions in state in sync with the persisted value.
        appDataStore.liveRegions
            .onEach { regions -> _state.update { it.copy(selectedRegions = regions) } }
            .launchIn(viewModelScope)

        // Single channel-list flow: regions × category × debounced query.
        val debouncedQuery = queryFlow.debounce(250)
        combine(appDataStore.liveRegions, categoryIdFlow, debouncedQuery) { regions, catId, q ->
            Triple(regions, catId, q)
        }
            .flatMapLatest { (regions, catId, q) ->
                if (q.isBlank()) {
                    val effectiveCatId = catId.takeIf { it != ALL_CATEGORY_ID }
                    repository.observeChannels(regions.toList(), effectiveCatId)
                } else {
                    // Global search — ignores region/category so every channel is reachable.
                    repository.searchChannels(q)
                }
            }
            .catch { e -> android.util.Log.e("LiveTvViewModel", "Channel flow error", e) }
            .onEach { channelEntities ->
                val channels = channelEntities.map { it.toDomain() }
                _state.update { s ->
                    s.copy(
                        isLoading = false,
                        hasSource = channels.isNotEmpty() || s.categories.isNotEmpty(),
                        channels = channels,
                    )
                }
            }
            .launchIn(viewModelScope)

        // Observe categories — reactive on region changes.
        appDataStore.liveRegions
            .flatMapLatest { regions ->
                repository.observeCategories(regions.toList())
            }
            .catch { e -> android.util.Log.e("LiveTvViewModel", "Category flow error", e) }
            .onEach { categoryEntities ->
                val categories = categoryEntities.map { it.toDomain() }
                _state.update { s ->
                    s.copy(
                        hasSource = s.channels.isNotEmpty() || categories.isNotEmpty(),
                        categories = listOf(ChannelCategory(ALL_CATEGORY_ID, "All")) + categories,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Phase 3: favourites + recently-watched ────────────────────────────────

    /** Collect favourite-channel, favourite-category, and recently-watched Flows into state. */
    private fun observeFavAndRecentFlows() {
        repository.observeFavChannels()
            .catch { e -> android.util.Log.e("LiveTvViewModel", "FavChannels flow error", e) }
            .onEach { entities ->
                val channels = entities.map { it.toDomain() }
                _state.update { it.copy(
                    favChannels = channels,
                    favChannelIds = channels.map { ch -> ch.id }.toSet(),
                ) }
            }
            .launchIn(viewModelScope)

        repository.observeFavCategories()
            .catch { e -> android.util.Log.e("LiveTvViewModel", "FavCategories flow error", e) }
            .onEach { entities ->
                _state.update { it.copy(favCategories = entities.map { it.toDomain() }) }
            }
            .launchIn(viewModelScope)

        repository.observeRecent(15)
            .catch { e -> android.util.Log.e("LiveTvViewModel", "Recent flow error", e) }
            .onEach { entities ->
                _state.update { it.copy(recent = entities.map { it.toDomain() }) }
            }
            .launchIn(viewModelScope)
    }

    /** Toggle the favourite state of a channel (persisted in Room). */
    fun toggleFavChannel(id: String) {
        viewModelScope.launch { repository.toggleFavouriteChannel(id) }
    }

    /** Toggle the favourite state of a category (persisted in Room). */
    fun toggleFavCategory(id: String) {
        viewModelScope.launch { repository.toggleFavouriteCategory(id) }
    }

    /**
     * Record that a channel has been played. Call this whenever a channel starts playback so it
     * appears in the recently-watched list.
     */
    fun onChannelPlayed(channelId: String) {
        viewModelScope.launch { repository.recordWatched(channelId) }
    }

    // ── Background refresh ────────────────────────────────────────────────────

    /**
     * Trigger a background network refresh. If Room already has fresh data (within TTL) this
     * is a no-op; otherwise channels/categories are updated and the Flows above push the new
     * data to the UI automatically.
     */
    private fun backgroundRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val fetched = repository.refresh(force = false)
            // If no network fetch occurred and Room is still empty, we have no source configured.
            if (!fetched && _state.value.channels.isEmpty()) {
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
        categoryIdFlow.value = id
        queryFlow.value = ""
        _state.update { it.copy(selectedCategoryId = id, query = "") }
    }
    // Region selection now lives in Settings (SettingsViewModel.setLiveRegions); the Live TV
    // screen only reacts to AppDataStore.liveRegions via observeRoomFlows.

    /**
     * Update the search query. The combined channel-list flow debounces [queryFlow] (250ms) and
     * switches between the global search and the region+category scoped flow automatically: a
     * non-blank query searches globally (ignoring region); a blank query restores the scoped list.
     */
    fun setQuery(q: String) {
        queryFlow.value = q
        _state.update { it.copy(query = q) }
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
}
