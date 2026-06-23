# Multi-Phase Refactor Task Ledger

**Last updated:** 2026-06-23  
**Resume:** read this file first, find first `[ ]` or `[~]`, continue from there.

---

## Phase 0 — Ledger bootstrap

- [x] DONE — Created this file

---

## Phase 1 — Finish in-progress UI refactors

- [x] 1.1 — Verify PremiumScorecardTab.kt imports (BowlerPerformance, BowlingPerformanceCard)
- [x] 1.2 — Verify PointsTableScreen.kt imports (SeriesTeam, SeriesStandingsCard) + NavHost call sites
- [x] 1.3 — Build verify: `./gradlew assembleDebug test` — BUILD SUCCESSFUL in 8s, 47 tasks
- [~] 1.4 — Commit: `feat: integrate BowlingPerformanceCard and SeriesStandingsCard`

---

## Phase 2 — Module audit (audit-modules skill)

- [ ] 2.1 — Audit all 6 modules in parallel (ui/, network/, data/, services/, analysis/, managers/)
- [ ] 2.2 — Fix Critical findings in ui/
- [ ] 2.3 — Fix Critical findings in network/
- [ ] 2.4 — Fix Critical findings in data/
- [ ] 2.5 — Fix Critical findings in services/
- [ ] 2.6 — Fix Critical findings in analysis/
- [ ] 2.7 — Fix Critical findings in managers/
- [ ] 2.8 — Build verify: `./gradlew assembleDebug test`
- [ ] 2.9 — Commit: `fix: resolve Critical audit findings across all modules`

---

## Phase 3 — Ship

- [ ] 3.1 — Final clean build + test
- [ ] 3.2 — Push to main: `git push origin main`

---

## Audit Findings (filled in after 2.1)

_TBD_

---

## Build Log

| Task | Result | Notes |
|------|--------|-------|
| 1.3  | GREEN  | BUILD SUCCESSFUL in 8s, 47 tasks |
| 2.8  | -      | -     |
| 3.1  | -      | -     |
