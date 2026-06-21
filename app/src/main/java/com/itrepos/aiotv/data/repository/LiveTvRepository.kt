package com.itrepos.aiotv.data.repository

import android.util.Base64
import android.util.Log
import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.local.db.dao.CacheMetaDao
import com.itrepos.aiotv.data.local.db.dao.CategoryDao
import com.itrepos.aiotv.data.local.db.dao.ChannelDao
import com.itrepos.aiotv.data.local.db.dao.EpgDao
import com.itrepos.aiotv.data.local.db.dao.FavouriteDao
import com.itrepos.aiotv.data.local.db.dao.RecentlyWatchedDao
import com.itrepos.aiotv.data.local.db.entity.CacheMetaEntity
import com.itrepos.aiotv.data.local.db.entity.CategoryEntity
import com.itrepos.aiotv.data.local.db.entity.ChannelEntity
import com.itrepos.aiotv.data.local.db.entity.EpgEntity
import com.itrepos.aiotv.data.local.db.entity.FavouriteCategoryEntity
import com.itrepos.aiotv.data.local.db.entity.FavouriteChannelEntity
import com.itrepos.aiotv.data.local.db.entity.RecentlyWatchedEntity
import com.itrepos.aiotv.data.remote.iptv.M3uParser
import com.itrepos.aiotv.data.remote.iptv.RegionClassifier
import com.itrepos.aiotv.data.remote.iptv.XtreamApi
import com.itrepos.aiotv.data.remote.iptv.XtreamCreds
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.domain.model.EpgEntry
import com.itrepos.aiotv.domain.model.EpgNowNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LiveTvRepository"
private const val TTL_MS = 12 * 60 * 60 * 1000L        // 12 hours
private const val EPG_FALLBACK_TTL_MS = 30 * 60 * 1000L // 30 min fallback when no programme end
private const val RECENTS_LIMIT = 15

private const val META_CHANNELS = "channels"
private const val META_CATEGORIES = "categories"

