package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.local.WatchProgressStore
import com.itrepos.aiotv.data.repository.MetaRepository
import com.itrepos.aiotv.data.repository.TorBoxRepository
import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.playback.DeviceCapabilities
import com.itrepos.aiotv.domain.playback.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DirectPlay(val url: String, val title: String, val progressId: String)

/** Pure: which episode a "play series" action should start. */
object DirectPlaySequencing {
    fun resumeEpisode(episodes: List<Episode>, inProgressIds: Set<String>): Episode? =
        episodes.firstOrNull { it.id in inProgressIds } ?: episodes.firstOrNull()
}

/**
 * Resolve + auto-pick a playable source straight from a catalog [MediaItem] (no detail screen).
 * Movie -> auto-pick its best eligible source. Series -> resume/first episode's best source.
 * Returns null when nothing eligible resolves (all sources exhausted). Starts a [PlaybackController]
 * session so the Player follows it (failover / next-episode keep working).
 */
class PlayDirectUseCase @Inject constructor(
    private val metaRepo: MetaRepository,
    private val getStreams: GetStreamsUseCase,
    private val torBoxRepo: TorBoxRepository,
    private val appDataStore: AppDataStore,
    private val deviceCapabilities: DeviceCapabilities,
    private val playbackController: PlaybackController,
    private val watchProgressStore: WatchProgressStore,
) {
    suspend operator fun invoke(item: MediaItem): DirectPlay? {
        playbackController.clear()
        return if (item.type == "series") playSeries(item) else playMovie(item)
    }

    private suspend fun playMovie(item: MediaItem): DirectPlay? {
        val auto = rankAuto(getStreamsOrNull(item.type, item.id) ?: return null)
        if (auto.isEmpty()) return null
        if (!playbackController.startMovieAuto(auto, item.name, item.id)) return null
        return DirectPlay(playbackController.state.value!!.currentUrl, item.name, item.id)
    }

    private suspend fun playSeries(item: MediaItem): DirectPlay? {
        val series = metaRepo.getSeriesMeta(item.id) ?: return null
        val inProgress = runCatching { watchProgressStore.getAllProgress().first() }
            .getOrDefault(emptyList())
            .filter { it.fraction in 0.001f..0.999f }
            .map { it.id }.toSet()
        val ep = DirectPlaySequencing.resumeEpisode(series.episodes, inProgress) ?: return null
        val auto = rankAuto(getStreamsOrNull("series", ep.id) ?: return null)
        if (auto.isEmpty()) return null
        if (!playbackController.startSeries(series, ep, auto)) return null
        val title = "${series.item.name} S${ep.season}·E${ep.number}"
        return DirectPlay(playbackController.state.value!!.currentUrl, title, ep.id)
    }

    private suspend fun getStreamsOrNull(type: String, id: String): List<Stream>? = try {
        getStreams(type, id)
    } catch (c: CancellationException) {
        throw c
    } catch (_: Exception) {
        null
    }

    private suspend fun rankAuto(raw: List<Stream>): List<Stream> {
        val hashes = raw.mapNotNull { it.infoHash }
        val cached = if (hashes.isNotEmpty()) {
            runCatching { torBoxRepo.checkCached(hashes) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        val withCached = raw.map { s -> s.copy(isCached = s.isCached || cached[s.infoHash?.lowercase()] == true) }
        val pref = appDataStore.preferredQuality.first()
        val profile = deviceCapabilities.profile
        val target = if (pref.rank <= profile.maxResolution.rank) pref else profile.maxResolution
        return StreamRanker.rank(withCached, profile, target).filter { StreamRanker.isAutoEligible(it, profile) }
    }
}
