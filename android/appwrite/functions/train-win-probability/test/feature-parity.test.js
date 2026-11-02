import test from "node:test";
import assert from "node:assert/strict";
import * as trainer from "../src/training.js";
import * as reader from "../../match-sync/src/winprob.js";

/**
 * The trainer writes the feature vector; `winprob.js` reads it back at inference. They live in
 * separate Appwrite function archives with no shared package, so the vectors are duplicated code,
 * and a drift between them is the worst kind of bug this system can have: nothing throws, nothing
 * logs, the model simply scores a live match against weights fitted for a different quantity.
 *
 * These assertions are the only thing standing between an edit to one copy and a silently wrong
 * probability. Run over a grid of states rather than one, because a reordered pair of features
 * agrees on any state where the two happen to be equal.
 */

const CHASE_STATES = [];
for (const currentRunRate of [0, 4.2, 7.5, 13.1]) {
  for (const requiredRunRate of [0.1, 6, 8.5, 18]) {
    for (const wicketsInHand of [1, 6, 10]) {
      for (const [ballsRemaining, ballsBudget, format] of [[1, 120, "T20"], [60, 120, "T20"], [180, 300, "ODI"], [40, 100, "HUNDRED"]]) {
        CHASE_STATES.push({
          currentRunRate,
          requiredRunRate,
          wicketsInHand,
          ballsRemaining,
          ballsBudget,
          runsNeeded: Math.round((requiredRunRate * ballsRemaining) / 6),
          runsScored: 64,
          target: 64 + Math.round((requiredRunRate * ballsRemaining) / 6),
          teamStrengthDiff: 0.17,
          parRunRate: trainer.parRunRateFor(format),
          format,
        });
      }
    }
  }
}

const LONG_STATES = [];
for (const lead of [-320, -40, 0, 85, 410]) {
  for (const wicketsInHand of [1, 5, 10]) {
    for (const inningsIndex of [0, 1, 2, 3]) {
      for (const ballsRemaining of [12, 900, 2600]) {
        LONG_STATES.push({
          lead,
          wicketsInHand,
          inningsIndex,
          ballsRemaining,
          ballsTotal: 2700,
          teamStrengthDiff: -0.08,
          isFourthInnings: inningsIndex === 3,
          runsNeeded: inningsIndex === 3 ? Math.max(1 - lead, 0) : 0,
          followedOn: inningsIndex >= 2,
          inningsUsedDiff: inningsIndex % 2 === 0 ? 1 : 0,
          inningsRunRate: 3.4,
          wicketsToTake: wicketsInHand + (inningsIndex < 2 ? 10 : 0),
          scheduledDays: 5,
        });
      }
    }
  }
}

function assertSameVector(a, b, label, state) {
  assert.equal(a.length, b.length, `${label}: length differs for ${JSON.stringify(state)}`);
  for (let i = 0; i < a.length; i++) {
    assert.ok(
      Object.is(a[i], b[i]) || Math.abs(a[i] - b[i]) < 1e-12,
      `${label}: feature ${i} differs (${a[i]} vs ${b[i]}) for ${JSON.stringify(state)}`,
    );
  }
}

test("the chase feature vector is identical in the trainer and the reader", () => {
  for (const state of CHASE_STATES) {
    assertSameVector(trainer.chaseFeatures(state), reader.chaseFeatures(state), "chase", state);
  }
});

test("the first-innings feature vector is identical in the trainer and the reader", () => {
  for (const state of CHASE_STATES) {
    assertSameVector(
      trainer.firstInningsFeatures(state),
      reader.firstInningsFeatures(state),
      "firstInnings",
      state,
    );
  }
});

test("the long-format feature vector is identical in the trainer and the reader", () => {
  for (const state of LONG_STATES) {
    assertSameVector(trainer.longFeatures(state), reader.longFeatures(state), "long", state);
  }
});

test("the reader expects exactly as many features as the trainer emits", () => {
  // winprob.js hardcodes these counts in its `usable` checks, which is what makes it refuse a
  // mismatched artifact instead of scoring against whatever lines up.
  assert.equal(trainer.CHASE_FEATURE_NAMES.length, 16);
  assert.equal(trainer.FIRST_INNINGS_FEATURE_NAMES.length, 11);
  assert.equal(trainer.LONG_FEATURE_NAMES.length, 16);
});

test("the team-stats key is built identically on both sides", () => {
  for (const [team, gender, format] of [
    ["India", "male", "T20"], ["Australia", "female", "ODI"], ["  Kent  ", "male", "TEST"],
    ["London Spirit", "female", "HUNDRED"],
  ]) {
    assert.equal(trainer.teamKey(team, gender, format), reader.teamKey(team, gender, format));
  }
  assert.equal(trainer.teamKey("India"), reader.teamKey("India"), "the defaults must agree too");
});

test("par run rates agree, and the artifact's own copy overrides the reader's constants", () => {
  for (const format of ["T20", "ODI", "HUNDRED", "T10"]) {
    assert.equal(trainer.PAR_RUN_RATE[format], reader.FORMAT_PAR_RUN_RATE[format]);
  }
  // A retrain can move these without a matching deploy of the reader, which is the point of
  // shipping them in the model.
  assert.equal(reader.parRunRate("T20", { parRunRate: { T20: 9.9 } }), 9.9);
  assert.equal(reader.parRunRate("T20", null), trainer.PAR_RUN_RATE.T20);
});

test("prior shrinkage behaves identically in the trainer and the reader", () => {
  const priors = [0.37, 0.23, 0.40];
  const probs = [0.1, 0.2, 0.7];
  for (const progress of [0, 0.1, 0.25, 0.5, 1]) {
    assert.deepEqual(
      trainer.shrinkToPrior(probs, priors, progress, 0.25),
      reader.shrinkToPrior(probs, priors, progress, 0.25),
    );
  }
  assert.equal(reader.MODEL_VERSION, trainer.MODEL_VERSION, "reader and writer must agree on the contract");
});
