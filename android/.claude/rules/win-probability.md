---
paths:
  - "appwrite/functions/train-win-probability/**"
  - "appwrite/functions/match-sync/src/winprob.js"
  - "appwrite/functions/match-sync/src/context*.js"
  - "appwrite/functions/match-sync/src/evallog.js"
  - "appwrite/functions/match-sync/src/playerjoin.js"
  - "appwrite/tools/winprob*"
  - "appwrite/tools/lab/**"
  - "app/src/main/kotlin/**/viewmodels/ProbabilityHistory.kt"
  - "app/src/main/kotlin/**/viewmodels/MatchLiveDetailAlignment.kt"
---

# Win Probability

## The v6 model

**Every team, ground and player term is computed as of the match.** v5 knew a team by its win rate over the whole training window, which includes the match being trained on, so the calibration temperature had to undo it (1.87 pre-match). `asOfState.js` walks the archive once in date order and reads each match's context BEFORE folding the match in: Elo, form and head-to-head per side; a ground's scoring, chase record and how often each side has played there (the only "home" signal Cricsheet has); each eleven's batting and bowling against the format's mean. Holdout (2024-12-27 on), log-loss as served: **chase 0.3949 → 0.3729, first innings 0.6110 → 0.5654, pre-match 0.6775 → 0.6274**. The Test head is v5's, unchanged.

## v7 (in code on `winprob-v7`; live only after a refit and a match-sync deploy)

Each change passed the lab gate (Δ log-loss, 95% match-bootstrap CI). Reports: `.claude/reports/E*.md`.

- **Rolling par** (E2): the constant par rates are replaced by an as-of par per format × gender. Chase uses 365 days (−0.0117 [−0.0148, −0.0085]); first innings uses the last 300 matches (−0.0052). With fewer than 30 matches it falls back to the constant. The constant par caused the early-chase under-prediction. With rolling par, the early-chase bands match actual win rates within 0.03.
- **First innings** (E4): adds the context groups × balls-left fraction (`tvB`, −0.0061). Training drops DLS/rain-reduced matches (E6, −0.0038; the holdout keeps them).
- **Chase rows** are weighted 1/√(rows in the match) (E6, −0.0030).
- **Pre-match is symmetric** (B5, −0.0021). It has no bias term and only antisymmetric columns (`…Diff`, `…Advantage`); shared columns get weight 0. So p(A,B)+p(B,A)=1: `publishProblems` refuses a model off by more than 1e-9, and a serving test builds both sides through `sideContextFor`. The old bias encoded what Cricsheet `teams[0]` means, while serving uses Cricbuzz team1.
- **Elo is `mov120`** (E5, `asOfState.js`). K is 120 and the update is multiplied by ln(1+margin)·2.2/(lead·0.001+2.2), so a favourite's win moves Elo less. Gains: pre-match −0.0054, first innings −0.0020, chase neutral. match-sync has no Elo walk; it reads the shipped tables.
- **FC routes to the Test head** (E7/B3), with strength under the MDM key; it beats a draw-rate prior by 0.14–0.15. T10 stays undetermined.
- The trainer refits daily (B15), so serving's tables no longer lag the model.
- Combined local holdout (trainer cutoff, as served): chase 0.3802 → 0.3697, first innings 0.5617 → 0.5523, pre-match 0.6219 → 0.6146. Symmetry costs pre-match about +0.0005 on this single holdout, against mov120 alone.
- **Live ECE is sampling noise** (E0, 356 resolved matches). The observed ECE is at or below the p50 a perfectly calibrated model would show (0.036 against p50 0.055).

**Measured and left out in v7 — do not re-add on intuition:**

- E1: chase rate shrunk toward par (k48). It won overall but made the early chase worse in every band.
- E2: recency row weights failed both heads; era × rrr fell below rolling par.
- E3: an estimated V(balls, wickets) resource table (Δ ≤ 0.0001) and a two-stage first innings. The hand-set `.55+.45wf` and `.3+.7wf` resource terms stay.
- E4: time-varying terms on the chase head.
- E8: recalibration (per-phase temperature, Platt).
- E5: inactivity regression and shrinkage toward tier means. kByGames was not tested on top of mov120.
- E6: chase drop-DLS (+0.0015), 1/n weights (ECE +0.0081), and first-innings match weights. T10: no Cricsheet rows.
- E7: Test +declared (−0.0048, CI includes 0), +oversLeft, +fourth-innings-vs-par, and a two-stage head (worse). Re-test declared only with a real declaration flag.

**Measured and left out in v6 — do not re-add on intuition** (on top of team + ground + elevens, chase head, same holdout):

