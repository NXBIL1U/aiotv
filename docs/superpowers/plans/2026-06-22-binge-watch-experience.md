# Binge / watch experience — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-play the next episode (Netflix countdown), silently fail over to the next stream source on a playback error, and rank sources by a preferred quality (1080p/4K).

**Architecture:** A `@Singleton` `PlaybackController` holds the now-playing session (ranked candidate list + episode queue); the Player observes it and, on error or end-of-episode, asks the controller to resolve the next source/episode and swaps the ExoPlayer media item (`prepare()`), per the Media3-endorsed pattern (no fallback-URL primitive exists). Quality preference is source-ranking in `StreamRanker`; episode sequencing + `bingeGroup` matching is a pure helper reimplemented from stremio-core (MIT).

**Tech Stack:** Kotlin, Compose Material3, Media3/ExoPlayer 1.5.1, Hilt, DataStore, coroutines. Spec: `docs/superpowers/specs/2026-06-22-binge-watch-experience-design.md`.

## Global Constraints

- Package `com.itrepos.aiotv`. Build: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"; ./gradlew assembleDebug`.
- **Tests: JUnit 4.13.2 + `kotlinx.coroutines.runBlocking` ONLY** (no mockk). `org.junit.Assert.*`. Hand-fake interfaces / extract pure helpers.
- **Failover/next-episode mechanism:** `onPlayerError`/`STATE_ENDED` → resolve next → swap MediaItem + `prepare()`. There is **no Media3 fallback-URL primitive** (ExoPlayer #6422/#4343).
- **Resolve the next episode's URL LATE** (at the countdown) — TorBox debrid URLs are ephemeral.
- **Quality = source-ranking**, NOT `TrackSelectionParameters` (separate URLs per quality). Default **1080p**.
- `CancellationException` must be rethrown (codebase convention), never swallowed.
- **Live IPTV playback path is untouched.** The `Screen.Player` route keeps `url/title/progressId` as the baseline; the controller only enriches VOD plays whose `progressId` matches an active session.
- **Validation: VPN OFF**; emulator `emulator-5554`; restart with `-dns-server 8.8.8.8,8.8.4.4` if a fetch 403s.
- **Git: commit locally to `feat/binge-watch`, never push.** Commit after each task.

---

### Task 1: Quality preference (StreamRanker + DataStore + Settings)

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/domain/StreamRanker.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/data/local/AppDataStore.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailViewModel.kt` (pass preferred quality into `rank`)
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/settings/SettingsScreen.kt` + `SettingsViewModel.kt`
- Create: `app/src/test/java/com/itrepos/aiotv/StreamRankerQualityTest.kt`

**Interfaces:**
- Produces: `StreamRanker.rank(streams: List<Stream>, preferred: Quality? = null): List<Stream>`; `AppDataStore.preferredQuality: Flow<Quality>` + `suspend setPreferredQuality(q: Quality)`.

- [ ] **Step 1: Failing test** `StreamRankerQualityTest.kt`:
```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRankerQualityTest {
    private fun s(t: String, cached: Boolean, q: Quality, lang: Int = 2, seed: Int? = 0) =
        Stream(title = t, url = "u/$t", infoHash = null, fileIdx = null,
            isCached = cached, name = t, quality = q, seeders = seed, languageScore = lang)

    @Test fun prefers1080pOver4kWhenPreferred() {
        val uhd = s("4k", true, Quality.UHD_2160)
        val hd = s("1080", true, Quality.HD_1080)
        assertEquals("1080", StreamRanker.rank(listOf(uhd, hd), Quality.HD_1080).first().title)
    }
    @Test fun prefers4kWhenPreferred() {
        val uhd = s("4k", true, Quality.UHD_2160)
        val hd = s("1080", true, Quality.HD_1080)
        assertEquals("4k", StreamRanker.rank(listOf(hd, uhd), Quality.UHD_2160).first().title)
    }
    @Test fun cachedStillBeatsPreferredUncached() {
        val uncachedPref = s("1080-uncached", false, Quality.HD_1080)
        val cachedOther = s("4k-cached", true, Quality.UHD_2160)
        assertEquals("4k-cached", StreamRanker.rank(listOf(uncachedPref, cachedOther), Quality.HD_1080).first().title)
    }
    @Test fun nullPreferenceKeepsQualityRankOrder() {
        val uhd = s("4k", true, Quality.UHD_2160)
        val hd = s("1080", true, Quality.HD_1080)
        assertEquals("4k", StreamRanker.rank(listOf(hd, uhd), null).first().title)
    }
}
```
- [ ] **Step 2: Run → FAIL** `./gradlew :app:testDebugUnitTest --tests "com.itrepos.aiotv.StreamRankerQualityTest"`.
- [ ] **Step 3: Implement** `StreamRanker.kt`:
```kotlin
object StreamRanker {
    fun rank(streams: List<Stream>, preferred: Quality? = null): List<Stream> =
        streams.sortedWith(
            compareByDescending<Stream> { it.isCached }
                .thenByDescending { it.languageScore }
                .thenByDescending { if (preferred != null && it.quality == preferred) 1 else 0 }
                .thenByDescending { it.quality.rank }
                .thenByDescending { it.seeders ?: -1 }
        )
}
```
(Existing single-arg callers still compile via the default `preferred = null`.)

`AppDataStore.kt` — add key + Flow + setter (mirror `liveRegions`). Map a stored string to `Quality`:
```kotlin
val KEY_PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
// ...
val preferredQuality: Flow<Quality> = dataStore.data.map {
    if (it[KEY_PREFERRED_QUALITY] == "2160p") Quality.UHD_2160 else Quality.HD_1080
}
suspend fun setPreferredQuality(q: Quality) = dataStore.edit {
    it[KEY_PREFERRED_QUALITY] = if (q == Quality.UHD_2160) "2160p" else "1080p"
}
```
(Add `import com.itrepos.aiotv.domain.model.Quality`.)

`DetailViewModel.kt` — read the pref and pass it into both `rank(...)` calls (in `playEpisode` and `loadMovie`). Inject `appDataStore` if not already present; read once per resolve: `val pref = appDataStore.preferredQuality.first()` then `StreamRanker.rank(mapped, pref)`.

`SettingsViewModel.kt` + `SettingsScreen.kt` — add `preferredQuality` to the settings state (collect the Flow like `liveRegions`), a `setPreferredQuality(Quality)` method, and a segmented/FilterChip control labelled **"Preferred quality"** with **1080p** and **4K** options under a "Playback" section (match the existing `FilterChip` region pattern).

- [ ] **Step 4: Run → PASS** (Step 2 command).
- [ ] **Step 5: Build** `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `git commit -am "feat(playback): preferred-quality source ranking + Settings 1080p/4K toggle"`.

---

### Task 2: `BingeSequencing` (pure next-episode + bingeGroup helper)

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/playback/BingeSequencing.kt`
- Create: `app/src/test/java/com/itrepos/aiotv/BingeSequencingTest.kt`

**Interfaces:**
- Consumes: `Episode(id, season, number, …)`.
- Produces: `object BingeSequencing { fun nextEpisode(episodes: List<Episode>, currentId: String): Episode?; fun isBingeMatch(a: String?, b: String?): Boolean }`.

- [ ] **Step 1: Failing test** `BingeSequencingTest.kt`:
```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.playback.BingeSequencing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class BingeSequencingTest {
    private fun ep(s: Int, n: Int) = Episode("tt:$s:$n", s, n, "E$n", null, null, null)
    private val eps = listOf(ep(1,1), ep(1,2), ep(2,1)) // sorted

    @Test fun nextWithinSeason() { assertEquals("tt:1:2", BingeSequencing.nextEpisode(eps, "tt:1:1")!!.id) }
    @Test fun nextCrossesSeasonBoundary() { assertEquals("tt:2:1", BingeSequencing.nextEpisode(eps, "tt:1:2")!!.id) }
    @Test fun lastEpisodeHasNoNext() { assertNull(BingeSequencing.nextEpisode(eps, "tt:2:1")) }
    @Test fun unknownIdHasNoNext() { assertNull(BingeSequencing.nextEpisode(eps, "tt:9:9")) }
    @Test fun doesNotRollFromRealSeasonIntoSpecials() {
        val withSpecials = listOf(ep(0,1), ep(1,1)) // specials sort first
        // last real episode -> nothing after it
        assertNull(BingeSequencing.nextEpisode(withSpecials, "tt:1:1"))
    }
    @Test fun bingeMatch() {
        assertTrue(BingeSequencing.isBingeMatch("torrentio|1080p", "torrentio|1080p"))
        assertFalse(BingeSequencing.isBingeMatch("a", "b"))
        assertFalse(BingeSequencing.isBingeMatch(null, null))
    }
}
```
- [ ] **Step 2: Run → FAIL** `./gradlew :app:testDebugUnitTest --tests "com.itrepos.aiotv.BingeSequencingTest"`.
- [ ] **Step 3: Implement** `BingeSequencing.kt`:
```kotlin
package com.itrepos.aiotv.domain.playback

import com.itrepos.aiotv.domain.model.Episode

object BingeSequencing {
    /** Next episode in the season/number-sorted list; null at the end or if rolling into season-0 specials. */
    fun nextEpisode(episodes: List<Episode>, currentId: String): Episode? {
        val i = episodes.indexOfFirst { it.id == currentId }
        if (i < 0 || i + 1 >= episodes.size) return null
        val next = episodes[i + 1]
        if (episodes[i].season != 0 && next.season == 0) return null
        return next
    }
    /** Stremio behaviour: same non-null bingeGroup string => same release. */
    fun isBingeMatch(a: String?, b: String?): Boolean = a != null && a == b
}
```
- [ ] **Step 4: Run → PASS** (Step 2 command).
- [ ] **Step 5: Commit** `git commit -am "feat(playback): BingeSequencing — next-episode + bingeGroup match (stremio-core MIT, clean-room)"`.

---

### Task 3: `PlaybackController` + Detail wiring + Player failover

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/playback/PlaybackController.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailViewModel.kt` (populate the controller before `onPlay`)
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/player/PlayerScreen.kt` + `PlayerViewModel.kt` (observe controller; failover on error; fail-fast load policy)

**Interfaces:**
- Consumes: `GetStreamsUseCase`, `StreamRanker.rank(streams, preferred)`, `ResolveStreamUseCase`, `TorBoxRepository`, `AppDataStore.preferredQuality`, `BingeSequencing`, `SeriesMeta`, `Episode`, `Stream`.
- Produces:
  - `data class PlaybackState(val currentUrl: String, val title: String, val progressId: String, val upNext: Episode?, val isFailingOver: Boolean = false)`
  - `@Singleton class PlaybackController` with `val state: StateFlow<PlaybackState?>`; `fun startSeries(series: SeriesMeta, episode: Episode, candidates: List<Stream>, chosenUrl: String)`; `fun startMovie(candidates: List<Stream>, chosenUrl: String, title: String, progressId: String)`; `suspend fun failover(): Boolean`; `suspend fun advanceToNextEpisode(): Boolean`; `fun hasSessionFor(progressId: String): Boolean`; `fun clear()`.

**Behaviour:** `startSeries`/`startMovie` store the session (candidates, `currentSourceIndex = 0`, the current stream's `bingeGroup`, for series the `SeriesMeta` + current episode) and emit the initial `PlaybackState` (`upNext` = `BingeSequencing.nextEpisode(series.episodes, episode.id)` for series, else null). `failover()` increments `currentSourceIndex`, resolves the next candidate (url-shape instant; hash-shape `withTimeoutOrNull(20_000)`), emits a new state with the new `currentUrl` (same `progressId`/`title`/`upNext`); returns false when candidates are exhausted. `advanceToNextEpisode()` computes the next episode, `getStreams("series", next.id)`, ranks with `preferredQuality.first()` **and** moves any candidate whose `bingeGroup` equals the session's current bingeGroup to the front, resolves a fresh URL, resets the session (new candidates/index/bingeGroup/episode/`progressId = next.id`/title), recomputes `upNext`, emits state; returns false if nothing resolves. All resolution rethrows `CancellationException`.

- [ ] **Step 1: Implement `PlaybackController.kt`** per the Behaviour above. Extract the index/bingeGroup-ordering as small pure functions where practical. (No unit test for the IO controller; its pure pieces are covered by Tasks 1–2 and it is validated on the emulator in Task 5.)
- [ ] **Step 2: `DetailViewModel`** — in `playEpisode`, after `resolveBestCandidate(ranked)` returns a non-null `url`, call `playbackController.startSeries(series, episode, ranked, url)` **before** `onPlay(url, episodeTitle(episode), episode.id)`. In `loadMovie`/the movie play path, call `playbackController.startMovie(ranked, url, title, progressId)` before navigating. Inject `PlaybackController`.
- [ ] **Step 3: `PlayerScreen`/`PlayerViewModel`** — inject `PlaybackController`; collect `controller.state`. The URL actually played = `state?.takeIf { it.progressId == progressId }?.currentUrl ?: url` (route arg as fallback). Drive the media item from a `LaunchedEffect(playUrl, playProgressId)` that resumes at `lastPositionMs` when `playProgressId` is unchanged (failover) or `viewModel.getStartPosition(playProgressId)` when it changed (new episode); track `lastPositionMs`. In `onPlayerError`: capture `exoPlayer.currentPosition`; if a session is active, set `isFailingOver` UI + `viewModel.failover()` (calls `controller.failover()`) — on `false`, show the existing error + Retry (+ a "Sources" affordance that pops back to Detail). Add a fail-fast `LoadErrorHandlingPolicy` (lower `minLoadableRetryCount`) to the `ExoPlayer.Builder` media-source factory.
- [ ] **Step 4: Build** `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git commit -am "feat(playback): PlaybackController session + silent mid-play failover to next source"`.

> Validated on the emulator in Task 5.

---

### Task 4: Auto-next-episode — Netflix countdown overlay

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/player/PlayerScreen.kt`
- Create: `app/src/main/java/com/itrepos/aiotv/ui/screen/player/UpNextOverlay.kt`

**Interfaces:**
- Consumes: `PlaybackController.state.upNext: Episode?`, `PlaybackController.advanceToNextEpisode()`.
- Produces: `UpNextOverlay(episode, secondsLeft, onPlayNow, onCancel)` composable.

- [ ] **Step 1: `UpNextOverlay.kt`** — a card (bottom-end aligned) showing "Up next: S{season}·E{number} — {name}", "Playing in {secondsLeft}s", and `[Play now]` `[Cancel]` buttons. On TV (`isTv`) make the buttons D-pad-focusable with **Play now** as initial focus (use the existing `FocusRequester` pattern from `DetailScreen`). Red `primary` Play button.
- [ ] **Step 2: Wire into `PlayerScreen`.** Add `onPlaybackStateChanged` handling for `Player.STATE_ENDED`: if `controller.state?.upNext != null`, start a 5→0s countdown (a `LaunchedEffect` with `delay(1000)` loop) and show `UpNextOverlay`. On countdown reaching 0 or **Play now** → `viewModel.advanceToNextEpisode()` (calls `controller.advanceToNextEpisode()`); the state change swaps the media item (Task 3's effect) and the overlay hides. On **Cancel** → hide the overlay (player stays paused at end; Back returns to Detail). If `advanceToNextEpisode()` returns false → show "Couldn't load next episode" + Back. No overlay when `upNext == null` (last episode).
- [ ] **Step 3: Build** `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `git commit -am "feat(player): Netflix-style 'Up next' countdown → auto-advance to next episode"`.

---

### Task 5: End-to-end validation + docs

**Files:** `DESIGN.md`, `TODO.md`

- [ ] **Step 1: Build + install** and validate on the emulator (VPN off; restart with `-dns-server` if a fetch 403s):
  - **Next episode:** play Rick & Morty S1E1; seek near the end (`adb shell input` or let it run) → **"Up next: S1·E2" countdown appears** → at 0 (or Play now) → **S1E2 plays** (logcat shows `…/stream/series/tt2861424:1:2.json`; player title "S1·E2"; resume now keys to E2). **Cancel** dismisses it. Jump to a season's last episode → crosses into next season; series' final episode → **no overlay**.
  - **Quality preference:** set 1080p in Settings → the auto-picked source is 1080p (verify via resolved filename / log, not 4K). Switch to 4K → next pick prefers a 4K source when present.
  - **Failover:** validate the `onPlayerError → failover` path (if a real mid-play error can't be forced, confirm via logs that `failover()` advances the source index; note hardware confirmation).
  - Screenshot the countdown overlay + the Settings quality toggle.
- [ ] **Step 2: Update docs.** `TODO.md`: tick `[P2]` **Auto-next-episode / binge** and `[P2]` **Player-level auto-advance on playback error** (shipped); add the **quality preference** as done (DESIGN decision 8). `DESIGN.md` §6a/§9: note auto-next + failover + quality-preference shipped on `feat/binge-watch`, with the build-vs-adopt verdict (Media3 playlist/transition + app-glue; stremio-core MIT bingeGroup; no Media3 fallback-URL primitive).
- [ ] **Step 3: Commit** `git commit -am "docs: binge/watch (auto-next + failover + quality pref) shipped"`.

---

## Self-Review

**Spec coverage:** §4 units → `PlaybackController` (T3), `BingeSequencing` (T2), `StreamRanker` preferred (T1), `AppDataStore`/Settings (T1), Player wiring (T3/T4). §5 flows → quality (T1), failover (T3), next-episode (T4). §6 UI → `UpNextOverlay` (T4), Settings control (T1), failover indicator (T3). §7 error handling → T3 (exhausted), T4 (resolve fail/last episode), CancellationException (T3). §8 testing → T1/T2 unit + T5 emulator. **All covered.**

**Placeholder scan:** None. T1/T2 carry full code + tests; T3/T4 carry interfaces + behaviour + the concrete Player effect rule (resume-position by progressId-change); the failover manual-test caveat is an honest note, not a deferred requirement.

**Type consistency:** `StreamRanker.rank(streams, preferred=null)` (T1) used by `DetailViewModel` (T1) + `PlaybackController.advanceToNextEpisode` (T3). `BingeSequencing.nextEpisode/isBingeMatch` (T2) used by `PlaybackController` (T3). `PlaybackState`/`PlaybackController` API (T3) consumed by `PlayerScreen` (T3) + `UpNextOverlay` wiring (T4). `AppDataStore.preferredQuality: Flow<Quality>` (T1) consumed by `DetailViewModel` (T1) + `PlaybackController` (T3). Consistent.
