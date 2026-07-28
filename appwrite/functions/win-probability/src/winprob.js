/**
 * The single win-probability implementation.
 *
 * This module is the authority: the device computes nothing. It is duplicated byte-for-byte into
 * every function that needs it (win-probability, poll-live-matches, match-sync) because Appwrite
 * deploys each function as an isolated archive with no shared package. If you edit this file, copy
 * it to the others — winprob.test.js asserts the copies agree.
 *
 * Model contract v3. A v2 artifact is deliberately NOT accepted on the learned path: v2 was trained
 * with a hardcoded 120-ball denominator, so its feature scaling is only valid for T20 and silently
 * wrong for every other format. Reading a v2 model falls back to the heuristic instead.
 */

export const MODEL_VERSION = 3;

/** Balls in one innings, by format. Test is absent on purpose — it has no fixed budget. */
export const FORMAT_INNINGS_BALLS = {
  T20: 120,
  ODI: 300,
  T10: 60,
  HUNDRED: 100,
};

/** Par run rate per format — the rate a side sustains across a full innings with wickets intact. */
export const FORMAT_PAR_RUN_RATE = {
  T20: 8.3,
  ODI: 5.6,
  T10: 10.5,
  HUNDRED: 8.6,
};

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
 * Resolve a format from free text, returning "UNKNOWN" rather than guessing.
 *
 * Guessing is the bug this replaces: `contains("ODI") -> 300; contains("T20") -> 120; else -> 120`
 * gave a Test, a Hundred game and a rain-shortened match the same 120-ball budget.
 */
export function formatFromText(...texts) {
  const haystack = texts.filter((t) => typeof t === "string" && t).join(" ");
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
export function parRunRate(format) {
  return FORMAT_PAR_RUN_RATE[format] ?? null;
}

export function sigmoid(x) {
  return 1 / (1 + Math.exp(-x));
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
  confidence: 0,
});

function finalize(rawProb) {
  const clamped = Math.min(PROB_CEILING, Math.max(PROB_FLOOR, rawProb));
  return {
    undetermined: false,
    battingTeamWinProbability: clamped,
    confidence: Math.abs(clamped - 0.5) * 2,
  };
}

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

/**
 * Win probability for the BATTING side.
 *
 * Always batting-team-oriented. The caller owns orientation and must carry battingTeamIndex — the
 * old response field was named `team1WinProbability` while being computed from the batting team,
 * so the number was inverted whenever team 2 was at the crease.
 *
 * @returns {{undetermined: boolean, battingTeamWinProbability: number|null, confidence: number}}
 */
export function evaluate({
  runsNeeded = 0,
  ballsRemaining = 0,
  wicketsLost = 0,
  currentRunRate = 0,
  requiredRunRate = 0,
  battingTeam = "",
  bowlingTeam = "",
  format = "UNKNOWN",
  totalBalls = null,
  model = null,
} = {}) {
  if (!Number.isFinite(ballsRemaining) || ballsRemaining <= 0) return UNDETERMINED;

  // A format with no ball budget cannot be scored by this model. Returning UNDETERMINED rather
  // than assuming 120 is the whole point: a Test past ball 120 previously computed
  // ballsRemaining = 1 and carried a permanent late-innings penalty.
  const ballsBudget = Number.isFinite(totalBalls) && totalBalls > 0 ? totalBalls : inningsBalls(format);
  if (!ballsBudget) return UNDETERMINED;

  const wicketsInHand = Math.max(0, 10 - wicketsLost);
  if (wicketsInHand === 0) return finalize(PROB_FLOOR);

  const parRate = parRunRate(format);
  const isChase = requiredRunRate > 0;
  const useLearned = model != null && model.version === MODEL_VERSION && Array.isArray(model.weights);

  if (useLearned && isChase) {
    const teamStats = model.teamStats ?? {};
    const features = [
      currentRunRate / Math.max(requiredRunRate, 0.1),
      wicketsInHand / 10,
      // Format-correct, not a hardcoded /120. An ODI with 180 balls left used to yield 1.5 here,
      // far outside the range the model ever saw.
      ballsRemaining / ballsBudget,
      runsNeeded / Math.max(ballsRemaining, 1),
      (teamStats[battingTeam]?.winRate ?? 0.5) - (teamStats[bowlingTeam]?.winRate ?? 0.5),
    ];
    const means = model.featureMeans ?? [];
    const stds = model.featureStds ?? [];
    let logit = model.bias ?? 0;
    for (let i = 0; i < model.weights.length; i++) {
      const scaled = (features[i] - (means[i] ?? 0)) / (stds[i] || 1);
      logit += model.weights[i] * scaled;
    }
    return finalize(sigmoid(logit));
  }

  // Heuristic path — also taken when the model is missing, stale (v2), or this is a first innings.
  const teamStats = useLearned ? (model.teamStats ?? {}) : (model ?? {});
  const strengthDiff =
    (teamStats[battingTeam]?.winRate ?? 0.5) - (teamStats[bowlingTeam]?.winRate ?? 0.5);

  const base = isChase
    ? chaseLogit({ currentRunRate, requiredRunRate, wicketsInHand, ballsRemaining, ballsBudget, parRate })
    : firstInningsLogit({ currentRunRate, wicketsInHand, parRate });

  return finalize(sigmoid(base + strengthDiff * 0.8));
}
