import test from "node:test";
import assert from "node:assert/strict";
import { MODEL_VERSION, evaluate, evaluateLong, evaluatePrematch, longFeatures, formatFromText, ballsRemainingFor, inningsBalls } from "../src/winprob.js";
import { resolveMatchTeams } from "../src/teamnames.js";
import { orientToTeam1 } from "../src/main.js";
import { liveSituation } from "../src/cricbuzz.js";
import { sideContextFor } from "../src/context.js";


test("balls remaining falls back to the format budget when oversRem is absent", () => {
  // Observed on a live T20 whose own status line read "need 100 runs in 95 balls" while the
  // miniscore carried no oversRem at all. Without the fallback the match scored as undetermined.
  assert.equal(
    ballsRemainingFor({ oversRemaining: null, ballsBowled: 25, format: "T20" }),
    95,
  );
  assert.equal(
    ballsRemainingFor({ oversRemaining: 16, ballsBowled: 24, format: "T20" }),
    96,
    "an explicit oversRem wins — it already accounts for a reduced-overs match",
  );
  assert.equal(
    ballsRemainingFor({ oversRemaining: null, ballsBowled: 258, format: "TEST" }),
    null,
    "a Test has no budget to subtract from",
  );
  assert.equal(ballsRemainingFor({ oversRemaining: null, ballsBowled: null, format: "T20" }), null);
  assert.equal(
    ballsRemainingFor({ oversRemaining: null, ballsBowled: 130, format: "T20" }),
    0,
    "an over-long innings clamps at zero rather than going negative",
  );
});

// The evaluate() half of winProbFor, ending in main.js's own `orientToTeam1`. This helper used to
// re-implement that last step, so the suite stayed green while the real one handed a Test's whole
// draw to the bowling side — a mirrored rule is not a pinned rule.
function orient(situation, { model = null, title = "" } = {}) {
  const format = formatFromText(situation.matchFormat, title);
  const ballsRemaining = ballsRemainingFor({
    oversRemaining: situation.oversRemaining,
    ballsBowled: situation.ballsBowled,
    format,
  });
  const r = evaluate({
    runsNeeded: situation.runsNeeded ?? 0,
    runsScored: situation.runsScored ?? 0,
    ballsRemaining: ballsRemaining ?? 0,
    wicketsLost: situation.wicketsLost ?? 0,
    currentRunRate: situation.currentRunRate ?? 0,
    requiredRunRate: situation.requiredRunRate ?? 0,
    battingTeam: situation.battingTeamName ?? "",
    bowlingTeam: situation.bowlingTeamName ?? "",
    gender: "male",
    format,
    model,
  });
  if (r.undetermined) return null;
  return orientToTeam1({
    battingTeamWinProbability: r.battingTeamWinProbability,
    drawProbability: r.drawProbability,
    battingTeamIsTeam1: situation.battingTeamIsTeam1,
  });
}

/**
 * A structurally valid v5 artifact whose every weight is zero.
 *
 * Zero weights make the learned path return exactly 0.5, which the heuristic never does for a real
 * situation — so "did the learned path run?" is answerable without asserting on a trained number
 * that a retrain would legitimately move.
 */
function flatModel({ version = 5 } = {}) {
  const flat = (k) => ({
    weights: new Array(k).fill(0),
    bias: 0,
    temperature: 1,
    featureMeans: new Array(k).fill(0),
    featureStds: new Array(k).fill(1),
  });
  return {
    version,
    chase: flat(16),
    firstInnings: flat(11),
    teamStats: {},
  };
}

// Real shape captured from Cricbuzz match 153893 (San Francisco Unicorns v Desert Vipers).
const chaseSituation = {
  inningsId: 2,
  matchFormat: "T20",
  battingTeamName: "DV",
  bowlingTeamName: "SFU",
  battingTeamIsTeam1: false,
  runsScored: 52,
  wicketsLost: 0,
  ballsBowled: 25,
  target: 152,
  runsNeeded: 100,
  oversRemaining: null,
  currentRunRate: 12.48,
  requiredRunRate: 6.32,
};

