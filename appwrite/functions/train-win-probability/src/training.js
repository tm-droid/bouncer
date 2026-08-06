/**
 * Pure training-data construction for the win-probability model.
 *
 * Split out of main.js so the row math and the temporal split are testable without downloading the
 * corpus or touching Appwrite.
 *
 * v4 widened the corpus from `t20s_json.zip` (5,602 matches, 109 teams, T20 only) to
 * `all_json.zip` (22,524 matches, 392 teams, every format). Three things had to change with it,
 * and each of them is a correctness fix rather than a consequence of the extra volume:
 *
 *  - **Team stats are keyed by (team, gender, format).** The old key was the bare team name, so
 *    `t20s_json.zip` — which is 3,490 men's and 2,112 women's matches pooled — gave Australia one
 *    win rate blending both (0.642 against a men's-only 0.555, and Thailand 0.557 against 0.359).
 *    That was harmless only while the lookup never hit; once match-sync started resolving names,
 *    the blend reached the logit. Widening to every format would have compounded it by folding
 *    Tests and ODIs into the same number.
 *  - **The chase model stays limited-overs.** Its features assume a fixed innings budget, which a
 *    Test does not have. Long-format matches feed team stats and the separate long-format model,
 *    never `chaseRows`.
 *  - **The innings budget multiplies by `balls_per_over`.** The Hundred is recorded as
 *    `match_type: "T20"`, `overs: 20`, `balls_per_over: 5` — 100 balls. `overs * 6` gave it 120,
 *    so every Hundred row would have been computed against a budget 20 balls too long.
 */

export const MODEL_VERSION = 4;

export const NUM_FEATURES = 5;
export const FEATURE_NAMES = [
  "runRateRatio",
  "wicketsInHand",
  "ballsRemainingFraction",
  "deficitPressure",
  "teamStrengthDiff",
];

/**
 * Long-format features. Separate model, separate shape — a Test has no run chase until innings 4.
 *
 * The last three exist because a Test's dynamics change completely in the fourth innings: a lead
 * that means "well placed" in innings two means "needs this many to win" in innings four, and the
 * five positional features alone cannot tell those apart. `followOn` is carried because a side
 * batting again immediately is in a materially different position from one batting in turn.
 */
export const NUM_LONG_FEATURES = 9;
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
];

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

/**
 * The key team stats are stored under.
 *
 * Must be produced identically wherever stats are read, or names get validated against one map and
 * looked up in another. `winprob.js` carries the reader; this is the writer, and
 * `team-key-parity.test.js` pins them together.
 */
export function teamKey(team, gender, formatClass) {
  return `${String(team ?? "").trim()}|${gender ?? "male"}|${formatClass ?? "T20"}`;
}

/**
 * Balls in one innings for a Cricsheet match.
 *
 * `info.overs` is the agreed overs per innings, which is how a rain-shortened game is represented,
 * and `info.balls_per_over` is 5 for The Hundred and 6 everywhere else. Both are needed: the
 * previous `overs * 6` was right for T20 and ODI and wrong by 20 balls for every Hundred match.
 */
export function inningsBudgetBalls(info) {
  const overs = Number(info?.overs);
  const ballsPerOver = Number(info?.balls_per_over);
  const safeOvers = Number.isFinite(overs) && overs > 0 ? overs : DEFAULT_OVERS;
  const safeBalls = Number.isFinite(ballsPerOver) && ballsPerOver > 0 ? ballsPerOver : 6;
  return Math.round(safeOvers * safeBalls);
}

