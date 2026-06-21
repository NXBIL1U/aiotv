# Live TV UX v2 (design spec)

_Status: approved 2026-06-21. Builds on the "core experience"
([2026-06-21-live-tv-core-experience-design.md](2026-06-21-live-tv-core-experience-design.md))._

Pulls forward DESIGN.md Phase 1 foundations (Room cache, reactive repos) and Phase 3 favourites,
driven by UX feedback: don't re-fetch everything on every open, don't default to all 27.5k
channels, and don't navigate 322 categories via a chip strip. Find-a-channel is the primary
action; region/genre are filters; favourites/recents are the fast path.

## 1. Problems (from current state)

1. **No persistence.** Channels (7.6 MB), categories, and EPG live in an in-memory cache only —
   every cold start re-downloads and re-fetches from scratch.
2. **Bad default.** Opens to "All" (27.5k channels dumped).
3. **Bad category nav.** 322 categories in a horizontal chip strip you swipe forever.
4. **Wrong primary action.** Browsing categories was foregrounded; finding a specific channel
   (and curating regulars) should lead.
5. **One mashed axis.** The provider encodes *region + genre* in one category string
   ("USA SPORTS", "USA NEWS", "24/7 ENGLISH") — users think in two axes (region, then genre).

## 2. Goals

- **Cache-first**: open instantly from disk; refresh in the background.
- **Region/Language axis** (default **UK + US**) → **Category** (within region) → **Channels**.
- **Global channel search** as the primary find tool (ignores filters; finds anything).
- **Favourites of two kinds**: favourite **channels** and favourite **categories**.
- **Recently watched**.
- **"For You" landing** that's useful from first run and gets richer over time.

## 3. Non-goals (deferred)

Catch-up/archive; full multi-hour EPG time-grid; Trakt/cross-device sync; OkHttp 5 `fastFallback`;
per-channel parental controls; reordering favourites by drag (simple add/remove only).

## 4. Architecture overview

```
Room (disk, source of truth)            Network (refresh only)
  channels, categories, epg,      ◀──   Xtream player_api (get_live_streams,
  favourite_channels,                    get_live_categories, get_short_epg)
  favourite_categories,
  recently_watched, cache_meta
        │ Flows (reactive)
        ▼
  LiveTvRepository  ── refresh(), toggleFav*, recordWatched(), epgFor()
        │ Flows
        ▼
  LiveTvViewModel (region/category/search state + derived lists)
        │ StateFlow
        ▼
  LiveTvScreen ("For You") + RegionPicker + CategoryPicker + ChannelRow(★)
```

Cache-first / stale-while-revalidate: the UI always renders from Room; `refresh()` updates Room
in the background and the Flows push the new data to the UI.

## 5. Data layer (Room)

### 5.1 Entities
- `ChannelEntity(id PK, name, logoUrl?, streamUrl, categoryId, regionTag, epgChannelId?, num)`
  — `regionTag` is derived at insert time (see §6).
- `CategoryEntity(id PK, name, regionTag)`.
- `EpgEntity(channelId PK, nowTitle?, nowStartMs, nowEndMs, nextTitle?, nextStartMs, fetchedAtMs, expiresAtMs)`
  — `expiresAtMs` = the now-programme's end (or fetchedAt + short fallback when no EPG).
- `FavouriteChannelEntity(channelId PK, addedAtMs)`.
- `FavouriteCategoryEntity(categoryId PK, addedAtMs)`.
- `RecentlyWatchedEntity(channelId PK, watchedAtMs)` — keep newest N (15); prune older.
- `CacheMetaEntity(key PK, refreshedAtMs)` — keys: `channels`, `categories` (for TTL).

### 5.2 DAOs (all reads return `Flow`)
- channels: `observe(regionTags, categoryId?)`, `search(query)` (global, name LIKE), `byId`.
- categories: `observe(regionTags)`, `all()`.
- favourites: `observeFavChannels()` (join channels), `observeFavCategories()` (join categories),
  `isChannelFav`, `isCategoryFav`, insert/delete.
