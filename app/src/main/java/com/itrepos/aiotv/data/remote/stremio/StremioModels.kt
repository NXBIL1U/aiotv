package com.itrepos.aiotv.data.remote.stremio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StremioManifest(
    val id: String,
    val version: String,
    val name: String,
    val description: String? = null,
    val types: List<String> = emptyList(),
    val catalogs: List<StremioCatalogDef> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
)

@Serializable
data class StremioCatalogDef(
    val type: String,
    val id: String,
    val name: String? = null,
    val extra: List<StremioExtraDef> = emptyList(),
) {
    val supportsSearch: Boolean get() = extra.any { it.name == "search" }
    val supportsSkip: Boolean get() = extra.any { it.name == "skip" }
}

@Serializable
data class StremioExtraDef(
    val name: String = "",
    val isRequired: Boolean = false,
)

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMeta> = emptyList(),
)

@Serializable
data class StremioMetaResponse(
    val meta: StremioMeta? = null,
)

@Serializable
data class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("imdbRating") val imdbRating: String? = null,
    val videos: List<StremioVideo> = emptyList(),
)

@Serializable
data class StremioVideo(
    val id: String,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val released: String? = null,
)

@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStream> = emptyList(),
)

@Serializable
data class StremioStream(
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val title: String? = null,
    val name: String? = null,
    val behaviorHints: StremioBehaviorHints? = null,
)

@Serializable
data class StremioBehaviorHints(
    val bingeGroup: String? = null,
    val filename: String? = null,
)
