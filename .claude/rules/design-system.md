---
paths:
  - "app/src/main/kotlin/**/ui/theme/**"
  - "app/src/main/kotlin/**/ui/components/**"
  - "app/src/main/kotlin/**/ui/screens/**"
  - "app/src/main/res/values*/colors.xml"
---

# Design System

## The visual grammar is restraint, and it is load-bearing

The August 2026 pass exists because the app read as generated rather than designed. Each rule is also written into the file that owns it.

- **Surface.** A list of same-kind things is **rows on one background split by `HorizontalDivider`**, never a card each — `MatchCardV2` is the reference. `BouncerCard` means "these belong together", not "this is important": 12dp radius, `surface` fill, 1dp `divider` border, **0 elevation**. Use `BouncerRowGroup(items) { row(it) }` when several rows share one container. Never nest cards.
- **Colour, one job each.**
  - Every number, name, over and status line is neutral (`onSurface`/`textSecondary`). A result is not win-green, or the Recent tab is one green column.
  - Red = live only.
  - **Gold = interactive affordance only** (links, selected chip/tab, action text) plus the wordmark. Not section headers, borders, fills or the top bar (which is the page colour with a hairline).
  - **Team colour identifies a team, as a shape or as ink**, and only when `TeamColorRegistry.isKnown` is true. Known means a hand-picked kit colour (`ui/theme/TeamPalettes.kt`) or an entry in `assets/TeamColors.json`. `tools/extract_team_colors.py` writes that file weekly, taking each team's colours from the first source that has them: TheSportsDB or Wikidata kit colours, then the crest, then the national flag. `tools/update_logos_extra.py` gives every side its TheSportsDB badge over Cricbuzz's crest. For a club or franchise that replaces a simplified mark on a coloured square. For a national side (and its Women, A and U19 variants) it replaces the flag with the board's crest (BCCI for India), a choice the owner made in October 2026. `NATIONAL_BOARD_CRESTS = False` in that tool puts the flags back. The same tool also fills sides that have no crest at all. On the device, a variant with no crest of its own ("India A Women") takes its parent side's crest (`baseSide` in `TeamLogoManager.kt`). Kit colours win, because a flag is often not the shirt: Australia's flag is blue and the side plays in gold. An unknown side is neutral grey. The old hash fallback painted associate fixtures magenta against lime, and it is gone.
    - **Ink** (team names, codes, the batting side's score in the hero and score header) goes through **`rememberTeamInk(name)`**, which returns `TeamColors.ink` (the brand colour pushed to 4.5:1 against surface and background, pinned by `TeamColorRegistryTest`) or `onSurface`. List-row scores stay neutral: a column of numbers in a different hue per row competes with itself.
    - **Two-sided proportional bars** call **`rememberSegmentColors`** (`ui/theme/TeamSegmentColors.kt`), which goes neutral unless both sides are known. It once lived inline in `HeroMatchCard` alone, so the match-detail card painted Amsterdam Flames magenta against Dublin Guardians blue. Do not re-inline it.
    - **Crests** are `TeamCrest` (`components/v2/TeamCrest.kt`): fitted on a neutral plate, with the team code as a monogram when there is no crest. TheSportsDB crests have transparent backgrounds drawn for one background colour, so the tool tests each one against both `surfaceVariant` plates (`crest_ink` in `tools/team_sources.py`, plates pinned by `LogoInkTest`). A one-ink line-art crest that vanishes on a plate (England's navy on dark, New Zealand's white fern on light) is listed in `assets/TeamLogoInk.json`, keyed by URL, and `rememberCrestTint` redraws it there in `onSurface` (`SrcIn`): the reversed version a logo kit carries. A filled shape is never reversed, because it becomes a blob (Middlesex, Hampshire). Where the rule cannot reach without catching crests it must not, a crest is reversed by hand in `REVERSE_OVERRIDES` (`tools/update_logos_extra.py`), checked by eye in a preview. Scotland is the one so far: two inks with ~60% vanishing on dark, and loosening the rule to catch it would also turn Kenya's multicolour lion white. A multicolour crest that vanishes is not used, and the side keeps its Cricbuzz crest (Surrey). Cricbuzz's grey placeholder (image id `174284`, carried by ~49 crestless sides) counts as no crest: `isCricbuzzPlaceholderLogo` skips it on the device, and `tools/update_logos.py` drops it from the asset. **People** are `PersonAvatar`: a photo or initials.
  - Dark-mode text greys are neutral, not cream/tan.
  - Event colours only on ball chips and commentary badges.
  - `glassBorderHighlight`/`glassBorder` are **border** tokens; using them as a fill gave Fall of Wickets its olive cast.
- **Numerals are tabular.** Scores repoll ~every 6s and proportional digits reflow the row. `scoreLarge`/`scoreMedium`/`numeric`/`numericSmall` set `fontFeatureSettings = "tnum"`; use them for anything that re-renders with new digits in place.
- **Motion reports state changes only.** A score tick and the event flash are motion. A breathing glow, sweeping shimmer, pulsing border, bouncing bell and pulsing LIVE dot are decoration: `StatusBadgeV2` is a static dot and a word with no tinted pill, and `SkeletonLoaders` are static blocks.
- **Type and labels.** The face is IBM Plex Sans, bundled in `res/font` (`BouncerFontFamily`) — not Inter, and not a downloadable font (it fell back to Roboto when the Play provider was slow). Plex digits are tabular by construction. `SectionHeaderV2` is sentence case in `onSurface`; a tiny tracked all-caps overline is the most recognisable generated-layout tell. A list row marks only a **live** match; upcoming and decided rows say so in their status line. Home's filters are text tabs plus one competition menu, not chip rows with counts, and its hero appears only for a live match.

**Don't restate the fixed header.** `ScoreHeaderV2` is a sibling of the pager, so it is on screen for every tab. Overview used to open with the status, CRR/RRR, recent balls and a "momentum stream" that were all already above it. A "need X off Y" chase card was considered and dropped for this reason: the header's status line is Cricbuzz's own.

**Never present a figure the app does not have.** `PremiumAiStatisticsTab` shipped a hardcoded bar chart as "ANALYTIC CONTRIBUTION", hardcoded sparklines as run-rate trends, a constant string as a "predictive engine" insight, and the win probability relabelled as both an impact score and a model confidence. The only server-side model is win probability (`bouncer-win-probability`).

**Density.** Home shows **4 matches per screen** (hero plus three rows, or four rows), measured against `other-apps/cricinfo|cricbuzz`. Five fit at 10dp `rowVertical` and read as cramped; the row is 14dp with 8dp between its lines. Those reference apps and `design/stitch-mocks/` are the design spec; check a change against them before adding chrome.

## Light mode

`BouncerThemeV2` resolves Light/Dark/System and there is a user picker. Light mode was unusable for three independent reasons, each now fixed:

1. **`LocalBouncerColors` had no provider.** It is read by ~46 files via `BouncerDesignSystem.colors`, and only the legacy `BouncerTheme` (never called) provided it, so every read returned the static dark default. `BouncerThemeV2` provides `legacyColorsFor(isDark)` from `ui/theme/LegacyColorBridge.kt`.
2. **Dark-only top-level constants were `val`s** (`TextPrimary`, `GlassSurface`, `GoldPrimary`, …). The raw literals are suffixed `*Dark` and the original names are **composable getters** reading the bridge, so ~260 call sites follow the theme unedited. Anything used outside composition points at the `*Dark` constant.
3. **Light `primary` (#E0B74F) is 1.74:1 on white.** Gold as *fill* and gold as *ink* are separate roles: `primary` vs **`primaryText`** (#8A6516, 5.34:1). In dark they are the same value, so every `primary` → `primaryText` swap is a light-only change.

`BouncerThemeV2` maps the **full** M3 scheme: unmapped roles fall through to the M3 baseline, which is purple, and `secondaryContainer` / `surfaceContainer*` paint the nav indicator and every Card. Pinned by `LightModeContrastTest` (WCAG floors for both palettes) — add a token, add its pair there.

**The legacy `textTertiary` and `textMuted` are text roles.** They are raised at the bridge (`LegacyColorBridge.kt`) to clear 4.5:1, because sixteen files draw text in them; so `LegacyDarkColors` is no longer exactly `BouncerColors()`. Over-strip chips are a solid fill with `ballChipInk`. Both pinned in `LightModeContrastTest`.

**Contrast helpers are direction-aware, and team colours have a ground.** `Color.ensureContrast` compared luminance the wrong way round and walked colours *toward* the background; `liftForContrast` only raised HSV value, correct on dark and bleaching on light. Both now move away from the background. `TeamColorRegistry` baked every brand colour against `BlackBackground`; it now takes a `Ground` (background + surface + ink). Use `rememberTeamGround()` / `rememberMatchTeamPalette` rather than calling the registry with the default.

**RemoteViews colour is resolved per Context, not per constant.** `values/colors.xml` is the light set and `values-night/colors.xml` the dark one — keep them key-for-key identical. Widgets and the seven notification layouts read through them, and `WidgetRemoteViewsColors.of(context)` replaces the old object of `toArgb()` constants (a singleton would cache the wrong mode). Glance styles pair day/night via `cp(light, dark)`. The **splash stays dark in both modes on purpose**: `ic_launcher_foreground.png` is a mostly-opaque black mark, so a light plate renders a black square — hence `@color/splash_background` has no night variant.
