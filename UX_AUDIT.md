# Bouncer UX Audit — July 2026

Source of truth for the UX overhaul. Produced from three parallel codebase explorations (navigation/screens, design system/state, accessibility/performance). Updated at the end of implementation with what actually changed.

---

## 1. Architecture reality (what exploration found)

- **Single Activity** (`BouncerActivity`), single NavHost (`ui/navigation/BouncerNavHost.kt`), 4-tab bottom nav — Home / Matches / News / More (`BottomNavBarV2`). 27 live screens, all V2-named; V1 deleted June 2026.
- **Home** (`ui/HomeScreenV2.kt`): LazyColumn = two stacked filter-chip rows (status + category) → hero card (`HeroMatchCard`) → match list (`MatchCardV2`). "Live/Upcoming/Recent" are **chips, not tabs**.
- **Match detail** (`ui/MatchDetailViewV2.kt`): `ScoreHeaderV2` + ScrollableTabRow with 6 tabs (Overview / Scorecard / Commentary / Highlights / Info / AI) + HorizontalPager. OverviewTabV2 is 1012 lines of real content; HighlightsTabV2 is a 52-line empty shell.
- **Onboarding** exists (4-page, button-driven pager, gated on pref or existing favourites). **Search** is real (6 tabs, DB + live merge). Deep-link scheme is `app://bouncer` (not `bouncer://`).
- **V2 theme globally active**; tokens complete in `ui/theme/v2/`: `ColorTokensV2` (incl. semantic `CricketEventColors` for wicket/four/six/etc.), `TypographyV2` (Inter), `SpacingV2` (4dp grid), `ShapesV2`, `ElevationV2`. Note: dark-mode primary is `Gold300 #F3DB93`; `#E0B74F` is `Gold700` (light primary / dark `primaryDark`).
- **State**: `LiveActivityViewModel` (home) = 13+ scattered flows, layout-coupled — home tab taxonomy must not change without a VM refactor. `MatchDetailViewModel` = clean single `UiState` — detail re-layout is safe. No DI framework (manual constructor defaults).

## 2. Findings by principle

### Attention & hierarchy
- **Visual hierarchy**: Home leads with two rows of filter chips — controls before content. The hero card (the thing that matters) is below two rows of equal-weight chips. *(Fix: B1)*
- **Von Restorff**: gold appears in ~59 files; when everything is gold-accented, nothing pops. Chip selected-state, hero border, subscribe bell, section accents all compete. *(Fix: B1 — one gold element above the fold)*
- **Gestalt**: team + score + status grouped well inside `MatchCardV2`/`TeamRow`. But TalkBack reads the card as fragments (no `mergeDescendants`) — the grouping is visual-only. *(Fix: A3)*

### Cognitive load
- **Hick's Law**: two stacked chip rows (status × category) at top of home = 8+ equal-weight choices before any content. *(Fix: B1 — progressive disclosure of category chips)*
- **Progressive disclosure**: match detail does this well (6 tabs, overview first). Home does not.

### Interaction & feedback
- **Fitts's Law violations** (sub-48dp targets): `MatchCardV2.kt:94` subscribe bell 32dp (primary action on the primary card), `SearchScreen.kt:457` clear 24dp, `PlayerComparisonScreen.kt:282` 24dp, `BatsmanMilestoneCard.kt:196` 32dp, `HighlightClipCard.kt:76` 32dp, `PlayerDetailScreenV2.kt:187` retry 16dp (smallest target on the highest-frustration path). *(Fix: A4)*
- **Broken affordance**: FavoritesScreen `onTeamClick` wired as no-op `{}` in nav graph — tapping a favourited team does nothing, though `TeamDetailScreen` exists and is reachable from search. *(Fix: A1)*
- **Feedback**: score updates animate (`AnimatedScoreText`) but no haptics anywhere on live events; no visual distinction when a wicket/boundary lands vs any other recomposition. *(Fix: B3)*

### Emotional engagement
- **Peak-End**: no designed peaks. Wicket, boundary, match-win pass silently through the same text update. `CricketEventColors` (wicket/four/six semantic colors) exist and are unused for moments. `BatsmanMilestoneCard` overlay exists for milestones only. *(Fix: B3)*
- **Zeigarnik**: live cards show static score, no tension cue ("needs 43 off 22") even though the data exists on `CricketMatch`. *(Fix: B4)*
- **Personalization**: favourite teams don't reorder or emphasize the home feed — everyone sees the identical generic feed. `FavouriteTeamsManager` exposes a `StateFlow` ready to use. *(Fix: B2)*
- **Trust (anti-dark-pattern)**: StatsHubScreenV2 shows **hardcoded fake stats** ("1,234 / 98,321 / 4,321"); PlayerDetailScreenV2 **silently falls back to mock data**; SeriesDetail has 2 of 3 tabs as "coming soon". Fake data reads as manipulation once noticed. *(Fix: C1 — honest empty states)*

