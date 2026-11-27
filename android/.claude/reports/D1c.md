# D1c: per-match chase weights and DLS-free first-innings training (E6)

## Changes (appwrite/functions/train-win-probability)
- src/training.js: `RowSink` tracks a match id per row (opt-in `trackMatch`); `matchRowWeights(sink, power)` (line ~707) gives each row n^-0.5 (n = rows the match has in the sink), scaled to mean 1; `trainBinary` takes `rowWeight`, applied to the gradient only (line ~850).
- src/build.js: `fitTier` (line ~269) passes the train-sink weights; `dropsDls` (line ~316) and `headSinks` (line ~320, extracted from `fitHead`) drop `match.method != null` from first-innings train and valid only. Only the chase train sink tracks match ids. Temperature fit, validation metric, holdout, `rest`, prematch and Test heads are unchanged and unweighted.
- test/e6-weights-dls.test.js (new): (a) chase per-match weight sums scale with sqrt(n) (ratios 2 and 5), mean 1, first-innings train sink carries no weights; (b) a DLS match gives first-innings train/valid 0 and holdout rows, and still gives chase train/valid/holdout rows. Each fails when its change is reverted (dropsDls forced false; weighted forced false), then restored.

## Checks
- `npm --prefix appwrite/functions/train-win-probability test`: 82 pass, 0 fail.
- `npm --prefix appwrite/functions/match-sync test`: fail 0.

## Holdout (2024-12-27 on), served log-loss, full all_json.zip (local cache copy), `buildModel` called directly, no publish
| head | before (D1a) | after |
|---|---|---|
| chase | 0.3698 | 0.3700 |
| first innings | 0.5558 | 0.5560 |
| prematch | 0.6219 | 0.6219 |

Both limited-overs heads are within +0.0002 of before (limit 0.002), so no gain on this holdout and no regression. The lab gain does not reproduce here.
