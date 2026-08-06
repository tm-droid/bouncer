/**
 * The single win-probability implementation.
 *
 * This module is the authority: the device computes nothing. It is duplicated byte-for-byte into
 * every function that needs it (win-probability, poll-live-matches, match-sync) because Appwrite
 * deploys each function as an isolated archive with no shared package. If you edit this file, copy
 * it to the others — winprob.test.js asserts the copies agree.
 *
 * ## Model contract v5
 *
 * The artifact carries three models rather than one:
 *
 *  - `chase` — the side batting second, in a limited-overs match.
 *  - `firstInnings` — the side setting the target. v4 had no model here; this half of every match
 *    was scored by the hand-tuned heuristic below, which is 0.069 log-loss worse on the same data.
 *  - `long` — a Test, three outcomes, because a Test can be drawn and no two-class model can say so.
 *
 * Each carries its own feature list, standardizer and calibration temperature, and the feature
 * builders here must produce the same vector as `training.js` did when fitting. That is not a style
 * rule: a reordered or rescaled feature is silently wrong rather than broken, and it is pinned by
 * `feature-parity.test.js` in train-win-probability.
 *
 * A v4 or older artifact is deliberately NOT accepted on the learned path. v4's five-feature chase
 * vector would be read against v5's sixteen weights, which is worse than having no model at all —
 * so an old artifact falls back to the heuristic, which is at least self-consistent.
 */

export const MODEL_VERSION = 5;

/** Balls in one innings, by format. Test is absent on purpose — it has no fixed budget. */
export const FORMAT_INNINGS_BALLS = {
  T20: 120,
  ODI: 300,
  T10: 60,
  HUNDRED: 100,
};

/**
 * Par run rate per format — the rate a side sustains across a full innings with wickets intact.
 *
 * Every learned rate feature is expressed relative to this, so one model spans T20, ODI and The
 * Hundred without the 8.3-vs-5.6 difference swamping the position. Must equal `PAR_RUN_RATE` in
 * train-win-probability's `training.js`; the artifact also ships its own copy as `parRunRate`, and
 * that copy wins when present so a retrain can move these without a matching deploy here.
 */
export const FORMAT_PAR_RUN_RATE = {
  T20: 8.3,
  ODI: 5.6,
  T10: 10.5,
  HUNDRED: 8.6,
};

/** Par run rate for a Test. Only the long-format features use it. */
const TEST_PAR_RUN_RATE = 3.2;

/** Scheduled balls in a day of long-format cricket: 90 overs. */
const BALLS_PER_LONG_DAY = 90 * 6;

// Longest / most specific tokens first, so "T20I" does not fall through to a broader rule and
// "The Hundred" is not read as a century. Mirrors CricketConstants.FORMAT_PATTERNS on device.
const FORMAT_PATTERNS = [
  [/\b(test|first[- ]class|4[- ]day|5[- ]day)\b/i, "TEST"],
  [/\b(the\s+hundred|hundred|100[- ]ball)\b/i, "HUNDRED"],
  [/\bt10\b/i, "T10"],
  [/\b(t20|twenty20|t20i)\b/i, "T20"],
  [/\b(odi|one[- ]day|50[- ]over|list\s*a)\b/i, "ODI"],
];

/**
 * Cricbuzz's own `matchFormat` field carries a short code, not prose: a Hundred game reports
 * `"HUN"`, a domestic 50-over game `"LIST_A"`. None of those reach [FORMAT_PATTERNS] — `HUN` is
 * not the word "hundred" and `LIST_A` has an underscore where `\blist\s*a\b` wants a space — so
 * every Hundred match resolved to UNKNOWN, which gives no ball budget, which makes `evaluate`
 * return UNDETERMINED for every ball of the match. Measured on the 2026-07-30 Hundred games: no
 * win probability was ever produced.
 *
 * Matched EXACTLY, against one whole field value, and before the loose patterns. Folding these
 * into the joined haystack instead would be a regression: `HUN` is also Hungary's team code, and
 * the loose rules test HUNDRED before T20, so a Hungary T20 would be scored on 100 balls.
 */
