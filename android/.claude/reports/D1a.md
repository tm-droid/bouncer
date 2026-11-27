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

## Test gap fixes (verifier on cf3d16f8)

- `match-sync/test/winprob.test.js`: the first-innings window test used a 24-wide tier, which `usable()` rejects, so every case fell to the old path. It now uses a 27-wide head with `*bf@tvB` names; it asserts the head is used, that rollingPar N moves the result (and follows N), and that D does not. Dead early return removed.
- `train-win-probability/test/feature-parity.test.js`: end-to-end parity of `limitedOversRows` (trainer) against serving's composition (`shippedRollingPar`, `withRollingPar`, `contextFeatures` with the tv flag from `hasTimeVaryingTerms`), chase and first innings, every ball, same order. Mutation check: swapping the trainer's windows (chase N, first innings D) failed it; reverted.
- Same file: `rollingParTable` (what `buildModel` stores as `model.rollingPar`) is keyed `format|gender` and carries finite N and D.
