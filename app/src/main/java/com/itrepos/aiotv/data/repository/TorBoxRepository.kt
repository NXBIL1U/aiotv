package com.itrepos.aiotv.data.repository

import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.remote.torbox.TorBoxApi
import com.itrepos.aiotv.data.remote.torbox.TorrentInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorBoxRepository @Inject constructor(
    private val torBoxApi: TorBoxApi,
    private val appDataStore: AppDataStore,
) {
    private suspend fun bearer(): String = "Bearer ${appDataStore.torBoxApiKey.first()}"

    suspend fun checkCached(hashes: List<String>): Map<String, Boolean> {
        if (hashes.isEmpty()) return emptyMap()
        return try {
            val resp = torBoxApi.checkCached(bearer(), hashes.joinToString(","))
            val dataKeys = resp.data?.keys?.map { it.lowercase() }?.toSet() ?: emptySet()
            hashes.associateWith { h -> h.lowercase() in dataKeys }
        } catch (_: Exception) { hashes.associateWith { false } }
    }

    suspend fun createTorrent(magnet: String): Int? {
        return try {
            val body = magnet.toRequestBody()
            val part = MultipartBody.Part.createFormData("magnet", null, body)
            val resp = torBoxApi.createTorrent(bearer(), part)
            resp.data?.torrentId
        } catch (_: Exception) { null }
    }

    suspend fun pollUntilReady(torrentId: Int, maxAttempts: Int = 30): TorrentInfo? {
        repeat(maxAttempts) {
            try {
                val resp = torBoxApi.getMyList(bearer(), torrentId)
                val info = resp.data
                if (info != null && info.downloadState == "completed") return info
            } catch (_: Exception) {}
            delay(5_000)
        }
        return null
    }

    suspend fun getDownloadUrl(torrentId: Int, fileId: Int): String {
        val key = appDataStore.torBoxApiKey.first()
        return "https://api.torbox.app/v1/api/torrents/requestdl?token=$key&torrent_id=$torrentId&file_id=$fileId&redirect=true"
    }
}
