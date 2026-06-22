# Netflix-style Movie Detail + Play-on-Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the movie Detail a Netflix-style hero (no torrent list; Play → direct stream, Sources behind a button) and add a ▶ overlay to every catalog card that streams directly, with tap-art → detail.

**Architecture:** Rewrite `MovieDetail` to mirror `SeriesDetail.Hero` (reusing `SourcesSheet`/`SourcesList` for manual override). Add a `PlayDirectUseCase` that resolves+auto-picks a movie or series straight from a `MediaItem` (reusing the device-aware ranker + `PlaybackController`). `MediaCard` gains a touch-only ▶ overlay wired through `HomeViewModel`/`SearchViewModel.playDirect` to the player.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Media3 1.5.1, Coil, JUnit4 (no mockk).

## Global Constraints

- Build directly on **`feat/binge-watch`**; commit after each task; owner merges.
- **No new dependencies.** Reuse `SeriesDetail.Hero` pattern, `SourcesSheet`/`SourcesList`, `MediaCard`, `PlaybackController`, `StreamRanker`, `DeviceCapabilities`.
- **No inline source list** in the default movie Detail; the full list lives behind a **Sources** button (sheet on phone, list on TV) — same as series.
- **▶ overlay is touch-only** (`pointerInput`/`detectTapGestures`, NOT `clickable`, so it is not D-pad focusable). **TV card center-press → Detail.**
- **Direct-play auto-advances** through eligible sources (existing `resolveFrom` + `failover`); total exhaustion → a transient **Toast** "Couldn't find a playable source" — no Detail/list fallback.
- Pure logic is **TDD (JUnit4, no mockk)** in `app/src/test/java/com/itrepos/aiotv/`. UI + IO orchestration is validated by **emulator smoke (VPN off)**, phone emulator `emulator-5554`.
- Build env: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"`. Build `./gradlew assembleDebug`; tests `./gradlew testDebugUnitTest`.
- Subagents run in **this workspace (no isolation)** → commits land on `feat/binge-watch`. Verify HEAD advances.

---

### Task 1: Movie Detail → Netflix hero (the headline fix)

**Files:**
- Rewrite: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/MovieDetail.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailViewModel.kt` (`DetailState`, `showMovieSources()`/`dismissMovieSources()`)
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailScreen.kt` (movie hero wiring + movie Sources sheet)
- Test: none (UI — emulator smoke)

**Interfaces:**
- Consumes: `DetailViewModel.playMovieAuto` (built), `resolveStream(stream){onResolved}` (existing), `SourcesSheet`/`SourcesList`, `MediaItem`, `AccentPrimary`, `SurfaceElevated`.
- Produces: `MovieDetail(state, fallbackTitle, onPlayAuto, onShowSources, onBack)`; `DetailState.showMovieSources`; `DetailViewModel.showMovieSources()` / `dismissMovieSources()`.

- [ ] **Step 1: Add movie Sources state to `DetailViewModel`**

In `DetailState` add (after `resolving`):
```kotlin
    val showMovieSources: Boolean = false,
```
Add methods (near `showSources`):
```kotlin
    /** Movie Sources sheet (manual override / auto-pick-fail fallback). */
    fun showMovieSources() { _state.value = _state.value.copy(showMovieSources = true) }
    fun dismissMovieSources() { _state.value = _state.value.copy(showMovieSources = false) }
```

- [ ] **Step 2: Rewrite `MovieDetail.kt` as a Netflix hero**

