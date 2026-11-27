import test from "node:test";
import assert from "node:assert/strict";
import { evaluate, evaluateLong, formatFromText, inningsBalls } from "../src/winprob.js";

// The Test head is fitted on Tests; first-class (Cricsheet class MDM) scores through it too, with
// team strength read from the MDM key. Cricsheet has no T10 class: `formatClassOf` never returns
// it, so no head, par rate or team key exists for T10.

const chase = (format) => ({
  currentRunRate: 8.0,
  requiredRunRate: 8.0,
  runsNeeded: 40,
  ballsRemaining: 30,
  wicketsLost: 3,
  format,
});

const longModel = () => ({
  version: 5,
  teamStats: {},
  long: {
    weights: [new Array(16).fill(0), new Array(16).fill(0), new Array(16).fill(0)],
    biases: [0.37, 0.23, 0.4].map(Math.log),
    featureMeans: new Array(16).fill(0),
    featureStds: new Array(16).fill(1),
    classPriors: [0.37, 0.23, 0.4],
    priorSettleProgress: 0.25,
  },
});

const testPosition = {
  lead: 84, wicketsLost: 4, inningsIndex: 2, ballsBowled: 1400, scheduledDays: 5,
  inningsRunRate: 3.4, ownInningsUsed: 2, oppInningsUsed: 1, battingTeam: "England", bowlingTeam: "India",
};

test("format text resolves to the class the trainer used", () => {
  assert.equal(formatFromText("TEST"), "TEST");
  assert.equal(formatFromText("India vs Australia, 1st Test"), "TEST");
  assert.equal(formatFromText("ODI"), "ODI");
  assert.equal(formatFromText("LIST_A"), "ODI");
  assert.equal(formatFromText("T20"), "T20");
  assert.equal(formatFromText("T20I"), "T20");
  assert.equal(formatFromText("HUN"), "HUNDRED");
  assert.equal(formatFromText("The Hundred"), "HUNDRED");
  assert.equal(formatFromText("T10"), "T10");
  assert.equal(formatFromText("Some Trophy Final"), "UNKNOWN");
});

test("first-class is its own format, not a Test", () => {
  assert.equal(formatFromText("FC"), "FC");
  assert.equal(formatFromText("Sheffield Shield, first-class"), "FC");
  assert.equal(formatFromText("County Championship, 4-day"), "FC");
});

test("ball budgets: T20 120, ODI 300, Hundred 100, nothing for Test, FC, T10, UNKNOWN", () => {
  assert.equal(inningsBalls("T20"), 120);
  assert.equal(inningsBalls("ODI"), 300);
  assert.equal(inningsBalls("HUNDRED"), 100);
  for (const f of ["TEST", "FC", "T10", "UNKNOWN"]) assert.equal(inningsBalls(f), null, f);
});

test("trained limited-overs formats are scored", () => {
  for (const f of ["T20", "ODI", "HUNDRED"]) {
    const r = evaluate(chase(f));
    assert.equal(r.undetermined, false, f);
    assert.ok(r.battingTeamWinProbability > 0, f);
  }
});

test("T10 is undetermined, even when a ball total is supplied", () => {
  assert.equal(evaluate(chase("T10")).undetermined, true);
  assert.equal(evaluate({ ...chase("T10"), totalBalls: 60 }).undetermined, true);
});

test("FC and UNKNOWN are undetermined on the limited-overs path", () => {
  assert.equal(evaluate(chase("FC")).undetermined, true);
  assert.equal(evaluate(chase("UNKNOWN")).undetermined, true);
});

// A model that reads teamStrengthDiff (feature index 4) and knows England and India under `key`.
const strengthModel = (key) => {
  const m = longModel();
  m.long.weights[2][4] = 3;
  m.long.weights[0][4] = -3;
  m.teamStats = {
    [`England|male|${key}`]: { winRate: 0.8 },
    [`India|male|${key}`]: { winRate: 0.2 },
  };
  return m;
};

test("the Test head scores a Test and a first-class match as three outcomes", () => {
  const model = longModel();
  assert.equal(evaluateLong({ ...testPosition, format: "TEST", model }).undetermined, false);
  assert.equal(evaluateLong({ ...testPosition, model }).undetermined, false, "default stays TEST");
  const fc = evaluateLong({ ...testPosition, scheduledDays: 4, format: "FC", model });
  assert.equal(fc.undetermined, false);
  assert.ok(fc.drawProbability > 0 && fc.battingTeamWinProbability > 0);
  const loss = 1 - fc.battingTeamWinProbability - fc.drawProbability;
  assert.ok(loss > 0, "a loss class remains, so the three are a real triple");
  assert.ok(fc.battingTeamWinProbability + fc.drawProbability < 1);
});

test("first-class reads team strength from the MDM key, a Test from the TEST key", () => {
  const neutral = evaluateLong({ ...testPosition, format: "FC", model: longModel() });
  const mdm = evaluateLong({ ...testPosition, format: "FC", model: strengthModel("MDM") });
  const testOnly = evaluateLong({ ...testPosition, format: "FC", model: strengthModel("TEST") });
  assert.notEqual(mdm.battingTeamWinProbability, neutral.battingTeamWinProbability);
  assert.equal(testOnly.battingTeamWinProbability, neutral.battingTeamWinProbability);

  const neutralTest = evaluateLong({ ...testPosition, format: "TEST", model: longModel() });
  const testKey = evaluateLong({ ...testPosition, format: "TEST", model: strengthModel("TEST") });
  const mdmOnly = evaluateLong({ ...testPosition, format: "TEST", model: strengthModel("MDM") });
  assert.notEqual(testKey.battingTeamWinProbability, neutralTest.battingTeamWinProbability);
  assert.equal(mdmOnly.battingTeamWinProbability, neutralTest.battingTeamWinProbability);
});

test("the Test head refuses T10 and unknown", () => {
  const model = longModel();
  assert.equal(evaluateLong({ ...testPosition, format: "T10", model }).undetermined, true);
  assert.equal(evaluateLong({ ...testPosition, format: "UNKNOWN", model }).undetermined, true);
});
