package com.itrepos.aiotv.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.repository.MetaRepository
import com.itrepos.aiotv.data.repository.SeriesMeta
import com.itrepos.aiotv.data.repository.StremioRepository
import com.itrepos.aiotv.data.repository.TorBoxRepository
import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.playback.DeviceCapabilities
import com.itrepos.aiotv.domain.playback.PlaybackController
import com.itrepos.aiotv.domain.usecase.GetStreamsUseCase
import com.itrepos.aiotv.domain.usecase.ResolveStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DetailKind { MOVIE, SERIES }

data class DetailState(
    val isLoading: Boolean = true,
    val kind: DetailKind = DetailKind.MOVIE,
    // Movie path
    val meta: MediaItem? = null,
    val streams: List<Stream> = emptyList(),
    val resolvedUrl: String? = null,
    val resolving: Boolean = false,
    val showMovieSources: Boolean = false,
    // Series path
    val series: SeriesMeta? = null,
    val selectedSeason: Int? = null,
    val episodeStreams: List<Stream> = emptyList(),
    val sourcesForEpisode: Episode? = null,
    val resolvingEpisode: Episode? = null,
    // Shared
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val stremioRepo: StremioRepository,
    private val metaRepo: MetaRepository,
    private val getStreams: GetStreamsUseCase,
    private val torBoxRepo: TorBoxRepository,
    private val resolveStream: ResolveStreamUseCase,
    private val appDataStore: AppDataStore,
    private val playbackController: PlaybackController,
    private val deviceCapabilities: DeviceCapabilities,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    /**
     * Device-aware ranking. Returns the FULL ranked list (for the manual Sources view) and the
     * auto-eligible subset (decodable + within screen/decode res + <= 20 GB) for auto-pick.
     */
    private suspend fun rankFor(raw: List<Stream>): Pair<List<Stream>, List<Stream>> {
        val pref = appDataStore.preferredQuality.first()
        val profile = deviceCapabilities.profile
        val target = if (pref.rank <= profile.maxResolution.rank) pref else profile.maxResolution
        val ranked = StreamRanker.rank(raw, profile, target)
        val auto = ranked.filter { StreamRanker.isAutoEligible(it, profile) }
        return ranked to auto
    }

    fun load(type: String, id: String) {
        viewModelScope.launch {
            _state.value = DetailState(isLoading = true)
            if (type == "series") {
                loadSeries(id)
            } else {
                loadMovie(type, id)
            }
        }
    }

    private suspend fun loadSeries(id: String) {
        val series = metaRepo.getSeriesMeta(id)
        if (series == null) {
            // Cinemeta down / no videos: show the title (id) with an "unavailable" state, don't crash.
            _state.value = DetailState(
                isLoading = false,
                kind = DetailKind.SERIES,
                series = null,
                error = "Episodes unavailable — try again later",
            )
            return
        }
        _state.value = DetailState(
            isLoading = false,
            kind = DetailKind.SERIES,
            series = series,
            selectedSeason = series.seasons.firstOrNull(),
            meta = series.item,
        )
    }

    private suspend fun loadMovie(type: String, id: String) {
        try {
            val meta = metaRepo.getMovieMeta(type, id)
            val streams = getStreams(type, id)
            val hashes = streams.mapNotNull { it.infoHash }
            val cached = if (hashes.isNotEmpty()) torBoxRepo.checkCached(hashes) else emptyMap()
            val withCached = streams.map { s -> s.copy(isCached = s.isCached || cached[s.infoHash?.lowercase()] == true) }
            val (ranked, _) = rankFor(withCached)
            _state.value = DetailState(
                isLoading = false,
                kind = DetailKind.MOVIE,
                meta = meta,
                streams = ranked,
            )
        } catch (e: Exception) {
            _state.value = DetailState(isLoading = false, kind = DetailKind.MOVIE, error = e.message)
        }
    }

    fun selectSeason(season: Int) {
        _state.value = _state.value.copy(selectedSeason = season)
    }

    /**
     * Tap-to-play: rank this episode's sources (cached first), then resolve candidates in order,
     * auto-advancing past any that fail or time out. On success, plays with an episode-keyed
     * progressId so resume is per-episode. If nothing resolves, surfaces the Sources sheet.
     */
    fun playEpisode(episode: Episode, onPlay: (url: String, title: String, progressId: String) -> Unit) {
        if (_state.value.resolvingEpisode != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(resolvingEpisode = episode, error = null, sourcesForEpisode = null)
            val raw = try {
                getStreams("series", episode.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(resolvingEpisode = null, error = e.message)
                return@launch
            }
            val hashes = raw.mapNotNull { it.infoHash }
            val cached = if (hashes.isNotEmpty()) {
                runCatching { torBoxRepo.checkCached(hashes) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            val withCached = raw.map { s -> s.copy(isCached = s.isCached || cached[s.infoHash?.lowercase()] == true) }
            val (ranked, auto) = rankFor(withCached)
            _state.value = _state.value.copy(episodeStreams = ranked)

            // Start a controller session from the auto-eligible subset: it resolves the best
            // candidate and holds the list so the Player can silently fail over / advance.
            val series = _state.value.series
            val started = series != null && auto.isNotEmpty() && playbackController.startSeries(series, episode, auto)
            if (started) {
                _state.value = _state.value.copy(resolvingEpisode = null)
                val session = playbackController.state.value!!
                onPlay(session.currentUrl, episodeTitle(episode), episode.id)
            } else {
                // Nothing resolved automatically — fall back to a manual pick.
                // Clear any stale session so the Sources sheet starts clean (no lingering controller
                // state from a prior play on the same singleton).
                playbackController.clear()
                _state.value = _state.value.copy(resolvingEpisode = null, sourcesForEpisode = episode)
            }
        }
    }

    /**
     * Movie Play button: auto-pick the best device-eligible source and play it (mirrors the series
     * [playEpisode] auto path). If nothing is eligible/resolves, the full sources list stays on
     * screen for a manual pick. Movie progressId = the stable movie id (resume keys to the movie).
     */
    fun playMovieAuto(onPlay: (url: String, title: String, progressId: String) -> Unit) {
        if (_state.value.resolving) return
        val movieId = _state.value.meta?.id ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(resolving = true, error = null)
            val (_, auto) = rankFor(_state.value.streams)
            playbackController.clear()
            val title = _state.value.meta?.name ?: movieId
            val started = auto.isNotEmpty() && playbackController.startMovieAuto(auto, title, movieId)
            _state.value = _state.value.copy(resolving = false)
            if (started) {
                onPlay(playbackController.state.value!!.currentUrl, title, movieId)
            } else {
                // No eligible source resolved — the full sources list stays visible for a manual pick.
                _state.value = _state.value.copy(error = "No streamable source — pick one below")
            }
        }
    }

    /** Movie Sources sheet (manual override / auto-pick-fail fallback). */
    fun showMovieSources() { _state.value = _state.value.copy(showMovieSources = true) }
    fun dismissMovieSources() { _state.value = _state.value.copy(showMovieSources = false) }

    /** Open the Sources sheet for manual selection. */
    fun showSources(episode: Episode) {
        _state.value = _state.value.copy(sourcesForEpisode = episode)
    }

    fun dismissSources() {
        _state.value = _state.value.copy(sourcesForEpisode = null)
    }

    /** Manual pick from the Sources sheet — uses the longer poll budget (no auto-advance timeout). */
    fun playSpecificStream(
        stream: Stream,
        episode: Episode,
        onPlay: (url: String, title: String, progressId: String) -> Unit,
    ) {
        if (_state.value.resolvingEpisode != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(resolvingEpisode = episode, sourcesForEpisode = null, error = null)
            // Clear any stale session so the Player's session-latch binds to the URL the user
            // actually chose, not to whatever the singleton held from a previous play.
            playbackController.clear()
            val url = resolveStream(stream).getOrNull()
            _state.value = _state.value.copy(resolvingEpisode = null)
            if (url != null) {
                onPlay(url, episodeTitle(episode), episode.id)
            } else {
                _state.value = _state.value.copy(error = "Failed to play this source", sourcesForEpisode = episode)
            }
        }
    }

    /**
     * Movie-path resolve. Behaviour preserved (manual source pick via [ResolveStreamUseCase]), but
     * now also starts a [PlaybackController] movie session so movies get mid-play failover too.
     * The movie progressId is the resolved url (mirrors DetailScreen's `onPlayStream(url, title, url)`).
     */
    fun resolveStream(stream: Stream, onResolved: (String) -> Unit) {
        if (_state.value.resolving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(resolving = true, error = null)
            val result = resolveStream(stream)
            result.fold(
                onSuccess = { url ->
                    _state.value = _state.value.copy(resolving = false, resolvedUrl = url)
                    val streams = _state.value.streams
                    val title = stream.title ?: _state.value.meta?.name ?: "Movie"
                    playbackController.startMovie(
                        candidates = streams,
                        chosenIndex = streams.indexOf(stream),
                        chosenUrl = url,
                        title = title,
                        progressId = url,
                    )
                    onResolved(url)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        resolving = false,
                        error = e.message ?: "Failed to resolve stream",
                    )
                },
            )
        }
    }

    private fun episodeTitle(episode: Episode): String {
        val show = _state.value.series?.item?.name ?: _state.value.meta?.name ?: "Episode"
        return "$show S${episode.season}·E${episode.number}"
    }
}
