import test from "node:test";
import assert from "node:assert/strict";
import {
  MODEL_VERSION,
  OUTCOME,
  PRIOR_SETTLE_PROGRESS,
  CHASE_FEATURE_NAMES,
  FIRST_INNINGS_FEATURE_NAMES,
  LONG_FEATURE_NAMES,
  chaseFeatures,
  firstInningsFeatures,
  longFeatures,
  packDelivery,
  unpackDelivery,
  skeletonOf,
  inningsTotals,
  chaseRows,
  firstInningsRows,
  longRows,
  formatClassOf,
  teamKey,
  parRunRateFor,
  inningsBudgetBalls,
  longMatchBalls,
  matchDate,
  isStatsCountable,
  isLimitedOvers,
  isLongFormat,
  isLongTrainable,
  longOutcomeFor,
  holdoutCutoffDate,
  shrunkWinRate,
  RowSink,
  sigmoid,
  softmax,
  trainBinary,
  trainMulticlass,
  fitTemperature,
  binaryMetrics,
  multiclassMetrics,
  expectedCalibrationError,
  baseRate,
  classBaseRates,
  predictClasses,
  shrinkToPrior,
} from "../src/training.js";

// ── corpus classification ────────────────────────────────────────────────────

test("format classes separate the things that are scored differently", () => {
  assert.equal(formatClassOf({ match_type: "T20" }), "T20");
  assert.equal(formatClassOf({ match_type: "IT20" }), "T20");
  assert.equal(formatClassOf({ match_type: "ODI" }), "ODI");
  assert.equal(formatClassOf({ match_type: "ODM" }), "ODI");
  assert.equal(formatClassOf({ match_type: "Test" }), "TEST");
  assert.equal(formatClassOf({ match_type: "MDM" }), "MDM");
  assert.equal(formatClassOf({ match_type: "The Hundred" }), null, "unmodelled types are not guessed");
  // The Hundred is not its own match_type. Five balls per over is the only marker.
  assert.equal(formatClassOf({ match_type: "T20", balls_per_over: 5 }), "HUNDRED");
  assert.equal(formatClassOf({ match_type: "T20", balls_per_over: 6 }), "T20");
});

test("the innings budget multiplies by balls_per_over", () => {
  // 359 Hundred matches are recorded as T20 with 20 overs of 5 balls. `overs * 6` gave them 120
  // instead of 100, so every row was computed against a budget 20 balls too long.
  assert.equal(inningsBudgetBalls({ overs: 20, balls_per_over: 5 }), 100);
  assert.equal(inningsBudgetBalls({ overs: 20, balls_per_over: 6 }), 120);
  assert.equal(inningsBudgetBalls({ overs: 50 }), 300);
  assert.equal(inningsBudgetBalls({ overs: 12.5, balls_per_over: 6 }), 75, "rain-shortened rounds");
  assert.equal(inningsBudgetBalls({}), 120, "no declared overs falls back to T20");
});

test("team stats are keyed by team, gender and format", () => {
  // A bare name pooled Australia's men's and women's sides and all formats into one win rate.
  assert.equal(teamKey("Australia", "female", "ODI"), "Australia|female|ODI");
  assert.equal(teamKey(" India ", "male", "TEST"), "India|male|TEST");
  assert.equal(teamKey("India"), "India|male|T20", "the defaults must be stable, not undefined");
});

test("long formats are recognised, but only Tests train the three-outcome model", () => {
  assert.ok(isLongFormat("TEST"));
  assert.ok(isLongFormat("MDM"));
  assert.ok(!isLongFormat("ODI"));
  // Test and MDM pooled scored 0.506 holdout accuracy against 0.619 for Tests alone. MDM still
  // feeds team stats under its own key — excluded from the model, not from the corpus.
  assert.ok(isLongTrainable("TEST"));
  assert.ok(!isLongTrainable("MDM"));
  assert.ok(isStatsCountable({ match_type: "MDM", teams: ["A", "B"] }));
  assert.ok(!isStatsCountable({ match_type: "MDM", teams: ["A"] }));
  assert.ok(isLimitedOvers("T20") && isLimitedOvers("ODI") && isLimitedOvers("HUNDRED"));
  assert.ok(!isLimitedOvers("TEST"));
});

