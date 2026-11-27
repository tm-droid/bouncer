# D1e: lab "mov120" Elo update in the trainer's as-of walk

Change: `asOfState.js` `update()` now uses `ELO_K=120 * eloMargin(...)`, mirroring the lab (b05e140c) exactly:
`mult = ln(1+marginNorm)*2.2/(lead*0.001+2.2)`, `lead` = winner Elo minus loser Elo before the update (0 for a draw), `marginNorm` 0.3 when unknown.
No kByGames, idle regression or shrinkage. `eloDiff` feature and serving unchanged; as-of order unchanged (context read before update).

match-sync: no change. `rg ELO_K|expectedA|scoreA` over match-sync/src finds nothing; it reads shipped team tables and never walks Elo. The lab/skew tools import `AsOfState`, not `ELO_K`.

Tests (context.test.js, 4 new): margin numbers from the lab formula (big 19.1071 vs narrow 1.4816), chasing-win margin (60*ln1.5), favourite moves less than underdog by same margin, own result not in its Elo context.
Revert check (ELO_K back to 24, no margin): 4 new tests fail, pass 82 / fail 4. Restored afterwards.
Suites: train-win-probability 86 pass / 0 fail; match-sync 500 pass / 0 fail.

Holdout log-loss (head, as served), before (6da49a96) -> after:
- chase 0.3700 -> 0.3697
- first innings 0.5560 -> 0.5523
- prematch 0.6219 -> 0.6141
(core/full after: chase 0.3717/0.3695, first 0.5513/0.5626, prematch 0.6136/0.6176.)

Note: work started in the winprob-v7 worktree by mistake; the edits were moved to d1e and reverted there.
