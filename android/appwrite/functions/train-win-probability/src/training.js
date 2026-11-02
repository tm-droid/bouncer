/**
 * Pure training-data construction and optimisation for the win-probability models.
 *
 * Split out of main.js so the row math, the temporal split and the optimiser are testable without
 * downloading the corpus or touching Appwrite.
 *
 * ## What v5 changed, and why each change is a correctness fix rather than a knob
 *
 * Measured on a temporal holdout of the 2024-11-02 onward window, 22,524 Cricsheet matches:
 *
 * | model                | v4     | v5     |
 * |----------------------|--------|--------|
 * | chase (limited-overs)| 0.4434 | 0.3949 |
 * | first innings        | 0.6801 | 0.6110 |
 * | Test (three-outcome) | 0.7237 | 0.5888 |
 *
 * The first-innings column compares against the *heuristic*, because v4 had no learned model for a
 * side setting a target at all — half of every limited-overs match was scored by hand-tuned
 * constants.
 *
 *  - **Wides and no-balls are no longer counted as balls.** `ballsFaced++` fired on every delivery,
 *    including the ones that do not advance the over. Every row therefore paired a true score with
 *    an overstated ball count, so `ballsRemaining`, the required rate and the innings fraction were
 *    all wrong, and wrong by more in a scrappy innings than a tidy one. Worth 0.024 log-loss on its
 *    own — the single largest fix here.
 *  - **Features describe the position, not just its ingredients.** A logistic model is linear in
 *    what it is given, and a chase is not linear in `crr/rrr`. Wickets-by-time interactions, the
 *    required rate relative to what the format sustains, and the size of the chase relative to a par
 *    total are all things the old five features could only approximate. Gradient-boosted trees over
 *    the same inputs scored 0.3963 against this set's 0.3949, which says the signal is in the
 *    features rather than in the function class — so this stays a linear model, and stays small
 *    enough to evaluate inside `winprob.js` without a tree walker.
 *  - **Adam with early stopping on a held-back validation window**, rather than 30 fixed epochs of
 *    plain SGD. The old loop could not detect that it had started overfitting because it never
 *    scored anything it had not trained on.
 *  - **Team win rates are shrunk toward 0.5 by a pseudo-count.** A side with three recorded matches
 *    used to read 1.000 and hand the model a strength difference of +0.5 on no evidence.
 *
 * v4's own widening — every format rather than T20 only, stats keyed by (team, gender, format), and
 * an innings budget that multiplies by `balls_per_over` so The Hundred's 100 balls are not scored as
 * 120 — is unchanged and still load-bearing. See `teamKey` and `inningsBudgetBalls`.
 */

export const MODEL_VERSION = 6;

/**
 * Par run rate per format: the rate a side sustains across a full innings with wickets intact.
 *
 * Every rate feature is expressed relative to this so one model can cover T20, ODI and The Hundred
 * without an 8.3-vs-5.6 difference swamping the position. Mirrored in `winprob.js` as
 * `FORMAT_PAR_RUN_RATE`; `feature-parity.test.js` pins the two together, because a mismatch here
 * silently rescales every feature at inference.
 */
export const PAR_RUN_RATE = { T20: 8.3, ODI: 5.6, HUNDRED: 8.6, T10: 10.5 };
export const DEFAULT_PAR_RUN_RATE = 8.0;

/** Par run rate for a Test. Only the long-format features use it. */
export const TEST_PAR_RUN_RATE = 3.2;

// ── feature sets ─────────────────────────────────────────────────────────────

export const CHASE_FEATURE_NAMES = [
  "logRateRatio",
  "wicketsInHand",
  "ballsRemainingFraction",
  "requiredRateOverPar",
  "currentRateOverPar",
  "teamStrengthDiff",
  "resource",
  "wicketAdjustedRequiredRate",
  "targetOverPar",
  "lateAndThin",
  "rateGapOverPar",
  "urgentRateGap",
  "runsNeededOverPar",
  "wicketsInHandSquared",
  "isOdi",
  "isHundred",
];

export const FIRST_INNINGS_FEATURE_NAMES = [
  "runsOverPar",
  "currentRateOverPar",
  "wicketsInHand",
  "ballsRemainingFraction",
  "resource",
  "projectedOverPar",
  "teamStrengthDiff",
  "lateAndThin",
  "wicketsInHandSquared",
  "isOdi",
  "isHundred",
];

/**
 * Pre-match features — everything knowable before a ball is bowled, which is not much.
 *
 * Deliberately only team strength and the format. There is no toss yet (so no batting order), no
 * position, and no in-match state; anything else would be invented. The point of this model is not
 * to be sharp — it cannot be — but to replace "nothing at all" with a stated, calibrated prior for
 * the fixtures where both sides are in the corpus.
 */
export const PREMATCH_FEATURE_NAMES = [
  "teamStrengthDiff",
  "isOdi",
  "isHundred",
  "isLongFormat",
];

export const LONG_FEATURE_NAMES = [
  "leadPerHundred",
  "wicketsInHand",
  "inningsProgress",
  "ballsRemainingFraction",
  "teamStrengthDiff",
  "isFourthInnings",
  "chasePressure",
  "followOn",
  "inningsUsedDiff",
  "inningsRateOverPar",
  "leadWeightedByTimeLeft",
  "wicketsToTakePerDay",
  "daysRemaining",
  "battingResource",
  "wicketAdjustedChasePressure",
  "isFiveDay",
];

