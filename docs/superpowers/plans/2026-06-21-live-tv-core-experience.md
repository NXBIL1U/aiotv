# Live TV Core Experience Implementation Plan

> **For agentic workers:** This plan is executed inline by the author session. The repo has **no
> unit-test harness** and the project's standing rule is **validate on the emulator**; therefore
> each task's verification is `./gradlew assembleDebug` (+ emulator/logcat/screenshot where noted),
> not a JUnit cycle. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the bare channel list with a category-first Live TV browser: categories, channel
logos, instant search, and lazy per-channel now/next EPG.

**Architecture:** Add an Xtream `get_short_epg` API call and repository helpers
(`getCategories`, `getShortEpg`, `resolveXtreamCreds`). A new `LiveTvViewModel` holds categories,
the filtered/searched channel set, and a lazily-filled per-channel EPG map. A new adaptive
`LiveTvScreen` (two-pane on wide, chips+list on compact) replaces `TvGuideScreen` for the single
consolidated "Live TV" destination.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit + kotlinx.serialization, Coil
(`coil.compose.AsyncImage`), Media3 (playback unchanged).

## Global Constraints

- Min/target unchanged; build with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`, `ANDROID_HOME="$HOME/Library/Android/sdk"`, `./gradlew assembleDebug`.
- Secrets (M3U/Xtream creds, TorBox key, addon URLs) live only on-device — never commit or log full values.
- All network on `Dispatchers.IO`; every fetch wrapped so failures degrade to empty/null, never crash.
- Follow existing patterns: Coil `AsyncImage` + `ColorPainter` fallback (see `MediaCard`); TV overscan insets; width breakpoint `>= 600.dp` = wide.
- Xtream EPG titles are **base64** — decode with `android.util.Base64` (NO_WRAP), fall back to raw on failure.
- Channel `id` == Xtream `stream_id` as a string (already true) — EPG cache keys on it.

---

### Task 1: Domain models + unified category key on Channel

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/model/ChannelCategory.kt`
- Create: `app/src/main/java/com/itrepos/aiotv/domain/model/EpgNowNext.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/domain/model/Channel.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/data/remote/iptv/M3uParser.kt` (set `categoryKey`)
- Modify: `app/src/main/java/com/itrepos/aiotv/data/repository/IptvRepository.kt` (set `categoryKey` in `fetchXtream`)

**Interfaces:**
- Produces: `data class ChannelCategory(val id: String, val name: String)`;
  `data class EpgEntry(val title: String, val startMs: Long, val endMs: Long)`;
  `data class EpgNowNext(val now: EpgEntry?, val next: EpgEntry?)`;
  `Channel.categoryKey: String` (Xtream `category_id`; M3U `group-title`).

- [ ] **Step 1: Create `ChannelCategory.kt`**
```kotlin
package com.itrepos.aiotv.domain.model

data class ChannelCategory(
    val id: String,   // filter key: Xtream category_id, or M3U group-title
    val name: String,
)
```

- [ ] **Step 2: Create `EpgNowNext.kt`**
```kotlin
package com.itrepos.aiotv.domain.model

data class EpgEntry(
    val title: String,
    val startMs: Long,
    val endMs: Long,
)

data class EpgNowNext(
    val now: EpgEntry?,
    val next: EpgEntry?,
)
```

- [ ] **Step 3: Add `categoryKey` to `Channel`**
```kotlin
data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String,
    val streamUrl: String,
    val tvgId: String?,
    val categoryKey: String = "",   // unified filter key (category_id / group-title)
    val isFavourite: Boolean = false,
)
```

- [ ] **Step 4: Set `categoryKey` in `M3uParser`** — in the channel-construction block, add
  `categoryKey = attrs["group-title"] ?: "Uncategorised",` alongside `groupTitle` (same value).

- [ ] **Step 5: Set `categoryKey` in `IptvRepository.fetchXtream`** — in the `Channel(...)` map add
  `categoryKey = s.categoryId ?: "",` alongside the existing `groupTitle`.

