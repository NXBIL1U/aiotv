# AIO TV — TODO / Tracking

_Last updated: 2026-06-21_

Living checklist of outstanding work. Each item is tagged with the roadmap phase that
resolves it (`[P0]`–`[P5]`), so executing a phase ticks off its items automatically.
For the product vision, target architecture, and phased roadmap, see [`DESIGN.md`](DESIGN.md).

Phases: **P0** stabilise/verify · **P1** foundations · **P2** core VOD · **P3** live TV ·
**P4** discovery/personalisation · **P5** hardening/modernisation.

---

## ✅ Done (on `main`)

- **P0 — Playback blockers:** cleartext HTTP enabled, custom User-Agent + cross-protocol
  redirects, DASH module + MIME hinting, error/buffering UI with retry, lifecycle pause/resume,
  audio focus, wake mode, resume-from-position.
- **P0 — Release build:** kotlinx.serialization R8 keep rules; release signs with the debug key.
- **P0 — TorBox VOD:** `checkcached` format fix; removed dead cached-resolution branch.
- **Data fixes:** M3U UTF-8 BOM handling; M3U/EPG fetch moved to `Dispatchers.IO`.
- **P1 — TV navigation:** clickable D-pad nav rail, initial focus per screen, focusable +
  scrollable TV Guide, TV overscan margins.
- **P1 — Foldable/adaptive:** width-based layouts (TV rail / bottom bar / side rail),
  Detail two-pane ≥840dp, Search grid ≥600dp.
- **P2 — UX polish:** fixed invisible progress bar, image placeholders, lazy-list keys, larger
  TV typography, Settings password toggles + IME actions, Detail loading/error feedback.
- **Build/repo fixes:** added missing `gradle.properties` (`android.useAndroidX=true`) and
  un-ignored it; fixed `SearchScreen` missing `items` import. Project compiles.
- **Nav fix:** phone bottom bar now includes Settings/Addons.
- **Verified live:** compiles, installs, browses on a phone emulator (Cinemeta → Hero + rails).

---

## 🧪 Verification still outstanding

- [ ] `[P0]` **Real video playback** — needs a streams addon or TorBox key (Cinemeta has no streams).
- [ ] `[P0]` **Detail screen** — open a title; verify loading/error states + two-pane layout.
- [ ] `[P0]` **Android TV emulator** — verify D-pad navigation and the 10-foot layout.
- [ ] `[P0]` **Foldable emulator** — verify side-rail / two-pane adaptive layout.
- [ ] `[P0]` **Fire TV** — must be tested on hardware (no Fire OS emulator exists).

## 🐞 Known bugs to fix

- [ ] `[P1]` **No auto-refresh / pull-to-refresh** — Home loads once in `init`; adding/removing a
      source doesn't update the UI until app restart. Make repositories reactive + add cache.
- [ ] `[P1]` **Redundant Addons tab + Live/Watchlist stubs** — consolidate into the dedicated
      Sources screen (per DESIGN decision 1).
- [ ] `[P1]` **Cache invalidation on settings change** — wire `clearCache()` / reactive sources.
- [ ] `[P1]` **Settings title under status bar** — missing safe-area/window-insets padding.
- [ ] `[P2]` **Continue Watching resume broken for TorBox** — keyed on the ephemeral resolved
      URL; store a stable content id (+ title/poster) in `WatchProgress`.
- [ ] `[P2]` **No hinge/fold posture awareness** — player should avoid the fold crease
      (`WindowInfoTracker` / `FoldingFeature`).

## 🧹 Data-layer hardening

- [ ] `[P1]` Make non-nullable model fields tolerant (`XtreamStream.streamId`/`name`) so one bad
      entry doesn't drop the whole list. _(touches shared parsing; do early)_
- [ ] `[P2]` Stremio catalog pagination (`skip`) — currently only the first page loads.
- [ ] `[P2]` `pickFile` uses positional index — prefer filename / largest-video matching.
- [ ] `[P3]` URL-encode Xtream username/password (breaks on `&`, `=`, `+`, spaces).
- [ ] `[P3]` XMLTV time parsing: support `+01:00`-style offsets; document no-offset = UTC.
- [ ] `[P5]` Redact TorBox API key from the `requestdl` URL and from HTTP logging.

## ⚙️ Optional / housekeeping

- [ ] `[P5]` Modernize toolchain (AGP / Kotlin / Compose BOM / Hilt / Coil). Media3 → 1.5.1 done.
- [ ] `[P5]` Replace deprecated `Icons.Filled.ArrowBack` with `Icons.AutoMirrored.Filled.ArrowBack`.
- [ ] `[P5]` Update README (release signs with debug key; corrected `gradle.properties` guidance).
- [ ] `[P5]` Add a `SessionStart` hook so Claude Code web sessions can build/lint.

---

## Out of scope (per DESIGN decisions)

Offline downloads · multiple profiles · DVR/recording · app backend.
