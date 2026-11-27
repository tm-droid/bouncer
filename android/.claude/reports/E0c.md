# E0c: win-probability lab, Test head like-for-like, bands, calibration, latest season

Status: DONE with one open gap. All four tasks run; the Test head figure differs from the lab's own long figure by more than the 0.005 aim (see Left).

## What changed
`android/appwrite/tools/winprob-lab.mjs` (commit 5d2f95c0):
- Wide store keeps `rem` and `budget` per row; `fit` keeps stated probability, outcome and store index, and takes an optional windows list.
- `trainerLong()` reimplements `fitLongModel` from the trainer's exported primitives (same RowSink caps, `walkLongRows`, standardiser, `trainMulticlass`, `multiclassMetrics`, same windows) into `report.a1.long`.
- `report.bands` (chase, first): per variant minus baseline, scaled remaining = rem*120/budget, edges 0-24, 25-60, 61-120, 121+, match-bootstrap 95% intervals via the lab's `bootstrap`.
- `report.calibration` (all heads, baseline and every variant): logistic recalibration of logit(p) on outcome (0.05-logit binned Newton), 200-round match-clustered bootstrap.
- `--latest-season`: trains on everything before the last 12 months (2025-11-25 to 2026-11-25), scores those months. `report.latestSeason`.
The as-served block is untouched.

## Runs
- 45964028729 (head 5d2f95c0, `--cutoffs 2024-03-08,2025-01-27 --latest-season`, label e0c): success. report.json carries a1.long, bands, calibration, latestSeason, all finite except the structurally empty 121+ band.

## a1 (unchanged from E0b to 4 dp)
| Head | Lab core | Lab as served | Lab clamped | Lab all groups |
|---|---|---|---|---|
| chase | 0.37444 | 0.37466 | 0.37808 | 0.37192 |
| first | 0.56498 | 0.56312 | 0.56319 | 0.55854 |
| prematch | 0.62619 | 0.62410 | 0.62410 | 0.61662 |
| long (trainer-like) | 0.65960 | n/a | n/a | 0.67330 |

Long: trainer-like holdout log-loss 0.65960 (accuracy 0.7144, 19,495 holdout rows, 262,631 training rows, stopped at epoch 15). Old reference 0.6034 was pre-leak-fix, so this is +0.0562. This is the current trainer's target.

## Bands: delta log-loss vs baseline (negative is better), selected rows
Rows per band chase 14,782 / 37,035 / 72,289; first 26,325 / 43,003 / 73,741. Each cell is delta [95% CI].
| Head | Variant | 0-24 | 25-60 | 61-120 |
|---|---|---|---|---|
| chase | + xi | -0.0027 [-0.0076, 0.0028] | -0.0151 [-0.0203, -0.0105] | -0.0292 [-0.0344, -0.0231] |
| chase | + state | -0.0066 [-0.0120, -0.0015] | -0.0113 [-0.0156, -0.0068] | -0.0240 [-0.0290, -0.0178] |
| chase | forward xi+toss+state | -0.0071 [-0.0120, -0.0017] | -0.0161 [-0.0219, -0.0105] | -0.0380 [-0.0441, -0.0307] |
| chase | all groups | -0.0050 [-0.0099, 0.0015] | -0.0165 [-0.0219, -0.0113] | -0.0346 [-0.0403, -0.0282] |
| first | + xi | -0.0499 [-0.0577, -0.0408] | -0.0578 [-0.0665, -0.0480] | -0.0553 [-0.0631, -0.0478] |
| first | + venue | -0.0394 [-0.0475, -0.0314] | -0.0477 [-0.0555, -0.0393] | -0.0456 [-0.0530, -0.0387] |
| first | all groups | -0.0386 [-0.0482, -0.0283] | -0.0535 [-0.0628, -0.0436] | -0.0549 [-0.0640, -0.0465] |
All other variants are in report.json. The 121+ band is empty (remaining never exceeds the budget), reported as 0 rows and a null delta. In chase the 0-24 band gains least and its interval often includes 0.

## Calibration: slope (intercept), baseline "v5 features" vs all groups
| Head | Baseline slope [95% CI] | All groups slope [95% CI] | All groups intercept |
|---|---|---|---|
| chase | 1.001 [0.956, 1.060] | 0.991 [0.939, 1.055] | -0.022 |
| first | 1.086 [1.017, 1.170] | 1.065 [0.998, 1.140] | -0.089 |
| prematch | 1.136 [0.948, 1.320] | 1.186 [1.086, 1.290] | 0.033 |
| long | 1.105 [0.851, 1.528] | 0.972 [0.714, 1.334] | -0.056 |
Per-variant rows are in report.json. Pre-match slope above 1 means the served model is under-confident; first innings "+ xi" is 1.121 [1.058, 1.210].

## Latest season (2025-11-25 to 2026-11-25), trained on everything before
| Head | Rows (matches) | Baseline | As-of team | All groups | Delta all vs baseline [95% CI] | ECE |
|---|---|---|---|---|---|---|
| chase | 70,468 (2,477) | 0.3943 | 0.3838 | 0.3666 | -0.0277 [-0.0364, -0.0187] | 0.0085 |
| first | 81,445 (2,477) | 0.6011 | 0.5655 | 0.5417 | -0.0594 [-0.0698, -0.0477] | 0.0158 |
| prematch | 2,648 (2,648) | 0.6747 | 0.6268 | 0.6121 | -0.0626 [-0.0739, -0.0504] | 0.0304 |
| long | 10,624 (37) | 0.7627 | 0.7360 | 0.7438 | -0.0190 [-0.1290, 0.0851] | 0.0664 |

## Left
- Long figure gap: trainer-like 0.6596 against the lab's long all-groups 0.6733 is 0.0137, outside the 0.005 aim. Not investigated. The lab's long rows differ from the trainer's (row caps and selection) and the lab's figure is a different feature set, so the two are not the same experiment yet.
- `report.a1.long.labCoreOnly` holds the trainer-like figure (set to match the a1 shape); it is not the lab's wide-matrix core.
- Gate verdicts per variant (0.001 gain, interval excluding 0, ECE) not tabulated here; the data is in report.json.
- No long as-served row; calibration uses the lab's linear fits only (GBT rows, if any, not recalibrated).
