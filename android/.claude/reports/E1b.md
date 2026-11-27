# E1b: verify E1 and the 0.26 chase-start probe

Run 45967290525 (head b4533f41, `--cutoffs 2024-03-08,2025-01-27 --latest-season`, label e1b). Report: `report.json` `crr.earlyChase`.

## Q1 verdict: not a bug

- Units and format: `limitedVector` (winprob-lab.mjs:362) is the single builder for training rows and the probe. par is runs per over and k is in balls, the same in both (k variants at :406, par/gap columns :408-412). The probe passes `format: "T20"` and the gender/par for men's T20 (:1044-1060), so no wrong-format or wrong-gender par.
- The 0.474 baseline is the artifact. Only the probe sets `servingStart` (:369), so crr = required rate at 0 balls. Training never carries that: `inningsStates` yields a state after each delivery, so there is no 0-ball row except after a wide or no-ball before the first legal ball. The training crr is `runs*6/max(balls,1)` (:369), which is 0 after a dot first ball and 6*runs after a wide. At exactly 0 balls the holdout has 45 rows in 44 matches, all with crr = 6*runs of that delivery (the `crrMean` field is null because of a column-name lookup miss at :1072; the value follows from :369).
- The 0.26 of every variant is the models' general early under-prediction plus the probe's neutral context (zeroed state/xi/team/toss/weather, xiKnown = 0). The earlyChase table shows the same under-prediction on real holdout chases.

No fix was made, so Q3 (re-report the probe) needed no re-run; the probe below is from the run above.

## earlyChase (men's T20 holdout chases, legal balls bowled <= 6, mean of per-match mean p)

| target | matches | actual | baseline | a | k48 | par |
|---|---|---|---|---|---|---|
| <=140 | 510 | 0.837 | 0.797 | 0.771 | 0.759 | 0.765 |
| 141-160 | 280 | 0.650 | 0.583 | 0.543 | 0.537 | 0.545 |
| 161-180 | 309 | 0.450 | 0.405 | 0.360 | 0.366 | 0.367 |
| 181+ | 624 | 0.253 | 0.206 | 0.172 | 0.188 | 0.182 |

All four models under-predict the chase win rate early. The baseline is closest to actual in every band. k48 is -0.084 in 161-180 versus baseline -0.045.

## Probe (chase of 172, men's T20, neutral context)

| | 0 balls | 1 dot | 1 single | 15-run over |
|---|---|---|---|---|
| baseline | 0.474 | 0.317 | 0.455 | 0.628 |
| a | 0.255 | 0.245 | 0.257 | 0.380 |
| k12 | 0.261 | 0.243 | 0.259 | 0.396 |
| k24 | 0.258 | 0.243 | 0.257 | 0.393 |
| k48 | 0.263 | 0.252 | 0.264 | 0.390 |
| k96 | 0.262 | 0.252 | 0.263 | 0.383 |
| par | 0.259 | 0.248 | 0.260 | 0.396 |

## Recommendation

Hold: do not adopt k48 yet. Its log-loss gain (-0.0060) is real but its start-of-chase level is further from actual than the baseline's; adopt it only with an early-chase recalibration. Serving's `chaseStart` crr = required rate convention is a never-trained input, so a crr-free variant (a) or k48 removes the need for it, but the convention itself should not be changed for the baseline, whose 0.474 at 0 balls is closer to actual (0.450 in 161-180) than any variant.
