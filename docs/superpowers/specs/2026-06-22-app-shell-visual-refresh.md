# App-wide Netflix-style navigation + visual refresh (workstream spec)

_Status: **draft, web-validated, NOT started** (2026-06-22). Realises the DESIGN §1-2 "Netflix-grade"
north-star at the **app-shell** level — complements the per-surface work (Live TV ✅; VOD §6a +
Home network-categories queued). Three independent sub-areas; ship in the order below. Each gets
its own brainstorm/build; this is the umbrella + validated approach._

## Goal
One cohesive, Netflix-grade product across every surface: a **persistent navigation shell**, a
**dark + red** identity (replacing the blue), and a **real, wordless app icon**.

---

## A. Visual identity — dark + Netflix red _(do first; contained, high-impact)_
- **Palette:** base `#0B0B0F` · surface `#16161C` · elevated `~#1F1F27` · brand red `#E50914` ·
  text `#FFFFFF` / muted `#9A9A9A`. Dark-only (no light mode).
- **⚠️ Validated correction — do NOT wire raw `#E50914` to Material3 `primary`.** It fails WCAG AA
  (~3.2:1 on `#0B0B0F`). Instead: run `#E50914` through the **Material Theme Builder**
  (m3.material.io/theme-builder) and use the generated **dark-scheme `primary` (~tone 80, ≈`#FFB3AF`)**
  for interactive affordances + focus rings; reserve **raw `#E50914` for non-semantic brand
  surfaces only** (hero/splash/badges). Let the tonal system supply `onPrimary` (don't hand-set).
- **Where:** `ui/theme/` (`Color.kt`, `Theme.kt`) — `AccentPrimary`, `SurfaceCard`,
  `SurfaceElevated`, `background`, `Outline`. Then **audit hardcoded colours** across components and
  swap to the new tokens: the `#6C63FF` purple (FocusableCard borders, CategoryRail, EPG progress
  bar), the `#00C853` green badge, any blue in NavRail/Settings/Player.
- **TV focus rings:** use tone-90 or white (saturated red "vibrates" against near-black and is
  hard to track at 10 ft).
- Refs: [M3 color roles](https://m3.material.io/styles/color/roles) · [M3 contrast](https://m3.material.io/foundations/designing/color-contrast) · [M2 dark-theme saturated-colour warning](https://m2.material.io/design/color/dark-theme.html)

## B. App icon — wordless monogram _(independent)_
- **Direction (owner):** something **distinctive / eye-catching — NOT a generic play button or TV**.
- Concepts drafted in `docs/superpowers/icon-concepts/` (dark + red, wordless), `.svg` + rendered `.png`:
  - `A` TV+play, `B` framed play, `C` play+arcs — **rejected** (too literal / YouTube-ish).
  - **`D` "stacked streams"** (3 angled graded-red bars — many sources → one app) — _current front-runner_.
  - `E` "signal burst" (ray-burst + glowing core) — energetic but reads a bit sun/asterisk-ish.
- **Owner pick: D — "stacked streams"** (decided 2026-06-22). Refine to the final adaptive icon
  (foreground in the safe-zone + `<monochrome>` layer); iterate polish as needed.
- **Build:** Android Studio → New → Image Asset → "Launcher Icons (Adaptive and Legacy)" →
  `mipmap-anydpi-v26/ic_launcher.xml` (`<adaptive-icon>` foreground + background) + legacy PNGs;
  design foreground within the ~66% safe-zone; add a **`<monochrome>`** layer for Android-13 themed
  icons; export a 512px Play-style master. Ref: [Create app icons](https://developer.android.com/studio/write/create-app-icons)

## C. Navigation shell — persistent + immersive _(the architectural piece)_
- **Today (problem):** only `HomeScreen` renders nav chrome (`TvNavRail`/`SideNavRail`/
  `PhoneBottomNav`); Live TV, Search, Detail, Settings, Addons are full-screen pushes with only
  back-nav — inconsistent, not Netflix-style.
- **Validated approach:**
  - **Phone + foldable/tablet:** wrap top-level destinations in **`NavigationSuiteScaffold`**
    (`androidx.compose.material3:material3-adaptive-navigation-suite`, stable). It auto-reads
    `WindowSizeClass`: compact → bottom `NavigationBar`; medium/expanded → side `NavigationRail`.
    Drive selection from the current `NavBackStackEntry` route. Hide on full-screen routes with
    **`layoutType = NavigationSuiteType.None`** (player/Detail = immersive).
  - **TV (D-pad):** do **NOT** use `NavigationSuiteScaffold` (it renders a bottom bar on TV).
    Gate by `UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION` and use
    **`androidx.tv.material3.NavigationDrawer`** (`tv-material`, stable) — collapsed icon rail ↔
    expanded labelled rail, with built-in D-pad focus.
  - Move nav rendering **out of `HomeScreen`** into the app shell (`MainActivity` / wrapping
    `AppNavigation`); kill the duplication. Edge-to-edge: `WindowCompat.setDecorFitsSystemWindows(false)`
    + consume insets **only at the top-level scaffold**.
  - Fold in §4 IA cleanup: finalise top-level set (Home, Live TV, Search, Settings/Addons; drop stubs).
- **Pitfalls (from research):** never mix `androidx.compose.material3` and `androidx.tv.material3`
  in the same nav tree (focus chaos) — keep TV vs touch in separate composable trees; beware
  double-applied insets (`NavigationRail` + inner `Scaffold`); `NavigationSuiteType.NavigationDrawer`
  is a drawer, not a persistent rail.
- Refs: [Adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation) · [NavigationSuiteScaffold](https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/navigationsuite/package-summary) · [TV Navigation Drawer](https://developer.android.com/design/ui/tv/guides/components/navigation-drawer)

## In-app icon set (separate from the logo)
Adopt a permissively-licensed Compose `ImageVector` icon pack for the UI (nav, player controls,
chips) so iconography is consistent and TV-legible:

| Pack | License | Compose fit |
|---|---|---|
| **Lucide** (rec.) | ISC (≈MIT) | `com.composables:icons-lucide-android` (Maven Central), clean strokes, TV-legible |
| **Tabler** (rec.) | MIT | `tabler-icons-kmp` / `compose-icons`, 6k icons |
| Phosphor | MIT | `adamglin/phosphor-icon-android`, 6 weights |
| Material Symbols | Apache 2.0 | XML drawables — use where it must match M3 components |

**Private-app licensing:** MIT/ISC/Apache all permit private use freely; a never-published
(sideloaded) APK triggers no attribution/redistribution obligations. **Recommendation:** Lucide
(or Tabler) for the core set + Material Symbols where M3-component parity matters.

## Decisions (resolved 2026-06-22, owner)
1. **Theme contrast:** tonal red for interactive (Material-Theme-Builder dark `primary`, ≈`#FFB3AF`)
   + raw `#E50914` on brand surfaces only — a11y-safe. [§A]
2. **App icon:** concept **D "stacked streams"** (wordless); refine to the final adaptive icon. [§B]
3. **Nav-shell scope:** **phone + foldable first**; TV (`androidx.tv` `NavigationDrawer`) as a
   follow-up. [§C]
4. **Sequencing:** ship the **whole workstream together** — theme + app icon + in-app icon pack
   (Lucide/Tabler) + the **phone/foldable** nav shell — as ONE combined push; the TV shell follows.

## Sequencing & risk
Build order within the one push: **theme tokens → app icon + in-app icon pack → phone/foldable nav
shell**. The **nav shell is the heavy part** (moves chrome out of `HomeScreen` into an app-level
shell via `NavigationSuiteScaffold`; touches `MainActivity`/`AppNavigation` + every screen's chrome
assumption; care on back-stack, immersive player/Detail, insets). TV `NavigationDrawer` is a
separate follow-up (don't mix `androidx.tv.material3` with `androidx.compose.material3` in one nav
tree). **Coordinate with `nabz`** (his `nabil/stremio-mirror-fixes` branch) before the shell refactor.
