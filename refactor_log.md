# Refactor Loop Log

## Run started: 2026-05-24

### Scoring methodology
`score = (violations * 2) + churn`
Violations: CyclomaticComplexity > 10, CognitiveComplexity > 15, LongMethod > 60 lines, TooManyFunctions > 11

---

## Files Processed

| File | Score | Violations Before | Violations After | CC Before | CC After | LOC Delta | Test Status | Retries |
|------|-------|-------------------|------------------|-----------|----------|-----------|-------------|---------|
| CricketVisualizations.kt | 42 | 18 | 6 | 51 (MatchCard) | — | +404 | PASS (pre-existing failures) | 0 |

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

- Total processed: 0
- Total passed: 0
- Total failed: 0
- Consecutive failures: 0
