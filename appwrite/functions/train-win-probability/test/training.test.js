import test from "node:test";
import assert from "node:assert/strict";
import {
  chaseRows,
  longRows,
  inningsBudgetBalls,
  longMatchBalls,
  matchDate,
  formatClassOf,
  teamKey,
  isStatsCountable,
  isChaseTrainable,
  isLongTrainable,
  isLongFormat,
  longOutcomeFor,
  holdoutCutoffDate,
  inningsTotals,
  fitStandardizer,
  applyStandardizer,
  evaluateModel,
  baseRate,
  shrinkToPrior,
  softmax,
  classBaseRates,
  OUTCOME,
  MODEL_VERSION,
  NUM_FEATURES,
  NUM_LONG_FEATURES,
  PRIOR_SETTLE_PROGRESS,
} from "../src/training.js";

/** Build a Cricsheet-shaped innings of [balls] deliveries scoring [runsPerBall] each. */
function innings(team, balls, runsPerBall = 1, wicketAt = []) {
  const overs = [];
  for (let b = 0; b < balls; b++) {
    const overIdx = Math.floor(b / 6);
    if (!overs[overIdx]) overs[overIdx] = { over: overIdx, deliveries: [] };
    const delivery = { runs: { total: runsPerBall } };
    if (wicketAt.includes(b + 1)) delivery.wickets = [{ player_out: "x" }];
    overs[overIdx].deliveries.push(delivery);
  }
  return { team, overs };
}

function match({
  overs = 20,
  date = "2024-06-01",
  winner = "B",
  firstBalls = 120,
  firstRunsPerBall = 1,
  secondBalls = 120,
  secondRunsPerBall = 1,
  wicketAt = [],
  matchType = "T20",
} = {}) {
  return {
    info: {
      match_type: matchType,
      teams: ["A", "B"],
      dates: [date],
      overs,
      outcome: { winner },
    },
    innings: [
      innings("A", firstBalls, firstRunsPerBall),
      innings("B", secondBalls, secondRunsPerBall, wicketAt),
    ],
  };
}

const noStrength = () => 0.5;

// --- innings budget -----------------------------------------------------

test("innings budget comes from the match, not a hardcoded 120", () => {
  assert.equal(inningsBudgetBalls({ overs: 20 }), 120);
  assert.equal(inningsBudgetBalls({ overs: 15 }), 90, "a rain-shortened game has a shorter budget");
  assert.equal(inningsBudgetBalls({ overs: 5 }), 30);
  assert.equal(inningsBudgetBalls({}), 120, "absent overs falls back to a full T20");
});

test("a 15-over match scores its final ball against 90, not 120", () => {
  const rows = chaseRows(
    match({ overs: 15, firstBalls: 90, secondBalls: 89, winner: "A" }),
    noStrength,
  );
  // Last emitted row is the 88th ball (89th has ballsLeft 1... check the tail explicitly).
  const last = rows[rows.length - 1];
  const ballsRemainingFraction = last.features[2];
  // With a 120 budget the same delivery would report (120-88)/120 = 0.267; against 90 it is
  // (90-88)/90 = 0.022.
  assert.ok(
    ballsRemainingFraction < 0.05,
    `expected a nearly-exhausted innings, got fraction ${ballsRemainingFraction}`,
  );
});

// --- off-by-one ---------------------------------------------------------

test("balls faced and balls remaining sum to the budget, not budget plus one", () => {
  // The old code computed ballsLeft before incrementing ballsFaced, so every row satisfied
  // ballsFaced + ballsLeft === 121 for a 120-ball innings.
  const budget = 120;
  const rows = chaseRows(match({ secondBalls: 119, winner: "A" }), noStrength);
  assert.ok(rows.length > 0);
  rows.forEach((row, i) => {
    const ballsFaced = i + 1;
    const ballsLeft = row.features[2] * budget;
    assert.equal(
      Math.round(ballsFaced + ballsLeft),
      budget,
      `row ${i}: ballsFaced ${ballsFaced} + ballsLeft ${ballsLeft} !== ${budget}`,
    );
  });
});

test("required run rate uses post-ball balls remaining", () => {
  const rows = chaseRows(match({ secondBalls: 3, winner: "A", firstBalls: 120 }), noStrength);
  const first = rows[0];
  // After ball 1 of a 120-ball chase: 119 balls left, target 121, 1 run scored => 120 needed.
  const runsNeededPerBall = first.features[3];
  assert.ok(
    Math.abs(runsNeededPerBall - 120 / 119) < 1e-9,
    `expected 120/119, got ${runsNeededPerBall}`,
  );
});

// --- filtering ----------------------------------------------------------

