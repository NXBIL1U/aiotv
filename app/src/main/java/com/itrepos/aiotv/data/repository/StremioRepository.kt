package com.itrepos.aiotv.data.repository

import android.util.Log
import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.stremio.StremioManifest
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.StremioStream
import com.itrepos.aiotv.data.remote.stremio.catalogUrl
import com.itrepos.aiotv.data.remote.stremio.metaUrl
import com.itrepos.aiotv.data.remote.stremio.searchUrl
import com.itrepos.aiotv.data.remote.stremio.streamUrl
import com.itrepos.aiotv.domain.model.ContentSection
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
        var urls = appDataStore.addonUrls.first()
        // Always ensure Cinemeta is present — it's the reliable fallback for movies + series
        val cinemeta = "https://v3-cinemeta.strem.io/manifest.json"
        if (cinemeta !in urls) {
            appDataStore.addAddonUrl(cinemeta)
            urls = urls + cinemeta
        }
        if (urls.size == 1) {
            // Only Cinemeta — also seed the Netflix catalog addon
            val netflix = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibSxjcnUsaXR2LGJiYyxhbDQ6OkdCOjE3ODIwMDk1MzQ0NDU6MDowOkdC/manifest.json"
            appDataStore.addAddonUrl(netflix)
            urls = urls + netflix
        }
        return urls.mapNotNull { url ->
            try {
                val manifest = manifestCache.getOrPut(url) {
                    stremioApi.getManifest(url)
                }
                val baseUrl = url.removeSuffix("/manifest.json")
                baseUrl to manifest
            } catch (_: Exception) { null }
        }
    }

    suspend fun getCatalog(type: String = "movie"): List<MediaItem> =
        getCatalogSections(type).flatMap { it.items }

    suspend fun getCatalogSections(type: String = "movie"): List<ContentSection> {
        val sections = mutableListOf<ContentSection>()
        getManifests().forEach { (baseUrl, manifest) ->
            Log.d("StremioRepo", "Processing addon ${manifest.name}, catalogs=${manifest.catalogs.size}, type filter=$type")
            manifest.catalogs
                .filter { it.type == type }
                .forEach { cat ->
                    Log.d("StremioRepo", "Loading catalog ${cat.id} (${cat.name}) type=${cat.type} supportsSkip=${cat.supportsSkip}")
                    try {
                        // Use skip=0 only when the catalog declares skip support — some addons
                        // reject the extra-param path format and return 404 otherwise
                        val url = catalogUrl(baseUrl, type, cat.id, skipParam = cat.supportsSkip)
                        Log.d("StremioRepo", "Fetching: $url")
                        var resp = stremioApi.getCatalog(url)
                        // Some addons require skip=0 even when not declared — retry with it
                        if (resp.metas.isEmpty() && !cat.supportsSkip) {
                            val retryUrl = catalogUrl(baseUrl, type, cat.id, skipParam = true)
                            Log.d("StremioRepo", "Empty, retrying with skip=0: $retryUrl")
                            resp = runCatching { stremioApi.getCatalog(retryUrl) }.getOrDefault(resp)
                        }
                        val items = resp.metas.map { it.toMediaItem() }
                        Log.d("StremioRepo", "Got ${items.size} items for ${cat.id}")
                        if (items.isNotEmpty()) {
                            sections += ContentSection(
                                title = cat.name ?: manifest.name,
                                items = items,
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("StremioRepo", "Catalog ${cat.id} failed: ${e.message}")
                    }
                }
        }
        Log.d("StremioRepo", "getCatalogSections($type) → ${sections.size} sections")
        return sections
    }

    suspend fun search(query: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        getManifests().forEach { (baseUrl, manifest) ->
            listOf("movie", "series").forEach { type ->
                manifest.catalogs
                    .filter { it.type == type && it.supportsSearch }
                    .forEach { cat ->
                        try {
                            val resp = stremioApi.getCatalog(searchUrl(baseUrl, type, cat.id, query))
                            items += resp.metas.map { it.toMediaItem() }
                        } catch (_: Exception) {}
                    }
            }
        }
        // Fall back to local filter across the full catalog if no addon supports search
        if (items.isEmpty()) {
            listOf("movie", "series").forEach { type ->
                items += getCatalog(type).filter { it.name.contains(query, ignoreCase = true) }
            }
        }
        return items.distinctBy { it.id }
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
