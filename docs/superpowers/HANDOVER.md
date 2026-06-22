# AIO TV — Session Handover (2026-06-22)

Paste this into the next Claude Code session to continue seamlessly.

## What this is
AIO TV — a native Android app (Kotlin, Jetpack Compose + androidx.tv, Media3/ExoPlayer, Hilt,
Retrofit/kotlinx, **Room**, DataStore, Coil). One APK for Fire TV, Android TV/Google TV, Galaxy
Fold, phones. Combines **live IPTV** (Xtream/M3U) with **Stremio-style VOD resolved via TorBox**.
Repo: `/Users/nayemrezavox/web-projects/home-projects/aiotv` (GitHub `NXBIL1U/aiotv`, branch `main`).

## READ FIRST (in order)
1. **Memory:** `/Users/nayemrezavox/.claude/projects/-Users-nayemrezavox-web-projects-home-projects-aiotv/memory/MEMORY.md` and the files it links — esp. *north-star intent*, *prefer-asking-for-observable-results*, *emulator-no-host-audio*, *emulator-dns-breaks-on-vpn-toggle*.
2. **`DESIGN.md`** (north-star, phases, §8 Home decision) and **`TODO.md`** (phase-tagged checklist — current).
3. **Specs/plans** under `docs/superpowers/`: `specs/2026-06-21-live-tv-core-experience-design.md`, `specs/2026-06-21-live-tv-ux-v2-design.md`, `plans/2026-06-22-live-tv-sky-refinement.md`.

## Where we are (all landed on `main`, latest `dfd9f8c`)
- **Live TV is done and validated** end-to-end on the phone emulator:
  - Original OOM fix: Xtream `get.php` (340 MB M3U) → compact `player_api.php` JSON; live plays via raw **MPEG-TS `.ts`** (not HLS — Xtream HLS 401/403s); 15 s/IP connect timeout for failover.
  - **Cache-first Room layer** (channels/categories/EPG persist; cold start w/ fresh cache = 0 network).
  - **Region/Language filter** default UK+US+EN (~7.2k of 27.5k), via unit-tested `RegionClassifier`.
  - **Favourites** (channels + categories) + **recently-watched**.
  - **Sky-mobile-style "For You" landing**: single "All Channels ▾" category control (Region moved to **Settings**), rich rows (channel number + logo + now/next `HH:mm · title` + progress bar + ★), and a **persistent D-pad category rail** on wide/TV.

## What's left (priority order)
1. **Home screen redesign** (owner `nabz`; DESIGN §8, TODO): make Home **VOD-only, network categories** (Netflix, Disney, … from the Stremio addon catalogs → TorBox/Torrentio) + Continue Watching; **remove the IPTV live-now rail**. This *also* fixes Home's slow first paint (it currently loads all 27.5k channels via `HomeViewModel.getChannels()` and gates `isLoading` on it). **Brainstorm first** (how "networks" map to addon catalogs), then build. _Quick interim win available: just delete the channel load/rail from `HomeViewModel`+`HomeScreen`._
2. **TV-emulator validation pass** of Live TV (D-pad category rail + rows) on `aiotv_tv` — only phone (portrait + landscape) was validated.
3. **Confirm live-TV audio on hardware** (emulator doesn't route audio).
4. Standing TODO.md items: pull-to-refresh + Home/Search reacting live to source edits; wire `clearCache()` to settings changes; Detail poster on phone; TV overscan on Settings/Search/Detail; app-wide nav (only Home shows the rail/bottom bar — inner screens are full-screen pushes); OkHttp 5 `fastFallback` (true IP racing, needs the upgrade); Phase 2 series one-tap (§6a); Fire TV hardware test.

## Build / run / emulators (this Mac)
- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"; ./gradlew assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`. Package `com.itrepos.aiotv`.
- adb: `$ANDROID_HOME/platform-tools/adb`. Emulators: `aiotv_phone`(API35), `aiotv_tv`(API36), `aiotv_fold`(API35). Run ONE at a time. Current running serial has been **`emulator-5554`** (it was relaunched with `-dns-server 8.8.8.8,8.8.4.4`).
- The owner's M3U (Xtream `get.php`) + addons are saved in DataStore on the emulator.

## Critical gotchas (learned this session)
- **The IPTV provider is blocked by the owner's UK ISP (Virgin Media).** It's only reachable over a **VPN**. If channel fetches time out / `http=000`, the VPN is off. (Down-detectors show "up" because they're not on Virgin.)
- **Toggling the host VPN breaks the running emulator's DNS** (`UnknownHostException`) — restart the emulator with `-dns-server 8.8.8.8,8.8.4.4` (serial may change). Airplane-mode toggle does NOT fix it.
- **Emulator plays video but no audio on the Mac** — verify audio on hardware, don't chase it.
- **Validation:** build + run on the emulator. For things the owner can SEE (video/UI), ASK them rather than decoding screenshots — UNLESS they're away, then screenshot. `uiautomator dump` is flaky on Compose lists (often returns sparse/empty) — prefer screenshots for the channel list.
- **Subagents commit in isolated git worktrees** (`.claude/worktrees/`, gitignored) — their commits do NOT auto-advance your branch ref. After a subagent reports DONE, `git merge --ff-only <subagent-tip>` (or commit in your own session) to bring the work onto the branch. Verify with `git log`.
- **Collaboration:** `nabz`/`nabzlive3` edits this repo in parallel. Prefer feature branch + PR; the owner has sometimes said push straight to main — follow their per-time call. Verify fast-forward (no force-push) before pushing.
- Provider re-fetch (27.5k channels: download + RegionClassifier + Room upsert) takes ~**36 s** — wait long enough before asserting "empty". A Room schema change needs a `Migration` (not destructive) to keep favourites/recents (see `DatabaseModule.MIGRATION_1_2`).

## Process
Use the superpowers skills: **brainstorming → writing-plans → subagent-driven-development** for features; **systematic-debugging** for bugs. Save specs to `docs/superpowers/specs/`, plans to `docs/superpowers/plans/`.
