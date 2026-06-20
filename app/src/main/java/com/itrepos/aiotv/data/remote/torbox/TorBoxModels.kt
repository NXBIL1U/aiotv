package com.itrepos.aiotv.data.remote.torbox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CachedCheckResponse(
    val success: Boolean,
    val data: Map<String, List<CachedFile>>? = null,
)

@Serializable
data class CachedFile(
    val name: String? = null,
    val size: Long? = null,
)

@Serializable
data class CreateTorrentResponse(
    val success: Boolean,
    val data: TorrentData? = null,
    val error: String? = null,
)

@Serializable
data class TorrentData(
    @SerialName("torrent_id") val torrentId: Int? = null,
)

@Serializable
data class MyListResponse(
    val success: Boolean,
    val data: TorrentInfo? = null,
)

@Serializable
data class TorrentInfo(
    val id: Int,
    val name: String? = null,
    @SerialName("download_state") val downloadState: String? = null,
    val files: List<TorrentFile> = emptyList(),
)

@Serializable
data class TorrentFile(
    val id: Int,
    val name: String? = null,
    val size: Long? = null,
    @SerialName("mime_type") val mimeType: String? = null,
)
