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

## 4. Changelog — what actually shipped (July 2026)

Four commits, each built (`assembleDebug` + unit tests) and detekt-gated (zero new findings vs the 23 pre-existing at HEAD) before push.

### Phase A — quick wins (`65ab126`)
| Item | Principle | Outcome |
|------|-----------|---------|
| A1 favourites team tap | Affordance honesty | Was a no-op `{}`; now opens TeamDetailScreen |
| A2 OverviewTab memoization | Doherty threshold | All 10 derived list transforms hoisted into `rememberOverviewDerivedData` (new `OverviewDerivedData.kt`); no more per-ball re-derivation. OverviewTabV2 complexity *improved* vs HEAD (645→587 lines) |
| A3 TalkBack | Feedback parity | Polite `liveRegion` on `AnimatedScoreText` (scores now announced), one-utterance merged semantics on `MatchCardV2`, score clip fixed at large font scale |
| A4 touch targets | Fitts's law | 48dp on subscribe bell, search/comparison clear, milestone share — via `requiredSize` overflow so layouts don't shift. **Two audit items were false alarms**: `HighlightClipCard:76` (decorative icon, not clickable) and `PlayerDetailScreenV2:187` (icon inside a full `Button`) |
| A5 contrast + motion | Legibility, WCAG 2.3.3 | Dot-ball text alpha 0.3/0.35 → full `textSecondary`; EmptyState icon 0.5→0.7; LIVE pulse honors reduced-motion. **False alarm**: `RecentBallsStrip:64` alpha was the *background*, fg was fine |
| A6 Coil placeholders | Perceived perf | Team logos now use the existing `CoilImage` wrapper (placeholder/error/fallback) instead of raw `AsyncImage` |
| A8 deviation | — | Match-detail team logo kept `contentDescription = null` **deliberately**: the team name Text sits beside it; labeling would double-announce. Documented in code |

### Phase B — core polish (`83de46d`)
| Item | Principle | Outcome |
|------|-----------|---------|
| B1 home hierarchy | Hick's law, progressive disclosure, Von Restorff | Category chips collapsed behind a filter toggle (auto-expands when a non-default category is active — a hidden filter can never silently shape the feed). Chip selection restyled gold-fill → gold border+label, so the hero is the one gold mass above the fold |
| B2 personalization | Serial position, Von Restorff | Favourite-team matches float to top with gold star marker; hero slot prefers a live favourite match; `match.id` keys kept so reorder animates. No VM change — partition in `remember(matches, favourites)` |
| B3 Peak-End | Peak-End, feedback | Wicket/4/6 tints score header with semantic `CricketEventColors` for 1.5s + one haptic (LongPress wicket, tick boundary). Completed matches show trophy result banner in win green. Flash gated on reduced-motion, haptics on the user's setting |
| B4 Zeigarnik | Honest open loops | Completed-card status ("won by…") reads in win green; live chase line keeps batting-team color. No manufactured urgency |
| B5 filter hygiene | Perf | AllMatchesScreenV2 format filter memoized. (Home already used `derivedStateOf` — audit item A-9 was partially stale) |

### Phase C1 — trust (`55efdb1`)
- StatsHub fake summary ("1,234 / 98,321 / 4,321") deleted — hub is honest navigation only.
- PlayerDetail: failed load shows `EmptyStateV2` + Retry instead of silently rendering `generateMockPlayer` output as if real (~170 lines of mock code removed).
- SeriesDetail: Points Table tab no longer steals selection (returning used to land on a blank body). Its "coming soon" tabs were already honest `EmptyStateV2` — kept.
- Orphaned `squad/*` and `match-info/*` route definitions deleted along with tab-mapping references.

### Phase C2 — token discipline, reachable subset (`aa5c892`)
- All hex literals in components reachable from the live nav graph migrated to `BouncerThemeV2` tokens (premium card containers, chip backgrounds, gradient companion, milestone tier colors → `statusLive`/`cricket.six`).
- Reachable-component hex count: 10 → 1 (the survivor is a `@Preview` annotation requiring a compile-time const). Non-theme `ui/` hex occurrences now 270, concentrated in widgets/notifications (RemoteViews — *cannot* read Compose theme; legitimately hardcoded) and dead legacy files.

