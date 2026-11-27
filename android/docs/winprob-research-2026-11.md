# Win-probability research, 2026-11

Three deep research passes feeding the v7 ledger (`android/.claude/task-ledger-winprob.md`).

## 1. Published cricket models
(Intended path: /private/tmp/claude-503/-Users-tanushmalhotra--claude-spares-spare-79gEOe/533e2b3c-c7f5-4d20-923a-0bee41f2f032/scratchpad/research-cricket-models.md ; written here because plan mode was active.)

## Our model as read from code (train-win-probability/src/training.js, contextmath.js)
- Chase base vector: log((cur+.5)/(req+.5)), wickets/10, ballsLeft/budget, req/par, cur/par, wf*bf, runsNeeded/(ballsLeft*(.3+.7wf)), target/parTotal, (1-wf)(1-bf), rateGap, rateGap/(.2+bf), runsNeeded/parTotal, wf^2, isOdi, isHundred. First innings: runs/par, cur/par, wf, bf, wf*bf, projected (= runs + curRate*ballsLeft/6*(.55+.45wf)) /par, ...
- Context terms (as-of): eloDiff, formDiff, h2h, winRateDiff, venueScoring, venueChase, targetVsVenue, familiarity, XI bat/bowl vs format mean.
- Observed gaps: (a) par run rate is a CONSTANT per format {T20 8.3, ODI 5.6, HUNDRED 8.6, T10 10.5}; no gender, competition tier or era. (b) no recency weighting of training rows (grep found none). (c) context terms enter with FIXED coefficients (no interaction with balls left / wickets). (d) resource heuristics (0.55+0.45wf, 0.3+0.7wf) are hand-set, not an estimated V(b,w). (e) one global temperature per head.

