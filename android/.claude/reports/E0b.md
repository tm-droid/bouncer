# E0b: win-probability lab, v6 as served baseline

Status: PARTIAL. Tasks 1 and 2 done. Tasks 3 (delta by balls-remaining band) and 4 (calibration slope/intercept, --latest-season) not done.

## What changed
`android/appwrite/tools/winprob-lab.mjs` (commit a82dab9e) adds a "v6 as served" block after the main walk. It mirrors the trainer's build.js: core and full (elevens) sinks per head (chase, first, prematch), each tier standardised on train, trained, temperature-fitted, then `system = share*full.holdout + (1-share)*core.rest`. Elevens come from `thinnedElevens` with `lcg(20261003)`. The clamped variant uses sigmoid(logit/T) clamped to [0.03, 0.97]. Skip with `--no-as-served`. Output: `report.asServed` and `report.a1`.

## Runs
- 45961806694 (commit a82dab9e): core and unclamped as-served valid; clamped figures null because build.js did not yet export SERVED_FLOOR/SERVED_CEILING at that commit.
- 45962412480 (head 921c038c, `--cutoffs 2024-03-08,2025-01-27`, label a1): all figures valid. Table below. Core and unclamped figures are identical to run 1.

## a1: holdout log-loss, lab minus reference
| Head | Lab core | Ref core | Diff | Lab as served | Ref as served | Diff | Lab clamped |
|---|---|---|---|---|---|---|---|
| chase | 0.37444 | 0.3765 | -0.0021 | 0.37466 | 0.3765 | -0.0018 | 0.37808 |
| first | 0.56498 | 0.5660 | -0.0010 | 0.56312 | 0.5639 | -0.0008 | 0.56319 |
| prematch | 0.62619 | 0.6266 | -0.0004 | 0.62410 | 0.6241 | +0.000004 | 0.62410 |
| long | n/a | 0.6034 | n/a | n/a | n/a | n/a | n/a |

Long: lab "all groups" is 0.6733, not comparable to the 0.6034 reference core; no long as-served row was built.

Gaps are small and negative, consistent with Cricsheet growing by about 236 matches. Not tuned to them. Eleven share is about 0.49 to 0.50 for each head. Chase clamp costs +0.0034 vs unclamped (clamp binds on lopsided chase states); first +0.00007; prematch none.

## Left
- Task 3: delta log-loss by balls-remaining band (T20 0-24, 25-60, 61-120, 121+, scaled for ODI) per variant vs baseline, match-bootstrap intervals, into report.json.
- Task 4: calibration slope/intercept with match-clustered bootstrap CIs; `--latest-season`.
- Long-head as-served row.