test("a second-innings probability is inverted to the first-innings side", () => {
  const team1 = orient(chaseSituation);
  assert.ok(team1 != null);
  // The chasing side is well placed, so the side that batted first must be below even.
  assert.ok(team1 < 0.5, `expected team1 below even, got ${team1}`);
});

test("a first-innings probability is not inverted", () => {
  const firstInnings = {
    ...chaseSituation,
    inningsId: 1,
    battingTeamIsTeam1: true,
    battingTeamName: "SFU",
    bowlingTeamName: "DV",
    target: null,
    runsNeeded: null,
    requiredRunRate: 0,
  };
  const team1 = orient(firstInnings);
  assert.ok(team1 != null);
  // Scoring at 12.48 in a T20 first innings is well above par, so team1 must be above even.
  assert.ok(team1 > 0.5, `expected team1 above even, got ${team1}`);
});

test("orientation is symmetric — the two sides sum to one", () => {
  const asTeam1 = orient({ ...chaseSituation, battingTeamIsTeam1: true });
  const asTeam2 = orient({ ...chaseSituation, battingTeamIsTeam1: false });
  assert.ok(Math.abs(asTeam1 + asTeam2 - 1) < 1e-9);
});

test("a Test is not scored by the limited-overs path", () => {
  const testMatch = {
    inningsId: 3,
    matchFormat: "TEST",
    battingTeamName: "WI",
    bowlingTeamName: "PAK",
    battingTeamIsTeam1: false,
    runsScored: 126,
    wicketsLost: 7,
    ballsBowled: 258,
    target: null,
    runsNeeded: null,
    oversRemaining: null,
    currentRunRate: 2.93,
    requiredRunRate: 0,
  };
  assert.equal(orient(testMatch), null);
});

test("a trained v5 model overrides the heuristic", () => {
  const withModel = orient(chaseSituation, { model: flatModel() });
  const heuristic = orient(chaseSituation);
  // All-zero weights and bias produce exactly 0.5 for the batting side, which the heuristic
  // would never return for this situation.
  assert.ok(Math.abs(withModel - 0.5) < 1e-9, `model path not taken: ${withModel}`);
  assert.notEqual(withModel, heuristic);
});

test("a v4 artifact is refused rather than read against v5 weights", () => {
  // v4's chase vector was five features. Feeding it to a sixteen-weight reader would produce a
  // number from whatever happened to line up, which is worse than having no model: it looks like a
  // prediction. The safe reading is the heuristic, which is at least self-consistent.
  const stale = { ...flatModel(), version: 4 };
  assert.equal(orient(chaseSituation, { model: stale }), orient(chaseSituation));
});

test("resolved team names make the strength term contribute", () => {
  // The defect this guards: the miniscore names sides by short code and the model keys them by
  // Cricsheet full name, so `teamStats["IND"]` missed, both sides fell back to 0.5, and
  // teamStrengthDiff was exactly zero on every match ever scored.
  const teamStats = {
    "India|male|T20": { winRate: 0.9 },
    "Zimbabwe|male|T20": { winRate: 0.1 },
  };
  const title = "India vs Zimbabwe, 1st T20I";
  // A tight chase on purpose. chaseSituation is lopsided enough to sit on PROB_CEILING, where the
  // clamp swallows any strength term and the test would pass whether or not the lookup worked.
  const situation = {
    ...chaseSituation,
    battingTeamName: "IND",
    bowlingTeamName: "ZIM",
    wicketsLost: 4,
    ballsBowled: 60,
    oversRemaining: 10,
    runsNeeded: 85,
    currentRunRate: 7.5,
    requiredRunRate: 8.5,
  };

  const rawCodes = orient(situation, { model: teamStats });
  assert.ok(rawCodes > 0.04 && rawCodes < 0.96, `fixture is on a clamp (${rawCodes}); strength cannot show`);

  const resolved = resolveMatchTeams({ title, battingTeamName: "IND", teamStats, format: "T20" });
  assert.deepEqual(resolved, { battingTeam: "India", bowlingTeam: "Zimbabwe", gender: "male" });

  const withNames = orient(
    { ...situation, battingTeamName: resolved.battingTeam, bowlingTeamName: resolved.bowlingTeam },
    { model: teamStats },
  );
  // A 0.9-vs-0.1 gap has to move the number. Equality here means the lookup missed again.
  assert.notEqual(withNames, rawCodes, "strength difference did not reach the logit");
  // The strong side is batting, so resolving must favour it. Orientation is team2 via
  // battingTeamIsTeam1: false, so team1's share must FALL.
  assert.ok(withNames < rawCodes, `expected the strong batting side to gain, got ${withNames} vs ${rawCodes}`);
});