- [ ] **Step 6: Build**
Run: `./gradlew assembleDebug`  — Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit** — `git add -A && git commit -m "Live TV: domain models + Channel.categoryKey"`

---

### Task 2: Xtream short-EPG API

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/data/remote/iptv/XtreamApi.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `suspend fun XtreamApi.getShortEpg(url: String): XtreamShortEpgResponse`;
  `XtreamShortEpgResponse(listings: List<XtreamEpgListing>)`;
  `XtreamEpgListing(title, start, end, startTimestamp, stopTimestamp)`.

- [ ] **Step 1: Add the API method** (in `interface XtreamApi`)
```kotlin
    @GET
    suspend fun getShortEpg(@Url url: String): XtreamShortEpgResponse
```

- [ ] **Step 2: Add the models** (top level in the file)
```kotlin
@Serializable
data class XtreamShortEpgResponse(
    @SerialName("epg_listings") val listings: List<XtreamEpgListing> = emptyList(),
)

@Serializable
data class XtreamEpgListing(
    @SerialName("title") val title: String = "",            // base64
    @SerialName("start") val start: String = "",
    @SerialName("end") val end: String = "",
    @SerialName("start_timestamp") val startTimestamp: Long = 0,
    @SerialName("stop_timestamp") val stopTimestamp: Long = 0,
)
```

- [ ] **Step 3: Build** — `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `git commit -am "Live TV: Xtream get_short_epg API + models"`

---

### Task 3: Repository — categories, EPG, shared cred resolution

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/data/repository/IptvRepository.kt`

**Interfaces:**
- Consumes: `XtreamCreds.fromGetPhp`, `XtreamApi.getLiveCategories/getShortEpg`, Task 1 models.
- Produces:
  - `suspend fun getCategories(): List<ChannelCategory>`
  - `suspend fun getShortEpg(channel: Channel): EpgNowNext?`
  - private `suspend fun resolveXtreamCreds(): XtreamCreds?`

- [ ] **Step 1: Add imports** — `ChannelCategory`, `EpgEntry`, `EpgNowNext`,
  `com.itrepos.aiotv.data.remote.iptv.buildUrl`, `android.util.Base64`.

- [ ] **Step 2: Add a category cache field** next to the existing caches:
```kotlin
    private var cachedCategories: List<ChannelCategory> = emptyList()
    // streamId -> (fetchedAtMs, nowNext)
    private val epgCache = mutableMapOf<String, Pair<Long, EpgNowNext>>()
    private val epgTtlMs = 10 * 60 * 1000L
```

- [ ] **Step 3: Add `resolveXtreamCreds()`** — single source of truth for creds:
```kotlin
    private suspend fun resolveXtreamCreds(): XtreamCreds? {
        val m3uUrl = appDataStore.m3uUrl.first()
        XtreamCreds.fromGetPhp(m3uUrl)?.let { return it }
        val server = appDataStore.xtreamServer.first()
        val user = appDataStore.xtreamUser.first()
        val pass = appDataStore.xtreamPass.first()
        return if (server.isNotEmpty() && user.isNotEmpty())
            XtreamCreds(server, user, pass) else null
    }
```
  Then refactor `getChannels()` to use `resolveXtreamCreds()` for its Xtream branch (keep the
  plain-M3U branch when creds are null but `m3uUrl` is set).

- [ ] **Step 4: Add `getCategories()`**
```kotlin
    suspend fun getCategories(): List<ChannelCategory> {
        if (cachedCategories.isNotEmpty()) return cachedCategories
        val creds = resolveXtreamCreds()
        val cats = try {
            if (creds != null) {
                val url = "${creds.server}/player_api.php?username=${creds.user}" +
                    "&password=${creds.pass}&action=get_live_categories"
                xtreamApi.getLiveCategories(url)
                    .map { ChannelCategory(it.categoryId, it.categoryName) }
            } else {
                // Plain M3U: derive groups from already-loaded channels.
                getChannels().map { it.categoryKey }.filter { it.isNotEmpty() }
                    .distinct().map { ChannelCategory(it, it) }
            }
        } catch (e: Exception) { emptyList() }
        cachedCategories = cats
        return cats
    }
```

