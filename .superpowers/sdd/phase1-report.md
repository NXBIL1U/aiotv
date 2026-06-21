# Phase 1 Report — Live TV Room Cache-First

**Branch:** `feat/live-tv-ux-v2`  
**Commits:** `5d7196f` → `2d6dcaa`  
**Build status:** BUILD SUCCESSFUL (all 4 tasks)

---

## Files Created

| Path | Description |
|---|---|
| `app/src/main/java/com/itrepos/aiotv/data/local/db/AppDatabase.kt` | Room DB, version 1, 7 entities |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/entity/ChannelEntity.kt` | Channel entity |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/entity/CategoryEntity.kt` | Category entity |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/entity/EpgEntity.kt` | EPG entity |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/entity/FavouriteChannelEntity.kt` | Fav channels entity |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/entity/FavouriteCategoryEntity.kt` | Fav categories entity |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/entity/RecentlyWatchedEntity.kt` | Recents entity |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/entity/CacheMetaEntity.kt` | Cache TTL metadata entity |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/dao/ChannelDao.kt` | Channel DAO |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/dao/CategoryDao.kt` | Category DAO |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/dao/EpgDao.kt` | EPG DAO |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/dao/FavouriteDao.kt` | Favourites DAO |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/dao/RecentlyWatchedDao.kt` | Recents DAO |
| `app/src/main/java/com/itrepos/aiotv/data/local/db/dao/CacheMetaDao.kt` | Cache meta DAO |
| `app/src/main/java/com/itrepos/aiotv/data/repository/LiveTvRepository.kt` | Cache-first repository |
| `app/src/main/java/com/itrepos/aiotv/di/DatabaseModule.kt` | Hilt DI module for Room |

## Files Modified

| Path | Description |
|---|---|
| `gradle/libs.versions.toml` | Added `room = "2.6.1"`, `room-runtime`, `room-ktx`, `room-compiler` libraries |
| `app/build.gradle.kts` | Added `implementation(libs.room.runtime)`, `implementation(libs.room.ktx)`, `ksp(libs.room.compiler)` |
| `app/src/main/java/com/itrepos/aiotv/ui/screen/live/LiveTvViewModel.kt` | Rewired to read `LiveTvRepository` Flows; calls `refresh()` on init |

---

## Entity Names

| Entity | Table | PK |
|---|---|---|
| `ChannelEntity` | `channels` | `id: String` (streamId) |
| `CategoryEntity` | `categories` | `id: String` (categoryId) |
| `EpgEntity` | `epg` | `channelId: String` |
| `FavouriteChannelEntity` | `favourite_channels` | `channelId: String` |
| `FavouriteCategoryEntity` | `favourite_categories` | `categoryId: String` |
| `RecentlyWatchedEntity` | `recently_watched` | `channelId: String` |
| `CacheMetaEntity` | `cache_meta` | `key: String` |

Indices on `ChannelEntity`: `regionTag`, `categoryId`, `name`.

## DAO Names

- `ChannelDao` — `observe(regionTags, categoryId?)`, `search(query)`, `byId(id)`, `upsertAll`, `deleteAll`
- `CategoryDao` — `observe(regionTags)`, `all()`, `upsertAll`, `deleteAll`
- `EpgDao` — `byId(channelId)`, `upsert`, `deleteAll`
- `FavouriteDao` — `observeFavChannels()`, `observeFavCategories()`, `isChannelFav`, `isCategoryFav`, `insertFavChannel`, `deleteFavChannel`, `insertFavCategory`, `deleteFavCategory`, `deleteAllFavChannels`, `deleteAllFavCategories`
- `RecentlyWatchedDao` — `observeRecent(limit)`, `upsert`, `prune(keep)`, `deleteAll`
- `CacheMetaDao` — `get(key)`, `upsert`, `deleteAll`

---

## `LiveTvRepository` — Exact Public Signatures

```kotlin
@Singleton
class LiveTvRepository @Inject constructor(...)

// Channels
fun observeChannels(regionTags: List<String>, categoryId: String? = null): Flow<List<ChannelEntity>>
fun searchChannels(query: String): Flow<List<ChannelEntity>>

// Categories
fun observeCategories(regionTags: List<String>): Flow<List<CategoryEntity>>
fun observeAllCategories(): Flow<List<CategoryEntity>>

// Favourites
fun observeFavChannels(): Flow<List<ChannelEntity>>
fun observeFavCategories(): Flow<List<CategoryEntity>>
fun isChannelFav(channelId: String): Flow<Boolean>
fun isCategoryFav(categoryId: String): Flow<Boolean>
suspend fun toggleFavouriteChannel(channelId: String)
suspend fun toggleFavouriteCategory(categoryId: String)

// Recents
fun observeRecent(limit: Int = 15): Flow<List<ChannelEntity>>
suspend fun recordWatched(channelId: String)

// EPG
suspend fun epgFor(channelId: String): EpgNowNext?

// Refresh / cache
suspend fun refresh(force: Boolean = false): Boolean   // returns true if network fetch attempted
suspend fun clearCache()
```

Top-level extensions (same file):
```kotlin
fun ChannelEntity.toDomain(): Channel
fun CategoryEntity.toDomain(): ChannelCategory
```

---

## Decisions / Deviations

1. **Phase 1 regionTag:** All entities get `regionTag = ""` as specified. The `observeChannels` query uses `IN (:regionTags)` — the ViewModel passes `listOf("")` for Phase 1, which returns all channels. Phase 2 will replace this with the real region set from DataStore.

2. **ViewModel design:** The ViewModel subscribes to Room Flows via `combine(observeChannels, observeAllCategories)` and applies local filtering/search in memory (same approach as the original). This avoids extra DB round-trips per user gesture. Phase 2 will move search/filter partially into the DAO when region scoping matters.

3. **`clearCache()` scope:** Intentionally wipes ALL tables including favourites and recents, matching the spec ("wipes tables"). In practice this is only called on source change. A future refinement could preserve favourites.

4. **`hasSource` detection:** In the new ViewModel, `hasSource` is derived from whether Room returns any data, OR falls back to false if `refresh()` ran without fetching (no creds). The retry logic is simplified — `retry()` calls `repository.refresh(force=true)` and the Room Flow pushes the new data.

5. **`num` field:** The spec says `num` on `ChannelEntity`; used `s.streamId` as the ordering number (mirrors what the Xtream API provides as stream order). This is the natural sort key for these lists.

6. **`IptvRepository` untouched:** VOD, search, Detail, and player all continue using `IptvRepository` unchanged. `LiveTvRepository` is additive.

---

## Commit Range

| Hash | Message |
|---|---|
| `5d7196f` | feat(livetv): add Room dependencies |
| `cf2532d` | feat(livetv): Room entities, DAOs, database |
| `75228c8` | feat(livetv): cache-first LiveTvRepository over Room |
| `2d6dcaa` | feat(livetv): ViewModel reads cache-first Room |

---

## Final Build Status

`./gradlew assembleDebug` → **BUILD SUCCESSFUL** after each of the 4 tasks.
