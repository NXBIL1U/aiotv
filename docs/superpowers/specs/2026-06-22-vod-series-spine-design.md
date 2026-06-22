# AIO TV — VOD Series Spine + Netflix-style Detail (design)

Date: 2026-06-22 · Branch: `feat/vod-series-spine` · Status: approved-to-build (owner away; async review on return)

## 1. Problem

Picking a **series** (e.g. *How I Met Your Mother*) from Search opens the Detail screen but it
**shows no series info / no season+episode picker, and never plays anything**. Two independent root
causes, both confirmed by code reading + a live network smoke test (§3):

1. **Series streams are requested with the bare title id.** `DetailViewModel.load()` →
   `getStreams("series", "tt0460649")` → `…/stream/series/tt0460649.json`. The Stremio protocol
   requires series stream requests to be **per-episode** (`…/stream/series/tt0460649:1:1.json`).
   The bare-series request returns **0 streams** → "nothing plays".
2. **No installed addon provides an episode list.** Installed addons are Torrentio (streams only)
   and a "Streaming Catalogs" addon (catalog only). Neither returns `meta.videos`, so there is no
   data to build a season/episode picker from. `getMeta()` returns no usable episode tree.

Additionally, the owner has approved doing the **polished Netflix-style Detail page now** (not
deferring it), which requires the app's **dark + Netflix-red theme foundation** to be pulled
forward app-wide (the current light Material theme can't carry a Netflix look).

## 2. Goals / Non-goals

**Goals**
- Series Detail = real metadata (poster/backdrop, year·genres·rating, overview) + **season selector**
  + **episode list** (with episode thumbnails + titles).
- Tap an episode → **auto-play the best available source** (cached-first, ranked), with a **Sources**
  override and an auto-advance-past-dead fallback (§6).
- Correct **per-episode** stream resolution; **per-episode resume** via the existing `WatchProgress`.
- **Netflix-style Detail visuals** on phone, TV (10-foot, D-pad), and Fold.
- **App-wide dark + Netflix-red theme foundation** (tokens only — see §8).
- Movies keep working unchanged.

**Non-goals (explicitly deferred)**
- **Auto-play next episode / binge** (uses `bingeGroup`) — fast-follow, not this pass.
- **Nav shell**, **app icon**, and **per-screen visual polish** beyond Detail — stay in the
  app-shell/visual-refresh workstream (`specs/2026-06-22-app-shell-visual-refresh.md`).
- **Home network-categories redesign** — separate workstream (owner `nabz`); but see §10 note: the
  "Streaming Catalogs" addon discovered here is its data source.
- Real-Debrid/AllDebrid abstraction — out of scope.

## 3. Validated findings (live smoke test, 2026-06-22, VPN off / residential IP)

Exact provider responses for `tt0460649` (HIMYM), using the owner's real configured addon + TorBox key:

| Check | Result | Implication |
|---|---|---|
| `…/stream/series/tt0460649.json` (bare) | **0 streams** | confirms root cause #1 |
| `…/stream/series/tt0460649:1:1.json` (per-ep) | **9 streams** | the fix mechanism works |
| Stream shape | **`url` set, `infoHash` null** | Torrentio has `torbox=` baked in → returns **pre-resolved TorBox URLs**, not hashes |
| Cached marker | every stream `name` starts **`[TB+]`** | "cached on TorBox / instant" — this, not `checkCached(hash)`, is the cached signal for this config |
| Seeders | present in `title` (`👤 7/3/8/27…`) | seeder-rank lever is real (for the uncached/non-debrid path) |
| `behaviorHints` | `bingeGroup`, `filename` (per-episode file), `videoSize`, `videoHash` | rich metadata; `filename` pins the exact episode even inside a season pack |
| Playback proof | range-GET first 64 KB of a resolve URL → **HTTP 206, `Matroska` bytes** | resolve→play path genuinely streams real video |
| Cinemeta `v3-cinemeta.strem.fun` | **`http=000`** (flaky/reset) | the "standard" host is unreliable from here |
| Cinemeta `cinemeta-live.strem.io` | **HTTP 200, 218 videos, seasons 0–9** | **use this host (with fallback)** as the episode-list source |
| TorBox `/v1/api/user/me` | **HTTP 200**, key valid | resolver backend reachable + authenticated |

**Environmental note:** `strem.fun` (Torrentio + Cinemeta) is **Cloudflare-403'd on the owner's VPN
datacenter IP**; it works on the residential IP. IPTV needs the VPN, Torrentio is blocked by it —
mutually exclusive on one exit. Validation for this work is done **VPN-off**.

## 4. Architecture

Keep the single `Detail` nav route and **one `DetailViewModel`** that branches on `meta.type`;
split the UI into focused Composables. New/changed units, each with one clear purpose:

| Unit | Responsibility | Depends on |
|---|---|---|
| `MetaRepository` (new) | Fetch title meta. For **series**, fetch **Cinemeta** episode tree (`videos`) with host fallback (`cinemeta-live.strem.io` → `v3-cinemeta.strem.fun`); merge with installed-addon meta. Cinemeta is an **internal default**, invisible to the user. | `StremioApi` |
| `Episode` (new domain model) | One episode: `id` (`tt…:S:E`), `season`, `number`, `name`, `overview`, `thumbnail`, `released`. | — |
| `MediaItem` (extend) | already has `type`; ensure `series` flows through. | — |
| `Stream` (extend) | add parsed `quality`, `seeders`, `sizeBytes`, `bingeGroup`, `languageHints`, and `isCached` derived from the **`[TB+]`** marker (in addition to the existing hash-based `isCached`). | — |
| `StreamRanker` (new, pure) | Rank a `List<Stream>`: **cached → English-preferred → quality (2160>1080>720>SD) → seeders**. One testable function; used for both auto-pick and the Sources list order. | — |
| `StreamResolver` (extract from `DetailViewModel`) | Resolve a chosen `Stream` to a playable URL, handling **both shapes**: pre-resolved `url` (use directly) and `infoHash` (existing `checkCached → createTorrent → poll → getDownloadUrl`). Exposes a suspend `resolve(): Result<String>`. | `TorBoxRepository` |
| `DetailViewModel` (rework) | Branch on type. Movie = current path. Series = load Cinemeta meta → expose seasons/episodes → on episode select, fetch per-episode streams → rank → **auto-resolve best**, with auto-advance + Sources fallback. Per-episode resume via `WatchProgressRepository`. | all above |
| `MovieDetail` / `SeriesDetail` Composables (split) | Focused UI per type (current `DetailScreen` content → `MovieDetail`; new `SeriesDetail`). | theme, VM |
| `SourcesSheet` Composable (new) | Bottom sheet (phone/Fold) / side panel (TV) listing ranked sources, cached badged. | theme |
| Theme foundation (rework `ui/theme/`) | App-wide dark surfaces + tonal-red accent; raw `#E50914` brand-only. See §8. | — |

*Alternative considered & rejected:* a separate `SeriesDetailViewModel` + nav route — duplicates
resolve/play wiring and forces every caller (Search/Home) to branch on type before navigating.

## 5. Data flow — series

```
Search/Home tap (type="series", id="tt0460649")
  → DetailViewModel.load(type,id)
      ├─ MetaRepository.getMeta("series", id)
      │     → Cinemeta(cinemeta-live.strem.io) /meta/series/tt0460649.json
      │     → meta + videos[] (218) → group into seasons (skip season 0 specials by default, but keep accessible)
      ├─ state: meta + seasons + selectedSeason (default: earliest unwatched, else S1)
      └─ render SeriesDetail (info + season selector + episode list, resume markers from WatchProgress)
  → user taps Episode S1E1 (episode.id = "tt0460649:1:1")
      → DetailViewModel.selectEpisode(episode)
          ├─ getStreams("series", episode.id)  →  …/stream/series/tt0460649:1:1.json  → 9 streams
          ├─ StreamRanker.rank(streams)         →  cached/English/quality/seeders
          ├─ auto-pick top CACHED ([TB+]) → StreamResolver.resolve() → url → Player(url, resumePos)
          │     (if top non-cached: try highest-seeded; on stall/fail → auto-advance to next)
          └─ else (nothing resolvable) → open SourcesSheet (ranked, cached badged) for manual pick
```

Movie flow is unchanged: `load` fetches streams for the bare id, lists/auto-picks them the same way.

## 6. Episode → action behaviour (owner-approved)

> Tap episode → auto-play the **best `[TB+]`-cached** source instantly (the common case). If nothing
> is cached → auto-try the **highest-seeded** source; if it stalls/dies, **auto-advance** to the next.
> Show a quiet "Finding a working source…" while that happens. A **Sources** button always allows a
> manual pick; the full ranked list is the final fallback if everything fails.

- "Dead source" handling is honest: cached (`[TB+]`) = provably alive + instant; for uncached we
  rank by seeders and auto-advance past anything that fails to resolve within a short window.
- Resolve timeout for auto-advance: short (e.g. ~20s) so a dead pick fails fast; manual picks keep
  the longer `pollUntilReady` budget.

## 7. UI / UX — wireframes

States: **loading** (skeleton) · **loaded** · **empty meta** (Cinemeta down → show title + "episodes
unavailable, try later") · **resolving** ("Finding a working source…") · **error**.

### Phone (portrait)
```
┌─────────────────────────────┐
│ ◀  [backdrop, scrim]        │  hero backdrop (background image), red back chip
│        How I Met Your Mother│
│        2005 · Comedy · ★8.3 │
│  [▶ Play S1·E1]  [＋ List]   │  Play = resume-aware ("Resume S2·E4" if in progress)
│  Overview text… (2 lines, ⌄)│
│  ─────────────────────────  │
│  Season ▾ [1]               │  season selector (dropdown/segmented)
│  ┌───┬───────────────────┐  │
│  │ 🖼 │ 1. Pilot      24m  │  │  episode rows: thumbnail, num·title, runtime,
│  │   │ ▓▓▓▓▓░░ (resume)   │  │  resume bar if partially watched
│  ├───┼───────────────────┤  │
│  │ 🖼 │ 2. Purple Giraffe │  │
│  └───┴───────────────────┘  │
└─────────────────────────────┘
Tap episode → auto-play; long-press / "Sources" affordance → SourcesSheet (bottom sheet).
```

### TV (10-foot, D-pad)
```
┌───────────────────────────────────────────────────────────┐
│  [full-bleed backdrop + left scrim]                        │
│  HOW I MET YOUR MOTHER                                      │
│  2005 · Comedy · ★8.3                                       │
│  Overview…                                                  │
│  [▶ Resume S2·E4]   [Sources]              Season ◀ 1 ▶     │
│  ───────────────────────────────────────────────────────  │
│  ▐ ep ▌ ▐ ep ▌ ▐ ep ▌ ▐ ep ▌ …   (horizontal, D-pad focus) │
└───────────────────────────────────────────────────────────┘
First focus = Play/Resume. Season change via L/R on the season control or a focusable rail.
Episodes are a horizontal focusable row (TV-idiomatic), with focus scale + title reveal.
```

### Fold / wide (≥840dp) — two-pane
```
┌──────────────────────────┬───────────────────────────┐
│ backdrop + meta + Play   │ Season ▾ [1]               │
│ + overview (left pane)   │ episode list (right pane,  │
│                          │ vertical, scrollable)      │
└──────────────────────────┴───────────────────────────┘
```
Reuse the existing `BoxWithConstraints` twoPane split point (840.dp).

`SourcesSheet`: rows = quality badge · cached `[TB+]` badge · size · seeders · source name; tap = play.

## 8. Theme foundation (pulled forward, app-wide)

Implements the palette already specified in `specs/2026-06-22-app-shell-visual-refresh.md`:
- **Dark** surfaces (near-black background, layered elevations).
- **Tonal red** as the interactive/accent colour (a11y-safe), **raw `#E50914` reserved for brand
  moments only** (e.g. wordmark/splash), not for large text/contrast-critical UI.
- Apply via `ui/theme/` (`Color.kt`, `Theme.kt`, typography untouched unless needed). Force dark
  (no dynamic/light) for now. Every existing screen inherits it.
- **Scope discipline:** this pass delivers the **theme tokens applied app-wide** + the **Detail page**
  built against them. It does **not** re-style other screens beyond what inheriting the theme gives
  them, and does **not** touch nav. Those remain in the visual-refresh workstream.

## 9. Error handling

- **Cinemeta host fallback:** try `cinemeta-live.strem.io`, then `v3-cinemeta.strem.fun`; on total
  failure show the title with an "episodes unavailable" state (don't crash, don't show a blank list).
- **Empty per-episode streams:** show "No sources for this episode" in the SourcesSheet; offer retry.
- **Resolve failure / dead source:** auto-advance (§6); if all fail, surface a concise error + manual
  Sources list.
- **Cloudflare 403 (VPN on):** detectable (HTML body / 403); surface "Source provider unreachable —
  check VPN/network" rather than a silent empty list. (Improves the current silent-empty UX.)
- All network on IO dispatchers; no work on main.

## 10. Testing & validation

**Unit (JVM, no device):**
- `StreamRanker`: cached-first; English preference; quality ordering; seeder tiebreak; mixed list
  from the real HIMYM fixture (§3) ranks the English 1080p AMZN source above RU/IT/FR.
- Cinemeta parse: `videos` → `Episode`s; season grouping; specials (season 0) separated.
- `[TB+]` cached detection from `name`; seeder/quality/size parsing from `name`/`title`.
- `StreamResolver`: url-shape returns url directly; hash-shape calls TorBox path; failure → Result.error.
- Capture the real HIMYM responses (§3) as JSON test fixtures under `src/test/resources/`.

**Manual on emulator (VPN off; restart with `-dns-server 8.8.8.8,8.8.4.4` if DNS stale):**
- Search → HIMYM → Detail shows backdrop + meta + overview + Season 1 + episode list (thumbnails).
- Tap S1E1 → "Finding a working source…" → **plays** (screenshot the player; confirm 206/MKV path).
- Season switch repopulates episodes; resume bar appears after partial play; reopening resumes.
- Movie (e.g. the cached *Masters of the Universe*) still plays.
- Theme: every screen renders dark + red accents (screenshots Home/Search/Live/Detail/Player).
- TV emulator (`aiotv_tv`): D-pad through season + episodes + Play; Fold layout two-pane.
- Owner is away → **validate via screenshots** (uiautomator dump is unreliable on Compose lists).

## 11. Docs to update (part of this work)
- `DESIGN.md` — series spine + Detail visuals + theme-foundation-pulled-forward; note Home redesign
  still pending and that the "Streaming Catalogs" addon (Netflix/Disney+/Prime/Apple/HBO/Crunchyroll/
  ITVX/BBC/Channel4, movie+series) is its catalog source.
- `TODO.md` — check off series spine; move auto-next/binge to a fast-follow item; reflect theme
  foundation done.
- `specs/2026-06-22-app-shell-visual-refresh.md` — record that **theme foundation + Detail page** are
  delivered in the series-spine work; remaining = nav shell, icon, per-screen polish.

## 12. Decisions (resolved)
- Episode action: **auto-play best cached, seeder-ranked, auto-advance, Sources override** (§6).
- Scope: **core spine + full Netflix Detail visuals** (option 3).
- Theme: **app-wide dark+red foundation now**.
- Cinemeta: **internal default meta provider** (host `cinemeta-live.strem.io` first), not a user addon.
- Cached signal for the owner's config: **`[TB+]` marker** (hash `checkCached` retained for non-debrid addons).
- Git: **commit locally to `feat/vod-series-spine`, no push**; owner reviews on return.
- Autonomy: decide-document-keep-going on uncovered judgment calls.