const FORMAT_CODES = {
  TEST: "TEST",
  FC: "TEST",
  ODI: "ODI",
  LIST_A: "ODI",
  T20: "T20",
  T20I: "T20",
  T10: "T10",
  HUN: "HUNDRED",
};

/**
 * Resolve a format from free text, returning "UNKNOWN" rather than guessing.
 *
 * Guessing is the bug this replaces: `contains("ODI") -> 300; contains("T20") -> 120; else -> 120`
 * gave a Test, a Hundred game and a rain-shortened match the same 120-ball budget.
 */
export function formatFromText(...texts) {
  const values = texts.filter((t) => typeof t === "string" && t);
  for (const value of values) {
    const code = FORMAT_CODES[value.trim().toUpperCase()];
    if (code) return code;
  }
  const haystack = values.join(" ");
  if (!haystack.trim()) return "UNKNOWN";
  for (const [pattern, format] of FORMAT_PATTERNS) {
    if (pattern.test(haystack)) return format;
  }
  return "UNKNOWN";
}

/** Innings ball budget for a format, or null when there is none (Test, unknown). */
export function inningsBalls(format) {
  return FORMAT_INNINGS_BALLS[format] ?? null;
}

/** Par run rate for a format, or null when there is none. */
export function parRunRate(format, model = null) {
  return model?.parRunRate?.[format] ?? FORMAT_PAR_RUN_RATE[format] ?? null;
}

export function sigmoid(x) {
  return 1 / (1 + Math.exp(-x));
}

/**
 * Softmax over class logits, shifted by the max before exponentiating.
 *
 * The shift is not cosmetic: a long-format lead of several hundred runs drives a logit large enough
 * that `Math.exp` overflows to Infinity, and Infinity/Infinity is NaN.
 */
export function softmax(logits) {
  const max = Math.max(...logits);
  const exps = logits.map((l) => Math.exp(l - max));
  const sum = exps.reduce((a, b) => a + b, 0) || 1;
  return exps.map((e) => e / sum);
}

/** Parse cricket over notation ("19.5", "19.5 Ov") into balls. The fraction is balls, not tenths. */
export function ballsFromOvers(overs) {
  if (overs == null) return null;
  const match = String(overs).match(/(\d+)(?:\.(\d))?/);
  if (!match) return null;
  const completed = Number.parseInt(match[1], 10);
  if (!Number.isFinite(completed)) return null;
  const balls = match[2] ? Number.parseInt(match[2], 10) : 0;
  if (balls >= 6) return null; // not valid over notation
  return completed * 6 + balls;
}

/**
 * Balls left in the current innings.
 *
 * Prefers the source's own figure (Cricbuzz `oversRem`), which already accounts for a
 * reduced-overs match. Falls back to the format budget minus balls bowled, because `oversRem` is
 * frequently absent even on a perfectly scoreable chase — observed missing on a live T20 whose own
 * status line read "need 100 runs in 95 balls". Returns null when neither source can answer, and
 * the caller must then report undetermined rather than substituting a guess.
 */
export function ballsRemainingFor({ oversRemaining = null, ballsBowled = null, format = "UNKNOWN", totalBalls = null } = {}) {
  if (Number.isFinite(oversRemaining) && oversRemaining > 0) return Math.round(oversRemaining * 6);
  const budget = Number.isFinite(totalBalls) && totalBalls > 0 ? totalBalls : inningsBalls(format);
  if (budget == null || !Number.isFinite(ballsBowled)) return null;
  const remaining = budget - ballsBowled;
  return remaining > 0 ? remaining : 0;
}

const PROB_FLOOR = 0.03;
const PROB_CEILING = 0.97;

/** An explicit "we cannot score this" result. Callers must render nothing, not 50%. */
export const UNDETERMINED = Object.freeze({
  undetermined: true,
  battingTeamWinProbability: null,
  drawProbability: null,
  confidence: 0,
});

