import test from "node:test";
import assert from "node:assert/strict";
import {
  evaluate,
  formatFromText,
  inningsBalls,
  ballsFromOvers,
  MODEL_VERSION,
} from "../src/winprob.js";

const chase = (over) => ({
  currentRunRate: 8.0,
  requiredRunRate: 8.0,
  runsNeeded: 80,
  ballsRemaining: 60,
  wicketsLost: 3,
  format: "T20",
  ...over,
});

test("format resolution returns UNKNOWN instead of guessing T20", () => {
  assert.equal(formatFromText("Test"), "TEST");
  assert.equal(formatFromText("The Hundred"), "HUNDRED");
  assert.equal(formatFromText("T20I"), "T20");
  assert.equal(formatFromText("ODI"), "ODI");
  assert.equal(formatFromText("Some Trophy Final"), "UNKNOWN");
  assert.equal(formatFromText(null, undefined, ""), "UNKNOWN");
});

test("a format with no ball budget is undetermined, not 120 balls", () => {
  assert.equal(inningsBalls("TEST"), null);
  assert.equal(inningsBalls("UNKNOWN"), null);
  for (const format of ["TEST", "UNKNOWN"]) {
    const r = evaluate(chase({ format }));
    assert.equal(r.undetermined, true, `${format} must be undetermined`);
    assert.equal(r.battingTeamWinProbability, null);
  }
});

test("an explicit totalBalls lets a rain-shortened game be scored", () => {
  const r = evaluate(chase({ format: "UNKNOWN", totalBalls: 90 }));
  assert.equal(r.undetermined, false);
  assert.ok(r.battingTeamWinProbability > 0);
});

test("more overs remaining does not lower the batting side's probability", () => {
  // The old legacy branch subtracted an overs factor:
  //   sigmoid((rrRatio - 1) * 3 + wicketFactor - min(oversRemaining/20, 1) * 0.1)
  // so a chase exactly on par was rated WORSE with 20 overs left than with 2.
  const onPar = { currentRunRate: 8.0, requiredRunRate: 8.0, wicketsLost: 3, format: "T20" };
  const early = evaluate({ ...onPar, ballsRemaining: 108, runsNeeded: 144 });
  const late = evaluate({ ...onPar, ballsRemaining: 12, runsNeeded: 16 });
  assert.ok(
    early.battingTeamWinProbability >= late.battingTeamWinProbability - 1e-9,
    `on-par chase should not improve as overs run out: early=${early.battingTeamWinProbability} late=${late.battingTeamWinProbability}`,
  );
});

test("a run-rate gap counts for more as the innings runs out", () => {
  const behind = { currentRunRate: 6.0, requiredRunRate: 9.0, wicketsLost: 3, format: "T20" };
  const early = evaluate({ ...behind, ballsRemaining: 90, runsNeeded: 135 });
  const late = evaluate({ ...behind, ballsRemaining: 12, runsNeeded: 18 });
  assert.ok(
    late.battingTeamWinProbability < early.battingTeamWinProbability,
    "being behind the rate should hurt more with fewer balls left",
  );
});

test("losing wickets always lowers the probability", () => {
  // The old wicket term `(wicketsInHand / 10) * 0.3` was a bonus that never went negative, so
  // nine down still scored above an even baseline.
  let previous = Infinity;
  for (const wicketsLost of [0, 2, 4, 6, 8, 9]) {
    const p = evaluate(chase({ wicketsLost })).battingTeamWinProbability;
    assert.ok(p <= previous + 1e-9, `wicketsLost=${wicketsLost} raised probability`);
    previous = p;
  }
});

test("all ten wickets down is a loss, not a mid-range number", () => {
  const r = evaluate(chase({ wicketsLost: 10 }));
  assert.equal(r.undetermined, false);
  assert.ok(r.battingTeamWinProbability <= 0.03 + 1e-9);
});

