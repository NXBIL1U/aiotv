# AIO TV — TODO / Tracking

_Last updated: 2026-06-22_

Living checklist of outstanding work. Each item is tagged with the roadmap phase that
resolves it (`[P0]`–`[P5]`), so executing a phase ticks off its items automatically.
For the product vision, target architecture, and phased roadmap, see [`DESIGN.md`](DESIGN.md).

Phases: **P0** stabilise/verify · **P1** foundations · **P2** core VOD · **P3** live TV ·
**P4** discovery/personalisation · **P5** hardening/modernisation.

---

## ✅ Done (on `main`)

- **P0 — Playback blockers:** cleartext HTTP enabled, custom User-Agent + cross-protocol
  redirects, DASH module + MIME hinting, error/buffering UI with retry, lifecycle pause/resume,
  audio focus, wake mode, resume-from-position.
- **P0 — Release build:** kotlinx.serialization R8 keep rules; release signs with the debug key.
- **P0 — TorBox VOD:** `checkcached` format fix; removed dead cached-resolution branch.
- **Data fixes:** M3U UTF-8 BOM handling; M3U/EPG fetch moved to `Dispatchers.IO`.
- **P1 — TV navigation:** clickable D-pad nav rail, initial focus per screen, focusable +
  scrollable TV Guide, TV overscan margins.
- **P1 — Foldable/adaptive:** width-based layouts (TV rail / bottom bar / side rail),
  Detail two-pane ≥840dp, Search grid ≥600dp.
- **P2 — UX polish:** fixed invisible progress bar, image placeholders, lazy-list keys, larger
  TV typography, Settings password toggles + IME actions, Detail loading/error feedback.
- **Build/repo fixes:** added missing `gradle.properties` (`android.useAndroidX=true`) and
  un-ignored it; fixed `SearchScreen` missing `items` import. Project compiles.
- **Nav fix:** phone bottom bar now includes Settings/Addons.
- **Addon robustness (2026-06-21):** manifest `resources` accepts object-form (Torrentio no
  longer crashes the app); resilient per-addon manifest loading; `async`/coroutine crash guard
  in Home; **series `year` range-string parse fix** (root cause of "series don't load").
- **Verified live:** end-to-end on emulators with the owner's real addons (Netflix catalog +
  Torrentio+TorBox) — movie playback, series listing, and search all work.
- **Live-TV Xtream fix + validation (2026-06-21):** an Xtream `get.php?type=m3u_plus` URL in the
  M3U field returns one ~340 MB file bundling live + all VOD + all series — loading it whole
  (`body.string()`) OOM-killed the app the instant the TV Guide opened (kernel kill, no Java
  trace). Now: detect Xtream `get.php` URLs and use the compact `player_api.php` live JSON
  (~7.6 MB, ~1 s, **27.5k live channels**); stream-parse genuine M3Us line-by-line with a
  `MAX_CHANNELS` cap; play live via raw **MPEG-TS (`.ts`)** not HLS (`.m3u8`) — Xtream HLS hands
  back tokenised `/hlsr/` segment URLs that 401/403 and trip `max_connections=1`; make
  `XtreamStream.streamId/name` tolerant so one malformed row can't drop the whole list; and guard
  the M3U fetch so failures render the empty state instead of crashing. **Validated on the phone
  emulator:** channels load and a channel plays (H.264 video confirmed). Audio decodes (AAC track
  + decoder running, focus granted) but the emulator doesn't route sound to the host — **confirm
  audio on phone/Fire TV hardware.**
- **Live-TV core experience UI (2026-06-21, merged to `main`):** replaced the bare
  channel list with a **category-first browser** — category sidebar (wide) / chips (compact),
  channel cards with **logos** (Coil), **instant search** across all channels, and **lazy
  per-channel now/next EPG** via Xtream `get_short_epg` (base64-decoded, cached, `Semaphore(4)`,
  null-result recorded to avoid scroll re-fetch). Consolidated the redundant **Guide + Live tabs
  into one "Live TV"** destination (removed `TvGuideScreen`/`TvGuideViewModel`/`Screen.Guide`).
  Spec: `docs/superpowers/specs/2026-06-21-live-tv-core-experience-design.md`; plan:
  `docs/superpowers/plans/2026-06-21-live-tv-core-experience.md`. Builds clean; passed a scoped
  code review (epgCache→ConcurrentHashMap, retry-flood guard, dup-route removal, next-programme
  fix).
