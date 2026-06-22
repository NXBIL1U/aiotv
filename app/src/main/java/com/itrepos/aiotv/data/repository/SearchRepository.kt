package com.itrepos.aiotv.data.repository

import com.itrepos.aiotv.data.remote.stremio.CINEMETA_HOSTS
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.fetchSearchFromHosts
import com.itrepos.aiotv.domain.model.MediaItem
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val stremioApi: StremioApi,
) {
    suspend fun search(query: String): List<MediaItem> {
        val movie = fetchSearchFromHosts(CINEMETA_HOSTS, "movie", query) { stremioApi.getCatalog(it).metas }
        val series = fetchSearchFromHosts(CINEMETA_HOSTS, "series", query) { stremioApi.getCatalog(it).metas }
        if (movie == null && series == null) throw IOException("Search unavailable")
        return ((movie ?: emptyList()) + (series ?: emptyList()))
            .map { it.toMediaItem() }
            .distinctBy { it.id }
    }

    private fun StremioMeta.toMediaItem() = MediaItem(
        id = id, type = type, name = name, description = description,
        posterUrl = poster, backdropUrl = background, year = year?.take(4)?.toIntOrNull(),
        genres = genres, imdbRating = imdbRating,
    )
}
