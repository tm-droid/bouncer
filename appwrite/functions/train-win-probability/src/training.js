/**
 * Pure training-data construction for the win-probability model.
 *
 * Split out of main.js so the row math and the temporal split are testable without downloading a
 * 40 MB corpus or touching Appwrite.
 */

export const MODEL_VERSION = 3;
export const NUM_FEATURES = 5;
export const FEATURE_NAMES = [
  "runRateRatio",
  "wicketsInHand",
  "ballsRemainingFraction",
  "deficitPressure",
  "teamStrengthDiff",
];

/** Default innings budget when a match declares none. Only T20/IT20 enter this pipeline. */
const DEFAULT_OVERS = 20;

/**
 * Balls in one innings for a Cricsheet match.
 *
 * Cricsheet records the agreed overs per innings in `info.overs`, which is how a rain-shortened
 * game is represented. The previous code hardcoded 120, so a 15-over-per-side match had every row
 * computed against a budget 30 balls too long — `ballsRemainingFraction` and `rrr` were both wrong
 * for the entire innings.
 */
export function inningsBudgetBalls(info) {
  const overs = Number(info?.overs);
  const safeOvers = Number.isFinite(overs) && overs > 0 ? overs : DEFAULT_OVERS;
  return Math.round(safeOvers * 6);
}

/** ISO date string for a match, or null. Cricsheet uses `info.dates: ["2024-06-01", ...]`. */
export function matchDate(info) {
  const first = info?.dates?.[0];
  return typeof first === "string" && first.length >= 10 ? first.slice(0, 10) : null;
}

export function isSupportedMatch(info) {
  if (!info) return false;
  if (info.match_type !== "T20" && info.match_type !== "IT20") return false;
  return (info.teams?.length ?? 0) === 2;
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
  if (!isSupportedMatch(info)) return [];
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

/** Mean/std per feature, computed over [rows] only. */
export function fitStandardizer(rows) {
  const means = new Array(NUM_FEATURES).fill(0);
  const stds = new Array(NUM_FEATURES).fill(1);
  const n = rows.length;
  if (n === 0) return { means, stds };

  for (const row of rows) {
    for (let j = 0; j < NUM_FEATURES; j++) means[j] += row.features[j];
  }
  for (let j = 0; j < NUM_FEATURES; j++) means[j] /= n;

  const variance = new Array(NUM_FEATURES).fill(0);
  for (const row of rows) {
    for (let j = 0; j < NUM_FEATURES; j++) {
      const diff = row.features[j] - means[j];
      variance[j] += diff * diff;
    }
  }
  for (let j = 0; j < NUM_FEATURES; j++) {
    stds[j] = Math.sqrt(variance[j] / n) || 1;
  }
  return { means, stds };
}

/** Apply a standardizer in place. */
export function applyStandardizer(rows, { means, stds }) {
  for (const row of rows) {
    for (let j = 0; j < NUM_FEATURES; j++) {
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
  for (let j = 0; j < NUM_FEATURES; j++) logit += weights[j] * features[j];
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
