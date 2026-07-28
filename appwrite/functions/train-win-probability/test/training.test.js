import test from "node:test";
import assert from "node:assert/strict";
import {
  chaseRows,
  inningsBudgetBalls,
  matchDate,
  isSupportedMatch,
  holdoutCutoffDate,
  inningsTotals,
  fitStandardizer,
  applyStandardizer,
  evaluateModel,
  baseRate,
  MODEL_VERSION,
  NUM_FEATURES,
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

test("only T20 and IT20 matches enter the pipeline", () => {
  assert.equal(isSupportedMatch({ match_type: "T20", teams: ["A", "B"] }), true);
  assert.equal(isSupportedMatch({ match_type: "IT20", teams: ["A", "B"] }), true);
  assert.equal(isSupportedMatch({ match_type: "ODI", teams: ["A", "B"] }), false);
  assert.equal(isSupportedMatch({ match_type: "Test", teams: ["A", "B"] }), false);
  assert.equal(isSupportedMatch({ match_type: "T20", teams: ["A"] }), false);
  assert.equal(chaseRows(match({ matchType: "ODI" }), noStrength).length, 0);
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

test("model version is 3", () => {
  assert.equal(MODEL_VERSION, 3);
});