## Published models compared
| Model | Inputs | Form | Team/player strength | Early-innings noise | Reported accuracy | Data |
|---|---|---|---|---|---|---|
| WASP (Brooker & Hogan, Univ. of Canterbury NZ, not Lincoln; owned by NV Play since 2018) | 1st inn: balls, wickets. 2nd inn: balls, wickets, runs to date, target | Dynamic programme: V(b,w)=expected extra runs from per-ball estimates r(b,w), p(b,w); 2nd inn win prob by working backwards | None publicly; known failure when batting order changes (retired hurt) and Sky dropped it after misses | State is only (b,w,runs): no current-rate term, so a single ball cannot swing it by rate | None published | Non-shortened ODI/T20 between top-8 nations since 2006 |
| ESPNcricinfo Forecaster (2019, IIT Madras + Gyan Data) | Team batting/bowling strength, batter-bowler matchup, phase-wise strike/economy rates | "Statistical and ML models"; internals unpublished | Yes, player-level phase rates | Unpublished | None published | Cricinfo ball-by-ball |
| CricViz WinViz | Test: last 500 Tests, simulates rest of match thousands of times; 2023 AI/ML revision with "what-if" conditioning. Hundred version ignores team strength | Simulation | Test version unpublished | Rolling 500-Test window = built-in era adjustment; criticised as lagging Bazball | None | CricViz |
| Cricbuzz win % | Not documented anywhere I could find | - | - | - | - | - |
| Opta/Stats Perform | Score, RRR, wickets, balls; batter, bowler, venue ratings | Next-ball feed-forward NN (six ratings per player per format, 0-1) inside a Monte-Carlo simulation | Yes, per player | Simulation averages | None published | Stats Perform feed |
| Bailey & Clarke 2006 (JSSM) | Pre-match: past performance, venue, opposition, experience, form; in-play: D/L resources converted to runs | Linear regression on margin of victory + normal approximation, P=Phi(MOV/SE) | Covariates | D/L resources, over-by-over update | Holdout 100 ODIs: 71% winners, margin AAE 55.8 runs, R2 23% (MOV), 19% (1st-inn total) | ODI 2002-05; also compared to Betfair in-play |
| Asif & McHale 2016 (IJF) | ODI second-innings state + team strength/form covariates | Dynamic logistic regression: coefficients change smoothly over the innings, cross-validated variable selection | Pre-match strength/form covariates (secondhand) | Coefficient smoothing across overs | Secondary source says ~82% over-by-over accuracy in 2nd inn (unverified) | ODI, Salford PhD 2013 |
| Asif & McHale 2019 (IJF) | Overs left, wickets lost | Generalised non-linear model of runs remaining; D/L and McHale-Asif 2016 are special cases | None | - | - | T20I |
| Bayesian dynamic LR for IPL (Springer 2024 chapter, builds on Asif-McHale) | IPL ball-by-ball | Bayesian, power priors from history, Gaussian-process smoothing of coefficients | - | GP smoothing | Better Brier and accuracy than Asif-McHale in CV (numbers paywalled) | IPL |
| Swartz/Gill/Muthukumarana 2009; Davis/Perera/Swartz 2015 | Ball outcome probs by batter, bowler, overs used, wickets | Multinomial ball outcomes; hierarchical empirical Bayes; Monte Carlo | Per-player | Hierarchical shrinkage | Goodness of fit only | ODI/T20 |
| Bhattacharya/Gill/Swartz 2011 | T20 scoring by overs/wickets | Non-parametric (isotonic-style Gibbs) resource table for T20 | - | monotone constraint | - | T20I |
| Akhtar & Scarf 2012 (IJF), Test | Pre-match strength, ground, home, toss; in-play lead, overs, run rate, wickets | Multinomial logit per session | Pre-match terms matter early, lead dominates late | Separate model per session | - | Tests |
| Brooks, Faff & Sokulsky 2002 | Batting avg, bowling avg, strike rates | Ordered response (win/draw/loss) | Team averages | - | 71% outcomes right, draws worst (57%) | Tests 1994-99 |
| Agrawal & Kandhway 2023 (JSA) | Dynamic state + team strength, toss, home | Supervised ML per state, SHAP | Team strength | - | not verified | 1,359 ODIs 2004-22 |
| arXiv 2608.14696 (Aug 2026) | IPL chases: balls, wickets, runs required | Exact Markov chain / backward induction, plus LR and XGBoost comparators | none | Markov WP over-confident because scoring persists 3-5 balls | Held-out 164 matches/18,010 balls: logistic on RRR alone log-loss 0.4379; engineered LR 0.4157; XGBoost on state 0.4374, XGBoost full features 0.4607; Markov 0.5313, +era weighting 0.4741 | IPL 2008-2026 |

## Facts that matter for us
- Gradient boosting not beating logistic is consistent with 2608.14696 (XGBoost with more features was WORSE: 0.4607 vs LR 0.4157). The state-to-probability map is saturated; gains must come from new information, lower-variance labels or drift correction.
- Same paper: a set-batter proxy (balls faced) moved log-loss by a null amount; persistence in scoring is short range (excess decays by lag 3-5, gone by 10-20), wickets anti-cluster. Consistent with our null for partnership and last-5-overs.
- Same paper: weighting recent seasons to correct scoring-level drift cut Markov log-loss 0.5313 to 0.4741; mean runs/ball in the newest training season 1.424 vs 1.598 in the held-out season (drift continued past the training window).
- CricViz uses a rolling window of the last 500 Tests; WASP restricted to a fixed top-8 modern database. Both are era adjusters we lack.
- ODI toss effects: McEwan 2023 (SA J Sports Med) found toss outcome/decision and match type mostly not significant: matches our null.
- DLS-Stern: G50=245 for ICC full-member games (200 for lower tiers); parameters Z0(w), b(w) per wicket are proprietary, so any resource feature must be re-estimated from Cricsheet; Bhattacharya et al. and Perera & Swartz show how for T20.

