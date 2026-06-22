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
- A **capability-filtered, size-capped** ranker: drop what the device can't decode/display, **hard 20 GB cap**,
  then highest quality at/under target, then within-resolution streamability, then reliability (cached, seeders),
  keeping English preference.
- A reusable `DeviceCapabilities` probe (decodable codecs, max resolution, HDR).
- Richer release parsing (codec / HDR / source type) feeding the ranker.

**Goal addition — candidate-list composition (folded in, owner-requested):** the owner's Torrentio addon is
configured `limit=5 | sort=qualitysize`, so it returns only the **5 biggest files** — for "Her" all 5 were
24–34 GB REMUX, leaving the ranker no streamable option. **Empirically verified (2026-06-22, VPN off):** the
same request with `limit=30` returns **18 streams**, all `[TB+]` cached, including a **5.85 GB 1080p HEVC**
(12 seeders), an **8.74 GB** and **12.54 GB 1080p AVC** — exactly the streamable encodes we want. So we **raise
the Torrentio request `limit`** at request time (see §4 `StreamRequestTuning`); the device-aware ranker then has
real choices. Raising `limit` is **necessary and sufficient** — no `sort` change (and no token-guessing) needed.

**Non-goals (v1):**
- **Real network-bandwidth measurement.** No active throughput probing in v1. The **flat 20 GB cap** is our
  streamability guardrail instead — it bounds bitrate enough to stream on a normal home connection regardless of
  device, and anything heavier is a deliberate manual pick.
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
| `StreamRequestTuning` (new, pure, `domain`) | `tuneStreamBase(baseUrl): String` — if the host is **Torrentio** (`contains "torrentio"`), rewrite the options path segment to raise `limit` (replace `limit=<n>` → `limit=30`; inject if absent), **preserving everything else** (the `torbox=` token, `qualityfilter`, `debridoptions`, `sort`). Non-Torrentio addons returned unchanged. Pure string transform ⇒ unit-testable. |
| `StremioRepository.getStreams` (modify) | Build the stream request from the **tuned** base: `streamUrl(tuneStreamBase(baseUrl), type, id)`. Manifest/catalog/meta requests unchanged (`limit` only affects stream lists). `toStream()` also fills the new `codec`/`hdr`/`source` fields (it already aggregates `name+title+filename` into `detectText`). |
| `StreamParsing.kt` (modify) | Add `codec(text): Codec`, `hdr(text): Hdr`, `sourceType(text): SourceType`. Regexes ported from parse-torrent-title (MIT). Existing `quality`/`seeders`/`sizeBytes`/`languageScore`/`isTbCached` unchanged. |
| `Stream.kt` (modify) | Add `codec: Codec = UNKNOWN`, `hdr: Hdr = UNKNOWN`, `source: SourceType = UNKNOWN`. Add enums `Codec { AVC, HEVC, AV1, UNKNOWN }`, `Hdr { SDR, HDR10, DOLBY_VISION, UNKNOWN }`, `SourceType { REMUX, BLURAY, WEBDL, WEBRIP, HDTV, UNKNOWN }`. |
| `DeviceCapabilities` (new, `@Singleton`, `domain/playback`) | Probes the device **once** and exposes a `DeviceProfile`. Decodable codecs + max decode resolution via `MediaCodecList(REGULAR_CODECS)` / Media3 `MediaCodecUtil.getDecoderInfos(mime,…)`; screen resolution via `Display.getMode()`; HDR via `Display.getHdrCapabilities()`. **No device tier** — the flat size cap (§5) replaces TV-vs-handheld branching. |
| `DeviceProfile` (data) | `maxResolution: Quality` = **min(max-decodable resolution, screen resolution)** (so a 1080p-screen phone targets 1080p — 4K on a 1080p panel is invisible + wasteful — while a 4K-screen phone/TV gets 4K), `decodableCodecs: Set<Codec>`, `hdrCapable: Boolean`. |
| `StreamRanker.kt` (rewrite) | `rank(streams, profile: DeviceProfile, target: Quality): List<Stream>` — algorithm in §5 (hard 20 GB cap, decodable + resolution filter, then quality→streamability→reliability). A `preferred`-only overload is kept for any non-VOD caller (delegates with a permissive profile) so nothing else breaks. |
| `PlaybackController.kt` (modify) | **Inject `DeviceCapabilities`.** Add `suspend startMovieAuto(candidates, title, progressId): Boolean` → `resolveFrom(candidates, 0)` then set a movie `PlaybackState` (`upNext = null`). Update `advanceToNextEpisode`'s internal `StreamRanker.rank(...)` call to pass the `DeviceProfile` + target so the next episode is device-aware too. The existing passive `startMovie(...)` (already-resolved URL, manual pick) stays for the Sources sheet. |
| `DetailViewModel.kt` (modify) | `loadMovie`: rank with `DeviceProfile` + target, call `startMovieAuto`, and `onPlay(...)` on success; on failure fall back to the Sources sheet (mirrors `playEpisode`). `playEpisode`/`advanceToNextEpisode` pass the `DeviceProfile` too so series benefit. `AppDataStore.preferredQuality` default becomes device-aware (§5). |
| `AppDataStore.kt` (modify) | `preferredQuality` default resolves to the device's **`maxResolution`** (screen-capable + decodable, capped at 4K) — so a 4K screen targets 4K, a 1080p screen targets 1080p, automatically. The user's explicit Settings choice still wins once set. |