test("a fixture outside the corpus scores exactly as it did before resolution", () => {
  // Surrey vs Kent — the collision case. Resolution declines, so the number must be bit-identical
  // to the pre-change behaviour rather than picking up Suriname's and Kenya's win rates.
  const teamStats = {
    "Suriname|male|T20": { winRate: 0.25 },
    "Kenya|male|T20": { winRate: 0.53 },
  };
  const situation = { ...chaseSituation, battingTeamName: "SUR", bowlingTeamName: "KEN" };
  assert.equal(
    resolveMatchTeams({ title: "Surrey vs Kent, Group A", battingTeamName: "SUR", teamStats, format: "T20" }),
    null,
  );
  assert.equal(orient(situation, { model: teamStats }), orient(situation, { model: {} }));
});

test("Cricbuzz's own format codes resolve, not just prose", () => {
  // Captured from the live page of the 2026-07-30 Hundred games: Cricbuzz reports the format as
  // `matchFormat: "HUN"`, and the title ("Southern Brave vs Birmingham Phoenix, 13th Match")
  // names no format. Before FORMAT_CODES this resolved to UNKNOWN, which carries no ball budget,
  // so evaluate() returned undetermined for every ball of every Hundred match ever played.
  assert.equal(formatFromText("HUN", "Southern Brave vs Birmingham Phoenix, 13th Match"), "HUNDRED");
  assert.equal(inningsBalls(formatFromText("HUN")), 100);
  assert.equal(
    ballsRemainingFor({ oversRemaining: null, ballsBowled: 60, format: formatFromText("HUN") }),
    40,
    "a Hundred chase must count down from 100",
  );
  // LIST_A has an underscore where the loose `\blist\s*a\b` rule wants a space.
  assert.equal(formatFromText("LIST_A"), "ODI");
  assert.equal(formatFromText("FC"), "FC", "first-class is not a Test; the Test head never trained on it");
  assert.equal(formatFromText("ODI"), "ODI");
  assert.equal(formatFromText("T20I"), "T20");
});

test("a format code is matched exactly, never as a substring of the haystack", () => {
  // HUN is also Hungary's team code, and the loose rules test HUNDRED before T20 — so folding the
  // codes into the joined haystack would score a Hungary T20 on a 100-ball budget.
  assert.equal(formatFromText("T20", "Hungary vs Malta, 3rd Match"), "T20");
  assert.equal(formatFromText("T20", "HUN vs MLT, 3rd Match"), "T20");
  assert.equal(formatFromText("", "HUN vs MLT, 3rd Match"), "UNKNOWN");
  assert.equal(formatFromText("The Hundred", "Southern Brave vs Birmingham Phoenix"), "HUNDRED");
  assert.equal(formatFromText(null, undefined), "UNKNOWN");
});

// ── Test matches (the cricket format) ────────────────────────────────────────

/** A v5 artifact carrying a long model that returns exactly the priors before shrinkage. */
function longModel(priors = [0.37, 0.23, 0.40]) {
  return {
    version: 5,
    teamStats: {},
    long: {
      weights: [new Array(16).fill(0), new Array(16).fill(0), new Array(16).fill(0)],
      // Softmax of the log-priors is the priors themselves, so the model's own output equals the
      // prior and any deviation below has to come from the code under test.
      biases: priors.map((p) => Math.log(p)),
      featureMeans: new Array(16).fill(0),
      featureStds: new Array(16).fill(1),
      classPriors: priors,
      priorSettleProgress: 0.25,
    },
  };
}

