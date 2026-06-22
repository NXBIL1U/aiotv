# Live TV — Sky-style refinement (plan)

> Refinement of the v2 Live TV UX (branch `feat/live-tv-ux-v2`, not yet landed), validated against
> Sky's mobile TV Guide and a UX review. Goal: make Live TV cohere like Sky's mobile EPG — one
> filter, richer now/next rows — and give TV/foldable a proper structure. Executed subagent-driven
> with on-device validation between phases.

## Why
A UX review + the owner's Sky-mobile reference agreed the disjointedness comes from **two filter
bottom sheets (Region + Category) feeding a plain list**, not from the scrolling list itself
(Sky's mobile EPG *is* a scrolling list, and works). Fixes: collapse to **one** category control,
move **Region to Settings** (set-once, default UK+US+EN), enrich rows **Sky-style**
(channel number + now/next with times + a progress bar), and give **TV/foldable** a persistent
category rail instead of the phone layout with bigger fonts.

## Keep (do not change)
Search-primary; UK+US+EN default; favourites (channels+categories) + recently-watched; the
cache-first Room layer; `.ts` playback; the region *classifier* + reactive filtering in the VM.

## Global constraints
- Build: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`, `ANDROID_HOME="$HOME/Library/Android/sdk"`, `./gradlew assembleDebug`. Emulator `emulator-5554` (provider via VPN).
- Don't break cache-first, favourites/recents, search, or VOD/player. Region filtering stays driven by `AppDataStore.liveRegions` (already reactive in `LiveTvViewModel`).

---

## Phase A — One filter: Region → Settings, single category control
**Outcome:** the landing has ONE filter (category), Sky-style; region is chosen in Settings.
- Add a **Live TV regions** multi-select to `SettingsScreen` (US, UK, EN, LATAM, EU, MENA, Other) writing `AppDataStore.setLiveRegions(...)` (Flow + setter already exist; default `DEFAULT_LIVE_REGIONS`). Show current selection; persist on toggle or Save.
- In `LiveTvScreen`: **remove the Region control/`RegionPicker` from the landing**. Replace the separate "Categories" button + region chip with a **single Sky-style category trigger** — a row showing the current category + a ▾ (e.g. "All Channels ▾") that opens the existing searchable `CategoryPicker`. Optionally show the active regions as a small non-interactive subtitle (e.g. "UK · US · EN") with a tap-through to Settings.
- Keep `RegionPicker.kt` only if reused by Settings; otherwise remove it from the screen flow.
- Validate: landing shows one category control; changing regions in Settings re-scopes the list; search still global.

## Phase B — Sky-style rich rows
**Outcome:** channel rows look like Sky's EPG.
- Capture the real **channel number**: add `num` to `XtreamStream` (`@SerialName("num")`), to `ChannelEntity`, set it in `LiveTvRepository.refresh()` (Xtream `num`; M3U `tvg-chno` if present, else index); keep `num` ordering.
- `ChannelRow`: show **channel number + logo** (leading), then **now** (`HH:mm · title`, bold) with a thin **progress bar** computed from `now.startMs/endMs` vs current time, then **next** (`HH:mm · title`, muted). Keep the ★ trailing. "—" when no EPG. (No programme thumbnails — Xtream `get_short_epg` has no images.)
- Validate on emulator (screenshot): rows show number + now/next + progress, matching Sky's layout.

## Phase C — TV / foldable structure
**Outcome:** on TV (D-pad) and unfolded foldable, a persistent category rail instead of the phone layout.
- Pass `isTv`/width into `LiveTvScreen`; for wide/TV: a **persistent left category rail** (focusable, D-pad-friendly — reuse `TvNavRail` focus patterns) + channel list on the right; phone stays single-column with the category trigger. No modal sheets on the TV path (rail replaces the picker there).
- Validate on the TV emulator (`aiotv_tv`) if feasible, else reason from the phone + foldable widths.

## Phase D — Review, validate, land
- Scoped code review of the refinement diff; fix findings.
- Full emulator validation (phone; foldable/TV if available).
- Update TODO/DESIGN. Land per the owner's call.
