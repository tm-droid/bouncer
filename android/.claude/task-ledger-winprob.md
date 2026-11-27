# Goal: Win probability v7 — research-driven model upgrades (lab-gated) + full audit, fixed and shipped

Plan: /Users/tanushmalhotra/.claude/plans/do-a-lot-of-tranquil-whale-of-533e2b3c-c7f5-4d20-923a-0bee41f2f032.md
Research: android/docs/winprob-research-2026-11.md. Reports per unit: android/.claude/reports/<unit>.md
Worktree: Bouncer/.claude/worktrees/winprob-v7 (branch winprob-v7 from origin/main). iOS units: main checkout, ios-rebuild.
Gate: >=0.001 log-loss gain, match-bootstrap interval excluding 0, holdout ECE not worse by >0.005.

## Units
- [x] U0: worktree + ledger + research doc | files: android/.claude/task-ledger-winprob.md, android/docs/winprob-research-2026-11.md | done when: branch = winprob-v7, both files exist
- [x] B9: second-pass audit (read-only) | files: reports/B9.md | done when: report exists; new bugs added as B units
- [ ] A1: lab workflow + baseline | files: .github/workflows/winprob-lab.yml | done when: run success; artifact holdout log-loss per head within 0.005 of manifest
- [x] B1: chase head selection + recomputed rates | files: match-sync/src/winprob.js, main.js | done when: regression test (chase, missing rrr) scores via chase head; npm test green
- [x] B7: Android list-bar alignment + draw-safe history flip | files: LiveActivityViewModel.kt, ProbabilityHistory.kt | done when: testDebugUnitTest --tests "*Probability*" green, lintDebug + detekt clean, whatsnew updated
- [ ] B8: iOS hero bar aligned by team id (ios-rebuild) | files: MatchHeroModel.swift + test | done when: test_sim hero-model tests pass
- [ ] E0: eval upgrades + simulated live-ECE noise | files: winprob-lab.mjs, winprob-live-score.mjs | done when: report JSON has new fields; ECE p50/p95 at n=74 logged here
- [ ] E1: early-chase rate variants + 172-chase probe | files: lab (+trainer if adopted) | done when: lab table with intervals + probe numbers
- [ ] E2: era drift (rolling par, recency weights) | done when: lab table vs E1-winner baseline
- [ ] E3: estimated resource table / two-stage first innings | done when: lab table
- [ ] E4: time-varying context terms | done when: lab table
- [ ] E5: strength model (Elo scale, MoV, K decay, shrinkage) | done when: lab table
- [x] B4: Test head as-of followedOn + strength | files: training.js, build.js | done when: as-of pin test passes; npm test green
- [ ] B5: symmetric pre-match | done when: test p(A,B)+p(B,A)=1±1e-9 on published model
- [ ] B6: publish gate coverage + monotonic grid + clamp-as-served | files: build.js | done when: each check fails on crafted bad model, passes on current
- [ ] E6: data hygiene (drop DLS, per-match weights, T10) | done when: lab table ball- and match-weighted
- [ ] E7: Test head rebuild | done when: four-fold Test lab table
- [ ] E8: recalibration variants | done when: lab table incl. ECE
- [x] B2: reduced-overs serving + oversRem notation | done when: 15-over chase and x.4-overs tests pass
- [ ] B3: format routing (FC, T10) | done when: tests pin each format's route
- [ ] B10: train/serve skew replay | done when: report lists per-feature mean |diff|; each >0.05 explained
- [x] B11: Test scheduledDays = days played (corpus.js:94 -> training.js:549) | done when: as-of pin test passes; npm test green
- [ ] B12: ballsBowled counts wides/no-balls (training.js:516, lab) | done when: test pins legal-ball count; lab+trainer agree with serving
- [x] B13: contextstore swallowed shard read keeps degraded context (contextstore.js:51-110) | done when: test: failed shard read -> retry/not cached; npm test green
- [x] B14: squad/team pairing by position (context.js:341-352) | done when: test with swapped squad order joins correctly
- [ ] D1: adopt winners in trainer + match-sync | done when: all function npm test + ContextMathVendorParityTest pass
- [ ] D2: push main + refit | done when: train run success, manifest moved, publishProblems empty
- [ ] D3: deploy match-sync | done when: live-winprob from new model on a live match
- [ ] D4: rules doc update | done when: <= old size + 3 KB, every E verdict named

## Decisions
- Lab on GitHub Actions: repo is public, standard runners free (user worried about minutes; checked).
- iOS MatchDetailStore.swift:407 draw flip held: user WIP in that file.
- ios-rebuild has 1 unpushed android commit (b6f75c6e) not on main; left alone.

