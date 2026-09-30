import test from "node:test";
import assert from "node:assert/strict";

import { scorecardWriteDue, scorecardOutline, SCORECARD_MIN_INTERVAL_MS } from "../src/main.js";

const NOW = 1_800_000_000_000;
const card = (wicketsPerInnings) => ({
  innings: wicketsPerInnings.map((w) => ({ fallOfWickets: Array.from({ length: w }, (_, i) => ({ i })) })),
});

test("outline is innings count and wickets fallen", () => {
  assert.equal(scorecardOutline(card([10, 3])), "2:13");
  assert.equal(scorecardOutline(null), "0:0");
});

test("a ball that changes only figures waits for the interval", () => {
  const previous = { body: "a", outline: "1:2", at: NOW - 60_000 };
  assert.equal(scorecardWriteDue({ previous, body: "b", outline: "1:2", now: NOW }), false);
  assert.equal(
    scorecardWriteDue({ previous, body: "b", outline: "1:2", now: previous.at + SCORECARD_MIN_INTERVAL_MS }),
    true
  );
});

test("a wicket or a new innings writes at once", () => {
  const previous = { body: "a", outline: "1:2", at: NOW - 1_000 };
  assert.equal(scorecardWriteDue({ previous, body: "b", outline: "1:3", now: NOW }), true);
  assert.equal(scorecardWriteDue({ previous, body: "b", outline: "2:0", now: NOW }), true);
});

test("the finalising write is never throttled, but an identical body is never rewritten", () => {
  const previous = { body: "a", outline: "2:20", at: NOW - 1_000 };
  assert.equal(scorecardWriteDue({ previous, body: "b", outline: "2:20", now: NOW, force: true }), true);
  assert.equal(scorecardWriteDue({ previous, body: "a", outline: "2:20", now: NOW, force: true }), false);
});

test("a cold container writes its first scorecard", () => {
  assert.equal(scorecardWriteDue({ previous: undefined, body: "a", outline: "1:0", now: NOW }), true);
});
