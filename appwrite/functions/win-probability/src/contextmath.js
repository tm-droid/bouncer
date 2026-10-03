/**
 * The arithmetic that turns what is known about two sides, a ground and two elevens into the
 * context terms of the win-probability model.
 *
 * The trainer computes these as of each historical match; match-sync computes them for a live one
 * from the tables the trainer publishes. A term that is shrunk, scaled or ordered differently on
 * the two sides is not an error anyone would see — it is a model reading a number that means
 * something else — so there is exactly one implementation, vendored byte-identically into
 * train-win-probability, match-sync and win-probability. `ContextMathVendorParityTest` fails the
 * build if the copies drift.
 */

export const ELO_START = 1500;
export const ELO_SCALE = 400;

/** Pseudo-counts that pull a thin record toward the format's mean. */
export const PRIOR = Object.freeze({
  h2hMatches: 4,
  venueMatches: 5,
  batDismissals: 5,
  batBalls: 80,
  recentDismissals: 2,
  recentBalls: 30,
  bowlBalls: 120,
});

/** The batters and bowlers an eleven's strength is read from. */
export const XI_BATTERS = 7;
export const XI_BOWLERS = 5;
/** Players of an eleven that must be known before its strength is used at all. */
export const XI_MIN_KNOWN = 7;
/** A ground counts as known once this many matches have been played on it. */
export const VENUE_MIN_MATCHES = 3;

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