- [ ] **Step 5: Add `getShortEpg(channel)`** (Xtream only; cache + TTL; base64 decode)
```kotlin
    suspend fun getShortEpg(channel: Channel): EpgNowNext? {
        val streamId = channel.id.toIntOrNull() ?: return null
        val now = System.currentTimeMillis()
        epgCache[channel.id]?.let { (at, v) -> if (now - at < epgTtlMs) return v }
        val creds = resolveXtreamCreds() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "${creds.server}/player_api.php?username=${creds.user}" +
                    "&password=${creds.pass}&action=get_short_epg&stream_id=$streamId&limit=2"
                val listings = xtreamApi.getShortEpg(url).listings
                val entries = listings.map {
                    EpgEntry(
                        title = decodeB64(it.title),
                        startMs = it.startTimestamp * 1000,
                        endMs = it.stopTimestamp * 1000,
                    )
                }.sortedBy { it.startMs }
                val nowEntry = entries.firstOrNull { it.startMs <= now && it.endMs > now }
                val nextEntry = entries.firstOrNull { it.startMs > now }
                    ?: entries.firstOrNull { it != nowEntry }
                EpgNowNext(nowEntry, nextEntry).also { epgCache[channel.id] = now to it }
            } catch (e: Exception) { null }
        }
    }

    private fun decodeB64(s: String): String = try {
        if (s.isBlank()) "" else String(Base64.decode(s, Base64.DEFAULT))
    } catch (e: Exception) { s }
```

- [ ] **Step 6: Extend `clearCache()`** to also clear `cachedCategories` and `epgCache`.

- [ ] **Step 7: Build** — `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 8: Commit** — `git commit -am "Live TV: repository categories + short-EPG + shared cred resolution"`

---

### Task 4: LiveTvViewModel

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/ui/screen/live/LiveTvViewModel.kt`

**Interfaces:**
- Consumes: `IptvRepository.getChannels/getCategories/getShortEpg`, Task 1 models.
- Produces: `LiveTvViewModel` with `state: StateFlow<LiveTvState>`, and
  `selectCategory(id: String?)`, `setQuery(q: String)`, `onChannelVisible(channel: Channel)`.
  `LiveTvState(isLoading, categories, selectedCategoryId, channels, query, epg)`.

- [ ] **Step 1: Create the ViewModel**
```kotlin
package com.itrepos.aiotv.ui.screen.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.repository.IptvRepository
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.ChannelCategory
import com.itrepos.aiotv.domain.model.EpgNowNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

const val ALL_CATEGORY_ID = "__all__"

data class LiveTvState(
    val isLoading: Boolean = true,
    val categories: List<ChannelCategory> = emptyList(),
    val selectedCategoryId: String = ALL_CATEGORY_ID,
    val channels: List<Channel> = emptyList(),
    val query: String = "",
    val epg: Map<String, EpgNowNext> = emptyMap(),
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repo: IptvRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    private var allChannels: List<Channel> = emptyList()
    private val epgSemaphore = Semaphore(4)
    private val epgInFlight = mutableSetOf<String>()
    private var queryJob: Job? = null

    init {
        viewModelScope.launch {
            val channelsDeferred = async { repo.getChannels() }
            val catsDeferred = async { repo.getCategories() }
            allChannels = channelsDeferred.await()
            val cats = catsDeferred.await()
            _state.update {
                it.copy(
                    isLoading = false,
                    categories = listOf(ChannelCategory(ALL_CATEGORY_ID, "All")) + cats,
                    channels = allChannels,
                )
            }
        }
    }

    fun selectCategory(id: String) {
        val filtered = if (id == ALL_CATEGORY_ID) allChannels
            else allChannels.filter { it.categoryKey == id }
        _state.update { it.copy(selectedCategoryId = id, query = "", channels = filtered) }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(250)
            val result = if (q.isBlank()) {
                val id = _state.value.selectedCategoryId
                if (id == ALL_CATEGORY_ID) allChannels else allChannels.filter { it.categoryKey == id }
            } else {
                allChannels.filter { it.name.contains(q, ignoreCase = true) }
            }
            _state.update { it.copy(channels = result) }
        }
    }

    fun onChannelVisible(channel: Channel) {
        if (_state.value.epg.containsKey(channel.id)) return
        synchronized(epgInFlight) {
            if (!epgInFlight.add(channel.id)) return
        }
        viewModelScope.launch {
            try {
                val nn = epgSemaphore.withPermit { repo.getShortEpg(channel) }
                if (nn != null) _state.update { it.copy(epg = it.epg + (channel.id to nn)) }
            } finally {
                synchronized(epgInFlight) { epgInFlight.remove(channel.id) }
            }
        }
    }
}
```