function finalize(rawProb) {
  const clamped = Math.min(PROB_CEILING, Math.max(PROB_FLOOR, rawProb));
  return {
    undetermined: false,
    battingTeamWinProbability: clamped,
    drawProbability: null,
    confidence: Math.abs(clamped - 0.5) * 2,
  };
}

// ── team strength ────────────────────────────────────────────────────────────

/**
 * Team stats are keyed `"name|gender|format"`.
 *
 * A bare team name pooled Australia's men's and women's sides and their Tests, ODIs and T20s into
 * one win rate — 0.642, against a men's-T20 0.545 and a women's-ODI 0.842. The key is only useful
 * if the reader builds it exactly as the trainer did, which `feature-parity.test.js` pins.
 */
export function teamKey(team, gender, format) {
  return `${String(team ?? "").trim()}|${gender ?? "male"}|${format ?? "T20"}`;
}

function winRateOf(teamStats, team, gender, format) {
  return teamStats?.[teamKey(team, gender, format)]?.winRate ?? 0.5;
}

// ── feature builders (must mirror training.js exactly) ───────────────────────

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

/**
 * Blend model output toward the class base rates while a Test is too young to read.
 *
 * A logistic model is linear in its features, so at the edge of the training data it extrapolates
 * with unearned confidence, and the first over of day one is exactly that edge — it returned 73%
 * before anything had happened, against a 39% base rate. There is genuinely no information there,
 * so the honest output is the prior.
 */
export function shrinkToPrior(probs, priors, progress, settle) {
  const safeSettle = settle > 0 ? settle : 1;
  const weight = Math.max(0, Math.min(1, (Number(progress) || 0) / safeSettle));
  const fallback = 1 / probs.length;
  return probs.map((p, i) => weight * p + (1 - weight) * (priors?.[i] ?? fallback));
}

// ── learned-model plumbing ───────────────────────────────────────────────────

function usable(sub, expectedFeatures) {
  return (
    sub != null &&
    Array.isArray(sub.weights) &&
    sub.weights.length === expectedFeatures &&
    Array.isArray(sub.featureMeans) &&
    Array.isArray(sub.featureStds)
  );
}

/**
 * A multiclass sub-model has one weight ROW per class, so its outer length is the class count and
 * the feature count lives one level down. Checking it the same way as a binary model would read
 * `weights.length === 16` against a 3, and refuse every valid Test artifact.
 */
function usableClasses(sub, expectedFeatures, classes) {
  return (
    sub != null &&
    Array.isArray(sub.weights) &&
    sub.weights.length === classes &&
    sub.weights.every((row) => Array.isArray(row) && row.length === expectedFeatures) &&
    Array.isArray(sub.biases) &&
    sub.biases.length === classes &&
    Array.isArray(sub.featureMeans) &&
    Array.isArray(sub.featureStds)
  );
}

function scoreBinary(sub, features) {
  let logit = sub.bias ?? 0;
  for (let i = 0; i < sub.weights.length; i++) {
    const scaled = (features[i] - (sub.featureMeans[i] ?? 0)) / (sub.featureStds[i] || 1);
    logit += sub.weights[i] * scaled;
  }
  // Temperature is fitted on a validation window at training time; 1 means "no correction needed",
  // which is also the right behaviour for an artifact that predates the field.
  return sigmoid(logit / (sub.temperature > 0 ? sub.temperature : 1));
}

function scoreClasses(sub, features) {
  const logits = sub.biases.map((bias, c) => {
    let logit = bias;
    for (let j = 0; j < features.length; j++) {
      const scaled = (features[j] - (sub.featureMeans[j] ?? 0)) / (sub.featureStds[j] || 1);
      logit += sub.weights[c][j] * scaled;
    }
    return logit;
  });
  return softmax(logits);
}

function learnedOf(model) {
  return model != null && model.version === MODEL_VERSION ? model : null;
}

// ── heuristic ────────────────────────────────────────────────────────────────