Replace the entire file with:
```kotlin
package com.itrepos.aiotv.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceElevated

/**
 * Netflix-style movie detail: hero backdrop + scrim, title/meta, Play (auto-pick → direct stream) and
 * a Sources button (manual override). No inline torrent list — single scrolling column on all widths.
 */
@Composable
fun MovieDetail(
    state: DetailState,
    fallbackTitle: String,
    onPlayAuto: () -> Unit,
    onShowSources: () -> Unit,
    onBack: () -> Unit,
) {
    val item = state.meta
    val hasStreams = state.streams.isNotEmpty()
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(hasStreams) {
        if (hasStreams) runCatching { playFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            val fallback = ColorPainter(SurfaceElevated)
            AsyncImage(
                model = item?.backdropUrl ?: item?.posterUrl,
                contentDescription = item?.name,
                contentScale = ContentScale.Crop,
                placeholder = fallback,
                error = fallback,
                fallback = fallback,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.4f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.95f),
                    ),
                ),
            )
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth()) {
                Text(
                    text = item?.name ?: fallbackTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                movieMetaLine(item)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPlayAuto,
                    enabled = !state.resolving && hasStreams,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    modifier = Modifier.focusRequester(playFocus),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                OutlinedButton(onClick = onShowSources, enabled = hasStreams) { Text("Sources") }
            }
            ErrorAndResolving(error = state.error, resolving = state.resolving)
            item?.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun movieMetaLine(item: MediaItem?): String? {
    if (item == null) return null
    val parts = listOfNotNull(item.year?.toString(), item.genres.firstOrNull(), item.imdbRating?.let { "★ $it" })
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

@Composable
private fun ErrorAndResolving(error: String?, resolving: Boolean) {
    if (error != null) {
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
    if (resolving) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(
                "Preparing stream…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
```
(This deletes the old `StreamsList`/`StreamRow`/`streamKey`/`DetailHeader` — they were only used by the old movie list. `SeriesDetail` has its own `Hero`/back button and does not depend on them.)

- [ ] **Step 3: Wire the movie hero + Sources sheet in `DetailScreen.kt`**

Replace the `DetailKind.MOVIE -> MovieDetail(...)` block with the hero wiring + a movie Sources sheet (mirrors the series block):
```kotlin
        DetailKind.MOVIE -> {
            MovieDetail(
                state = state,
                fallbackTitle = id,
                onPlayAuto = { viewModel.playMovieAuto(onPlayStream) },
                onShowSources = { viewModel.showMovieSources() },
                onBack = onBack,
            )
            if (state.showMovieSources) {
                val pick: (com.itrepos.aiotv.domain.model.Stream) -> Unit = { stream ->
                    viewModel.dismissMovieSources()
                    viewModel.resolveStream(stream) { url ->
                        onPlayStream(url, state.meta?.name ?: id, url)
                    }
                }
                if (isTv) {
                    SourcesList(streams = state.streams, onPick = pick)
                } else {
                    SourcesSheet(streams = state.streams, onPick = pick, onDismiss = { viewModel.dismissMovieSources() })
                }
            }
        }
```

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug` → `BUILD SUCCESSFUL`. (Remove any now-unused imports the compiler flags as errors; warnings are fine.)

- [ ] **Step 5: Emulator smoke**

```bash
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
# open a movie poster's art → expect a Netflix hero (backdrop, Play, Sources), NO torrent list
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 exec-out screencap -p > /tmp/movie_hero.png
```
Confirm: tap poster → hero (backdrop + Play + Sources, no list); **Play** → streams; **Sources** → sheet with the list; pick one → plays.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/itrepos/aiotv/ui/screen/detail/MovieDetail.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailViewModel.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailScreen.kt
git commit -m "feat(detail): Netflix-style movie hero; sources behind a button (no inline list)"
```

---

### Task 2: `PlayDirectUseCase` + resume helper + `HomeViewModel.playDirect`

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/usecase/PlayDirectUseCase.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/itrepos/aiotv/DirectPlaySequencingTest.kt`

**Interfaces:**
- Consumes: `MetaRepository.getSeriesMeta`, `GetStreamsUseCase`, `TorBoxRepository.checkCached`, `AppDataStore.preferredQuality`, `DeviceCapabilities.profile`, `PlaybackController.startMovieAuto`/`startSeries`, `StreamRanker.rank`/`isAutoEligible`, `WatchProgressStore.getAllProgress`, `MediaItem`, `Episode`, `SeriesMeta`, `Stream`.
- Produces: `data class DirectPlay(url, title, progressId)`; `PlayDirectUseCase(item): DirectPlay?`; `DirectPlaySequencing.resumeEpisode(episodes, inProgressIds): Episode?`; `HomeUiState.resolvingId`/`directError`; `HomeViewModel.playDirect(item, onResolved)`.

- [ ] **Step 1: Write the failing test for the resume helper**

`DirectPlaySequencingTest.kt`:
```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.usecase.DirectPlaySequencing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectPlaySequencingTest {
    private fun ep(id: String, s: Int, n: Int) = Episode(id = id, season = s, number = n, name = "E$n")
    private val eps = listOf(ep("tt:1:1", 1, 1), ep("tt:1:2", 1, 2), ep("tt:1:3", 1, 3))

    @Test fun picksFirstInProgressEpisode() {
        assertEquals("tt:1:3", DirectPlaySequencing.resumeEpisode(eps, setOf("tt:1:3"))!!.id)
    }
    @Test fun fallsBackToFirstEpisode() {
        assertEquals("tt:1:1", DirectPlaySequencing.resumeEpisode(eps, emptySet())!!.id)
    }
    @Test fun nullWhenNoEpisodes() {
        assertNull(DirectPlaySequencing.resumeEpisode(emptyList(), setOf("x")))
    }
}
```
(Confirm `Episode`'s constructor params with `app/src/main/java/com/itrepos/aiotv/domain/model/Episode.kt`; adjust the `ep(...)` factory if field names differ.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.itrepos.aiotv.DirectPlaySequencingTest"`
Expected: FAIL — `DirectPlaySequencing` unresolved.