const testSituation = {
  lead: 84,
  wicketsLost: 4,
  inningsIndex: 2,
  ballsBowled: 1400,
  scheduledDays: 5,
  inningsRunRate: 3.4,
  ownInningsUsed: 2,
  oppInningsUsed: 1,
  followedOn: false,
  battingTeam: "England",
  bowlingTeam: "India",
};

test("a Test yields three outcomes, and they sum to one", () => {
  const r = evaluateLong({ ...testSituation, model: longModel() });
  assert.equal(r.undetermined, false);
  const loss = 1 - r.battingTeamWinProbability - r.drawProbability;
  assert.ok(Math.abs(r.battingTeamWinProbability + r.drawProbability + loss - 1) < 1e-9);
  assert.ok(r.drawProbability > 0, "a Test must be able to say draw");
});

test("without a long model a Test is undetermined, never a made-up split", () => {
  // There is no heuristic for a Test position. A three-way guess would render as a confident bar.
  assert.equal(evaluateLong({ ...testSituation, model: null }).undetermined, true);
  assert.equal(evaluateLong({ ...testSituation, model: { version: 4 } }).undetermined, true);
  assert.equal(evaluateLong({ ...testSituation, model: longModel(), wicketsLost: 10 }).undetermined, true);
});

test("the opening over of a Test returns the priors, not a confident number", () => {
  // The defect: a linear model at the edge of its training data extrapolated to 73% for the side
  // batting first before anything had happened, against a 39% base rate.
  const priors = [0.37, 0.23, 0.40];
  const opening = evaluateLong({
    ...testSituation,
    inningsIndex: 0,
    ballsBowled: 6,
    ownInningsUsed: 1,
    oppInningsUsed: 0,
    model: longModel(priors),
  });
  assert.ok(Math.abs(opening.battingTeamWinProbability - priors[2]) < 0.01);
  assert.ok(Math.abs(opening.drawProbability - priors[1]) < 0.01);
});

test("re-orienting a Test subtracts the draw instead of handing it to the other side", () => {
  // The published defect: match 170667 carried winProbabilityTeam1 0.80 against a 0.41 draw, a sum
  // of 1.21 that leaves the loss negative. `1 - batting` is `loss + draw` in a three-outcome model.
  const r = evaluateLong({ ...testSituation, model: longModel() });
  const team1 = orientToTeam1({
    battingTeamWinProbability: r.battingTeamWinProbability,
    drawProbability: r.drawProbability,
    battingTeamIsTeam1: false,
  });
  assert.ok(
    Math.abs(team1 - (1 - r.drawProbability - r.battingTeamWinProbability)) < 1e-9,
    "team1 must be the loss class, not loss + draw",
  );
  assert.ok(team1 + r.drawProbability <= 1 + 1e-9, `team1 ${team1} + draw ${r.drawProbability} > 1`);
  assert.equal(
    orientToTeam1({
      battingTeamWinProbability: r.battingTeamWinProbability,
      drawProbability: r.drawProbability,
      battingTeamIsTeam1: true,
    }),
    r.battingTeamWinProbability,
    "the batting side's own number is never re-oriented",
  );
});

test("a limited-overs flip is unchanged — no draw to subtract", () => {
  // The fix must reduce to the old arithmetic for every format that is not a Test.
  assert.equal(
    orientToTeam1({ battingTeamWinProbability: 0.62, drawProbability: null, battingTeamIsTeam1: false }),
    1 - 0.62,
  );
});

test("a Test's third innings is team1's when the same side batted first", () => {
  // battingTeamIsTeam1 compared innings ids, so a side batting its SECOND innings read as not-team1
  // and winProbFor flipped the probability onto the opposition for half of every Test.
  const isl = [
    { inningsId: 1, batTeamId: 9, batTeamName: "ENG", score: 325, wickets: 10 },
    { inningsId: 2, batTeamId: 6, batTeamName: "IND", score: 241, wickets: 10 },
    { inningsId: 3, batTeamId: 9, batTeamName: "ENG", score: 168, wickets: 4 },
  ];
  const ms = {
    inningsId: 3,
    matchScoreDetails: { matchFormat: "TEST", inningsScoreList: isl },
    score: 168,
    wickets: 4,
  };
  const s = liveSituation(ms, isl);
  assert.equal(s.battingTeamIsTeam1, true, "England batted first and are batting again");
  assert.equal(s.firstInningsTeamId, "9", "and team1 is still the side the device aligns on");

  const second = liveSituation(
    { ...ms, inningsId: 2 },
    isl,
  );
  assert.equal(second.battingTeamIsTeam1, false, "India batting is genuinely not team1");
});

