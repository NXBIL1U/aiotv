package com.itrepos.aiotv.data.repository

import com.itrepos.aiotv.data.remote.stremio.CINEMETA_HOSTS
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.metaUrl
import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

data class SeriesMeta(
    val item: MediaItem,
    val seasons: List<Int>,
    val episodes: List<Episode>,
) {
    fun episodesIn(season: Int) = episodes.filter { it.season == season }
}

fun StremioMeta.toSeriesMeta(): SeriesMeta {
    val eps = videos
        .filter { it.season != null && it.episode != null }
        .map { v ->
            Episode(
                id = v.id,
                season = v.season!!,
                number = v.episode!!,
                name = v.name ?: v.title ?: "Episode ${v.episode}",
                overview = v.overview,
                thumbnail = v.thumbnail,
                released = v.released,
            )
        }
        .sortedWith(compareBy({ it.season }, { it.number }))

    // seasons ascending, but specials (season 0) moved to the end
    val seasons = eps.map { it.season }.distinct().sortedWith(
        compareBy({ if (it == 0) 1 else 0 }, { it })
    )

    val item = MediaItem(
        id = id,
        type = type,
        name = name,
        description = description,
        posterUrl = poster,
        backdropUrl = background,
        year = year?.take(4)?.toIntOrNull(),
        genres = genres,
        imdbRating = imdbRating,
    )
    return SeriesMeta(item, seasons, eps)
}

/**
 * Tries each host in order, calling [fetch] with the fully-resolved meta URL.
 * Returns the first non-empty [SeriesMeta], or null if all hosts fail or return
 * empty video lists. Extracted as a top-level function so it can be unit-tested
 * with a fake [fetch] lambda without needing to fake any concrete class.
 */
suspend fun fetchFromHosts(
    hosts: List<String>,
    id: String,
    fetch: suspend (url: String) -> StremioMeta?,
): SeriesMeta? {
    for (host in hosts) {
        try {
            val meta = fetch(metaUrl(host, "series", id)) ?: continue
            if (meta.videos.isNotEmpty()) return meta.toSeriesMeta()
        } catch (_: Exception) {
            // try next host
        }
    }
    return null
}

@Singleton
class MetaRepository @Inject constructor(
    private val stremioApi: StremioApi,
    private val stremioRepository: StremioRepository,
) {
    suspend fun getSeriesMeta(id: String): SeriesMeta? {
        // Try Cinemeta hosts first
        val cinemeta = fetchFromHosts(CINEMETA_HOSTS, id) { url ->
            stremioApi.getMeta(url).meta
        }
        if (cinemeta != null) return cinemeta

        // Last resort: any installed addon that returns series meta with videos
        return try {
            stremioRepository.getMeta("series", id)
                ?.takeIf { it.videos.isNotEmpty() }
                ?.toSeriesMeta()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getMovieMeta(type: String, id: String): MediaItem? =
        try {
            stremioRepository.getMeta(type, id)?.let { m ->
                MediaItem(
                    id = m.id,
                    type = m.type,
                    name = m.name,
                    description = m.description,
                    posterUrl = m.poster,
                    backdropUrl = m.background,
                    year = m.year?.take(4)?.toIntOrNull(),
                    genres = m.genres,
                    imdbRating = m.imdbRating,
                )
            }
        } catch (_: Exception) {
            null
        }
}
