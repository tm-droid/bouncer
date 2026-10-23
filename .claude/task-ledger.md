# Goal: Design upgrade — big-crest match hero (collapsing), richer Home, premium failure/empty states, live motion, result card, consistency

Plan (full specs, research, decisions): /Users/tanushmalhotra/.claude/plans/https-youtu-be-8eayze6y3i8-si-bv7shoahbe-pure-firefly.md
Resume: read the plan's unit spec, then find the first `[ ]` below. Previous ledger (June 2026 refactor, complete) is in git history.

B = app/src/main/kotlin/com/tanush/bouncer · T = ./gradlew testDebugUnitTest --tests · D = detekt under JBR 21

## Units
### Wave 1
- [x] U0: foundations (scoreHero, motion tokens, all strings) | files: B/ui/theme/v2/TypographyV2.kt, res/values/strings.xml | done when: compileDebugKotlin + T *HardcodedUiStringRatchetTest green
### Wave 2
- [x] U1: hero logic (chase line, innings split, Test lead/trail, collapsed summary) | files: B/ui/components/v2/MatchHeroLogic.kt + test | done when: T *MatchHeroLogicTest
- [x] U2: thread LiveSituation + lastUpdatedMs into MatchDetail UiState | files: B/viewmodels/MatchDetailViewModel.kt, MatchLiveDetailAlignment.kt | done when: T *MatchLiveDetailAlignmentTest *MatchDetail*
- [x] U3: header collapse math | files: B/ui/screens/matchdetail/v2/HeaderCollapse.kt + test | done when: T *HeaderCollapseTest
- [x] U4: start countdown | files: B/ui/components/v2/MatchStartLabel.kt + test | done when: T *MatchStartTimeTest
- [x] U5: result summary logic | files: B/ui/screens/matchdetail/v2/SummaryResult.kt + test | done when: T *SummaryResultTest
- [x] U6: one failure pattern | files: B/ui/components/v2/ScreenStates.kt, StaleDataBanner.kt, EmptyState.kt, B/ui/components/ErrorComponents.kt | done when: T *ScreenStatesTest *LightModeContrastTest
- [x] U13: live motion + score a11y | files: AnimatedScoreText.kt, RecentBallsStrip.kt, PremiumWinProbabilityHero.kt, LiveScoringGraphCard.kt | done when: T *ScoreRollTest; rememberInfiniteTransition only in CricketBallPullRefresh.kt
- [x] U16: freshness logic | files: B/ui/components/v2/Freshness.kt + test | done when: T *FreshnessTest
- [x] U17: Home hero selection (multi followed teams) | files: B/ui/HomeFeatured.kt + test, HomeScreenV2.kt (splitFeatured call) | done when: T *HomeFeaturedTest
### Wave 3
- [x] U7: shared MatchHeroV2 + WinProbabilityBarV2 | files: B/ui/components/v2/MatchHero.kt, WinProbabilityBar.kt | done when: compile + D + no gradients
- [x] U11: Summary result card | files: SummaryResultCard.kt, SummaryTabV2.kt | done when: T *SummaryResultTest *LiveAndSummaryDerivationTest
- [x] U12: apply failure pattern app-wide | files: Home, Scorecard, Commentary, Series, News, Player, Comparison, Search, PointsTable, TeamDetail | done when: action_retry only in shared components
### Wave 4
- [ ] U8: match-detail hero + collapse | files: B/ui/MatchDetailViewV2.kt, ScoreHeaderRows.kt, MatchDetailHeader.kt, LiveTabV2.kt | done when: D + T *MatchTabsTest + emulator pass
- [ ] U9: Home hero | files: B/ui/components/v2/HeroMatchCard.kt | done when: D + HeroWinProbability gone + ≤240dp
- [ ] U10: Home rows | files: TeamRow.kt, MatchCardV2.kt | done when: D + 4 matches/screen
### Wave 5
- [ ] U14: consistency pass | files: many (crests, shapes, type roles, pressed states, toggle haptic) | done when: D + full test
- [ ] U18: shared-element row→header (experimental; drop if risky) | done when: emulator clean or dropped with reason
- [ ] U15: docs + whatsnew | done when: rule docs updated, whatsnew ≤500 chars