- [ ] **Step 2: Build** — `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "Live TV: LiveTvViewModel (categories, search, lazy EPG)"`

---

### Task 5: UI components — ChannelRow + CategoryPane/CategoryChips

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/ui/screen/live/LiveTvComponents.kt`

**Interfaces:**
- Consumes: `Channel`, `EpgNowNext`, `ChannelCategory`, Coil `AsyncImage`.
- Produces: `@Composable ChannelRow(channel, nowNext, onClick, modifier, focusRequester?)`;
  `@Composable CategoryPane(categories, selectedId, onSelect, modifier)`;
  `@Composable CategoryChips(categories, selectedId, onSelect, modifier)`.

- [ ] **Step 1: Create the components file** — a horizontal `ChannelRow` (logo via `AsyncImage` +
  `ColorPainter(SurfaceElevated)` fallback, name, and a now/next line: `● <now>` + `Next HH:MM <next>`
  or "—" when `nowNext == null`); a vertical focusable `CategoryPane` (highlight selected); a
  horizontally-scrolling `CategoryChips`. Reuse `FocusableCard`/`clickable` focus patterns from
  `TvGuideScreen`/`MediaCard`; format times with `SimpleDateFormat("HH:mm")`.
  (Full composable bodies written at implementation time following the cited patterns; each is a
  small, self-contained composable with the signature in Interfaces above.)

- [ ] **Step 2: Build** — `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "Live TV: ChannelRow + category pane/chips components"`

---

### Task 6: LiveTvScreen (adaptive) + nav consolidation

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/ui/screen/live/LiveTvScreen.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/components/NavRail.kt` (navItems + phone routes)
- Modify: `app/src/main/res/values/strings.xml` (label "Live TV" for `nav_live` if needed)

**Interfaces:**
- Consumes: `LiveTvViewModel`, Task 5 components.
- Produces: `@Composable LiveTvScreen(isTv, windowSizeClass, onPlayChannel, onNavigate, vm = hiltViewModel())`.

- [ ] **Step 1: Create `LiveTvScreen`** — `BoxWithConstraints`; `wide = maxWidth >= 600.dp`.
  Loading → spinner; empty channels → empty state (pointer to Settings). Wide: `Row { CategoryPane(weight) ; Column { SearchField ; LazyColumn(ChannelRow) } }`. Compact: `Column { SearchField ; CategoryChips ; LazyColumn(ChannelRow) }`. Each `ChannelRow` calls
  `LaunchedEffect(channel.id) { vm.onChannelVisible(channel) }`. Apply TV overscan insets when `isTv`.
  Search field bound to `state.query`/`vm.setQuery`. Row click → `onPlayChannel(channel.streamUrl, channel.name)`.

