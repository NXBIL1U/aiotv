# Client-aware Source Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-pick the best *device-compatible, streamable* source (movies auto-play like series), never a buffer-prone REMUX, by raising the Torrentio candidate limit, parsing codec/HDR/source, probing device decode capability, and a device-aware size-capped ranker.

**Architecture:** A pure `StreamRequestTuning` raises the Torrentio `limit` so streamable encodes enter the pool. `StreamParsing` gains codec/HDR/source extraction feeding new `Stream` fields. A `DeviceCapabilities` singleton probes decodable codecs + max resolution + HDR into a `DeviceProfile`. `StreamRanker` is rewritten to sort eligible-first (decodable + ≤ screen/decode res + ≤ 20 GB) then quality→streamability→reliability; auto-pick resolves only the eligible subset (`isAutoEligible`), the manual list shows all. Movies get a Play button wired to `PlaybackController.startMovieAuto` (reusing the series `resolveFrom` engine).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Media3/ExoPlayer 1.5.1, Retrofit/kotlinx, JUnit4 (no mockk).

## Global Constraints

- Build directly on **`feat/binge-watch`** (no new branch); commit locally after each task; owner merges.
- **No new dependencies.** Android `MediaCodecList`/`Display` + Media3 are already available.
- **Adopt MIT logic only**: parse-torrent-title regexes → `StreamParsing`; AutoStream heuristic → `StreamRanker`. No GPL code.
- **20 GB is a global hard cap** on *auto*-picked sources (`SIZE_CAP_BYTES = 20L * 1024 * 1024 * 1024`); over-cap stays manually selectable.
- **Resolution = capability**: effective ceiling = `min(decodable res, screen res)`; target = `min(preferredQuality, maxResolution)`.
- **No device tier** (TV/handheld) — the flat cap replaces it.
- Pure logic is **TDD (JUnit4 + `runBlocking`, hand-fakes, no mockk)** in `app/src/test/java/com/itrepos/aiotv/`. Android-API code (`DeviceCapabilities`) is validated by emulator smoke, not unit tests.
- Build env: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"`. Build: `./gradlew assembleDebug`. Unit tests: `./gradlew testDebugUnitTest`.
- Emulator smoke is **VPN OFF** (strem.fun Cloudflare-403s on the VPN IP); restart with `-dns-server 8.8.8.8,8.8.4.4` if a fetch 403s. Phone emulator `emulator-5554`.
- Subagents run in **this workspace (no isolation)** → commits land on `feat/binge-watch`. Verify HEAD advances after each task.

---