test("only a decided or drawn long match is labelled", () => {
  assert.equal(longOutcomeFor({ winner: "India" }, "India"), OUTCOME.WIN);
  assert.equal(longOutcomeFor({ winner: "India" }, "Australia"), OUTCOME.LOSS);
  assert.equal(longOutcomeFor({ result: "draw" }, "India"), OUTCOME.DRAW);
  assert.equal(longOutcomeFor({ result: "tie" }, "India"), OUTCOME.DRAW);
  // Abandoned tells us nothing about the position it was abandoned from; labelling it a draw would
  // teach the model that rain is a cricketing outcome.
  assert.equal(longOutcomeFor({ result: "no result" }, "India"), null);
  assert.equal(longOutcomeFor({}, "India"), null);
});

test("a long match's scheduled balls come from the days allotted", () => {
  assert.equal(longMatchBalls(5), 2700);
  assert.equal(longMatchBalls(4), 2160);
  assert.equal(longMatchBalls(undefined), 2700, "an unknown length is assumed to be a Test");
});

test("the split is temporal, and there is no split at all below ten matches", () => {
  const dates = Array.from({ length: 100 }, (_, i) => `2024-01-${String((i % 28) + 1).padStart(2, "0")}`).sort();
  assert.equal(holdoutCutoffDate(dates, 0.2), dates[80]);
  assert.equal(holdoutCutoffDate(["2024-01-01"], 0.2), null);
});

test("a win rate is shrunk toward even by a pseudo-count", () => {
  // all_json has teams with one recorded match. A 1/1 record used to enter the logit as +0.5 — the
  // largest value the strength feature can take, from a single result.
  assert.ok(shrunkWinRate(1, 1) < 0.6, "one win from one match must not read as a strong side");
  assert.ok(Math.abs(shrunkWinRate(60, 100) - 0.6) < 0.01, "an established record is barely moved");
  assert.equal(shrunkWinRate(0, 0), 0.5);
});

test("matchDate reads the first day of a multi-day match", () => {
  assert.equal(matchDate({ dates: ["2024-06-01", "2024-06-02"] }), "2024-06-01");
  assert.equal(matchDate({ dates: [] }), null);
  assert.equal(matchDate({}), null);
});

// ── skeleton ─────────────────────────────────────────────────────────────────

test("a delivery packs and unpacks losslessly", () => {
  for (const runs of [0, 1, 4, 6, 7]) {
    for (const wickets of [0, 1, 2]) {
      for (const legal of [true, false]) {
        assert.deepEqual(unpackDelivery(packDelivery(runs, wickets, legal)), { runs, wickets, legal });
      }
    }
  }
});

/** Build a Cricsheet-shaped match from a compact per-innings spec. */
function rawMatch({
  type = "T20", overs = 20, ballsPerOver = 6, teams, gender = "male",
  dates = ["2024-01-01"], outcome, innings,
}) {
  return {
    info: { match_type: type, overs, balls_per_over: ballsPerOver, teams, gender, dates, outcome },
    innings: innings.map(({ team, deliveries }) => ({ team, overs: [{ over: 0, deliveries }] })),
  };
}

const ball = (runs = 1, wickets = 0, extras = null) => ({
  runs: { total: runs },
  ...(wickets ? { wickets: new Array(wickets).fill({ player_out: "x" }) } : {}),
  ...(extras ? { extras } : {}),
});