- Toss 0.0004; current partnership 0.0008; last five overs 0.0006 (against the innings' own rate and against the required rate); crease pair plus remaining order 0.0012, which needs a scorecard a non-viewed match never fetches.
- Boosted trees on the same columns: 0.3672 against the logistic model's 0.3667, worse calibration, and worse outright on first innings and Tests.
- Tests, four rolling folds: no group cleared its own noise (ground closest: −0.025, interval [−0.053, +0.005]). **Weather did nothing even with hindsight**: 0.7330 with the weather that actually happened (rain crossed against time left) against 0.7208 without.
- Limited-overs weather, tested as served (trained on what happened, 7,853 matches covered; holdout scored on what was FORECAST, `--weather-holdout`): chase 0.3873 against 0.3830, first innings 0.5902 against 0.5806, calibration error 0.012 → 0.040.

**Weather is not a model feature in any format.** The forecast is still fetched for the Info tab and the preview, shown as a forecast. Do not finish `appwrite/tools/weather-backfill.mjs` expecting a different answer.

`appwrite/tools/winprob-lab.mjs` is the harness; every comparison is a column subset of one wide matrix, so two configurations never differ by sample. **The gate is ≥0.001 log-loss with a match-bootstrap interval that excludes zero** (rows within a match are near-duplicates, so a row-level interval is meaningless).

**Each head ships `core` and `full`, and the reader picks.** `full` also reads both elevens and is used only when the context carries one for each side. A single model tolerating a missing eleven measured worse in BOTH cases (0.3911 masked against 0.3845). Elevens are thinned in training at the miss rates measured on 637 players of live team sheets (`JOIN_FAILURE` in `build.js`), because **a Cricbuzz player joins to a Cricsheet person by name only** (`key_cricbuzz` fills 50 of 18,554 register rows). `playerjoin.js` joins inside one side's recent roster by surname and initials (read off the name as written, so "MS Dhoni" is not a man called "Ms"), then among everyone of that gender with that surname's first letter, and returns null when two people fit. Seven of eleven joined is the floor for an eleven.

**`contextmath.js` is the one implementation of those terms, vendored twice** (trainer, match-sync; a third copy went with the `win-probability` function in October 2026). A differing shrinkage constant or vector order would break nothing visibly — the model would be handed a number that means something else. `ContextMathVendorParityTest` byte-compares the copies, all declared as Gradle inputs. `feature-parity.test.js` pins the BASE vectors; `contextFeatures` removes the retired strength term from each and appends the context.

**Published by pointer; a bad refit is not published.** The trainer writes the tables, then `winprob-v6-<stamp>`, reads it back, and only then rewrites `winprob-manifest`; the previous model stays, so rollback is rewriting one small file. `publishProblems` refuses a refit whose as-served log-loss is 0.02 worse than the manifest's, whose ECE passes 0.04, or that scores a side needing two runs off thirty balls below 0.9. match-sync follows the manifest and re-checks hourly (it used to hold one load, or one FAILED load, for the container's life).

## Serving

**A live match's context is `mc-{matchId}`, built at most two a run, costing no database read.** `context.js` is pure arithmetic; `contextstore.js` fetches — the team-sheet page (`squads.js`, a port of `MatchSquadsRsc`), up to four table shards (`wpx-<hh>`: rosters, grounds, head-to-head, keyed by FNV hash so a match never downloads the megabyte the whole table would be; `wps-<letter>` for a player new to his side), and an Open-Meteo forecast refreshed hourly. Upcoming fixtures build on a separate budget of one a run (on a shared budget they took both builds). An unknown ground is not a special case: it reads as a ground's first matches did in training. One known side against one unknown is NOT an edge: team terms are used only when both are known. The file carries the forecast **as a forecast**, with its fetch time; nothing may say it is raining because of it — the interruption that matters is Cricbuzz's own status line.

**`winProbabilityFactors`** on the detail payload is up to three of `teams` / `ground` / `elevens`, each the probability as scored minus the probability with that group neutral, oriented to team1 (a Test has none). Kept out of `live-winprob`: every list downloads that file and no list shows factors.

**A first innings that is over is scored as the chase it has become** (`chaseStart`, `main.js`). All out with balls unused was scored as a side still batting ten down, which the model puts on its 0.03 floor (West Indies all out 171, a minute after 0.52 at 171/9); a full innings was `undetermined`. The position is now the other side needing one more from the full allotment, with the current rate set to the required one, because the chase head is very sensitive to the current rate early (chase of 172: 0.50 after one dot ball, 0.37 after a single, 0.19 after a 15-run over) and zero reads as a side scoring nothing. E1/E2 traced most of that early-chase sensitivity to the constant par; v7's rolling par fixes the bias.

**Orientation.** The payload and `live-winprob` are oriented to the first-innings side and aligned on the device by team id (`viewmodels/MatchLiveDetailAlignment.kt`); never by name.

**A Test is three outcomes.** `evaluateLong` returns a normalised (loss, draw, win) triple; re-orienting with `1 - batting`, as `winProbFor` did, hands the whole draw to the bowling side (match 170667: `winProbabilityTeam1 = 0.80` against `drawProbability` 0.41, a sum of 1.21, clamped away by the bar). The rule lives in one place, `orientToTeam1` in `match-sync/src/main.js`, and subtracts the draw; the draw belongs to neither side and is never re-oriented. The device half (`MatchLiveDetailAlignment.alignedTo`) was already correct. The integration test carried its own copy of the rule, which is why the suite stayed green; it now imports the exported one. **A mirrored rule is not a pinned rule.**