/**
 * Chase features. The state object carries raw cricket quantities; everything derived lives here so
 * that `winprob.js` can reproduce the vector exactly from a live Cricbuzz miniscore.
 *
 * Every feature here is computable from the Cricbuzz miniscore alone. Since v6 this is the BASE
 * of the chase vector: `contextFeatures` (contextmath.js) drops `teamStrengthDiff` and appends
 * what is known about the two sides, the ground and the elevens, and `build.js` records what each
 * of those measured.
 *
 * An earlier note here said a venue par score (0.0008) and the current partnership (0.0025) were
 * left out because the absent case could not be validated and venue strings would not join. Both
 * were re-measured once every term was computed as of the match. The ground is in: on top of
 * as-of team terms it is worth 0.012, and an unknown ground is not a special case — it is what a
 * ground's first matches already look like in the corpus. The partnership is still out, now for
 * a plainer reason: beside the ground and the elevens it is worth 0.0008.
 */
export function chaseFeatures(s) {
  const budget = Math.max(s.ballsBudget, 1);
  const ballsLeft = Math.max(s.ballsRemaining, 1);
  const par = s.parRunRate;
  const parTotal = (par * budget) / 6;
  const bf = ballsLeft / budget;
  const wf = s.wicketsInHand / 10;
  const rateGap = (s.requiredRunRate - s.currentRunRate) / par;
  return [
    Math.log((s.currentRunRate + 0.5) / (s.requiredRunRate + 0.5)),
    wf,
    bf,
    s.requiredRunRate / par,
    s.currentRunRate / par,
    s.teamStrengthDiff,
    wf * bf,
    s.runsNeeded / (ballsLeft * (0.3 + 0.7 * wf)),
    s.target / parTotal,
    (1 - wf) * (1 - bf),
    rateGap,
    rateGap / (0.2 + bf),
    s.runsNeeded / parTotal,
    wf * wf,
    s.format === "ODI" ? 1 : 0,
    s.format === "HUNDRED" ? 1 : 0,
  ];
}

/**
 * First-innings features — the side setting a target.
 *
 * `projectedOverPar` is the only non-obvious one: a flat run-rate extrapolation says a side 60/0
 * off 6 and a side 60/6 off 6 finish in the same place. Weighting the remaining overs by wickets in
 * hand is what makes the projection mean anything, and it is why this beats the shipped heuristic
 * by 0.069 log-loss.
 */
export function firstInningsFeatures(s) {
  const budget = Math.max(s.ballsBudget, 1);
  const ballsLeft = Math.max(s.ballsRemaining, 0);
  const par = s.parRunRate;
  const parTotal = (par * budget) / 6;
  const bf = ballsLeft / budget;
  const wf = s.wicketsInHand / 10;
  const projected = s.runsScored + s.currentRunRate * (ballsLeft / 6) * (0.55 + 0.45 * wf);
  return [
    s.runsScored / parTotal,
    s.currentRunRate / par,
    wf,
    bf,
    wf * bf,
    projected / parTotal,
    s.teamStrengthDiff,
    (1 - wf) * (1 - bf),
    wf * wf,
    s.format === "ODI" ? 1 : 0,
    s.format === "HUNDRED" ? 1 : 0,
  ];
}

/**
 * Pre-match features, oriented to the FIRST NAMED side.
 *
 * "First named" means the first side in the fixture title, which is the same order the app lists
 * teams in, so the caller needs no second mapping. The strength difference is antisymmetric, so the
 * model is symmetric by construction: swapping the sides gives exactly one minus the answer.
 */
export function prematchFeatures(s) {
  return [
    s.teamStrengthDiff,
    s.format === "ODI" ? 1 : 0,
    s.format === "HUNDRED" ? 1 : 0,
    // Written out rather than routed through isLongFormat so the reader's copy can be
    // textually identical; a helper that exists on only one side is how the two drift.
    s.format === "TEST" || s.format === "MDM" ? 1 : 0,
  ];
}

/**
 * Long-format features, oriented to the side batting at that moment.
 *
 * `wicketsToTakePerDay` and `daysRemaining` are what let the model express a draw: a side nine down
 * with two days left is losing, the same side nine down with two overs left is drawing, and neither
 * `wicketsInHand` nor `ballsRemainingFraction` alone can separate those. Adding them and the other
 * six took holdout log-loss from 0.7237 to 0.5888.
 */
export function longFeatures(s) {
  const ballsLeft = Math.max(s.ballsRemaining, 1);
  const wf = s.wicketsInHand / 10;
  const bf = ballsLeft / Math.max(s.ballsTotal, 1);
  const isFourth = s.isFourthInnings ? 1 : 0;
  return [
    s.lead / 100,
    wf,
    (s.inningsIndex + 1) / 4,
    bf,
    s.teamStrengthDiff,
    isFourth,
    isFourth ? s.runsNeeded / ballsLeft : 0,
    s.followedOn ? 1 : 0,
    s.inningsUsedDiff,
    s.inningsRunRate / TEST_PAR_RUN_RATE,
    (s.lead / 100) / (0.15 + bf),
    s.wicketsToTake / Math.max(ballsLeft / 90, 0.05),
    ballsLeft / 540,
    wf * bf,
    isFourth ? s.runsNeeded / Math.max(ballsLeft * (0.3 + 0.7 * wf), 1) : 0,
    s.scheduledDays >= 5 ? 1 : 0,
  ];
}