## Untried ideas, ranked (expected log-loss gain x cost). Gains are my estimates, not published figures.
1. As-of rolling par/scoring level by (format, gender, competition tier) replacing constant PAR_RUN_RATE, plus exponential recency weights on training rows. Est. 0.002-0.008 chase and first innings; cost low (asOfState.js already walks the archive). Source: arXiv 2608.14696 (era), CricViz 500-Test window. Check first: holdout (post 2024-12-27) mean scoring vs training mean.
2. Time-varying coefficients: interact eloDiff/form/h2h/venue/XI terms with ballsRemainingFraction (and wickets in hand). Est. 0.001-0.004; cost low (~8-10 columns). Source: Asif & McHale 2016, Akhtar & Scarf 2012, Bayesian GP version (Springer 2024).
3. Empirical-Bayes shrunk current run rate: (runs + k*expected_rate*)/(balls + k) with k about 24-48 balls, expected rate from ground/format/team; addresses the documented early-chase sensitivity (0.50 -> 0.37 after one single). Est. 0.001-0.003, mostly first overs; cost low. Source: WASP has no rate term; Swartz T20 simulator hierarchical EB; 2608.14696 persistence 3-5 balls.
4. Estimated resource table V(b,w) per format/era (DLS/WASP style, isotonic in b and w) giving expected-remaining-runs and a z-score (runsNeeded - V)/SD(b,w), replacing 0.55+0.45wf and 0.3+0.7wf. Est. first innings 0.003-0.01, chase 0.001-0.003; cost medium. Source: Duckworth-Lewis, WASP, Bhattacharya/Gill/Swartz 2011, Asif & McHale 2019, Bailey & Clarke (MOV/SE).
5. Continuous-margin strength: Elo with margin-scaled K (runs for bat-first wins; wickets+balls left for chases, DLS-resource-based) and resource-adjusted margin ratings; feed as eloDiff variants. Est. 0.002-0.006 pre-match; cost low-medium. Source: Bailey & Clarke/Bailey 2005 MOV, ICC rating margin method, Glicko-with-margin for Tests (arXiv 2603.02574, preprint).
6. Two-stage first-innings head: regress final total (or resource-adjusted total) on state+strength, then map via residual SD to win prob; lower-variance label than binary result. Est. 0.003-0.01 first innings (speculative); cost medium. Source: Bailey & Clarke, WASP 1st-inn predicted score, Asif & McHale 2019.
7. Phase-specific, opposition-adjusted player ratings (powerplay/middle/death batter and bowler; ball-outcome-level shrinkage), gender flag. Est. 0.001-0.004 on full tier; cost high; JOIN_FAILURE name-join limits benefit. Source: Opta six ratings, Swartz/Davis/Perera, Lemmer opposition-adjusted measures, 2604.13861 (James-Stein per phase).
8. Per-phase temperature or isotonic-on-logit calibration. Est. 0.0005-0.002 (below your 0.001 gate unless drift); cost low. Source: 2608.14696 (symmetric miscalibration from persistence).
9. Test head: ordered-response structure (fewer parameters) and strike-rate/bowling-strike-rate terms; as-of Elo. Low confidence; you measured no group clearing noise on Tests. Source: Brooks/Faff/Sokulsky 2002, Akhtar & Scarf 2012.
10. Short-window momentum (last 12-18 balls). Est. under 0.001; your last-5-overs null suggests skip.

## Do NOT retry (supported by literature)
Boosted trees, balls-faced/set-batter proxy, toss, partnership, long-window form, weather: 2608.14696, McEwan 2023, your own measurements.

## Not verified
Asif-McHale 2016 covariate list and Brier (paywalled; Salford 403); Liu 2021 PDF text (not extractable here); Cricbuzz method (undocumented); Forecaster internals; any published betting-market log-loss for cricket (none found); log-loss figures for any commercial model (none published).