## 5. Not done yet (high-impact, deferred, with reasons)

| Item | Why deferred |
|------|--------------|
| Component dedup (2 skeletons, 3 empty states, 2 pull-refresh, 2 bottom navs, 2 top bars, 2 spacing systems) | Per-pair PRs with screenshot comparison; touching shared primitives across many screens is its own project |
| Gold-discipline sweep (~59 files) | Needs an allowlist doc first (gold = primary action / hero / favourite marker / brand chrome), then mechanical pass; depends on dedup landing |
| Dead legacy file deletion (EliteShareCard, BouncerSnackbar, LiveActivityStyles, PremiumShimmer etc. appear unreferenced) | Dead-code caution rules: manifest/Worker/Gson reflection roots must be verified beyond grep before deleting |
| `LiveActivityViewModel` single-UiState refactor (13+ scattered flows) | Layout-coupled; only needed if home tab taxonomy changes; explicitly out of scope per plan |
| Full hex migration of widgets/notifications | RemoteViews/Glance can't read Compose theme — needs a parallel resource-based token set, not a find-replace |
| `BatsmanMilestoneCard` infinite sparkle/pulse ignores reduced-motion | Legacy celebration component; same gating pattern as StatusBadge applies — small follow-up |
| `BouncerColors.kt` deprecation pointer to nonexistent `UnifiedColors.kt` | Fix alongside component dedup when the legacy theme family is next touched |
| Layout Inspector recomposition-count verification + on-device accessibility-scanner pass | Needs an attached device/emulator session; code-level checks (memoization, semantics, contrast math) done — runtime verification recommended before release |
| `HighlightsTabV2` (52-line shell) | Needs highlight data wiring, not UX polish — product decision |
| Live-region tuning under rapid updates | `Polite` mode chosen to avoid TalkBack spam; verify announcement frequency on-device with a real live match |

---

# Delta Audit — July 2026 round 2

Second pass under five lenses (A visual system, B interaction/feedback, C mobile structure, D emotional engagement, E behavioral psychology). Scope: delta only — items shipped in §4 are not re-audited; findings below are new or were deferred. Stitch mockups (`design/stitch-mocks/`, project "Bouncer Mocks") treated as intended direction. Verified against code, not screenshots.

## Corrections to prior audit / exploration false-positives

- Trophy Room, Achievements, Activity Feed, Stats Hub **do exist** as V2 screens (`TrophyRoomScreenV2`, `AchievementsScreenV2`, `IntelligenceFeedScreenV2`, `StatsHubScreenV2`) — Stitch gaps are content depth, not missing screens.
- Stitch mocks use Space Grotesk headlines; app is Inter-only — accepted deviation, single-font rule wins (Lens A typography: **Pass**).
- `ScreenBackgroundV2.kt` working-tree diff was whitespace only.

## Lens A — Visual System Consistency

> **Principle:** Component consistency & token discipline
> **Status:** Pass (V2 layer) / Fail (legacy layer)
> **Rationale:** `ui/theme/v2/` + `ui/components/v2/` fully tokenized — zero hex, zero glow, 6 shape tokens, 13-step Inter scale, tonal dark elevation. All debt confined to legacy `ui/components/` root (~24 files with hex; PitchMapView 12, LiveActivityStyles 10), `glass/` (5 files glassmorphism), `premium/` (~14 files gradient/glow).
> **Recommendation:** Delete verified-dead legacy files (S3); defer wholesale glass/premium removal (blast radius).

> **Principle:** Visual economy (gradient/glow earning its place)
> **Status:** Pass (V2) / Fail (legacy)
> **Rationale:** V2 gradients are limited to team-color hero backgrounds and shimmer — earning their place. ~47 legacy files use glow/blur decoratively.
> **Recommendation:** Same as above; no V2 change needed.