test("a fourth-innings Test chase carries a target rather than an empty one", () => {
  // liveSituation used to hardcode `inningsId === 2` for a chase, so the one innings of a Test that
  // IS a run chase never carried target or runsNeeded. The feature vector has to show it.
  const chasing = longFeatures({
    lead: -150, wicketsInHand: 6, inningsIndex: 3, ballsRemaining: 400, ballsTotal: 2700,
    teamStrengthDiff: 0, isFourthInnings: true, runsNeeded: 151, followedOn: false,
    inningsUsedDiff: 0, inningsRunRate: 3.1, wicketsToTake: 6, scheduledDays: 5,
  });
  assert.equal(chasing[5], 1, "isFourthInnings");
  assert.ok(chasing[6] > 0, "chasePressure must be non-zero in the fourth innings");
});

// ── pre-match: corpus membership is the model's own precondition ──────────────

/** A v5 artifact with a usable pre-match sub-model and a two-team corpus. */
function prematchModel(teamStats) {
  return {
    version: 5,
    prematch: {
      weights: [4, 0, 0, 0],
      bias: 0,
      temperature: 1,
      featureMeans: [0, 0, 0, 0],
      featureStds: [1, 1, 1, 1],
    },
    teamStats,
  };
}

const twoTeams = {
  "Scotland|male|ODI": { winRate: 0.5, matchesPlayed: 126 },
  "Canada|male|ODI": { winRate: 0.422, matchesPlayed: 103 },
};

test("a team the corpus has never seen makes the fixture undetermined", () => {
  // Team strength is the only feature this model has, so an absent team used to default to a win
  // rate of exactly 0.5 — turning "no record" into the claim "exactly average" and producing a
  // confident-looking number. Peru, which Cricsheet has no record of, against a known Brazil
  // returned 0.600 that way. The guard must live here and not in the caller.
  const model = prematchModel({ "Brazil|male|T20": { winRate: 0.333, matchesPlayed: 21 } });
  const result = evaluatePrematch({
    firstTeam: "Peru", secondTeam: "Brazil", gender: "male", format: "T20", resolved: true, model,
  });
  assert.equal(result.undetermined, true);
  assert.equal(result.battingTeamWinProbability, null);
});

test("both sides absent is undetermined, not a coin flip", () => {
  const result = evaluatePrematch({
    firstTeam: "East Delhi Riders", secondTeam: "West Delhi Lions",
    gender: "male", format: "T20", resolved: true, model: prematchModel(twoTeams),
  });
  assert.equal(result.undetermined, true);
});

test("the right format slice is the one that decides the favourite", () => {
  // Scotland vs Canada, 2026-08-07. Both teams have a T20 record and an ODI record, and the
  // fixture is an ODI. Scored on T20 form the two are level and Canada edges it; on their actual
  // ODI records Scotland is clearly ahead. Same teams, same model, opposite answer — which is why
  // the format now comes from Cricbuzz rather than from a probe over slices.
  const model = prematchModel({
    ...twoTeams,
    "Scotland|male|T20": { winRate: 0.5137614678899083, matchesPlayed: 103 },
    "Canada|male|T20": { winRate: 0.5208333333333334, matchesPlayed: 90 },
  });
  const asT20 = evaluatePrematch({
    firstTeam: "Scotland", secondTeam: "Canada", gender: "male", format: "T20", resolved: true, model,
  });
  const asOdi = evaluatePrematch({
    firstTeam: "Scotland", secondTeam: "Canada", gender: "male", format: "ODI", resolved: true, model,
  });
  assert.ok(asT20.battingTeamWinProbability < 0.5, "T20 form makes Canada the favourite");
  assert.ok(asOdi.battingTeamWinProbability > 0.5, "ODI form makes Scotland the favourite");
});