## Sources
- WASP: https://en.wikipedia.org/wiki/WASP_(cricket_calculation_tool) ; https://www.nzc.nz/news-items/archive/whats-wasp-all-about/
- Bailey & Clarke 2006: https://pmc.ncbi.nlm.nih.gov/articles/PMC3861745
- Asif & McHale 2016: https://ideas.repec.org/a/eee/intfor/v32y2016i1p34-43.html ; 2019: https://econpapers.repec.org/RePEc:eee:intfor:v:35:y:2019:i:2:p:634-640
- Bayesian dynamic LR IPL: https://link.springer.com/chapter/10.1007/978-3-031-67871-4_11
- Swartz/Davis/Perera T20 simulator: https://www.sfu.ca/~tswartz/papers/t20sim.pdf ; Bhattacharya et al.: https://www.sfu.ca/~tswartz/papers/twenty20.pdf
- Akhtar & Scarf 2012: https://ideas.repec.org/a/eee/intfor/v28y2012i3p632-643.html ; Brooks/Faff/Sokulsky: https://ideas.repec.org/a/taf/applec/v34y2002i18p2353-2365.html
- Calibration-leverage (2026): https://arxiv.org/html/2608.14696
- Agrawal & Kandhway: https://content.iospress.com/articles/journal-of-sports-analytics/jsa220735
- Forecaster: https://www.espncricinfo.com/ci/content/story/1178276.html ; https://www.iitm.ac.in/ta/node/8427
- WinViz: https://uk.sports.yahoo.com/news/bazball-battering-winviz-why-hate-070033911.html
- Opta: https://theanalyst.com/articles/opta-next-ball-predictor
- DLS: https://images.icc-cricket.com/image/upload/prd/orlbya4cqyhqaceje3b2.pdf
- Liu 2021 Elo ODI: https://wsb.wharton.upenn.edu/wp-content/uploads/2022/05/Liu_2021_WSABI_cricket.pdf ; Glicko Tests: https://arxiv.org/abs/2603.02574
- Toss: https://pmc.ncbi.nlm.nih.gov/articles/PMC10805135/
Searched: ~26 web searches, ~9 sources read in depth (rest snippet-level).

## 2. In-play methodology across sports
(Plan mode blocked writing to the requested scratchpad path; this plan file is the only permitted write target. Copy to
/private/tmp/claude-503/-Users-tanushmalhotra--claude-spares-spare-79gEOe/533e2b3c-c7f5-4d20-923a-0bee41f2f032/scratchpad/research-methodology.md when out of plan mode.)

## Headline
Your current design (logistic heads, as-of Elo/form, temperature, match-bootstrap, 0.001 gate) already matches best practice. The three
biggest unexplored levers are: (a) your live ECE of 0.062 on 74 matches is probably inside the noise of a perfectly calibrated model, (b) serve-time
skew and era drift are likelier causes of any real live gap than model class, (c) the pre-match prior should be blended with an explicit
decay in remaining resources (diffusion theory says prior weight on the logit ~ sqrt(remaining fraction)).

## 1. What transfers from NFL / NBA / soccer / tennis
- nflfastR WP: xgboost binary:logistic, hyperparameters tuned on game-grouped CV folds (never split plays of one game across folds), last seasons held out,
  monotone constraints on score diff, diff/time ratio, timeouts, spread-time etc. Baldwin tested a monotone constraint on spread_time: log loss 0.44787 -> 0.44826,
  i.e. constraints bought sanity, not accuracy. [high] (Baldwin 2021; Brill et al. 2023 list the constrained features)
- Spread-adjusted model injects the pre-game prior as a time-decayed feature (spread_time = spread * exp(-4 * elapsed_share) in the nflfastR helper source;
  a later dev version uses a log form, so exact formula is version dependent) and a lead/time ratio feature that grows as time runs out. [med]
  Transfer: engineer the prior and lead features with explicit time decay instead of hoping a flat logit weight fits all phases.
- NBA: Burke/Torvik partition the game into fixed time slices with one logistic regression each (survey in Elhabr/inpredictable pages); smoothing the slice
  coefficients (LOESS) is the standard fix for jitter. [med - secondary]. Asif & McHale (IJF 2016) is the cricket analogue: ODI logistic regression whose coefficients
  evolve smoothly over the match, far fewer parameters, stable forecasts. [high on abstract; covariates not verified]
- Soccer (Robberechts et al., KDD 2021): Elo-difference prior matters most early; contextual features matter after; by the last 10% of the game score diff
  dominates. Their Bayesian model was the only well calibrated one vs logistic / multinomial logistic / RF. Treating coefficients as a temporal process shares
  information across time slices. [high]
