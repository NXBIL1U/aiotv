# Client-aware automatic source selection (design)

Date: 2026-06-22 · Branch: `feat/binge-watch` (built directly on it, stacked after track-selection) · Status: approved-to-spec

## 1. Problem & goal

Two gaps surfaced during track-selection validation:

1. **Movies don't auto-pick.** Tapping a movie shows a **manual list of sources** (`DetailViewModel.loadMovie` →
   ranked list in the UI; comment: *"Behaviour preserved (manual source pick)"*). Only **series** auto-play
   (`playEpisode` → `PlaybackController.startSeries` → resolve best). The two paths are inconsistent.
2. **"Best" is not device-aware.** `StreamRanker` ranks `cached → English → quality → seeders` and the `Stream`
   model carries no codec/HDR/source fields. So it auto-picked a **24.46 GB 1080p BluRay REMUX** for "Her"
   (~26 Mbps avg), which buffers/stutters on the emulator (and strains weak networks anywhere). Worse, the app
   has **no device-capability detection at all** — an earlier play hard-failed with `video/hevc …
   format_supported=NO_EXCEEDS_CAPABILITIES` because nothing checks whether the device can decode a source.

**Goal:** movies auto-play the best source like series, and "best" means **best *for this device*** — never a
file the client can't smoothly decode/stream. Aligns with the north-star ("one app, best UX, most compatible
for the client we're watching from").

## 2. Goals / Non-goals

**Goals:**
- Movies auto-play the best source (reuse the series engine); the **Sources sheet stays** as a manual override
  (a button on Detail + the automatic fallback when nothing resolves).
- A **device-tier-adaptive** ranker: per-device ceiling from real capability, then highest quality under the
  ceiling, then streamability (avoid REMUX bloat), then reliability (cached, seeders), keeping English preference.
- A reusable `DeviceCapabilities` probe (codecs, max resolution, HDR, device tier).
- Richer release parsing (codec / HDR / source type) feeding the ranker.

**Non-goals (v1):**
- **Candidate-list composition.** We rank whatever the Torrentio addon returns. The owner's addon is configured
  `sort=qualitysize | limit=5`, so it returns only the **5 biggest files** — for some titles (e.g. "Her") the
  list may contain *no* streamable encode, and no ranker can conjure one. **Surfacing more/smaller candidates
  (bump `limit`, or `sort=quality`) is a related follow-up**, tracked separately (it edits the stored addon
  config, a different concern). See §10.
- **Real network-bandwidth measurement.** We infer network tolerance from **device tier** (TV ⇒ assume strong/
  wired; handheld ⇒ assume variable). No active throughput probing in v1.
- **Per-stream track UI / quality switching mid-play** — that's the (separate) track-selection work.
- **Live IPTV** path — untouched (no candidate list; single URL).

## 3. Build-vs-adopt (validated by web research, 2026-06-22)

