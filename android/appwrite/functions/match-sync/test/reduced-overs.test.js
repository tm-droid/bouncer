import test from "node:test";
import assert from "node:assert/strict";
import { ballsRemainingFor } from "../src/winprob.js";
import { chaseStart, liveSituationArgs } from "../src/main.js";
import { liveSituation } from "../src/cricbuzz.js";

// Fixture evidence (android/app/src/test/resources/cricbuzz_commentary_{overball,hundred}.html):
// `overs` is cricket notation and `ballNbr` is the innings' legal balls bowled (ODI "overs":49.3
// with "ballNbr":297 = 49*6+3; Hundred "overs":16.4 with "ballNbr":100). `oversRem` is
// "$undefined" in all three captures, so its notation is taken from its sibling `overs`.

test("ballNbr is the legal balls bowled in the innings", () => {
  const ms = { inningsId: 2, target: 327, remRunsToWin: 0, matchScoreDetails: { matchFormat: "ODI" } };
  const isl = [{ inningsId: 1, ballNbr: 300 }, { inningsId: 2, ballNbr: 297, score: 326, wickets: 6 }];
  assert.equal(liveSituation(ms, isl).ballsBowled, 297);
});

test("oversRem is cricket notation: 3.4 overs left is 22 balls, not 20", () => {
  assert.equal(ballsRemainingFor({ oversRemaining: 3.4, ballsBowled: 98, format: "T20" }), 22);
  assert.equal(ballsRemainingFor({ oversRemaining: 12.3, ballsBowled: 45, format: "T20" }), 75);
  assert.equal(ballsRemainingFor({ oversRemaining: 16, ballsBowled: 24, format: "T20" }), 96);
});

test("a rain-reduced 15-over chase is served on its 15-over budget when oversRem is absent", () => {
  // 40 balls bowled, 50 needed at a required rate of 6.00: Cricbuzz's own figures leave 50 balls.
  // The T20 budget alone would say 80.
  const s = { matchFormat: "T20", target: 150, runsNeeded: 50, runsScored: 100, wicketsLost: 3, ballsBowled: 40, oversRemaining: null, currentRunRate: 15, requiredRunRate: 6 };
  assert.equal(liveSituationArgs(s, "T20").ballsRemaining, 50);
  // A full-length chase keeps the exact budget.
  const full = { ...s, runsNeeded: 100, requiredRunRate: 7.5 };
  assert.equal(liveSituationArgs(full, "T20").ballsRemaining, 80);
});

test("a reduced first innings that is over is scored as a chase on its own allotment", () => {
  const live = { battingTeamIsTeam1: true, runsScored: 120, wicketsLost: 5, ballsBowled: 90, oversRemaining: 0, target: null, long: null };
  const c = chaseStart(live, "T20");
  assert.equal(c.ballsRemaining, 90);
  assert.equal(c.requiredRunRate, 8.07);
});

test("a full-length chase is not shortened by the feed's 2-dp rounding of the required rate", () => {
  // 1 off 90 in a T20 shows a required rate of 0.07, which implies 86 balls: a rounding gap, not a cut.
  assert.equal(ballsRemainingFor({ ballsBowled: 30, format: "T20", runsNeeded: 1, requiredRunRate: 0.07 }), 90);
  // 50 off 30 at 10.00 is exact and leaves the count alone.
  assert.equal(ballsRemainingFor({ ballsBowled: 90, format: "T20", runsNeeded: 50, requiredRunRate: 10 }), 30);
});
