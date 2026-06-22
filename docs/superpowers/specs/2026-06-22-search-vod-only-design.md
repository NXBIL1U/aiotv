# Search VOD-only + real Cinemeta search (+ Home channel-strip) — design

Date: 2026-06-22 · Branch: `feat/search-vod-home` (off `feat/vod-series-spine`) · Status: approved-to-spec

## 1. Problem

The app is meant to be **movie/series focused, with live IPTV reachable only via the Live TV tab**
(owner direction, 2026-06-22). Two screens violate that, and the main Search is doubly broken:

1. **Main Search leaks channels.** `SearchViewModel.search()` calls `getChannels()` and renders a
   "Channels" section alongside VOD. (This is why searching "How I Met Your Mother" surfaced live
   IPTV channels.)
2. **VOD "search" is not a search.** It calls `getCatalog("movie")` — which loads the **browse
   catalogs** the installed *Streaming Catalogs* addon advertises (curated "what's currently on
   Netflix/Disney+/… in the UK" availability lists) — then **substring-filters that preloaded list**.
   It only loads `type=movie` (so **series never appear**), and any title not currently on one of
   those 9 UK services is invisible. There is no real index.
3. **Home leaks channels + paints slowly.** `HomeViewModel` awaits `getChannels()`
   (`LiveTvRepository.getChannelsOnce()` → all 27.5k channels, plus a ~36 s refetch on a stale
   cache) and gates `isLoading` on it, then renders a "Live Now" channel rail.

## 2. Goals / Non-goals

**Goals**
- Main Search returns **real movies AND series** from a proper search index, routing to the existing
  Detail page. **No channels.**
- The search index is **Cinemeta** (already our built-in meta provider; returns IMDb `tt…` ids that
  the Detail → Cinemeta-meta → Torrentio/TorBox pipeline already speaks).
- Search behind a small **provider seam** (default Cinemeta) so TMDB / "any search-capable addon"
  can be added later without a rewrite.
- **Home:** remove the "Live Now" rail and the channel load; Home paints fast and is channel-free.
- **Live TV tab unchanged** — channel browsing/search stays there.

**Non-goals (out of scope here)**
- Home **VOD network-category rails** (Netflix/Disney/… rows) — that's nabz's tracked `[P2]` work.
  We only *remove* channels from Home; we do not add or restyle the VOD rails.
- Additional search providers (TMDB, multi-addon aggregation) — only the seam is left.
- Recent searches, voice search, search-result pagination, "available on Netflix" badges.

## 3. Why Cinemeta (decision)

Downstream resolution is keyed on **IMDb ids** (`tt…`): Detail loads Cinemeta meta by `tt…`;
Torrentio resolves streams by `tt…`. The search source must return `tt…` or results dead-end at
"no streams."

| Option | Coverage | Returns `tt…` | New infra | Verdict |
|---|---|---|---|---|
| **Cinemeta search** | IMDb-scale, movie+series | ✅ | none (already used) | **chosen** |
| TMDB-based addon | rich/multilingual | ⚠️ often `tmdb:` → needs imdb mapping | new addon | risky (id mismatch) |
| Streaming Catalogs addon search | 9 UK services only | ✅ but tiny | none | too narrow |
| IMDb/OMDb suggest API | good titles | ✅ | new integration + still need posters | no upside over Cinemeta |
| Generic all-addon search | varies | varies | most code | future, YAGNI now |

- **No new VPN constraint:** Cinemeta is `strem.fun` (Cloudflare/VPN-blocked on the owner's VPN IP),
  but so is Torrentio (streams). The whole VOD path is already VPN-off; search adds nothing new.
  (See `[[strem-fun-blocked-by-vpn-cloudflare]]` memory.)
- The *Streaming Catalogs* addon (config decoded: `nfx,dnp,amp,atp,hbm,cru,itv,bbc,al4 :: GB`) is a
  **discovery/availability** source, not a search index — and is the data source for nabz's Home
  network rows, which is why we leave its catalogs alone.

## 4. Architecture

| Unit | Responsibility | Notes |
|---|---|---|
| `StremioApi.getCatalog(@Url)` (existing) | reused for search — Cinemeta returns `StremioCatalogResponse { metas }` for a search URL | no new API method strictly needed; a `searchUrl(host,type,query)` helper is added |
| `searchUrl(host, type, query)` (new helper in `StremioApi.kt`) | builds `"$host/catalog/$type/top/search=${URLEncoder.encode(query)}.json"` | URL-encode the query |
| `SearchRepository` (new, data) | `suspend fun search(query: String): List<MediaItem>` — query Cinemeta hosts (fallback `cinemeta-live.strem.io` → `v3-cinemeta.strem.fun`) for **both `movie` and `series`**, map `metas`→`MediaItem`, **merge + dedup by id**. This method *is* the provider seam; internally Cinemeta-only for now. | mirrors `MetaRepository`'s host-fallback shape |
| `SearchVodUseCase` (new, domain) | thin `operator fun invoke(query) = repo.search(query)` | matches existing use-case pattern (`GetCatalogUseCase`) |
| `SearchViewModel` (modify) | **drop `getChannels()` + `channelResults`**; debounce the query ~**350 ms**; cancel in-flight search on a new keystroke; call `SearchVodUseCase`; state = `query`, `isSearching`, `results`, `error` | empty query → idle (no results shown) |
| `SearchScreen` (modify) | remove the "Channels" section + channel rendering; placeholder → **"Search movies & series…"**; add **TV overscan** margins; loading / empty / error states; result `onClick` → `Screen.Detail.createRoute(item.type, item.id)` (unchanged) | also fixes `[P1]` Search-overscan TODO |
| `HomeViewModel` (modify) | drop the `getChannels()` await + `liveChannels` from state; **`isLoading` no longer gated on channels** | the slow-paint fix |
| `HomeScreen` (modify) | remove the "Live Now" rail block | keep VOD rails (Continue Watching, Top Picks, Series, etc.) as-is |

`GetChannelsUseCase` / `LiveTvRepository` are **not** changed — Live TV keeps using them.

## 5. Data flow (search)

```
user types → SearchViewModel debounces 350ms → SearchVodUseCase(query)
  → SearchRepository.search(query):
       for type in ["movie","series"]:
         try hosts [cinemeta-live, v3-cinemeta]:
           getCatalog( searchUrl(host, type, query) ) → metas → MediaItem
       merge movie+series, dedup by id  → List<MediaItem>
  → state.results → grid → tap → Screen.Detail.createRoute(item.type, item.id)
```

A new query supersedes an in-flight one (cancel/replace), so results never show stale matches.

## 6. Error / empty states

- **Empty query:** idle — show nothing (or a one-line hint); no network call.
- **Searching:** spinner.
- **No matches:** "No movies or series found for \"<query>\"."
- **Cinemeta unreachable** (both hosts fail — VPN on / no network): an explicit
  "Search unavailable — check your connection." state, **not** a silent empty list (the
  HTML/403/timeout is distinguishable). This is a UX improvement over today's silent-empty.
- All network on IO dispatchers.

## 7. Testing

**Unit (JUnit4 + `runBlocking`, hand-fake `StremioApi` — same style as `MetaRepositoryFallbackTest`):**
- `searchUrl` builds the correct Cinemeta path and **URL-encodes** the query (spaces, `&`, accents).
- `SearchRepository.search` queries **both** `movie` and `series`, maps `metas`→`MediaItem`, and
  **dedups by id** (a fake api returning overlapping ids yields a single entry).
- Host fallback: first Cinemeta host throws → second host's results are returned.

**Manual on emulator (VPN off; restart emulator with `-dns-server` if a fetch 403s/UnknownHosts):**
- Search **"rick and morty"** → a **series** result appears → tap → Detail → episode plays.
- Search **"aftersun"** (movie) and a title NOT on the 9 UK services (e.g. **"inception"**) → real
  results (proves it's an index, not a catalog filter).
- Confirm **no channels** anywhere in Search; placeholder reads "movies & series".
- **Home** loads fast (no 27.5k-channel wait) and shows **no "Live Now" rail**; VOD rails intact.
- **Live TV tab** channel search still works (unchanged).

## 8. Decisions

- Search index = **Cinemeta** (`cinemeta-live.strem.io` first), behind a `SearchRepository` seam;
  a provider *interface* is deferred until a 2nd provider exists (YAGNI).
- Search returns **movies + series combined**, deduped by id; routes to the existing Detail.
- **Debounce 350 ms**; new query cancels in-flight.
- **Home:** remove the live-channel rail + load only; **network rails are nabz's** and untouched.
- Git: branch `feat/search-vod-home` (off `feat/vod-series-spine`); **commit locally, no push**;
  owner + nabz coordinate the Home merge.
- Validation: VPN off, phone emulator, screenshots.
</content>
