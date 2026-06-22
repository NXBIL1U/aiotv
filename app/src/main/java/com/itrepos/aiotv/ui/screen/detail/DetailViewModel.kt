package com.itrepos.aiotv.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.remote.stremio.StremioVideo
import com.itrepos.aiotv.data.repository.StremioRepository
import com.itrepos.aiotv.data.repository.TorBoxRepository
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.usecase.GetStreamsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val isLoading: Boolean = true,
    val meta: MediaItem? = null,
    val type: String = "",
    // Series episode data
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int? = null,
    val episodesInSeason: List<StremioVideo> = emptyList(),
    val selectedEpisode: StremioVideo? = null,
    // Streams for selected episode (or movie)
    val streams: List<Stream> = emptyList(),
    val loadingStreams: Boolean = false,
    val resolvedUrl: String? = null,
    val error: String? = null,
    val resolving: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val stremioRepo: StremioRepository,
    private val getStreams: GetStreamsUseCase,
    private val torBoxRepo: TorBoxRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    // Raw episodes from meta, keyed by season
    private var episodesBySeason: Map<Int, List<StremioVideo>> = emptyMap()
    private var currentType: String = ""
    private var currentId: String = ""

    fun load(type: String, id: String) {
        currentType = type
        currentId = id
        viewModelScope.launch {
            _state.value = DetailState(isLoading = true, type = type)
            try {
                val meta = stremioRepo.getMeta(type, id)
                val mediaItem = meta?.let { m ->
                    MediaItem(m.id, m.type, m.name, m.description, m.poster, m.background, m.year, m.genres, m.imdbRating)
                }

                if (type == "series" && meta != null && meta.videos.isNotEmpty()) {
                    // Group episodes by season
                    episodesBySeason = meta.videos
                        .filter { it.season != null && it.season > 0 }
                        .groupBy { it.season!! }
                        .toSortedMap()
                    val seasons = episodesBySeason.keys.toList()
                    val firstSeason = seasons.firstOrNull()
                    val firstEpisodes = firstSeason?.let { episodesBySeason[it] } ?: emptyList()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        meta = mediaItem,
                        seasons = seasons,
                        selectedSeason = firstSeason,
                        episodesInSeason = firstEpisodes,
                        selectedEpisode = null,
                        streams = emptyList(),
                    )
                } else {
                    // Movie or series with no video list — load streams directly
                    _state.value = _state.value.copy(
                        isLoading = false,
                        meta = mediaItem,
                        loadingStreams = true,
                    )
                    loadStreams(type, id)
                }
            } catch (e: Exception) {
                _state.value = DetailState(isLoading = false, error = e.message)
            }
        }
    }

    fun selectSeason(season: Int) {
        val episodes = episodesBySeason[season] ?: emptyList()
        _state.value = _state.value.copy(
            selectedSeason = season,
            episodesInSeason = episodes,
            selectedEpisode = null,
            streams = emptyList(),
            error = null,
        )
    }

    fun selectEpisode(episode: StremioVideo) {
        _state.value = _state.value.copy(
            selectedEpisode = episode,
            streams = emptyList(),
            loadingStreams = true,
            error = null,
        )
        viewModelScope.launch {
            loadStreams(currentType, episode.id)
        }
    }

    private suspend fun loadStreams(type: String, id: String) {
        try {
            val streams = getStreams(type, id)
            val hashes = streams.mapNotNull { it.infoHash }
            val cached = if (hashes.isNotEmpty()) torBoxRepo.checkCached(hashes) else emptyMap()
            val ranked = streams.map { s ->
                s.copy(isCached = cached[s.infoHash?.lowercase()] == true)
            }.sortedByDescending { if (it.isCached) 1 else 0 }
            _state.value = _state.value.copy(streams = ranked, loadingStreams = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                loadingStreams = false,
                error = e.message ?: "Failed to load streams",
            )
        }
    }

    fun resolveStream(stream: Stream, onResolved: (String) -> Unit) {
        if (_state.value.resolving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(resolving = true, error = null)
            try {
                val url = stream.url
                    ?: run {
                        val infoHash = stream.infoHash
                            ?: throw IllegalStateException("Stream has no URL or info hash")
                        val torrentId = torBoxRepo.createTorrent("magnet:?xt=urn:btih:$infoHash")
                            ?: throw IllegalStateException("Failed to create torrent")
                        val info = torBoxRepo.pollUntilReady(torrentId)
                            ?: throw IllegalStateException("Torrent did not become ready")
                        val fileId = info.files.firstOrNull()?.id
                            ?: throw IllegalStateException("Torrent has no playable files")
                        torBoxRepo.getDownloadUrl(torrentId, fileId)
                    }
                _state.value = _state.value.copy(resolving = false, resolvedUrl = url)
                onResolved(url)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    resolving = false,
                    error = e.message ?: "Failed to resolve stream",
                )
            }
        }
    }
}