- [ ] **Step 2: Wire navigation** in `AppNavigation.kt` — point BOTH `Screen.Live.route` and
  `Screen.Guide.route` composables at `LiveTvScreen(isTv, windowSizeClass, onPlayChannel = { url, title -> navController.navigate(Screen.Player.createRoute(url, title)) }, onNavigate = onNavigate)`.
  (`AppNavigation` already receives `windowSizeClass`.)

- [ ] **Step 3: Consolidate nav items** in `NavRail.kt` — remove the `Screen.Guide` `NavItem`;
  keep `NavItem(Screen.Live, R.string.nav_live, Icons.Default.LiveTv)`. In `PhoneBottomNav.phoneRoutes`
  replace `Screen.Guide.route` with `Screen.Live.route`.

- [ ] **Step 4: Update string** — set `nav_live` to "Live TV" in `strings.xml`.

- [ ] **Step 5: Build** — `./gradlew assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 6: Install + emulator validation** (user away → screenshots OK):
  - `adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk`
  - Force-stop + relaunch; open Live TV.
  - uiautomator dump: category names present; channel names present.
  - logcat: `get_live_categories` 200 + `get_short_epg` 200s.
  - Screenshot: logos render; now/next badges appear; select a category filters; type in search filters.
  - Tap a channel → player (regression of `.ts`).

- [ ] **Step 7: Commit** — `git commit -am "Live TV: adaptive LiveTvScreen + consolidate Guide/Live nav"`

---

### Task 7: Independent review + docs + finalize

**Files:**
- Modify: `TODO.md`, `DESIGN.md`
- Delete (optional): `app/src/main/java/com/itrepos/aiotv/ui/screen/guide/` if fully unused.

- [ ] **Step 1: Code review** — dispatch a `feature-dev:code-reviewer` agent scoped strictly to the
  diff of this branch; triage findings; fix real issues.
- [ ] **Step 2: Remove dead code** — if nothing references `TvGuideScreen`/`TvGuideViewModel`, delete
  them; otherwise leave. Re-build.
- [ ] **Step 3: Update docs** — TODO.md: mark the `[P2/P3]` Live TV UX items done; DESIGN.md: note the
  Live TV core experience landed; reference this spec/plan.
- [ ] **Step 4: Final build** — `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `git commit -am "Live TV: docs + cleanup after core-experience build"`
- [ ] **Step 6: Landing** — leave on `feat/live-tv-core` for the user's push/PR call (default: push branch + PR; user previously chose push-to-main — confirm since they're away).

---

## Self-Review

**Spec coverage:**
- IA consolidation → Task 6 ✓
- Adaptive layout (wide/compact) → Task 6 ✓
- Channel logos → Task 5 (ChannelRow) ✓
- now/next EPG (lazy, cached, base64) → Tasks 2,3,4,5 ✓
- Categories (322, named) → Tasks 3,4,5,6 ✓
- Search (global, debounced) → Task 4 ✓
- Generic M3U degradation → Task 1 (categoryKey from group-title), Task 3 (category fallback) ✓
- Playback unchanged → Task 6 (onPlayChannel) ✓
- Error/empty/loading → Task 6 ✓
- Deferred items (favourites/catch-up/time-grid) → not implemented ✓

**Type consistency:** `categoryKey` (Channel) ↔ `ChannelCategory.id` ↔ `selectedCategoryId` share one
key space; `ALL_CATEGORY_ID` sentinel used in VM + screen; `EpgNowNext`/`EpgEntry` consistent across
repo→VM→UI; channel.id (String) is the EPG cache + epg-map key everywhere. ✓

**Placeholders:** Task 5 step 1 and Task 6 step 1 describe composable bodies rather than full code —
acceptable because they're UI assembly following explicitly-cited existing patterns
(`MediaCard`/`TvGuideScreen`), with exact signatures fixed in the Interfaces blocks. All logic-bearing
tasks (1–4) contain complete code.
