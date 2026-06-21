# AIO TV — Design & Roadmap

_Living design document. Draft — open to revision. Last updated: 2026-06-21._

Companion to [`TODO.md`](TODO.md) (task tracking). This doc is the **north-star reference**:
what we're building, why, the target architecture, and the phased path to get there. It is
intentionally **not anchored to the current implementation** — treat today's code as a
starting point, not a constraint.

---

## 1. Vision

**One Android app that unifies on-demand movies/TV (VOD) and live IPTV, with a
best-in-class experience across Fire TV, Android TV / Google TV, foldables (Galaxy Fold 7),
phones and tablets — from a single APK.**

A user brings their own sources (Stremio addons, IPTV subscriptions) and a debrid account
(TorBox), and the app turns them into a polished, Netflix-grade 10-foot-and-touch experience.

## 2. Design principles

1. **Source-agnostic.** Content and playback come through clean abstractions; adding a new
   addon type, IPTV format, or debrid provider should be a small, isolated change.
2. **One codebase, native on every form factor.** D-pad on TV, touch on phone, two-pane on
   foldable/tablet — adaptive, not lowest-common-denominator.
3. **No dead ends.** Every screen has loading, empty, and error states with a way forward.
4. **Cache-first & reactive.** The UI reflects data automatically; changing a source updates
   the app without manual restarts.
5. **Bring-your-own-source, local-first.** Credentials stay on device; no app backend.
6. **Fast on weak hardware.** Fire TV sticks are RAM/CPU-constrained — budget for them.

## 3. Target devices & inputs

| Device | Input | Layout | Notes |
|---|---|---|---|
| Fire TV Stick 4K | D-pad remote | 10-foot, nav rail | **No emulator exists** — test on hardware. Primary target. |
| Android TV / Google TV | D-pad remote, voice | 10-foot, nav rail | Emulatable. |
| Galaxy Fold 7 | Touch, fold/unfold | Compact (cover) ↔ two-pane (inner) | Hinge-aware player. |
| Phone | Touch | Compact, bottom bar | |
| Tablet | Touch | Two-pane / side rail | |

## 4. Information architecture

Resolve today's redundancy (read-only Addons tab duplicating Settings; Live/Watchlist stubs).

**Primary surfaces**
- **Onboarding** — first-run setup (TorBox, addons, IPTV). On TV: pairing-code / QR instead
  of typing URLs on a remote.
- **Home** — unified VOD + live: Continue Watching, Trending, genres, "Live now".
- **Search** — unified across catalogs + channels; voice on TV.
- **Live TV** — real EPG grid (now/next, zapping, catch-up, favourites).
- **Detail** — rich metadata; series → seasons/episodes; stream picker (quality/size/cached).
- **Player** — VOD + live; subtitles, audio/quality, next-episode autoplay, fold-aware.
- **Library / Watchlist** — saved titles + Continue Watching (later phase).
- **Settings → Sources** — manage addons, IPTV, debrid, playback prefs. Single control surface.

**Decision:** a **dedicated Sources screen** (reached from Settings) manages all addons /
IPTV / debrid, with per-source status and validation. No standalone read-only Addons tab.

## 5. Content & source model (target architecture)

Introduce two key abstractions so the app isn't wired to Stremio/TorBox specifically:

```
ContentSource            (where catalogs/metadata/streams come from)
  ├─ StremioAddonSource  (manifest → catalog / meta / streams)
  ├─ XtreamSource        (live + VOD via Xtream Codes API)
  └─ M3uSource           (live channels via M3U + XMLTV EPG)

StreamResolver           (turn a Stream into a playable URL)
  ├─ DirectUrlResolver   (addon already gave an http(s) url)
  ├─ TorBoxResolver      (debrid: infoHash → cached/added → download url)
  └─ (future) RealDebrid / AllDebrid / Premiumize
```

- A **SourceRepository** aggregates enabled `ContentSource`s and merges results.
- A **PlaybackResolver** picks the right `StreamResolver` for a given `Stream`.
- Unified domain models: `MediaItem`, `Series`/`Season`/`Episode`, `Channel`, `EpgProgram`,
  `Stream`, `PlayableSource`, `WatchProgress`.

This makes "add Real-Debrid support" or "add a new catalog provider" a contained task.

## 6. Playback

- Resolve chain: `Stream` → `PlaybackResolver` → playable URL → Media3/ExoPlayer.
- Player capabilities (target): subtitle tracks (incl. external/OpenSubtitles-style addons),
  audio-track + quality selection, resize/zoom modes, **next-episode autoplay**, resume,
  hinge-aware control placement, robust error/retry (P0 foundation already in place).
- Live vs VOD handled by the same player with mode-aware controls (no seek bar tricks for live).

## 7. Technical architecture

- **Layers:** keep `data / domain / ui`. Add a **cache layer (Room)** and **Paging 3** for
  large catalogs.
- **Reactive everywhere:** repositories expose `Flow`s; screens collect them so source/setting
  changes **auto-refresh** the UI (fixes the current one-shot-load bug). Add pull-to-refresh
  as a manual escape hatch.
- **DI:** Hilt (current). **Settings:** DataStore (current). **Player:** Media3 (current).
- **Networking:** Retrofit/OkHttp + kotlinx.serialization (current); share one tuned OkHttp
  (UA, redirects, timeouts) between API calls and ExoPlayer.