## Decisions
- Flat hero, 56dp crests, no gradient. No win-prob bar on rows. Freshness on header + Home hero only. Test layout separate. Hero = followed-team-first by favourites order.
- Plex digits are already 600 units wide in every weight; no tnum font swap.
- Commit per unit locally; ONE push at the end after full verification.
- Per-unit commits use SKIP_RELEASE_NOTES=1 (nothing ships until the final push); U15 writes whatsnew before that push.
- New strings live in one block in strings.xml headed `<!-- Design upgrade 2026-10 -->`; later units grep it and reuse names.

## Log
- 2026-10-23: research done (15 tracks), plan approved, ledger written.
- 2026-10-23: baseline HEAD 2752492e green (assembleDebug + testDebugUnitTest cached UP-TO-DATE; detekt clean).
- 2026-10-23: U0 done (21c7d978): scoreHero; BouncerMotion Duration.Brisk/Medium/Measured + Springs.noBounce(); strings block. UnusedResources is an error (warningsAsErrors) — every new string must be consumed by the end.
- 2026-10-23: Wave 2 done, batch-verified (compile, 93 tests in 10 classes, detekt clean). Notes for later units:
  - U1 MatchHeroLogic.kt (HeroLine, chaseLineFor, ballsFromOvers, isTestMatch, collapsedSummary) + TestMatchHeroLogic.kt (splitInningsScore, testLeadLine, testDayFor). oversRemaining = decimal overs (server code only, unconfirmed on a live payload). chaseLineFor returns null when status text already states the requirement → UI shows status.
  - U2 UiState.situation, UiState.lastUpdatedMs (seeded on first score seen, moves only on score/overs change).
  - U3 rememberHeaderCollapse(): connection, offsetPx, fraction, setRange(px), expand().
  - U4 startLabelOf + MatchStartLabel(startMs, style, color) in existing MatchStartLabel.kt.
  - U5 summaryResultOf(match, scorecard) incl. ResultKind.Drawn (needs a string in U11).
  - U6 EmptyStateV2 kept as is (8 callers); FailedState, StaleDataBanner, DelayedSkeleton, failureKindOf, screenStateOf. Timeout = Server.
  - U13 AnimatedScoreText(describedByParent=false default) + Modifier.scoreSemantics + scoreSentence; infinite transitions only in CricketBallPullRefresh.kt.
  - U16 rememberFreshness + FreshnessLabel; caller swaps LIVE for "Last seen live" when Stale.
  - U17 featuredFor(...allowUpcoming = HERO_SHOWS_UPCOMING const false) — U9 flips it and passes kind.
- 2026-10-23: Wave 3 done, batch-verified (compile, full unit tests, detekt clean; lint only flags hero_expand_score/hero_collapse_score/hero_next_up, consumed in Wave 4).
  - U7 MatchHeroV2(match, situation, lastUpdatedMs, size: HeroSize.Large|Compact, modifier, onTeamClick), HeroLineText, WinProbabilityBarV2(match) (shown pre-match too); parts in MatchHeroParts.kt, HeroRunRates.kt.
  - U11 SummaryResultCard: crest + winner + margin only; performer rows dropped (already in per-innings sections) — told user, plan had them.
  - U12 Home/Scorecard/Commentary/Series use screenStateOf/FailedState/StaleDataBanner/DelayedSkeleton (MatchCacheService.savedAtMs); News/Player/Comparison/Search/PointsTable/Team use EmptyStateV2 (VMs expose only a message). action_retry left in MatchDetailViewV2 LiveDataErrorBanner → U8.

