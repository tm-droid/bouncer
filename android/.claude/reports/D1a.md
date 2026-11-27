# D1a: rolling par (chase rpD, first innings rpN + tvB) in trainer and serving

Holdout (temporal, 2024-12-27 onward), log-loss, full Cricsheet all_json.zip, before = HEAD trainer, after = this change.

| head | raw before | raw after | served before | served after | served gain |
|---|---|---|---|---|---|
| chase | 0.3768 | 0.3661 | 0.3802 | 0.3698 | 0.0105 (need 0.006) |
| first innings | 0.5616 | 0.5557 | 0.5617 | 0.5558 | 0.0059 (need 0.005) |
| prematch | 0.6219 | 0.6219 | 0.6219 | 0.6219 | 0 |

`publishProblems(after, before)` returned `[]`. No gate changed.

Design: model ships `rollingPar["format|gender"] = {N, D}` (thin windows omitted, MODEL_VERSION unchanged). Serving reads it via `shippedRollingPar` and falls back to the constant par when absent, so old models serve unchanged. Time-varying terms (first innings only) are detected from `featureNames` (`*bf@tvB`). Shared math is in contextmath.js (vendored copies byte-identical).

Checks: `cmp` clean; train-win-probability 78/78, match-sync 498/498; Gradle `*ContextMathVendorParityTest*` BUILD SUCCESSFUL.
First innings gain (0.006) is below the lab's 0.010 prediction but above the 0.005 bar.
