---
paths:
  - "appwrite/functions/win-probability/**"
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

**Win probability is v6: every team, ground and player term is computed as of the match.** v5 knew
a team by its win rate over the whole training window — which includes the match being trained on,
so the term read its own result, the model leaned on it, and the calibration temperature had to
undo it (1.87 pre-match). `asOfState.js` walks the archive once in date order and reads each
match's context BEFORE folding the match in: Elo, form and head-to-head per side; a ground's
scoring, chase record and how often each side has played there (the only "home" signal Cricsheet
has); and each eleven's batting and bowling against the format's mean. Holdout (2024-12-27 on),
log-loss as served: **chase 0.3949 → 0.3729, first innings 0.6110 → 0.5654, pre-match
0.6775 → 0.6274**. The Test head is v5's, unchanged.

What was measured and left out, so nobody re-adds it on intuition (all on top of team + ground +
elevens, chase head, same holdout): the toss 0.0004, the current partnership 0.0008, the last five
overs 0.0006 — both against the innings' own rate and against the rate the chase still requires —
and the crease pair plus the remaining order 0.0012, which needs a scorecard a non-viewed match
never fetches. Boosted trees on the same columns: 0.3672 against the logistic model's 0.3667, with
worse calibration, and worse outright on first innings and Tests. For Tests, over four rolling
folds no group cleared its own noise (ground came closest: −0.025, interval [−0.053, +0.005]), and
**weather did nothing even with hindsight** — the weather that actually happened, with rain crossed
against time left, scored 0.7330 against 0.7208 without it. Limited-overs weather was then tested the way
it would be served — trained on the weather that happened (7,853 matches covered, a shuffled spread
across every season), holdout scored on what was FORECAST (`--weather-holdout`) — and it made both
heads worse: chase 0.3873 against 0.3830, first innings 0.5902 against 0.5806 with calibration
error up from 0.012 to 0.040. **Weather is not a model feature in any format.** The forecast is
still fetched for the Info tab and the preview, where it is shown as a forecast. Do not finish the
backfill (`appwrite/tools/weather-backfill.mjs`) expecting a different answer; the remaining
matches are more of the same seasons. `appwrite/tools/winprob-lab.mjs`
is the harness; every comparison is a column subset of one wide matrix, so two configurations
never differ by their sample. **The gate is ≥0.001 log-loss with a match-bootstrap interval that
excludes zero** — rows within a match are near-duplicates, so a row-level interval is meaningless.

**Each head ships two models, `core` and `full`, and the reader picks.** `full` also reads both
elevens and is used only when the context carries one for each side. A single model taught to
tolerate a missing eleven measured worse in BOTH cases than one fitted for each (0.3911 masked
against 0.3845 for a model that never had the group). Elevens are thinned in training at the miss
rates measured on 637 players of live team sheets (`JOIN_FAILURE` in `build.js`), because **a
Cricbuzz player joins to a Cricsheet person by name only** — the register's `key_cricbuzz` column
fills 50 of 18,554 rows. `playerjoin.js` joins inside one side's recent roster by surname and
initials (reading initials off the name as written, so "MS Dhoni" is not a man called "Ms"), then
among everyone of that gender with that surname's first letter, and returns null when two people
fit. Seven of eleven joined is the floor for an eleven; below it the side has none.

**`contextmath.js` is the one implementation of those terms, vendored three times.** The trainer
computes them for history, match-sync and win-probability recompute them live. A shrinkage
constant or a vector order that differed would not break anything — the model would be handed a
number that means something else. `ContextMathVendorParityTest` byte-compares the copies, all
three declared as Gradle inputs. `feature-parity.test.js` still pins the BASE vectors;
`contextFeatures` removes the retired strength term from each and appends the context.

**The model is published by pointer, and a bad refit is not published.** The trainer writes the
tables, then `winprob-v6-<stamp>`, reads it back, and only then rewrites `winprob-manifest`; the
previous model stays in the bucket, so rolling back is rewriting one small file. `publishProblems`
refuses a refit whose as-served log-loss is 0.02 worse than the manifest's, whose ECE passes 0.04,
or that scores a side needing two runs off thirty balls below 0.9. match-sync follows the manifest
and re-checks hourly; it used to hold one load — or one FAILED load — for the life of its
container, so a trainer mid-rewrite could leave a container on the heuristic for hours.

**A live match's context is `mc-{matchId}`, built at most two a run, and it costs no database
read.** `context.js` is the arithmetic and is pure; `contextstore.js` does the fetching — the
team-sheet page (`squads.js`, a port of `MatchSquadsRsc`), up to four table shards
(`wpx-<hh>`: rosters, grounds, head-to-head, keyed by FNV hash so a match never downloads the
megabyte the whole table would be; `wps-<letter>` for a player new to his side), and an Open-Meteo
forecast refreshed hourly. Upcoming fixtures build on a separate budget of one a run: they are
scored before live matches, and on a shared budget they took both builds. A ground the archive
does not know is not a special case — it reads exactly as a ground's first matches did in
training. One known side against one unknown is NOT an edge: the team terms are used only when
both are known. The file carries the forecast **as a forecast**, with its fetch time; nothing may
say it is raining because of it, and the interruption that matters is Cricbuzz's own status line.

**The detail payload says what moved the number.** `winProbabilityFactors` is up to three of
`teams` / `ground` / `elevens`, each the probability as scored minus the probability with that
group neutral, oriented to team1 like the number itself (two outcomes, so the sign simply flips;
a Test has none). It is exact for the question "what would the model have said without this",
which is the only question the prediction card asks. Kept out of `live-winprob`: every list
downloads that file and no list shows factors.

