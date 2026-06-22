# Netflix-style movie detail + play-on-card (design)

Date: 2026-06-22 · Branch: `feat/binge-watch` (stacked, after client-aware source selection) · Status: approved-to-spec

## 1. Problem & goal

The movie Detail is a **raw torrent/source list** (header + the new Play button + a `StreamsList`). That is the
opposite of "best UX": the user should never see a list of releases. Series already do it right — a Netflix-style
`SeriesDetail.Hero` (backdrop + scrim + **Play** + a **"Sources"** button, no inline list). Movies must match,
and playback should be reachable **directly from a poster** without opening anything.

**Goal (owner-approved):**
- **Movie Detail → Netflix-style hero** (poster/backdrop art, title, meta, description, **Play** → direct stream,
  **Sources** behind a button). **No inline source list.**
- **Every catalog card** (Home rails + Search) gets a **▶ overlay → direct stream**; tapping the **art → detail**.
- Direct-play **auto-advances through sources** (no list, no manual pick on the happy path).

## 2. Goals / Non-goals

**Goals:** the three above; reuse the existing `SeriesDetail.Hero` pattern + `SourcesSheet`/`SourcesList` and the
already-built device-aware auto-pick (`playMovieAuto` / `StreamRanker` / `PlaybackController`).

**Non-goals (v1):**
- **TV (D-pad) play-on-card.** A focusable card center-press opens **Detail** (Play is one press away on the new
  hero); the ▶ overlay is a **touch** affordance. (Owner-confirmed; revisit at TV validation — "TV is the decider".)
- No change to the **auto-pick policy** (20 GB cap, eligibility, ranking) — that ships in the prior feature.
- No new player chrome; no card hover-preview/trailer autoplay.
- Live IPTV cards (channels) are unchanged — direct-play is VOD only.

## 3. Architecture / components

| Unit | Change |
|---|---|
| `MovieDetail.kt` (rewrite) | Replace header+`StreamsList` with a **Netflix hero** mirroring `SeriesDetail.Hero`: `AsyncImage(backdropUrl ?: posterUrl)` + scrim + back + title + meta; a **Play** Button (`AccentPrimary`, initial TV focus) → `onPlayAuto`; an **OutlinedButton "Sources"** → `onShowSources`; then the description. **Delete the inline `StreamsList`/`StreamRow`** and the two-pane branch (it existed only to show the source list) — movie Detail is now a **single vertically-scrolling hero + description column** on all widths. |
| `DetailState` (modify) | Add `showMovieSources: Boolean = false` (movie-side analogue of `sourcesForEpisode`). |
| `DetailViewModel` (modify) | `showMovieSources()` / `dismissMovieSources()`. The **Sources** sheet for movies lists `state.streams` (full ranked list); `onPick` → existing `resolveStream(stream){ onPlay }`. |
| `DetailScreen.kt` (modify) | For `MOVIE`, render the `SourcesSheet` (phone) / `SourcesList` (TV) when `state.showMovieSources`, wired to `resolveStream`. Pass `onShowSources = { viewModel.showMovieSources() }` to `MovieDetail`. |
| `MediaCard.kt` (modify) | Add optional `onPlay: (() -> Unit)? = null`. When non-null, overlay a small **▶ circle** (bottom-end) that is its own tap target → `onPlay`; the card body tap stays `onClick` (→ detail). On TV/no-touch the ▶ is decorative-only (center-press → `onClick`). A per-card `isResolving: Boolean` shows a small spinner over the ▶ during resolve. |
| `PlayDirectUseCase` (new, `domain/usecase`) | `suspend operator fun invoke(item: MediaItem): DirectPlay?` — **movie:** `getStreams(item.id)` → device-aware rank → eligible subset → `PlaybackController.startMovieAuto` → `DirectPlay(url, title=item.name, progressId=item.id)`. **series:** `metaRepo.getSeriesMeta(item.id)` → resume/first episode → `getStreams(ep.id)` → rank → `startSeries` → `DirectPlay(url, episodeTitle, progressId=ep.id)`. Returns `null` when nothing eligible resolves (all sources exhausted). Reuses `StreamRanker` + `DeviceCapabilities` + `AppDataStore.preferredQuality`. |
| `HomeViewModel` / `SearchViewModel` (modify) | `playDirect(item, onPlay)`: set the card's `isResolving`, call `PlayDirectUseCase`; on success `onPlay(url,title,progressId)` (→ player); on `null` clear resolving + emit a transient **"Couldn't find a playable source"** message (snackbar/toast). |
| `HomeScreen` / `SearchScreen` (modify) | Pass `onPlay = { vm.playDirect(item, navToPlayer) }` to each `MediaCard`; tapping art keeps `onClick = navToDetail`. |