/** Lowercase, accents folded, punctuation dropped — how every name is compared and keyed. */
export function squash(text) {
  return String(text ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9 ]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * A ground's name as a key. Cricsheet writes the same ground as "Eden Gardens" and as
 * "Eden Gardens, Kolkata", so the name is cut at its first comma.
 */
export function venueName(venue) {
  return squash(String(venue ?? "").split(",")[0]);
}

/**
 * One key per ground. The city stays in it because the name alone is not unique — "County
 * Ground" is a dozen different grounds.
 */
export function venueKey(venue, city) {
  const name = venueName(venue);
  return name ? `${name}@${squash(city)}` : null;
}

/** The key a pair of sides' head-to-head record is stored under, whichever order they arrive in. */
export function pairKey(teamA, teamB, gender, format) {
  const [a, b] = [teamA, teamB].sort();
  return `${a}|||${b}|${gender}|${format}`;
}

/** How many context shard files the published tables are spread over. */
export const SHARDS = 64;

/**
 * The shard a key lives in, as two hex digits. FNV-1a, because the trainer that writes the shards
 * and the reader that fetches one must agree without sharing a process.
 */
export function shardOf(key, shards = SHARDS) {
  let h = 0x811c9dc5;
  const text = String(key);
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return (h % shards).toString(16).padStart(2, "0");
}

/**
 * A player's standing relative to the format: 1.0 is the format's mean, and a player with no
 * record is exactly 1.0. `recent` is the same quantity over a decaying window of innings.
 *
 * @param p      career and recent counts, or null for a player with no record
 * @param means  the format's own means (`runsPerBall`, `runsPerOut`, `bowlRunsPerBall`, `wicketsPerBall`)
 */
export function playerRating(p, means) {
  if (!p) return { avg: 1, strike: 1, recent: 1, bowl: 1, batBalls: 0, bowlBalls: 0 };
  const avg = ((p.runs + PRIOR.batDismissals * means.runsPerOut) / (p.outs + PRIOR.batDismissals)) / means.runsPerOut;
  const strike = ((p.runs + PRIOR.batBalls * means.runsPerBall) / (p.balls + PRIOR.batBalls)) / means.runsPerBall;
  const rAvg = ((p.rRuns + PRIOR.recentDismissals * means.runsPerOut) / (p.rOuts + PRIOR.recentDismissals)) / means.runsPerOut;
  const rStrike = ((p.rRuns + PRIOR.recentBalls * means.runsPerBall) / (p.rBalls + PRIOR.recentBalls)) / means.runsPerBall;
  const conceded = (p.bowlRuns + PRIOR.bowlBalls * means.bowlRunsPerBall) / (p.bowlBalls + PRIOR.bowlBalls);
  const striking = (p.wickets + PRIOR.bowlBalls * means.wicketsPerBall) / (p.bowlBalls + PRIOR.bowlBalls);
  return {
    avg,
    strike,
    recent: (rAvg + rStrike) / 2,
    // Above 1 is a better bowler: cheaper than the format, and striking more often.
    bowl: (means.bowlRunsPerBall / conceded + striking / means.wicketsPerBall) / 2,
    batBalls: p.balls,
    bowlBalls: p.bowlBalls,
  };
}

/** The rating of a player nobody could identify — the format's mean, with no balls behind it. */
export const UNKNOWN_RATING = Object.freeze({ avg: 1, strike: 1, recent: 1, bowl: 1, batBalls: 0, bowlBalls: 0 });

/**
 * An eleven's batting and bowling strength, each centred on 0 = the format's mean.
 *
 * Batting is the seven best averages; bowling is the five who have bowled most, because an
 * eleven's attack is the players who actually bowl, not its eleven best economy rates.
 */
export function xiStrength(ratings) {
  if (!ratings || ratings.length === 0) return { batAvg: 0, batStrike: 0, batRecent: 0, bowl: 0, coverage: 0 };
  const batters = [...ratings].sort((a, b) => b.avg - a.avg).slice(0, XI_BATTERS);
  const bowlers = ratings.filter((r) => r.bowlBalls > 0).sort((a, b) => b.bowlBalls - a.bowlBalls).slice(0, XI_BOWLERS);
  const mean = (list, f) => (list.length ? list.reduce((s, r) => s + f(r), 0) / list.length : 0);
  return {
    batAvg: mean(batters, (r) => r.avg - 1),
    batStrike: mean(batters, (r) => r.strike - 1),
    batRecent: mean(batters, (r) => r.recent - 1),
    bowl: mean(bowlers, (r) => r.bowl - 1),
    coverage: mean(ratings, (r) => (r.batBalls >= 30 || r.bowlBalls >= 60 ? 1 : 0)),
  };
}

/**
 * How far one side leads the head-to-head, shrunk toward even. Range −0.5..0.5, and exactly the
 * negative when read from the other side — which is what lets a record stored once be served to
 * whichever side is batting.
 */
export function h2hAdvantage(ownWins, oppWins, decided) {
  return ((ownWins ?? 0) - (oppWins ?? 0)) / (2 * ((decided ?? 0) + PRIOR.h2hMatches));
}

/**
 * A ground against its format.
 *
 * @param v      counts for the ground (`matches`, `firstRatioSum`, `firstRatioN`, `chases`, `chaseWins`, `draws`), or null
 * @param means  the format's own means (`firstRatio`, `chaseWin`, `draw`)
 */
export function venueView(v, means) {
  const n = PRIOR.venueMatches;
  return {
    matches: v?.matches ?? 0,
    // First-innings scoring here over first-innings scoring everywhere; 1.0 is a typical ground.
    scoring: ((v?.firstRatioSum ?? 0) + n * means.firstRatio) / ((v?.firstRatioN ?? 0) + n) / means.firstRatio,
    chaseAdvantage: ((v?.chaseWins ?? 0) + n * means.chaseWin) / ((v?.chases ?? 0) + n) - means.chaseWin,
    drawAdvantage: ((v?.draws ?? 0) + n * means.draw) / ((v?.matches ?? 0) + n) - means.draw,
  };
}

/**
 * How much more one side has played on this ground than the other, as a log ratio in −1..1.
 * Cricsheet records no home side; a side that has played here forty times against a side that
 * has played here twice is what home advantage looks like in the data.
 */
export function familiarityDiff(ownVisits, oppVisits) {
  return clamp(Math.log(((ownVisits ?? 0) + 1) / ((oppVisits ?? 0) + 1)) / 3, -1, 1);
}

/**
 * Context for one side against the other — the object the feature builders read.
 *
 * @param own, opp   `{ elo, form, winRate, visits, xi }` where `xi` is an `xiStrength` or null
 * @param h2hOwn     `h2hAdvantage` already oriented to `own`
 * @param venue      a `venueView`
 */
export function sideContext({ own, opp, h2hOwn = 0, venue }) {
  return {
    eloDiff: ((own?.elo ?? ELO_START) - (opp?.elo ?? ELO_START)) / ELO_SCALE,
    formDiff: (own?.form ?? 0.5) - (opp?.form ?? 0.5),
    h2hAdvantage: h2hOwn,
    winRateDiff: (own?.winRate ?? 0.5) - (opp?.winRate ?? 0.5),
    venueScoring: venue?.scoring ?? 1,
    venueChaseAdvantage: venue?.chaseAdvantage ?? 0,
    venueDrawAdvantage: venue?.drawAdvantage ?? 0,
    familiarityDiff: familiarityDiff(own?.visits, opp?.visits),
    hasVenue: (venue?.matches ?? 0) >= VENUE_MIN_MATCHES,
    xi: own?.xi && opp?.xi ? { own: own.xi, opp: opp.xi } : null,
  };
}

/** The context of a match about which nothing is known. Every term is the neutral value. */
export const NEUTRAL_CONTEXT = Object.freeze(sideContext({ own: null, opp: null, venue: null }));

/** Where the retired all-time strength term sits in each base vector; the context models drop it. */
const STRENGTH_INDEX = Object.freeze({ chase: 5, firstInnings: 6, prematch: 0 });

const TEAM_NAMES = ["eloDiff", "formDiff", "h2hAdvantage", "winRateDiff"];
const VENUE_NAMES = {
  chase: ["venueScoring", "venueChaseAdvantage", "targetVsVenue", "familiarityDiff", "hasVenue"],
  firstInnings: ["venueScoring", "venueChaseAdvantage", "projectedVsVenue", "familiarityDiff", "hasVenue"],
  prematch: ["familiarityDiff", "venueScoring", "hasVenue"],
};
const XI_NAMES = {
  chase: ["batAvg", "batStrike", "batRecent", "oppBowl", "xiCoverage"],
  firstInnings: ["batAvg", "batStrike", "batRecent", "oppBowl", "oppBatAvg", "oppBatStrike", "ownBowl", "xiCoverage"],
  prematch: ["batAvgDiff", "batStrikeDiff", "batRecentDiff", "bowlDiff", "xiCoverage"],
};

/** The names of a context vector, in order. `baseNames` are the head's base feature names. */
export function contextFeatureNames(head, baseNames, withXi) {
  return [
    ...baseNames.filter((_, i) => i !== STRENGTH_INDEX[head]),
    ...TEAM_NAMES,
    ...VENUE_NAMES[head],
    ...(withXi ? XI_NAMES[head] : []),
  ];
}

/**
 * A head's full feature vector: its base features without the retired strength term, then the
 * team, ground and (when both elevens are known) player terms.
 *
 * @param head   "chase" | "firstInnings" | "prematch"
 * @param base   the head's base vector, exactly as its own builder returns it
 * @param s      the state that base vector was built from (`target`, `runsScored`, …)
 * @param c      a `sideContext`
 */
export function contextFeatures(head, base, s, c, withXi) {
  const out = base.filter((_, i) => i !== STRENGTH_INDEX[head]);
  out.push(c.eloDiff, c.formDiff, c.h2hAdvantage, c.winRateDiff);
  const scoring = c.venueScoring > 0 ? c.venueScoring : 1;
  const known = c.hasVenue ? 1 : 0;
  if (head === "prematch") {
    out.push(c.familiarityDiff, scoring - 1, known);
  } else {
    const budget = Math.max(s.ballsBudget, 1);
    const parTotal = (s.parRunRate * budget) / 6;
    let total;
    if (head === "chase") {
      total = s.target / parTotal;
    } else {
      const ballsLeft = Math.max(s.ballsRemaining, 0);
      const wf = s.wicketsInHand / 10;
      total = (s.runsScored + s.currentRunRate * (ballsLeft / 6) * (0.55 + 0.45 * wf)) / parTotal;
    }
    // The same total read against this ground rather than against the format: 180 is a different
    // target at a ground where sides make 200 than at one where they make 150.
    out.push(scoring - 1, c.venueChaseAdvantage, total / scoring - total, c.familiarityDiff, known);
  }
  if (withXi) {
    const { own, opp } = c.xi;
    const coverage = Math.min(own.coverage, opp.coverage);
    if (head === "chase") out.push(own.batAvg, own.batStrike, own.batRecent, opp.bowl, coverage);
    else if (head === "firstInnings") out.push(own.batAvg, own.batStrike, own.batRecent, opp.bowl, opp.batAvg, opp.batStrike, own.bowl, coverage);
    else out.push(own.batAvg - opp.batAvg, own.batStrike - opp.batStrike, own.batRecent - opp.batRecent, own.bowl - opp.bowl, coverage);
  }
  return out;
}