// ── corpus classification ────────────────────────────────────────────────────

/** Three outcomes, because a Test can be drawn and a two-class model cannot say so. */
export const OUTCOME = Object.freeze({ LOSS: 0, DRAW: 1, WIN: 2 });
export const NUM_CLASSES = 3;

/** Default innings budget when a match declares none. Only limited-overs matches reach this. */
const DEFAULT_OVERS = 20;

/** Scheduled balls in a day of long-format cricket: 90 overs. */
const BALLS_PER_LONG_DAY = 90 * 6;

/** Cricsheet `match_type` to the class the models reason in. */
const MATCH_TYPE_CLASS = {
  T20: "T20",
  IT20: "T20",
  ODI: "ODI",
  ODM: "ODI",
  Test: "TEST",
  MDM: "MDM",
};

/** Format classes played over more than one day. */
const LONG_CLASSES = new Set(["TEST", "MDM"]);

/**
 * Format class for a Cricsheet match, or null when it is not one we model.
 *
 * The Hundred is not a distinct `match_type` — Cricsheet records it as T20 with
 * `balls_per_over: 5`, which is the only thing distinguishing a 100-ball innings from a 120-ball
 * one. Keying it separately keeps its win rates and its budget apart from real T20.
 */
export function formatClassOf(info) {
  const cls = MATCH_TYPE_CLASS[info?.match_type];
  if (!cls) return null;
  if (cls === "T20" && Number(info?.balls_per_over) === 5) return "HUNDRED";
  return cls;
}

/** Par run rate for a format class, falling back rather than throwing on an unmodelled one. */
export function parRunRateFor(formatClass) {
  return PAR_RUN_RATE[formatClass] ?? DEFAULT_PAR_RUN_RATE;
}

/**
 * The key team stats are stored under.
 *
 * Must be produced identically wherever stats are read, or names get validated against one map and
 * looked up in another. `winprob.js` carries the reader; this is the writer, and
 * `feature-parity.test.js` pins them together.
 */
export function teamKey(team, gender, formatClass) {
  return `${String(team ?? "").trim()}|${gender ?? "male"}|${formatClass ?? "T20"}`;
}

/**
 * Balls in one innings for a Cricsheet match.
 *
 * `info.overs` is the agreed overs per innings, which is how a rain-shortened game is represented,
 * and `info.balls_per_over` is 5 for The Hundred and 6 everywhere else. Both are needed: `overs * 6`
 * was right for T20 and ODI and wrong by 20 balls for every Hundred match.
 */
export function inningsBudgetBalls(info) {
  const overs = Number(info?.overs);
  const ballsPerOver = Number(info?.balls_per_over);
  const safeOvers = Number.isFinite(overs) && overs > 0 ? overs : DEFAULT_OVERS;
  const safeBalls = Number.isFinite(ballsPerOver) && ballsPerOver > 0 ? ballsPerOver : 6;
  return Math.round(safeOvers * safeBalls);
}

/** Scheduled balls in the whole match, from the number of days it was allotted. */
export function longMatchBalls(scheduledDays) {
  const days = Number(scheduledDays);
  return (Number.isFinite(days) && days > 0 ? days : 5) * BALLS_PER_LONG_DAY;
}

/** ISO date string for a match, or null. Cricsheet uses `info.dates: ["2024-06-01", ...]`. */
export function matchDate(info) {
  const first = info?.dates?.[0];
  return typeof first === "string" && first.length >= 10 ? first.slice(0, 10) : null;
}

/** Any match we can take descriptive team aggregates from — every format, both genders. */
export function isStatsCountable(info) {
  return formatClassOf(info) != null && (info?.teams?.length ?? 0) === 2;
}

/**
 * Limited-overs only.
 *
 * The chase and first-innings features are ratios against a fixed innings budget. A Test has no such
 * budget, which is why `evaluate` refuses one outright rather than assuming 120 balls, and why
 * long-format matches must not become limited-overs rows just because they are in the archive.
 */
export function isLimitedOvers(formatClass) {
  return formatClass === "T20" || formatClass === "ODI" || formatClass === "HUNDRED";
}

/** Any multi-day format — Tests and domestic first-class. Used for team stats and at inference. */
export function isLongFormat(formatClass) {
  return LONG_CLASSES.has(formatClass);
}

/**
 * Tests only, for the three-outcome model.
 *
 * Deliberately NOT every multi-day match. Training on Test and MDM pooled scored 0.506 holdout
 * accuracy against a 0.394 baseline; Tests alone scored 0.619 on the same features. Domestic
 * first-class cricket is played over four days by squads of wildly uneven strength and its
 * positions simply do not generalise to a Test. MDM still feeds team stats under its own key —
 * it is excluded from the model, not from the corpus.
 */
export function isLongTrainable(formatClass) {
  return formatClass === "TEST";
}

/**
 * Outcome class for [team], or null when the match produced no usable result.
 *
 * Cricsheet writes `{by, winner}` for a decided match and `{result}` for everything else. Only
 * "draw" and "tie" are trainable — an abandoned match tells us nothing about the position it was
 * abandoned from, and labelling it a draw would teach the model that rain is a cricketing outcome.
 */