/**
 * Chase logit from the heuristic.
 *
 * Composed in logit space and squashed once, so terms cannot saturate the result and no inner clamp
 * is needed. Three corrections to the formula this replaces:
 *
 *  - Overs remaining is an URGENCY MULTIPLIER on the run-rate gap, not a subtracted term. The old
 *    `- min(oversRemaining/20, 1) * 0.1` meant more overs left lowered the batting side's
 *    probability monotonically, which is backwards. What actually happens is that a given run-rate
 *    gap matters more as the innings runs out.
 *  - The wickets term is centred on five wickets, so losing wickets is a penalty. The old
 *    `(wicketsInHand/10) * 0.3` was a bonus that never went negative.
 *  - Team strength enters the LOGIT. The old code added it to a probability after the sigmoid,
 *    giving a pre-clamp range of [-0.1, 1.1] that the clamp merely hid.
 */
function chaseLogit({ currentRunRate, requiredRunRate, wicketsInHand, ballsRemaining, ballsBudget, parRate }) {
  const gap = currentRunRate / Math.max(requiredRunRate, 0.1) - 1.0;
  const ballsFraction = ballsBudget ? Math.min(1, ballsRemaining / ballsBudget) : 1;
  const urgency = 1 / (0.35 + 0.65 * ballsFraction);

  let logit = gap * 3.0 * urgency;
  logit += (wicketsInHand / 10 - 0.5) * 1.2;

  // Feasibility: a required rate far above anything ever sustained is close to lost regardless of
  // the ratio above. Continuous, so there is no discontinuity at a threshold rate — the old code
  // applied its pressure penalty only above exactly 9.00 RRR.
  if (parRate) {
    const ceilingRate = parRate * 2.2;
    logit -= Math.max(0, requiredRunRate / ceilingRate - 1) * 4.0;
  }
  return logit;
}

function firstInningsLogit({ currentRunRate, wicketsInHand, parRate }) {
  const breakeven = parRate ?? 8.0;
  return (currentRunRate - breakeven) * 0.5 + (wicketsInHand / 10 - 0.5) * 1.5;
}

// ── entry points ─────────────────────────────────────────────────────────────

/**
 * Win probability for the BATTING side of a limited-overs match.
 *
 * Always batting-team-oriented. The caller owns orientation and must carry battingTeamIndex — the
 * old response field was named `team1WinProbability` while being computed from the batting team,
 * so the number was inverted whenever team 2 was at the crease.
 *
 * @returns {{undetermined: boolean, battingTeamWinProbability: number|null, drawProbability: number|null, confidence: number}}
 */
export function evaluate({
  runsNeeded = 0,
  runsScored = 0,
  ballsRemaining = 0,
  wicketsLost = 0,
  currentRunRate = 0,
  requiredRunRate = 0,
  battingTeam = "",
  bowlingTeam = "",
  gender = "male",
  format = "UNKNOWN",
  totalBalls = null,
  model = null,
} = {}) {
  if (!Number.isFinite(ballsRemaining) || ballsRemaining <= 0) return UNDETERMINED;

  // A format with no ball budget cannot be scored by this model. Returning UNDETERMINED rather
  // than assuming 120 is the whole point: a Test past ball 120 previously computed
  // ballsRemaining = 1 and carried a permanent late-innings penalty. Tests go through
  // [evaluateLong] instead.
  const ballsBudget = Number.isFinite(totalBalls) && totalBalls > 0 ? totalBalls : inningsBalls(format);
  if (!ballsBudget) return UNDETERMINED;

  const wicketsInHand = Math.max(0, 10 - wicketsLost);
  if (wicketsInHand === 0) return finalize(PROB_FLOOR);

  const learned = learnedOf(model);
  const par = parRunRate(format, learned) ?? 8.0;
  const teamStats = learned?.teamStats ?? (model?.teamStats ?? model ?? {});
  const teamStrengthDiff =
    winRateOf(teamStats, battingTeam, gender, format) - winRateOf(teamStats, bowlingTeam, gender, format);
  const isChase = requiredRunRate > 0;

  if (learned) {
    if (isChase && usable(learned.chase, 16)) {
      return finalize(scoreBinary(learned.chase, chaseFeatures({
        currentRunRate,
        requiredRunRate,
        wicketsInHand,
        ballsRemaining,
        ballsBudget,
        runsNeeded,
        target: runsScored + runsNeeded,
        teamStrengthDiff,
        parRunRate: par,
        format,
      })));
    }
    if (!isChase && usable(learned.firstInnings, 11)) {
      return finalize(scoreBinary(learned.firstInnings, firstInningsFeatures({
        runsScored,
        currentRunRate,
        wicketsInHand,
        ballsRemaining,
        ballsBudget,
        teamStrengthDiff,
        parRunRate: par,
        format,
      })));
    }
  }

  // Heuristic path — taken when the model is missing, stale, or malformed.
  const base = isChase
    ? chaseLogit({ currentRunRate, requiredRunRate, wicketsInHand, ballsRemaining, ballsBudget, parRate: par })
    : firstInningsLogit({ currentRunRate, wicketsInHand, parRate: par });

  return finalize(sigmoid(base + teamStrengthDiff * 0.8));
}

