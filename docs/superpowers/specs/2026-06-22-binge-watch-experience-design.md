# Binge / watch experience — auto-next-episode + failover + quality preference (design)

Date: 2026-06-22 · Branch: `feat/binge-watch` (off `main`) · Status: approved-to-spec

## 1. Problem & goal

Complete Phase 2's "binge a series end-to-end" goal (DESIGN §6a). Today the Player is a **dumb,
single-URL screen**: an error shows a manual **Retry** (no auto-advance), there is **no
end-of-episode handling**, and the **ranked candidate list + episode queue are discarded** once
`DetailViewModel` picks one URL and navigates. Add:

1. **Auto-play next episode** at end-of-episode — Netflix-style "Up next in 5s" countdown.
2. **Failover** — on a mid-play ExoPlayer error, **silently advance to the next candidate source**
   for the same episode (we already hold the ranked list).
3. **Quality preference** — default **1080p**, optional **4K**, influencing which source we pick.

## 2. Goals / Non-goals

**Goals:** the three features above, behind a small **`PlaybackController`** session holder; live
channels unaffected; per-episode resume preserved (it re-keys to the new episode on advance).

**Non-goals:** in-stream audio/subtitle **track-selection UI** (separate later item);
`TrackSelectionParameters`-based quality (not applicable — see §3); pre-buffering the whole season;
Trakt sync; any change to the live-IPTV playback path.

## 3. Build-vs-adopt (validated by web research, 2026-06-22)

**Verdict: no turnkey library fits our debrid/multi-URL case; the "thing we rip" is Media3 itself
plus a little MIT-licensed Stremio logic. The custom code is thin glue.**