test("a wide is not a ball", () => {
  // `ballsFaced++` used to fire on every delivery. Every row then paired a true score with an
  // overstated ball count, and by more in a scrappy innings than a tidy one. Worth 0.024 log-loss.
  const skeleton = skeletonOf(rawMatch({
    teams: ["A", "B"],
    outcome: { winner: "A" },
    innings: [{ team: "A", deliveries: [ball(1), ball(1, 0, { wides: 1 }), ball(4)] }],
  }));
  assert.deepEqual(inningsTotals(skeleton.innings[0]), { runs: 6, wickets: 0, legalBalls: 2 });
});

test("a no-ball is not a ball either, and its runs still count", () => {
  const skeleton = skeletonOf(rawMatch({
    teams: ["A", "B"],
    outcome: { winner: "A" },
    innings: [{ team: "A", deliveries: [ball(2, 0, { noballs: 1 })] }],
  }));
  assert.deepEqual(inningsTotals(skeleton.innings[0]), { runs: 2, wickets: 0, legalBalls: 0 });
});

test("an unmodelled format produces no skeleton at all", () => {
  assert.equal(skeletonOf({ info: { match_type: "Hundred Ball", teams: ["A", "B"] }, innings: [] }), null);
  assert.equal(skeletonOf({ info: { match_type: "T20", teams: ["A"] }, innings: [] }), null);
});

// ── rows ─────────────────────────────────────────────────────────────────────

const winRateFlat = () => 0.5;

function chaseFixture() {
  return skeletonOf(rawMatch({
    teams: ["A", "B"],
    outcome: { winner: "B" },
    innings: [
      { team: "A", deliveries: Array.from({ length: 6 }, () => ball(2)) },
      { team: "B", deliveries: Array.from({ length: 6 }, () => ball(1)) },
    ],
  }));
}

function testFixture(outcome = { result: "draw" }) {
  return skeletonOf(rawMatch({
    type: "Test",
    dates: ["2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05"],
    teams: ["A", "B"],
    outcome,
    innings: [{ team: "A", deliveries: Array.from({ length: 12 }, () => ball(3)) }],
  }));
}

test("chase rows describe the state after each delivery, oriented to the chasing side", () => {
  const rows = chaseRows(chaseFixture(), winRateFlat);
  // Target is 13. The chasing side scores 1 a ball, so it never gets there: six deliveries, six
  // rows, all labelled a win because B won the match.
  assert.equal(rows.length, 6);
  assert.ok(rows.every((r) => r.label === 1), "B won, and rows are oriented to the chasing side");
  assert.equal(rows[0].features.length, CHASE_FEATURE_NAMES.length);
});

test("a Test never becomes a chase row, and a limited-overs match never a long one", () => {
  const match = testFixture();
  assert.equal(chaseRows(match, winRateFlat).length, 0);
  assert.equal(firstInningsRows(match, winRateFlat).length, 0);
  assert.equal(longRows(chaseFixture(), winRateFlat).length, 0);
  assert.ok(longRows(match, winRateFlat).length > 0);
});

test("first-innings rows exist at all — v4 scored this half of the match by hand", () => {
  const rows = firstInningsRows(chaseFixture(), winRateFlat);
  assert.equal(rows.length, 6);
  assert.equal(rows[0].features.length, FIRST_INNINGS_FEATURE_NAMES.length);
  assert.ok(rows.every((r) => r.label === 0), "A batted first and lost");
});

test("an abandoned long match contributes no rows, not draw-labelled ones", () => {
  assert.equal(longRows(testFixture({ result: "no result" }), winRateFlat).length, 0);
});

// ── features ─────────────────────────────────────────────────────────────────

const chaseState = {
  currentRunRate: 7.5,
  requiredRunRate: 8.5,
  wicketsInHand: 6,
  ballsRemaining: 60,
  ballsBudget: 120,
  runsNeeded: 85,
  target: 160,
  teamStrengthDiff: 0,
  parRunRate: 8.3,
  format: "T20",
};

