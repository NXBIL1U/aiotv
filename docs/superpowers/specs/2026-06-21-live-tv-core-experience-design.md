# Live TV — Core Experience (design spec)

_Status: approved 2026-06-21. Scope: "core experience" slice of DESIGN.md Phase 3 (Live TV)._

Companion to [`DESIGN.md`](../../../DESIGN.md) §3-§4 (Live TV / EPG) and Phase 3. This spec
covers a focused redesign of the Live TV surface; it does **not** cover favourites, catch-up,
or the full multi-hour EPG time-grid (explicitly deferred below).

## 1. Problem

The current TV Guide is Phase-0 scaffolding: a flat, ungrouped list of all channels with no
images, no search, and no programme info. After the Xtream OOM fix (commit `129f2ba`) the app
loads **27,491 live channels** from the user's provider, which makes the bare list actively
unusable. All the data needed for a good experience already exists on the provider — it just
isn't fetched or rendered.

## 2. Grounding facts (from the user's Xtream provider, 2026-06-21)

- **Channels:** 27,491 live (via `player_api.php?action=get_live_streams`, ~7.6 MB, cached).
- **Logos:** 99% of channels carry a `stream_icon` URL (e.g. `https://lo1.in/ppvs/fifa.png`).
- **Categories:** 322 named groups via `get_live_categories` (~23 KB), e.g. "AM | USA SPORTS",
  "24/7 | ENGLISH". Each channel carries a `category_id`.
- **EPG:** available per-channel on demand via `get_short_epg&stream_id=X&limit=N` → returns
  `epg_listings[]` with base64 `title`/`description`, `start`/`end` and `start_timestamp`/
  `stop_timestamp`. 30% of channels also carry an `epg_channel_id` (not needed for this path).
- **Catch-up:** 801 channels have `tv_archive` (deferred).
- Provider quirk: `max_connections=1` applies to **live stream** connections, not API calls.

## 3. Goals

A category-first Live TV browser that is fast and usable across TV (D-pad), phone (touch), and
foldable:

1. Browse channels **by category** (322 groups, by name).
2. Show **channel logos** with a graceful placeholder.
3. Show **now/next programme** ("● <now>" / "Next HH:MM <next>") per channel.
4. **Search** channels by name instantly across the whole list.
5. Tap a channel → play immediately (fast zapping) — existing `.ts` player.

## 4. Non-goals (deferred)

- Favourites / recents.
- Catch-up / archive playback (the 801 `tv_archive` channels).
- Full multi-hour EPG **time-grid** (the classic cable-guide layout).
- XMLTV-based EPG for plain (non-Xtream) M3U providers — they render channels + groups + logos
  but no now/next unless this is added later.
- Pagination of the channel list (the full set is already in memory; lazy UI rendering suffices).

## 5. Information architecture

The nav currently exposes **two** destinations that both render `TvGuideScreen`
(`Screen.Guide` and `Screen.Live`). Collapse to a **single "Live TV"** destination backed by
the new `LiveTvScreen`. Keep one route (`Screen.Live`) wired in the rail/bottom bar; remove the
duplicate Guide entry from the nav surfaces. (Consistent with DESIGN decision 1 / Phase-1 IA
cleanup; the old `TvGuideScreen` is replaced for this destination.)

## 6. Layout (adaptive)

Width-based, matching the app's existing pattern (`>= 600.dp` = wide).

- **Wide (≥600dp — TV / tablet / unfolded):** two-pane.
  - Left: **category pane** — vertical, focusable, scrollable list of category names; the
    selected category is highlighted. A "Search" affordance sits above it.
  - Right: **channel list** for the selected category (or search results).
  - D-pad: up/down within a pane; left/right crosses panes. Initial focus on the category pane.
- **Compact (<600dp — phone / folded):** single column.
  - Top: search field, then a horizontally-scrolling **category chip row**.
  - Below: the channel list for the selected category (or search results).

**Channel card** (both layouts): leading logo (Coil, rounded, placeholder on missing/slow),
channel name (1–2 lines), and a now/next line. Whole card is focusable + clickable → play.

