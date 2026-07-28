import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { evaluate, formatFromText, ballsRemainingFor } from "../src/winprob.js";

const here = (p) => fileURLToPath(new URL(p, import.meta.url));

test("winprob.js is byte-identical to the win-probability copy", () => {
  // The module is duplicated into each function because Appwrite deploys isolated archives with
  // no shared package. This is the guard against the copies drifting.
  const mine = readFileSync(here("../src/winprob.js"), "utf8");
  const canonical = readFileSync(here("../../win-probability/src/winprob.js"), "utf8");
  assert.equal(mine, canonical, "match-sync/src/winprob.js has drifted from win-probability's copy");
});

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

// Mirrors winProbFor in main.js. Kept here rather than exported so main.js stays a function entry
// point; the assertions below are about the orientation rule, which is the part that has bitten us.
function orient(situation, { model = null, title = "" } = {}) {
  const format = formatFromText(situation.matchFormat, title);
  const ballsRemaining = ballsRemainingFor({
    oversRemaining: situation.oversRemaining,
    ballsBowled: situation.ballsBowled,
    format,
  });
  const r = evaluate({
    runsNeeded: situation.runsNeeded ?? 0,
    ballsRemaining: ballsRemaining ?? 0,
    wicketsLost: situation.wicketsLost ?? 0,
    currentRunRate: situation.currentRunRate ?? 0,
    requiredRunRate: situation.requiredRunRate ?? 0,
    battingTeam: situation.battingTeamName ?? "",
    bowlingTeam: situation.bowlingTeamName ?? "",
    format,
    model,
  });
  if (r.undetermined) return null;
  return situation.battingTeamIsTeam1
    ? r.battingTeamWinProbability
    : 1 - r.battingTeamWinProbability;
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

test("a Test is omitted rather than scored", () => {
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

test("a trained v3 model overrides the heuristic", () => {
  const model = {
    version: 3,
    weights: [0, 0, 0, 0, 0],
    bias: 0,
    featureMeans: [0, 0, 0, 0, 0],
    featureStds: [1, 1, 1, 1, 1],
    teamStats: {},
  };
  // All-zero weights and bias produce exactly 0.5 for the batting side, which the heuristic
  // would never return for this situation.
  const withModel = orient(chaseSituation, { model });
  const heuristic = orient(chaseSituation);
  assert.ok(Math.abs(withModel - 0.5) < 1e-9, `model path not taken: ${withModel}`);
  assert.notEqual(withModel, heuristic);
});