`DirectPlay` data: `data class DirectPlay(val url: String, val title: String, val progressId: String)`.

## 4. Interaction model

- **Touch (phone/Fold):** card **art tap → Detail**; card **▶ tap → direct stream**. Movie Detail: **Play → stream**,
  **Sources → sheet** (manual list), back → catalog.
- **TV (D-pad):** card center-press → **Detail**; Detail **Play** (initial focus) → stream, **Sources** → list. The
  ▶ overlay is touch-only. (Confirmed.)

## 5. Data flow (direct-play, auto-advance, failover)

```
Card ▶  → VM.playDirect(item)
   → PlayDirectUseCase(item)
        movie:  getStreams(id) → rank(profile,target) → filter{isAutoEligible}
                → startMovieAuto(eligible,…)   // resolveFrom walks candidates 0..n: AUTO-ADVANCES past
                                               //   any source that fails to resolve
        series: getSeriesMeta → resume/first ep → getStreams(ep) → rank → eligible → startSeries(…)
   → success → onPlay(url,title,progressId) → Player (follows the controller session)
   → null (all eligible exhausted) → transient "Couldn't find a playable source" (no Detail, no list)
Mid-play error in Player → controller.failover() → next source (existing). // auto-advance during playback too
```

The "try the next best source automatically" guarantee is the existing `resolveFrom` (resolve-time auto-advance)
+ `failover()` (play-time auto-advance). Direct-play adds no manual step on the happy path.

## 6. Error handling

- **A source fails to resolve** → `resolveFrom` silently tries the next eligible candidate (existing).
- **Mid-play error** → `failover()` to the next source (existing).
- **All eligible sources exhausted** (`PlayDirectUseCase` returns null) → brief transient message; the card returns
  to idle. **No Detail fallback, no source list** (owner-chosen: keep it automatic).
- **Series meta unavailable** → null → same transient message.
- `DeviceCapabilities` failure → permissive profile (existing). `CancellationException` rethrown (convention).

## 7. Testing

Mostly **integration on the emulator** (UI + IO orchestration). Pure-logic unit tests where they exist:
- `PlayDirectUseCase` **routing** (movie vs series → which repo path) and the **resume-episode rule** are extractable
  pure helpers — unit-test: movie id routes to the streams path; a series with partial progress on E3 picks E3, else
  E1; empty eligible → null. Hand-fake the repos/controller (no mockk).

**Emulator smoke (VPN off):**
1. Tap a movie poster's **art** → **Netflix hero** (backdrop, Play, Sources) — **no torrent list**.
2. Hero **Play** → streams directly; **Sources** → sheet with the full list; pick one → plays.
3. Tap a card's **▶** (Home + Search) → resolves (brief spinner) → **streams directly**, no list.
4. Force a dud first source if feasible → confirm **auto-advance** to the next (logs); else verify via the
   existing failover path.
5. Series card ▶ → plays the resume/first episode.

**Hardware/TV (owner):** confirm the card→Detail→Play path on D-pad (the ▶ overlay is touch-only).

## 8. Build order (incremental — smoke each)

1. **Movie Detail Netflix hero** (rewrite `MovieDetail`, movie `SourcesSheet` wiring, remove inline list) — the
   headline fix. Smoke: tap poster → hero; Play → stream; Sources → list.
2. **`PlayDirectUseCase`** (movie + series) + `HomeViewModel.playDirect` — smoke via a temporary trigger / logs.
3. **`MediaCard` ▶ overlay** on Home + Search → `playDirect` (+ resolving spinner, terminal toast) — smoke: tap ▶
   on a Top Picks card → streams; tap art → hero; Search results too.

Commit after each; owner merges.

## 9. Decisions

- Movie Detail = **Netflix hero**, reusing `SeriesDetail.Hero` + `SourcesSheet`; **no inline source list**.
- **▶ overlay on every catalog card** (touch) → **direct stream**; **art → Detail**. **TV: card → Detail** (▶ touch-only).
- **Direct-play auto-advances** through eligible sources (`resolveFrom` + `failover`); exhaustion → **transient toast**,
  never a forced list or Detail.
- Reuse the built device-aware auto-pick; **no policy change**.
- Built on **`feat/binge-watch`**; commit locally; owner merges.
