import { test } from "node:test";
import assert from "node:assert/strict";

import { isTerminalStatus } from "../src/cricbuzz.js";

/**
 * The status classifier that decides when match-sync writes a match's last detail row.
 *
 * A false negative freezes the row mid-innings for good — the defect this exists to fix. A false
 * positive finalises a match that is still being played, which stops nothing (the match keeps
 * syncing) but writes a row early. So the negatives below matter as much as the positives, and
 * every one of them is a real Cricbuzz status line shape.
 */

const TERMINAL = [
  "Southern Brave won by 39 runs",
  "India won by 5 wkts",
  "West Indies won by 6 wickets",
  "Australia won the match by an innings and 12 runs",
  "India beat Australia by 5 wickets",
  "Match drawn",
  "Match tied",
  "Match abandoned due to rain",
  "No result",
  "Complete",
  // Precedence: a result string routinely quotes the in-progress prose around it.
  "Match drawn, England 2nd innings 340/6",
];

const NOT_TERMINAL = [
  // The trap the device classifier was written for: a bare "won" starts a match.
  "India won the toss and elected to bat",
  "Australia opted to bowl",
  "Birmingham Phoenix need 96 runs in 44 balls",
  "India needed 12 more runs",
  "Day 3: Stumps",
  "Day 2: Tea - England lead by 143 runs",
  "Innings Break",
  "Rain delay",
  "Bad light stopped play",
  "Live",
  "Match starts at 19:30 local time",
];

test("a result line is terminal", () => {
  for (const status of TERMINAL) {
    assert.equal(isTerminalStatus(status), true, status);
  }
});

test("a match still being played is not terminal", () => {
  for (const status of NOT_TERMINAL) {
    assert.equal(isTerminalStatus(status), false, status);
  }
});

test("word boundaries hold", () => {
  // "tea" inside "team", "draw" inside "drawing", "beat" inside "beaten up" is fine but
  // "heartbeat" is not a result.
  assert.equal(isTerminalStatus("Both teams are on the field"), false);
  assert.equal(isTerminalStatus("Kohli is drawing the field in"), false);
  assert.equal(isTerminalStatus("Winning feeling"), false);
});

test("absence is not a result", () => {
  // parseLiveDetail returns status: null on a page with no miniscore. Reading that as terminal
  // would finalise every pre-toss match on its first sync.
  assert.equal(isTerminalStatus(null), false);
  assert.equal(isTerminalStatus(undefined), false);
  assert.equal(isTerminalStatus(""), false);
});
