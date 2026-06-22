# Search VOD-only + Cinemeta search + Home channel-strip — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the main Search return real movies + series from a Cinemeta search index (no channels), and remove live channels from Home.

**Architecture:** A new `SearchRepository` queries Cinemeta's search catalog (`catalog/<type>/top/search=<q>.json`) for `movie` + `series` with host fallback, returning IMDb-id `MediaItem`s that the existing Detail→Torrentio pipeline already resolves. `SearchViewModel` drops its channel query; `SearchScreen` drops the channels section. `HomeViewModel`/`HomeScreen` drop the channel load + "Live Now" rail. Live TV is untouched.

**Tech Stack:** Kotlin, Hilt, Retrofit (`@GET @Url`) + kotlinx.serialization, Jetpack Compose Material3, coroutines `Flow`. Spec: `docs/superpowers/specs/2026-06-22-search-vod-only-design.md`.

## Global Constraints

- Package `com.itrepos.aiotv`. Build: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"; ./gradlew assembleDebug`.
- **Tests: JUnit 4.13.2 ONLY** (no mockk/coroutines-test). Use `org.junit.Assert.*`; suspend funcs via `kotlinx.coroutines.runBlocking`; the only fakeable collaborator is the `StremioApi` interface (hand-fake it, as in `MetaRepositoryFallbackTest`).
- **Cinemeta hosts** (already defined in `StremioApi.kt`): `CINEMETA_HOSTS = ["https://cinemeta-live.strem.io", "https://v3-cinemeta.strem.fun"]`; try in order, first responder wins.
- **Cinemeta search URL:** `<host>/catalog/<type>/top/search=<urlEncoded query>.json`. Encode spaces as `%20` (use `URLEncoder.encode(q,"UTF-8").replace("+","%20")`).
- Search returns **movie + series combined, deduped by id**; routes to `Screen.Detail.createRoute(item.type, item.id)`.
- Keep the existing **400 ms debounce** + `q.length >= 2` gate in `SearchViewModel`.
- **Validation: VPN OFF** (Cinemeta/Torrentio 403 on the VPN IP). Emulator `emulator-5554`; restart with `-dns-server 8.8.8.8,8.8.4.4` if a fetch 403s/UnknownHosts.
- **Git: commit locally to `feat/search-vod-home`, never push.** Commit after each task.
- Do NOT change `GetChannelsUseCase` / `LiveTvRepository` / the Live TV screen — channel search stays there.

---