export function longOutcomeFor({ winner, result }, team) {
  if (winner) return winner === team ? OUTCOME.WIN : OUTCOME.LOSS;
  const normalized = String(result ?? "").toLowerCase();
  if (normalized === "draw" || normalized === "tie") return OUTCOME.DRAW;
  return null;
}

/**
 * Choose the date at which a window starts, as a fraction of matches held back.
 *
 * A temporal split — not a random one. Rows from one match are near-duplicates of each other, so a
 * random split would put a match's own later balls in the test set and report a score that mostly
 * measures memorisation. Splitting on date also removes leakage through team win rates: those are
 * computed from the training window only, or a 2014 row carries information about who won in 2023.
 *
 * @returns the first date belonging to the held-back window, or null when there is too little data.
 */
export function holdoutCutoffDate(sortedDates, holdoutFraction = 0.2) {
  if (sortedDates.length < 10) return null;
  const idx = Math.floor(sortedDates.length * (1 - holdoutFraction));
  return sortedDates[Math.min(idx, sortedDates.length - 1)];
}

/**
 * Win rate for a team, shrunk toward even by a pseudo-count.
 *
 * The raw ratio is unusable in the tail: `all_json.zip` has teams with a single recorded match, and
 * a 1/1 record entered the logit as a +0.5 strength difference — the largest value the feature can
 * take, from one result. Six pseudo-matches at 0.5 costs a well-established side almost nothing
 * (100 matches at 0.6 reads 0.594) and costs a one-match side everything it should.
 */
export const WIN_RATE_PSEUDO_MATCHES = 6;

export function shrunkWinRate(wins, matches) {
  return (wins + 0.5 * WIN_RATE_PSEUDO_MATCHES) / (matches + WIN_RATE_PSEUDO_MATCHES);
}

// ── corpus skeleton ──────────────────────────────────────────────────────────

/**
 * One delivery packed into a byte: runs in bits 0-4, wickets in 5-6, legality in 7.
 *
 * A wide or no-ball is marked illegal by the caller (`richSkeletonOf` in corpus.js) and never
 * counted as a ball later. The first trainer incremented its ball counter on every delivery, so a
 * scrappy innings reported more balls bowled than it had faced — overstating the balls gone and
 * understating the balls left, on every row of every match. Correcting it alone moved chase
 * holdout log-loss from 0.4434 to 0.4194.
 *
 * The parsed archive is 22,524 matches of nested objects and does not fit in the function's memory
 * alongside the feature rows. Reducing each delivery to the three numbers any model needs takes the
 * whole corpus to roughly 14 MB, which does, and it removes the second decompression pass the
 * previous version needed to build rows after the temporal split was known.
 */
export function packDelivery(runs, wickets, legal) {
  return (Math.min(31, runs) & 31) | ((Math.min(3, wickets) & 3) << 5) | (legal ? 128 : 0);
}

export function unpackDelivery(code) {
  return { runs: code & 31, wickets: (code >> 5) & 3, legal: (code & 128) !== 0 };
}

/** Runs, wickets and legal balls in one skeleton innings. */
export function inningsTotals(inning) {
  let runs = 0;
  let wickets = 0;
  let legalBalls = 0;
  for (const code of inning?.balls ?? []) {
    const d = unpackDelivery(code);
    runs += d.runs;
    wickets += d.wickets;
    if (d.legal) legalBalls++;
  }
  return { runs, wickets, legalBalls };
}

// ── row construction ─────────────────────────────────────────────────────────

// The limited-overs and pre-match rows are built in build.js (`limitedOversRows`,
// `prematchContextRows`), because since v6 a row needs the match's as-of context as well as its
// score. The Test rows stay here: that head reads no context.

/**
 * Feature rows for a completed Test, one per over rather than per delivery.
 *
 * Per-over because a Test runs to ~2,700 balls against a T20's 120: per-delivery rows would let 911
 * Tests outweigh 13,992 T20s in the sampler while carrying no more information — consecutive
 * deliveries in a Test are near-identical.
 *
 * Rows are oriented to the side batting at that moment, and the label is that side's eventual
 * result, so the model learns a position rather than a team.
 */