| Feature | Source of the solution |
|---|---|
| Auto-next-episode | **Media3 playlist/transition gives the advance for free** (`onMediaItemTransition`), but the **countdown overlay is app-built everywhere** (no official sample). Episode sequencing + the `bingeGroup` "same-release next source" trick: reimplement from **stremio-core (MIT)** — ~3 tiny functions, reuse its test vectors. |
| Failover on error | **No Media3 primitive** — ExoPlayer maintainers explicitly declined fallback-URL support ([#6422](https://github.com/google/ExoPlayer/issues/6422), [#4343](https://github.com/google/ExoPlayer/issues/4343)); the endorsed pattern is `onPlayerError → swap MediaItem → prepare()`. Reference: CloudStream `GeneratorPlayer` (GPL → **patterns only**). |
| Quality preference | **Source-ranking, NOT track selection.** `TrackSelectionParameters` picks tracks *within one stream*; our qualities are **separate URLs/torrents**, so it does not apply. Rank candidates by preferred quality. |

**Ephemeral-URL gotcha (key finding):** Media3's seamless auto-advance *pre-buffers* the next item's
URL early. **TorBox debrid URLs expire**, so a pre-enqueued next-episode URL would be dead by
transition. → We **resolve the next episode's URL late** (at the countdown), then swap the media
item. The same `onError/onEnded → resolve → swap MediaItem → prepare()` mechanism serves both
failover and next-episode.

**Licenses:** borrow logic from **stremio-core (MIT)** and **Aniyomi (Apache-2.0, resolve-and-
fallback orchestration)**; **CloudStream / NextPlayer (GPL)** are patterns-only — no GPL code copied.

## 4. Architecture

| Unit | Responsibility |
|---|---|
| `PlaybackController` (new, `@Singleton`, `domain/playback`) | The **now-playing session orchestrator**. Injects `GetStreamsUseCase`, `StreamRanker`, `ResolveStreamUseCase`, `TorBoxRepository`, `AppDataStore`. Holds a `PlaybackSession` and exposes `state: StateFlow<PlaybackUiState>`. |
| `PlaybackSession` (data) | `title`, `currentUrl`, `progressId`, `candidates: List<Stream>` (ranked), `currentSourceIndex`, `series: SeriesMeta?`, `episodeIndex: Int?`, `currentBingeGroup: String?`, `upNext: Episode?`. |
| `BingeSequencing` (new, pure, `domain/playback`) | `nextEpisode(episodes, currentIndex): Episode?` (skip season-0 specials), `isBingeMatch(a: String?, b: String?): Boolean` (string equality). Clean-room from stremio-core (MIT). |
| `StreamRanker.rank(streams, preferred: Quality?)` (modify) | Add an optional preferred-quality tiebreaker, **below** cached + languageScore, **above** `quality.rank` (a cached English 1080p still beats an uncached 4K; among equals the preferred quality wins). `preferred = null` → current behavior. |
| `AppDataStore` (modify) | `KEY_PREFERRED_QUALITY` (string `"1080p"`/`"2160p"`, default `"1080p"`) + `preferredQuality: Flow<Quality>` + `setPreferredQuality(Quality)`. |
| `PlayerScreen` / `PlayerViewModel` (modify) | Observe `controller.state`. `onPlayerError` → `controller.failover()` (silent; `null` → existing error+Retry). `STATE_ENDED` → if `upNext != null`, show countdown overlay. Set a fail-fast `LoadErrorHandlingPolicy` on the ExoPlayer. |
| `DetailViewModel` (modify) | After resolving the first candidate in `playEpisode`/`loadMovie`, call `controller.startSeries(...)` / `controller.startMovie(...)` before invoking `onPlay`. Pass the preferred quality into `StreamRanker.rank`. |
| `SettingsScreen` / `SettingsViewModel` (modify) | A segmented **"Preferred quality [1080p | 4K]"** control under a Playback section (wired like `liveRegions`). |

**`PlaybackController` methods:** `startSeries(series, episode, candidates, chosenUrl)`,
`startMovie(candidates, chosenUrl, title, progressId)`, `suspend failover(): String?` (advance
`currentSourceIndex`, resolve next candidate, update state; `null` when exhausted),
`suspend advanceToNextEpisode(): Boolean` (compute next ep via `BingeSequencing`, `getStreams`,
`rank` with quality-pref **and** bingeGroup-prefer matching `currentBingeGroup`, resolve **fresh**,
reset session + `upNext`, update state), `cancelAutoNext()`, `clear()`.

**Player source of truth:** the `Screen.Player` route still carries `url/title/progressId` as the
**baseline** (live channels, direct plays, and the process-death fallback). For VOD, `DetailViewModel`
populates the controller keyed by the same `progressId`; `PlayerScreen` uses the controller session
when it matches, enabling failover + next-episode. Live IPTV is untouched.

## 5. Data flow

**Quality:** Settings → `setPreferredQuality` → DataStore → `DetailViewModel` reads
`preferredQuality.first()` → `StreamRanker.rank(streams, preferred)` → ranked candidates (used for
both the first pick and failover).

**Failover (error → next source):**
```
Player.onPlayerError → controller.failover()
  → currentSourceIndex++ ; resolve candidates[idx] (url-shape instant; hash-shape withTimeout)
  → if url: state.currentUrl = url → Player: replaceMediaItem(idx, url) + prepare() + seekTo(pos) + play  [silent, brief "Trying another source…"]
  → if exhausted (null): Player shows error + Retry + "Sources" (back to Detail sheet)
```

**Next-episode (Netflix countdown):**
```
Player.STATE_ENDED → upNext != null ? show countdown card (5s, [Play now] [Cancel])
  → on fire/Play now: controller.advanceToNextEpisode()
       → next = BingeSequencing.nextEpisode(series.episodes, episodeIndex)   (null → last episode → no-op)
       → getStreams("series", next.id) → rank(streams, preferredQuality)
         with a bingeGroup-preference: a candidate whose bingeGroup == currentBingeGroup sorts first
       → resolveBestCandidate(ranked)   (fresh URL, resolved LATE)
       → reset session (candidates, index, currentBingeGroup, episodeIndex, progressId=next.id, title)
       → Player: setMediaItem(newUrl, startPos=0) + prepare() + play
  → on Cancel: dismiss overlay (player stays at end; Back returns to Detail)
  → resolve fail: "Couldn't load next episode" + Back
```

## 6. UI

- **Countdown overlay** — a card (bottom area) "Up next: S1·E2 — *Purple Giraffe*", "Playing in `N`s",
  `[Play now]` `[Cancel]`. Appears on `STATE_ENDED`; counts 5→0 then auto-advances; D-pad focusable
  with **Play now** as initial focus on TV. Hidden when there is no next episode.
- **Failover indicator** — a brief, transient "Trying another source…" line; otherwise silent.
- **Settings** — a segmented control (FilterChip/SegmentedButton) "Preferred quality: 1080p / 4K"
  under a Playback section.

## 7. Error handling

- Failover exhausted → existing error + **Retry**, plus a **Sources** affordance (re-open Detail's
  sheet) so the user can pick manually.
- Next-episode resolve failure → "Couldn't load next episode" + Back; **last episode** → no overlay.
- Process death → controller session is empty → the baseline `url` plays (no failover/next until the
  flow is re-entered). Acceptable degradation.
- All resolution on IO dispatchers; `CancellationException` is **rethrown** (codebase convention), not
  swallowed.

## 8. Testing

**Unit (JUnit4 + `runBlocking`; hand-fake interfaces / extract pure helpers — no mockk):**
- `BingeSequencing.nextEpisode`: returns the next by (season, number); **last episode → null**; a
  current episode followed only by season-0 specials → null (don't auto-roll into specials).
  `isBingeMatch`: equal non-null strings match; null/empty don't.
- `StreamRanker.rank(streams, preferred)`: 1080p-preferred ranks a 1080p above a 4K (both cached
  English); 4K-preferred reverses it; **cached still beats uncached** regardless of preference;
  `preferred = null` reproduces the current order.
- `PlaybackController` index logic via extracted pure helpers: failover advances the source index and
  stops at exhaustion; next-episode picks the right episode and prefers a matching `bingeGroup`.

**Manual on emulator (VPN off; restart with `-dns-server` if a fetch 403s):**
- Play Rick & Morty S1E1 → let it end (or seek near the end) → **countdown appears** → **S1E2 plays**
  with a new `progressId` (resume now keys to E2). **Cancel** dismisses it. Last episode → no card.
- **Quality pref = 1080p** → the auto-picked source is 1080p (verify via the resolved filename / log,
  not 4K); switch to 4K in Settings → next pick prefers 4K.
- **Failover**: force a bad first candidate if feasible (e.g. a deliberately broken first URL in a
  test build) to confirm the silent advance; otherwise validate the `onPlayerError → failover` path
  by logs and note hardware confirmation.

## 9. Decisions

- Next-episode = **Netflix countdown (5 s)**; resolve the next URL **late** (ephemeral debrid URLs).
- Quality = **source-ranking** via `StreamRanker` (default 1080p Settings toggle); **per-play override
  = the existing Sources sheet** (no new player chrome). **No `TrackSelectionParameters`** (N/A).
- Mechanism for failover + next-episode = `onPlayerError`/`STATE_ENDED` → resolve next → **swap
  MediaItem + `prepare()`** (Media3-endorsed; there is no fallback-URL primitive). Fail-fast
  `LoadErrorHandlingPolicy`.
- **`bingeGroup`-preferred** next-episode source (stremio-core MIT, clean-room).
- `PlaybackController` is `@Singleton`; **live channels unaffected**; baseline `url`-route retained as
  fallback.
- **Build order: failover → quality ranking → autoplay-next** (purest app code + highest value first;
  each feeds the next).
- Borrow logic from **stremio-core (MIT)** + **Aniyomi (Apache-2.0)**; **CloudStream/NextPlayer (GPL)**
  patterns-only — no GPL code copied.
- Git: branch `feat/binge-watch` off `main`; commit locally; owner merges.