test("the chase model takes limited-overs matches and refuses long formats", () => {
  const two = (t, extra = {}) => ({ match_type: t, teams: ["A", "B"], ...extra });
  assert.equal(isChaseTrainable(two("T20")), true);
  assert.equal(isChaseTrainable(two("IT20")), true);
  assert.equal(isChaseTrainable(two("ODI")), true, "ODI is a chase, v3 excluded it for no reason");
  assert.equal(isChaseTrainable(two("ODM")), true);
  assert.equal(isChaseTrainable(two("T20", { balls_per_over: 5 })), true, "The Hundred");
  // The features are ratios against a fixed innings budget, which a multi-day match has none of.
  assert.equal(isChaseTrainable(two("Test")), false);
  assert.equal(isChaseTrainable(two("MDM")), false);
  assert.equal(isChaseTrainable({ match_type: "T20", teams: ["A"] }), false);
});

test("only Tests train the long-format model, but every format feeds team stats", () => {
  const two = (t) => ({ match_type: t, teams: ["A", "B"] });
  // Test and MDM pooled scored 0.506 holdout accuracy against 0.394 baseline; Tests alone 0.619.
  assert.equal(isLongTrainable(two("Test")), true);
  assert.equal(isLongTrainable(two("MDM")), false, "domestic first-class must not train the model");
  // ...but MDM is still counted, under its own key.
  assert.equal(isStatsCountable(two("MDM")), true);
  assert.equal(isStatsCountable(two("Test")), true);
  assert.equal(isStatsCountable(two("T20")), true);
  assert.equal(isStatsCountable({ match_type: "Hockey", teams: ["A", "B"] }), false);
});

test("The Hundred is a 100-ball innings, not a 120-ball one", () => {
  // Cricsheet records it as match_type T20 with balls_per_over 5. Multiplying overs by a hardcoded
  // 6 gave every Hundred row a budget 20 balls too long.
  assert.equal(formatClassOf({ match_type: "T20", balls_per_over: 5 }), "HUNDRED");
  assert.equal(formatClassOf({ match_type: "T20", balls_per_over: 6 }), "T20");
  assert.equal(inningsBudgetBalls({ overs: 20, balls_per_over: 5 }), 100);
  assert.equal(inningsBudgetBalls({ overs: 20, balls_per_over: 6 }), 120);
  assert.equal(inningsBudgetBalls({ overs: 50 }), 300, "missing balls_per_over defaults to six");
});

test("Test and MDM are separate format classes", () => {
  assert.equal(formatClassOf({ match_type: "Test" }), "TEST");
  assert.equal(formatClassOf({ match_type: "MDM" }), "MDM");
  assert.equal(formatClassOf({ match_type: "ODM" }), "ODI");
  assert.ok(isLongFormat("TEST") && isLongFormat("MDM"));
  assert.ok(!isLongFormat("T20") && !isLongFormat("ODI"));
});

test("team stats are keyed by team, gender and format", () => {
  // The v3 key was the bare name, which pooled Australia's men's T20 (0.545) with their women's
  // ODI (0.842) and women's multi-day (0.286) into a single 0.642.
  assert.notEqual(teamKey("Australia", "male", "T20"), teamKey("Australia", "female", "T20"));
  assert.notEqual(teamKey("Australia", "male", "T20"), teamKey("Australia", "male", "TEST"));
  assert.equal(teamKey("Australia", "male", "T20"), teamKey(" Australia ", "male", "T20"));
});

test("a long-format outcome is win, loss or draw — and abandoned is none of them", () => {
  const of = (outcome) => longOutcomeFor({ outcome }, "A");
  assert.equal(of({ winner: "A", by: { runs: 100 } }), OUTCOME.WIN);
  assert.equal(of({ winner: "B", by: { runs: 100 } }), OUTCOME.LOSS);
  assert.equal(of({ result: "draw" }), OUTCOME.DRAW);
  assert.equal(of({ result: "tie" }), OUTCOME.DRAW);
  // A rained-off match says nothing about the position it was abandoned from, and calling it a
  // draw would teach the model that weather is a cricketing outcome.
  assert.equal(of({ result: "no result" }), null);
  assert.equal(of({}), null);
});

// --- long format --------------------------------------------------------

/** A four-innings Test where each side bats [balls] deliveries per innings. */
function testMatch({ balls = 120, outcome = { winner: "A", by: { runs: 10 } }, order = ["A", "B", "A", "B"] } = {}) {
  return {
    info: {
      match_type: "Test",
      gender: "male",
      teams: ["A", "B"],
      dates: ["2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05"],
      outcome,
    },
    innings: order.map((team) => innings(team, balls, 1)),
  };
}

