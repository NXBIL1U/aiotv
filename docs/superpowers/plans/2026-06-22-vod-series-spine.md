# VOD Series Spine + Netflix Detail + Theme — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make series playable and browsable — a real season/episode picker (Cinemeta) + per-episode stream resolution that auto-plays the best source — behind a Netflix-style Detail page on an app-wide dark + Netflix-red theme.

**Architecture:** One `DetailViewModel` + `Detail` route branches on `meta.type`; series load Cinemeta episode trees and request streams per-episode (`tt…:S:E`). A pure `StreamRanker` orders sources (cached → English → quality → seeders); a thin `StreamResolver` handles both stream shapes (pre-resolved `[TB+]` `url` and raw `infoHash`). UI splits into `MovieDetail`/`SeriesDetail`/`SourcesSheet`.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Media3/ExoPlayer, Hilt, Retrofit + kotlinx.serialization (`@GET @Url`), Coil, DataStore. Spec: `docs/superpowers/specs/2026-06-22-vod-series-spine-design.md`.

## Global Constraints

- Package `com.itrepos.aiotv`. Build: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"; ./gradlew assembleDebug`.
- **Tests: JUnit 4.13.2 ONLY** (no mockk/mockito/truth/coroutines-test). Use `org.junit.Assert.*`, `@Test`. For suspend funcs in tests use `kotlinx.coroutines.runBlocking`. Fakes = hand-written classes implementing the interface under test (only `StremioApi` is an interface; concrete repos are not faked — see Testing Strategy).
- **Testing strategy:** TDD the **pure logic** (parsing, ranking, Cinemeta mapping, host-fallback over the `StremioApi` interface). Validate **UI / ViewModel / TorBox IO** on the emulator with screenshots (owner is away → screenshot, don't ask; `uiautomator dump` is unreliable on Compose lists). State this in commits.
- Theme uses `androidx.compose.material3.MaterialTheme` (NOT androidx.tv) — keep it.
- **Cinemeta is internal**, host order: `https://cinemeta-live.strem.io` then `https://v3-cinemeta.strem.fun`. `v3-cinemeta.strem.fun` is flaky from here — `cinemeta-live.strem.io` is primary.
- **Cached signal** for the owner's config = the literal substring **`[TB+]`** in the stream `name` (Torrentio debrid marker). Keep the existing infoHash `checkCached` path for non-debrid addons.
- **Validation network state: VPN OFF** (Torrentio/Cinemeta 403 on the VPN datacenter IP). If a fetch returns HTML/403 or `UnknownHostException`, restart the emulator: `$ANDROID_HOME/platform-tools/adb -s <serial> emu kill; $ANDROID_HOME/emulator/emulator -avd aiotv_phone -dns-server 8.8.8.8,8.8.4.4 -netdelay none -netspeed full &`.
- **Git: commit locally to `feat/vod-series-spine`, never push.** Commit after each task.
- Provider re-fetch is slow; series meta/streams take seconds — wait before asserting "empty".

---

### Task 1: Theme — app-wide dark + Netflix-red palette

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/theme/Theme.kt`

**Interfaces:**
- Produces: color tokens `Background`, `SurfaceCard`, `SurfaceElevated`, `AccentPrimary` (now red), `NetflixRed` (new, brand-only), `CachedBadge` (unchanged green). `AioTvTheme(isTv, content)` unchanged signature.

App is already dark Material3 — this is a palette swap (indigo `#6C63FF` → Netflix-red) plus neutral-ising the bluish surfaces.

- [ ] **Step 1: Rewrite `Color.kt`** with neutral-dark surfaces + tonal-red accent + brand red:

```kotlin
package com.itrepos.aiotv.ui.theme

import androidx.compose.ui.graphics.Color

// Neutral near-black surfaces (Netflix-like), layered elevations
val Background = Color(0xFF0B0B0B)
val SurfaceCard = Color(0xFF1A1A1A)
val SurfaceElevated = Color(0xFF242424)
val SurfaceGlass = Color(0xCC141414)

// Interactive accent = tonal red (a11y-safe on dark); brand red reserved for brand moments only
val AccentPrimary = Color(0xFFE5403F)   // tonal/interactive red
val AccentSecondary = Color(0xFFB81D24) // deep red (containers/pressed)
val NetflixRed = Color(0xFFE50914)      // BRAND ONLY (wordmark/splash) — not for body text/contrast-critical UI

val OnBackground = Color(0xFFF5F5F5)
val OnSurface = Color(0xFFD6D6D6)
val OnSurfaceMuted = Color(0xFF9A9A9A)
val FocusGlow = Color(0x80E5403F)
val CachedBadge = Color(0xFF46D369)     // Netflix-ish green for "cached/available"
val LiveDot = Color(0xFFE50914)
val ProgressBar = Color(0xFFE50914)
val Outline = Color(0xFF333333)
```

- [ ] **Step 2: Update `Theme.kt`** color-scheme mapping (red primary, neutral surfaces):

```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = OnBackground,
    primaryContainer = AccentSecondary,
    onPrimaryContainer = OnBackground,
    secondary = AccentPrimary,
    onSecondary = OnBackground,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceCard,
    onSurface = OnSurface,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Outline,
    error = AccentSecondary,
)
```
(Leave `AioTvTheme` and `Type.kt` untouched.)

- [ ] **Step 3: Build.** Run: `./gradlew assembleDebug` → Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 4: Visual check.** Install, screenshot Home + Search + an existing screen → accents are red, surfaces neutral-dark, no indigo. (Detail/series come later.)
- [ ] **Step 5: Commit.**
```bash
git add app/src/main/java/com/itrepos/aiotv/ui/theme/Color.kt app/src/main/java/com/itrepos/aiotv/ui/theme/Theme.kt
git commit -m "feat(theme): app-wide dark + Netflix-red foundation (tonal red interactive, brand red reserved)"
```

---

### Task 2: Stream metadata parsing (quality / seeders / size / [TB+] / language / bingeGroup)

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/StreamParsing.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/domain/model/Stream.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/data/remote/stremio/StremioModels.kt` (add `name`, `thumbnail`, `overview` to `StremioVideo`)
- Modify: `app/src/main/java/com/itrepos/aiotv/data/repository/StremioRepository.kt` (`toStream` populates parsed fields)
- Create: `app/src/test/java/com/itrepos/aiotv/StreamParsingTest.kt`

**Interfaces:**
- Produces: `Stream` gains `name: String?`, `quality: Quality`, `seeders: Int?`, `sizeBytes: Long?`, `languageScore: Int`, `bingeGroup: String?`, and `isCached` is set true when `name` contains `[TB+]`. `enum class Quality { UHD_2160, HD_1080, HD_720, SD, UNKNOWN }` with `rank: Int`. `object StreamParsing { fun quality(text): Quality; fun seeders(title): Int?; fun sizeBytes(title): Long?; fun isTbCached(name): Boolean; fun languageScore(text): Int }`.

- [ ] **Step 1: Failing test** `StreamParsingTest.kt`:

```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamParsing
import com.itrepos.aiotv.domain.model.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class StreamParsingTest {
    @Test fun parsesQuality() {
        assertEquals(Quality.HD_1080, StreamParsing.quality("[TB+] Torrentio\n1080p"))
        assertEquals(Quality.HD_720, StreamParsing.quality("HIMYM.S01E01.720p.WEB-DL.mkv"))
        assertEquals(Quality.UHD_2160, StreamParsing.quality("Show.2160p.4K"))
        assertEquals(Quality.UNKNOWN, StreamParsing.quality("[TB+] Torrentio\nDLMux"))
    }
    @Test fun parsesSeeders() {
        assertEquals(27, StreamParsing.seeders("Complete 720p 👤 27 💾 4 GB"))
        assertEquals(null, StreamParsing.seeders("no seeder marker here"))
    }
    @Test fun parsesSize() {
        assertEquals(4L * 1024 * 1024 * 1024, StreamParsing.sizeBytes("x 💾 4 GB y"))
        assertEquals(1_500L * 1024 * 1024, StreamParsing.sizeBytes("x 💾 1.5 GB"))
    }
    @Test fun detectsTbCached() {
        assertTrue(StreamParsing.isTbCached("[TB+] Torrentio\n1080p"))
        assertFalse(StreamParsing.isTbCached("[TB download] Torrentio\n1080p"))
    }
    @Test fun englishScoresHigherThanForeign() {
        val eng = StreamParsing.languageScore("How.I.Met.Your.Mother.S01.1080p.AMZN.WEBRip.DDP5.1.x264-NOGRP")
        val rus = StreamParsing.languageScore("Как я встретил вашу маму / How I Met Your Mother VO (Кураж-Бамбей)")
        val fra = StreamParsing.languageScore("How I Met Your Mother (Integrale) FRENCH HDTV")
        assertTrue(eng > rus)
        assertTrue(eng > fra)
    }
}
```

- [ ] **Step 2: Run → FAIL** (`StreamParsing` unresolved). Run: `./gradlew :app:testDebugUnitTest --tests "com.itrepos.aiotv.StreamParsingTest"`.

- [ ] **Step 3: Implement.** `Stream.kt` — add fields + `Quality`:

```kotlin
package com.itrepos.aiotv.domain.model

enum class Quality(val rank: Int) { UHD_2160(4), HD_1080(3), HD_720(2), SD(1), UNKNOWN(0) }

data class Stream(
    val title: String?,
    val url: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val behaviorHints: BehaviorHints? = null,
    val isCached: Boolean = false,
    val torBoxTorrentId: Int? = null,
    val torBoxFileId: Int? = null,
    val name: String? = null,
    val quality: Quality = Quality.UNKNOWN,
    val seeders: Int? = null,
    val sizeBytes: Long? = null,
    val languageScore: Int = 0,
    val bingeGroup: String? = null,
)

data class BehaviorHints(val bingeGroup: String? = null, val filename: String? = null)
```

`StreamParsing.kt`:

```kotlin
package com.itrepos.aiotv.domain

import com.itrepos.aiotv.domain.model.Quality

object StreamParsing {
    private val seedersRe = Regex("👤\\s*(\\d+)")          // 👤 N
    private val sizeRe = Regex("💾\\s*([\\d.]+)\\s*(GB|MB)", RegexOption.IGNORE_CASE) // 💾 N GB/MB
    private val foreign = listOf(
        Regex("[\\u0400-\\u04FF]"),                                  // Cyrillic
        Regex("\\bFRENCH\\b|\\bVOSTFR\\b|\\bVF\\b", RegexOption.IGNORE_CASE),
        Regex("\\bITA\\b|\\bITALIAN\\b", RegexOption.IGNORE_CASE),
        Regex("\\bSPA\\b|\\bSPANISH\\b|\\bLAT\\b", RegexOption.IGNORE_CASE),
    )
    private val english = Regex("\\bENG\\b|\\bENGLISH\\b|\\bAMZN\\b", RegexOption.IGNORE_CASE)

    fun quality(text: String?): Quality {
        val t = text ?: return Quality.UNKNOWN
        return when {
            Regex("2160p|\\b4K\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> Quality.UHD_2160
            t.contains("1080p", true) -> Quality.HD_1080
            t.contains("720p", true) -> Quality.HD_720
            Regex("480p|\\bSD\\b|DVDRip|XviD", RegexOption.IGNORE_CASE).containsMatchIn(t) -> Quality.SD
            else -> Quality.UNKNOWN
        }
    }
    fun seeders(title: String?): Int? = title?.let { seedersRe.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    fun sizeBytes(title: String?): Long? = title?.let {
        val m = sizeRe.find(it) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val mult = if (m.groupValues[2].equals("GB", true)) 1024L * 1024 * 1024 else 1024L * 1024
        (n * mult).toLong()
    }
    fun isTbCached(name: String?): Boolean = name?.contains("[TB+]") == true
    /** Higher = more likely English. */
    fun languageScore(text: String?): Int {
        val t = text ?: return 0
        var s = 0
        if (english.containsMatchIn(t)) s += 2
        if (foreign.any { it.containsMatchIn(t) }) s -= 2
        return s
    }
}
```

`StremioModels.kt` — extend `StremioVideo`:
```kotlin
@Serializable
data class StremioVideo(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val released: String? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
)
```

`StremioRepository.toStream` — populate parsed fields (combine `name` + `title` + `filename` for detection):
```kotlin
private fun StremioStream.toStream(): Stream {
    val detectText = listOfNotNull(name, title, behaviorHints?.filename).joinToString(" ")
    return Stream(
        title = title ?: name,
        url = url,
        infoHash = infoHash,
        fileIdx = fileIdx,
        behaviorHints = behaviorHints?.let { BehaviorHints(it.bingeGroup, it.filename) },
        isCached = StreamParsing.isTbCached(name),
        name = name,
        quality = StreamParsing.quality(detectText),
        seeders = StreamParsing.seeders(title),
        sizeBytes = StreamParsing.sizeBytes(title),
        languageScore = StreamParsing.languageScore(detectText),
        bingeGroup = behaviorHints?.bingeGroup,
    )
}
```
(Add imports for `BehaviorHints`, `StreamParsing`.)

- [ ] **Step 4: Run → PASS.** Same command as Step 2.
- [ ] **Step 5: Build** `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 6: Commit** `git commit -am "feat(streams): parse quality/seeders/size/[TB+]/language from Torrentio metadata"`.

---

### Task 3: StreamRanker (pure ordering)

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/StreamRanker.kt`
- Create: `app/src/test/java/com/itrepos/aiotv/StreamRankerTest.kt`

**Interfaces:**
- Consumes: `Stream` (Task 2 fields).
- Produces: `object StreamRanker { fun rank(streams: List<Stream>): List<Stream> }` — stable order by `isCached` desc, `languageScore` desc, `quality.rank` desc, `seeders` desc. Used for both the auto-play candidate order and the Sources list.

- [ ] **Step 1: Failing test** `StreamRankerTest.kt` (mirrors the real HIMYM fixture from the spec — English 1080p AMZN must win):

```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRankerTest {
    private fun s(name: String, title: String, cached: Boolean, q: Quality, lang: Int, seed: Int?) =
        Stream(title = title, url = "u/$title", infoHash = null, fileIdx = null,
            isCached = cached, name = name, quality = q, seeders = seed, languageScore = lang)

    @Test fun englishCached1080pWins() {
        val rus = s("[TB+] Torrentio\n1080p", "Как я встретил VO (Кураж)", true, Quality.HD_1080, -2, 0)
        val eng = s("[TB+] Torrentio\n1080p", "HIMYM S01 1080p AMZN", true, Quality.HD_1080, 2, 7)
        val eng720 = s("[TB+] Torrentio\n720p", "HIMYM S01 720p ENG", true, Quality.HD_720, 2, 27)
        val ranked = StreamRanker.rank(listOf(rus, eng720, eng))
        assertEquals("HIMYM S01 1080p AMZN", ranked.first().title)
    }
    @Test fun cachedBeatsUncachedEvenIfLowerQuality() {
        val uncached1080 = s("[TB download]", "A 1080p", false, Quality.HD_1080, 2, 50)
        val cached720 = s("[TB+]", "B 720p", true, Quality.HD_720, 2, 3)
        assertEquals("B 720p", StreamRanker.rank(listOf(uncached1080, cached720)).first().title)
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :app:testDebugUnitTest --tests "com.itrepos.aiotv.StreamRankerTest"`.
- [ ] **Step 3: Implement** `StreamRanker.kt`:

```kotlin
package com.itrepos.aiotv.domain

import com.itrepos.aiotv.domain.model.Stream

object StreamRanker {
    fun rank(streams: List<Stream>): List<Stream> =
        streams.sortedWith(
            compareByDescending<Stream> { it.isCached }
                .thenByDescending { it.languageScore }
                .thenByDescending { it.quality.rank }
                .thenByDescending { it.seeders ?: -1 }
        )
}
```

- [ ] **Step 4: Run → PASS.** Same command.
- [ ] **Step 5: Commit** `git commit -am "feat(streams): StreamRanker — cached>English>quality>seeders"`.

---

### Task 4: Episode model + Cinemeta MetaRepository (host fallback)

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/model/Episode.kt`
- Create: `app/src/main/java/com/itrepos/aiotv/data/repository/MetaRepository.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/data/remote/stremio/StremioApi.kt` (Cinemeta hosts constant + reuse `getMeta(@Url)`)
- Create: `app/src/test/java/com/itrepos/aiotv/CinemetaMappingTest.kt`
- Create: `app/src/test/java/com/itrepos/aiotv/MetaRepositoryFallbackTest.kt`

**Interfaces:**
- Consumes: `StremioApi.getMeta(url): StremioMetaResponse`, `StremioMeta`/`StremioVideo` (Task 2).
- Produces:
  - `data class Episode(val id: String, val season: Int, val number: Int, val name: String, val overview: String?, val thumbnail: String?, val released: String?)`
  - `data class SeriesMeta(val item: MediaItem, val seasons: List<Int>, val episodes: List<Episode>) { fun episodesIn(season: Int): List<Episode> }`
  - pure `fun StremioMeta.toSeriesMeta(): SeriesMeta` (season 0 kept but listed last as "specials").
  - `class MetaRepository @Inject constructor(stremioApi, stremioRepository) { suspend fun getSeriesMeta(id: String): SeriesMeta?; suspend fun getMovieMeta(type,id): MediaItem? }` — series tries `cinemeta-live.strem.io` then `v3-cinemeta.strem.fun`, then installed-addon meta.
  - `CINEMETA_HOSTS = listOf("https://cinemeta-live.strem.io", "https://v3-cinemeta.strem.fun")` in StremioApi.kt.

- [ ] **Step 1: Failing test — mapping** `CinemetaMappingTest.kt` (uses a trimmed real-shape fixture):

```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.StremioVideo
import com.itrepos.aiotv.data.repository.toSeriesMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class CinemetaMappingTest {
    private val meta = StremioMeta(
        id = "tt0460649", type = "series", name = "How I Met Your Mother",
        videos = listOf(
            StremioVideo(id = "tt0460649:1:1", season = 1, episode = 1, name = "Pilot", thumbnail = "t1"),
            StremioVideo(id = "tt0460649:1:2", season = 1, episode = 2, name = "Purple Giraffe"),
            StremioVideo(id = "tt0460649:2:1", season = 2, episode = 1, name = "Where Were We?"),
            StremioVideo(id = "tt0460649:0:1", season = 0, episode = 1, name = "Special"),
        )
    )
    @Test fun groupsSeasonsSkippingSpecialsToEnd() {
        val sm = meta.toSeriesMeta()
        assertEquals(listOf(1, 2, 0), sm.seasons) // specials (0) last
        assertEquals(2, sm.episodesIn(1).size)
        assertEquals("Pilot", sm.episodesIn(1).first().name)
        assertEquals("tt0460649:2:1", sm.episodesIn(2).first().id)
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :app:testDebugUnitTest --tests "com.itrepos.aiotv.CinemetaMappingTest"`.
- [ ] **Step 3: Implement** `Episode.kt` + mapping (put `SeriesMeta` + `toSeriesMeta` in `MetaRepository.kt` file or a `SeriesMeta.kt`; tests import from `data.repository`):

```kotlin
// domain/model/Episode.kt
package com.itrepos.aiotv.domain.model
data class Episode(
    val id: String, val season: Int, val number: Int,
    val name: String, val overview: String?, val thumbnail: String?, val released: String?,
)
```

```kotlin
// data/repository/MetaRepository.kt
package com.itrepos.aiotv.data.repository

import com.itrepos.aiotv.data.remote.stremio.CINEMETA_HOSTS
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.metaUrl
import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

data class SeriesMeta(val item: MediaItem, val seasons: List<Int>, val episodes: List<Episode>) {
    fun episodesIn(season: Int) = episodes.filter { it.season == season }
}

fun StremioMeta.toSeriesMeta(): SeriesMeta {
    val eps = videos
        .filter { it.season != null && it.episode != null }
        .map { v -> Episode(
            id = v.id, season = v.season!!, number = v.episode!!,
            name = v.name ?: v.title ?: "Episode ${v.episode}",
            overview = v.overview, thumbnail = v.thumbnail, released = v.released,
        ) }
        .sortedWith(compareBy({ it.season }, { it.number }))
    // seasons ascending, but specials (0) moved to the end
    val seasons = eps.map { it.season }.distinct().sortedWith(
        compareBy({ if (it == 0) 1 else 0 }, { it })
    )
    val item = MediaItem(id, type, name, description, poster, background,
        year?.take(4)?.toIntOrNull(), genres, imdbRating)
    return SeriesMeta(item, seasons, eps)
}

@Singleton
class MetaRepository @Inject constructor(
    private val stremioApi: StremioApi,
    private val stremioRepository: StremioRepository,
) {
    suspend fun getSeriesMeta(id: String): SeriesMeta? {
        for (host in CINEMETA_HOSTS) {
            try {
                val meta = stremioApi.getMeta(metaUrl(host, "series", id)).meta ?: continue
                if (meta.videos.isNotEmpty()) return meta.toSeriesMeta()
            } catch (_: Exception) {}
        }
        // last resort: any installed addon that returns series meta with videos
        return try { stremioRepository.getMeta("series", id)?.takeIf { it.videos.isNotEmpty() }?.toSeriesMeta() }
        catch (_: Exception) { null }
    }
    suspend fun getMovieMeta(type: String, id: String): MediaItem? =
        stremioRepository.getMeta(type, id)?.let { m ->
            MediaItem(m.id, m.type, m.name, m.description, m.poster, m.background,
                m.year?.take(4)?.toIntOrNull(), m.genres, m.imdbRating)
        }
}
```

`StremioApi.kt` — add at file end:
```kotlin
val CINEMETA_HOSTS = listOf("https://cinemeta-live.strem.io", "https://v3-cinemeta.strem.fun")
```

- [ ] **Step 4: Run → PASS** (mapping). Same command as Step 2.
- [ ] **Step 5: Failing test — host fallback** `MetaRepositoryFallbackTest.kt` (hand-fake the `StremioApi` interface; first host throws, second returns videos):

```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.data.remote.stremio.*
import com.itrepos.aiotv.data.repository.MetaRepository
import com.itrepos.aiotv.data.repository.StremioRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MetaRepositoryFallbackTest {
    private fun metaWithEp() = StremioMetaResponse(StremioMeta(
        id = "tt1", type = "series", name = "S",
        videos = listOf(StremioVideo(id = "tt1:1:1", season = 1, episode = 1, name = "P"))))

    private class FakeApi(val firstHostThrows: Boolean) : StremioApi {
        var lastUrl: String? = null
        override suspend fun getManifest(url: String) = throw NotImplementedError()
        override suspend fun getCatalog(url: String) = throw NotImplementedError()
        override suspend fun getStreams(url: String) = throw NotImplementedError()
        override suspend fun getMeta(url: String): StremioMetaResponse {
            lastUrl = url
            if (firstHostThrows && url.startsWith(CINEMETA_HOSTS[0])) throw RuntimeException("000")
            return metaWithEp()
        }
    }

    @Test fun fallsBackToSecondHost() = runBlocking {
        val api = FakeApi(firstHostThrows = true)
        // StremioRepository unused on the happy path; pass a real one only if constructible, else this asserts host loop:
        val repo = MetaRepository(api, UnusedStremioRepo)
        val sm = repo.getSeriesMeta("tt1")
        assertNotNull(sm)
        assertEquals(1, sm!!.episodes.size)
        assertEquals(CINEMETA_HOSTS[1], api.lastUrl!!.substringBefore("/meta/"))
    }
    companion object {
        // StremioRepository has no interface; the fallback path never reaches it here, so a throwing stub is fine.
        val UnusedStremioRepo: StremioRepository = StremioRepositoryStub
    }
}
```
> **NOTE for implementer:** `StremioRepository` is a concrete class without an interface, so it can't be cleanly faked. If constructing a stub is awkward, refactor `MetaRepository` to depend on a tiny `SeriesMetaFallback` functional interface for the installed-addon path, OR drop `MetaRepositoryFallbackTest` and instead unit-test the host-loop by extracting `suspend fun fetchFromHosts(hosts, id, fetch: suspend (String)->StremioMeta?): SeriesMeta?` as a pure-ish function taking the fetch lambda. **Prefer the lambda extraction** — it makes the fallback testable without faking either concrete class. Adjust the test to call `fetchFromHosts`.

- [ ] **Step 6: Implement the `fetchFromHosts` lambda extraction** (per the note) so the host-fallback is testable without faking concrete classes; have `getSeriesMeta` delegate to it.
- [ ] **Step 7: Run → PASS.** `./gradlew :app:testDebugUnitTest --tests "com.itrepos.aiotv.MetaRepositoryFallbackTest"`.
- [ ] **Step 8: Build + commit** `./gradlew assembleDebug` then `git commit -am "feat(meta): Cinemeta series meta with host fallback + Episode model"`.

---

### Task 5: Per-episode resume — `progressId` on the Player route

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/player/PlayerScreen.kt`
- (Player progress already reads/writes by the `id` passed in — we route the episode id as that id.)

**Interfaces:**
- Produces: `Screen.Player.createRoute(url: String, title: String, progressId: String = url)`, route `player/{url}/{title}/{progressId}`. `PlayerScreen(url, title, progressId, isTv, onBack)`; progress save + `getStartPosition` use `progressId` (defaults to `url`, so movies/live are unchanged; series pass the episode id `tt…:S:E`).

- [ ] **Step 1: Update `Screen.Player`:**
```kotlin
object Player : Screen("player/{url}/{title}/{progressId}") {
    fun createRoute(url: String, title: String, progressId: String = url) =
        "player/${enc(url)}/${enc(title)}/${enc(progressId)}"
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
```
- [ ] **Step 2: Update `AppNavigation` Player composable** to decode `progressId` and pass it to `PlayerScreen`.
- [ ] **Step 3: Update `PlayerScreen`** signature to accept `progressId: String`, and use it where it currently uses `url` for `getStartPosition(...)` and `saveProgress(...)` (keep `url` for ExoPlayer media). All existing callers compile unchanged (default `progressId = url`).
- [ ] **Step 4: Build** `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** `git commit -am "feat(player): progressId route param so series resume keys on episode id, not volatile resolve URL"`.

---

### Task 6: DetailViewModel rework + StreamResolver (movie/series branch, auto-play, resume)

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailViewModel.kt`
- Create: `app/src/main/java/com/itrepos/aiotv/domain/usecase/ResolveStreamUseCase.kt`

**Interfaces:**
- Consumes: `MetaRepository`, `GetStreamsUseCase`, `StreamRanker`, `ResolveStreamUseCase`, `WatchProgressStore`.
- Produces: `DetailState` gains `kind: DetailKind { MOVIE, SERIES }`, `series: SeriesMeta?`, `selectedSeason: Int?`, `episodeStreams: List<Stream>`, `sourcesForEpisode: Episode?`, `resolvingEpisode: Episode?`, plus existing movie fields. Methods: `load(type, id)`, `selectSeason(Int)`, `playEpisode(Episode, onPlay: (url, title, progressId)->Unit)`, `showSources(Episode)`, `playSpecificStream(Stream, Episode, onPlay)`. `ResolveStreamUseCase`: `suspend operator fun invoke(stream: Stream): Result<String>`.

Behaviour:
- `load`: if `type == "series"` → `MetaRepository.getSeriesMeta(id)` → set `series`, `selectedSeason = seasons.first()`. Else movie path (existing meta+streams).
- `playEpisode`: `getStreams("series", ep.id)` → `StreamRanker.rank` → set `episodeStreams`; pick candidates in ranked order, prefer cached; `ResolveStreamUseCase` each with a short timeout, **auto-advance** on failure; on success call `onPlay(url, "<series> S{season}·E{number}", ep.id)`. If none resolve → `showSources(ep)`.
- `ResolveStreamUseCase`: `stream.url?.let { Result.success(it) } ?: <existing TorBox createTorrent→poll→getDownloadUrl path> ` wrapped in try/Result.

- [ ] **Step 1: Implement `ResolveStreamUseCase.kt`** (extract the existing resolve logic from the old `DetailViewModel.resolveStream`):
```kotlin
package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.TorBoxRepository
import com.itrepos.aiotv.domain.model.Stream
import javax.inject.Inject

class ResolveStreamUseCase @Inject constructor(private val torBox: TorBoxRepository) {
    suspend operator fun invoke(stream: Stream): Result<String> = runCatching {
        stream.url ?: run {
            val hash = stream.infoHash ?: error("Stream has no URL or info hash")
            val torrentId = torBox.createTorrent("magnet:?xt=urn:btih:$hash") ?: error("Failed to create torrent")
            val info = torBox.pollUntilReady(torrentId) ?: error("Torrent did not become ready")
            val fileId = info.files.firstOrNull()?.id ?: error("Torrent has no playable files")
            torBox.getDownloadUrl(torrentId, fileId)
        }
    }
}
```
- [ ] **Step 2: Rework `DetailViewModel`** per the Behaviour above (full state machine; movie path preserved; series path added; auto-advance loop over `StreamRanker.rank(...)`; `withTimeoutOrNull(20_000)` around each url-shape resolve for fast auto-advance; resume `progressId = ep.id`).
- [ ] **Step 3: Build** `./gradlew assembleDebug` → `BUILD SUCCESSFUL` (UI in Task 7 will consume the new state; keep the old `DetailScreen` compiling by temporarily rendering movie-only until Task 7, or land 6+7 together).
- [ ] **Step 4: Commit** `git commit -am "feat(detail): VM branches movie/series, per-episode auto-play with auto-advance + episode-keyed resume"`.

> Validation deferred to Task 8 (emulator). VM logic leans on the already-tested `StreamRanker`/parsing/mapping.

---

### Task 7: Detail UI — dispatch + MovieDetail + SeriesDetail + SourcesSheet

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailScreen.kt` (branch on `state.kind`)
- Create: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/MovieDetail.kt` (move current stream-list UI here)
- Create: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/SeriesDetail.kt`
- Create: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/SourcesSheet.kt`
- Create: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/EpisodeRow.kt`

**Interfaces:**
- Consumes: `DetailViewModel` state/methods (Task 6), `Episode`/`SeriesMeta`, theme tokens, Coil `AsyncImage` (pattern: `model`, `ContentScale.Crop`, `ColorPainter(SurfaceElevated)` fallback).
- Produces: composables `SeriesDetail(state, isTv, onPlayEpisode, onShowSources, onSelectSeason, onBack)`, `SourcesSheet(streams, onPick, onDismiss)`, `EpisodeRow(episode, progressFraction, onClick)`.

Build per the spec §7 wireframes: hero backdrop (`AsyncImage` of `backdropUrl ?: posterUrl` + scrim), title + `year · genres · ★rating`, Play/Resume button (red `primary`), overview, **season selector** (Material3 dropdown or segmented row; specials labeled "Specials"), **episode list** (`EpisodeRow`: thumbnail, `N. Title`, runtime/released, resume progress bar via `WatchProgressStore.getProgress(ep.id)`), "Sources" affordance per episode. Two-pane at `maxWidth >= 840.dp` (reuse existing `BoxWithConstraints` split). TV: horizontal focusable episode row + `focusRequester` first focus on Play (match existing focus pattern in `DetailScreen`). `SourcesSheet` = `ModalBottomSheet` on phone, side list on TV; rows show quality + `[TB+]` cached badge (green `CachedBadge`) + size + seeders.

- [ ] **Step 1:** Extract current movie UI from `DetailScreen.kt` into `MovieDetail.kt` (no behaviour change).
- [ ] **Step 2:** Implement `EpisodeRow.kt`, `SourcesSheet.kt`, `SeriesDetail.kt`.
- [ ] **Step 3:** `DetailScreen` branches: `when (state.kind) { MOVIE -> MovieDetail(...); SERIES -> SeriesDetail(...) }`; loading/empty/error states preserved.
- [ ] **Step 4: Build** `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Validate UI on emulator (VPN off)** — install; Search → "How I Met Your Mother" → screenshot Detail (backdrop, meta, overview, Season 1, episode thumbnails). Screenshot phone portrait + landscape; then `aiotv_tv` (D-pad focus) + `aiotv_fold` (two-pane).
- [ ] **Step 6: Commit** `git commit -am "feat(detail): Netflix-style series Detail — hero, season selector, episode list, Sources sheet"`.

---

### Task 8: End-to-end validation + docs

**Files:**
- Modify: `DESIGN.md`, `TODO.md`, `docs/superpowers/specs/2026-06-22-app-shell-visual-refresh.md`

- [ ] **Step 1: Confirm Search/Home route type correctly.** Verify `SearchScreen.kt:134` / Home pass `item.type` into `Screen.Detail.createRoute(type, id)` (they do) so series reach the series path. Fix any movie-only assumption.
- [ ] **Step 2: Full manual validation (emulator, VPN off; restart with `-dns-server` if a fetch 403s/UnknownHosts):**
  - Search → HIMYM → Detail renders meta + Season 1 + episodes.
  - Tap S1E1 → "Finding a working source…" → **plays** (screenshot player). Tap S1E2 from picker → plays.
  - Switch to Season 2 → episodes repopulate.
  - Partially watch S1E1, back out, reopen → resume bar shows; Play = "Resume S1·E1"; resumes at position.
  - "Sources" on an episode → sheet lists ranked sources (cached badged) → manual pick plays.
  - A movie (e.g. cached *Masters of the Universe*) still plays.
  - Theme: screenshot Home/Search/Live/Detail/Player — all dark + red.
- [ ] **Step 3: Update docs:** `DESIGN.md` (series spine + Detail + theme-foundation-pulled-forward; Home redesign still pending; "Streaming Catalogs" addon = its catalog source). `TODO.md` (check off series spine; add fast-follow "auto-next-episode/binge via bingeGroup"; theme foundation done). app-shell/visual-refresh spec (record theme foundation + Detail page delivered here; remaining = nav shell, icon, per-screen polish).
- [ ] **Step 4: Commit** `git commit -am "docs: series spine shipped — update DESIGN/TODO + visual-refresh scope"`.

---

## Self-Review

**Spec coverage:** §1 root cause → T2/T4/T6 (per-episode + Cinemeta). §4 architecture units → MetaRepository(T4), StreamRanker(T3), StreamResolver/ResolveStreamUseCase(T6), DetailViewModel(T6), split Composables(T7), theme(T1). §5 data flow → T6. §6 episode action (auto-play cached/auto-advance/Sources) → T6. §7 wireframes → T7. §8 theme → T1. §9 error handling → T4 (host fallback), T6 (auto-advance), T7 (empty/error states). §10 testing → T2/T3/T4 unit + T7/T8 emulator. §11 docs → T8. **All covered.**

**Placeholder scan:** No TBD/“handle errors”/“similar to”. Logic tasks carry full code + tests; UI tasks carry structure + exact composable signatures + screenshot acceptance (an accepted adaptation for Compose UI, stated in Global Constraints).

**Type consistency:** `Stream` fields (T2) used by `StreamRanker` (T3) and `ResolveStreamUseCase` (T6). `Episode`/`SeriesMeta`/`toSeriesMeta` (T4) used by VM (T6) + UI (T7). `Screen.Player.createRoute(url,title,progressId=url)` (T5) used by VM playback callback (T6). `MetaRepository.getSeriesMeta`/`getMovieMeta` (T4) consumed by VM (T6). Consistent.

**Known adaptation (documented):** `MetaRepository` host-fallback is tested via an extracted `fetchFromHosts(hosts, fetch)` lambda (Task 4 note) because concrete repos can't be faked under JUnit-only. TorBox hash-shape resolution is validated on-device, not unit-tested (no mockk).