- Tennis 2026 (preprint): serve-shrink = live estimate weighted by n/(n+k) against the pre-match estimate (pseudo-count k); HGBM weak early, best of single models late;
  hybrid best at all checkpoints; evaluated at 25/50/75% match-progress checkpoints. [med]
- Cricket specific (Mishra, arXiv 2608.14696, single-author preprint, IPL chases, 164 test matches): logistic with engineered features beat XGBoost
  (log loss 0.4157 vs 0.4374 on RRR-only XGB and 0.4607 full-feature XGB; ECE 0.070 vs 0.077/0.085). Same finding as yours. Exact Markov models were too extreme
  both ways because of 3-5 ball scoring persistence (independence assumption), recoverable only partly (26%) by block bootstrap simulation. [med]

## 2. Monotonicity, smoothness, GAM vs logistic, interactions, time-varying coefficients
- Tools: xgboost/lightgbm/sklearn HistGB monotone constraints; pyGAM LogisticGAM with monotonic_inc/dec (slow without scikit-sparse); a monotone constraint on the
  linear predictor keeps the probability monotone because the link is increasing. [high]
- Evidence that unconstrained trees hurt: baseball Grid WAR paper notes XGBoost overfits tails even with constraints. Trees were less calibrated for you and for Mishra. [med]
- Practical recipe (my synthesis, not from a single source): keep logistic; replace linear RRR/wickets terms with monotone basis (I-spline or non-negative
  coefficients on a ramp basis); wickets in hand as ordered dummies with cumulative non-negative increments; interactions RRR x wickets, RRR x balls_remaining;
  varying-coefficient: each main effect times a low-df spline in balls_remaining with a roughness penalty (the Asif-McHale idea). Add a property test: grid scan
  asserts WP non-increasing in runs needed and non-decreasing in wickets in hand, and across successive balls with no event.
- Expect small log-loss gains from constraints (Baldwin saw none); the value is no embarrassing live outputs. [high]
- Dynamic-programming/DLS-style resource percentage as a single engineered feature is a cheap interaction proxy (not verified in a source).

## 3. Calibration
- Hierarchy (Van Calster 2019): mean, weak (intercept 0 / slope 1), moderate, strong. With ~74 live matches only mean and weak calibration are estimable;
  report calibration intercept and slope with match-clustered CIs instead of ECE. [high]
- Small-sample choice: isotonic overfits when data are scarce and can emit exact 0/1 -> infinite log loss (Niculescu-Mizil & Caruana 2005; Kull 2017; R vignette).
  Platt (2 params) and temperature (1 param) are the safe end. Beta calibration (3 params, contains identity, can be inverse-sigmoid) is the principled upgrade
  and a 2026 benchmark reports it improves log loss most often; Venn-Abers gives validity guarantees and an interval but most superiority claims come from its authors. [med]
- ECE bias: binned ECE is biased upward even for a perfectly calibrated model; equal-mass bins, debiased ECE or ECE_sweep are less biased (Roelofs et al. 2022). [high]
- Stable reliability diagrams: CORP (PAV isotonic, no bins) with consistency bands separates estimation noise from real miscalibration (Dimitriadis, Gneiting, Jordan, PNAS 2021). [high]
- MY SIMULATION (toy Stern diffusion, forecaster knows true drift, perfectly calibrated, 10 equal-width bins, ball-level rows, 74 clustered matches, 250 reps):
  mean ECE 0.045, 95th pct 0.075; at 300 matches 0.023 (p95 0.041); at 1000 matches 0.014. One row per match at 50% progress with 5 bins: 0.073 (p95 0.117) at 74 matches.
  => a live ECE of 0.062 on 74 matches is within the range of a perfect model; the lab ECE 0.01-0.04 was measured on far more matches. The toy ignores real
  forecast-spread differences, so rerun the simulation with your own forecast distribution before trusting the number. [med, own computation]