## 5. Ranking policy

```
SIZE_CAP_BYTES = 20 GB        // global hard cap, any resolution, any device (named constant)
target = min(userTarget, profile.maxResolution)            // userTarget = preferredQuality setting

eligible = streams.filter {
    decodable(it.codec, profile)                           // device has a decoder for the codec
    && it.quality.rank <= profile.maxResolution.rank        // not above what device decodes/screen shows
    && (it.sizeBytes == null || it.sizeBytes <= SIZE_CAP_BYTES)   // under 20 GB (unknown size kept)
}
if (eligible.isEmpty()) → caller falls back to the Sources sheet (manual pick — never auto-play a buffer-fest)

eligible.sortedWith(
    compareByDescending { it.isCached }                    // 1. cached debrid first (uncached = slow)
    .thenByDescending   { it.languageScore }               // 2. English preference (existing)
    .thenByDescending   { qualityFit(it, target) }         // 3. highest quality at/under target
    .thenByDescending   { streamability(it) }              // 4. within a resolution, prefer smaller/sane bitrate
    .thenByDescending   { seederScore(it) }                // 5. healthy sources (1-seeder penalty)
)
```

- **20 GB hard cap** — never auto-pick a source above it (kills REMUX, 24–34 GB, and absurd 4K everywhere).
  Unknown-size sources are kept (we can't measure them). If **nothing** is under the cap, the ranker returns
  empty and the caller shows the **Sources sheet** (§7) — we never auto-play a guaranteed buffer-fest.
- **`decodable`** — `codec == UNKNOWN` (can't tell ⇒ keep) **or** `codec ∈ profile.decodableCodecs`. Drops a
  HEVC/AV1 source on a device with no such decoder (prevents the `NO_EXCEEDS_CAPABILITIES` hard-fail).
- **resolution filter** — drop sources above `profile.maxResolution` (= min of decodable + screen res); no point
  fetching 4K for a 1080p panel. Capability decides — a 4K-screen phone is **not** excluded from 4K.
- **`qualityFit`** — quality at/under `target` scores by `quality.rank`; over-target already filtered. Higher
  resolution wins across resolutions (up to target); **streamability** breaks ties **within** a resolution.
- **`streamability`** — within a resolution, prefer a **sane bitrate band**: penalise **both** oversize bloat
  **and** ultra-low-bitrate potatoes (a 1.8 GB "1080p"), landing on a healthy encode (~6–13 GB at 1080p here),
  not merely the smallest. This is what makes a **1080p 5 GB beat a 1080p 20 GB** (owner's example). Since the
  candidates are often *all* cached (verified on "Her": 18/18 `[TB+]`), this — not cached-first — does the real
  work. (Bands + penalty magnitudes fixed in the plan, unit-tested.)
- **`hdr`** — an HDR source on a non-HDR screen is **kept but penalised** (plays, looks washed out) so an SDR
  equivalent wins when present; never hard-dropped.
- **`seederScore`** — seeders descending; **1-seeder = penalty** (AutoStream). Cached `[TB+]` already dominates
  via step 1, so seeders mostly tiebreak uncached candidates.

**Effect:** any device → a streamable encode **under 20 GB** at the best resolution the device can both decode
*and* display; never an undecodable codec; never a buffer-prone REMUX. REMUX/over-cap sources are reachable only
via the **manual Sources sheet** (and are the auto fallback when nothing fits the cap).

## 6. Data flow

```
DetailViewModel.loadMovie(id)
  → GetStreamsUseCase → StremioRepository.getStreams → streamUrl(tuneStreamBase(baseUrl), …)   // limit raised
  → raw candidates (each parsed: quality/codec/hdr/source/size/seeders/lang/cached)
  → target = preferredQuality (device-aware default) ; profile = DeviceCapabilities.profile
  → StreamRanker.rank(raw, profile, target)            // 20GB cap + decode/res filter, then quality→streamable
  → PlaybackController.startMovieAuto(ranked, title, progressId=id)
        → resolveFrom(ranked, 0)  (instant for [TB+] url-shape; withTimeout for hash-shape)
        → success: PlaybackState(currentUrl, title, progressId, upNext=null) ; onPlay(...)
        → all fail: showSources(...)   // manual fallback (same as series)
Series: playEpisode / advanceToNextEpisode pass the same profile into rank() — series get the upgrade too.
Live IPTV: unchanged (no candidate list).
```

## 7. Error handling

- **Nothing under the 20 GB cap / nothing decodable** (ranker returns empty) → **fall back to the Sources sheet**
  (manual pick). This is the owner-chosen behaviour: never auto-play a buffer-prone REMUX; let the user choose one
  deliberately if they want. The sheet shows every source with its size so the choice is informed.
- **Nothing resolves** (URLs all fail) → also the **Sources sheet** (existing behaviour for series; new for movies).
- **`DeviceCapabilities` probe throws / returns sparse data** → degrade to a **permissive profile** (`maxResolution
  = UHD_2160`, all codecs assumed decodable) so ranking still runs on size/quality alone (≈ current behaviour, plus
  the 20 GB cap). Never crash playback over a capability query.
- All resolution stays on IO dispatchers; `CancellationException` is **rethrown** (codebase convention).

## 8. Testing

**Pure logic — TDD (JUnit4 + `runBlocking`; hand-fake `DeviceProfile`, no mockk):**
- `StreamRequestTuning.tuneStreamBase`: a real Torrentio base with `…limit=5…torbox=<tok>…` → `limit=30` with the
  **token and all other options preserved**; a Torrentio base with no `limit` → `limit` injected; a
  **non-Torrentio** URL → returned **unchanged**.
- `StreamParsing`: `codec`/`hdr`/`sourceType` from **real titles** — e.g. `"Her 2013 1080p BluRay REMUX AVC
  DTS-HD MA"` → AVC/SDR/REMUX; `"… 2160p HDR10 HEVC x265"` → HEVC/HDR10; `"… WEB-DL x264"` → AVC/WEBDL; unknown
  strings → `UNKNOWN`.
- `StreamRanker.rank` with injected profiles (assert on the real "Her" candidate list):
  - **20 GB cap**: every source > 20 GB (the 24/30/34 GB REMUX) is excluded from the auto-pick; the chosen source
    is ≤ 20 GB.
  - **All-over-cap → empty**: a list where *every* source is > 20 GB returns **empty** (caller → Sources sheet).
  - **Within-resolution streamability**: given a 1080p 5 GB and a 1080p 20 GB (both decodable, under cap), the
    **5 GB wins** (owner's example); but an ultra-low 1.8 GB "1080p" is **not** preferred over a healthy ~8 GB one.
  - **Resolution ceiling**: a 1080p-screen profile (`maxResolution = HD_1080`) never picks a 2160p source; a
    4K-capable profile may pick 2160p when it's decodable and ≤ 20 GB.
  - **Codec hard filter**: an HEVC-only list on an AVC-only profile returns empty (→ Sources sheet), never a
    source the device can't decode.
  - **1-seeder penalty** and **cached-first** preserved.

**Emulator smoke (VPN off; restart with `-dns-server` if a fetch 403s) — per build step (§9):**
1. Parsing — log parsed fields for the "Her" candidates + a HEVC/4K title.
2. `DeviceCapabilities` — log the probed `DeviceProfile` on the phone emulator (expect: max ~1080p, **no 4K-HEVC**
   decode — this concretely explains the earlier black-screen/REMUX pain).
3. Ranker — log the ranked order + the auto-picked candidate for "Her" (expect a streamable ≤ 20 GB pick, not the
   24 GB REMUX — now guaranteed available thanks to the raised `limit`).
4. Movie auto-pick — tap "Her" → it **auto-plays** a streamable source (no list); Sources sheet still reachable.

**Hardware / TV (owner):** confirms the **4K path** on a 4K screen and real decode (emulator can't decode 4K-HEVC
— known limitation).

## 9. Build order (incremental — smoke-test each, per owner's "validate as we go")

1. **Candidate tuning + parsing + model** (`StreamRequestTuning.tuneStreamBase` wired into
   `StremioRepository.getStreams`; `Codec`/`Hdr`/`SourceType` enums; `StreamParsing` rules; `Stream` fields) —
   TDD pure; smoke on emulator = the "Her" stream list now shows **~18 candidates incl. ~6–13 GB encodes**, with
   parsed codec/source logged (not just the 5 REMUX).
2. **`DeviceCapabilities` probe** + `DeviceProfile` — smoke on emulator (log the profile; expect ~1080p max, AVC
   present, HEVC-4K likely absent).
3. **`StreamRanker` rewrite** (20 GB cap + decode/res filter + within-res streamability) — TDD pure (1080p-screen
   vs 4K-capable fixtures; cap/empty/within-res cases); smoke = log ranked order.
4. **Movie auto-pick wiring** (`startMovieAuto` + `DetailViewModel.loadMovie`; series pass the profile too;
   device-aware `preferredQuality` default) — smoke = tap "Her" auto-plays a **streamable** ~6–13 GB 1080p source
   (not the 24 GB REMUX); Sources sheet intact; series still OK.

Commit locally after each step. Owner merges.

## 10. Decisions

- "Best" = **capability-filtered + size-capped** (owner-chosen, simplified from per-tier): drop undecodable codecs
  and over-resolution; **hard 20 GB cap, any resolution/device**; then highest quality at/under target →
  within-resolution streamability (prefer smaller/sane-bitrate) → cached/seeders → English. **No TV-vs-handheld
  tier** — the flat cap replaces it.
- **20 GB is a global hard cap** (kills REMUX everywhere). **Nothing under the cap → Sources sheet** (manual);
  never auto-play a buffer-fest. REMUX stays reachable by deliberate manual pick.
- **Resolution = capability, not tier**: target ceiling = min(decodable res, **screen res**); a 4K-screen phone is
  not excluded from 4K, a 1080p-screen phone targets 1080p (4K on a 1080p panel is invisible + wasteful).
- **Movies reuse the series engine** (`resolveFrom`); Sources sheet kept as manual override.
- **`DeviceCapabilities` is client-side and built** (no OSS fits — server pickers are device-blind).
- **Adopt MIT logic**: parse-torrent-title regexes → `StreamParsing`; AutoStream heuristic → `StreamRanker`.
- The existing **Preferred-quality** setting is the *target*, defaulting to the device's `maxResolution`
  (screen-capable, capped at 4K); device capability + the 20 GB cap are always the hard limits.
- **Candidate-list composition is folded in** (owner-requested): raise the Torrentio request `limit` (→ 30) via a
  pure `StreamRequestTuning` transform so streamable encodes enter the pool. **Empirically verified** the lever
  works (5 REMUX-only @ limit=5 → 18 mixed-size cached @ limit=30). Only `limit` is touched; the `torbox=` token
  and all other options are preserved; non-Torrentio addons untouched.
- Built on **`feat/binge-watch`**; commit locally; owner merges.