export function longRows(match, winRateFor) {
  if (!isLongTrainable(match.formatClass) || match.innings.length === 0) return [];

  const ballsTotal = longMatchBalls(match.scheduledDays);
  const runsByTeam = new Map(match.teams.map((t) => [t, 0]));
  const rows = [];
  let ballsBowled = 0;

  // A side batting innings 2 and 3 back to back was made to follow on. Cricsheet has no flag for
  // it; the innings order is the only record.
  const followedOn =
    match.innings.length > 2 && match.innings[1]?.team === match.innings[2]?.team;

  for (let idx = 0; idx < match.innings.length; idx++) {
    const inning = match.innings[idx];
    const batting = inning.team;
    if (!runsByTeam.has(batting)) continue;
    const bowling = match.teams.find((t) => t !== batting) ?? "";
    const isFourthInnings = idx === 3;

    // A raw lead is not comparable across innings: the side batting first always shows a growing
    // positive lead simply because the opposition has not batted, and the side batting second
    // always shows a large deficit for the same reason. Replaying finished Tests exposed the model
    // reading that as position — it gave 78% to a side level after ten overs of day one, against a
    // 39% base rate, and flipped to 22% on the opposition's reply in the same match. Counting
    // innings used makes the lead mean the same thing at every point in the match.
    let ownUsed = 0;
    let oppUsed = 0;
    for (let j = 0; j <= idx; j++) {
      if (match.innings[j]?.team === batting) ownUsed++;
      else oppUsed++;
    }

    const label = longOutcomeFor(match, batting);
    if (label == null) return []; // abandoned: no usable label for any row of this match
    const teamStrengthDiff = winRateFor(batting) - winRateFor(bowling);

    let wicketsLost = 0;
    let inningsRuns = 0;
    let inningsBalls = 0;
    let ballsThisOver = 0;

    for (const code of inning.balls) {
      const d = unpackDelivery(code);
      runsByTeam.set(batting, runsByTeam.get(batting) + d.runs);
      inningsRuns += d.runs;
      wicketsLost += d.wickets;
      ballsBowled++;
      if (d.legal) {
        inningsBalls++;
        ballsThisOver++;
      }
      if (ballsThisOver < 6) continue;
      ballsThisOver = 0;

      const ballsRemaining = ballsTotal - ballsBowled;
      if (ballsRemaining <= 0 || wicketsLost >= 10) continue;

      const lead = runsByTeam.get(batting) - runsByTeam.get(bowling);
      // In the fourth innings the target is exactly the lead inverted: the side batting needs to
      // erase the deficit and go one past it. Cricsheet records no target, so this is the only way
      // to recover the chase, and it is the same quantity Cricbuzz reports as remRunsToWin.
      const wicketsInHand = 10 - wicketsLost;

      rows.push({
        features: longFeatures({
          lead,
          wicketsInHand,
          inningsIndex: idx,
          ballsRemaining,
          ballsTotal,
          teamStrengthDiff,
          isFourthInnings,
          runsNeeded: isFourthInnings ? Math.max(1 - lead, 0) : 0,
          followedOn,
          inningsUsedDiff: ownUsed - oppUsed,
          inningsRunRate: (inningsRuns * 6) / Math.max(inningsBalls, 1),
          // What the bowling side must still do to win: take this innings out, and a whole further
          // innings if the batting side has not yet used both of theirs.
          wicketsToTake: wicketsInHand + (ownUsed < 2 ? 10 : 0),
          scheduledDays: match.scheduledDays,
        }),
        label,
      });
    }
  }
  return rows;
}

// ── row storage ──────────────────────────────────────────────────────────────

/**
 * Fixed-capacity feature store backed by one flat `Float32Array`.
 *
 * The object-per-row version this replaces held ~250 bytes per row against 76, and exhausted the
 * heap at around two million rows — which is what the full corpus produces. Reservoir sampling once
 * full keeps the cap from biasing toward whatever was streamed first.
 */
export class RowSink {
  constructor(capacity, numFeatures, seed = 20260806) {
    this.capacity = capacity;
    this.numFeatures = numFeatures;
    this.X = new Float32Array(capacity * numFeatures);
    this.y = new Uint8Array(capacity);
    this.length = 0;
    this.seen = 0;
    this.rng = seed >>> 0;
  }

  random() {
    this.rng = (this.rng * 1664525 + 1013904223) >>> 0;
    return this.rng / 4294967296;
  }

  push(features, label) {
    this.seen++;
    let slot;
    if (this.length < this.capacity) {
      slot = this.length++;
    } else {
      const j = Math.floor(this.random() * this.seen);
      if (j >= this.capacity) return;
      slot = j;
    }
    this.X.set(features, slot * this.numFeatures);
    this.y[slot] = label;
  }

  /** Mean/std per feature over the rows held here. */
  fitStandardizer() {
    const { X, length, numFeatures } = this;
    const means = new Array(numFeatures).fill(0);
    const stds = new Array(numFeatures).fill(1);
    if (length === 0) return { means, stds };
    for (let i = 0; i < length; i++) {
      for (let j = 0; j < numFeatures; j++) means[j] += X[i * numFeatures + j];
    }
    for (let j = 0; j < numFeatures; j++) means[j] /= length;
    const variance = new Array(numFeatures).fill(0);
    for (let i = 0; i < length; i++) {
      for (let j = 0; j < numFeatures; j++) {
        variance[j] += (X[i * numFeatures + j] - means[j]) ** 2;
      }
    }
    for (let j = 0; j < numFeatures; j++) stds[j] = Math.sqrt(variance[j] / length) || 1;
    return { means, stds };
  }

  /** Standardize in place. */
  applyStandardizer({ means, stds }) {
    const { X, length, numFeatures } = this;
    for (let i = 0; i < length; i++) {
      for (let j = 0; j < numFeatures; j++) {
        const at = i * numFeatures + j;
        X[at] = (X[at] - means[j]) / stds[j];
      }
    }
  }
}

// ── maths ────────────────────────────────────────────────────────────────────

export function sigmoid(x) {
  return 1 / (1 + Math.exp(-x));
}

/**
 * Softmax over [NUM_CLASSES] logits, shifted by the max before exponentiating.
 *
 * The shift is not cosmetic: a long-format lead of several hundred runs drives a logit large enough
 * that `Math.exp` overflows to Infinity, and Infinity/Infinity is NaN — which would poison every
 * weight on the next gradient step.
 */