- recents: `observeRecent(limit)` (join channels), `upsert`, `prune(keep)`.
- epg: `byId`, `upsert`.
- meta: `get`, `upsert`.

### 5.3 Repository (`LiveTvRepository`, replaces in-memory caches in `IptvRepository`)
- Exposes the DAO Flows.
- `refresh(force=false)`: if `force` or `channels`/`categories` stale (TTL ~12 h), fetch network
  (existing Xtream calls), derive `regionTag` (§6), upsert channels+categories, set `cache_meta`.
  Cache-first: callers never block on this; it runs in `viewModelScope`/IO and Flows update the UI.
- `epgFor(channel)`: return cached `EpgEntity` if `now < expiresAtMs`, else fetch `get_short_epg`,
  upsert, return. (Same base64 + now/next logic as core; cached to disk now.)
- `toggleFavouriteChannel(id)`, `toggleFavouriteCategory(id)`, `recordWatched(id)` (+ prune).
- Settings/source change → `clearCache()` (wipe tables) + `refresh(force=true)`.
- Keep the existing get.php→Xtream cred resolution and `.ts` stream-URL building.

TTL: channels/categories 12 h (background refresh past that, or on pull-to-refresh / source
change). EPG per-programme expiry. First-ever launch shows a spinner only until the first network
fetch completes; subsequent launches are instant from Room.

## 6. Region/Language classification (pure, testable)

`RegionClassifier` maps a category name (and, as a fallback, a channel-name prefix) to a
`regionTag`. Heuristic keyword match over upper-cased tokens, first match wins:

| regionTag | matches (examples) |
|---|---|
| `US` | "USA", "US", "AMERICAN" (but not "LATIN AMERICA") |
| `UK` | "UK", "GB", "UNITED KINGDOM", "BRITISH", "ENGLAND", "IRELAND"/"IRISH" (→ optional `IE`) |
| `EN` | "ENGLISH", "24/7" English bundles (when no stronger country tag) |
| `LATAM` | country codes DO/EC/HN/HT/PA/MX/AR(gentina)/CO/…, "LATINO", "AMERICA" |
| `EU` | "EUROPE", "WEST EUROP", "EAST EUROP" |
| `MENA` | "AR/AF", "ARAB", "AFRICA", "ASIAN" (broad; refine later) |
| `OTHER` | no confident match |

- Output is a single primary tag (keep it simple; multi-tag is a later refinement).
- The map lives in one place, is unit-friendly (pure `String -> regionTag`), and is documented as
  best-effort. **`OTHER` is always selectable and global channel search ignores region**, so no
  channel is ever unreachable if its name doesn't parse.
- Default selected regions: **`{US, UK, EN}`** (English-language scope), persisted in DataStore
  (`live_regions`), changeable by the user.

## 7. ViewModel (`LiveTvViewModel`)

`ChannelUi` = a UI row model: the `Channel` + `isFavourite: Boolean` + `epg: EpgNowNext?`
(joined/derived so the row renders without extra lookups).

State (`StateFlow<LiveTvState>`):
```
data class LiveTvState(
  isLoading, isRefreshing: Boolean,
  selectedRegions: Set<String>,         // default {US, UK, EN}
  selectedCategoryId: String?,          // null = all categories within region
  query: String,                        // global channel search
  channels: List<ChannelUi>,            // current scoped/search result (each: + isFav, epg)
  favChannels: List<ChannelUi>,
  favCategories: List<ChannelCategory>,
  recent: List<ChannelUi>,
  categories: List<ChannelCategory>,    // within selectedRegions (for the picker)
)
```
- Combines repository Flows (channels-by-region/category OR search, favourites, recents,
  categories) into `LiveTvState`. Search non-empty → channels = global search results (region/
  category ignored); else channels = region(+category)-scoped.