/**
 * Win, draw and loss probabilities for the BATTING side of a Test.
 *
 * Separate entry point rather than a branch inside [evaluate] because the answer has a different
 * shape: a Test position is three outcomes, and squeezing it into one number would have to throw
 * the draw away — which for roughly a quarter of Test positions is the most likely result.
 *
 * There is no heuristic fallback. Without the learned model there is nothing honest to say about a
 * Test position, and a made-up three-way split would render as a confident-looking bar.
 *
 * @returns the same shape as [evaluate], with `drawProbability` populated.
 */
export function evaluateLong({
  lead = 0,
  wicketsLost = 0,
  inningsIndex = 0,
  ballsBowled = 0,
  scheduledDays = 5,
  inningsRunRate = 0,
  ownInningsUsed = 1,
  oppInningsUsed = 0,
  followedOn = false,
  battingTeam = "",
  bowlingTeam = "",
  gender = "male",
  model = null,
} = {}) {
  const learned = learnedOf(model);
  if (!usableClasses(learned?.long, 16, 3)) return UNDETERMINED;

  const ballsTotal = Math.max(1, scheduledDays) * BALLS_PER_LONG_DAY;
  const ballsRemaining = ballsTotal - ballsBowled;
  if (!Number.isFinite(ballsRemaining) || ballsRemaining <= 0) return UNDETERMINED;

  const wicketsInHand = Math.max(0, 10 - wicketsLost);
  if (wicketsInHand === 0) return UNDETERMINED;

  const isFourthInnings = inningsIndex === 3;
  const teamStats = learned.teamStats ?? {};
  const teamStrengthDiff =
    winRateOf(teamStats, battingTeam, gender, "TEST") - winRateOf(teamStats, bowlingTeam, gender, "TEST");

  const probs = scoreClasses(learned.long, longFeatures({
    lead,
    wicketsInHand,
    inningsIndex,
    ballsRemaining,
    ballsTotal,
    teamStrengthDiff,
    isFourthInnings,
    // In the fourth innings the target is the lead inverted: erase the deficit and go one past it.
    runsNeeded: isFourthInnings ? Math.max(1 - lead, 0) : 0,
    followedOn,
    inningsUsedDiff: ownInningsUsed - oppInningsUsed,
    inningsRunRate,
    // What the bowling side must still do to win: take this innings out, and a whole further
    // innings if the batting side has not yet used both of theirs.
    wicketsToTake: wicketsInHand + (ownInningsUsed < 2 ? 10 : 0),
    scheduledDays,
  }));

  const settled = shrinkToPrior(
    probs,
    learned.long.classPriors,
    1 - ballsRemaining / ballsTotal,
    learned.long.priorSettleProgress ?? 0.25
  );

  // OUTCOME order in training.js is LOSS, DRAW, WIN.
  const win = settled[2];
  const draw = settled[1];
  return {
    undetermined: false,
    battingTeamWinProbability: win,
    drawProbability: draw,
    confidence: Math.max(win, draw, settled[0]),
  };
}