/** Scheduled balls in the whole match, from the number of days it was allotted. */
export function longMatchBalls(info) {
  const days = info?.dates?.length;
  const safeDays = Number.isFinite(days) && days > 0 ? days : 5;
  return safeDays * BALLS_PER_LONG_DAY;
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
 * The chase model's features are ratios against a fixed innings budget. A Test has no such budget,
 * which is why `evaluate` refuses one outright rather than assuming 120 balls, and why 3,090
 * long-format matches must not become chase rows just because they are now in the archive.
 */
export function isChaseTrainable(info) {
  const cls = formatClassOf(info);
  return (cls === "T20" || cls === "ODI" || cls === "HUNDRED") && (info?.teams?.length ?? 0) === 2;
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
export function isLongTrainable(info) {
  return formatClassOf(info) === "TEST" && (info?.teams?.length ?? 0) === 2;
}

/**
 * Outcome class for [team], or null when the match produced no usable result.
 *
 * Cricsheet writes `{by, winner}` for a decided match and `{result}` for everything else. Only
 * "draw" and "tie" are trainable — an abandoned match tells us nothing about the position it was
 * abandoned from, and labelling it a draw would teach the model that rain is a cricketing outcome.
 */
export function longOutcomeFor(info, team) {
  const outcome = info?.outcome ?? {};
  if (outcome.winner) return outcome.winner === team ? OUTCOME.WIN : OUTCOME.LOSS;
  const result = String(outcome.result ?? "").toLowerCase();
  if (result === "draw" || result === "tie") return OUTCOME.DRAW;
  return null;
}

/**
 * Choose the date at which the holdout window starts.
 *
 * A temporal split — not a random one. Rows from one match are near-duplicates of each other, so a
 * random split would put a match's own later balls in the test set and report a score that mostly
 * measures memorisation. Splitting on date also removes the leakage that the previous back-fill
 * introduced: team win rates must be computed from the training window only, or a 2014 row carries
 * information about who won in 2023.
 *
 * @returns the first date belonging to the holdout window, or null when there is too little data.
 */
export function holdoutCutoffDate(dates, holdoutFraction = 0.2) {
  const sorted = dates.filter(Boolean).slice().sort();
  if (sorted.length < 10) return null;
  const idx = Math.floor(sorted.length * (1 - holdoutFraction));
  return sorted[Math.min(idx, sorted.length - 1)];
}

/** Sum runs and count balls in an innings. */
export function inningsTotals(inning) {
  let runs = 0;
  let balls = 0;
  for (const over of inning?.overs ?? []) {
    for (const ball of over.deliveries ?? []) {
      runs += ball.runs?.total ?? 0;
      balls++;
    }
  }
  return { runs, balls };
}

/**
 * Build one feature row per delivery of a completed chase.
 *
 * All quantities describe the state AFTER the delivery. The previous version computed
 * `ballsLeft = 120 - ballsFaced` before incrementing `ballsFaced`, so every row paired post-ball
 * runs with pre-ball balls-remaining: `ballsFaced + ballsLeft === 121`, and both `rrr` and
 * `ballsRemainingFraction` were credited one ball that had already been bowled.
 *
 * @param {object} raw Cricsheet match JSON
 * @param {(team: string) => number} winRateFor win rate lookup, TRAINING WINDOW ONLY
 */
export function chaseRows(raw, winRateFor) {
  const info = raw?.info;
  if (!isChaseTrainable(info)) return [];
  const outcome = info.outcome;
  if (!outcome?.winner) return [];

  const innings = raw?.innings ?? [];
  if (innings.length < 2) return [];

  const budget = inningsBudgetBalls(info);
  const first = inningsTotals(innings[0]);
  const targetRuns = first.runs + 1;

  const chasingTeam = innings[1].team;
  const bowlingTeam = info.teams.find((t) => t !== chasingTeam) ?? "";
  const label = outcome.winner === chasingTeam ? 1 : 0;
  const teamStrengthDiff = winRateFor(chasingTeam) - winRateFor(bowlingTeam);

  const rows = [];
  let runsScored = 0;
  let wicketsFallen = 0;
  let ballsFaced = 0;

  for (const over of innings[1].overs ?? []) {
    for (const ball of over.deliveries ?? []) {
      runsScored += ball.runs?.total ?? 0;
      wicketsFallen += ball.wickets?.length ?? 0;
      ballsFaced++;

      const runsNeeded = targetRuns - runsScored;
      const ballsLeft = budget - ballsFaced;

      if (ballsLeft <= 0) continue;
      if (wicketsFallen >= 10) continue;
      if (runsNeeded <= 0) continue;

      const crr = (runsScored * 6) / ballsFaced;
      const rrr = Math.max((runsNeeded * 6) / ballsLeft, 0.1);

      rows.push({
        features: [
          crr / rrr,
          (10 - wicketsFallen) / 10,
          ballsLeft / budget,
          runsNeeded / ballsLeft,
          teamStrengthDiff,
        ],
        label,
      });
    }
  }
  return rows;
}

/**
 * Build feature rows for a completed long-format match, one per over rather than per delivery.
 *
 * Per-over because a Test runs to ~2,700 balls against a T20's 120: per-delivery rows would let
 * 911 Tests outweigh 13,992 T20s in the sampler while carrying no more information — consecutive
 * deliveries in a Test are near-identical.
 *
 * Rows are oriented to the side batting at that moment, and the label is that side's eventual
 * result, so the model learns a position rather than a team.
 *
 * @param {object} raw Cricsheet match JSON
 * @param {(team: string) => number} winRateFor win rate lookup, TRAINING WINDOW ONLY
 */
export function longRows(raw, winRateFor) {
  const info = raw?.info;
  if (!isLongTrainable(info)) return [];

  const innings = raw?.innings ?? [];
  if (innings.length === 0) return [];

  const totalBalls = longMatchBalls(info);
  const runsByTeam = new Map(info.teams.map((t) => [t, 0]));
  const rows = [];
  let ballsBowled = 0;

  // A side batting innings 2 and 3 back to back was made to follow on. Cricsheet has no flag for
  // it; the innings order is the only record.
  const followedOn = innings.length > 2 && innings[1]?.team === innings[2]?.team ? 1 : 0;

  for (let idx = 0; idx < innings.length; idx++) {
    const inning = innings[idx];
    const battingTeam = inning?.team;
    if (!runsByTeam.has(battingTeam)) continue;
    const bowlingTeam = info.teams.find((t) => t !== battingTeam) ?? "";
    const isFourth = idx === 3 ? 1 : 0;

    // A raw lead is not comparable across innings: the side batting first always shows a growing
    // positive lead simply because the opposition has not batted, and the side batting second
    // always shows a large deficit for the same reason. Replaying finished Tests exposed the
    // model reading that as position — it gave 78% to a side level after ten overs of day one,
    // against a 39% base rate, and flipped to 22% on the opposition's reply in the same match.
    // Counting innings used makes the lead mean the same thing at every point in the match.
    let ownUsed = 0;
    let oppUsed = 0;
    for (let j = 0; j <= idx; j++) {
      if (innings[j]?.team === battingTeam) ownUsed++;
      else oppUsed++;
    }
    const inningsUsedDiff = ownUsed - oppUsed;

    const label = longOutcomeFor(info, battingTeam);
    if (label == null) return []; // abandoned: no usable label for any row of this match
    const teamStrengthDiff = winRateFor(battingTeam) - winRateFor(bowlingTeam);

    let wicketsFallen = 0;
    let ballsThisOver = 0;

    for (const over of inning?.overs ?? []) {
      for (const ball of over.deliveries ?? []) {
        runsByTeam.set(battingTeam, runsByTeam.get(battingTeam) + (ball.runs?.total ?? 0));
        wicketsFallen += ball.wickets?.length ?? 0;
        ballsBowled++;
        ballsThisOver++;
      }
      if (ballsThisOver === 0) continue;
      ballsThisOver = 0;

      const ballsLeft = totalBalls - ballsBowled;
      if (ballsLeft <= 0) continue;
      if (wicketsFallen >= 10) continue;

      const lead = runsByTeam.get(battingTeam) - runsByTeam.get(bowlingTeam);
      // In the fourth innings the target is exactly the lead inverted: the side batting needs to
      // erase the deficit and go one past it. Cricsheet records no target, so this is the only
      // way to recover the chase, and it is the same quantity Cricbuzz reports as remRunsToWin.
      const runsNeeded = isFourth ? Math.max(1 - lead, 0) : 0;

      rows.push({
        features: [
          lead / 100,
          (10 - wicketsFallen) / 10,
          (idx + 1) / 4,
          ballsLeft / totalBalls,
          teamStrengthDiff,
          isFourth,
          isFourth ? runsNeeded / Math.max(ballsLeft, 1) : 0,
          followedOn,
          inningsUsedDiff,
        ],
        label,
      });
    }
  }
  return rows;
}

/** Mean/std per feature, computed over [rows] only. */
export function fitStandardizer(rows, numFeatures = NUM_FEATURES) {
  const means = new Array(numFeatures).fill(0);
  const stds = new Array(numFeatures).fill(1);
  const n = rows.length;
  if (n === 0) return { means, stds };

  for (const row of rows) {
    for (let j = 0; j < numFeatures; j++) means[j] += row.features[j];
  }
  for (let j = 0; j < numFeatures; j++) means[j] /= n;

  const variance = new Array(numFeatures).fill(0);
  for (const row of rows) {
    for (let j = 0; j < numFeatures; j++) {
      const diff = row.features[j] - means[j];
      variance[j] += diff * diff;
    }
  }
  for (let j = 0; j < numFeatures; j++) {
    stds[j] = Math.sqrt(variance[j] / n) || 1;
  }
  return { means, stds };
}

/** Apply a standardizer in place. */
export function applyStandardizer(rows, { means, stds }) {
  const numFeatures = means.length;
  for (const row of rows) {
    for (let j = 0; j < numFeatures; j++) {
      row.features[j] = (row.features[j] - means[j]) / stds[j];
    }
  }
  return rows;
}

export function sigmoid(x) {
  return 1 / (1 + Math.exp(-x));
}

function predict(features, weights, bias) {
  let logit = bias;
  for (let j = 0; j < weights.length; j++) logit += weights[j] * features[j];
  return sigmoid(logit);
}

/** Mean log-loss and accuracy of [weights]/[bias] over [rows]. */
export function evaluateModel(rows, weights, bias) {
  if (rows.length === 0) return { logLoss: null, accuracy: null, rows: 0 };
  const eps = 1e-7;
  let loss = 0;
  let correct = 0;
  for (const { features, label } of rows) {
    const p = predict(features, weights, bias);
    loss += -(label * Math.log(p + eps) + (1 - label) * Math.log(1 - p + eps));
    if ((p >= 0.5 ? 1 : 0) === label) correct++;
  }
  return { logLoss: loss / rows.length, accuracy: correct / rows.length, rows: rows.length };
}

/** Share of rows labelled 1 — the accuracy a constant predictor would get. */
export function baseRate(rows) {
  if (rows.length === 0) return null;
  return rows.reduce((acc, r) => acc + r.label, 0) / rows.length;
}

/**
 * Softmax over [NUM_CLASSES] logits, shifted by the max before exponentiating.
 *
 * The shift is not cosmetic: a long-format lead of several hundred runs drives a logit large
 * enough that `Math.exp` overflows to Infinity, and Infinity/Infinity is NaN — which would poison
 * every weight on the next gradient step.
 */
export function softmax(logits) {
  const max = Math.max(...logits);
  const exps = logits.map((l) => Math.exp(l - max));
  const sum = exps.reduce((a, b) => a + b, 0) || 1;
  return exps.map((e) => e / sum);
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

/** Mean cross-entropy and accuracy of a multinomial model over [rows]. */
export function evaluateMulticlass(rows, model) {
  if (rows.length === 0) return { logLoss: null, accuracy: null, rows: 0 };
  const eps = 1e-7;
  let loss = 0;
  let correct = 0;
  for (const { features, label } of rows) {
    const probs = predictClasses(features, model);
    loss += -Math.log(probs[label] + eps);
    let best = 0;
    for (let c = 1; c < probs.length; c++) if (probs[c] > probs[best]) best = c;
    if (best === label) correct++;
  }
  return { logLoss: loss / rows.length, accuracy: correct / rows.length, rows: rows.length };
}

/**
 * Fraction of the match that must be played before the model is trusted on its own.
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
 * 0.6533 for every setting up to 0.50 and only degrades beyond it: shrinkage touches the
 * uninformative early rows and leaves the part of the match worth reading alone.
 *
 * 0.25 rather than the 0.35 minimum, which is a judgement and not a measurement: both cost nothing
 * late, and a quarter of a Test is about day one, so a genuine day-one collapse escapes the prior
 * sooner. Accuracy is flat across the whole range (0.651 to 0.648), so this trades almost nothing.
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

/** Share of rows in each class — what a constant predictor would achieve. */
export function classBaseRates(rows) {
  if (rows.length === 0) return null;
  const counts = new Array(NUM_CLASSES).fill(0);
  for (const r of rows) counts[r.label]++;
  return counts.map((c) => c / rows.length);
}

/**
 * Fit a multinomial logistic model by mini-batch gradient descent with L2 regularisation.
 *
 * Kept here rather than in main.js so it is testable without the corpus, matching how the binary
 * path is already split.
 */
export function trainMulticlass(rows, { epochs, batchSize, learningRate, l2, numFeatures }) {
  const weights = Array.from({ length: NUM_CLASSES }, () => new Array(numFeatures).fill(0));
  const biases = new Array(NUM_CLASSES).fill(0);
  if (rows.length === 0) return { weights, biases };

  for (let epoch = 0; epoch < epochs; epoch++) {
    for (let start = 0; start < rows.length; start += batchSize) {
      const batch = rows.slice(start, start + batchSize);
      const gradW = Array.from({ length: NUM_CLASSES }, () => new Array(numFeatures).fill(0));
      const gradB = new Array(NUM_CLASSES).fill(0);

      for (const { features, label } of batch) {
        const probs = predictClasses(features, { weights, biases });
        for (let c = 0; c < NUM_CLASSES; c++) {
          const err = probs[c] - (c === label ? 1 : 0);
          gradB[c] += err;
          for (let j = 0; j < numFeatures; j++) gradW[c][j] += err * features[j];
        }
      }

      const scale = learningRate / batch.length;
      for (let c = 0; c < NUM_CLASSES; c++) {
        biases[c] -= scale * gradB[c];
        for (let j = 0; j < numFeatures; j++) {
          weights[c][j] -= scale * gradW[c][j] + learningRate * l2 * weights[c][j];
        }
      }
    }
  }
  return { weights, biases };
}