- **Modularization:** optional later (`:core`, `:data`, `:feature-*`) once surface area grows.

## 8. UX notes per surface

- **Onboarding:** detect what's configured; guide TorBox + first addon + (optional) IPTV.
  TV: QR / phone-companion entry to avoid on-remote typing.
- **Home:** hero carousel, horizontally-scrolling rails with stable focus; "Live now" rail
  shows current programme per channel; Continue Watching that actually resumes.
- **Detail:** seasons/episodes for series; stream list sorted by cached → quality → size with
  clear badges; one-press play of best stream + "choose source" affordance.
- **Live/EPG:** time-grid guide navigable by D-pad and touch; now/next; channel groups &
  favourites; catch-up where the provider supports it.
- **Search:** instant results across VOD + channels; voice on TV; recent searches.
- **Settings/Sources:** show-password toggles (done), reachable on all form factors (done),
  validation feedback when adding a source.

## 9. Phased roadmap

Each phase lists its goal, key deliverables, the `TODO.md` items it absorbs, and exit criteria.

### Phase 0 — Stabilise & verify _(in progress)_
- **Goal:** prove the merged P0/P1/P2 work end-to-end.
- **Done:** project builds; runs and browses on phone emulator (Cinemeta).
- **Remaining:** verify **real playback** (needs a streams addon or TorBox key); verify the
  **Detail** screen; pass on **Android TV** and **foldable** emulators.
- **Exit:** a torrent/VOD title plays via TorBox, and live IPTV plays, on at least one device.

### Phase 1 — Foundations
- **Goal:** the architecture and onboarding that everything else builds on.
- **Deliverables:** `ContentSource` + `StreamResolver` abstractions; reactive repositories
  with **auto-refresh** + cache (Room/Paging); **onboarding wizard**; consolidate IA (drop
  redundant Addons/Watchlist tabs → **dedicated Sources screen**).
- **Absorbs TODO:** no auto-refresh; redundant Addons tab; Live/Watchlist stubs;
  cache-invalidation-on-settings-change.
- **Exit:** adding/removing a source updates the UI live; first-run wizard configures the app.

### Phase 2 — Core experience (VOD)
- **Goal:** make browsing → detail → play feel premium.
- **Deliverables:** Home rails done right (genres, trending, working Continue Watching);
  Detail with seasons/episodes + stream picker; Player polish (subtitles, audio/quality,
  next-episode autoplay, fold-aware, resize).
- **Absorbs TODO:** Continue Watching resume fix; hinge/fold awareness; ArrowBack deprecation.
- **Exit:** binge a series end-to-end with resume and autoplay.

### Phase 3 — Live TV
- **Goal:** first-class IPTV.
- **Deliverables:** real EPG grid (now/next, D-pad + touch), fast zapping, channel groups &
  favourites, catch-up; robust Xtream/M3U handling.
- **Absorbs TODO:** XMLTV offset parsing; Xtream credential encoding; non-nullable model
  tolerance.
- **Exit:** browse the guide, zap channels, and watch live reliably.

### Phase 4 — Discovery & personalisation
- **Goal:** find and keep content.
- **Deliverables:** unified search + voice; Watchlist; **optional Trakt.tv sync** (Watchlist +
  Continue Watching); add Real-Debrid/AllDebrid resolvers behind the existing interface.
- **Exit:** search across sources; save and resume from a watchlist.

### Phase 5 — Hardening & modernisation
- **Goal:** production-grade quality.
- **Deliverables:** toolchain upgrades (AGP/Kotlin/Compose BOM/Hilt/Coil); performance &
  memory tuning for Fire TV sticks; accessibility pass; security (redact API keys from
  URLs/logs); release/signing pipeline; README refresh; SessionStart hook.
- **Absorbs TODO:** dependency currency; key-redaction; README; data-layer hardening; hook.

## 10. Risks & constraints

- **Fire TV has no emulator** — the primary device must be tested on hardware.
- **Debrid dependency** — playback of torrent streams relies on TorBox (paid, third-party);
  abstract it and degrade gracefully when unavailable.
- **Source legality/availability** — sources are user-provided; the app ships none.
- **Low-end hardware** — Fire sticks have limited RAM/CPU; watch image/list/cache budgets.

## 11. Decisions (resolved 2026-06-21)

1. **Sources IA → dedicated Sources screen.** A standalone Sources area (from Settings)
   manages all addons / IPTV / debrid, with per-source status and validation.
2. **Watchlist & Continue Watching → local-first, Trakt-ready.** On-device now; architected so
   an optional Trakt.tv login adds cross-device sync later (Phase 4).
3. **Debrid → abstract, TorBox-only now.** `StreamResolver` interface with TorBox implemented;
   Real-Debrid / AllDebrid are later drop-ins.
4. **Offline downloads → out of scope.** Streaming-only.
5. **Profiles → single user.** One watchlist / history; aligns with Trakt's one-account model.
6. **Live IPTV → catch-up yes, recording no.** Timeshift/replay in the Live TV phase
   (provider-dependent); no DVR/recording.

### Non-goals (current scope)
No offline downloads · no multiple profiles · no DVR/recording · no app backend of our own.

## 12. Where we are now

See [`TODO.md`](TODO.md) for the live checklist. Summary: build + browse verified on phone;
playback and TV/foldable verification pending; foundations (Phase 1) not yet started.