**The history is the over history, stamped.** `stampProbability` (`match-sync/src/main.js`) writes team1's probability as `p` on the over just completed, in the same `scorecard.overs` points the worm is drawn from (no extra write or read); `scorecard.probabilityTeamId` says whose it is. Only the newest over is stamped, once: an over first seen late gets no figure. `mergeOverHistory` keeps a stamp when a fresh reading of the same over arrives without one. Tests are not stamped. `probabilityHistoryFrom` (`viewmodels/ProbabilityHistory.kt`) reads the stamps in the screen's team order, names an over's wicket only when the over before it is present, and returns nothing when the team id is absent. The session series accumulated on the device remains the fallback and supplies the live end point. Before this the series came from `RetrofitClient.localApi` (`http://10.0.2.2:8000/`, an emulator-only mock), a guaranteed CLEARTEXT failure on a real device; that client is deleted — do not reintroduce a localhost API.

## Scoring what was served

The lab scores the model on Cricsheet; live failures are a name that does not join or a side oriented wrongly. On the run that ends a match, `writeEvalLog` (`match-sync/src/main.js`, shape in `evallog.js`) writes `wpe-{matchId}`: the `p` stamps on the over history, the pre-match figure, and the outcome from `matchHeader.result.winningteamId` (structured, so the status line is never parsed; anything unresolved is `unknown` and unscored). The pre-match figure is kept in `prematch-log` by match and team id, rewritten only when it moves 0.02 — it was first kept on the context, which `buildContext` rebuilds from scratch when the elevens arrive, so no finished match had one. `appwrite/tools/winprob-live-score.mjs` reads the logs: accuracy, log-loss, Brier and calibration by phase, format and what the model had, with intervals that resample matches. Tests are not covered.

**The finalising run fires on the status line, before Cricbuzz posts the result.** `matchHeader.result` is still `{}` for some minutes after "Bahrain won by 8 wkts". 26 of the first 72 logs were written in that gap and left `unknown`; the page carried `winningteamId` a day later. A log written without a result (`awaitsResult`, `evallog.js`) is now kept as `<matchId>:wpe` in `slow-sync-state`, and `resolveEvalLogs` (`main.js`) re-reads one page a run, oldest first, also on idle runs. It rewrites only the outcome fields and gives up after 3 days (`EVAL_RESOLVE_MAX_MS`). The archive copies the log 6 h after the match, so a resolution inside that window reaches it. The scorer's `--resolve` does the same in memory for older logs, by team id, never from the status line.

**The unknowns were not a random sample.** With them left out, live looked far worse than the lab (chase 0.525, a team1 lean of 0.52 said against 0.42 won). Resolved, on 74 matches (2026-10-06..15), live matches the lab: chase 0.391, first innings 0.541, pre-match 0.618, ECE 0.062. Never read a live score with unknowns in it.

**Against the rivals, no verdict yet (2026-10-15).** Cricbuzz: 6 paired matches, ours worse by 0.039 [−0.108, +0.210]; Brier 0.195 against 0.193. Cricinfo: 7 matches, ours worse by 0.091 [−0.127, +0.354], but its ECE is 0.181 against our 0.141. The scorer estimates ~48 (Cricinfo) and ~136 (Cricbuzz) paired matches to settle it. Rivals publish on major internationals only, under one paired match a day. `wci-` files are pruned at 60 days and the archive does not copy them, so a Cricinfo verdict needs them kept longer.

**Rivals are captured raw.** Cricbuzz's live page has a `winProbability` prop and Cricinfo's live-scores page a `liveInningPredictions` per match; both publish on some matches only (2026-10-05: empty on all 24 and 23 listed).

- Cricbuzz: stored once per over in `wpr-{matchId}` as each arrives (a container does not outlive a match), then folded into the log's `cricbuzz` array when the match ends. Shape: `{team1: {id, percent}, team2: {id, percent}, drawTiePercent, lastUpdatedOver}`.
- Cricinfo: `cricinfoStep` samples every five minutes into `wci-{cricinfoId}` (one page covers every match; `hs-consumer-api` is 403); `appwrite/tools/rival-winprob-watch.mjs` is the same parser from a laptop. The page answered the container on 2026-10-06 (200, 614 KB, 29 matches); a refusal logs `[CRICINFO] unavailable` and retries hourly. Shape: `{inningNumber, oversUnique, score, winProbability}`.
- Both shapes were first seen on India v West Indies, 2026-10-06. The report reads both (`cricbuzzForTeam1`, `cricinfoForTeam1`): Cricbuzz by team id; Cricinfo as the **batting side's** chance — an inference from one match — joined to a log by both team names and start time. It compares only on points both published.
