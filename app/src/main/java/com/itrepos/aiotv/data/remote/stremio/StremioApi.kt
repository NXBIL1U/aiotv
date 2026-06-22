package com.itrepos.aiotv.data.remote.stremio

import retrofit2.http.GET
import retrofit2.http.Url

interface StremioApi {
    @GET
    suspend fun getManifest(@Url url: String): StremioManifest

    @GET
    suspend fun getCatalog(@Url url: String): StremioCatalogResponse

    @GET
    suspend fun getMeta(@Url url: String): StremioMetaResponse

    @GET
    suspend fun getStreams(@Url url: String): StremioStreamResponse
}

fun catalogUrl(baseUrl: String, type: String, id: String) =
    "${baseUrl.trimEnd('/')}/catalog/$type/$id.json"

fun metaUrl(baseUrl: String, type: String, id: String) =
    "${baseUrl.trimEnd('/')}/meta/$type/$id.json"

fun streamUrl(baseUrl: String, type: String, id: String) =
    "${baseUrl.trimEnd('/')}/stream/$type/$id.json"

val CINEMETA_HOSTS = listOf("https://cinemeta-live.strem.io", "https://v3-cinemeta.strem.fun")

fun searchUrl(baseUrl: String, type: String, query: String): String {
    val q = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
    return "${baseUrl.trimEnd('/')}/catalog/$type/top/search=$q.json"
}

/** Tries each host's search URL; returns null iff NONE could be reached, else the first responder's metas. */
suspend fun fetchSearchFromHosts(
    hosts: List<String>,
    type: String,
    query: String,
    fetch: suspend (url: String) -> List<StremioMeta>,
): List<StremioMeta>? {
    for (host in hosts) {
        try {
            return fetch(searchUrl(host, type, query))
        } catch (_: Exception) {}
    }
    return null
}
