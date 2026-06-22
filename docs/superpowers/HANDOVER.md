# AIO TV — Session Handover (2026-06-22, late)

Paste into the next Claude Code session to continue seamlessly. Supersedes the previous handover.

## What this is
AIO TV — native Android (Kotlin, Compose + Material3, Media3/ExoPlayer, Hilt, Retrofit/kotlinx,
Room, DataStore, Coil). One APK for Fire TV / Android TV / Galaxy Fold / phones. Combines **live
IPTV** (Xtream/M3U) with **Stremio VOD resolved via TorBox**. Repo:
`/Users/nayemrezavox/web-projects/home-projects/aiotv` (GitHub `NXBIL1U/aiotv`).

## READ FIRST
1. **Auto-memory:** `~/.claude/projects/-Users-nayemrezavox-web-projects-home-projects-aiotv/memory/MEMORY.md` + linked files — esp. *north-star-product-intent*, *prefer-asking-for-observable-results*, *emulator-no-host-audio*, *emulator-dns-breaks-on-vpn-toggle*, **`strem-fun-blocked-by-vpn-cloudflare`** (new, critical for VOD validation).
2. **`DESIGN.md`** + **`TODO.md`** (both current as of this handover).
3. **Specs/plans** under `docs/superpowers/specs/` & `/plans/` (2026-06-22 dates).
4. **`.superpowers/sdd/progress.md`** (gitignored, on disk) — detailed per-task ledger of all the subagent-driven work this session.

## Branch state (IMPORTANT)
- **`main` = `ab7e917`, PUSHED to origin.** Contains: **VOD series spine + Netflix-style Detail page + dark/Netflix-red theme** AND **VOD-only Search (Cinemeta) + Home channel-strip**.
- **`feat/binge-watch` = local, off main, ~12 commits, NOT pushed.** **Auto-next-episode (Netflix countdown) + mid-play failover + quality preference (1080p/4K).** DONE, emulator-validated end-to-end, final-reviewed + all fixes applied. **Owner chose: keep local, stack the next feature on it, merge later.**
- **NEXT: `feat/track-selection` branches OFF `feat/binge-watch`** (stacked — it builds on the player code there).

## ⏭️ Immediate next task: player audio/subtitle track selection
**Brainstorm is DONE and the design is APPROVED (owner, 2026-06-22). Pick up at: write the spec → owner reviews → plan → build.**

**Approved design (Option 1 — built-in + smart defaults):**
- **Default track preferences** on the ExoPlayer (the real fix for the "foreign subs/audio auto-selected" problem we saw — a torrent played with Hungarian subs burned in): `trackSelectionParameters` → **prefer English audio** (`setPreferredAudioLanguage("en")`, falls back to source default) and **subtitles OFF by default** (don't auto-select any text track, so a "default"-flagged foreign sub doesn't show).
- **Manual override = Media3 `PlayerView`'s built-in UI** — enable `setShowSubtitleButton(true)` (CC button) + the existing settings **gear menu** (already there; offers audio/subtitle track selection). No custom UI for v1.
- **Scope:** in-stream tracks only (audio + embedded subs). Sideloaded subs via the **Stremio `/subtitles` addon** is deferred (`[P4]`; the owner has no subtitles addon installed).
- **TV is the decider:** if the built-in track menu is clunky on D-pad in TV-emulator validation, *that* triggers building a **custom Compose track sheet** (Option 2, NextPlayer-style, on-theme). Don't build custom unless TV says so.
- File to touch: `ui/screen/player/PlayerScreen.kt` (the `ExoPlayer.Builder` + `PlayerView` `AndroidView` config, ~lines 155-327). Player uses the built-in controller (`useController = true`).

