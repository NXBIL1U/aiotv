# AIO TV — TODO / Tracking

_Last updated: 2026-06-21_

Living checklist of outstanding work. Grouped by priority. Check items off as they land.
For the product vision, target architecture, and phased roadmap, see [`DESIGN.md`](DESIGN.md).

---

## ✅ Done (on `main`)

- **P0 — Playback blockers:** cleartext HTTP enabled, custom User-Agent + cross-protocol
  redirects on the ExoPlayer data source, DASH module + MIME hinting, error/buffering UI
  with retry, lifecycle pause/resume, audio focus, wake mode, resume-from-position.
- **P0 — Release build:** kotlinx.serialization R8 keep rules; release signs with the debug
  key so `assembleRelease` is installable.
- **P0 — TorBox VOD:** `checkcached` format fix; removed dead cached-resolution branch.
- **Data fixes:** M3U UTF-8 BOM handling; M3U/EPG fetch moved to `Dispatchers.IO`.
- **P1 — TV navigation:** clickable D-pad nav rail, initial focus per screen, focusable +
  scrollable TV Guide, TV overscan margins.
- **P1 — Foldable/adaptive:** width-based layouts (TV rail / compact bottom bar / side rail),
  Detail two-pane ≥840dp, Search grid ≥600dp.
- **P2 — UX polish:** fixed invisible progress bar, image placeholders, lazy-list keys, larger
  TV typography, Settings password toggles + IME actions, Detail loading/error feedback.
- **Build/repo fixes:** added missing `gradle.properties` (`android.useAndroidX=true`) and
  un-ignored it; fixed `SearchScreen` missing `items` import. Project now compiles.
- **Nav fix:** phone bottom bar now includes Settings/Addons (was cut off → app was
  unconfigurable on phone/foldable).
- **Verified live:** compiles, installs, launches and browses on a phone emulator
  (Cinemeta catalog → Hero + rails + posters render).

---

## 🧪 Verification still outstanding (highest value first)

- [ ] **Real video playback** — the whole point of the P0 work, still unproven. Needs a
      streaming source: a TorBox API key, or a public streams addon (e.g. a Torrentio-style
      addon). Cinemeta provides catalog/metadata only, no streams.
- [ ] **Detail screen** — open a title; verify loading/error states and the two-pane layout.
- [ ] **Android TV emulator** — verify D-pad navigation and the 10-foot TV layout.
- [ ] **Foldable emulator** — verify side-rail / two-pane adaptive layout (and unfold behaviour).

## 🐞 Known bugs to fix

- [ ] **Continue Watching resume broken for TorBox** — progress is keyed on the ephemeral
      resolved stream URL. Store a stable content id (+ title/poster) in `WatchProgress`.
- [ ] **Live / Watchlist tabs are stubs** — they re-show Guide / Addons. Implement real
      screens or remove the entries.
- [ ] **No hinge/fold posture awareness** — width adaptivity exists, but the player doesn't
      avoid the fold crease (`WindowInfoTracker` / `FoldingFeature`).
- [ ] **Settings title under status bar** — missing safe-area/window-insets padding on phone.

## 🧹 Data-layer hardening (from review)

- [ ] URL-encode Xtream username/password (breaks on `&`, `=`, `+`, spaces).
- [ ] Redact TorBox API key from the `requestdl` URL and from HTTP logging.
- [ ] XMLTV time parsing: support `+01:00`-style offsets; document no-offset = UTC.
- [ ] Invalidate in-memory repo caches when settings change (wire `clearCache()`).
- [ ] Stremio catalog pagination (`skip`) — currently only the first page loads.
- [ ] Make non-nullable model fields tolerant (`XtreamStream.streamId`/`name`) so one bad
      entry doesn't drop the whole list.
- [ ] `pickFile` uses positional index — prefer matching by filename / largest video file.

## ⚙️ Optional / housekeeping

- [ ] Modernize toolchain (AGP / Kotlin / Compose BOM / Hilt / Coil). Media3 already bumped
      to 1.5.1.
- [ ] Replace deprecated `Icons.Filled.ArrowBack` with `Icons.AutoMirrored.Filled.ArrowBack`.
- [ ] Update README (release signing now uses debug key; corrected `gradle.properties` guidance).
- [ ] Add a `SessionStart` hook so Claude Code web sessions can build/lint.
- [ ] Real Fire TV testing must be on hardware (no Fire OS emulator exists).
