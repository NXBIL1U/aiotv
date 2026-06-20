package com.itrepos.aiotv.data.repository

import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.stremio.StremioManifest
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.StremioStream
import com.itrepos.aiotv.data.remote.stremio.catalogUrl
import com.itrepos.aiotv.data.remote.stremio.metaUrl
import com.itrepos.aiotv.data.remote.stremio.streamUrl
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.Stream
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StremioRepository @Inject constructor(
    private val stremioApi: StremioApi,
    private val appDataStore: AppDataStore,
) {
    private val manifestCache = mutableMapOf<String, StremioManifest>()

    private suspend fun getManifests(): List<Pair<String, StremioManifest>> {
        val urls = appDataStore.addonUrls.first()
        return urls.map { url ->
            val manifest = manifestCache.getOrPut(url) {
                stremioApi.getManifest(url)
            }
            val baseUrl = url.removeSuffix("/manifest.json")
            baseUrl to manifest
        }
    }

    suspend fun getCatalog(type: String = "movie"): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        getManifests().forEach { (baseUrl, manifest) ->
            manifest.catalogs
                .filter { it.type == type }
                .forEach { cat ->
                    try {
                        val resp = stremioApi.getCatalog(catalogUrl(baseUrl, type, cat.id))
                        items += resp.metas.map { it.toMediaItem() }
                    } catch (_: Exception) {}
                }
        }
        return items
    }

    suspend fun getMeta(type: String, id: String): StremioMeta? {
        getManifests().forEach { (baseUrl, _) ->
            try {
                return stremioApi.getMeta(metaUrl(baseUrl, type, id)).meta
            } catch (_: Exception) {}
        }
        return null
    }

    suspend fun getStreams(type: String, id: String): List<Stream> {
        val streams = mutableListOf<Stream>()
        getManifests().forEach { (baseUrl, _) ->
            try {
                val resp = stremioApi.getStreams(streamUrl(baseUrl, type, id))
                streams += resp.streams.map { it.toStream() }
            } catch (_: Exception) {}
        }
        return streams
    }

    fun clearCache() { manifestCache.clear() }

    private fun StremioMeta.toMediaItem() = MediaItem(
        id = id, type = type, name = name, description = description,
        posterUrl = poster, backdropUrl = background, year = year,
        genres = genres, imdbRating = imdbRating,
    )

    private fun StremioStream.toStream() = Stream(
        title = title ?: name,
        url = url,
        infoHash = infoHash,
        fileIdx = fileIdx,
        behaviorHints = behaviorHints?.let { com.itrepos.aiotv.domain.model.BehaviorHints(it.bingeGroup, it.filename) },
    )
}
