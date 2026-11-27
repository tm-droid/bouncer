# Goal: Win probability v7 — research-driven model upgrades (lab-gated) + full audit, fixed and shipped

Plan: /Users/tanushmalhotra/.claude/plans/do-a-lot-of-tranquil-whale-of-533e2b3c-c7f5-4d20-923a-0bee41f2f032.md
Research: android/docs/winprob-research-2026-11.md. Reports per unit: android/.claude/reports/<unit>.md
Worktree: Bouncer/.claude/worktrees/winprob-v7 (branch winprob-v7 from origin/main). iOS units: main checkout, ios-rebuild.
Gate: >=0.001 log-loss gain, match-bootstrap interval excluding 0, holdout ECE not worse by >0.005.

## Units
- [x] U0: worktree + ledger + research doc | files: android/.claude/task-ledger-winprob.md, android/docs/winprob-research-2026-11.md | done when: branch = winprob-v7, both files exist
- [ ] B9: second-pass audit (read-only) | files: reports/B9.md | done when: report exists; new bugs added as B units
- [ ] A1: lab workflow + baseline | files: .github/workflows/winprob-lab.yml | done when: run success; artifact holdout log-loss per head within 0.005 of manifest
- [ ] B1: chase head selection + recomputed rates | files: match-sync/src/winprob.js, main.js | done when: regression test (chase, missing rrr) scores via chase head; npm test green
- [ ] B7: Android list-bar alignment + draw-safe history flip | files: LiveActivityViewModel.kt, ProbabilityHistory.kt | done when: testDebugUnitTest --tests "*Probability*" green, lintDebug + detekt clean, whatsnew updated
- [ ] B8: iOS hero bar aligned by team id (ios-rebuild) | files: MatchHeroModel.swift + test | done when: test_sim hero-model tests pass
- [ ] E0: eval upgrades + simulated live-ECE noise | files: winprob-lab.mjs, winprob-live-score.mjs | done when: report JSON has new fields; ECE p50/p95 at n=74 logged here
- [ ] E1: early-chase rate variants + 172-chase probe | files: lab (+trainer if adopted) | done when: lab table with intervals + probe numbers
- [ ] E2: era drift (rolling par, recency weights) | done when: lab table vs E1-winner baseline
- [ ] E3: estimated resource table / two-stage first innings | done when: lab table
- [ ] E4: time-varying context terms | done when: lab table
- [ ] E5: strength model (Elo scale, MoV, K decay, shrinkage) | done when: lab table
- [ ] B4: Test head as-of followedOn + strength | files: training.js, build.js | done when: as-of pin test passes; npm test green
- [ ] B5: symmetric pre-match | done when: test p(A,B)+p(B,A)=1±1e-9 on published model
- [ ] B6: publish gate coverage + monotonic grid + clamp-as-served | files: build.js | done when: each check fails on crafted bad model, passes on current
- [ ] E6: data hygiene (drop DLS, per-match weights, T10) | done when: lab table ball- and match-weighted
- [ ] E7: Test head rebuild | done when: four-fold Test lab table
- [ ] E8: recalibration variants | done when: lab table incl. ECE
- [ ] B2: reduced-overs serving + oversRem notation | done when: 15-over chase and x.4-overs tests pass
- [ ] B3: format routing (FC, T10) | done when: tests pin each format's route
- [ ] B10: train/serve skew replay | done when: report lists per-feature mean |diff|; each >0.05 explained
- [ ] D1: adopt winners in trainer + match-sync | done when: all function npm test + ContextMathVendorParityTest pass
- [ ] D2: push main + refit | done when: train run success, manifest moved, publishProblems empty
- [ ] D3: deploy match-sync | done when: live-winprob from new model on a live match
- [ ] D4: rules doc update | done when: <= old size + 3 KB, every E verdict named

## Decisions
- Lab on GitHub Actions: repo is public, standard runners free (user worried about minutes; checked).
- iOS MatchDetailStore.swift:407 draw flip held: user WIP in that file.
- ios-rebuild has 1 unpushed android commit (b6f75c6e) not on main; left alone.

## Log
- U0: `git -C .claude/worktrees/winprob-v7 branch --show-current` -> winprob-v7; research doc 30,026 B.
