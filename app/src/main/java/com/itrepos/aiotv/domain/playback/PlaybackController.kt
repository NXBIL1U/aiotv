package com.itrepos.aiotv.domain.playback

import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.repository.SeriesMeta
import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.usecase.GetStreamsUseCase
import com.itrepos.aiotv.domain.usecase.ResolveStreamUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Now-playing session snapshot the Player observes. [currentUrl] is the source actually playing;
 * [upNext] is the next episode (series only, null for movies/last episode); [isFailingOver] is a
 * brief transient flag while we swap to another source for the SAME item.
 */
data class PlaybackState(
    val currentUrl: String,
    val title: String,
    val progressId: String,
    val upNext: Episode?,
    val isFailingOver: Boolean = false,
)

/**
 * The now-playing session orchestrator. Holds the ranked candidate list, the current source index,
 * and (for series) the [SeriesMeta] + current episode so the Player can silently fail over to the
 * next source on a mid-play error, or advance to the next episode at end-of-episode.
 *
 * Live IPTV never starts a session, so the Player falls back to its route `url` (no failover/next).
 * All resolution rethrows [CancellationException] (codebase convention).
 */
@Singleton
class PlaybackController @Inject constructor(
    private val getStreams: GetStreamsUseCase,
    private val resolveStream: ResolveStreamUseCase,
    private val appDataStore: AppDataStore,
) {
    private val _state = MutableStateFlow<PlaybackState?>(null)
    val state: StateFlow<PlaybackState?> = _state.asStateFlow()

    private var candidates: List<Stream> = emptyList()
    private var sourceIndex = 0
    private var series: SeriesMeta? = null
    private var episode: Episode? = null
    private var bingeGroup: String? = null

    /** Resolve the best of [candidates] and start a series session. Returns false if nothing resolves. */
    suspend fun startSeries(series: SeriesMeta, episode: Episode, candidates: List<Stream>): Boolean {
        val picked = resolveFrom(candidates, 0) ?: return false
        this.series = series
        this.episode = episode
        this.candidates = candidates
        this.sourceIndex = picked.second
        this.bingeGroup = candidates[picked.second].bingeGroup
        _state.value = PlaybackState(
            currentUrl = picked.first,
            title = titleOf(series, episode),
            progressId = episode.id,
            upNext = BingeSequencing.nextEpisode(series.episodes, episode.id),
        )
        return true
    }

    /** Start a movie session from an already-resolved [chosenUrl] (the source the user tapped). */
    fun startMovie(
        candidates: List<Stream>,
        chosenIndex: Int,
        chosenUrl: String,
        title: String,
        progressId: String,
    ) {
        this.series = null
        this.episode = null
        this.candidates = candidates
        this.sourceIndex = chosenIndex.coerceAtLeast(0)
        this.bingeGroup = candidates.getOrNull(sourceIndex)?.bingeGroup
        _state.value = PlaybackState(chosenUrl, title, progressId, upNext = null)
    }

    fun hasSessionFor(progressId: String) = _state.value?.progressId == progressId

    /** Mid-play failover: try the next candidate source for the SAME item. false when exhausted. */
    suspend fun failover(): Boolean {
        val s = _state.value ?: return false
        _state.value = s.copy(isFailingOver = true)
        val picked = resolveFrom(candidates, sourceIndex + 1)
        return if (picked != null) {
            sourceIndex = picked.second
            bingeGroup = candidates[picked.second].bingeGroup
            _state.value = s.copy(currentUrl = picked.first, isFailingOver = false)
            true
        } else {
            _state.value = s.copy(isFailingOver = false)
            false
        }
    }

    /** Resolve + start the next episode. false if no next episode or nothing resolves. */
    suspend fun advanceToNextEpisode(): Boolean {
        val ser = series ?: return false
        val cur = episode ?: return false
        val next = BingeSequencing.nextEpisode(ser.episodes, cur.id) ?: return false
        val raw = try {
            getStreams("series", next.id)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            return false
        }
        val pref = appDataStore.preferredQuality.first()
        // rank, then float same-bingeGroup candidates to the front (stable sort preserves rank within groups)
        val ranked = StreamRanker.rank(raw, pref)
            .sortedByDescending { BingeSequencing.isBingeMatch(it.bingeGroup, bingeGroup) }
        val picked = resolveFrom(ranked, 0) ?: return false
        this.episode = next
        this.candidates = ranked
        this.sourceIndex = picked.second
        this.bingeGroup = ranked[picked.second].bingeGroup
        _state.value = PlaybackState(
            currentUrl = picked.first,
            title = titleOf(ser, next),
            progressId = next.id,
            upNext = BingeSequencing.nextEpisode(ser.episodes, next.id),
        )
        return true
    }

    fun clear() {
        _state.value = null
        candidates = emptyList()
        series = null
        episode = null
        sourceIndex = 0
        bingeGroup = null
    }

    /** Resolve candidates from [start] onward; returns (url, index) of the first that resolves. */
    private suspend fun resolveFrom(list: List<Stream>, start: Int): Pair<String, Int>? {
        var i = start
        while (i < list.size) {
            val r = if (list[i].url != null) {
                resolveStream(list[i])
            } else {
                withTimeoutOrNull(20_000) { resolveStream(list[i]) }
            }
            val url = r?.getOrNull()
            if (url != null) return url to i
            i++
        }
        return null
    }

    private fun titleOf(s: SeriesMeta, e: Episode) = "${s.item.name} S${e.season}·E${e.number}"
}