- [ ] **Step 3: Create `PlayDirectUseCase.kt` (helper + use case)**

```kotlin
package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.local.WatchProgressStore
import com.itrepos.aiotv.data.repository.MetaRepository
import com.itrepos.aiotv.data.repository.TorBoxRepository
import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.playback.DeviceCapabilities
import com.itrepos.aiotv.domain.playback.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DirectPlay(val url: String, val title: String, val progressId: String)

/** Pure: which episode a "play series" action should start. */
object DirectPlaySequencing {
    fun resumeEpisode(episodes: List<Episode>, inProgressIds: Set<String>): Episode? =
        episodes.firstOrNull { it.id in inProgressIds } ?: episodes.firstOrNull()
}

/**
 * Resolve + auto-pick a playable source straight from a catalog [MediaItem] (no detail screen).
 * Movie → auto-pick its best eligible source. Series → resume/first episode's best source.
 * Returns null when nothing eligible resolves (all sources exhausted). Starts a [PlaybackController]
 * session so the Player follows it (failover / next-episode keep working).
 */
class PlayDirectUseCase @Inject constructor(
    private val metaRepo: MetaRepository,
    private val getStreams: GetStreamsUseCase,
    private val torBoxRepo: TorBoxRepository,
    private val appDataStore: AppDataStore,
    private val deviceCapabilities: DeviceCapabilities,
    private val playbackController: PlaybackController,
    private val watchProgressStore: WatchProgressStore,
) {
    suspend operator fun invoke(item: MediaItem): DirectPlay? {
        playbackController.clear()
        return if (item.type == "series") playSeries(item) else playMovie(item)
    }

    private suspend fun playMovie(item: MediaItem): DirectPlay? {
        val auto = rankAuto(getStreamsOrNull(item.type, item.id) ?: return null)
        if (auto.isEmpty()) return null
        if (!playbackController.startMovieAuto(auto, item.name, item.id)) return null
        return DirectPlay(playbackController.state.value!!.currentUrl, item.name, item.id)
    }

    private suspend fun playSeries(item: MediaItem): DirectPlay? {
        val series = metaRepo.getSeriesMeta(item.id) ?: return null
        val inProgress = runCatching { watchProgressStore.getAllProgress().first() }
            .getOrDefault(emptyList())
            .filter { it.fraction in 0.001f..0.999f }
            .map { it.id }.toSet()
        val ep = DirectPlaySequencing.resumeEpisode(series.episodes, inProgress) ?: return null
        val auto = rankAuto(getStreamsOrNull("series", ep.id) ?: return null)
        if (auto.isEmpty()) return null
        if (!playbackController.startSeries(series, ep, auto)) return null
        val title = "${series.item.name} S${ep.season}·E${ep.number}"
        return DirectPlay(playbackController.state.value!!.currentUrl, title, ep.id)
    }

    private suspend fun getStreamsOrNull(type: String, id: String): List<Stream>? = try {
        getStreams(type, id)
    } catch (c: CancellationException) {
        throw c
    } catch (_: Exception) {
        null
    }

    private suspend fun rankAuto(raw: List<Stream>): List<Stream> {
        val hashes = raw.mapNotNull { it.infoHash }
        val cached = if (hashes.isNotEmpty()) {
            runCatching { torBoxRepo.checkCached(hashes) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        val withCached = raw.map { s -> s.copy(isCached = s.isCached || cached[s.infoHash?.lowercase()] == true) }
        val pref = appDataStore.preferredQuality.first()
        val profile = deviceCapabilities.profile
        val target = if (pref.rank <= profile.maxResolution.rank) pref else profile.maxResolution
        return StreamRanker.rank(withCached, profile, target).filter { StreamRanker.isAutoEligible(it, profile) }
    }
}
```