States: full-screen loading spinner while channels load; empty state ("No channels — add an
IPTV source in Settings") when none; per-card EPG shows a subtle "—" until loaded/if absent.

## 7. Data layer

### 7.1 Xtream API additions (`XtreamApi.kt`)
- `getShortEpg(url): XtreamShortEpgResponse` — `GET` to
  `…/player_api.php?…&action=get_short_epg&stream_id=X&limit=2`.
- Models:
  ```
  @Serializable data class XtreamShortEpgResponse(
      @SerialName("epg_listings") val listings: List<XtreamEpgListing> = emptyList())
  @Serializable data class XtreamEpgListing(
      val title: String = "",            // base64
      val start: String = "",            // "yyyy-MM-dd HH:mm:ss"
      val end: String = "",
      @SerialName("start_timestamp") val startTimestamp: Long = 0,
      @SerialName("stop_timestamp")  val stopTimestamp: Long = 0)
  ```
  Titles are base64; decode with `android.util.Base64` (fallback to raw on failure).

### 7.2 Domain models (`domain/model/`)
- New `ChannelCategory(id: String, name: String)`.
- New `EpgNowNext(now: EpgEntry?, next: EpgEntry?)` with `EpgEntry(title: String, startMs: Long, endMs: Long)`.
- `Channel` already has `logoUrl`, `groupTitle`, `streamUrl`; keep as-is. Add
  `categoryKey: String` — the **unified filter key**: for Xtream it's `category_id`, for plain
  M3U it's `group-title`. `ChannelCategory.id` lives in the same key space, so filtering is
  always `channel.categoryKey == selectedCategoryId` regardless of source.

### 7.3 Repository (`IptvRepository.kt`)
- `getCategories(): List<ChannelCategory>` — `get_live_categories` for Xtream creds (from the
  saved `get.php` URL or Xtream fields); cached in memory; empty for plain M3U. Build
  `categoryId → name`. For plain M3U, derive categories from the channels' `group-title`.
- `getShortEpg(channel: Channel): EpgNowNext?` — Xtream only; resolves creds, calls
  `get_short_epg` with the channel's stream id (parsed from `channel.id`), decodes + maps the
  first two listings to now/next using timestamps. Returns null on any failure (no crash).
- Per-stream EPG cache: `Map<streamId, Pair<timestampFetched, EpgNowNext>>`, 10-minute TTL.
- Keep all network on `Dispatchers.IO`; all fetches wrapped so failures degrade to empty/null.

### 7.4 Credential resolution
- Factor the existing get.php→Xtream detection (`XtreamCreds.fromGetPhp`) + the Xtream-fields
  fallback into a single `resolveXtreamCreds(): XtreamCreds?` in the repository so categories,
  channels, and EPG all use the same source.

## 8. ViewModel (`LiveTvViewModel`)

State (single `StateFlow<LiveTvState>`):
```
data class LiveTvState(
  isLoading: Boolean,
  categories: List<ChannelCategory>,     // includes a synthetic "All" first
  selectedCategoryId: String?,           // null = All
  channels: List<Channel>,               // visible set (category-filtered or search results)
  query: String,
  epg: Map<String, EpgNowNext>,          // keyed by channel.id; filled lazily
)
```
- `init`: load channels + categories (parallel), default selection = All.
- `selectCategory(id)`: filter cached channels by `categoryId`; clears search.
- `setQuery(q)`: debounced (~250 ms) in-memory case-insensitive filter over **all** channels by
  name; non-empty query overrides category filter.
- `onChannelVisible(channel)`: if no cached EPG for `channel.id`, fetch `getShortEpg` (cap ~4
  concurrent via a semaphore; dedupe in-flight by stream id); update `epg` map. Triggered from
  `ChannelCard` via `LaunchedEffect(channel.id)` on first composition — lazy lists compose only
  near-visible items, so this approximates "visible" without manual viewport tracking. Cancelled
  implicitly when the VM scope ends.
- Exposes the existing play action (navigate with `channel.streamUrl`, `channel.name`).

## 9. UI (`ui/screen/live/`)

- `LiveTvScreen(isTv, windowSizeClass, onPlayChannel, onNavigate, vm)` — picks two-pane vs
  compact by width; owns search field, category pane/chips, channel list.
- `CategoryPane` (wide) / `CategoryChips` (compact) — render `categories`, highlight selection,
  call `vm.selectCategory`.
- `ChannelCard(channel, nowNext, onClick, focusRequester?)` — logo + name + now/next; reports
  visibility (e.g. via `LaunchedEffect(channel.id)` on first composition, or a list
  `onPlaced`/index check) to `vm.onChannelVisible`.
- Reuse existing theme + Coil image loading patterns (see `MediaCard`). Apply TV overscan
  insets (the app's `TV_OVERSCAN_*`) like the other TV screens.
- Replace the `Screen.Live`/`Screen.Guide` composables in `AppNavigation` with `LiveTvScreen`;
  drop the redundant nav entry.

## 10. Playback

Unchanged. `onPlayChannel(channel.streamUrl, channel.name)` → existing `PlayerScreen`
(`.ts` MPEG-TS). Tap = instant zap. No channel-detail intermediate screen in this scope.

## 11. Error / empty / loading

- Channels loading → centered spinner.
- No channels (no source / fetch failed) → empty state with a pointer to Settings.
- Category fetch fails → fall back to a single "All" category (channels still browse).
- EPG fetch fails / absent → card shows no badge (subtle "—"); never blocks the row.
- Logo missing/slow → placeholder box (no broken-image flash).

## 12. Validation plan (emulator — user away, screenshots permitted)

Build + install on `aiotv_phone`, then verify:
1. Live TV tab shows **categories** (sidebar/chips) with real names.
2. Selecting a category filters the channel list.
3. Channel cards render **logos** (Coil) + names.
4. **now/next EPG** appears on visible cards (logcat confirms `get_short_epg` 200s; screenshot
   confirms text).
5. **Search** filters across all channels instantly.
6. Tapping a channel still **plays** (regression check of the `.ts` path).
7. No crash; app survives scrolling a large category. Re-check on a narrow window (compact
   layout) via emulator resize or the fold emulator if needed.

## 13. Risks

- **EPG request volume while scrolling** — mitigated by visible-only fetch, per-stream cache,
  concurrency cap, and in-flight dedupe.
- **Category cardinality (322)** — the pane/chips must scroll; selection state must be cheap.
- **Plain-M3U providers** — no Xtream API; ensure graceful degradation (groups from
  `group-title`, logos from `tvg-logo`, no now/next).
- **Coil at 27k logos** — only visible images load (lazy lists); placeholders prevent jank.

## 14. Component summary (isolation)

| Unit | Does | Depends on |
|---|---|---|
| `XtreamApi.getShortEpg` + models | one Xtream EPG call → typed listings | Retrofit/kotlinx |
| `IptvRepository.getCategories/getShortEpg/resolveXtreamCreds` | provider data + EPG, cached | XtreamApi, DataStore |
| `LiveTvViewModel` | UI state, filtering, lazy EPG | IptvRepository |
| `LiveTvScreen` + `CategoryPane`/`Chips` + `ChannelCard` | adaptive browser UI | ViewModel, theme, Coil |
| `AppNavigation` edit | one Live TV destination | LiveTvScreen |