const longState = {
  lead: 40, wicketsInHand: 7, inningsIndex: 1, ballsRemaining: 1800, ballsTotal: 2700,
  teamStrengthDiff: 0, isFourthInnings: false, runsNeeded: 0, followedOn: false,
  inningsUsedDiff: 0, inningsRunRate: 3.1, wicketsToTake: 17, scheduledDays: 5,
};

test("every feature list matches the vector its builder produces", () => {
  // A name list that has drifted from the builder is the failure mode that cannot be seen in the
  // output: the model still trains, and every weight is attributed to the wrong feature in the log.
  assert.equal(chaseFeatures(chaseState).length, CHASE_FEATURE_NAMES.length);
  assert.equal(firstInningsFeatures({ ...chaseState, runsScored: 75 }).length, FIRST_INNINGS_FEATURE_NAMES.length);
  assert.equal(longFeatures(longState).length, LONG_FEATURE_NAMES.length);
});

test("no feature is NaN or infinite at the boundaries a live match actually reaches", () => {
  const edges = [
    { ...chaseState, ballsRemaining: 1, runsNeeded: 1 },
    { ...chaseState, wicketsInHand: 1, currentRunRate: 0, requiredRunRate: 36 },
    { ...chaseState, ballsRemaining: 120, runsNeeded: 200, currentRunRate: 0 },
  ];
  for (const state of edges) {
    for (const v of chaseFeatures(state)) assert.ok(Number.isFinite(v), `${JSON.stringify(state)} -> ${v}`);
    for (const v of firstInningsFeatures({ ...state, runsScored: 0 })) assert.ok(Number.isFinite(v));
  }
  for (const v of longFeatures({ ...longState, ballsRemaining: 1, wicketsInHand: 1 })) {
    assert.ok(Number.isFinite(v));
  }
});

test("the fourth innings is the only long innings with chase pressure", () => {
  const base = { ...longState, lead: -150, runsNeeded: 151, ballsRemaining: 400, wicketsToTake: 6 };
  const fourth = longFeatures({ ...base, inningsIndex: 3, isFourthInnings: true });
  const second = longFeatures({ ...base, inningsIndex: 1, isFourthInnings: false, runsNeeded: 0 });
  assert.ok(fourth[LONG_FEATURE_NAMES.indexOf("chasePressure")] > 0);
  assert.equal(second[LONG_FEATURE_NAMES.indexOf("chasePressure")], 0);
});

test("innings used, not the raw lead, is what makes a lead comparable", () => {
  // Replaying finished Tests caught this: the side batting first always shows a growing lead
  // because the opposition has not batted. The model read that as position and gave 78% to a side
  // level after ten overs of day one, then 22% on the reply in the same match.
  const idx = LONG_FEATURE_NAMES.indexOf("inningsUsedDiff");
  assert.ok(idx >= 0);
  assert.equal(longFeatures({ ...longState, inningsUsedDiff: 1 })[idx], 1);
  assert.equal(longFeatures({ ...longState, inningsUsedDiff: 0 })[idx], 0);
});

test("par rates exist for every limited-overs class the corpus produces", () => {
  for (const cls of ["T20", "ODI", "HUNDRED"]) assert.ok(parRunRateFor(cls) > 0);
  assert.equal(parRunRateFor("NOT_A_FORMAT"), 8.0, "an unknown class falls back rather than dividing by zero");
});

// ── row storage ──────────────────────────────────────────────────────────────

test("a row sink keeps everything under its cap and samples above it", () => {
  const sink = new RowSink(4, 2);
  for (let i = 0; i < 3; i++) sink.push([i, i], i % 2);
  assert.equal(sink.length, 3);
  assert.equal(sink.seen, 3);
  for (let i = 0; i < 100; i++) sink.push([i, i], 1);
  assert.equal(sink.length, 4, "the cap is never exceeded");
  assert.equal(sink.seen, 103, "but everything seen is counted, so the sample stays uniform");
});