- [ ] **Step 4: Run the helper test + build**

Run: `./gradlew testDebugUnitTest --tests "com.itrepos.aiotv.DirectPlaySequencingTest"` → PASS.
Run: `./gradlew assembleDebug` → `BUILD SUCCESSFUL`. (Fix any signature mismatch against the real `MetaRepository`/`TorBoxRepository`/`Episode`.)

- [ ] **Step 5: Add `playDirect` to `HomeViewModel`**

Add to `HomeUiState`:
```kotlin
    val resolvingId: String? = null,
    val directError: String? = null,
```
Inject the use case (constructor): `private val playDirectUseCase: PlayDirectUseCase,`. Add:
```kotlin
    /** ▶-on-card: resolve + auto-pick a source and hand the player route back via [onResolved]. */
    fun playDirect(item: MediaItem, onResolved: (url: String, title: String, progressId: String) -> Unit) {
        if (_uiState.value.resolvingId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingId = item.id, directError = null)
            val result = playDirectUseCase(item)
            _uiState.value = _uiState.value.copy(resolvingId = null)
            if (result != null) onResolved(result.url, result.title, result.progressId)
            else _uiState.value = _uiState.value.copy(directError = "Couldn't find a playable source")
        }
    }
    fun clearDirectError() { _uiState.value = _uiState.value.copy(directError = null) }
```
Add import `import com.itrepos.aiotv.domain.usecase.PlayDirectUseCase`.

- [ ] **Step 6: Build + commit**
```bash
./gradlew testDebugUnitTest assembleDebug   # all green
git add app/src/main/java/com/itrepos/aiotv/domain/usecase/PlayDirectUseCase.kt \
        app/src/test/java/com/itrepos/aiotv/DirectPlaySequencingTest.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/home/HomeViewModel.kt
git commit -m "feat(playback): PlayDirectUseCase — resolve+auto-pick a movie/series from a card"
```

---

### Task 3: `MediaCard` ▶ overlay + Home & Search wiring

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/components/MediaCard.kt` (▶ overlay + spinner)
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/home/HomeScreen.kt` (movie + series cards → `playDirect`; Toast)
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/search/SearchViewModel.kt` (`playDirect`) and `SearchScreen.kt` (cards → `playDirect`; Toast)
- Test: none (UI — emulator smoke)

**Interfaces:**
- Consumes: `HomeViewModel.playDirect`/`uiState.resolvingId`/`directError`, `PlayDirectUseCase`, `Screen.Player.createRoute`.
- Produces: `MediaCard(..., onPlay: (() -> Unit)? = null, isResolving: Boolean = false)`; `SearchViewModel.playDirect` + `SearchState.resolvingId`/`directError`.

- [ ] **Step 1: Add the ▶ overlay to `MediaCard`**

Add two params to `MediaCard` (after `progress`):
```kotlin
    onPlay: (() -> Unit)? = null,
    isResolving: Boolean = false,
```
Inside the `Box(Modifier.aspectRatio(aspectRatio))`, after the `progress?.let { … }` block, add the touch-only ▶ (uses `pointerInput` so it is NOT D-pad focusable → TV center-press still hits the card's `onClick`):
```kotlin
                if (onPlay != null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
                            .pointerInput(onPlay) { detectTapGestures { onPlay() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isResolving) {
                            androidx.compose.material3.CircularProgressIndicator(
                                Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp,
                            )
                        } else {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                            )
                        }
                    }
                }
```
Add imports: `androidx.compose.foundation.gestures.detectTapGestures`, `androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.foundation.layout.size`, `androidx.compose.material.icons.filled.PlayArrow`.

- [ ] **Step 2: Wire Home movie + series cards to `playDirect`**

In `HomeScreen.kt`, the movie rail `MediaCard` — add `onPlay` + `isResolving`:
```kotlin
                        MediaCard(
                            title = item.name,
                            imageUrl = item.posterUrl,
                            modifier = focusMod(firstRail == "movies" && item == movies.first()),
                            onClick = { onNavigate(Screen.Detail.createRoute(item.type, item.id)) },
                            onPlay = {
                                viewModel.playDirect(item) { url, t, pid ->
                                    onNavigate(Screen.Player.createRoute(url, t, pid))
                                }
                            },
                            isResolving = state.resolvingId == item.id,
                        )
