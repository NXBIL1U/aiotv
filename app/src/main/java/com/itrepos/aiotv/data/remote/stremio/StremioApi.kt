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