- Why live drifts from holdout (ordered by suspicion): (1) sampling noise + within-match clustering (above); (2) training-serving skew: as-of Elo/form/XI features computed
  differently or defaulted when missing at serve time (Nubank, Chalk, Arize all describe default-value mismatch; no source quantifies effect on calibration) [med];
  (3) era drift: IPL runs/ball 1.344 in training vs 1.598 in 2024-26; recency weights with a half-life chosen on rolling-origin CV improved a Markov model's log loss 0.531->0.474 [med, one paper];
  (4) persistence of scoring makes ball-level models over-confident; (5) selection of which matches go live (competition mix, format).
- Corrections: serve-log the exact feature vector and replay it offline (diff to the training pipeline); recency weights; shrunk Platt (slope prior centered at 1) with phase offsets
  rather than independent per-phase fits; online Platt scaling with calibeating (Gupta & Ramdas ICML 2023) adapts under drift with regret guarantees and extends to beta scaling. [high on paper; not tested on sports]
- Per-phase calibration: fit only if CORP bands per phase (powerplay/middle/death or balls-remaining quartiles) exclude the diagonal; otherwise one temperature.

## 4. Team and player strength
- 538 NFL Elo: margin multiplier ln(|MOV|+1) damped by an autocorrelation term that shrinks credit for favorites; 2/3 carry-over between seasons. [high]
- Cricket MOV: no standard. Bandyopadhyay & Mukherjee (arXiv 2603.02574, preprint, Test cricket) add MOV through innings run distributions, home/toss, Glicko RD; their calibrated logistic
  scale is far below chess's 400 (best near 80-90 in their table), and on WTC 2021-23 log loss was Elo 0.657, unmodified Glicko 0.689, theirs 0.582 (70 matches, scale tuned on a prior window). Low confidence on effect size;
  high confidence on the lesson: fit the logistic scale for cricket, do not inherit 400.
- Glicko vs Elo: no clean cricket head-to-head; soccer preprints claim modified Glicko beats Elo. Elo is a heuristic online approximation of Bradley-Terry; Glicko adds RD (uncertainty).
  Practical: use RD (or games-played) as an attenuation feature for sparse teams. [med]
- Hierarchical Bayesian Bradley-Terry beat MLE in MLB (Phelan & Whelan); Gaussian priors on log-strengths are the standard shrinkage; shrink associate and women's teams toward a (format, gender, tier) mean. No cricket-specific
  source for associates/women's found. [med/low]
- Prior-into-in-play blend:
  * Diffusion (Stern 1994): P = Phi((d + mu*tau) / (sigma*sqrt(tau))), tau = remaining time. Prior drift enters the probit argument as mu*sqrt(tau)/sigma, so the prior's weight decays ~ sqrt(remaining fraction),
    while the lead term is amplified by 1/sqrt(tau). [high on formula, from secondary summaries; mapping to cricket is my inference]
  * Test in logistic form: logit WP = f(state) + b * prior_logit * g(resources_remaining), with g monotone, initialized at sqrt(remaining fraction) or the DLS resource fraction, learned by a 1-D spline.
  * Bundesliga study: in-play implied probabilities are almost fully determined by the pre-match probabilities in the first minutes. [med]

## 5. Evaluation
- Proper scores: log and Brier are strictly proper (Gneiting & Raftery 2007); report both; log loss punishes tail errors, which matter for infinite-loss risk.
- Clustering: all balls of an innings share one label; per-ball resampling understates uncertainty; Brill et al. (JQAS 2025) show even a cluster bootstrap undercovers (data are "worth about half" the games)
  - treat your match-bootstrap interval as a lower bound on uncertainty. [high]
- NBA real-time evaluation (arXiv 2010.00781): calibration surfaces over (time x forecast) and pointwise-in-time confidence intervals of the loss difference between two methods; ESPN's complex model did not beat a simple logistic
  model except at the end. Transfer: plot paired delta-log-loss vs balls remaining with match-clustered bands, and test the integrated difference. [high]
