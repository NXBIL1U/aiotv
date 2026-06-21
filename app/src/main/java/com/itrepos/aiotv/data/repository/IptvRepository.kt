package com.itrepos.aiotv.data.repository

import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.remote.iptv.M3uParser
import com.itrepos.aiotv.data.remote.iptv.XmltvParser
import com.itrepos.aiotv.data.remote.iptv.XtreamApi
import com.itrepos.aiotv.data.remote.iptv.XtreamCreds
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

        // Never let a fetch/parse failure crash the Guide — fall back to an empty list,
        // which renders the "add an IPTV source" empty state.
        val channels = try {
            val xtreamFromM3u = m3uUrl.takeIf { it.isNotEmpty() }?.let { XtreamCreds.fromGetPhp(it) }
            when {
                // An Xtream `get.php` URL dumps live + all VOD + series into one huge M3U;
                // use the compact live-only JSON API instead (see XtreamCreds).
                xtreamFromM3u != null ->
                    fetchXtream(xtreamFromM3u.server, xtreamFromM3u.user, xtreamFromM3u.pass)
                m3uUrl.isNotEmpty() -> fetchM3u(m3uUrl)
                server.isNotEmpty() && user.isNotEmpty() -> fetchXtream(server, user, pass)
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        cachedChannels = channels
        return channels
    }

    private suspend fun fetchM3u(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        // Stream the body line-by-line (capped) rather than materialising the whole playlist
        // in memory — IPTV M3Us can be hundreds of MB and would OOM-kill the app otherwise.
        okHttpClient.newCall(req).execute().use { resp ->
            val reader = resp.body?.charStream()?.buffered() ?: return@withContext emptyList()
            M3uParser.parse(reader)
        }
    }

    private suspend fun fetchXtream(server: String, user: String, pass: String): List<Channel> {
        return try {
            val apiUrl = "$server/player_api.php?username=$user&password=$pass&action=get_live_streams"
            val streams = xtreamApi.getLiveStreams(apiUrl)
            streams.filter { it.streamId > 0 && it.name.isNotBlank() }.map { s ->
                Channel(
                    id = s.streamId.toString(),
                    name = s.name,
                    logoUrl = s.streamIcon,
                    groupTitle = s.categoryId ?: "All",
                    // Use raw MPEG-TS (.ts), not HLS (.m3u8). Xtream HLS hands back tokenised
                    // /hlsr/ segment URLs that 401/403 (and trip max_connections=1); the .ts
                    // endpoint is one persistent connection and the account's native format.
                    streamUrl = "$server/live/$user/$pass/${s.streamId}.ts",
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
