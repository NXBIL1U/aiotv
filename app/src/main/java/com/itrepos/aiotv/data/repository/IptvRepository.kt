package com.itrepos.aiotv.data.repository

import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.remote.iptv.M3uParser
import com.itrepos.aiotv.data.remote.iptv.XmltvParser
import com.itrepos.aiotv.data.remote.iptv.XtreamApi
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvRepository @Inject constructor(
    private val xtreamApi: XtreamApi,
    private val appDataStore: AppDataStore,
    private val okHttpClient: OkHttpClient,
) {
    private var cachedChannels: List<Channel> = emptyList()
    private var cachedEpg: List<EpgProgram> = emptyList()

    suspend fun getChannels(): List<Channel> {
        if (cachedChannels.isNotEmpty()) return cachedChannels

        val m3uUrl = appDataStore.m3uUrl.first()
        val server = appDataStore.xtreamServer.first()
        val user = appDataStore.xtreamUser.first()
        val pass = appDataStore.xtreamPass.first()
        val groupFilter = appDataStore.channelGroupFilter.first()

        val channels = when {
            m3uUrl.isNotEmpty() -> fetchM3u(m3uUrl, groupFilter)
            server.isNotEmpty() && user.isNotEmpty() -> fetchXtream(server, user, pass)
            else -> emptyList()
        }
        cachedChannels = channels
        return channels
    }

    suspend fun fetchGroupNames(): List<String> = withContext(Dispatchers.IO) {
        val url = appDataStore.m3uUrl.first()
        if (url.isEmpty()) return@withContext emptyList()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) AIO-TV/1.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val stream = resp.body?.byteStream() ?: return@withContext emptyList()
                M3uParser.parseGroupNames(BufferedReader(InputStreamReader(stream)))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchM3u(url: String, groupFilter: Set<String> = emptySet()): List<Channel> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) AIO-TV/1.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val stream = resp.body?.byteStream() ?: return@withContext emptyList()
                M3uParser.parseStreaming(BufferedReader(InputStreamReader(stream)), groupFilter)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchXtream(server: String, user: String, pass: String): List<Channel> {
        return try {
            val apiUrl = "$server/player_api.php?username=$user&password=$pass&action=get_live_streams"
            val streams = xtreamApi.getLiveStreams(apiUrl)
            streams.map { s ->
                Channel(
                    id = s.streamId.toString(),
                    name = s.name,
                    logoUrl = s.streamIcon,
                    groupTitle = s.categoryId ?: "All",
                    streamUrl = "$server/live/$user/$pass/${s.streamId}.m3u8",
                    tvgId = s.epgChannelId,
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getEpg(): List<EpgProgram> {
        if (cachedEpg.isNotEmpty()) return cachedEpg
        val url = appDataStore.xmltvUrl.first()
        if (url.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).build()
                val body = okHttpClient.newCall(req).execute().body?.string()
                    ?: return@withContext emptyList()
                XmltvParser.parse(body).also { cachedEpg = it }
            } catch (e: Exception) { emptyList() }
        }
    }

    fun clearCache() {
        cachedChannels = emptyList()
        cachedEpg = emptyList()
    }
}