> **Principle:** Redundant primitives
> **Status:** Fail
> **Rationale:** 3 empty-state components, 2 pull-refresh wrappers, 2 skeletons, 2 bottom navs, 2 top bars still coexist.
> **Recommendation:** Dedup empty states → `EmptyStateV2` (S1) and pull-refresh → `PullRefreshWrapperV2` with cricket-ball indicator (S2) now; nav/top-bar/skeleton dedup deferred (whole-app chrome, needs screenshot review).

> **Principle:** Deprecation hygiene
> **Status:** Fail
> **Rationale:** `BouncerColors.kt` `@Deprecated` points at nonexistent `UnifiedColors.kt`; `BouncerNavGraph` comments claim Favorites/Stats are bottom tabs (actual: Home/Matches/News/More).
> **Recommendation:** Fix pointers/comments (Q4).

## Lens B — Interaction, Affordance & Feedback

> **Principle:** Every action gets explicit feedback
> **Status:** Fail
> **Rationale:** Subscribe bell (`MatchCardV2` L150) flips icon silently — primary action on primary card, no haptic. Onboarding team follow fires `subscribeToTopic` with zero acknowledgement. Player favorite toggle silent. Preference-gated haptic system (`utils/HapticFeedback.kt` with CONFIRM/REJECT) exists and is unused at these sites.
> **Recommendation:** Haptic + micro-bounce on bell; haptic on onboarding select and favorite toggle (Q1).

> **Principle:** Error states exist everywhere data is fetched
> **Status:** Fail (systemic)
> **Rationale:** Error frequently collapses into "empty": PointsTable ViewModel exposes `_error` but the screen's `when{}` ignores it; News same; Search swallows `AppResult.Error` into empty lists (reads as "no results" on network failure); match-detail container has no top-level error; refresh failures show nothing anywhere.
> **Recommendation:** Render error branches with `EmptyStateV2(onRetry)` (Q2); match-detail UiState error (M1); refresh-failure snackbars (M2). Copy: cricket-flavored, honest ("Rain delay — couldn't reach the server").

> **Principle:** No blank screens
> **Status:** Fail
> **Rationale:** `InfoTabV2` and `OverviewTabV2` render blank when data absent (sections gated on `isNotEmpty()`); `StatsHubScreenV2`, `TeamDetailScreen`, `NewsDetailScreenV2` lack loading/empty affordances.
> **Recommendation:** Skeletons + `EmptyStateV2` (M1).

> **Principle:** Gestures where they fit
> **Status:** Needs Improvement
> **Rationale:** Pull-refresh only on Home/AllMatches/News; PointsTable/TeamDetail/SeriesList are data screens with staleness and refresh functions already on their ViewModels. Long-press context menus already exist (MatchCardV2, Commentary) — prior audit shipped those.
> **Recommendation:** `PullRefreshWrapperV2` on the three screens (M4).

> **Principle:** Text over imagery legibility
> **Status:** Pass
> **Rationale:** App places text below/beside images, never overlaid; `CoilImage` wrapper handles broken images. Scrim tokens exist if ever needed.
> **Recommendation:** None.

## Lens C — Mobile Structure & Navigation

> **Principle:** Bottom nav 3–5 destinations, ≥44px targets
> **Status:** Pass
> **Rationale:** 4 tabs (Home/Matches/News/More), Material3 NavigationBar defaults ≥48dp. Prior audit fixed sub-48dp targets.
> **Recommendation:** None.

> **Principle:** One screen one job / no double-nesting
> **Status:** Pass
> **Rationale:** Match detail's 6 tabs are progressive disclosure done right; secondary actions already in bottom sheets (`MatchContextMenuSheet`, `ReminderBottomSheet`); cards not double-nested in V2.
> **Recommendation:** None.

## Lens D — Emotional Engagement & Polish

