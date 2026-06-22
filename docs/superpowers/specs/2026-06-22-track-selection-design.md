# Audio / subtitle track selection — smart defaults + built-in player UI (design)

Date: 2026-06-22 · Branch: `feat/binge-watch` (built directly on it, stacked after binge/watch) · Status: approved-to-spec

## 1. Problem & goal

The Player never configures **track selection**, so ExoPlayer's `DefaultTrackSelector` applies its
stock defaults: it auto-selects any **"default"-flagged** audio and text track in the stream. On real
debrid torrents this misbehaves — we hit a release that auto-played **foreign audio** and showed a
**default-flagged foreign subtitle** (a Hungarian sub auto-appeared) with no obvious way for the user
to change it. There is also **no visible affordance** to switch audio language or toggle subtitles.

Goal: make the common case correct by default, and give the user a one-tap manual override using
Media3's built-in chrome — **no custom UI** for v1.

1. **Default track preferences** on the ExoPlayer — **prefer English audio** (`setPreferredAudioLanguage("en")`,
   falling back to the source default when there is no English track) and **subtitles OFF by default**
   (`setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)` so a default-flagged foreign sub never auto-shows).
2. **Manual override = Media3 `PlayerView`'s built-in UI** — enable the dedicated **CC button**
   (`setShowSubtitleButton(true)`) and rely on the existing **settings (gear) menu**, which already
   offers Audio / Subtitles track lists. Selecting a track (or "None") flips the disabled flag and sets
   an override for us — no app code needed.

## 2. Goals / Non-goals

**Goals:** the two items above, applied to the single shared `ExoPlayer` in `PlayerScreen.kt`; correct
default for VOD; harmless no-op for live IPTV (typically one audio track, no embedded text).

**Non-goals (v1):**
- **No custom Compose track sheet** (that is Option 2, gated on TV validation — see §6).
- **No Settings toggles** for default audio language / subtitles-on — defaults are app constants for v1
  (the one open decision, §8). Per-play override is the built-in UI.
- **Sideloaded subtitles** via the Stremio `/subtitles` addon — deferred `[P4]` (owner has no subtitles
  addon installed).
- **Burned-in (hardcoded) subtitles** — these are pixels in the video, not a selectable text track, so
  track selection cannot remove them. Out of scope by physics; the fix targets *embedded/soft* tracks.
- **Quality** track selection — already handled by source-ranking in binge/watch; unrelated here.

## 3. Build-vs-adopt

Nothing to adopt — this is **stock Media3 configuration**, not a library decision.
`media3-ui` (the `PlayerView` + `PlayerControlView` track UI) is **already a dependency**; Media3 is
**1.5.1**, which has all three APIs (`setPreferredAudioLanguage`, `setTrackTypeDisabled`,
`PlayerView.setShowSubtitleButton`). No new dependencies, no new modules. The OSS follow-up
**media3-ui-compose** (logged in TODO) stays a *future* polish item, not part of v1.

## 4. Architecture / changes

Single file: **`ui/screen/player/PlayerScreen.kt`**. Two edits, both inside existing blocks.

| Unit | Change |
|---|---|
| `exoPlayer = remember { … }` (~L155-181) | After `.build()`, set the default selection params:<br>`setTrackSelectionParameters(player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage("en").setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build())`. Build the params off the player's current defaults so we only override these two axes. |
| `PlayerView(ctx).apply { … }` factory (~L314-327) | Add `setShowSubtitleButton(true)` so the CC toggle is always present alongside the gear menu. `useController = true` stays. |

No ViewModel, DataStore, domain, or DI changes. No route/nav changes. Live-IPTV path is the same
shared player, so it inherits the (harmless) English-audio preference and subs-off default.

**Why a single shared player is fine:** the same `ExoPlayer` instance persists across failover and
next-episode advance (only the `MediaItem` swaps). So the user's manual track choice (and the subs-off
default) carries forward across episodes — desirable, Netflix-like. See §7 for the one carry-over edge.

## 5. Behaviour

- **On play (any stream):** English audio preferred (else source default); **no subtitles shown**.
- **User taps CC button or gear → Subtitles:** picks a soft-sub track → it shows. Picking **"None"**
  turns them back off. The built-in dialog manages `setTrackTypeDisabled(TEXT, …)` and the override.
