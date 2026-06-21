# Tesla Casting — PARKED

_Status: **ON HOLD** (2026-06-21). Do not build yet. Blocked on identifying the
screen-mirroring app the owner uses._

Separate from [`DESIGN.md`](DESIGN.md) / [`TODO.md`](TODO.md) because it's a niche,
not-yet-scheduled feature with an open dependency.

## What the owner actually does (corrected)

The owner watches AIO TV on his **phone**, and uses a **screen-mirroring app** to mirror
the phone's screen (whatever is playing) onto the **Tesla's display**. The exact app is
**unknown** — that's the blocker. (Earlier assumption — a phone-hosted web server feeding the
Tesla's built-in browser — is **not** what he does; see "Alternative" below.)

Reported symptoms with his current setup: the page/cast sometimes doesn't load; it
"recognises it then crashes the stream."

## Why mirroring changes everything (good news)

With screen mirroring, the **phone decodes and renders** the video and the mirror just sends
**pixels** to the Tesla. Implications:
- **No Tesla-side codec limits.** MKV / HEVC / anything the phone's ExoPlayer can play will
  mirror fine. The codec/container problem that would kill a browser-based approach is moot.
- **Our app needs little-to-no Tesla-specific code.** It mainly has to: play reliably, run
  full-screen, keep the screen on (we already do), and **not block screen capture**.
- The "recognises then crashes" symptom is therefore most likely **not** codecs — more likely:
  - a **secure surface** (`FLAG_SECURE` / protected video path) that mirroring can't capture,
  - a **SurfaceView vs TextureView** capture quirk, or
  - the mirroring app / Tesla connection itself.

## Open questions (must answer before building)

1. **What is the mirroring app?** (name / how it's installed)
2. **How does it connect** to the Tesla — USB cable, wireless display, or a Tesla-specific app?
3. Does it mirror the **whole screen**, or hook a media route / cast protocol?
4. Does it fail only on **our app**, or on other video apps too? (Isolates app-side vs app-agnostic.)

## When we revisit — what to check in our player

- Confirm we do **not** set `FLAG_SECURE` on the activity/player surface (default ExoPlayer +
  PlayerView don't, but verify — it's the #1 mirroring blocker).
- Consider `TextureView` for the player surface if a mirror can't capture the `SurfaceView`.
- Keep-screen-on is already set; ensure full-screen / immersive while casting.
- Verify behaviour with non-DRM debrid streams (our case) — DRM-protected content can't mirror.

## Two possible technical paths (for later)

1. **Screen mirroring (owner's approach) — preferred.** App just plays; ensure no secure
   surface and a robust full-screen player. Minimal/no new code. Codec-agnostic.
2. **Local HTTP server → Tesla built-in browser (fallback only).** Phone runs a tiny web
   server (NanoHTTPD/Ktor) serving a `<video>` page; hand the **direct debrid URL** to the
   Tesla browser. Requires **filtering to browser-safe formats** (MP4 H.264/AAC or HLS) and
   **no on-device transcoding**. Only pursue if mirroring proves unreliable — it reintroduces
   the codec limitation that mirroring avoids.

## Decision

**Hold.** Once the mirroring app is identified, the first step is to confirm whether the issue
is a secure-surface/capture problem in our player (cheap to check/fix) before considering any
larger feature work.