export function softmax(logits) {
  const max = Math.max(...logits);
  const exps = logits.map((l) => Math.exp(l - max));
  const sum = exps.reduce((a, b) => a + b, 0) || 1;
  return exps.map((e) => e / sum);
}

function logitAt(X, row, numFeatures, weights, bias) {
  let z = bias;
  const off = row * numFeatures;
  for (let j = 0; j < numFeatures; j++) z += weights[j] * X[off + j];
  return z;
}

/** Mean log-loss, accuracy and Brier score of a binary model over a sink. */
export function binaryMetrics(sink, weights, bias, temperature = 1) {
  if (sink.length === 0) return { logLoss: null, accuracy: null, brier: null, rows: 0 };
  const eps = 1e-7;
  let loss = 0;
  let correct = 0;
  let brier = 0;
  for (let i = 0; i < sink.length; i++) {
    const raw = sigmoid(logitAt(sink.X, i, sink.numFeatures, weights, bias) / temperature);
    const p = Math.min(1 - eps, Math.max(eps, raw));
    const y = sink.y[i];
    loss += -(y * Math.log(p) + (1 - y) * Math.log(1 - p));
    if ((p >= 0.5 ? 1 : 0) === y) correct++;
    brier += (p - y) ** 2;
  }
  return {
    logLoss: loss / sink.length,
    accuracy: correct / sink.length,
    brier: brier / sink.length,
    rows: sink.length,
  };
}

/**
 * Expected calibration error: how far a stated probability is from the rate it actually happens at.
 *
 * Log-loss can be improved by a model that is sharper on average while still lying at the edges;
 * this is the number that says whether "70%" means seventy per cent. Reported alongside so a future
 * change that trades honesty for sharpness is visible rather than silent.
 */
export function expectedCalibrationError(sink, weights, bias, temperature = 1, bins = 20) {
  if (sink.length === 0) return null;
  const sum = new Float64Array(bins);
  const hits = new Float64Array(bins);
  const counts = new Int32Array(bins);
  for (let i = 0; i < sink.length; i++) {
    const p = sigmoid(logitAt(sink.X, i, sink.numFeatures, weights, bias) / temperature);
    const b = Math.min(bins - 1, Math.floor(p * bins));
    sum[b] += p;
    hits[b] += sink.y[i];
    counts[b]++;
  }
  let error = 0;
  for (let b = 0; b < bins; b++) {
    if (!counts[b]) continue;
    error += (counts[b] / sink.length) * Math.abs(sum[b] / counts[b] - hits[b] / counts[b]);
  }
  return error;
}

/** Share of rows labelled 1 — the accuracy a constant predictor would get. */
export function baseRate(sink) {
  if (sink.length === 0) return null;
  let positives = 0;
  for (let i = 0; i < sink.length; i++) positives += sink.y[i];
  return positives / sink.length;
}

/** Share of rows in each class. */
export function classBaseRates(sink) {
  if (sink.length === 0) return null;
  const counts = new Array(NUM_CLASSES).fill(0);
  for (let i = 0; i < sink.length; i++) counts[sink.y[i]]++;
  return counts.map((c) => c / sink.length);
}

// ── optimisation ─────────────────────────────────────────────────────────────

const ADAM_BETA1 = 0.9;
const ADAM_BETA2 = 0.999;
const ADAM_EPS = 1e-8;

/**
 * Binary logistic regression by Adam, stopping when a held-back window stops improving.
 *
 * The predecessor ran a fixed 30 epochs of plain SGD and reported only its training loss, so it
 * could neither tell that it had converged nor that it had started memorising. Early stopping needs
 * a third window — validation is carved out of the *training* period, never out of the holdout,
 * which must stay untouched to mean anything.
 */