## What shipped this session (all on main unless noted)
1. **VOD series spine + Netflix Detail (on main):** Cinemeta meta (movies+series, host fallback `cinemeta-live.strem.io`→`v3-cinemeta.strem.fun`); per-episode stream requests (`tt…:S:E`, fixed the bare-series bug); `StreamRanker` (cached `[TB+]`→English→quality→seeders); auto-play best cached + 20s auto-advance; per-episode resume (`progressId` route param); Netflix Detail (hero/season selector/episode list/thumbnails/two-pane ≥840dp). Movie meta via Cinemeta too. Dark+Netflix-red theme.
2. **VOD-only Search + Home channel-strip (on main):** Search now queries Cinemeta search (`catalog/<type>/top/search=`) for movies+series; **channels removed from Search** (channel search stays in Live TV tab); Home "Live Now" rail + 27.5k-channel load removed (paints ~3s vs ~14s).
3. **Binge/watch (feat/binge-watch, local):** `@Singleton PlaybackController` session holder; auto-next-episode countdown; silent failover (`onPlayerError→swap MediaItem→prepare`, no Media3 fallback-URL primitive); quality pref (source-ranking, default 1080p). `BingeSequencing` (next-ep + `bingeGroup`, from stremio-core MIT).

## Process cycle (KEEP THIS)
**brainstorming → write spec (`docs/superpowers/specs/`) → OWNER REVIEWS SPEC → writing-plans (`docs/superpowers/plans/`) → subagent-driven-development → validate on emulator → final whole-branch review → fix → (merge when owner asks).**
- Subagent-driven: fresh implementer subagent per task (sonnet; opus for big integration tasks), a reviewer subagent per task, a final opus whole-branch review, fix subagents for findings. Use the SDD skill scripts: `task-brief PLAN N`, `review-package BASE HEAD` (in `~/.claude/plugins/cache/claude-plugins-official/superpowers/6.0.3/skills/subagent-driven-development/scripts/`).
- **Subagents run in THIS workspace (no isolation) → their commits land directly on the branch** (no ff-merge dance). Verify HEAD advanced.
- TDD the pure logic (JUnit4 only — no mockk; use `runBlocking`, hand-fake interfaces). Validate UI/VM/IO on the emulator.
- Commit locally after each task. **Merge/push only when owner asks** (`git fetch` first, fast-forward only, never force).

## Build / run / emulator
- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"; ./gradlew assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`. Package `com.itrepos.aiotv`. adb: `$ANDROID_HOME/platform-tools/adb`.
- Emulator running: **`emulator-5554`** (`aiotv_phone`, API35). Also `aiotv_tv`(API36), `aiotv_fold`(API35) — one at a time. Owner's M3U + addons are in DataStore on it.

## CRITICAL gotchas
- **VPN is mutually exclusive for VOD vs IPTV.** `strem.fun` (Torrentio + Cinemeta) is **Cloudflare-403'd on the owner's VPN datacenter IP** → **VOD validation needs VPN OFF** (residential Virgin IP). IPTV needs VPN ON (Virgin blocks the provider). Can't do both at once. (`http=403`/HTML on strem.fun ⇒ VPN is on.)
- **Toggling host VPN breaks the running emulator's DNS** → restart with `-dns-server 8.8.8.8,8.8.4.4` (serial may change).
- **Emulator: no host audio; 4K-HEVC renders BLACK** (decoder runs, no visible frame). The 1080p quality default usually picks a decodable source. Confirm audio + 4K video on **hardware**.
- **Player seekbar needs a DRAG, not a tap** to seek: `adb shell input swipe <x1> <y> <x2> <y> 500` (a tap toggles play/pause). Useful for forcing end-of-episode to test auto-next.
- **Validate via screenshots** (`adb exec-out screencap -p`); `uiautomator dump` is flaky on Compose lists. Owner present this session → ask/show; when away → screenshot.
- Streams for the owner's `torbox=`-configured Torrentio arrive **pre-resolved as `url` with `[TB+]` cached marker** (NO infoHash) — cached detection = the `[TB+]` marker, not `checkCached(hash)`.
- **Collaboration:** `nabz` owns the **Home VOD network-category rows** (we only removed channels from Home). Coordinate Home edits.

## Notable follow-ups (in TODO/DESIGN)
- **OSS adopt opportunities (audit logged):** (1) **iptv-org/database** (Unlicense) for channel region/language — adopt over `RegionClassifier`; (2) **media3-ui-compose** (Apache-2.0) for player UI polish; (3) **Stremio `/subtitles` addon** for sideloaded subs. Hand-rolling was right elsewhere (CloudStream is GPL, etc.).
- Home → VOD network rails (nabz). Player subtitle/track UI (← the next task). Fold/hinge posture. TV-emulator validation pass. Confirm audio on hardware. Binge deferred edges: latch-restore on process-death; resume-aware next-episode (intentional).
