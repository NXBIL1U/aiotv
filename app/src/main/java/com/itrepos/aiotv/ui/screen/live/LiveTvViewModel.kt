package com.itrepos.aiotv.ui.screen.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.repository.IptvRepository
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.domain.model.EpgNowNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/** Sentinel id for the synthetic "All" category that shows every channel. */
const val ALL_CATEGORY_ID = "__all__"

/** Auto-retry attempts before falling to the error state (each attempt ~ the connect timeout). */
private const val MAX_LOAD_ATTEMPTS = 3
private const val RETRY_BACKOFF_MS = 2000L

data class LiveTvState(
    val isLoading: Boolean = true,
    val hasSource: Boolean = false,
    val categories: List<ChannelCategory> = emptyList(),
    val selectedCategoryId: String = ALL_CATEGORY_ID,
    val channels: List<Channel> = emptyList(),
    val query: String = "",
    val epg: Map<String, EpgNowNext> = emptyMap(),
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repo: IptvRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    private var allChannels: List<Channel> = emptyList()
    private val epgSemaphore = Semaphore(4)
    private val epgInFlight = mutableSetOf<String>()
    private var queryJob: Job? = null

    private var loadJob: Job? = null

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val hasSource = repo.hasIptvSource()
            if (!hasSource) {
                // Nothing to fetch — show the "add a source" state at once; don't retry.
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasSource = false,
                        channels = emptyList(),
                        categories = listOf(ChannelCategory(ALL_CATEGORY_ID, "All")),
                    )
                }
                return@launch
            }
            // Keep trying for a few attempts before showing the error state. isLoading stays
            // true across attempts so the UI can show an escalating "still loading…" message;
            // OkHttp owns each attempt's ~30s connect timeout (fastFallback races the IPs).
            var attempt = 0
            while (true) {
                attempt++
                repo.clearCache()
                val channelsDeferred = async { repo.getChannels() }
                val catsDeferred = async { repo.getCategories() }
                val channels = channelsDeferred.await()
                val cats = catsDeferred.await()
                if (channels.isNotEmpty()) {
                    allChannels = channels
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasSource = true,
                            categories = listOf(ChannelCategory(ALL_CATEGORY_ID, "All")) + cats,
                            channels = channels,
                            selectedCategoryId = ALL_CATEGORY_ID,
                            query = "",
                        )
                    }
                    return@launch
                }
                if (attempt >= MAX_LOAD_ATTEMPTS) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasSource = true,
                            channels = emptyList(),
                            categories = listOf(ChannelCategory(ALL_CATEGORY_ID, "All")),
                        )
                    }
                    return@launch
                }
                delay(RETRY_BACKOFF_MS)
            }
        }
    }

    /** Re-attempt loading (manual Retry, or after the provider was unreachable). */
    fun retry() = load()

    fun selectCategory(id: String) {
        val filtered = filterByCategory(id)
        _state.update { it.copy(selectedCategoryId = id, query = "", channels = filtered) }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(250)
            val result = if (q.isBlank()) {
                filterByCategory(_state.value.selectedCategoryId)
            } else {
                // Search spans every channel, ignoring the selected category.
                allChannels.filter { it.name.contains(q, ignoreCase = true) }
            }
            _state.update { it.copy(channels = result) }
        }
    }

    /** Lazily fetch now/next for a channel as its row first composes (≈ becomes visible). */
    fun onChannelVisible(channel: Channel) {
        if (_state.value.epg.containsKey(channel.id)) return
        synchronized(epgInFlight) {
            if (!epgInFlight.add(channel.id)) return
        }
        viewModelScope.launch {
            try {
                // Record every attempt — including the empty/failed result (null → no-EPG
                // sentinel) — so the containsKey guard above stops a re-fetch flood when the
                // user scrolls a channel off-screen and back.
                val nowNext = epgSemaphore.withPermit { repo.getShortEpg(channel) }
                    ?: EpgNowNext(null, null)
                _state.update { it.copy(epg = it.epg + (channel.id to nowNext)) }
            } finally {
                synchronized(epgInFlight) { epgInFlight.remove(channel.id) }
            }
        }
    }

    private fun filterByCategory(id: String): List<Channel> =
        if (id == ALL_CATEGORY_ID) allChannels
        else allChannels.filter { it.categoryKey == id }
}