- **Live-TV loading/error UX (2026-06-21, on `main`):** distinct **"provider unreachable" state
  with Retry** (vs. "no source configured"); **progressive loading message** that appears ~4 s in
  and escalates at 15 s / 30 s; **auto-retry** (a few attempts with backoff) before the error
  state; OkHttp **connect timeout 30 s → 15 s per IP** so a dead IP no longer blocks a live one
  for the full 30 s (true parallel IP racing needs OkHttp 5 `fastFallback` — see Optional). **All
  of these validated on-device** during the provider outage (escalating message at 6/18/33 s,
  3 auto-retries at 15 s/IP, then error+Retry; no crash).
- **Live-TV populated browser VALIDATED (2026-06-21, on `main`):** with the provider reachable
  (via VPN — see below), the full browser works on the phone emulator: **category chips** with
  real names, **channel logos** (Coil), **instant search** (e.g. "ecuavisa" → 2 matches), and
  **now/next EPG** auto-loading for visible rows (`get_short_epg` 200s; e.g. "● La Rosa de
  Guadalupe / Next 23:00 · Versión original"), with "—" for channels lacking EPG. Tapping a
  channel plays it. No crash. **Live TV core experience is complete + validated.**
- **Live TV UX v2 (2026-06-22, branch `feat/live-tv-ux-v2`):** a ground-up redesign per UX
  feedback — **cache-first Room persistence** (channels/categories/EPG persist to disk; the app
  opens instantly and refreshes in the background; cold start with a fresh cache does **0**
  network fetches; Home/Search read the same cache via `GetChannelsUseCase`); a **Region/Language
  filter** defaulting to **UK+US+EN** (~7.2k channels vs 27.5k), derived by a unit-tested
  `RegionClassifier` (OTHER bucket + global search as the safety net); **favourite channels AND
  categories** + **recently-watched** (persisted); and a **"For You" landing** with
  channel-search as the primary action, a searchable **Category picker** + multi-select **Region
  picker** (replacing the 322-item chip strip). Spec/plan in `docs/superpowers/`. Built
  subagent-driven, final-reviewed (fixed: search-collector leak, search-clear not restoring the
  scoped list, plain-M3U regression, clearCache wiping favourites, over-eager region codes).
  **Validated on the phone emulator** (via VPN): instant cache-first opens, region default,
  favourites/recents, search + clear, pickers.
- **Live TV Sky-style refinement (2026-06-22):** after a UX review + the owner's Sky-mobile-EPG
  reference, made it cohere — **one** filter (a single "All Channels ▾" category trigger; **Region
  moved to Settings**, shown as an "EN · UK · US" caption); **Sky-style rows** (channel number +
  logo, now `HH:mm · title` with a **progress bar**, next `HH:mm · title`, ★); and a **persistent
  D-pad category rail** on wide/TV (replacing the modal picker there). Final-reviewed (fixed: a
  real Room 1→2 migration so upgrades keep favourites/recents; removed dead `setRegions`). Plan:
  `docs/superpowers/plans/2026-06-22-live-tv-sky-refinement.md`. Validated on the phone emulator
  (Sky rows confirmed on FANDUEL ch 5208; landscape rail) — _TV-emulator pass still pending._
  Note: only Home renders the app nav rail/bottom bar (pre-existing IA); inner screens are
  full-screen pushes — a separate app-wide nav cleanup (DESIGN §4).
- **Network note:** the owner's UK ISP (Virgin Media) **blocks the IPTV provider's IP range**
  (`149.18.45.x`) — confirmed by TCP-unreachable from Virgin vs. reachable over a VPN. So live TV
  needs a VPN on the owner's network during ISP IPTV blocking (common around live football).
- **VOD series spine + Netflix Detail page (2026-06-22, branch `feat/vod-series-spine`):**
  bare-series stream bug fixed (per-episode `ttID:S:E` requests); Cinemeta internal meta provider
  for movies + series (host fallback); `StreamRanker` (cached `[TB+]` → English → quality →
  seeders); 20 s auto-advance past dead torrent picks; per-episode resume (`progressId` route
  param); Netflix Detail page (hero backdrop, title/year/genres/rating, resume-aware Play, Sources
  sheet, season selector, episode list with thumbnails + resume bars, two-pane ≥840dp, TV D-pad
  Sources list). Movie metadata also resolves via Cinemeta (titles/posters/overviews). Dark +
  Netflix-red theme foundation (`ui/theme/`) shipped. Validated on phone emulator with VPN off:
  Rick & Morty S1E1 plays + resumes (00:59/22:01); Aftersun title + overview; two-pane landscape.
  _TV-emulator pass still pending._

---

## 🧪 Verification

- [x] **Phone** — builds, browses, Home + bottom bar (Cinemeta catalog renders). _(2026-06-21)_
- [x] **Detail screen** — metadata + description load; correct "no streams" empty state. _(2026-06-21)_
- [x] **Android TV emulator** — nav rail, initial focus + visible focus indicator, D-pad
      move **and center-select navigation**, TV typography. _(2026-06-21)_
- [x] **Foldable emulator** — side rail when unfolded ↔ bottom bar when folded; switches live
      on fold. _(2026-06-21)_
- [x] **Real video playback** — movie plays end-to-end via Torrentio+TorBox cached direct URLs
      (Path B; H.264+AAC decoding confirmed). _(2026-06-21)_
- [x] **Series load** — fixed after the `year` parse bug (see Done). _(2026-06-21)_
- [x] **Search** — returns catalog results ("Movies & Series"). _(2026-06-21)_
- [x] **Live TV / IPTV** — validated on phone emulator: Xtream provider (`get.php` → `player_api.php`
      live JSON), 27.5k channels load in ~1 s, a channel plays via raw MPEG-TS (H.264 video
      confirmed). Audio decodes but emulator is silent — **confirm audio on hardware.** _(2026-06-21)_
- [x] **Series Detail + episode playback** — Rick & Morty S1E1 plays and resumes; resume bar +
      "Resume S1·E1" shown; two-pane landscape confirmed; Aftersun (movie) shows title + overview.
      No crashes. Validated on phone emulator with VPN off. _(2026-06-22)_
- [ ] **TV emulator** — re-validate Detail + episode playback + D-pad Sources list after series
      spine work. _(pending)_
- [ ] **Fire TV** — must be tested on hardware (no Fire OS emulator exists).

## 🐞 Known bugs to fix

- [~] `[P1]` **No auto-refresh / pull-to-refresh** — Home loads once in `init`; adding/removing a
      source doesn't update the UI until app restart. Make repositories reactive + add cache.
      _(Live TV is now reactive + cache-first via Room (v2); Home/Search read the same cache.
      Still TODO: explicit pull-to-refresh gesture, and Home/Search reacting live to source edits.)_
- [ ] `[P1]` **Redundant Addons tab + Live/Watchlist stubs** — consolidate into the dedicated
      Sources screen (per DESIGN decision 1). _(Partly done 2026-06-21: the duplicate Guide/Live
      tabs are now a single "Live TV" destination; Addons tab + Watchlist stub still to fold in.)_
- [ ] `[P1]` **Cache invalidation on settings change** — wire `clearCache()` / reactive sources.
      _(v2 note: `LiveTvRepository.clearCache()` now exists and is scoped to cache tables only —
      preserves favourites/recents — but is not yet called on a source/settings change.)_
- [ ] `[P1]` **Settings title under status bar** — missing safe-area/window-insets padding.
- [x] `[P2]` **Series usability** — ~~Detail shows raw id; no season/episode picker; no
      auto-select~~ — fixed (2026-06-22, branch `feat/vod-series-spine`): Cinemeta meta, Netflix
      Detail with season selector + episode list + thumbnails + resume bars, `StreamRanker`
      auto-selects best cached source, 20 s auto-advance on dead picks. _(resolved 2026-06-22)_
- [ ] `[P2]` **Continue Watching resume broken for TorBox** — keyed on the ephemeral resolved
      URL; store a stable content id (+ title/poster) in `WatchProgress`.
- [ ] `[P2]` **No hinge/fold posture awareness** — player should avoid the fold crease
      (`WindowInfoTracker` / `FoldingFeature`). Note: width-based layout switching on fold
      *does* work; this is specifically about the hinge crease.
- [ ] `[P2]` **Detail screen has no poster/backdrop art on phone** — only the two-pane
      (≥840dp) layout shows artwork; single-column should too. _(found 2026-06-21)_
- [ ] `[P1]` **TV overscan missing on Settings/Search/Detail** — only Home/Guide apply the
      10-foot safe-area margins. _(found 2026-06-21)_
- [ ] `[P2]` **TV: Settings auto-focuses first field** → on-screen keyboard pops immediately
      on entry; should require an explicit tap. _(found 2026-06-21)_

- [ ] `[P2]` **Home screen → VOD network categories, NO live channels** (owner `nabz`, 2026-06-22).
      Home should show **network/provider rows (Netflix, Disney, …) from the VOD side (Stremio
      addon catalogs → TorBox/Torrentio)** + hero + Continue Watching; **remove the IPTV "live now"
      rail** entirely (live TV stays in the Live TV tab). **Also fixes Home's slow first paint:**
      `HomeViewModel.loadContent()` awaits `getChannels()` (→ `LiveTvRepository.getChannelsOnce()`,
      which loads **all 27.5k** channels mapped to domain, plus a ~36s 7.6 MB refetch on a stale
      cache) and only clears `isLoading` once all rails finish. Fix: drop the channel load from
      Home + let each rail fill independently. Needs a brainstorm: how "networks" map to the
      configured Stremio addon catalogs (one row per catalog? grouped?). See DESIGN §8.
- [~] `[P4]` **App-wide Netflix-style navigation + visual refresh** (workstream, web-validated). Spec:
      `docs/superpowers/specs/2026-06-22-app-shell-visual-refresh.md`.
  - [x] **(A) Dark + Netflix-red theme foundation** — `ui/theme/` (`Color.kt`, `Theme.kt`): tonal
        red for interactive (Material-Theme-Builder dark `primary`); raw `#E50914` on brand surfaces
        only. Shipped with the series-spine work (2026-06-22). _(done)_
  - [ ] **(B) Wordless app icon** — concept D "stacked streams" (decided 2026-06-22); refine to
        final adaptive icon (foreground + `<monochrome>` layer). _(pending)_
  - [ ] **(C) Persistent nav shell** — `NavigationSuiteScaffold` (phone/foldable) + `androidx.tv`
        `NavigationDrawer` (TV); immersive on player/Detail; move chrome out of `HomeScreen`.
        Adopt Lucide/Tabler Compose icon pack. _(pending — architectural piece)_

## 🎬 VOD follow-ups (from series spine, 2026-06-22)

- [ ] `[P2]` **Auto-next-episode / binge** — after an episode ends, auto-advance to the next
      episode. Use the `bingeGroup` field in stream metadata to group episodes; hook into the
      existing Player 20 s auto-advance mechanism.
- [ ] `[P2]` **VOD search — needs a search-capable meta source** — current search returns only
      IPTV channels. Cinemeta is an internal meta provider but is not installed as a user addon
      search catalog; the "Streaming Catalogs" addon is catalog-only. Install or wire a
      Cinemeta-style search catalog/addon to make VOD titles discoverable via search.
- [ ] `[P3]` **TV-emulator validation pass** — re-validate Detail + episode playback, D-pad
      Sources list, and focus behaviour on the TV emulator after series-spine work. (Only phone
      emulator validated so far.)
- [ ] `[P2]` **Player-level auto-advance on playback error** — the current 20 s auto-advance is
      at source-resolve time (past dead torrent picks); add a parallel path that triggers if
      ExoPlayer raises a `PlayerError` mid-playback, advancing to the next `StreamRanker` candidate
      without requiring a Sources sheet tap.
- [ ] `[P2]` **MovieMetaFallbackTest** — add a unit test covering movie metadata resolution via
      Cinemeta (similar to the series episode-list path) to ensure Cinemeta fallback parity for
      movies stays intact.
- [ ] `[P0]` **Confirm live + VOD audio on hardware** — emulator plays video but has no host
      audio; verify both live IPTV and VOD playback audio on Fire TV hardware (or a physical
      Android phone/tablet). Emulator silence is known-harmless. _(emulator-no-host-audio)_

## 🧹 Data-layer hardening

- [x] `[P1]` Make non-nullable model fields tolerant (`XtreamStream.streamId`/`name`) so one bad
      entry doesn't drop the whole list. _(done 2026-06-21 — defaults + post-filter; needed at 27.5k channels)_
- [ ] `[P2]` Stremio catalog pagination (`skip`) — currently only the first page loads.
- [ ] `[P2]` `pickFile` uses positional index — prefer filename / largest-video matching.
- [ ] `[P3]` URL-encode Xtream username/password (breaks on `&`, `=`, `+`, spaces).
- [ ] `[P3]` XMLTV time parsing: support `+01:00`-style offsets; document no-offset = UTC.
- [ ] `[P5]` Redact TorBox API key from the `requestdl` URL and from HTTP logging.

## ⚙️ Optional / housekeeping

- [ ] `[P5]` Modernize toolchain (AGP / Kotlin / Compose BOM / Hilt / Coil). Media3 → 1.5.1 done.
- [ ] `[P3/P5]` **OkHttp 5 `fastFallback` (Happy Eyeballs) for Live TV** — race a multi-IP IPTV
      host's addresses in parallel so a live IP is reached in ~1 s even when others are dead.
      Needs the OkHttp 4.12 → 5 upgrade (folds into the toolchain modernization above); until then
      the 15 s-per-IP connect timeout is the 4.x stand-in. _(noted 2026-06-21)_
- [ ] `[P5]` Replace deprecated `Icons.Filled.ArrowBack` with `Icons.AutoMirrored.Filled.ArrowBack`.
- [ ] `[P5]` Update README (release signs with debug key; corrected `gradle.properties` guidance).
- [ ] `[P5]` Add a `SessionStart` hook so Claude Code web sessions can build/lint.

---

## Out of scope (per DESIGN decisions)

Offline downloads · multiple profiles · DVR/recording · app backend.