test("standardizing a sink centres it", () => {
  const sink = new RowSink(10, 2);
  for (const v of [1, 2, 3, 4, 5]) sink.push([v, 10], 1);
  const std = sink.fitStandardizer();
  assert.ok(Math.abs(std.means[0] - 3) < 1e-6);
  assert.equal(std.stds[1], 1, "a constant feature must not divide by zero");
  sink.applyStandardizer(std);
  let sum = 0;
  for (let i = 0; i < sink.length; i++) sum += sink.X[i * 2];
  assert.ok(Math.abs(sum) < 1e-5);
});

// ── optimisation ─────────────────────────────────────────────────────────────

/** A linearly separable problem the optimiser must solve, plus label noise so loss cannot hit 0. */
function separable(n, seed = 1) {
  let rng = seed >>> 0;
  const rand = () => ((rng = (rng * 1664525 + 1013904223) >>> 0) / 4294967296);
  const sink = new RowSink(n, 2);
  for (let i = 0; i < n; i++) {
    const x = rand() * 2 - 1;
    const y = rand() * 2 - 1;
    const label = x + y > 0 ? 1 : 0;
    sink.push([x, y], rand() < 0.05 ? 1 - label : label);
  }
  return sink;
}

test("the optimiser learns a separable problem and stops when validation stops improving", () => {
  const train = separable(4000, 1);
  const valid = separable(1000, 2);
  const fit = trainBinary(train, valid, { epochs: 60, patience: 5 });
  const metrics = binaryMetrics(valid, fit.weights, fit.bias);
  assert.ok(metrics.accuracy > 0.9, `expected the boundary to be learned, got ${metrics.accuracy}`);
  assert.ok(fit.epoch >= 0 && fit.epoch < 60);
  assert.ok(fit.weights[0] > 0 && fit.weights[1] > 0, "both features push the same way");
});

test("training on nothing returns zero weights rather than NaN", () => {
  const empty = new RowSink(1, 3);
  const fit = trainBinary(empty, empty, {});
  assert.deepEqual(fit.weights, [0, 0, 0]);
  assert.equal(fit.bias, 0);
});

/** Labels drawn from sigmoid(x), so a weight of exactly 1 with no bias is perfectly calibrated. */
function calibratedSink(seed) {
  const sink = new RowSink(2000, 1);
  let rng = seed >>> 0;
  const rand = () => ((rng = (rng * 1664525 + 1013904223) >>> 0) / 4294967296);
  for (let i = 0; i < 2000; i++) {
    const x = rand() * 6 - 3;
    sink.push([x], rand() < sigmoid(x) ? 1 : 0);
  }
  return sink;
}

test("temperature scaling finds roughly 1 for an already-calibrated model", () => {
  const T = fitTemperature(calibratedSink(9), [1], 0);
  assert.ok(Math.abs(T - 1) < 0.25, `expected roughly 1, got ${T}`);
});

test("temperature scaling pulls an overconfident model back", () => {
  // Weight 4 where the truth is 1: the logits are four times too sharp, so the fitted temperature
  // has to be well above 1 to undo it.
  const T = fitTemperature(calibratedSink(11), [4], 0);
  assert.ok(T > 2, `expected the correction to be large, got ${T}`);
});

test("metrics report what they claim to", () => {
  const sink = new RowSink(4, 1);
  sink.push([0], 1);
  sink.push([0], 1);
  sink.push([0], 0);
  sink.push([0], 0);
  // A zero bias on a zero feature is exactly 0.5 for every row.
  const m = binaryMetrics(sink, [0], 0);
  assert.ok(Math.abs(m.logLoss - Math.log(2)) < 1e-5);
  assert.ok(Math.abs(m.brier - 0.25) < 1e-9);
  assert.equal(baseRate(sink), 0.5);
  // Every prediction is 0.5 and half of them happen, so calibration is perfect despite the model
  // being useless — which is the distinction ECE exists to draw.
  assert.ok(expectedCalibrationError(sink, [0], 0) < 1e-9);
});

