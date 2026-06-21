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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveTvRepository,
    private val appDataStore: AppDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    private val epgSemaphore = Semaphore(4)
    private val epgInFlight = mutableSetOf<String>()
    private var queryJob: Job? = null

    init {
        observeRoomFlows()
        observeFavAndRecentFlows()
        backgroundRefresh()
    }

    /**
     * Subscribe to Room Flows for channels and categories.
     *
     * - The channel + category flows switch whenever the persisted [AppDataStore.liveRegions]
     *   changes (via [flatMapLatest]).
     * - Within each region set, a secondary combine re-queries when [selectedCategoryId] changes.
     * - When [query] is non-blank, the channel list is replaced by a global search result that
     *   ignores region and category — so every channel remains reachable.
     */
    private fun observeRoomFlows() {
        // Internal flow tracking the current category selection (so we can switch it reactively).
        val categoryIdFlow = MutableStateFlow(ALL_CATEGORY_ID)

        // Observe channels: switch the Room Flow whenever regions or category changes.
        appDataStore.liveRegions
            .flatMapLatest { regions ->
                _state.update { it.copy(selectedRegions = regions) }
                val regionList = regions.toList()
                categoryIdFlow.flatMapLatest { catId ->
                    val effectiveCatId = if (catId == ALL_CATEGORY_ID) null else catId
                    repository.observeChannels(regionList, effectiveCatId)
                }
            }
            .catch { e -> android.util.Log.e("LiveTvViewModel", "Channel flow error", e) }
            .onEach { channelEntities ->
                val channels = channelEntities.map { it.toDomain() }
                val currentQuery = _state.value.query
                val displayedChannels = if (currentQuery.isBlank()) channels else {
                    // Query is active: keep showing the in-flight search result unchanged;
                    // the setQuery debounce will re-issue a search when the user types.
                    _state.value.channels
                }
                _state.update { s ->
                    s.copy(
                        isLoading = false,
                        hasSource = channels.isNotEmpty() || s.categories.isNotEmpty(),
                        channels = displayedChannels,
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

        // Wire categoryIdFlow so selectCategory() triggers a new channel query.
        _state
            .onEach { s -> categoryIdFlow.value = s.selectedCategoryId }
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
        _state.update { it.copy(selectedCategoryId = id, query = "") }
    }

    /**
     * Persist the user's region selection. The [appDataStore.liveRegions] Flow will emit the
     * new value and the [flatMapLatest] in [observeRoomFlows] will automatically re-query Room.
     */
    fun setRegions(regions: Set<String>) {
        viewModelScope.launch {
            appDataStore.setLiveRegions(regions)
        }
    }

    /**
     * Debounced search. When [q] is non-blank the global Room search is used (ignores region);
     * when blank, the current region+category scoped Flow takes over again.
     */
    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(250)
            if (q.isBlank()) {
                // Blank query: let the region+category Flow supply the channel list.
                // Trigger a re-emit by nudging the category selection state.
                val catId = _state.value.selectedCategoryId
                _state.update { it.copy(selectedCategoryId = catId) }
            } else {
                // Non-blank: global search — ignores region so every channel is reachable.
                repository.searchChannels(q)
                    .catch { e -> android.util.Log.e("LiveTvViewModel", "Search flow error", e) }
                    .onEach { entities ->
                        _state.update { it.copy(channels = entities.map { e -> e.toDomain() }) }
                    }
                    .launchIn(viewModelScope)
            }
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
}