test("team strength stays inside [0,1] before clamping", () => {
  // The old code did `rawProb = rawProb + (battingWinRate - bowlingWinRate) * 0.1` AFTER the
  // sigmoid, giving a pre-clamp range of [-0.1, 1.1]. Composing in the logit cannot leave [0,1].
  const model = { Strong: { winRate: 1.0 }, Weak: { winRate: 0.0 } };
  const favoured = evaluate(chase({ battingTeam: "Strong", bowlingTeam: "Weak", model }));
  const against = evaluate(chase({ battingTeam: "Weak", bowlingTeam: "Strong", model }));
  for (const r of [favoured, against]) {
    assert.ok(r.battingTeamWinProbability >= 0 && r.battingTeamWinProbability <= 1);
  }
  assert.ok(favoured.battingTeamWinProbability > against.battingTeamWinProbability);
});

test("required rate is continuous — no step at exactly 9.00", () => {
  const at = evaluate(chase({ requiredRunRate: 9.0, currentRunRate: 9.0 })).battingTeamWinProbability;
  const just = evaluate(chase({ requiredRunRate: 9.01, currentRunRate: 9.0 })).battingTeamWinProbability;
  assert.ok(Math.abs(at - just) < 0.02, `discontinuity at 9.00: ${at} vs ${just}`);
});

test("a v2 model is refused on the learned path and falls back to the heuristic", () => {
  // v2 was trained with a hardcoded /120 denominator, so its scaling is T20-only.
  const v2 = {
    version: 2,
    weights: [50, 50, 50, 50, 50],
    bias: -25,
    featureMeans: [0, 0, 0, 0, 0],
    featureStds: [1, 1, 1, 1, 1],
    teamStats: {},
  };
  const withV2 = evaluate(chase({ model: v2 }));
  const withNone = evaluate(chase({ model: null }));
  assert.equal(
    withV2.battingTeamWinProbability,
    withNone.battingTeamWinProbability,
    "a v2 model must be ignored for inference",
  );
});

test("a v3 model drives the learned path and uses a format-correct balls fraction", () => {
  // weights isolate feature[2] (ballsRemainingFraction). With /120 hardcoded, an ODI with 180
  // balls left produced 1.5; the correct value against a 300-ball budget is 0.6.
  const model = {
    version: MODEL_VERSION,
    weights: [0, 0, 1, 0, 0],
    bias: 0,
    featureMeans: [0, 0, 0, 0, 0],
    featureStds: [1, 1, 1, 1, 1],
    teamStats: {},
  };
  const odi = evaluate({
    currentRunRate: 5.5,
    requiredRunRate: 6.0,
    runsNeeded: 180,
    ballsRemaining: 180,
    wicketsLost: 3,
    format: "ODI",
    model,
  });
  const expected = 1 / (1 + Math.exp(-(180 / 300)));
  assert.ok(
    Math.abs(odi.battingTeamWinProbability - expected) < 1e-9,
    `expected ${expected}, got ${odi.battingTeamWinProbability}`,
  );
  assert.ok(odi.battingTeamWinProbability <= 0.97);
});

test("no balls remaining is undetermined rather than an even match", () => {
  const r = evaluate(chase({ ballsRemaining: 0 }));
  assert.equal(r.undetermined, true);
  assert.equal(r.battingTeamWinProbability, null);
});

test("confidence is the distance from even, and zero when undetermined", () => {
  assert.equal(evaluate(chase({ ballsRemaining: 0 })).confidence, 0);
  const r = evaluate(chase({ currentRunRate: 12.0, requiredRunRate: 4.0 }));
  assert.ok(r.confidence > 0.5);
});

test("over notation parses balls, not decimal tenths", () => {
  assert.equal(ballsFromOvers("19.5"), 119);
  assert.equal(ballsFromOvers("19.5 Ov"), 119);
  assert.equal(ballsFromOvers("(12.4)"), 76);
  assert.equal(ballsFromOvers("19"), 114);
  assert.equal(ballsFromOvers("19.7"), null, "a ball digit of 7 is not valid over notation");
  assert.equal(ballsFromOvers(null), null);
  assert.equal(ballsFromOvers("no digits"), null);
});