- Rolling origin: leave-most-recent-season-out/LOSO (Horowitz, Ventura, Yurko), grouped folds by match (Baldwin). Needed because of era drift.
- Checkpoint scoring (tennis): evaluate at 25/50/75% progress => one row per match per checkpoint, no ball weighting issues.
- Market/rival comparison: de-vig odds (proportional or Shin; favourite-longshot bias exists), paired per-match delta log loss, match-level cluster bootstrap or DM-type test. Sample size: n ~ ((z_a+z_b) * sd_d / delta)^2 with z=1.96+0.84.
  Illustrative (assumed sd_d, measure your own): sd_d 0.05, delta 0.005 -> ~780 matches; sd_d 0.10, delta 0.005 -> ~3,100; delta 0.001 with sd_d 0.05 -> ~19,600. 74 matches can only detect gaps of roughly 0.02-0.03. No source gave an applied sports threshold. [formula high; numbers illustrative]
- Your 0.001 gate is fine for offline comparisons with thousands of matches; for live comparisons use it only as a non-inferiority/monitoring bound.

## 6. Row weighting
- Evidence is thin. No sports-WP source compares per-match weights. Statistical literature on informative cluster size: inverse-cluster-size weights (cluster-weighted GEE, Williamson 2003; within-cluster resampling, Hoffman 2001; Lee-Kim-Skinner Biometrika 2019) remove the
  over-representation of large clusters; matters when cluster size correlates with outcome or covariates. In cricket chases, innings length is informative (fast wins and collapses end early), so unweighted ball rows over-represent long innings. [med, inference]
- If the model is correctly specified, clustering inflates SEs but not the MLE; since it is misspecified, weights change the fitted compromise. Test: weight = 1/n_balls (or n^-0.5) per match, then recalibrate intercept, score on both ball-weighted and match-weighted
  (and checkpoint) log loss under rolling-origin CV. Also combine with recency weights; report effective sample size (Mishra required ESS >= 15%).
- Per-phase weights alter the intercept/calibration unless you recalibrate; use only if you care about one phase (e.g., death overs), and score that phase separately.

## Not verified / open
- Asif & McHale full text (covariates, scores) not accessible (403); Kull 2017 section on small data and the isotonic comparison not read in full; Venn-Abers independent benchmarks not found.
- nflfastR current spread_time formula (exp(-4*elapsed) vs log form) not confirmed at a tag; my direct fetch was blocked in plan mode.
- No source for associate-nation / women's cricket strength shrinkage, nor for per-match row weighting in WP models, nor an applied sample-size rule for model-vs-market log-loss tests.
- Mishra, Bandyopadhyay & Mukherjee, and the tennis paper are 2026 preprints, not peer reviewed.