> **Principle:** Motion as feedback, not decoration
> **Status:** Fail
> **Rationale:** Zero `Crossfade` and zero `animateContentSize` app-wide — skeleton→content pops, expand/collapse snaps. Peaks (wicket/boundary flash + haptic) shipped in Phase B; transitions did not.
> **Recommendation:** Fade skeleton→content on state-branch screens; `animateContentSize` on expandables; Material default specs, reduced-motion gated (M3).

> **Principle:** Reduced-motion respect
> **Status:** Fail (one site)
> **Rationale:** `BatsmanMilestoneCard` infinite sparkle ignores reduced-motion (deferred item from round 1); StatusBadge pattern exists to copy.
> **Recommendation:** Gate it + sweep remaining `rememberInfiniteTransition` sites (Q3).

> **Principle:** Mascot/character moment
> **Status:** Pass (deliberate skip — user decision)
> **Rationale:** Cricket-ball pull-refresh indicator is the app's brand-delight moment; a new illustrated character is high-effort/subjective.
> **Recommendation:** Preserve the cricket ball through pull-refresh dedup (S2). Future option only.

> **Principle:** Warm copy at high-stakes moments
> **Status:** Needs Improvement
> **Rationale:** New error states are the opportunity; notification permission page is structurally fine but clinical.
> **Recommendation:** Cricket-flavored error copy (Q2/M1); one priming line before permission button (Q1).

## Lens E — Behavioral Psychology (applies only where flows exist)

> **Principle:** Loss aversion / contrast (premium, upsell, ads)
> **Status:** N/A — explicitly
> **Rationale:** Free app: no paywall, no signup, no ads, no premium tier in code (Stitch "AI Forecast (Premium)" mock has no code counterpart). Nothing to audit; not inventing one.
> **Recommendation:** None.

> **Principle:** Reciprocity (value before permission)
> **Status:** Pass (structure) / Needs Improvement (copy)
> **Rationale:** Onboarding picks teams before the notification-permission page, which has an explicit skip path — ordering already correct.
> **Recommendation:** Priming line "Follow your teams first — we'll only ping you about them" (Q1).

> **Principle:** IKEA effect
> **Status:** Pass
> **Rationale:** Team selection during onboarding + favourites-shape-the-feed (Phase B2) already implement investment-before-commitment.
> **Recommendation:** None.

> **Principle:** Smart defaults
> **Status:** Pass
> **Rationale:** Notification prefs default sensibly; onboarding is pre-seeded (no "0%" moment — pager, not a progress form).
> **Recommendation:** None.

## Stitch alignment gaps (flagged, not built — user decision)

Content-depth gaps vs mocks, all needing product/data work, out of scope this round: Trophy Room & Achievements depth, Activity Feed population, Analytics Hub visualizations, AI Forecast premium layout, `HighlightsTabV2` data wiring, home-screen widget redesign (RemoteViews token system prerequisite).

## Round-2 backlog (implemented this round unless marked deferred)

| ID | Change | Lens | Effort |
|----|--------|------|--------|
| Q1 | Haptics/bounce on silent toggles + onboarding priming line | B/D/E | S |
| Q2 | Render existing errors: PointsTable, News, Search (+`_error` flow) | B | S |
| Q3 | Reduced-motion gates on infinite animations | D/a11y | S |
| Q4 | Deprecation pointer + stale nav comments | A | S |
| M1 | Blank-render screens get states; match-detail top-level error | B | M |
| M3 | Crossfade skeleton→content; animateContentSize expandables | D | M |
| M2 | Refresh-failure snackbars (News, AllMatches, Home) | B | M |
| M4 | Pull-refresh: PointsTable, TeamDetail, SeriesList | B/C | S |
| S1 | Empty-state dedup → EmptyStateV2 | A | M |
| S2 | Pull-refresh dedup, cricket ball kept | A/D | M |
| S3 | Verified dead legacy file deletion | A | M |

Still deferred (unchanged reasons + new): nav/top-bar/skeleton/spacing dedup, gold-discipline sweep (needs allowlist decision), LiveActivityViewModel refactor, widgets/notifications hex, wholesale glass/premium deletion, HighlightsTab wiring, on-device passes, full `ScreenState<T>` adoption.