### Task 1: Torrentio limit tuning (`StreamRequestTuning`)

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/StreamRequestTuning.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/data/repository/StremioRepository.kt:68` (the `getStreams` request)
- Test: `app/src/test/java/com/itrepos/aiotv/StreamRequestTuningTest.kt`

**Interfaces:**
- Produces: `object StreamRequestTuning { fun tuneStreamBase(baseUrl: String): String }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamRequestTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRequestTuningTest {
    private val tok = "f64ec1c1-d30c-4aa2-8207-6eda8a579a25"

    @Test fun raisesLimitAndPreservesTokenAndOptions() {
        val base = "https://torrentio.strem.fun/sort=qualitysize%7Climit=5%7Cqualityfilter=cam,screener,3d%7Cdebridoptions=nocatalog,nodownloadlinks%7Ctorbox=$tok"
        val out = StreamRequestTuning.tuneStreamBase(base)
        assertTrue("limit raised", out.contains("limit=30"))
        assertTrue("no stale limit=5", !out.contains("limit=5"))
        assertTrue("token preserved", out.contains("torbox=$tok"))
        assertTrue("sort preserved", out.contains("sort=qualitysize"))
        assertTrue("qualityfilter preserved", out.contains("qualityfilter=cam,screener,3d"))
    }

    @Test fun injectsLimitWhenAbsent() {
        val base = "https://torrentio.strem.fun/torbox=$tok"
        val out = StreamRequestTuning.tuneStreamBase(base)
        assertTrue("limit injected", out.contains("limit=30"))
        assertTrue("token preserved", out.contains("torbox=$tok"))
    }

    @Test fun leavesNonTorrentioUnchanged() {
        val base = "https://other-addon.example.com/some/path"
        assertEquals(base, StreamRequestTuning.tuneStreamBase(base))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.itrepos.aiotv.StreamRequestTuningTest"`
Expected: FAIL — `StreamRequestTuning` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.itrepos.aiotv.domain

/**
 * Tunes a Torrentio addon base URL before a /stream request so the candidate pool includes
 * streamable encodes (not just the 5 biggest REMUX). Only touches `limit`; every other option
 * (the `torbox=` token, `sort`, `qualityfilter`, `debridoptions`) is preserved. Non-Torrentio
 * addons are returned unchanged. The options live in Torrentio's path segment, `|`-separated
 * (URL-encoded as %7C), e.g. .../sort=qualitysize%7Climit=5%7Ctorbox=<token>.
 */
object StreamRequestTuning {
    private const val DESIRED_LIMIT = 30
    private val limitRe = Regex("limit=\\d+")

    fun tuneStreamBase(baseUrl: String): String {
        if (!baseUrl.contains("torrentio", ignoreCase = true)) return baseUrl
        if (limitRe.containsMatchIn(baseUrl)) {
            return limitRe.replace(baseUrl, "limit=$DESIRED_LIMIT")
        }
        // No limit option present — inject one right after the host as a new option segment.
        val schemeEnd = baseUrl.indexOf("://").let { if (it >= 0) it + 3 else 0 }
        val hostEnd = baseUrl.indexOf('/', schemeEnd)
        return if (hostEnd >= 0) {
            baseUrl.substring(0, hostEnd) + "/limit=$DESIRED_LIMIT" + baseUrl.substring(hostEnd)
        } else {
            "$baseUrl/limit=$DESIRED_LIMIT"
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.itrepos.aiotv.StreamRequestTuningTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Wire into the repository**

In `StremioRepository.getStreams` (line ~68), change the request to use the tuned base. Add the import `import com.itrepos.aiotv.domain.StreamRequestTuning`.

```kotlin
    suspend fun getStreams(type: String, id: String): List<Stream> {
        val streams = mutableListOf<Stream>()
        getManifests().forEach { (baseUrl, _) ->
            try {
                val resp = stremioApi.getStreams(streamUrl(StreamRequestTuning.tuneStreamBase(baseUrl), type, id))
                streams += resp.streams.map { it.toStream() }
            } catch (_: Exception) {}
        }
        return streams
    }
```

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Emulator smoke — confirm more candidates appear**

```bash
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
# open the "Her" movie in the app; expect the Streams list to now show ~18 entries incl. small encodes
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 logcat -d | grep -i "torrentio.*stream/movie/tt1798709" | tail -2
```
Expected: the request URL contains `limit=30`; the movie's Streams list shows many entries (not only 24–34 GB REMUX).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/itrepos/aiotv/domain/StreamRequestTuning.kt \
        app/src/test/java/com/itrepos/aiotv/StreamRequestTuningTest.kt \
        app/src/main/java/com/itrepos/aiotv/data/repository/StremioRepository.kt
git commit -m "feat(streams): raise Torrentio limit so streamable encodes enter the pool"
```

---

### Task 2: Release parsing — codec / HDR / source (`StreamParsing` + `Stream`)

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/domain/model/Stream.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/domain/StreamParsing.kt`
- Modify: `app/src/main/java/com/itrepos/aiotv/data/repository/StremioRepository.kt` (`toStream()`)
- Test: `app/src/test/java/com/itrepos/aiotv/StreamParsingTest.kt` (extend)

**Interfaces:**
- Produces: enums `Codec { AVC, HEVC, AV1, UNKNOWN }`, `Hdr { SDR, HDR10, DOLBY_VISION, UNKNOWN }`, `SourceType { REMUX, BLURAY, WEBDL, WEBRIP, HDTV, UNKNOWN }`; `Stream.codec/hdr/source`; `StreamParsing.codec(text)`, `.hdr(text)`, `.sourceType(text)`.
- Consumes: nothing new.

- [ ] **Step 1: Write the failing test (append to `StreamParsingTest.kt`)**

```kotlin
import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Hdr
import com.itrepos.aiotv.domain.model.SourceType
// ... inside class StreamParsingTest:

@Test fun parsesCodec() {
    assertEquals(Codec.AVC, StreamParsing.codec("Her 2013 1080p BluRay REMUX AVC DTS-HD MA"))
    assertEquals(Codec.AVC, StreamParsing.codec("Her.2013.1080p.BluRay.x264-SPARKS"))
    assertEquals(Codec.HEVC, StreamParsing.codec("Her 2013 1080p BluRay x265-YAWNTiC"))
    assertEquals(Codec.HEVC, StreamParsing.codec("Movie 2160p HDR10 HEVC"))
    assertEquals(Codec.AV1, StreamParsing.codec("Movie 1080p WEB-DL AV1"))
    assertEquals(Codec.UNKNOWN, StreamParsing.codec("Movie 1080p BluRay"))
}

@Test fun parsesHdr() {
    assertEquals(Hdr.HDR10, StreamParsing.hdr("Movie 2160p HDR10 HEVC"))
    assertEquals(Hdr.DOLBY_VISION, StreamParsing.hdr("Movie 2160p DV Dolby Vision HEVC"))
    assertEquals(Hdr.UNKNOWN, StreamParsing.hdr("Her 2013 1080p BluRay x264"))
}

@Test fun parsesSourceType() {
    assertEquals(SourceType.REMUX, StreamParsing.sourceType("Her 2013 1080p BluRay REMUX AVC"))
    assertEquals(SourceType.WEBDL, StreamParsing.sourceType("Movie 1080p WEB-DL x264"))
    assertEquals(SourceType.WEBRIP, StreamParsing.sourceType("Movie 1080p WEBRip x265"))
    assertEquals(SourceType.BLURAY, StreamParsing.sourceType("Her.2013.1080p.BluRay.x264-SPARKS"))
    assertEquals(SourceType.UNKNOWN, StreamParsing.sourceType("Some random title"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.itrepos.aiotv.StreamParsingTest"`
Expected: FAIL — `Codec`/`Hdr`/`SourceType` and the new functions unresolved.

- [ ] **Step 3: Add the enums and `Stream` fields**

In `Stream.kt`, add the enums (below the existing `Quality` enum) and three fields:

```kotlin
enum class Codec { AVC, HEVC, AV1, UNKNOWN }
enum class Hdr { SDR, HDR10, DOLBY_VISION, UNKNOWN }
enum class SourceType { REMUX, BLURAY, WEBDL, WEBRIP, HDTV, UNKNOWN }
```

Add to the `Stream` data class (after `bingeGroup`):

```kotlin
    val codec: Codec = Codec.UNKNOWN,
    val hdr: Hdr = Hdr.UNKNOWN,
    val source: SourceType = SourceType.UNKNOWN,
```

- [ ] **Step 4: Implement the parsers (append to `StreamParsing`)**

```kotlin
    // parse-torrent-title patterns (MIT), trimmed to what the ranker needs.
    private val hevcRe = Regex("\\bx265\\b|\\bh\\.?265\\b|\\bHEVC\\b", RegexOption.IGNORE_CASE)
    private val avcRe  = Regex("\\bx264\\b|\\bh\\.?264\\b|\\bAVC\\b", RegexOption.IGNORE_CASE)
    private val av1Re  = Regex("\\bAV1\\b", RegexOption.IGNORE_CASE)
    private val dvRe   = Regex("\\bDV\\b|\\bDolby\\s?Vision\\b|\\bDoVi\\b", RegexOption.IGNORE_CASE)
    private val hdr10Re = Regex("\\bHDR10(\\+)?\\b|\\bHDR\\b", RegexOption.IGNORE_CASE)
    private val remuxRe  = Regex("\\bREMUX\\b", RegexOption.IGNORE_CASE)
    private val webdlRe  = Regex("\\bWEB[-. ]?DL\\b|\\bWEBDL\\b|\\bAMZN\\b|\\bNF\\b", RegexOption.IGNORE_CASE)
    private val webripRe = Regex("\\bWEB[-. ]?Rip\\b|\\bWEBRip\\b", RegexOption.IGNORE_CASE)
    private val blurayRe = Regex("\\bBlu[-. ]?Ray\\b|\\bBDRip\\b|\\bBRRip\\b|\\bBDRemux\\b", RegexOption.IGNORE_CASE)
    private val hdtvRe   = Regex("\\bHDTV\\b", RegexOption.IGNORE_CASE)

    fun codec(text: String?): Codec {
        val t = text ?: return Codec.UNKNOWN
        return when {
            av1Re.containsMatchIn(t)  -> Codec.AV1
            hevcRe.containsMatchIn(t) -> Codec.HEVC
            avcRe.containsMatchIn(t)  -> Codec.AVC
            else -> Codec.UNKNOWN
        }
    }

    fun hdr(text: String?): Hdr {
        val t = text ?: return Hdr.UNKNOWN
        return when {
            dvRe.containsMatchIn(t)    -> Hdr.DOLBY_VISION
            hdr10Re.containsMatchIn(t) -> Hdr.HDR10
            else -> Hdr.UNKNOWN
        }
    }

    fun sourceType(text: String?): SourceType {
        val t = text ?: return SourceType.UNKNOWN
        return when {
            remuxRe.containsMatchIn(t)  -> SourceType.REMUX
            webdlRe.containsMatchIn(t)  -> SourceType.WEBDL
            webripRe.containsMatchIn(t) -> SourceType.WEBRIP
            blurayRe.containsMatchIn(t) -> SourceType.BLURAY
            hdtvRe.containsMatchIn(t)   -> SourceType.HDTV
            else -> SourceType.UNKNOWN
        }
    }
```

Add the import `import com.itrepos.aiotv.domain.model.Codec` (and `Hdr`, `SourceType`) at the top of `StreamParsing.kt`.

> Note: `webdlRe` is checked **before** `blurayRe` so a "BluRay REMUX" still classifies as REMUX (remux first), and a plain "WEB-DL" isn't swallowed. REMUX is matched first of all.

- [ ] **Step 5: Fill the fields in `toStream()`**

In `StremioRepository.toStream()`, add (after `bingeGroup = ...`):

```kotlin
            codec = StreamParsing.codec(detectText),
            hdr = StreamParsing.hdr(detectText),
            source = StreamParsing.sourceType(detectText),
```

- [ ] **Step 6: Run tests + build**

Run: `./gradlew testDebugUnitTest --tests "com.itrepos.aiotv.StreamParsingTest"` → PASS.
Run: `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/itrepos/aiotv/domain/model/Stream.kt \
        app/src/main/java/com/itrepos/aiotv/domain/StreamParsing.kt \
        app/src/main/java/com/itrepos/aiotv/data/repository/StremioRepository.kt \
        app/src/test/java/com/itrepos/aiotv/StreamParsingTest.kt
git commit -m "feat(streams): parse codec/HDR/source into Stream (parse-torrent-title patterns, MIT)"
```

---

### Task 3: Device capability probe (`DeviceCapabilities` + `DeviceProfile`)

**Files:**
- Create: `app/src/main/java/com/itrepos/aiotv/domain/playback/DeviceProfile.kt`
- Create: `app/src/main/java/com/itrepos/aiotv/domain/playback/DeviceCapabilities.kt`
- Test: none (Android `MediaCodecList`/`Display` APIs — validated by emulator smoke).

**Interfaces:**
- Produces: `data class DeviceProfile(maxResolution: Quality, decodableCodecs: Set<Codec>, hdrCapable: Boolean)`; `DeviceProfile.PERMISSIVE`; `@Singleton class DeviceCapabilities` with `val profile: DeviceProfile`.
- Consumes: `Quality`, `Codec` (Task 2).

- [ ] **Step 1: Create `DeviceProfile`**

```kotlin
package com.itrepos.aiotv.domain.playback

import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Quality

/** What the current device can actually decode and display. */
data class DeviceProfile(
    val maxResolution: Quality,
    val decodableCodecs: Set<Codec>,
    val hdrCapable: Boolean,
) {
    companion object {
        /** Used as a safe fallback when probing fails, and by the back-compat ranker overload. */
        val PERMISSIVE = DeviceProfile(
            maxResolution = Quality.UHD_2160,
            decodableCodecs = setOf(Codec.AVC, Codec.HEVC, Codec.AV1),
            hdrCapable = true,
        )
    }
}
```

- [ ] **Step 2: Create `DeviceCapabilities`**

```kotlin
package com.itrepos.aiotv.domain.playback

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Quality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probes the device once for what it can decode (codecs + max resolution) and display (screen
 * resolution + HDR), exposing a [DeviceProfile]. Any failure degrades to [DeviceProfile.PERMISSIVE]
 * so ranking never crashes over a capability query.
 */
@Singleton
class DeviceCapabilities @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val profile: DeviceProfile by lazy { runCatching { probe() }.getOrElse {
        Log.w(TAG, "capability probe failed; using permissive profile", it); DeviceProfile.PERMISSIVE
    } }

    private fun probe(): DeviceProfile {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecs = mutableSetOf<Codec>()
        var maxDecodeRank = Quality.SD.rank
        val mimes = mapOf("video/avc" to Codec.AVC, "video/hevc" to Codec.HEVC, "video/av01" to Codec.AV1)
        for ((mime, codec) in mimes) {
            val info = list.codecInfos.firstOrNull { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
                ?: continue
            codecs += codec
            val vc = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() ?: continue
            val w = vc.supportedWidths.upper; val h = vc.supportedHeights.upper
            val rank = qualityRankFor(maxOf(w, h))
            if (rank > maxDecodeRank) maxDecodeRank = rank
        }
        if (codecs.isEmpty()) return DeviceProfile.PERMISSIVE

        val dm = context.resources.displayMetrics
        val screenRank = qualityRankFor(maxOf(dm.widthPixels, dm.heightPixels))
        val maxRank = minOf(maxDecodeRank, screenRank)
        val maxRes = Quality.values().firstOrNull { it.rank == maxRank } ?: Quality.HD_1080

        val hdr = runCatching {
            val display = if (Build.VERSION.SDK_INT >= 30) context.display
                else (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            (display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty()) == true
        }.getOrDefault(false)

        val p = DeviceProfile(maxRes, codecs, hdr)
        Log.i(TAG, "DeviceProfile = $p (decodeMaxRank=$maxDecodeRank, screenRank=$screenRank)")
        return p
    }

    // Map a pixel dimension (longest side) to a Quality rank.
    private fun qualityRankFor(longestSidePx: Int): Int = when {
        longestSidePx >= 3840 -> Quality.UHD_2160.rank
        longestSidePx >= 1920 -> Quality.HD_1080.rank
        longestSidePx >= 1280 -> Quality.HD_720.rank
        else -> Quality.SD.rank
    }

    private companion object { const val TAG = "DeviceCapabilities" }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Fix any import/API mismatch, e.g. `context.display` nullability, before continuing.)

- [ ] **Step 4: Emulator smoke — log the probed profile**

```bash
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 logcat -c
# open any movie in the app (triggers a play path later; for now the probe runs lazily on first rank)
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 logcat -d | grep -i "DeviceCapabilities" | tail -5
```
Expected: a `DeviceProfile = DeviceProfile(maxResolution=HD_1080, decodableCodecs=[AVC, ...], hdrCapable=false)` line on the phone emulator (≈1080p max, likely no 4K). (The profile is read lazily — it appears once `StreamRanker.rank` runs in Task 5; for an early check, temporarily read it from a debug log if needed. Otherwise verify in Task 5's smoke.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/itrepos/aiotv/domain/playback/DeviceProfile.kt \
        app/src/main/java/com/itrepos/aiotv/domain/playback/DeviceCapabilities.kt
git commit -m "feat(playback): DeviceCapabilities probe (decodable codecs, max resolution, HDR)"
```

---

### Task 4: Device-aware `StreamRanker` rewrite

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/domain/StreamRanker.kt`
- Test: `app/src/test/java/com/itrepos/aiotv/StreamRankerDeviceTest.kt` (new); existing `StreamRankerTest`/`StreamRankerQualityTest` must still pass unchanged.

**Interfaces:**
- Produces: `StreamRanker.rank(streams, profile: DeviceProfile, target: Quality): List<Stream>`; `StreamRanker.isAutoEligible(s: Stream, profile: DeviceProfile): Boolean`; `StreamRanker.SIZE_CAP_BYTES`. Keeps the existing `rank(streams, preferred: Quality? = null)` overload (delegates with `DeviceProfile.PERMISSIVE`).
- Consumes: `DeviceProfile` (Task 3), `Codec`/`Quality` (Task 2).

- [ ] **Step 1: Write the failing test (`StreamRankerDeviceTest.kt`)**

```kotlin
package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.playback.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRankerDeviceTest {
    private val GB = 1024L * 1024 * 1024
    private fun s(t: String, q: Quality, codec: Codec, sizeGb: Double, cached: Boolean = true, lang: Int = 2, seed: Int? = 10) =
        Stream(title = t, url = "u/$t", infoHash = null, fileIdx = null, isCached = cached,
            name = t, quality = q, seeders = seed, sizeBytes = (sizeGb * GB).toLong(), languageScore = lang, codec = codec)

    private val phone = DeviceProfile(Quality.HD_1080, setOf(Codec.AVC, Codec.HEVC), hdrCapable = false)
    private val tv = DeviceProfile(Quality.UHD_2160, setOf(Codec.AVC, Codec.HEVC, Codec.AV1), hdrCapable = true)

    @Test fun excludesOver20GbFromAutoPick() {
        val remux = s("1080p REMUX", Quality.HD_1080, Codec.AVC, 24.0)
        assertFalse(StreamRanker.isAutoEligible(remux, phone))
    }

    @Test fun picksStreamableUnderCapOverRemux() {
        val remux = s("1080p REMUX", Quality.HD_1080, Codec.AVC, 24.0)
        val webdl = s("1080p WEB", Quality.HD_1080, Codec.AVC, 8.0)
        val ranked = StreamRanker.rank(listOf(remux, webdl), phone, Quality.HD_1080)
        assertEquals("1080p WEB", ranked.filter { StreamRanker.isAutoEligible(it, phone) }.first().title)
    }

    @Test fun within1080pPrefersSmaller() {
        val big = s("1080p big", Quality.HD_1080, Codec.AVC, 18.0)
        val small = s("1080p small", Quality.HD_1080, Codec.AVC, 5.0)
        assertEquals("1080p small", StreamRanker.rank(listOf(big, small), phone, Quality.HD_1080).first().title)
    }

    @Test fun notUltraLowBitratePotato() {
        val potato = s("1080p potato", Quality.HD_1080, Codec.AVC, 1.5)
        val healthy = s("1080p healthy", Quality.HD_1080, Codec.AVC, 8.0)
        assertEquals("1080p healthy", StreamRanker.rank(listOf(potato, healthy), phone, Quality.HD_1080).first().title)
    }

    @Test fun resolutionCeilingExcludes4kOn1080pProfile() {
        val uhd = s("2160p", Quality.UHD_2160, Codec.HEVC, 18.0)
        assertFalse(StreamRanker.isAutoEligible(uhd, phone))
        assertTrue(StreamRanker.isAutoEligible(uhd, tv))
    }

    @Test fun codecHardFilterDropsUndecodable() {
        val av1 = s("1080p AV1", Quality.HD_1080, Codec.AV1, 6.0)  // phone has no AV1
        assertFalse(StreamRanker.isAutoEligible(av1, phone))
        assertTrue(StreamRanker.isAutoEligible(av1, tv))
    }

    @Test fun allOverCapMeansNoAutoEligible() {
        val a = s("a", Quality.HD_1080, Codec.AVC, 24.0)
        val b = s("b", Quality.HD_1080, Codec.AVC, 30.0)
        assertTrue(StreamRanker.rank(listOf(a, b), phone, Quality.HD_1080).none { StreamRanker.isAutoEligible(it, phone) })
    }

    @Test fun oneSeederPenalised() {
        val oneSeed = s("oneSeed", Quality.HD_1080, Codec.AVC, 8.0, seed = 1)
        val many = s("many", Quality.HD_1080, Codec.AVC, 8.0, seed = 40)
        assertEquals("many", StreamRanker.rank(listOf(oneSeed, many), phone, Quality.HD_1080).first().title)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.itrepos.aiotv.StreamRankerDeviceTest"`
Expected: FAIL — new `rank`/`isAutoEligible` overloads unresolved.

- [ ] **Step 3: Rewrite `StreamRanker.kt`**

```kotlin
package com.itrepos.aiotv.domain

import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.playback.DeviceProfile
import kotlin.math.abs

object StreamRanker {
    const val SIZE_CAP_BYTES = 20L * 1024 * 1024 * 1024 // 20 GB hard cap on auto-picked sources

    /** A source the device can decode + display + stream (≤ 20 GB). Auto-pick uses only these. */
    fun isAutoEligible(s: Stream, profile: DeviceProfile): Boolean =
        (s.codec == Codec.UNKNOWN || s.codec in profile.decodableCodecs) &&
        s.quality.rank <= profile.maxResolution.rank &&
        (s.sizeBytes == null || s.sizeBytes <= SIZE_CAP_BYTES)

    /**
     * Returns the FULL list (nothing dropped) sorted best-for-this-device first, so the manual
     * Sources list can still show every source. Ineligible (over-cap/undecodable/over-res) sink
     * to the bottom. [target] = min(user preferredQuality, profile.maxResolution).
     */
    fun rank(streams: List<Stream>, profile: DeviceProfile, target: Quality): List<Stream> =
        streams.sortedWith(
            compareByDescending<Stream> { isAutoEligible(it, profile) }
                .thenByDescending { it.isCached }
                .thenByDescending { it.languageScore }
                .thenByDescending { qualityFit(it.quality, target) }
                .thenByDescending { streamability(it) }
                .thenByDescending { seederScore(it.seeders) }
        )

    /** Back-compat overload (existing callers/tests): permissive profile, target = preferred or max. */
    fun rank(streams: List<Stream>, preferred: Quality? = null): List<Stream> =
        rank(streams, DeviceProfile.PERMISSIVE, preferred ?: Quality.UHD_2160)

    // Highest quality at/under target wins; anything over target is demoted below at-target picks.
    private fun qualityFit(q: Quality, target: Quality): Int =
        if (q.rank <= target.rank) q.rank else target.rank - 1

    // Within a resolution: prefer a sane bitrate band — penalise both bloat and ultra-low. Higher = better.
    private fun streamability(s: Stream): Int {
        val size = s.sizeBytes ?: return 0 // unknown size: neutral
        val gb = size.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val ideal = when (s.quality) {
            Quality.UHD_2160 -> 15.0
            Quality.HD_1080 -> 8.0
            Quality.HD_720 -> 3.0
            else -> 2.0
        }
        return -abs(gb - ideal).toInt() // 0 at ideal, more negative as it deviates
    }

    private fun seederScore(seeders: Int?): Int = when {
        seeders == null -> 0
        seeders <= 1 -> -1 // 1-seeder penalty (AutoStream)
        else -> seeders
    }
}
```

- [ ] **Step 4: Run all ranker tests (new + existing) + build**

Run: `./gradlew testDebugUnitTest --tests "com.itrepos.aiotv.StreamRanker*"`
Expected: PASS — `StreamRankerDeviceTest` (8), `StreamRankerTest` (2), `StreamRankerQualityTest` (4) all green.
Run: `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/itrepos/aiotv/domain/StreamRanker.kt \
        app/src/test/java/com/itrepos/aiotv/StreamRankerDeviceTest.kt
git commit -m "feat(streams): device-aware ranker with 20GB cap + isAutoEligible (AutoStream heuristic, MIT)"
```

---

### Task 5: Integration — movie auto-pick, Play button, series pass-through

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/domain/playback/PlaybackController.kt` (inject `DeviceCapabilities`; `startMovieAuto`; device-aware `advanceToNextEpisode`)
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailViewModel.kt` (inject `DeviceCapabilities`; `playMovieAuto`; device-aware ranking + `autoCandidates`; series pass-through)
- Modify: `app/src/main/java/com/itrepos/aiotv/data/local/AppDataStore.kt` (`preferredQuality` default → `UHD_2160`)
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailScreen.kt` + `MovieDetail.kt` (▶ Play button)
- Test: none new (integration validated on emulator); all prior unit tests must stay green.

**Interfaces:**
- Consumes: `DeviceCapabilities.profile` (Task 3), `StreamRanker.rank(streams, profile, target)` + `isAutoEligible` (Task 4).
- Produces: `PlaybackController.startMovieAuto(candidates, title, progressId): Boolean`; `DetailViewModel.playMovieAuto(onPlay)`.

- [ ] **Step 1: `PlaybackController` — inject `DeviceCapabilities`, add `startMovieAuto`, make `advanceToNextEpisode` device-aware**

Constructor: add `private val deviceCapabilities: DeviceCapabilities`.

Add method:

```kotlin
    /** Resolve the best of [candidates] (already device-filtered by the caller) and start a movie session. */
    suspend fun startMovieAuto(candidates: List<Stream>, title: String, progressId: String): Boolean {
        val picked = resolveFrom(candidates, 0) ?: return false
        this.series = null
        this.episode = null
        this.candidates = candidates
        this.sourceIndex = picked.second
        this.bingeGroup = candidates.getOrNull(picked.second)?.bingeGroup
        _state.value = PlaybackState(picked.first, title, progressId, upNext = null)
        return true
    }
```

In `advanceToNextEpisode`, replace the rank line so the next episode is device-aware and eligible-filtered:

```kotlin
        val pref = appDataStore.preferredQuality.first()
        val profile = deviceCapabilities.profile
        val target = minQuality(pref, profile.maxResolution)
        val ranked = StreamRanker.rank(raw, profile, target)
            .filter { StreamRanker.isAutoEligible(it, profile) }
            .sortedByDescending { BingeSequencing.isBingeMatch(it.bingeGroup, bingeGroup) }
```

Add a private helper at the bottom of the class:

```kotlin
    private fun minQuality(a: Quality, b: Quality): Quality =
        if (a.rank <= b.rank) a else b
```

Add imports: `import com.itrepos.aiotv.domain.model.Quality`.

> Note: if `ranked` becomes empty (all over cap), `resolveFrom` returns null and `advanceToNextEpisode` returns false → the Player shows the "couldn't load next episode" path that already exists.

- [ ] **Step 2: `DetailViewModel` — inject `DeviceCapabilities`, device-aware ranking, `autoCandidates`, `playMovieAuto`, series pass-through**

Constructor: add `private val deviceCapabilities: DeviceCapabilities`.

Add a private helper and use it in `loadMovie` and `playEpisode`:

```kotlin
    private suspend fun rankFor(raw: List<Stream>): Pair<List<Stream>, List<Stream>> {
        val pref = appDataStore.preferredQuality.first()
        val profile = deviceCapabilities.profile
        val target = if (pref.rank <= profile.maxResolution.rank) pref else profile.maxResolution
        val ranked = StreamRanker.rank(raw, profile, target)
        val auto = ranked.filter { StreamRanker.isAutoEligible(it, profile) }
        return ranked to auto
    }
```

`loadMovie`: replace the `val ranked = StreamRanker.rank(...)` block with device-aware ranking and keep the full list in state:

```kotlin
            val withCached = streams.map { s -> s.copy(isCached = s.isCached || cached[s.infoHash?.lowercase()] == true) }
            val (ranked, _) = rankFor(withCached)
            _state.value = DetailState(
                isLoading = false,
                kind = DetailKind.MOVIE,
                meta = meta,
                streams = ranked,
            )
```

Add `playMovieAuto` (mirrors `playEpisode`'s auto path, but for a movie — `progressId == id`):

```kotlin
    /** Movie Play button: auto-pick the best device-eligible source and play; else leave the list visible. */
    fun playMovieAuto(onPlay: (url: String, title: String, progressId: String) -> Unit) {
        if (_state.value.resolving) return
        val movieId = _state.value.meta?.id ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(resolving = true, error = null)
            val (_, auto) = rankFor(_state.value.streams)
            playbackController.clear()
            val title = _state.value.meta?.name ?: movieId
            val started = auto.isNotEmpty() && playbackController.startMovieAuto(auto, title, movieId)
            _state.value = _state.value.copy(resolving = false)
            if (started) {
                onPlay(playbackController.state.value!!.currentUrl, title, movieId)
            } else {
                // No eligible source resolved — the full sources list stays on screen for a manual pick.
                _state.value = _state.value.copy(error = "No streamable source — pick one below")
            }
        }
    }
```

`playEpisode`: feed the eligible subset to the controller while keeping the full ranked list for the Sources sheet. Replace its rank block:

```kotlin
            val withCached = raw.map { s -> s.copy(isCached = s.isCached || cached[s.infoHash?.lowercase()] == true) }
            val (ranked, auto) = rankFor(withCached)
            _state.value = _state.value.copy(episodeStreams = ranked)
            val series = _state.value.series
            val started = series != null && auto.isNotEmpty() && playbackController.startSeries(series, episode, auto)
```

Add imports if needed (`Stream` already imported).

- [ ] **Step 3: `AppDataStore` — default `preferredQuality` to unrestricted (4K)**

Change the default (line ~47-49) so an unset preference means "allow up to the device max":

```kotlin
    /** The user's preferred quality CEILING for source ranking (default: unrestricted / UHD_2160). */
    val preferredQuality: Flow<Quality> = dataStore.data.map {
        if (it[KEY_PREFERRED_QUALITY] == "1080p") Quality.HD_1080 else Quality.UHD_2160
    }
```

(`setPreferredQuality` already writes `"2160p"`/`"1080p"`; the `SettingsViewModel` default display will now show 4K when unset, which is correct — the user can still pick 1080p to cap.)

- [ ] **Step 4: `MovieDetail` / `DetailScreen` — add the ▶ Play button**

In `DetailScreen.kt`, pass an `onPlayAuto` lambda to `MovieDetail`:

```kotlin
        DetailKind.MOVIE -> MovieDetail(
            state = state,
            fallbackTitle = id,
            onPlayAuto = { viewModel.playMovieAuto(onPlayStream) },
            onPlayStream = { stream ->
                viewModel.resolveStream(stream) { url ->
                    onPlayStream(url, stream.title ?: state.meta?.name ?: id, url)
                }
            },
            onBack = onBack,
        )
```

In `MovieDetail.kt`: add the `onPlayAuto: () -> Unit` parameter, and a primary Play button at the top of both the two-pane left column and the single-column `LazyColumn` (as a new `item(key = "play")` right after the header), focused by default on TV. Example for the single-column branch (place before the description item):

```kotlin
                item(key = "play") {
                    Button(
                        onClick = onPlayAuto,
                        enabled = !state.resolving && state.streams.isNotEmpty(),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .focusRequester(firstStreamFocusRequester),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                }
```

Move the `firstStreamFocusRequester` from the first stream row to this Play button (so D-pad lands on Play first). Add imports: `androidx.compose.material3.Button`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.PlayArrow`, `androidx.compose.material3.Icon`, `androidx.compose.foundation.layout.width`, `androidx.compose.foundation.layout.Spacer`. Keep the streams list below (the "Streams" header label can stay; it is now the manual override).

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Fix any Hilt/constructor or Compose import errors.

- [ ] **Step 6: Run the full unit suite (no regressions)**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all suites green).

- [ ] **Step 7: Emulator smoke — end to end (VPN OFF)**

```bash
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 logcat -c
# In the app: open "Her" → tap ▶ Play
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 logcat -d | grep -iE "DeviceCapabilities|MediaCodecVideoRenderer|ExoPlayerImplInternal" | tail -20
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 exec-out screencap -p > /tmp/her_autopick.png
```
Confirm:
1. `DeviceProfile = …` logged (≈1080p max, AVC present).
2. Tapping **Play** auto-plays — the player title bar shows a **streamable ~6–13 GB** source (e.g. 8.74 GB AVC or 5.85 GB HEVC), **not** the 24 GB REMUX. (Capture the title-bar screenshot.)
3. The movie Detail still lists **all** sources (REMUX included) for manual override.
4. A series episode still auto-plays (regression check); the Sources sheet still opens.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/itrepos/aiotv/domain/playback/PlaybackController.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailViewModel.kt \
        app/src/main/java/com/itrepos/aiotv/data/local/AppDataStore.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/detail/DetailScreen.kt \
        app/src/main/java/com/itrepos/aiotv/ui/screen/detail/MovieDetail.kt
git commit -m "feat(detail): movie Play auto-picks best device-eligible source; series pass-through"
```

---

## Self-Review

**Spec coverage:**
- §3/§4 Torrentio limit tuning → Task 1. ✅
- §4 codec/HDR/source parsing + Stream fields → Task 2. ✅
- §4 DeviceCapabilities/DeviceProfile (codecs/res/HDR, permissive fallback) → Task 3. ✅
- §5 ranker (20 GB cap, isAutoEligible, eligible-first, qualityFit/streamability/seeder) → Task 4. ✅
- §4 movie auto-pick (startMovieAuto + Play button), manual list shows all, series pass-through, device-aware preferredQuality default → Task 5. ✅
- §7 error handling (no-eligible → manual list / Sources sheet; probe failure → permissive) → Task 3 (permissive) + Task 5 (fallbacks). ✅
- §8 tests (tuning, parsing, ranker device cases) → Tasks 1/2/4; emulator smokes per task. ✅

**Placeholder scan:** No TBD/“handle errors” — every code step shows code; magnitudes (limit=30, 20 GB, ideal bands, 1-seeder penalty) are concrete. ✅

**Type consistency:** `rank(streams, profile, target)` + `isAutoEligible(s, profile)` + `SIZE_CAP_BYTES` defined in Task 4, consumed in Task 5; `DeviceProfile(maxResolution, decodableCodecs, hdrCapable)` + `PERMISSIVE` defined Task 3, used Tasks 4/5; `Codec/Hdr/SourceType` + `Stream.codec/hdr/source` defined Task 2, used Tasks 4/5; `startMovieAuto`/`playMovieAuto` defined Task 5. The existing `rank(streams, preferred)` overload is retained so prior tests/callers compile. ✅