- A1 check could not pass as written: baseline run 45930475806 (success, 3.6 min) used 23477 matches vs the trainer's 23241 (Cricsheet grew 236 in 4 days), so the holdout moved 2025-01-27 -> 2025-03-05, and the lab has no row equal to v6's served feature set. Check kept unchanged; E0 adds `--cutoffs` pinning and a "v6 as served" row, then A1 is re-run.
- Lab runs take ~4 min on Actions, not 15-25: E units are agent-bound, not compute-bound.

## Log
- U0: `git -C .claude/worktrees/winprob-v7 branch --show-current` -> winprob-v7; research doc 30,026 B.
- A1 reference (manifest not public, 403): trainer run 2026-11-23 log, winprob-v6-202611231031. Core holdout log-loss: chase 0.3765, first 0.5660, prematch 0.6266, long (Test) 0.6034. As served: chase 0.3765, first 0.5639, prematch 0.6241.
- B1: verifier PASS. `npm test` (match-sync) -> tests 474, pass 474, fail 0. Old src + new tests -> pass 19, fail 2; (a) actual 0.5374 vs expected 0.5597 (old evaluate used first-innings head). liveRates moved above evaluate JSDoc after review; npm test still 474/0.
- B7: `./gradlew testDebugUnitTest --tests "*Probability*" --tests "*MatchLiveDetailAlignmentTest" --tests "*LiveActivity*"` -> BUILD SUCCESSFUL; TEST-MatchLiveDetailAlignmentTest tests=14 failures=0 incl. both new list tests; lintDebug BUILD SUCCESSFUL; detekt (JBR 21) BUILD SUCCESSFUL, baseline diff empty; whatsnew 490 B. ProbabilityHistory.kt:46 left: Tests are never stamped (B9 verdict o WRONG).
- B9: reports/B9.md: a b c d e f h i j k CONFIRMED, m o WRONG (m was nonetheless real per B7's failing test), g UNSURE; new N1-N13. Added units B11 (N1 scheduledDays leak), B12 (N2 ballsBowled counts wides/no-balls), B13 (N4 contextstore swallowed shard read), B14 (N5 squad/team pairing by position). N3 -> folded into B2/E6, N6 = B4.
- Live vs Cricbuzz (archive m/*.json evalLog -> scratchpad/wpe, 360 logs, `winprob-live-score.mjs --dir <wpe> --resolve`, all resolved): live chase 0.4006 [0.295,0.513], first 0.5671, pre-match 0.6446, ECE all 0.0357 (356 matches, 2986 points). Cricbuzz paired 42 matches/343 points: ours 0.5356 vs Cricbuzz 0.5111, diff +0.0245 [-0.0687, +0.1351], Brier 0.1645 vs 0.1633 -> no verdict (~7000 paired matches to settle at this gap: a tie in practice). Cricinfo: no paired points (wci- files are not archived; Appwrite paused since ~2026-11-24, keep-alive failing in bouncer-archive "Appwrite alive"). Live chase/pre-match trail the lab (0.3765/0.6241) by 0.024/0.020; first innings matches -> B10 skew replay and B5 target those two.
- B13+B14: verifier PASS. `node --test test/context.test.js` -> 22/22; `npm test` (match-sync) -> tests 477, pass 477, fail 0. HEAD contextstore.js+context.js + new tests -> exactly 2 fail (failed shard read not stored as final; swapped squad order). 404 still caches empty shard; other errors uncached, rebuild within 2-builds budget. Left: patch() preview/summary can still write a degraded held context.
- B4+B11: verifier PASS. `npm test` (trainer) -> 62 pass, 0 fail. HEAD corpus.js + HEAD followedOn + new tests -> 2 fail (follow-on flagged from innings 3 only; scheduled length 2 !== 5). walkLongRows: lookup before record; same-date results buffered (training.js:468-483); matches date-sorted at build.js:466 before fitLongModel (528). Serving agrees: scheduledDays TEST 5 else 4 (cricbuzz.js:1153), followedOn from feed, strength from shipped winRate (0.5 unseen). Test head holdout log-loss will rise (old figures included leaks); Test head is not in publishProblems (-> B6).
- B2: verifier PASS. `node --test test/reduced-overs.test.js` -> 4/4; `npm test` (match-sync) -> 481/481; HEAD winprob.js+main.js + new tests -> 1 pass 3 fail (x.4 -> 20 not 22; 15-over chase; reduced first innings chaseStart null). Verifier flagged the implied-balls branch could shave balls from the feed's 2-dp RRR; added a whole-over gap guard + test (1 off 90 at 0.07 -> 90): fails without the guard, `npm test` 482/482. ballNbr = legal balls (fixtures 49.3 = 297, 16.4 = 100). Limit: oversRem absent in every fixture; a reduced first innings that completes is undetectable.
