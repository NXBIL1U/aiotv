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

// skipParam=true appends /skip=0 as an extra — required by some addons (e.g. Netflix catalog)
// but breaks others that don't support extra params
fun catalogUrl(baseUrl: String, type: String, id: String, skipParam: Boolean = false) =
    if (skipParam) "${baseUrl.trimEnd('/')}/catalog/$type/$id/skip=0.json"
    else "${baseUrl.trimEnd('/')}/catalog/$type/$id.json"

fun searchUrl(baseUrl: String, type: String, id: String, query: String): String {
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    return "${baseUrl.trimEnd('/')}/catalog/$type/$id/search=$encoded.json"
}

fun metaUrl(baseUrl: String, type: String, id: String) =
    "${baseUrl.trimEnd('/')}/meta/$type/$id.json"

fun streamUrl(baseUrl: String, type: String, id: String) =
    "${baseUrl.trimEnd('/')}/stream/$type/$id.json"