export function trainBinary(train, valid, options = {}) {
  const {
    epochs = 100, batchSize = 512, learningRate = 0.05, l2 = 1e-4, patience = 8, seed = 12345,
  } = options;
  const k = train.numFeatures;
  const weights = new Float64Array(k);
  let bias = 0;
  if (train.length === 0) return { weights: Array.from(weights), bias, validLogLoss: null, epoch: -1 };

  const mW = new Float64Array(k);
  const vW = new Float64Array(k);
  const gradW = new Float64Array(k);
  let mB = 0;
  let vB = 0;
  let step = 0;

  let rng = seed >>> 0;
  const random = () => ((rng = (rng * 1664525 + 1013904223) >>> 0) / 4294967296);
  const order = new Int32Array(train.length);
  for (let i = 0; i < train.length; i++) order[i] = i;

  let best = { logLoss: Infinity, weights: Array.from(weights), bias, epoch: -1 };
  let sinceImprovement = 0;

  for (let epoch = 0; epoch < epochs; epoch++) {
    for (let i = train.length - 1; i > 0; i--) {
      const j = Math.floor(random() * (i + 1));
      const tmp = order[i];
      order[i] = order[j];
      order[j] = tmp;
    }
    for (let start = 0; start < train.length; start += batchSize) {
      const end = Math.min(start + batchSize, train.length);
      gradW.fill(0);
      let gradB = 0;
      for (let i = start; i < end; i++) {
        const row = order[i];
        const off = row * k;
        let z = bias;
        for (let j = 0; j < k; j++) z += weights[j] * train.X[off + j];
        const err = sigmoid(z) - train.y[row];
        gradB += err;
        for (let j = 0; j < k; j++) gradW[j] += err * train.X[off + j];
      }
      const n = end - start;
      step++;
      const c1 = 1 - ADAM_BETA1 ** step;
      const c2 = 1 - ADAM_BETA2 ** step;
      for (let j = 0; j < k; j++) {
        const g = gradW[j] / n + l2 * weights[j];
        mW[j] = ADAM_BETA1 * mW[j] + (1 - ADAM_BETA1) * g;
        vW[j] = ADAM_BETA2 * vW[j] + (1 - ADAM_BETA2) * g * g;
        weights[j] -= learningRate * (mW[j] / c1) / (Math.sqrt(vW[j] / c2) + ADAM_EPS);
      }
      const gb = gradB / n;
      mB = ADAM_BETA1 * mB + (1 - ADAM_BETA1) * gb;
      vB = ADAM_BETA2 * vB + (1 - ADAM_BETA2) * gb * gb;
      bias -= learningRate * (mB / c1) / (Math.sqrt(vB / c2) + ADAM_EPS);
    }

    const scored = valid.length > 0 ? valid : train;
    const { logLoss } = binaryMetrics(scored, weights, bias);
    if (logLoss < best.logLoss - 1e-5) {
      best = { logLoss, weights: Array.from(weights), bias, epoch };
      sinceImprovement = 0;
    } else if (++sinceImprovement >= patience) {
      break;
    }
  }
  return { ...best, validLogLoss: best.logLoss };
}

/** Multinomial logistic regression by Adam, same early-stopping contract as [trainBinary]. */
export function trainMulticlass(train, valid, options = {}) {
  const {
    epochs = 200, batchSize = 512, learningRate = 0.02, l2 = 1e-4, patience = 20, seed = 7,
  } = options;
  const k = train.numFeatures;
  const weights = Array.from({ length: NUM_CLASSES }, () => new Float64Array(k));
  const biases = new Float64Array(NUM_CLASSES);
  const empty = {
    weights: weights.map((w) => Array.from(w)),
    biases: Array.from(biases),
    validLogLoss: null,
    epoch: -1,
  };
  if (train.length === 0) return empty;

  const mW = Array.from({ length: NUM_CLASSES }, () => new Float64Array(k));
  const vW = Array.from({ length: NUM_CLASSES }, () => new Float64Array(k));
  const mB = new Float64Array(NUM_CLASSES);
  const vB = new Float64Array(NUM_CLASSES);
  let step = 0;

  let rng = seed >>> 0;
  const random = () => ((rng = (rng * 1664525 + 1013904223) >>> 0) / 4294967296);
  const order = new Int32Array(train.length);
  for (let i = 0; i < train.length; i++) order[i] = i;

  const probsAt = (X, row) => {
    const off = row * k;
    const logits = new Array(NUM_CLASSES);
    for (let c = 0; c < NUM_CLASSES; c++) {
      let z = biases[c];
      for (let j = 0; j < k; j++) z += weights[c][j] * X[off + j];
      logits[c] = z;
    }
    return softmax(logits);
  };

  let best = {
    logLoss: Infinity,
    weights: weights.map((w) => Array.from(w)),
    biases: Array.from(biases),
    epoch: -1,
  };
  let sinceImprovement = 0;

  for (let epoch = 0; epoch < epochs; epoch++) {
    for (let i = train.length - 1; i > 0; i--) {
      const j = Math.floor(random() * (i + 1));
      const tmp = order[i];
      order[i] = order[j];
      order[j] = tmp;
    }
    for (let start = 0; start < train.length; start += batchSize) {
      const end = Math.min(start + batchSize, train.length);
      const gradW = Array.from({ length: NUM_CLASSES }, () => new Float64Array(k));
      const gradB = new Float64Array(NUM_CLASSES);
      for (let i = start; i < end; i++) {
        const row = order[i];
        const off = row * k;
        const p = probsAt(train.X, row);
        for (let c = 0; c < NUM_CLASSES; c++) {
          const err = p[c] - (c === train.y[row] ? 1 : 0);
          gradB[c] += err;
          for (let j = 0; j < k; j++) gradW[c][j] += err * train.X[off + j];
        }
      }
      const n = end - start;
      step++;
      const c1 = 1 - ADAM_BETA1 ** step;
      const c2 = 1 - ADAM_BETA2 ** step;
      for (let c = 0; c < NUM_CLASSES; c++) {
        for (let j = 0; j < k; j++) {
          const g = gradW[c][j] / n + l2 * weights[c][j];
          mW[c][j] = ADAM_BETA1 * mW[c][j] + (1 - ADAM_BETA1) * g;
          vW[c][j] = ADAM_BETA2 * vW[c][j] + (1 - ADAM_BETA2) * g * g;
          weights[c][j] -= learningRate * (mW[c][j] / c1) / (Math.sqrt(vW[c][j] / c2) + ADAM_EPS);
        }
        const gb = gradB[c] / n;
        mB[c] = ADAM_BETA1 * mB[c] + (1 - ADAM_BETA1) * gb;
        vB[c] = ADAM_BETA2 * vB[c] + (1 - ADAM_BETA2) * gb * gb;
        biases[c] -= learningRate * (mB[c] / c1) / (Math.sqrt(vB[c] / c2) + ADAM_EPS);
      }
    }

    const scored = valid.length > 0 ? valid : train;
    let loss = 0;
    for (let i = 0; i < scored.length; i++) loss += -Math.log(probsAt(scored.X, i)[scored.y[i]] + 1e-7);
    loss /= scored.length;
    if (loss < best.logLoss - 1e-5) {
      best = {
        logLoss: loss,
        weights: weights.map((w) => Array.from(w)),
        biases: Array.from(biases),
        epoch,
      };
      sinceImprovement = 0;
    } else if (++sinceImprovement >= patience) {
      break;
    }
  }
  return { ...best, validLogLoss: best.logLoss };
}