test("a long-format lead is corrected for innings used", () => {
  // The defect replay caught: in innings one the batting side always shows a growing positive
  // lead because the opposition has not batted, and in innings two always a large deficit. The
  // model read that as position and gave 73% to a side level after ten overs of day one, against
  // a 39% base rate. inningsUsedDiff makes a lead mean the same thing at every point.
  const rows = longRows(testMatch(), () => 0.5);
  assert.ok(rows.length > 0);
  const usedDiff = (r) => r.features[8];
  const inningsOf = (r) => Math.round(r.features[2] * 4);
  assert.equal(usedDiff(rows.find((r) => inningsOf(r) === 1)), 1, "innings 1: one innings ahead");
  assert.equal(usedDiff(rows.find((r) => inningsOf(r) === 2)), 0, "innings 2: level");
  assert.equal(usedDiff(rows.find((r) => inningsOf(r) === 3)), 1);
  assert.equal(usedDiff(rows.find((r) => inningsOf(r) === 4)), 0);
});

test("a follow-on is read from the innings order", () => {
  const normal = longRows(testMatch({ order: ["A", "B", "A", "B"] }), () => 0.5);
  const followOn = longRows(testMatch({ order: ["A", "B", "B", "A"] }), () => 0.5);
  assert.equal(normal[0].features[7], 0);
  assert.equal(followOn[0].features[7], 1);
});

test("chase pressure only exists in the fourth innings", () => {
  const rows = longRows(testMatch(), () => 0.5);
  const inningsOf = (r) => Math.round(r.features[2] * 4);
  for (const r of rows) {
    if (inningsOf(r) === 4) continue;
    assert.equal(r.features[6], 0, "a side batting before the fourth innings is not chasing");
    assert.equal(r.features[5], 0);
  }
  assert.ok(rows.some((r) => inningsOf(r) === 4 && r.features[5] === 1));
});

test("an abandoned long-format match yields no rows at all", () => {
  assert.equal(longRows(testMatch({ outcome: { result: "no result" } }), () => 0.5).length, 0);
});

test("long rows are emitted per over, not per delivery", () => {
  // A Test runs to ~2,700 balls against a T20's 120. Per-delivery rows would let 911 Tests
  // outweigh 13,992 T20s in the sampler while carrying no more information.
  const rows = longRows(testMatch({ balls: 120 }), () => 0.5);
  assert.ok(rows.length <= 4 * 20 + 4, `expected ~one row per over, got ${rows.length}`);
});

test("shrinkage returns the prior at the start and the model once settled", () => {
  const priors = [0.367, 0.239, 0.394];
  const model = [0.9, 0.05, 0.05];
  assert.deepEqual(shrinkToPrior(model, priors, 0, 0.25), priors);
  assert.deepEqual(shrinkToPrior(model, priors, 0.25, 0.25), model);
  assert.deepEqual(shrinkToPrior(model, priors, 1, 0.25), model, "past settle stays unshrunk");
  const half = shrinkToPrior(model, priors, 0.125, 0.25);
  half.forEach((p, i) => assert.ok(Math.abs(p - (model[i] + priors[i]) / 2) < 1e-9));
  // Whatever the blend, it stays a distribution.
  assert.ok(Math.abs(half.reduce((a, b) => a + b, 0) - 1) < 1e-9);
});

test("softmax survives a logit large enough to overflow", () => {
  // A long-format lead of several hundred runs drives a logit where Math.exp is Infinity, and
  // Infinity/Infinity is NaN — which would poison every weight on the next gradient step.
  const p = softmax([1000, 0, -1000]);
  assert.ok(p.every(Number.isFinite), `not finite: ${p}`);
  assert.ok(Math.abs(p.reduce((a, b) => a + b, 0) - 1) < 1e-9);
});

test("rows stop once the chase is won or the side is all out", () => {
  const chased = chaseRows(
    match({ firstBalls: 10, firstRunsPerBall: 1, secondBalls: 60, winner: "B" }),
    noStrength,
  );
  // Target is 11; the chase passes it on ball 11, so no row may be emitted after that.
  assert.ok(chased.length <= 11, `emitted ${chased.length} rows past the winning runs`);

  const allOut = chaseRows(
    match({ secondBalls: 60, wicketAt: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10], winner: "A" }),
    noStrength,
  );
  assert.ok(
    allOut.every((r) => r.features[1] > 0),
    "no row may report zero wickets in hand",
  );
});

test("every row carries the match label and the right feature count", () => {
  const rows = chaseRows(match({ secondBalls: 30, winner: "B", firstBalls: 120 }), noStrength);
  assert.ok(rows.length > 0);
  for (const row of rows) {
    assert.equal(row.features.length, NUM_FEATURES);
    assert.equal(row.label, 1, "team B chasing and winning must be labelled 1");
    assert.ok(row.features.every(Number.isFinite), "features must all be finite");
  }
});