test("a fixture whose teams are both in the corpus is still scored", () => {
  const result = evaluatePrematch({
    firstTeam: "Scotland", secondTeam: "Canada", gender: "male", format: "ODI", resolved: true,
    model: prematchModel(twoTeams),
  });
  assert.equal(result.undetermined, false);
  assert.equal(result.prematch, true);
  assert.ok(result.battingTeamWinProbability > 0 && result.battingTeamWinProbability < 1);
});

/**
 * The symmetric pre-match head the trainer now fits: no bias, weight only on the antisymmetric
 * columns, zero means. Same shape as the old model, so serving needs no change to score it.
 */
function symmetricPrematch(bias = 0) {
  return {
    version: MODEL_VERSION,
    prematch: {
      core: {
        weights: [0, 0, 0, 0.8, 0.5, 0.3, 0.6, 0.2, 0, 0],
        bias,
        temperature: 1.3,
        featureMeans: new Array(10).fill(0),
        featureStds: new Array(10).fill(1),
      },
      full: null,
    },
    teamStats: {
      "Scotland|male|ODI": { winRate: 0.5, matchesPlayed: 126 },
      "Canada|male|ODI": { winRate: 0.422, matchesPlayed: 103 },
    },
  };
}

const ctxAB = {
  eloDiff: 0.7, formDiff: 0.2, h2hAdvantage: 0.4, winRateDiff: 0.1, familiarityDiff: -0.3,
  venueScoring: 1.15, venueChaseAdvantage: 0.05, hasVenue: true, xi: null,
};
const ctxBA = {
  ...ctxAB,
  eloDiff: -0.7, formDiff: -0.2, h2hAdvantage: -0.4, winRateDiff: -0.1, familiarityDiff: 0.3,
};

test("pre-match p(A beats B) + p(B beats A) is one through the served path", () => {
  const score = (first, second, context, model) => evaluatePrematch({
    firstTeam: first, secondTeam: second, gender: "male", format: "ODI", resolved: true, context, model,
  }).battingTeamWinProbability;
  const model = symmetricPrematch();
  const ab = score("Scotland", "Canada", ctxAB, model);
  const ba = score("Canada", "Scotland", ctxBA, model);
  assert.ok(ab > 0.5 && ab < 0.97, "the scored side is a real favourite, not the clamp");
  assert.ok(Math.abs(ab + ba - 1) <= 1e-9, `sum ${ab + ba}`);
  // A free bias is what made the old model asymmetric: the same swap no longer sums to one.
  const biased = symmetricPrematch(0.4);
  const sum = score("Scotland", "Canada", ctxAB, biased) + score("Canada", "Scotland", ctxBA, biased);
  assert.ok(Math.abs(sum - 1) > 0.01);
});

test("pre-match symmetry holds when each side's context comes from sideContextFor, as main.js builds it", () => {
  const match = {
    teams: [
      { id: 27, known: true, elo: 1610, form: 0.7, winRate: 0.58, visits: 6, xi: null },
      { id: 9, known: true, elo: 1480, form: 0.4, winRate: 0.45, visits: 1, xi: null },
    ],
    h2h: { firstWins: 5, secondWins: 2, games: 7 },
    venue: { known: true, scoring: 1.1, chaseAdvantage: 0.04, drawAdvantage: 0, matches: 30 },
  };
  const score = (first, second, context) => evaluatePrematch({
    firstTeam: first, secondTeam: second, gender: "male", format: "ODI", resolved: true, context, model: symmetricPrematch(),
  }).battingTeamWinProbability;
  const ab = score("Scotland", "Canada", sideContextFor(match, 27));
  const ba = score("Canada", "Scotland", sideContextFor(match, 9));
  assert.ok(ab > 0.5 && ab < 0.97, `the stronger side is favoured off the clamp: ${ab}`);
  assert.ok(Math.abs(ab + ba - 1) <= 1e-9, `sum ${ab + ba}`);
});