@Singleton
class LiveTvRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val epgDao: EpgDao,
    private val favouriteDao: FavouriteDao,
    private val recentlyWatchedDao: RecentlyWatchedDao,
    private val cacheMetaDao: CacheMetaDao,
    private val xtreamApi: XtreamApi,
    private val appDataStore: AppDataStore,
    private val okHttpClient: OkHttpClient,
) {

    // ── Channel / Category Flows ──────────────────────────────────────────────

    /**
     * Observe channels filtered by [regionTags] and optionally [categoryId].
     * When [regionTags] is empty all channels are returned (useful as a fallback
     * before the region selection is initialised).
     */
    fun observeChannels(regionTags: List<String>, categoryId: String? = null): Flow<List<ChannelEntity>> =
        channelDao.observe(regionTags, categoryId)

    /** Global channel search — ignores region/category. */
    fun searchChannels(query: String): Flow<List<ChannelEntity>> =
        channelDao.search(query)

    /** Observe categories filtered to [regionTags]. */
    fun observeCategories(regionTags: List<String>): Flow<List<CategoryEntity>> =
        categoryDao.observe(regionTags)

    /** Observe all categories (no region filter). */
    fun observeAllCategories(): Flow<List<CategoryEntity>> =
        categoryDao.all()

    // ── Favourites ───────────────────────────────────────────────────────────

    fun observeFavChannels(): Flow<List<ChannelEntity>> = favouriteDao.observeFavChannels()
    fun observeFavCategories(): Flow<List<CategoryEntity>> = favouriteDao.observeFavCategories()
    fun isChannelFav(channelId: String): Flow<Boolean> = favouriteDao.isChannelFav(channelId)
    fun isCategoryFav(categoryId: String): Flow<Boolean> = favouriteDao.isCategoryFav(categoryId)

    suspend fun toggleFavouriteChannel(channelId: String) = withContext(Dispatchers.IO) {
        val isFav = favouriteDao.isChannelFav(channelId).first()
        if (isFav) favouriteDao.deleteFavChannel(channelId)
        else favouriteDao.insertFavChannel(FavouriteChannelEntity(channelId, System.currentTimeMillis()))
    }

    suspend fun toggleFavouriteCategory(categoryId: String) = withContext(Dispatchers.IO) {
        val isFav = favouriteDao.isCategoryFav(categoryId).first()
        if (isFav) favouriteDao.deleteFavCategory(categoryId)
        else favouriteDao.insertFavCategory(FavouriteCategoryEntity(categoryId, System.currentTimeMillis()))
    }

    // ── Recently watched ─────────────────────────────────────────────────────

    fun observeRecent(limit: Int = RECENTS_LIMIT): Flow<List<ChannelEntity>> =
        recentlyWatchedDao.observeRecent(limit)

    suspend fun recordWatched(channelId: String) = withContext(Dispatchers.IO) {
        recentlyWatchedDao.upsert(RecentlyWatchedEntity(channelId, System.currentTimeMillis()))
        recentlyWatchedDao.prune(RECENTS_LIMIT)
    }

    // ── EPG ──────────────────────────────────────────────────────────────────

    /**
     * Return cached EPG for [channelId] if not expired; otherwise fetch from the network,
     * persist, and return. Never throws — returns null on failure.
     */
    suspend fun epgFor(channelId: String): EpgNowNext? = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val cached = epgDao.byId(channelId)
        if (cached != null && nowMs < cached.expiresAtMs) {
            return@withContext cached.toNowNext()
        }
        // Fetch from network
        val creds = resolveXtreamCreds() ?: return@withContext cached?.toNowNext()
        val streamId = channelId.toIntOrNull() ?: return@withContext cached?.toNowNext()
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
            val next = entries.firstOrNull { it.startMs > nowMs }
            val expiresAtMs = now?.endMs?.takeIf { it > nowMs } ?: (nowMs + EPG_FALLBACK_TTL_MS)
            val epgEntity = EpgEntity(
                channelId = channelId,
                nowTitle = now?.title,
                nowStartMs = now?.startMs ?: 0L,
                nowEndMs = now?.endMs ?: 0L,
                nextTitle = next?.title,
                nextStartMs = next?.startMs ?: 0L,
                fetchedAtMs = nowMs,
                expiresAtMs = expiresAtMs,
            )
            epgDao.upsert(epgEntity)
            EpgNowNext(now, next)
        } catch (e: Exception) {
            Log.w(TAG, "EPG fetch failed for $channelId", e)
            cached?.toNowNext()
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    /**
     * Refresh channels and categories from the network if [force] is true or if the cache is
     * stale (older than [TTL_MS]). Runs on Dispatchers.IO; never throws.
     *
     * @return true if a network fetch was attempted (regardless of success).
     */
    // Serialises refreshes so concurrent callers (Home, Search, Live TV) share one network
    // fetch instead of each downloading the 7.6 MB list; the freshness re-check inside the lock
    // means the 2nd caller sees the cache the 1st just wrote and skips.
    private val refreshMutex = Mutex()

    suspend fun refresh(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val nowMs = System.currentTimeMillis()

            if (!force) {
                val channelsMeta = cacheMetaDao.get(META_CHANNELS)
                val categoriesMeta = cacheMetaDao.get(META_CATEGORIES)
                val isFresh = channelsMeta != null && categoriesMeta != null &&
                    nowMs - channelsMeta.refreshedAtMs < TTL_MS &&
                    nowMs - categoriesMeta.refreshedAtMs < TTL_MS
                if (isFresh) {
                    Log.d(TAG, "Cache fresh — skipping network refresh")
                    return@withLock false
                }
            }

            val creds = resolveXtreamCreds()
            if (creds == null) {
                // No Xtream account. If the user configured a plain (non-get.php) M3U URL, fetch
                // and parse it so Room is populated — otherwise Home/Search/Live TV would be empty
                // for M3U users (GetChannelsUseCase reads getChannelsOnce()).
                val m3uUrl = appDataStore.m3uUrl.first()
                return@withLock if (m3uUrl.isNotBlank()) {
                    refreshFromM3u(m3uUrl, nowMs)
                } else {
                    Log.d(TAG, "No Xtream creds and no M3U URL — skipping refresh")
                    false
                }
            }

            try {
            Log.d(TAG, "Fetching live channels + categories from network")
            val streamsUrl = "${creds.server}/player_api.php?username=${creds.user}" +
                "&password=${creds.pass}&action=get_live_streams"
            val catsUrl = "${creds.server}/player_api.php?username=${creds.user}" +
                "&password=${creds.pass}&action=get_live_categories"

            val streams = xtreamApi.getLiveStreams(streamsUrl)
            val rawCats = xtreamApi.getLiveCategories(catsUrl)

            // Map categories — derive regionTag from the category name
            val categoryEntities = rawCats.map { c ->
                CategoryEntity(
                    id = c.categoryId,
                    name = c.categoryName,
                    regionTag = RegionClassifier.classify(c.categoryName),
                )
            }

            // Build a categoryId → categoryName lookup for channels
            val catNameById = rawCats.associate { it.categoryId to it.categoryName }

            // Map channels — regionTag derived from category name (fallback: channel name)
            val channelEntities = streams
                .filter { it.streamId > 0 && it.name.isNotBlank() }
                .map { s ->
                    val catName = catNameById[s.categoryId]?.takeIf { it.isNotBlank() } ?: s.name
                    ChannelEntity(
                        id = s.streamId.toString(),
                        name = s.name,
                        logoUrl = s.streamIcon,
                        streamUrl = "${creds.server}/live/${creds.user}/${creds.pass}/${s.streamId}.ts",
                        categoryId = s.categoryId ?: "",
                        regionTag = RegionClassifier.classify(catName),
                        epgChannelId = s.epgChannelId,
                        num = s.streamId, // use streamId as ordering num
                    )
                }

            categoryDao.upsertAll(categoryEntities)
            channelDao.upsertAll(channelEntities)

            cacheMetaDao.upsert(CacheMetaEntity(META_CHANNELS, nowMs))
            cacheMetaDao.upsert(CacheMetaEntity(META_CATEGORIES, nowMs))

            Log.d(TAG, "Refreshed: ${channelEntities.size} channels, ${categoryEntities.size} categories")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Network refresh failed — serving stale cache", e)
            false
        }
        }
    }

    /**
     * Fetch + parse a plain M3U playlist and populate Room. Mirrors [IptvRepository.fetchM3u]:
     * the body is streamed via OkHttp into [M3uParser.parse] rather than materialised whole
     * (IPTV playlists can be hundreds of MB). Categories are derived from the distinct
     * group-titles. Never throws — returns true iff a network fetch was attempted.
     *
     * Must be called inside [refreshMutex] and on [Dispatchers.IO].
     */
    private suspend fun refreshFromM3u(url: String, nowMs: Long): Boolean {
        return try {
            Log.d(TAG, "Fetching plain M3U playlist from network")
            val req = Request.Builder().url(url).build()
            val channels: List<Channel> = okHttpClient.newCall(req).execute().use { resp ->
                val reader = resp.body?.charStream()?.buffered() ?: return@use emptyList()
                M3uParser.parse(reader)
            }

            // Map channels — regionTag derived from group-title (the unified category key).
            val channelEntities = channels.mapIndexed { index, ch ->
                ChannelEntity(
                    id = ch.id,
                    name = ch.name,
                    logoUrl = ch.logoUrl,
                    streamUrl = ch.streamUrl,
                    categoryId = ch.categoryKey,
                    regionTag = RegionClassifier.classify(ch.categoryKey),
                    epgChannelId = ch.tvgId,
                    num = index,
                )
            }

            // Derive categories from the distinct group-titles.
            val categoryEntities = channels
                .map { it.categoryKey }
                .filter { it.isNotEmpty() }
                .distinct()
                .map { groupTitle ->
                    CategoryEntity(
                        id = groupTitle,
                        name = groupTitle,
                        regionTag = RegionClassifier.classify(groupTitle),
                    )
                }

            categoryDao.upsertAll(categoryEntities)
            channelDao.upsertAll(channelEntities)

            cacheMetaDao.upsert(CacheMetaEntity(META_CHANNELS, nowMs))
            cacheMetaDao.upsert(CacheMetaEntity(META_CATEGORIES, nowMs))

            Log.d(TAG, "Refreshed from M3U: ${channelEntities.size} channels, ${categoryEntities.size} categories")
            true
        } catch (e: Exception) {
            Log.e(TAG, "M3U refresh failed — serving stale cache", e)
            false
        }
    }

    /**
     * One-shot, cache-first list of all channels for Home/Search: ensures a refresh has run
     * (no-op when the cache is fresh) then returns the cached channels. Region-agnostic.
     */
    suspend fun getChannelsOnce(): List<com.itrepos.aiotv.domain.model.Channel> = withContext(Dispatchers.IO) {
        refresh(force = false)
        channelDao.getAll().map { it.toDomain() }
    }

    /**
     * Wipe ONLY the re-fetchable cache tables (channels, categories, EPG, cache_meta). Call
     * before a source-change and follow with `refresh(force=true)`.
     *
     * Favourites and recently-watched are user data, not cache, so they are intentionally left
     * intact — wiping them here would silently destroy a user's stars/history on every refresh.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        channelDao.deleteAll()
        categoryDao.deleteAll()
        epgDao.deleteAll()
        cacheMetaDao.deleteAll()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Single source of truth for Xtream credentials — mirrors the logic in [IptvRepository] so
     * both repositories resolve the same account from the same DataStore.
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

    private fun decodeB64(s: String): String = try {
        if (s.isBlank()) "" else String(Base64.decode(s, Base64.DEFAULT))
    } catch (e: Exception) { s }
}

// ── Extension: EpgEntity → domain model ──────────────────────────────────────

private fun EpgEntity.toNowNext(): EpgNowNext {
    val now = if (nowTitle != null && nowEndMs > 0) EpgEntry(nowTitle, nowStartMs, nowEndMs) else null
    val next = if (nextTitle != null && nextStartMs > 0) EpgEntry(nextTitle, nextStartMs, 0L) else null
    return EpgNowNext(now, next)
}

// ── Mapping: ChannelEntity → domain Channel ───────────────────────────────────

fun ChannelEntity.toDomain() = com.itrepos.aiotv.domain.model.Channel(
    id = id,
    name = name,
    logoUrl = logoUrl,
    groupTitle = categoryId,
    streamUrl = streamUrl,
    tvgId = epgChannelId,
    categoryKey = categoryId,
)

fun CategoryEntity.toDomain() = ChannelCategory(id = id, name = name)