```
Do the same for the **series** rail `MediaCard` (the `series` block just below it). Leave the **Continue Watching** card unchanged (it already resumes on tap).

- [ ] **Step 3: Show the terminal Toast on Home**

Near the top of `HomeScreen`'s body (after `val state by viewModel.uiState.collectAsState()`), add:
```kotlin
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.directError) {
        state.directError?.let {
            android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearDirectError()
        }
    }
```
(Ensure `LaunchedEffect` is imported: `androidx.compose.runtime.LaunchedEffect`.)

- [ ] **Step 4: Mirror `playDirect` in `SearchViewModel`**

Add to `SearchState`:
```kotlin
    val resolvingId: String? = null,
    val directError: String? = null,
```
Inject `private val playDirectUseCase: PlayDirectUseCase,` and add the same `playDirect(item, onResolved)` + `clearDirectError()` as Home (operate on `_state`). Add import `import com.itrepos.aiotv.domain.usecase.PlayDirectUseCase`.

- [ ] **Step 5: Wire Search result cards + Toast**

In `SearchScreen.kt`, each media-result `MediaCard` (lines ~133 and ~163) — add:
```kotlin
                                    onPlay = {
                                        viewModel.playDirect(item) { url, t, pid ->
                                            onNavigate(Screen.Player.createRoute(url, t, pid))
                                        }
                                    },
                                    isResolving = state.resolvingId == item.id,
```
Add the same Toast `LaunchedEffect(state.directError)` block as Home near the top of `SearchScreen`. Add `Screen` import if missing.

- [ ] **Step 6: Build + full unit suite**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS + `BUILD SUCCESSFUL`.

- [ ] **Step 7: Emulator smoke (VPN off)**

```bash
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 logcat -c
# Home: tap the ▶ on a Top Picks movie card → spinner → player (no list). Tap the art → hero.
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 exec-out screencap -p > /tmp/card_play.png
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 logcat -d | grep -iE "DeviceCapabilities|ExoPlaybackException" | tail
```
Confirm: ▶ → resolves → streams (no list); art → hero; a series card ▶ → plays the resume/first episode; Search results behave the same.

- [ ] **Step 8: Commit**
```bash
git add app/src/main/java/com/itrepos/aiotv/ui/components/MediaCard.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/home/HomeScreen.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/search/SearchViewModel.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/search/SearchScreen.kt
git commit -m "feat(home/search): ▶-on-card direct play (touch); tap art opens Netflix detail"
```

---

## Self-Review

**Spec coverage:**
- §3 Movie Detail Netflix hero, no inline list → Task 1. ✅
- §3 movie Sources sheet behind a button → Task 1 Step 3. ✅
- §3 `PlayDirectUseCase` (movie + series, resume/first episode) → Task 2. ✅
- §3 `MediaCard` ▶ overlay (touch-only via `pointerInput`) → Task 3 Step 1. ✅
- §3 Home + Search wiring + resolving spinner + terminal Toast → Task 3 Steps 2–5. ✅
- §4 interaction model (touch ▶→stream/art→detail; TV card→detail) → Task 3 (`pointerInput` keeps ▶ off the focus order). ✅
- §5/§6 auto-advance (reuse `resolveFrom`/`failover`) + exhaustion Toast → Task 2 (returns null) + Task 3 (Toast). ✅
- §7 resume-episode pure test → Task 2 Step 1. ✅

**Placeholder scan:** No TBD/"handle errors". One verification note (confirm `Episode` ctor field names in Task 2 Step 1) — that is a real instruction, not a placeholder. ✅

**Type consistency:** `DirectPlay(url,title,progressId)` + `PlayDirectUseCase(item): DirectPlay?` + `DirectPlaySequencing.resumeEpisode(episodes, inProgressIds)` defined Task 2, consumed Task 3; `MediaCard(..., onPlay, isResolving)` defined Task 3 Step 1, used Steps 2/5; `HomeViewModel.playDirect`/`uiState.resolvingId`/`directError` defined Task 2/3, used Task 3; `MovieDetail(state, fallbackTitle, onPlayAuto, onShowSources, onBack)` defined Task 1 Step 2, used Step 3 (drops the old `onPlayStream` param). ✅