## Sources (date)
- Baldwin, NFL win probability from scratch using xgboost in R (2021-04-13): https://opensourcefootball.netlify.app/posts/2021-04-13-creating-a-model-from-scratch-using-xgboost-in-r/
- Brill, Yurko, Wyner, Analytics have some humility (arXiv 2311.03490, v4 2024): https://arxiv.org/abs/2311.03490
- Brill, Yurko, Wyner, Difficulty of estimating win probability (arXiv 2406.16171; JQAS 2025): https://arxiv.org/abs/2406.16171
- nflfastR helper source: https://rdrr.io/cran/nflfastR/src/R/helper_add_ep_wp.R ; fastrmodels: https://github.com/mrcaseb/fastrmodels
- NBA real-time forecast evaluation (arXiv 2010.00781, 2020): https://arxiv.org/abs/2010.00781
- Robberechts, Van Haaren, Davis, Bayesian in-game WP soccer (arXiv 1906.05029; KDD 2021): https://arxiv.org/abs/1906.05029
- Asif & McHale, IJF 32(1) 2016: https://ideas.repec.org/a/eee/intfor/v32y2016i1p34-43.html
- Mishra, Calibration-Leverage Tradeoff (arXiv 2608.14696, 2026-07): https://arxiv.org/html/2608.14696
- Live tennis forecasting (arXiv 2609.07617, 2026): https://arxiv.org/html/2609.07617
- Stern 1994 Brownian motion model: https://www.stat.berkeley.edu/~aldous/157/Papers/stern.pdf
- Roelofs et al., Mitigating bias in calibration error estimation (arXiv 2012.08668; AISTATS 2022): https://arxiv.org/abs/2012.08668
- Dimitriadis, Gneiting, Jordan, Stable reliability diagrams (PNAS 2021): https://pmc.ncbi.nlm.nih.gov/articles/PMC7923594
- Van Calster et al., Calibration: the Achilles heel (BMC Med 2019): https://pmc.ncbi.nlm.nih.gov/articles/PMC6912996
- Kull et al., Beyond sigmoids, beta calibration (EJS 2017): https://projecteuclid.org/euclid.ejs/1513306867
- Niculescu-Mizil & Caruana (ICML 2005): https://mlanthology.org/icml/2005/niculescumizil2005icml-predicting
- Vovk et al., Venn-Abers / cross-VA (NeurIPS 2015): https://arxiv.org/abs/1511.00213
- Gupta & Ramdas, Online Platt scaling with calibeating (ICML 2023): https://arxiv.org/abs/2305.00070
- Bandyopadhyay & Mukherjee, Glicko for Test cricket (arXiv 2603.02574, 2026): https://arxiv.org/abs/2603.02574
- 538 NFL Elo methodology: https://fivethirtyeight.com/methodology/how-our-nfl-predictions-work
- Hierarchical Bayesian Bradley-Terry, MLB (arXiv 1712.05879): https://arxiv.org/abs/1712.05879
- Clustered-data weighting: Lee, Kim, Skinner Biometrika 2019: https://ideas.repec.org/a/oup/biomet/v106y2019i4p965-972..html ; arXiv 1803.01175
- Train-serve skew: https://building.nubank.com/dealing-with-train-serve-skew-in-real-time-ml-models-a-short-guide/ ; https://chalk.ai/blog/data-drift
- Bundesliga in-play vs pre-match prices (arXiv 2505.21275): https://arxiv.org/abs/2505.21275
- Market-calibrated in-play football AFT (arXiv 2605.16066, 2026): https://arxiv.org/abs/2605.16066

Searched: ~34 web searches, 10 sources read in depth (indexed and queried), plus 1 own simulation.

## 3. Weak spots (early chase, Tests, DLS, drift, sparse sides)

Ranked changes to test:
1. Ablate current run rate (WASP's chase model has none). arXiv 2608.14696: required-rate logistic 0.438 vs backward induction 0.531; engineered logistic 0.416.
2. Par-relative and shrunk rate: runs_above_par = score - Par(balls, wickets, ground, era); (runs + k*par)/(balls + k), k by CV (2608.14696, 2604.13861).
3. Era drift: recency weights, half-life by rolling-origin CV (IPL study: 0.75 seasons, Brier 0.164 -> 0.149); era x required rate; frequent refits. IPL 2026 powerplay 10.47 rpo vs 9.61 in 2025.
4. Rain-reduced matches: train without them (WASP, CricLeverage, 2608.14696); serve on revised target and reduced balls with an is_reduced flag; DLS-par gap only after validation (github abdulhaquemohammed/dls-fairness-t20, 149 games).
5. Tests: session-level multinomial baseline (Akhtar & Scarf 2012, IJF 28:632); two-stage decisive-then-winner; overs left net of weather, follow-on/declaration flags, fourth-innings target vs par. Draws ~13-15% of recent Tests.
6. Calibration by required-rate band with match-clustered bootstrap; ball rows autocorrelated (arXiv 2406.16171).
7. Women's/associates: pooled model with gender/format intercepts and ridge-shrunk interactions (inference; no published guidance).
8. Ties terminal at 0.5 or super-over winner; drop no-results; Hundred and T10 via ball-native features and format intercepts.

Sources: arxiv.org/html/2608.14696, arxiv.org/html/2604.13861, arxiv.org/abs/2406.16171, en.wikipedia.org/wiki/WASP_(cricket_calculation_tool), github.com/abdulhaquemohammed/dls-fairness-t20, huggingface.co/Sarthak213/doosra-win-probability, link.springer.com/article/10.1057/jors.2014.30, ideas.repec.org/a/eee/intfor/v28y2012i3p632-643.html, sfu.ca/~tswartz/papers/declare.pdf, resources.ecb.co.uk (DLS methodology 2026-03-24), espncricinfo.com/story/...-1320734.
