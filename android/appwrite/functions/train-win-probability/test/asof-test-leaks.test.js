import test from "node:test";
import assert from "node:assert/strict";
import { LONG_FEATURE_NAMES, longRows, AsOfWinRates, walkLongRows } from "../src/training.js";
import { richSkeletonOf } from "../src/corpus.js";

const ball = (runs = 1) => ({ runs: { total: runs } });
const over = () => Array.from({ length: 6 }, () => ball(1));
const innings = (team, overs = 2) => ({ team, deliveries: Array.from({ length: overs }, over).flat() });
const testMatch = ({ dates = ["2024-01-01", "2024-01-02"], inns, outcome = { winner: "A" }, gender = "male" } = {}) =>
  richSkeletonOf({
    info: { match_type: "Test", overs: 90, balls_per_over: 6, teams: ["A", "B"], gender, dates, outcome },
    innings: inns.map(({ team, deliveries }) => ({ team, overs: [{ over: 0, deliveries }] })),
  });

const col = (name) => LONG_FEATURE_NAMES.indexOf(name);

test("follow-on is flagged only from the innings that is the follow-on (N6/B4a)", () => {
  const m = testMatch({ inns: [innings("A"), innings("B"), innings("B"), innings("A")] });
  const rows = longRows(m, () => 0.5);
  const flags = rows.map((r) => r.features[col("followOn")]);
  // Two overs per innings; rows exist for every completed over.
  assert.equal(rows.length, 8);
  assert.deepEqual(flags, [0, 0, 0, 0, 1, 1, 1, 1], "innings 0 and 1 cannot know a follow-on is coming");
});

test("scheduled length is the schedule, not the days played (N1/B11)", () => {
  const m = testMatch({ dates: ["2024-01-01", "2024-01-02"], inns: [innings("A")] });
  assert.equal(m.scheduledDays, 5, "a Test that ended on day 2 was still scheduled for 5");
  const mdm = richSkeletonOf({
    info: { match_type: "MDM", overs: 90, teams: ["A", "B"], dates: ["2024-01-01", "2024-01-02"] },
    innings: [],
  });
  assert.equal(mdm.scheduledDays, 4);
});

test("Test strength is as-of: a team's own match result never reaches its own row (B4b)", () => {
  const tracker = new AsOfWinRates();
  const first = testMatch({ dates: ["2024-01-01"], inns: [innings("A")], outcome: { winner: "A" } });
  assert.equal(tracker.lookup(first)("A"), 0.5, "first ever match: no history, neutral");
  assert.equal(tracker.lookup(first)("B"), 0.5);
  tracker.record(first);
  const second = testMatch({ dates: ["2024-02-01"], inns: [innings("A")], outcome: { winner: "B" } });
  const rate = tracker.lookup(second);
  assert.ok(rate("A") > 0.5 && rate("B") < 0.5, "second match sees only the first result");
  // The leaky whole-window table would give 0.5 for both (one win each).
  const diffs = longRows(second, rate).map((r) => r.features[col("teamStrengthDiff")]);
  assert.ok(diffs.length > 0 && diffs.every((d) => d > 0));
  // Gender and format are separate keys.
  assert.equal(tracker.lookup(testMatch({ inns: [innings("A")], gender: "female" }))("A"), 0.5);
});

test("the Test row walk gives a match strength without its own result, and not a same-day one (B4b wiring)", () => {
  // Two Tests between the same sides: A wins the first, B the second. A whole-window table would
  // rate both sides 0.5 on both matches; the first match must read the neutral 0.5 prior and the
  // second must see only the first result.
  const m1 = testMatch({ dates: ["2024-01-01"], inns: [innings("A")], outcome: { winner: "A" } });
  const m2 = testMatch({ dates: ["2024-02-01"], inns: [innings("A")], outcome: { winner: "B" } });
  const same = testMatch({ dates: ["2024-02-01"], inns: [innings("A")], outcome: { winner: "B" } });
  const diffsOf = (rows) => rows.map((r) => r.features[col("teamStrengthDiff")]);
  const walked = [...walkLongRows([m1, m2], () => true)];
  assert.ok(diffsOf(walked[0].rows).every((d) => d === 0), "first match: no history, so equal strength");
  assert.ok(diffsOf(walked[1].rows).every((d) => d > 0), "second match: A won the first");
  const sameDay = [...walkLongRows([m2, same], () => true)];
  assert.ok(diffsOf(sameDay[1].rows).every((d) => d === 0), "a same-day result does not count yet");
});