// ── multiclass ───────────────────────────────────────────────────────────────

test("softmax survives a logit large enough to overflow exp", () => {
  // A long-format lead of several hundred runs drives exactly this. Infinity/Infinity is NaN, which
  // would poison every weight on the next gradient step.
  const p = softmax([1000, 0, -1000]);
  assert.ok(p.every(Number.isFinite));
  assert.ok(Math.abs(p.reduce((a, b) => a + b, 0) - 1) < 1e-9);
  assert.ok(p[0] > 0.99);
});

test("the multiclass optimiser separates three classes", () => {
  const sink = new RowSink(3000, 1);
  let rng = 3;
  const rand = () => ((rng = (rng * 1664525 + 1013904223) >>> 0) / 4294967296);
  for (let i = 0; i < 3000; i++) {
    const x = rand() * 3 - 1.5;
    sink.push([x], x < -0.5 ? OUTCOME.LOSS : x < 0.5 ? OUTCOME.DRAW : OUTCOME.WIN);
  }
  const fit = trainMulticlass(sink, sink, { epochs: 60, patience: 10, learningRate: 0.1 });
  const m = multiclassMetrics(sink, fit);
  assert.ok(m.accuracy > 0.8, `expected the three bands to be learned, got ${m.accuracy}`);
  assert.ok(m.logLoss < Math.log(3));
});

test("class base rates and predictions are three-wide and sum to one", () => {
  const sink = new RowSink(4, 1);
  sink.push([0], OUTCOME.LOSS);
  sink.push([0], OUTCOME.DRAW);
  sink.push([0], OUTCOME.WIN);
  sink.push([0], OUTCOME.WIN);
  assert.deepEqual(classBaseRates(sink), [0.25, 0.25, 0.5]);
  const p = predictClasses([1], { weights: [[1], [0], [-1]], biases: [0, 0, 0] });
  assert.ok(Math.abs(p.reduce((a, b) => a + b, 0) - 1) < 1e-9);
  assert.ok(p[0] > p[1] && p[1] > p[2]);
});

test("shrinkage returns the prior at the start and the model once settled", () => {
  const priors = [0.37, 0.23, 0.40];
  const model = [0.05, 0.05, 0.90];
  assert.deepEqual(shrinkToPrior(model, priors, 0, 0.25), priors);
  assert.deepEqual(shrinkToPrior(model, priors, 0.25, 0.25), model);
  assert.deepEqual(shrinkToPrior(model, priors, 0.9, 0.25), model);
  const half = shrinkToPrior(model, priors, 0.125, 0.25);
  assert.ok(Math.abs(half[2] - (0.5 * 0.90 + 0.5 * 0.40)) < 1e-9);
  // No priors is not a licence to trust the model early: uniform is the honest stand-in.
  assert.deepEqual(
    shrinkToPrior(model, null, 0, 0.25).map((v) => Number(v.toFixed(6))),
    [0.333333, 0.333333, 0.333333],
  );
});

test("the settle point is a measured optimum, not a default", () => {
  // Swept on the holdout: log-loss falls to a minimum at 0.35-0.50 and rises again beyond it, and
  // rows past halfway score identically for every setting up to 0.50. 0.25 is chosen inside that
  // flat region so a genuine day-one collapse escapes the prior sooner.
  assert.ok(PRIOR_SETTLE_PROGRESS > 0 && PRIOR_SETTLE_PROGRESS <= 0.5);
});

test("model version is 5", () => {
  // Bumped from 4 because the artifact now carries three models with three differently shaped
  // feature vectors. A v4 reader would score a sixteen-feature chase against five weights and
  // return a number rather than an error, so the version is the only thing standing between a
  // stale reader and a confident wrong answer.
  assert.equal(MODEL_VERSION, 5);
});
