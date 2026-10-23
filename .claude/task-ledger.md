# Goal: Design upgrade — big-crest match hero (collapsing), richer Home, premium failure/empty states, live motion, result card, consistency

Plan (full specs, research, decisions): /Users/tanushmalhotra/.claude/plans/https-youtu-be-8eayze6y3i8-si-bv7shoahbe-pure-firefly.md
Resume: read the plan's unit spec, then find the first `[ ]` below. Previous ledger (June 2026 refactor, complete) is in git history.

B = app/src/main/kotlin/com/tanush/bouncer · T = ./gradlew testDebugUnitTest --tests · D = detekt under JBR 21

## Units
### Wave 1
- [ ] U0: foundations (scoreHero, motion tokens, all strings) | files: B/ui/theme/v2/TypographyV2.kt, res/values/strings.xml | done when: compileDebugKotlin + T *HardcodedUiStringRatchetTest green
### Wave 2
- [ ] U1: hero logic (chase line, innings split, Test lead/trail, collapsed summary) | files: B/ui/components/v2/MatchHeroLogic.kt + test | done when: T *MatchHeroLogicTest
- [ ] U2: thread LiveSituation + lastUpdatedMs into MatchDetail UiState | files: B/viewmodels/MatchDetailViewModel.kt, MatchLiveDetailAlignment.kt | done when: T *MatchLiveDetailAlignmentTest *MatchDetail*
- [ ] U3: header collapse math | files: B/ui/screens/matchdetail/v2/HeaderCollapse.kt + test | done when: T *HeaderCollapseTest
- [ ] U4: start countdown | files: B/ui/components/v2/MatchStartLabel.kt + test | done when: T *MatchStartTimeTest
- [ ] U5: result summary logic | files: B/ui/screens/matchdetail/v2/SummaryResult.kt + test | done when: T *SummaryResultTest
- [ ] U6: one failure pattern | files: B/ui/components/v2/ScreenStates.kt, StaleDataBanner.kt, EmptyState.kt, B/ui/components/ErrorComponents.kt | done when: T *ScreenStatesTest *LightModeContrastTest
- [ ] U13: live motion + score a11y | files: AnimatedScoreText.kt, RecentBallsStrip.kt, PremiumWinProbabilityHero.kt, LiveScoringGraphCard.kt | done when: T *ScoreRollTest; rememberInfiniteTransition only in CricketBallPullRefresh.kt
- [ ] U16: freshness logic | files: B/ui/components/v2/Freshness.kt + test | done when: T *FreshnessTest
- [ ] U17: Home hero selection (multi followed teams) | files: B/ui/HomeFeatured.kt + test, HomeScreenV2.kt (splitFeatured call) | done when: T *HomeFeaturedTest
### Wave 3
- [ ] U7: shared MatchHeroV2 + WinProbabilityBarV2 | files: B/ui/components/v2/MatchHero.kt, WinProbabilityBar.kt | done when: compile + D + no gradients
- [ ] U11: Summary result card | files: SummaryResultCard.kt, SummaryTabV2.kt | done when: T *SummaryResultTest *LiveAndSummaryDerivationTest
- [ ] U12: apply failure pattern app-wide | files: Home, Scorecard, Commentary, Series, News, Player, Comparison, Search, PointsTable, TeamDetail | done when: action_retry only in shared components
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
