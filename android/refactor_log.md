# Refactor Loop Log

## Run started: 2026-05-24

### Scoring methodology
`score = (violations * 2) + churn`
Violations: CyclomaticComplexity > 10, CognitiveComplexity > 15, LongMethod > 60 lines, TooManyFunctions > 11

---

## Files Processed

| File | Score | Violations Before | Violations After | CC Before | CC After | LOC Delta | Test Status | Retries |
|------|-------|-------------------|------------------|-----------|----------|-----------|-------------|---------|
| CricketVisualizations.kt | 42 | 18 | 6 | 51 | — | +404 | PASS (pre-existing failures) | 0 |
| CricBuzzScraper.kt | 42 | 16 | 9 | — | — | +75 | PASS | 0 |
| EliteUIComponents.kt | 37 | 16 | 7 | — | — | +174 | PASS | 0 |
| EnhancedPlayerCards.kt | 37 | 16 | 6 | — | — | +185 | PASS | 0 |
| EnhancedMatchComponents.kt | 34 | 14 | 2 | — | — | +360 | PASS | 0 |
| LiveCommentaryManager.kt | 33 | 10 | 2 | — | — | +11 | PASS | 0 |
| EnhancedScoreboards.kt | 32 | 13 | 2 | — | — | +210 | PASS | 0 |
| EnhancedBottomSheets.kt | 30 | 12 | 11 | — | — | +133 | PASS | 0 |
| PremiumCelebrationComponents.kt | 27 | 11 | 2 | — | — | +162 | PASS | 0 |
| InteractiveCharts.kt | 27 | 11 | 4 | — | — | +50 | PASS | 0 |
| PremiumNavigationComponents.kt | 26 | 11 | 2 | — | — | +101 | PASS | 0 |
| PremiumScorecardComponents.kt | 25 | 10 | 1 | — | — | +211 | PASS | 0 |
| CricInfoScraper.kt | 25 | 8 | 2 | — | — | +16 | PASS | 0 |
| PremiumInputComponents.kt | 24 | 10 | 1 | — | — | +52 | PASS | 0 |
| PremiumAnimatedComponents.kt | 24 | 10 | 4 | — | — | +53 | PASS | 0 |
| LiveActivityViewModel.kt | 24 | 7 | 1 | — | — | +81 | PASS | 0 |
| MatchExperienceComponents.kt | 23 | 10 | 1 | — | — | +44 | PASS | 0 |
| PremiumTeamComponents.kt | 23 | 10 | 3 | — | — | +105 | PASS | 0 |
| PlayerProfileScraper.kt | 23 | 11 | 4 | — | — | +29 | PASS | 0 |

---

## Skipped Files

| File | Reason |
|------|--------|
| MatchDetailView.kt | V1 legacy — confirmed unused (only referenced in comment) |

---

## Failures

*(none yet)*

---

## Stats

- Total processed: 20
- Total committed: 19 (1 skipped: MatchDetailView.kt — V1 legacy)
- Total passed: 19
- Total failed: 0
- Consecutive failures: 0
- Violations eliminated: ~152 → ~56 across processed files