### Accessibility (violations flagged regardless of other work)
| # | Issue | Where |
|---|-------|-------|
| A-1 | Zero `remember` in OverviewTabV2 — filter/map/sort/distinctBy/associateBy re-run every recomposition on the live screen | `OverviewTabV2.kt:306,512,605,622,708-711,866-869` |
| A-2 | No `liveRegion` on score updates — TalkBack silent on live score change | `AnimatedScoreText.kt` |
| A-3 | Sub-48dp touch targets (6 sites) | see Fitts list above |
| A-4 | Contrast: dot-ball text at 0.3-0.35 alpha (fails AA) | `CommentaryTabV2.kt:433,464`, `RecentBallsStrip.kt:64` |
| A-5 | Scores clip at large font scale (`TextOverflow.Clip`, maxLines=1) | `AnimatedScoreText.kt` |
| A-6 | Infinite LIVE pulse ignores reduced-motion | `StatusBadge.kt:109` |
| A-7 | No `mergeDescendants` on match cards | `MatchCardV2.kt` |
| A-8 | 116 `contentDescription=null` vs 46 meaningful; team logo null | `MatchDetailViewV2.kt:457` |
| A-9 | Zero `derivedStateOf` app-wide; inline filtering per recomposition | `AllMatchesScreenV2.kt:63-68` |
| A-10 | Coil: crossfade present, placeholders absent | app-wide |

### Design-system debt
- ~590 hardcoded hex literals across 38 non-theme files (legacy premium/enhanced/elite/glass family) bypassing tokens and `CricketEventColors`.
- Duplicated primitives: 2 skeletons, 3 empty states, 2 pull-refresh, 2 bottom navs, 2 top bars, 2 spacing systems.
- `BouncerColors.kt` deprecated, pointing at nonexistent `UnifiedColors.kt`.
- Orphaned routes `squad/*`, `match-info/*` (defined, no composables). Legacy `StatsDetailActivity`/`RemindersActivity` outside Compose graph.

## 3. Ranked backlog

Sorted by impact-to-effort. Impact H/M/L · Effort S/M/L · Risk = shared components / state shape.

| ID | Change | Impact | Effort | Risk | Principle |
|----|--------|--------|--------|------|-----------|
| A1 | Fix no-op favourites team tap (1 line, `BouncerNavHost.kt:174`) | H | S | none | Affordance honesty, personalization |
| A4 | Touch targets → 48dp (6 files) | H | S | med (shared card) | Fitts's law |
| A3 | TalkBack: liveRegion on scores, merged card semantics, clip fix | H | S | low | A11y = feedback parity |
| A5 | Contrast fixes + reduced-motion gate on LIVE pulse | M-H | S | low | Legibility, WCAG 2.3.3 |
| A2 | Memoize OverviewTabV2 (8-10 sites) | H | M | low | Doherty threshold |
| A6 | Coil placeholders (token-colored) | M | S | low | Perceived perf |
| B1 | Home hierarchy: progressive disclosure of category chips, one gold element above fold | H | M | low-med | Hick's, Von Restorff |
| B2 | Personalized feed: favourites float top + subtle marker, hero prefers favourite | H | M | med | Personalization, serial position |
| B3 | Peak-End: event flash (semantic colors), haptics on wicket/boundary, end-of-match banner | H | M | med | Peak-End, feedback |
| B4 | Zeigarnik cue on live cards (true tension line, no fake urgency) | M | S-M | med | Zeigarnik |
| B5 | derivedStateOf hygiene | M | S | low | Perf |
| C1 | Honest empty states (StatsHub fake numbers, SeriesDetail stubs, PlayerDetail silent mock) + orphan route cleanup | H (trust) | M | product | Anti-dark-pattern |
| C2 | Hex migration, live-nav-reachable files only | M | L | high | Token discipline |

**Quick wins (headline 5)**: A1 (one-line broken-nav fix), A4 (32→48dp subscribe bell), A3 (silent scores for TalkBack), A5 (dot-ball legibility), A2 (live-screen jank).

**Deferred** (see §5 at end of project): component dedup, gold-discipline audit (~59 files), `LiveActivityViewModel` single-UiState refactor, full hex migration (dead-file subset).

## 4. Changelog (updated as implemented)

*(pending — filled in per commit)*

## 5. Not done yet

*(pending — filled at end)*