- **User taps gear → Audio:** switches audio track/language for this playback.
- **D-pad (TV):** the gear and CC buttons are part of the standard `PlayerControlView` and are D-pad
  focusable. **This is the thing TV validation judges** (see §6).

## 6. Validation gate — "TV is the decider"

The whole point of Option 1 is to avoid custom UI *if the built-in menu is usable on a remote*. So
TV-emulator validation is a **decision gate**, not just a smoke test:

- **Pass** → built-in track menu is navigable and selectable on D-pad → ship Option 1, done.
- **Fail** (clunky/unreachable/unreadable on D-pad) → that *triggers* a follow-up spec for a **custom
  Compose track sheet** (Option 2, NextPlayer-style, on-theme). **Do not build Option 2 unless TV says
  so.** Phone/Fold validation is secondary (touch is fine on the built-in UI).

## 7. Edge cases

- **Variant / regional English tags** → handled automatically. `DefaultTrackSelector` does
  prefix/subtag matching, not exact-string: `en-GB`/`en-US`/`en-CA` match `"en"` (subtag), `eng`
  matches (normalized to `en`), and even a non-standard `English` matches (starts with `en`). Genuinely
  other languages (`de`, `hu`, `fr`) are correctly skipped.
- **No English audio track, untagged track, or garbage tag** (`und`, missing metadata) → the preference
  has nothing to match, so `DefaultTrackSelector` falls back to the stream's **default audio** track —
  which is exactly what plays today with no preference set. The preference can only help, never regress.
  We do **not** do content-based language detection (analysing the audio) — we trust tags + fall back.
- **No text tracks at all** (most live IPTV) → CC button shows no options / disabled; subs-off is a no-op.
- **Carry-over across episode advance:** the player instance is reused, so the **subs-off default and
  any language-based audio preference persist** to the next episode (good). A *specific* per-track
  override the user picked (e.g. "this exact English sub group") won't match the next episode's track
  groups, so on advance text returns to the subs-off default rather than re-enabling — acceptable for
  v1; note it, don't fix it.
- **Burned-in foreign subs** → unchanged (not a track). Document as a known limitation.

## 8. Open decision (for owner review)

**Hardcode the defaults vs. expose Settings toggles.** v1 spec = **hardcoded constants** (English audio,
subs off), matching the brainstorm's "no custom UI for v1". There is already a Settings → Playback
section (preferred quality) that a "Default audio language" / "Subtitles on by default" pair of controls
could mirror cheaply if wanted. **Recommendation: ship hardcoded for v1**, add Settings toggles later
only if the owner wants per-user defaults. Confirm at review.

## 9. Testing

**No new pure logic → no unit tests of substance.** This is player configuration validated by
observation. (Optional cheap guard: a JUnit4 test asserting a small `defaultTrackParams(base)` helper
yields `preferredAudioLanguages == ["en"]` and text disabled — only worth it if we extract the helper;
otherwise skip and rely on emulator validation.)

**Manual on emulator (VPN OFF — strem.fun is Cloudflare-403'd on the VPN IP; restart with `-dns-server`
if a fetch 403s):**
1. Play a VOD title whose release has multiple audio tracks and/or a default-flagged foreign sub
   (Rick & Morty / a multi-audio movie). Confirm: **English audio plays, no subtitle is shown** on start.
2. Tap the **CC button** → enable a subtitle → it appears; select **None** → it hides.
3. Open the **gear menu** → switch **Audio** track → audio language changes.
4. **TV emulator (`aiotv_tv`):** repeat 2–3 entirely on **D-pad**. Judge the §6 gate.
5. Episode advance (binge): start E1, let it auto-advance to E2 → confirm subs stay off by default and
   audio preference still applies.

**Hardware (owner):** confirm audio actually switches and subtitles render (emulator has no host audio;
4K-HEVC renders black — covered by playing a 1080p source).

## 10. Decisions

- Default selection = **prefer-English audio + subtitles-disabled**, set once on the shared `ExoPlayer`.
- Manual override = **built-in Media3 `PlayerView`** (CC button + gear menu), **no custom UI** in v1.
- Scope = **in-stream tracks only**; sideloaded subs (Stremio `/subtitles`) and burned-in subs are out.
- **TV validation is the decision gate** for whether Option 2 (custom sheet) becomes necessary.
- Built directly on **`feat/binge-watch`** (no separate branch); commit locally; owner merges.
