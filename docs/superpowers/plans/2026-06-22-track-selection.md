# Audio / Subtitle Track Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Player prefer English audio and keep subtitles off by default, and expose Media3's built-in CC button so the user can override audio/subtitle tracks per-play.

**Architecture:** Configure `TrackSelectionParameters` once on the single shared `ExoPlayer` in `PlayerScreen.kt` (prefer-English audio + text-track disabled), and turn on `PlayerView`'s subtitle button. No ViewModel/DataStore/domain/DI changes; the built-in `PlayerControlView` gear menu + CC button provide the manual override, flipping the disabled flag and setting overrides for us.

**Tech Stack:** Kotlin, Jetpack Compose, Media3/ExoPlayer **1.5.1** (`media3-exoplayer` + `media3-ui`, both already dependencies).

## Global Constraints

- Media3 version is **1.5.1** — `setPreferredAudioLanguage`, `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, …)`, and `PlayerView.setShowSubtitleButton` all exist; **add no new dependencies**.
- **Defaults are hardcoded constants** (English audio, subtitles off) — **no Settings toggles** in v1 (owner-approved 2026-06-22).
- **Scope = in-stream tracks only.** No sideloaded subtitles, no custom Compose UI, no track-detection.
- File touched: **only** `app/src/main/java/com/itrepos/aiotv/ui/screen/player/PlayerScreen.kt`.
- Built directly on **`feat/binge-watch`** (no separate branch); commit locally; owner merges.
- Branch is the current branch; subagents run in **this workspace (no isolation)** → commits land on `feat/binge-watch`. Verify HEAD advances.
- Build env: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"`.
- **No new pure logic → no JUnit tests.** The verification cycle is *clean build + emulator observation*. Do not invent unit tests for player config.

---

### Task 1: Default track preferences + CC button in PlayerScreen

**Files:**
- Modify: `app/src/main/java/com/itrepos/aiotv/ui/screen/player/PlayerScreen.kt` (the `exoPlayer = remember { … }` block ~L175-181, and the `PlayerView(ctx).apply { … }` factory ~L316-324)
- Test: none (no pure logic — verified by build + emulator; see Task 2)

**Interfaces:**
- Consumes: the existing `exoPlayer` `ExoPlayer` instance and its `trackSelectionParameters` property (Media3); `C.TRACK_TYPE_TEXT` (`androidx.media3.common.C`, already imported at L44).
- Produces: no new public symbols. Behavioural contract: on play, English audio is preferred and no text track is auto-selected; the `PlayerView` shows a subtitle (CC) button.

- [ ] **Step 1: Set default `TrackSelectionParameters` on the shared player**

In the `exoPlayer = remember { … }` block, replace the trailing `.apply { setWakeMode(C.WAKE_MODE_NETWORK) }` on the built player with:

```kotlin
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                // Smart defaults for in-stream tracks: prefer English audio (DefaultTrackSelector
                // does prefix/subtag matching, so en-GB/en-US/eng also match; falls back to the
                // source default when there is no English track) and keep subtitles OFF so a
                // default-flagged foreign sub never auto-appears. The user overrides per-play via
                // PlayerView's CC button / gear menu, which flips this disabled flag for us.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage("en")
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
```

No new imports: `C` is already imported (L44); `trackSelectionParameters` is a property of the `ExoPlayer` receiver.

- [ ] **Step 2: Enable the subtitle (CC) button on `PlayerView`**

In the `AndroidView` `factory`, add `setShowSubtitleButton(true)` to the `PlayerView(ctx).apply { … }` block (keep `useController = true`):

```kotlin
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    keepScreenOn = true
                    setShowSubtitleButton(true)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
```

- [ ] **Step 3: Clean build (this is the "test" for a config change)**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`. Resolve any compile error (e.g. a wrong method name) before proceeding.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/itrepos/aiotv/ui/screen/player/PlayerScreen.kt docs/superpowers/specs/2026-06-22-track-selection-design.md docs/superpowers/plans/2026-06-22-track-selection.md
git commit -m "feat(player): default English audio + subtitles-off, enable CC button"
```

---

### Task 2: Emulator validation (also the Option 1 → Option 2 decision gate)

**Files:** none (validation only). This task gates whether a custom Compose track sheet (Option 2) becomes a follow-up.

**Interfaces:** Consumes the debug APK from Task 1.

**Preconditions:** **VPN OFF** (VOD via strem.fun is Cloudflare-403'd on the VPN IP). If a fetch 403s or DNS breaks after a VPN toggle, restart the emulator with `-dns-server 8.8.8.8,8.8.4.4`. Emulator has **no host audio** and renders **4K-HEVC black** → validate video on a **1080p** source; defer audio-switch confirmation to hardware.

- [ ] **Step 1: Install on the phone emulator**

```bash
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Success`.

- [ ] **Step 2: Validate defaults on a multi-track VOD title**

Play a VOD title whose release has multiple audio tracks and/or a default-flagged foreign sub (e.g. Rick & Morty, or a multi-audio movie). Capture a screenshot:
```bash
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 exec-out screencap -p > /tmp/track_default.png
```
Expected: **no subtitle burned across the frame on start**; a **CC button** is visible in the controls. (Owner present → also confirm by eye.)

- [ ] **Step 3: Validate manual override (touch)**

Tap the **CC button** → pick a subtitle track → it appears; pick **None** → it hides. Open the **gear** menu → **Audio** → switch track. Confirm each works (screenshot or owner confirmation).

- [ ] **Step 4: TV-emulator validation — the decision gate**

Boot the TV emulator (`aiotv_tv`, one emulator at a time), install, and repeat Step 3 **entirely on D-pad** (no touch):
```bash
"$ANDROID_HOME/platform-tools/adb" -s <tv-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```
Drive with key events, e.g.: `adb -s <tv-serial> shell input keyevent DPAD_DOWN / DPAD_CENTER / DPAD_RIGHT`.
- **PASS** (menu reachable + selectable on D-pad) → Option 1 is sufficient; feature done.
- **FAIL** (clunky/unreachable/unreadable) → log it and write a follow-up spec for the **custom Compose track sheet (Option 2)**. Do **not** build Option 2 unless this fails.

- [ ] **Step 5: Binge carry-over check**

Start an episode, let it auto-advance to the next (binge/watch) → confirm subtitles stay off by default and English-audio preference still applies on the new episode.

- [ ] **Step 6: Record the outcome**

Note the validation result (and the §6 gate decision) in `.superpowers/sdd/progress.md` and in the branch's notes; flag audio-switch + 4K as **needs hardware confirmation** (owner).

---

## Self-Review

**Spec coverage:**
- §1/§4 default English audio + subs-off → Task 1 Step 1. ✅
- §1/§4 CC button (`setShowSubtitleButton`) → Task 1 Step 2. ✅
- §3 no new deps → Global Constraints + Task 1 (no import/dependency changes). ✅
- §6 TV decision gate → Task 2 Step 4. ✅
- §7 variant/untagged tags, carry-over → covered by behaviour (Step 1 comment) + Task 2 Step 5. ✅
- §8 hardcoded, no Settings toggle → Global Constraints. ✅
- §9 no unit tests, emulator validation, hardware caveats → Task 2 (Preconditions + Step 6). ✅

**Placeholder scan:** No TBD/TODO/"handle edge cases"; every code step shows the exact code. ✅

**Type consistency:** `setPreferredAudioLanguage` / `setTrackTypeDisabled` / `C.TRACK_TYPE_TEXT` / `setShowSubtitleButton` are the real Media3 1.5.1 symbols; `trackSelectionParameters` is the `ExoPlayer` property. ✅
