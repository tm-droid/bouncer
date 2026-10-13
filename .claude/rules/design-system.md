---
paths:
  - "app/src/main/kotlin/**/ui/theme/**"
  - "app/src/main/kotlin/**/ui/components/**"
  - "app/src/main/kotlin/**/ui/screens/**"
  - "app/src/main/res/values*/colors.xml"
---

# Design System

**The visual grammar is restraint, and it is load-bearing.** The August 2026 pass exists because
the app read as generated rather than designed. Four rules, each written into the file that owns it:

- **Surface.** A list of same-kind things is **rows on one background split by `HorizontalDivider`**,
  never a card each — `MatchCardV2` is the reference. `BouncerCard` means "these belong together",
  not "this is important": 12dp radius, `surface` fill, 1dp `divider` border, **0 elevation**. Use
  `BouncerRowGroup(items) { row(it) }` when several rows share one container. Never nest cards.
- **Colour, one job each.** Every number, name, over and status line is neutral
  (`onSurface`/`textSecondary`) — a result is not win-green, or the Recent tab is one green column. Red = live only. **Gold = interactive affordance only** — links,
  selected chip/tab, action text — plus the wordmark; not section headers, not borders, not fills, not
  the top bar (which is the page colour with a hairline). Team colour only
  where it identifies a team as a *shape* (the Home hero's win-probability bar), **never as ink**, and only
  when `TeamColorRegistry.isKnown` is true for both sides: its hash fallback painted associate fixtures
  magenta against lime. That gate is **`rememberSegmentColors`** (`ui/theme/TeamSegmentColors.kt`) and
  every proportional two-sided bar calls it — it lived inline in `HeroMatchCard` alone, so Home obeyed
  the rule while the match-detail win-probability card painted Amsterdam Flames magenta against Dublin
  Guardians blue. Do not re-inline it; a mirrored rule is not a pinned rule. Dark-mode text greys are neutral, not cream/tan. Event colours
  only on ball chips and commentary badges. `glassBorderHighlight`/`glassBorder` are **border**
  tokens — using them as a fill is what gave Fall of Wickets its olive cast.
- **Numerals are tabular.** Scores repoll ~every 6s; proportional digits reflow the row on each
  tick. `scoreLarge`/`scoreMedium`/`numeric`/`numericSmall` set `fontFeatureSettings = "tnum"` —
  use them for anything that re-renders with new digits in place.
- **Motion reports state changes only.** No ambient loops. A score tick and the event flash are
  motion; a breathing glow, a sweeping shimmer, a pulsing border, a bouncing bell and a pulsing LIVE
  dot are decoration — `StatusBadgeV2` is a static dot and a word, with no tinted pill. `SkeletonLoaders` are static blocks for the same reason.

- **Type and labels.** The face is IBM Plex Sans, bundled in `res/font` (`BouncerFontFamily`) —
  not Inter, and not a downloadable font, which fell back to Roboto whenever the Play provider was
  slow. Plex digits are tabular by construction. `SectionHeaderV2` is sentence case in `onSurface`;
  a tiny tracked all-caps overline is the most recognisable generated-layout tell. A list row marks
  only a **live** match; upcoming and decided rows say so in their status line. Home's filters are
  text tabs plus one competition menu, not chip rows with counts, and its hero appears only for a
  live match.

Two related traps. **Don't restate the fixed header**: `ScoreHeaderV2` is a sibling of the pager,
so it is on screen for every tab (a "need X off Y" chase card was considered and dropped for this
reason — the header's status line is Cricbuzz's own, which already says it) — Overview used to open with the status, CRR/RRR, recent balls and
a "momentum stream" that were all already above it. And **never present a figure the app does not
have**: `PremiumAiStatisticsTab` shipped a hardcoded bar chart as "ANALYTIC CONTRIBUTION",
hardcoded sparklines as run-rate trends, a constant string as a "predictive engine" insight, and
the win probability relabelled as both an impact score and a model confidence. The only server-side
model is win probability (`bouncer-win-probability`).

Density target: Home shows **4 matches per screen** (hero plus three rows, or four rows), measured
against `other-apps/cricinfo|cricbuzz`. Five fit at 10dp `rowVertical` and read as cramped; the row
is 14dp with 8dp between its lines. Those three reference apps and `design/stitch-mocks/` are the
design spec; check a change against them before adding chrome.

**Light mode is a first-class palette, and the legacy layer is bridged into it.** `BouncerThemeV2`
resolves Light/Dark/System and there is a user picker, but for a long time light mode was unusable
for three independent reasons. (1) `LocalBouncerColors` — read by ~46 files via
`BouncerDesignSystem.colors` — had **no provider** outside the legacy `BouncerTheme`, which the app
never calls, so every one of those reads returned the static dark default. `BouncerThemeV2` now
provides `legacyColorsFor(isDark)` from `ui/theme/LegacyColorBridge.kt`; `LegacyDarkColors` is
`BouncerColors()` so dark is byte-identical. (2) The dark-only top-level constants (`TextPrimary`,
`GlassSurface`, `GoldPrimary`, …) were `val`s, constant by construction. The raw literals are now
suffixed `*Dark` and the original names are **composable getters** reading the bridge, so ~260 call
sites follow the theme unedited; anything used outside composition points at the `*Dark` constant.
(3) Light `primary` (#E0B74F) is 1.74:1 on white. Gold as *fill* and gold as *ink* are now separate
roles — `primary` vs **`primaryText`** (#8A6516, 5.34:1). In dark they are the same value, so every
`primary` → `primaryText` swap is a light-only change. `BouncerThemeV2` also maps the **full** M3
scheme: unmapped roles fall through to the M3 baseline, which is purple, and `secondaryContainer` /
`surfaceContainer*` paint the nav indicator and every Card. Pinned by `LightModeContrastTest`
(WCAG floors for both palettes) — add a token, add its pair there.

**Contrast helpers are direction-aware, and team colours have a ground.** `Color.ensureContrast`
compared luminance the wrong way round and walked colours *toward* the background;
`liftForContrast` only ever raised HSV value, which is correct on a dark surface and bleaches on a
light one. Both now move away from the background. `TeamColorRegistry` baked every brand colour
against `BlackBackground` regardless of theme — it now takes a `Ground` (background + surface + ink);
use `rememberTeamGround()` / `rememberMatchTeamPalette` rather than calling the registry with the
default.

**RemoteViews colour is resolved per Context, not per constant.** `values/colors.xml` is the light
set and `values-night/colors.xml` the dark one — keep them key-for-key identical. Widgets and the
seven notification layouts read through them, and `WidgetRemoteViewsColors.of(context)` replaces the
old object of `toArgb()` constants (a singleton would cache the wrong mode). Glance styles pair
day/night via `cp(light, dark)`. The **splash stays dark in both modes on purpose**:
`ic_launcher_foreground.png` is a mostly-opaque black mark, so a light plate renders a black square
— hence `@color/splash_background` has no night variant.

**The legacy `textTertiary` and `textMuted` are text roles.** They are raised at the bridge
(`LegacyColorBridge.kt`) to clear 4.5:1, because sixteen files draw text in them; so
`LegacyDarkColors` is no longer exactly `BouncerColors()`. Over-strip chips are a solid fill with
`ballChipInk`. Both pinned in `LightModeContrastTest`.
