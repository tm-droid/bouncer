# D1d: symmetric pre-match head (trainer + serving gate)

Change
- training.js: fitSymmetricSinks/fitSymmetricHead (mirrored rows on antisymmetric columns /Diff$|Advantage$/, uncentred standardizer, no bias, zero weight on symmetric columns).
- build.js: pre-match head fitted via fitTier({symmetric:true}); publishProblems refuses a pre-match head with |p(A,B)+p(B,A)-1| > 1e-9 (PREMATCH_SYMMETRY_TOLERANCE).
- match-sync/src/winprob.js and both contextmath.js: untouched.

Shape compatibility: shape-compatible. Same keys; bias 0, weight 0 on symmetric columns, featureMeans 0. Serving (scoreContextHead) is unchanged and serves both. The CURRENTLY PUBLISHED model is old-shape and asymmetric (symErr 0.0365 mean / 0.1732 max) and stays so until the next refit; the new gate would refuse it if refitted that way.

Tests (each shown failing on revert)
- context.test.js (a) fitted-symmetric head, sum = 1 within 1e-9: fails 2 tests when fitBias:false is dropped and bias returned from the fit. Dropping only the row mirroring does NOT fail it: the odd logit holds by construction (no bias, uncentred); mirroring is what keeps the fit unbiased and is covered by the holdout, not a unit assertion.
- context.test.js (b) publishProblems refuses asymmetric model: fails with the gate disabled.
- match-sync winprob-integration.test.js (c) served pre-match sum = 1: fails with a biased model (asserts sum off by > 0.01).
- Suites: train-win-probability ℹ tests 84 pass 84 fail 0; match-sync ℹ tests 501 pass 501 fail 0.

Holdout (served log-loss, head), before D1c -> after D1d
- chase 0.3700 -> 0.3700 (0.36996)
- first innings 0.5560 -> 0.5560 (0.55598)
- pre-match 0.6219 -> 0.6230 (+0.0011, within the 0.002 allowance; core 0.6246, full 0.6256)