/** Class probabilities for one row under a multinomial model. */
export function predictClasses(features, { weights, biases }) {
  const logits = biases.map((bias, c) => {
    let logit = bias;
    for (let j = 0; j < features.length; j++) logit += weights[c][j] * features[j];
    return logit;
  });
  return softmax(logits);
}

/** Mean cross-entropy and accuracy of a multinomial model over a sink. */
export function multiclassMetrics(sink, model) {
  if (sink.length === 0) return { logLoss: null, accuracy: null, rows: 0 };
  const k = sink.numFeatures;
  const features = new Array(k);
  let loss = 0;
  let correct = 0;
  for (let i = 0; i < sink.length; i++) {
    for (let j = 0; j < k; j++) features[j] = sink.X[i * k + j];
    const probs = predictClasses(features, model);
    loss += -Math.log(probs[sink.y[i]] + 1e-7);
    let best = 0;
    for (let c = 1; c < probs.length; c++) if (probs[c] > probs[best]) best = c;
    if (best === sink.y[i]) correct++;
  }
  return { logLoss: loss / sink.length, accuracy: correct / sink.length, rows: sink.length };
}

/**
 * Single-scalar temperature on the logit, fitted by golden-section search on the validation window.
 *
 * A logistic model trained on log-loss is nearly calibrated already — measured temperatures land
 * between 1.10 and 1.34, worth roughly 0.002 to 0.007 log-loss. It is kept because the cost is one
 * number and thirty evaluations, and because a future feature change that does skew the outputs
 * gets corrected automatically rather than shipping a confidently wrong bar.
 */
export function fitTemperature(sink, weights, bias, lo = 0.4, hi = 2.5) {
  if (sink.length === 0) return 1;
  const loss = (T) => binaryMetrics(sink, weights, bias, T).logLoss;
  const phi = (Math.sqrt(5) - 1) / 2;
  let a = lo;
  let b = hi;
  let c = b - phi * (b - a);
  let d = a + phi * (b - a);
  let fc = loss(c);
  let fd = loss(d);
  for (let i = 0; i < 30; i++) {
    if (fc < fd) {
      b = d; d = c; fd = fc; c = b - phi * (b - a); fc = loss(c);
    } else {
      a = c; c = d; fc = fd; d = a + phi * (b - a); fd = loss(d);
    }
  }
  return (a + b) / 2;
}

/**
 * Fraction of the match that must be played before the long-format model is trusted on its own.
 *
 * A logistic model is linear in its features, so at the edge of the training data it extrapolates
 * with unearned confidence. Replaying finished Tests showed it: after one over of day one — before
 * anything had happened — it returned 73% for the side batting first, against a 39% base rate,
 * because `ballsRemainingFraction ≈ 0.998` sits in the corner of feature space where almost no
 * training rows live.
 *
 * There is genuinely no information at the start of a Test, so the honest output is the prior.
 *
 * Swept on the holdout. Log-loss falls from 0.8400 (no shrinkage) to a 0.8020 minimum at 0.35-0.50
 * and then rises again — 0.8192 at 0.75, 0.8474 at 1.0 — so this is a real optimum rather than
 * "flatter always scores better". Crucially, log-loss restricted to rows past the halfway point is
 * identical for every setting up to 0.50 and only degrades beyond it: shrinkage touches the
 * uninformative early rows and leaves the part of the match worth reading alone.
 *
 * 0.25 rather than the 0.35 minimum, which is a judgement and not a measurement: both cost nothing
 * late, and a quarter of a Test is about day one, so a genuine day-one collapse escapes the prior
 * sooner.
 */
export const PRIOR_SETTLE_PROGRESS = 0.25;

/**
 * Blend model output toward the base rates while the match is too young to read.
 *
 * At `progress = 0` this returns the prior exactly; by `settle` it returns the model untouched.
 * Applied at inference, not in training — the model should still learn from early rows, it just
 * should not be believed on them.
 *
 * @param probs   model class probabilities
 * @param priors  class base rates from the training window
 * @param progress fraction of the match's scheduled balls already bowled, 0..1
 */
export function shrinkToPrior(probs, priors, progress, settle = PRIOR_SETTLE_PROGRESS) {
  const safeSettle = settle > 0 ? settle : 1;
  const weight = Math.max(0, Math.min(1, (Number(progress) || 0) / safeSettle));
  const fallback = 1 / probs.length;
  return probs.map((p, i) => weight * p + (1 - weight) * (priors?.[i] ?? fallback));
}
