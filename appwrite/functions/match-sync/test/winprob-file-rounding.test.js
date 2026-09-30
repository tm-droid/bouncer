import test from "node:test";
import assert from "node:assert/strict";

import { roundFractions } from "../src/main.js";

test("probabilities round to three places, ids and timestamps pass through", () => {
  const entry = { team1Id: 2, team1WinProbability: 0.6234567891, confidence: 0.81, at: 1_790_000_000_000 };
  assert.deepEqual(JSON.parse(JSON.stringify(entry, roundFractions)), {
    team1Id: 2,
    team1WinProbability: 0.623,
    confidence: 0.81,
    at: 1_790_000_000_000,
  });
});

test("a sub-rounding wobble no longer changes the file body", () => {
  const a = JSON.stringify({ m: { team1WinProbability: 0.62341 } }, roundFractions);
  const b = JSON.stringify({ m: { team1WinProbability: 0.62338 } }, roundFractions);
  assert.equal(a, b);
});