- Actions: `setRegions(set)`, `selectCategory(id?)`, `setQuery(q)` (debounced),
  `toggleFavChannel(id)`, `toggleFavCategory(id)`, `onChannelVisible(channel)` (lazy EPG via
  `epgFor`, semaphore-capped as in core), `refresh()`.
- On first composition, trigger `repository.refresh()` (background); UI shows cached data meanwhile.

## 8. UI (`ui/screen/live/`)

**`LiveTvScreen` — "For You" landing**, adaptive (`>= 600dp` = wide):
- Top bar: **channel search** (primary) + **Region** control ("UK · US ▾") + **Categories** control.
- When `query` blank:
  - **Favourite categories** (quick chips; tap → scope to that category; managed in picker).
  - **Favourite channels** (row/list; ★ filled).
  - **Recently watched** (row).
  - **Channels** for the current region(+category) scope (the main list).
  - Adaptive empty states: no favs/recents → lead with the channel list + a "★ to add favourites"
    hint; first-ever run pre-refresh → spinner with the core's escalating message.
- When `query` non-empty: a single global **search results** list (region/category hidden).
- Wide screens: Region+Category can sit in a left pane; favourites/recents/channels on the right.

**`ChannelRow`** — logo + name + now/next + **★ favourite toggle**. Tap row → play (records recent).
**`RegionPicker`** — multi-select sheet/screen of regions (US, UK, EN, LATAM, EU, MENA, Other, All).
**`CategoryPicker`** — searchable list of categories within selected regions; each row has a **★**
to favourite the category; tap → scope channel list. Replaces the chip strip.

## 9. Playback
Unchanged (`.ts` MPEG-TS, existing player). Add `recordWatched(channelId)` on launch.

## 10. Error / empty / loading
Reuse the core's patterns: cache-first means usually instant; first run / forced refresh with no
cache → spinner + escalating message → on failure, "couldn't reach provider · Retry". Empty
region/category → "No channels here — try another region/category or search." Logo/EPG missing →
placeholder / "—".

## 11. Build order (for the plan)
1. **Room layer**: entities, DAOs, DB, Hilt wiring; `LiveTvRepository` reading/writing Room;
   `refresh()` populating from network; cache-first wiring so the existing screen reads Room.
2. **RegionClassifier** + `regionTag` on insert + region filter (default UK+US, persisted).
3. **Favourites (channels + categories) + Recently watched** (entities, DAOs, toggles, recents on play).
4. **Landing + RegionPicker + CategoryPicker** UI; ★ on rows; search-primary; remove chip strip.

Each step builds + runs on the emulator (provider reachable via VPN; see the network note in
TODO.md). EPG/data now persist, so validate that a second cold start renders instantly from cache.

## 12. Components (isolation)

| Unit | Does | Depends on |
|---|---|---|
| Room entities + DAOs + DB | typed disk cache + reactive reads | Room |
| `RegionClassifier` | category/channel name → regionTag (pure) | — |
| `LiveTvRepository` | Room-backed cache-first data + refresh + favs/recents/epg | DAOs, XtreamApi, DataStore |
| `LiveTvViewModel` | scope/search state, derived lists | LiveTvRepository |
| `LiveTvScreen` + `RegionPicker` + `CategoryPicker` + `ChannelRow` | adaptive For-You UI | ViewModel |

## 13. Risks
- **Region heuristic imperfect** → mitigated by `OTHER` bucket + global search + user-editable regions.
- **Room migration churn** as schema evolves → keep entities minimal; `fallbackToDestructiveMigration`
  is acceptable for a cache DB (it's re-fetchable).
- **27.5k rows in Room** → fine; index `regionTag`/`categoryId`/`name`; queries are Flow + LIMIT
  where listing. (Paging 3 is a later optimization if needed.)
- **First-run latency** unchanged (must fetch once) — but every subsequent open is instant.