### Task 1: Cinemeta `SearchRepository` + `searchUrl` + `SearchVodUseCase`

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/data/remote/stremio/StremioApi.kt` (add `searchUrl` + a top-level `fetchSearchFromHosts`)
- Create: `app/src/main/java/com/itrepos/aiotv/data/repository/SearchRepository.kt`
- Create: `app/src/main/java/com/itrepos/aiotv/domain/usecase/SearchVodUseCase.kt`
- Create: `app/src/test/java/com/itrepos/aiotv/SearchRepositoryTest.kt`

**Interfaces:**
- Consumes: `StremioApi.getCatalog(@Url): StremioCatalogResponse` (has `.metas: List<StremioMeta>`); `CINEMETA_HOSTS`; `StremioMeta(id,type,name,description,poster,background,year,genres,imdbRating)`; `MediaItem(id,type,name,description,posterUrl,backdropUrl,year,genres,imdbRating)`.
- Produces:
  - `fun searchUrl(baseUrl: String, type: String, query: String): String`
  - `suspend fun fetchSearchFromHosts(hosts: List<String>, type: String, query: String, fetch: suspend (url: String) -> List<StremioMeta>): List<StremioMeta>?` — null iff NO host could be reached; else the (possibly empty) metas from the first responder.
  - `class SearchRepository @Inject constructor(stremioApi)` with `suspend fun search(query: String): List<MediaItem>` — queries `movie`+`series`, maps→`MediaItem`, dedups by id; throws `IOException("Search unavailable")` iff Cinemeta is unreachable for BOTH types.
  - `class SearchVodUseCase @Inject constructor(repo)` with `suspend operator fun invoke(query: String): List<MediaItem>`.

- [ ] **Step 1: Write the failing test** `SearchRepositoryTest.kt`:

```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.data.remote.stremio.CINEMETA_HOSTS
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.stremio.StremioCatalogResponse
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.StremioMetaResponse
import com.itrepos.aiotv.data.remote.stremio.StremioStreamResponse
import com.itrepos.aiotv.data.remote.stremio.StremioManifest
import com.itrepos.aiotv.data.remote.stremio.fetchSearchFromHosts
import com.itrepos.aiotv.data.remote.stremio.searchUrl
import com.itrepos.aiotv.data.repository.SearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SearchRepositoryTest {
    @Test fun searchUrlEncodesQueryWithPercent20() {
        val u = searchUrl("https://cinemeta-live.strem.io", "series", "rick and morty")
        assertEquals("https://cinemeta-live.strem.io/catalog/series/top/search=rick%20and%20morty.json", u)
    }

    @Test fun fetchSearchFromHosts_allHostsFail_returnsNull() = runBlocking {
        val res = fetchSearchFromHosts(CINEMETA_HOSTS, "movie", "x") { throw RuntimeException("000") }
        assertNull(res)
    }

    @Test fun fetchSearchFromHosts_firstResponderWins() = runBlocking {
        var calls = 0
        val res = fetchSearchFromHosts(CINEMETA_HOSTS, "movie", "x") { calls++; listOf(meta("tt1","movie")) }
        assertEquals(1, calls)
        assertEquals(1, res!!.size)
    }

    private fun meta(id: String, type: String) = StremioMeta(id = id, type = type, name = id)

    private class FakeApi(val ok: Boolean) : StremioApi {
        override suspend fun getManifest(url: String): StremioManifest = throw NotImplementedError()
        override suspend fun getMeta(url: String): StremioMetaResponse = throw NotImplementedError()
        override suspend fun getStreams(url: String): StremioStreamResponse = throw NotImplementedError()
        override suspend fun getCatalog(url: String): StremioCatalogResponse {
            if (!ok) throw RuntimeException("000")
            // return one movie + one series, with a duplicate id across types to test dedup
            return if (url.contains("/movie/")) StremioCatalogResponse(listOf(StremioMeta("tt1","movie","M1"), StremioMeta("ttDUP","movie","Dup")))
            else StremioCatalogResponse(listOf(StremioMeta("tt2","series","S2"), StremioMeta("ttDUP","series","Dup")))
        }
    }

    @Test fun search_mergesBothTypes_dedupsById() = runBlocking {
        val out = SearchRepository(FakeApi(ok = true)).search("q")
        assertEquals(3, out.size) // tt1, ttDUP (once), tt2
        assertTrue(out.any { it.id == "tt1" && it.type == "movie" })
        assertTrue(out.any { it.id == "tt2" && it.type == "series" })
        assertEquals(1, out.count { it.id == "ttDUP" })
    }

    @Test fun search_cinemetaUnreachable_throws() {
        var threw = false
        try { runBlocking { SearchRepository(FakeApi(ok = false)).search("q") } }
        catch (e: IOException) { threw = true }
        assertTrue(threw)
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :app:testDebugUnitTest --tests "com.itrepos.aiotv.SearchRepositoryTest"` (unresolved `searchUrl`/`fetchSearchFromHosts`/`SearchRepository`).

- [ ] **Step 3: Implement.** Append to `StremioApi.kt`:

```kotlin
fun searchUrl(baseUrl: String, type: String, query: String): String {
    val q = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
    return "${baseUrl.trimEnd('/')}/catalog/$type/top/search=$q.json"
}

/** Tries each host's search URL; returns null iff NONE could be reached, else the first responder's metas. */
suspend fun fetchSearchFromHosts(
    hosts: List<String>,
    type: String,
    query: String,
    fetch: suspend (url: String) -> List<StremioMeta>,
): List<StremioMeta>? {
    for (host in hosts) {
        try {
            return fetch(searchUrl(host, type, query))
        } catch (_: Exception) {}
    }
    return null
}
```

`SearchRepository.kt`:
```kotlin
package com.itrepos.aiotv.data.repository

import com.itrepos.aiotv.data.remote.stremio.CINEMETA_HOSTS
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.fetchSearchFromHosts
import com.itrepos.aiotv.domain.model.MediaItem
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val stremioApi: StremioApi,
) {
    suspend fun search(query: String): List<MediaItem> {
        val movie = fetchSearchFromHosts(CINEMETA_HOSTS, "movie", query) { stremioApi.getCatalog(it).metas }
        val series = fetchSearchFromHosts(CINEMETA_HOSTS, "series", query) { stremioApi.getCatalog(it).metas }
        if (movie == null && series == null) throw IOException("Search unavailable")
        return ((movie ?: emptyList()) + (series ?: emptyList()))
            .map { it.toMediaItem() }
            .distinctBy { it.id }
    }

    private fun StremioMeta.toMediaItem() = MediaItem(
        id = id, type = type, name = name, description = description,
        posterUrl = poster, backdropUrl = background, year = year?.take(4)?.toIntOrNull(),
        genres = genres, imdbRating = imdbRating,
    )
}
```

`SearchVodUseCase.kt`:
```kotlin
package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.SearchRepository
import com.itrepos.aiotv.domain.model.MediaItem
import javax.inject.Inject

class SearchVodUseCase @Inject constructor(private val repo: SearchRepository) {
    suspend operator fun invoke(query: String): List<MediaItem> = repo.search(query)
}
```

- [ ] **Step 4: Run → PASS** (same command as Step 2).
- [ ] **Step 5: Build** `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `git commit -am "feat(search): Cinemeta search index (movie+series) behind SearchRepository"`.

---

### Task 2: `SearchViewModel` + `SearchScreen` — drop channels, wire Cinemeta search

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/search/SearchScreen.kt`

**Interfaces:**
- Consumes: `SearchVodUseCase.invoke(query): List<MediaItem>` (Task 1).
- Produces: `SearchState(query, mediaResults, isSearching, error)` — **no `channelResults`**.

- [ ] **Step 1: Rewrite `SearchViewModel.kt`** (swap dep, drop channels, add `error`):

```kotlin
package com.itrepos.aiotv.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.usecase.SearchVodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val mediaResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchVod: SearchVodUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .collect { q -> if (q.length >= 2) search(q) else clearResults() }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        _queryFlow.value = q
    }

    private suspend fun search(query: String) {
        _state.value = _state.value.copy(isSearching = true, error = null)
        try {
            val results = searchVod(query)
            _state.value = _state.value.copy(isSearching = false, mediaResults = results)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isSearching = false,
                mediaResults = emptyList(),
                error = "Search unavailable — check your connection.",
            )
        }
    }

    private fun clearResults() {
        _state.value = _state.value.copy(mediaResults = emptyList(), error = null)
    }
}
```

- [ ] **Step 2: Edit `SearchScreen.kt`.**
  (a) Placeholder text (`:59`): `Text("Search movies, series, channels…")` → `Text("Search movies & series…")`.
  (b) TV overscan: first `grep -rn "overscan" app/src/main/java` — if a shared overscan modifier exists, apply it; otherwise change the root `Column(Modifier.fillMaxSize().padding(16.dp))` to:
  ```kotlin
  Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 27.dp else 16.dp)) {
  ```
  (c) Empty-state condition (`:81-83`) — drop the channel clause, gate on no error:
  ```kotlin
  state.query.length >= 2 && state.mediaResults.isEmpty() && state.error == null -> {
  ```
  (d) Add an **error branch** in the `when` (above the empty-state branch):
  ```kotlin
  state.error != null -> {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(state.error!!, style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
      }
  }
  ```
  (e) **Delete both `if (state.channelResults.isNotEmpty()) { … }` blocks** — the one at `:99-118` (LazyColumn) and `:149-168` (LazyVerticalGrid). Keep the `state.mediaResults` blocks. Remove the now-unused `header_channels` keys.
  (f) Remove now-unused imports if the compiler flags them (the channel `MediaCard` for channels is gone; `Screen.Player` may now be unused in this file — remove its import only if unused).

- [ ] **Step 3: Build** `./gradlew assembleDebug` → BUILD SUCCESSFUL (fix any unused-import/compile errors).
- [ ] **Step 4: Commit** `git commit -am "feat(search): VOD-only Search (movies+series via Cinemeta), drop channels, TV overscan, error state"`.

> UI validated on the emulator in Task 4.

---

### Task 3: Home — strip the live-channel rail + load

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/home/HomeScreen.kt`

**Interfaces:**
- Produces: `HomeUiState` **without** `liveChannels`; `HomeViewModel` no longer injects `GetChannelsUseCase`.

- [ ] **Step 1: Edit `HomeViewModel.kt`.**
  (a) Remove `liveChannels: List<Channel> = emptyList(),` from `HomeUiState`.
  (b) Remove the constructor param `private val getChannels: GetChannelsUseCase,`.
  (c) In `loadContent()`, remove `channelsDeferred`, `val channels = channelsDeferred.await()`, and the `liveChannels = channels,` line. Result:
  ```kotlin
  val moviesDeferred = async { runCatching { getCatalog("movie") }.getOrDefault(emptyList()) }
  val seriesDeferred = async { runCatching { getCatalog("series") }.getOrDefault(emptyList()) }
  val movies = moviesDeferred.await()
  val series = seriesDeferred.await()
  _uiState.value = _uiState.value.copy(
      isLoading = false,
      movies = movies,
      series = series,
      featuredItem = movies.firstOrNull(),
  )
  ```
  (d) Remove unused imports: `GetChannelsUseCase`, `Channel`.

- [ ] **Step 2: Edit `HomeScreen.kt`.**
  (a) Delete the live rail block (`:170-188`):
  ```kotlin
  if (state.liveChannels.isNotEmpty()) {
      item(key = "rail_live") { ContentRail(title = stringResource(R.string.live_now), … live … ) { … } }
  }
  ```
  (b) `grep -n "firstRail\|\"live\"\|liveChannels" app/src/main/java/com/itrepos/aiotv/ui/screen/home/HomeScreen.kt` — in the `firstRail` computation (it decides initial D-pad focus), remove any branch that selects `"live"`/references `state.liveChannels`, so first focus falls through to the next present rail (cw → movies → series). Remove any other `state.liveChannels` reference.

- [ ] **Step 3: Build** `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `git commit -am "feat(home): remove live-channel rail + 27.5k-channel load (channels stay in Live TV; fixes slow paint)"`.

---

### Task 4: End-to-end validation + docs

**Files:** `DESIGN.md`, `TODO.md`

- [ ] **Step 1: Build + install.** `./gradlew assembleDebug`; `$ANDROID_HOME/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Validate on emulator (VPN off; restart with `-dns-server` if a fetch 403s):**
  - **Search "rick and morty"** → a **series** result appears → tap → Detail → episode plays. (Confirms VOD search finds series + routes correctly.)
  - **Search "inception"** (a film not necessarily on the 9 UK services) → a real **movie** result → proves it's an index, not a catalog filter.
  - Confirm **NO "Channels" section** in Search; placeholder reads **"Search movies & series…"**.
  - Turn the emulator to airplane mode briefly (or expect VPN-on) and search → the **"Search unavailable"** error state shows (not a silent empty list); restore network.
  - **Home**: loads fast (no multi-second channel wait); **no "Live Now" rail**; VOD rails (Continue Watching / Top Picks / Series) intact.
  - **Live TV tab**: channel search still works (unchanged).
  - Screenshot Search (results), Search (no channels), Home (no Live Now).
- [ ] **Step 3: Update docs.** `DESIGN.md` §8: change the "VOD search gap" bullet to **resolved** (Cinemeta search for movie+series), and note Home's live-rail removed (network rails still nabz's). `TODO.md`: check off the `[P2]` "VOD search — needs a search-capable meta source" item (done via Cinemeta search); under the Home item, note the **channel-strip + slow-paint fix shipped**, network rails still pending (nabz).
- [ ] **Step 4: Commit** `git commit -am "docs: VOD search (Cinemeta) + Home channel-strip shipped"`.

---

## Self-Review

**Spec coverage:** §1 problems → channel leak in Search (T2), fake VOD search/series-excluded (T1+T2), Home channel leak + slow paint (T3). §3 Cinemeta/imdb ids → T1 (`StremioMeta`→`MediaItem`, type preserved). §4 architecture units → `searchUrl`/`fetchSearchFromHosts`/`SearchRepository` (T1), `SearchVodUseCase` (T1), VM (T2), Screen (T2), Home VM/Screen (T3). §5 data flow → T1 search() + T2 VM. §6 error/empty → T1 throws on unreachable, T2 maps to error state, T2 screen branches. §7 testing → T1 unit + T4 emulator. **All covered.**

**Placeholder scan:** None. T1 carries full code + tests; T2/T3 are precise edits with concrete values; the one "grep for existing overscan modifier" instruction has a concrete fallback (48/27 dp) so it's not open-ended.

**Type consistency:** `SearchRepository.search(query): List<MediaItem>` (T1) ↔ `SearchVodUseCase.invoke` (T1) ↔ `SearchViewModel.searchVod(query)` (T2). `SearchState` loses `channelResults`, gains `error` (T2) — `SearchScreen` updated to match in the same task. `HomeUiState` loses `liveChannels` (T3) — `HomeScreen` updated in the same task. `searchUrl`/`fetchSearchFromHosts` signatures identical across test (T1 step 1) and impl (T1 step 3).