| Piece | Decision | Source / license |
|---|---|---|
| Release parsing (codec/HDR/source/bitdepth) | **Adopt — port the regex rules** | [parse-torrent-title](https://github.com/platelminto/parse-torrent-title) (**MIT**) — the de-facto Stremio-ecosystem parser (Torrentio/Comet/AIOStreams). Pure regex; port ~10 rules into `StreamParsing`. |
| Scoring heuristic (quality tiers, 1-seeder penalty, "4K-too-slow → 1080p") | **Adopt — copy logic** | [AutoStream](https://github.com/keypop3750/AutoStream) (**MIT**) → `StreamRanker`. |
| Device-capability probe | **Build** (no OSS fits — server-side pickers are device-blind) | Android `MediaCodecList` / Media3 `MediaCodecUtil`, `Display.getHdrCapabilities()`. |
| Movie auto-pick engine | **Reuse our own** | `PlaybackController.resolveFrom(candidates, 0)` (already powers series). |
| CloudStream / Aniyomi prioritisation | **Patterns-only / cautionary** (GPL; [prioritisation is buggy](https://github.com/recloudstream/cloudstream/issues/1114)) | no code copied. |

**Key finding:** every server-side picker (AutoStream, Torrentio's `sort=`, AIOStreams) is **blind to the client
device** — they run in the cloud and can't know what a Fire TV vs. a phone can decode. So "most compatible for
the client" **must be client-side, in our app**. That is the differentiator and the reason `DeviceCapabilities`
is build-not-adopt.

## 4. Architecture

| Unit | Responsibility |
|---|---|
| `StreamParsing.kt` (modify) | Add `codec(text): Codec`, `hdr(text): Hdr`, `sourceType(text): SourceType`. Regexes ported from parse-torrent-title (MIT). Existing `quality`/`seeders`/`sizeBytes`/`languageScore`/`isTbCached` unchanged. |
| `Stream.kt` (modify) | Add `codec: Codec = UNKNOWN`, `hdr: Hdr = UNKNOWN`, `source: SourceType = UNKNOWN`. Add enums `Codec { AVC, HEVC, AV1, UNKNOWN }`, `Hdr { SDR, HDR10, DOLBY_VISION, UNKNOWN }`, `SourceType { REMUX, BLURAY, WEBDL, WEBRIP, HDTV, UNKNOWN }`. |
| `DeviceCapabilities` (new, `@Singleton`, `domain/playback`) | Probes the device **once** and exposes a `DeviceProfile`. Codecs + max decode resolution via `MediaCodecList(REGULAR_CODECS)` / Media3 `MediaCodecUtil.getDecoderInfos(mime,…)`; screen resolution via `Display.getMode()`; HDR via `Display.getHdrCapabilities()`; tier via the existing `isTv` signal (`UiModeManager.UI_MODE_TYPE_TELEVISION`). |
| `DeviceProfile` (data) | `maxResolution: Quality` (min of screen res and decodable res), `decodableCodecs: Set<Codec>`, `hdrCapable: Boolean`, `tier: DeviceTier { TV, HANDHELD }`. |
| `StreamRanker.kt` (rewrite) | `rank(streams, profile: DeviceProfile, target: Quality): List<Stream>` — algorithm in §5. A `preferred`-only overload is kept for any non-VOD caller (delegates with a permissive profile) so nothing else breaks. |
| `PlaybackController.kt` (modify) | **Inject `DeviceCapabilities`.** Add `suspend startMovieAuto(candidates, title, progressId): Boolean` → `resolveFrom(candidates, 0)` then set a movie `PlaybackState` (`upNext = null`). Update `advanceToNextEpisode`'s internal `StreamRanker.rank(...)` call to pass the `DeviceProfile` + target so the next episode is device-aware too. The existing passive `startMovie(...)` (already-resolved URL, manual pick) stays for the Sources sheet. |
| `DetailViewModel.kt` (modify) | `loadMovie`: rank with `DeviceProfile` + target, call `startMovieAuto`, and `onPlay(...)` on success; on failure fall back to the Sources sheet (mirrors `playEpisode`). `playEpisode`/`advanceToNextEpisode` pass the `DeviceProfile` too so series benefit. `AppDataStore.preferredQuality` default becomes device-aware (§5). |
| `AppDataStore.kt` (modify) | `preferredQuality` default resolves to **4K on a 4K-capable TV tier, 1080p otherwise** (device-aware default; the user's explicit Settings choice still wins once set). |

## 5. Ranking policy

```
ceiling = min(userTarget, profile.maxResolution)          // userTarget = preferredQuality setting

streams
  .filter { decodable(it.codec, profile) && it.quality.rank <= profile.maxResolution.rank }   // HARD FILTER
  .sortedWith(
      compareByDescending { it.isCached }                 // 1. cached debrid first (uncached = slow)
      .thenByDescending    { it.languageScore }            // 2. English preference (existing)
      .thenByDescending    { qualityFit(it, ceiling) }     // 3. highest quality at/under ceiling
      .thenByDescending    { streamability(it, profile) }  // 4. tier-dependent bloat handling
      .thenByDescending    { seederScore(it) }             // 5. healthy sources (1-seeder penalty)
  )
```

- **`decodable`** — `codec == UNKNOWN` (can't tell ⇒ keep) **or** `codec ∈ profile.decodableCodecs`. Drops a
  HEVC/AV1 source on a device with no such decoder (prevents the `NO_EXCEEDS_CAPABILITIES` hard-fail).
- **`qualityFit`** — quality at/under the ceiling scores by `quality.rank`; over-ceiling is already filtered.
- **`streamability` (tier-dependent — the §3-of-design crux):**
  - **HANDHELD:** strong **bloat demotion** — a REMUX (and any source whose `sizeBytes` is far above a sane band
    for its resolution) is demoted so a clean WEB-DL/encode of the **same or even one-lower** resolution outranks
    it. This is what stops a phone auto-picking a 24 GB 1080p REMUX.
  - **TV:** REMUX is acceptable (assumed strong network) — only a mild size tiebreak applies, so the high-quality
    pick is preserved. (Size bands + penalty magnitudes are fixed in the plan, with unit tests.)
- **`hdr`** — an HDR source on a non-HDR screen is **kept but penalised** (plays, looks washed out) so an SDR
  equivalent wins when present; never hard-dropped.
- **`seederScore`** — seeders descending; **1-seeder = penalty** (AutoStream). Cached `[TB+]` already dominates
  via step 1, so seeders mostly tiebreak uncached candidates.

**Effect:** phone/Fold → a streamable ≤1080p encode; 4K-capable TV → up to 2160p the device can actually decode;
neither auto-picks an undecodable codec; REMUX reachable only via the manual Sources sheet on handhelds.

## 6. Data flow

```
DetailViewModel.loadMovie(id)
  → GetStreamsUseCase → raw candidates (each parsed: quality/codec/hdr/source/size/seeders/lang/cached)
  → target = preferredQuality (device-aware default) ; profile = DeviceCapabilities.profile
  → StreamRanker.rank(raw, profile, target)            // device-tier adaptive
  → PlaybackController.startMovieAuto(ranked, title, progressId=id)
        → resolveFrom(ranked, 0)  (instant for [TB+] url-shape; withTimeout for hash-shape)
        → success: PlaybackState(currentUrl, title, progressId, upNext=null) ; onPlay(...)
        → all fail: showSources(...)   // manual fallback (same as series)
Series: playEpisode / advanceToNextEpisode pass the same profile into rank() — series get the upgrade too.
Live IPTV: unchanged (no candidate list).
```

## 7. Error handling

- **Nothing resolves** → fall back to the **Sources sheet** (existing behaviour for series; new for movies).
- **All candidates filtered out** (no decodable source within the device's resolution) → keep the *least-bad*
  (don't return empty): fall back to ranking ignoring the hard filter so the user still gets *something* + the
  Sources sheet; log it. A device with literally no decoder for any candidate is rare.
- **`DeviceCapabilities` probe throws / returns sparse data** → degrade to a **permissive profile** (`maxResolution
  = UHD_2160`, all codecs assumed decodable, `tier = HANDHELD`) so ranking still runs (≈ current behaviour). Never
  crash playback over a capability query.
- All resolution stays on IO dispatchers; `CancellationException` is **rethrown** (codebase convention).

## 8. Testing

**Pure logic — TDD (JUnit4 + `runBlocking`; hand-fake `DeviceProfile`, no mockk):**
- `StreamParsing`: `codec`/`hdr`/`sourceType` from **real titles** — e.g. `"Her 2013 1080p BluRay REMUX AVC
  DTS-HD MA"` → AVC/SDR/REMUX; `"… 2160p HDR10 HEVC x265"` → HEVC/HDR10; `"… WEB-DL x264"` → AVC/WEBDL; unknown
  strings → `UNKNOWN`.
- `StreamRanker.rank` with injected profiles:
  - **HANDHELD** profile (max 1080p, AVC+HEVC, SDR) over the real "Her" list → picks a **non-REMUX** candidate if
    present; among all-REMUX, picks the **smallest/most-decodable**; never an over-1080p or undecodable source.
  - **TV** profile (max 2160p, +HDR) → keeps the **highest-quality decodable** pick (REMUX/4K **not** demoted).
  - **Hard filter**: an HEVC-only list on an AVC-only profile drops them (or falls back per §7), never returns a
    source the device can't decode.
  - **1-seeder penalty** and **cached-first** preserved.

**Emulator smoke (VPN off; restart with `-dns-server` if a fetch 403s) — per build step (§9):**
1. Parsing — log parsed fields for the "Her" candidates + a HEVC/4K title.
2. `DeviceCapabilities` — log the probed `DeviceProfile` on the phone emulator (expect: **no 4K-HEVC** decode,
   max ~1080p, tier HANDHELD — this concretely explains the earlier black-screen/REMUX pain).
3. Ranker — log the ranked order + the auto-picked candidate for "Her" (expect a streamable pick, not the 24 GB
   REMUX — **iff** a smaller candidate is in the list; otherwise see §2 non-goal).
4. Movie auto-pick — tap "Her" → it **auto-plays** (no list); Sources sheet still reachable.

**Hardware / TV (owner):** confirms the **TV-tier 4K path** and real decode (emulator can't decode 4K-HEVC —
known limitation).

## 9. Build order (incremental — smoke-test each, per owner's "validate as we go")

1. **Parsing + model** (`Codec`/`Hdr`/`SourceType` enums, `StreamParsing` rules, `Stream` fields) — TDD pure;
   smoke = log parsed fields.
2. **`DeviceCapabilities` probe** + `DeviceProfile` — smoke on emulator (log the profile).
3. **`StreamRanker` rewrite** (device-tier adaptive) — TDD pure (phone vs TV fixtures); smoke = log ranked order.
4. **Movie auto-pick wiring** (`startMovieAuto` + `DetailViewModel.loadMovie`; series pass the profile too;
   device-aware `preferredQuality` default) — smoke = tap "Her" auto-plays; Sources sheet intact; series still OK.

Commit locally after each step. Owner merges.

## 10. Decisions

- "Best" = **device-tier adaptive** (owner-chosen): per-device ceiling → highest quality under it → streamability →
  reliability → language. **TV tolerates REMUX/4K; handhelds demote bloat.**
- **Movies reuse the series engine** (`resolveFrom`); Sources sheet kept as manual override.
- **`DeviceCapabilities` is client-side and built** (no OSS fits — server pickers are device-blind).
- **Adopt MIT logic**: parse-torrent-title regexes → `StreamParsing`; AutoStream heuristic → `StreamRanker`.
- The existing **Preferred-quality** setting is the *target*, with a **device-aware default** (4K-capable TV ⇒
  4K, else 1080p); device capability is always the hard cap.
- **Candidate-list composition is a non-goal/known limitation** (Torrentio `limit=5 | sort=qualitysize` returns
  only the biggest files). **Follow-up:** surface streamable encodes by bumping `limit` / switching `sort` in the
  stored addon config — tracked separately; without it, all-REMUX titles still lack a smooth option.
- Built on **`feat/binge-watch`**; commit locally; owner merges.