**What was served is scored separately from what was trained, and the rivals are captured raw.**
The lab scores the model on Cricsheet; nothing scored the numbers match-sync published from live
Cricbuzz inputs, where the failures are a name that does not join or a side oriented wrongly. On
the run that ends a match, `writeEvalLog` (`match-sync/src/main.js`, shape in `evallog.js`) writes
`wpe-{matchId}`: the `p` stamps already on the over history, the pre-match figure (kept in the
`prematch-log` file by match and team id, rewritten only when it moves 0.02 — it was first kept on
the context, which `buildContext` rebuilds from scratch when the elevens arrive, so no finished
match had one), and the outcome from
`matchHeader.result.winningteamId` — a structured field, so the status line is never parsed for a
winner, and anything unresolved is `unknown` and unscored. `appwrite/tools/winprob-live-score.mjs`
reads them: accuracy, log-loss, Brier and calibration by phase, format and what the model had, with
intervals that resample matches. Tests are not covered (only two-outcome formats are stamped).
Both rivals publish a figure on some matches only: Cricbuzz's live page has a `winProbability` prop
and Cricinfo's live-scores page a `liveInningPredictions` per match, and on 2026-10-05 both were
empty on every match listed (24 and 23). Their populated shapes are therefore **unknown, and
nothing parsed them when this was written**: match-sync stores Cricbuzz's raw value once per over — in `wpr-{matchId}`
as each arrives, because a container does not outlive a match, then in the log's `cricbuzz` array
when the match ends (the info event it raised on first sight was removed once the shape was parsed);
`cricinfoStep` does the same for Cricinfo every five minutes into `wci-{cricinfoId}` (one page
covers every match; `hs-consumer-api` is 403), and `appwrite/tools/rival-winprob-watch.mjs` is the
same parser run from a laptop. The page answered the container on 2026-10-06 — 200, 614 KB, 29
matches — with a sample identical to the laptop's; a refusal is logged as `[CRICINFO] unavailable`
and retried hourly. Both shapes were first seen on India v West Indies that day: Cricbuzz
`{team1: {id, percent}, team2: {id, percent}, drawTiePercent, lastUpdatedOver}`, Cricinfo
`{inningNumber, oversUnique, score, winProbability}`. The report reads both (`cricbuzzForTeam1`, `cricinfoForTeam1` in the tool): Cricbuzz by team
id, and Cricinfo as the **batting side's** chance — an inference from one match, where it tracked
Cricbuzz's batting-side figure through both innings — joined to a log by both team names and the
start time, since Cricinfo has its own match ids. It compares only on points both published.

**A first innings that is over is scored as the chase it has become** (`chaseStart`, `main.js`).
All out with balls unused was scored as a side still batting ten wickets down, which the model
puts on its 0.03 floor (West Indies all out 171, a minute after 0.52 at 171/9), and a full innings
as no balls left, which is `undetermined` — so the app showed a cliff or nothing through every
innings break. The position is now the other side needing one more from the full allotment, with
the current rate set to the required one: the chase head is very sensitive to the current rate
early on (same chase of 172 with the real context: 0.50 after one dot ball, 0.37 after a single,
0.19 after a 15-run over), and a rate of zero reads as a side scoring nothing. That early-chase
sensitivity is the model's, not this rule's, and is unexamined.

**The win-probability history is the over history, stamped.** `live-winprob` and the detail
payload are snapshots, and for a long time nothing kept a series: `MatchDetailViewModel`
accumulated one on the device, so the swing chart began when the screen was opened and Key Moments
(which reads `keyEvent`, never set on that path) was always empty. `stampProbability`
(`match-sync/src/main.js`) now writes team1's probability as `p` on the over that has just been
completed, in the same `scorecard.overs` points the worm is drawn from — no extra write or read —
and `scorecard.probabilityTeamId` says whose probability it is. Only the newest over is stamped,
once: an over first seen late gets no figure rather than a later moment's. `mergeOverHistory` keeps
a stamp when a fresh page reading of the same over arrives without one. Tests are not stamped
(three outcomes). `probabilityHistoryFrom` (`viewmodels/ProbabilityHistory.kt`) reads the stamps in
the screen's team order, names an over's wicket only when the over before it is present, and
returns nothing when the team id is absent — an unknown orientation is not a guess between two.
The session series remains the fallback and supplies the live end point. Before any of this the
series came from `RetrofitClient.localApi` — `http://10.0.2.2:8000/`, an emulator-only dev mock —
so on a real device it was a guaranteed CLEARTEXT failure per match open; that client is deleted,
do not reintroduce a localhost API.

**A Test is three outcomes, so the complement of a win is not the other side's win.** `evaluateLong`
returns a normalised (loss, draw, win) triple, and re-orienting it to team1 with `1 - batting` — as
`winProbFor` did — hands the whole draw to the bowling side. Match 170667 published
`winProbabilityTeam1 = 0.80` against a `drawProbability` of 0.41: a sum of 1.21, leaving the loss
negative, which the bar clamped away rather than surfaced. The rule now lives in one place,
`orientToTeam1` in `match-sync/src/main.js`, and subtracts the draw; the draw itself belongs to
neither side and is never re-oriented. The device half
(`MatchLiveDetailAlignment.alignedTo`) already did this correctly. The integration test carried its
own copy of the orientation rule, which is why the suite stayed green through the bug — it now
imports the exported one. **A mirrored rule is not a pinned rule.**