// --- temporal split -----------------------------------------------------

test("holdout cutoff splits chronologically", () => {
  const dates = Array.from({ length: 100 }, (_, i) => `2024-01-${String((i % 28) + 1).padStart(2, "0")}`);
  const cutoff = holdoutCutoffDate(dates, 0.2);
  assert.ok(cutoff, "expected a cutoff");
  const before = dates.filter((d) => d < cutoff).length;
  assert.ok(before >= 70 && before <= 85, `training share looks wrong: ${before}/100`);
});

test("too little data yields no cutoff, so everything trains", () => {
  assert.equal(holdoutCutoffDate(["2024-01-01", "2024-01-02"], 0.2), null);
  assert.equal(holdoutCutoffDate([], 0.2), null);
});

test("team strength is whatever the caller supplies, so leakage is the caller's choice", () => {
  // The old code back-filled corpus-wide win rates into features[4] after the fact, which is how
  // a 2014 row came to carry 2023 results. chaseRows now takes the lookup as a parameter, so the
  // training-window restriction is expressible and testable.
  const rows = chaseRows(
    match({ secondBalls: 12, winner: "B" }),
    (team) => (team === "B" ? 0.9 : 0.3),
  );
  assert.ok(rows.length > 0);
  for (const row of rows) {
    assert.ok(Math.abs(row.features[4] - 0.6) < 1e-9, `expected 0.9 - 0.3, got ${row.features[4]}`);
  }
});

test("matchDate reads the first Cricsheet date", () => {
  assert.equal(matchDate({ dates: ["2024-06-01", "2024-06-02"] }), "2024-06-01");
  assert.equal(matchDate({ dates: [] }), null);
  assert.equal(matchDate({}), null);
});

// --- standardizer and metrics ------------------------------------------

test("the standardizer is fit on training rows and applied to both sets", () => {
  const train = [
    { features: [0, 0, 0, 0, 0], label: 0 },
    { features: [2, 2, 2, 2, 2], label: 1 },
  ];
  const holdout = [{ features: [4, 4, 4, 4, 4], label: 1 }];
  const std = fitStandardizer(train);
  assert.ok(Math.abs(std.means[0] - 1) < 1e-9);
  applyStandardizer(train, std);
  applyStandardizer(holdout, std);
  // Holdout is scaled by the TRAINING statistics, so it is free to fall outside [-1, 1].
  assert.ok(holdout[0].features[0] > 1, "holdout must not be re-centred on itself");
});

test("evaluateModel reports loss and accuracy, and copes with an empty set", () => {
  const rows = [
    { features: [1, 0, 0, 0, 0], label: 1 },
    { features: [-1, 0, 0, 0, 0], label: 0 },
  ];
  const good = evaluateModel(rows, [5, 0, 0, 0, 0], 0);
  assert.equal(good.accuracy, 1);
  assert.ok(good.logLoss < 0.1);

  const backwards = evaluateModel(rows, [-5, 0, 0, 0, 0], 0);
  assert.equal(backwards.accuracy, 0);
  assert.ok(backwards.logLoss > good.logLoss);

  const empty = evaluateModel([], [0, 0, 0, 0, 0], 0);
  assert.equal(empty.logLoss, null);
  assert.equal(empty.rows, 0);
});

test("baseRate is the constant-predictor accuracy to beat", () => {
  assert.equal(baseRate([{ label: 1 }, { label: 1 }, { label: 0 }, { label: 0 }]), 0.5);
  assert.equal(baseRate([]), null);
});

test("inningsTotals sums runs and counts balls", () => {
  const { runs, balls } = inningsTotals(innings("A", 12, 2));
  assert.equal(balls, 12);
  assert.equal(runs, 24);
  assert.deepEqual(inningsTotals(null), { runs: 0, balls: 0 });
});

test("model version is 4", () => {
  // Bumped from 3 because `teamStats` changed key shape from a bare team name to
  // "team|gender|format". A v3 reader would miss every lookup and fall back to 0.5 — silently,
  // and on exactly the feature v4 exists to repair. winprob.js refuses a version it does not know
  // on the learned path, so the mismatch degrades to the heuristic instead of going unnoticed.
  assert.equal(MODEL_VERSION, 4);
  assert.equal(NUM_FEATURES, 5, "the chase model's shape is unchanged");
  assert.equal(NUM_LONG_FEATURES, 9);
  assert.equal(PRIOR_SETTLE_PROGRESS, 0.25);
});
