package com.itrepos.aiotv.data.repository

import android.util.Base64
import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.remote.iptv.M3uParser
import com.itrepos.aiotv.data.remote.iptv.XmltvParser
import com.itrepos.aiotv.data.remote.iptv.XtreamApi
import com.itrepos.aiotv.data.remote.iptv.XtreamCreds
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.domain.model.EpgEntry
import com.itrepos.aiotv.domain.model.EpgNowNext
import com.itrepos.aiotv.domain.model.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
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
    private var cachedCategories: List<ChannelCategory> = emptyList()
    // streamId -> (fetchedAtMs, nowNext); short-lived so now/next stays roughly current.
    // Concurrent: read on the caller's dispatcher, written from Dispatchers.IO across up to
    // Semaphore(4) parallel EPG fetches.
    private val epgCache = ConcurrentHashMap<String, Pair<Long, EpgNowNext>>()
    private val epgTtlMs = 10 * 60 * 1000L

    suspend fun getChannels(): List<Channel> {
        if (cachedChannels.isNotEmpty()) return cachedChannels

        val m3uUrl = appDataStore.m3uUrl.first()
        val creds = resolveXtreamCreds()

        // Never let a fetch/parse failure crash the Guide — fall back to an empty list,
        // which renders the "add an IPTV source" empty state.
        val channels = try {
            when {
                // An Xtream account (from a get.php M3U URL or the Xtream fields) — use the
                // compact live-only JSON API, not the huge get.php M3U dump (see XtreamCreds).
                creds != null -> fetchXtream(creds.server, creds.user, creds.pass)
                m3uUrl.isNotEmpty() -> fetchM3u(m3uUrl)
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        cachedChannels = channels
        return channels
    }

    /**
     * Single source of truth for Xtream credentials: an Xtream `get.php` URL pasted into the
     * M3U field takes priority, otherwise the dedicated Xtream fields. Null = no Xtream account
     * (plain M3U, or nothing configured).
     */
    private suspend fun resolveXtreamCreds(): XtreamCreds? {
        val m3uUrl = appDataStore.m3uUrl.first()
        XtreamCreds.fromGetPhp(m3uUrl)?.let { return it }
        val server = appDataStore.xtreamServer.first()
        val user = appDataStore.xtreamUser.first()
        val pass = appDataStore.xtreamPass.first()
        return if (server.isNotEmpty() && user.isNotEmpty()) {
            XtreamCreds(server, user, pass)
        } else null
    }

    suspend fun getCategories(): List<ChannelCategory> {
        if (cachedCategories.isNotEmpty()) return cachedCategories
        val creds = resolveXtreamCreds()
        val cats = try {
            if (creds != null) {
                val url = "${creds.server}/player_api.php?username=${creds.user}" +
                    "&password=${creds.pass}&action=get_live_categories"
                xtreamApi.getLiveCategories(url)
                    .map { ChannelCategory(it.categoryId, it.categoryName) }
            } else {
                // Plain M3U: derive groups from the already-loaded channels' group-title.
                getChannels().map { it.categoryKey }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .map { ChannelCategory(it, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
        cachedCategories = cats
        return cats
    }

    /** Now/next for [channel] via Xtream `get_short_epg` (cached, TTL'd). Null if unavailable. */
    suspend fun getShortEpg(channel: Channel): EpgNowNext? {
        val streamId = channel.id.toIntOrNull() ?: return null
        val nowMs = System.currentTimeMillis()
        epgCache[channel.id]?.let { (at, v) -> if (nowMs - at < epgTtlMs) return v }
        val creds = resolveXtreamCreds() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "${creds.server}/player_api.php?username=${creds.user}" +
                    "&password=${creds.pass}&action=get_short_epg&stream_id=$streamId&limit=2"
                val entries = xtreamApi.getShortEpg(url).listings.map {
                    EpgEntry(
                        title = decodeB64(it.title),
                        startMs = it.startTimestamp * 1000,
                        endMs = it.stopTimestamp * 1000,
                    )
                }.sortedBy { it.startMs }
                val now = entries.firstOrNull { it.startMs <= nowMs && it.endMs > nowMs }
                // Only a genuinely upcoming programme is "next"; never fall back to a past entry.
                val next = entries.firstOrNull { it.startMs > nowMs }
                EpgNowNext(now, next).also { epgCache[channel.id] = nowMs to it }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun decodeB64(s: String): String = try {
        if (s.isBlank()) "" else String(Base64.decode(s, Base64.DEFAULT))
    } catch (e: Exception) {
        s
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
                    categoryKey = s.categoryId ?: "",
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
        cachedCategories = emptyList()
        epgCache.clear()
    }
}
