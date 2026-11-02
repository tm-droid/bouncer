import test from "node:test";
import assert from "node:assert/strict";
import {
  MANIFEST_MAX_AGE_MS,
  WRITE_LIMIT,
  cycleBudget,
  finalIdsOldestFirst,
  inWindow,
  planRun,
} from "../../src/prune/budget.js";

const cycle = { start: "2026-10-14T00:00:00.000Z", end: "2026-11-13T00:00:00.000Z", commentaryRows: 120_000 };
const at = (iso) => Date.parse(iso);
const manifest = (now, extra = {}) => ({ updated: new Date(now - 3_600_000).toISOString(), watermark: "2026-11-12T17:00:00Z", cycle, ...extra });

test("the window is the cycle's last twelve hours, stopping a quarter-hour short", () => {
  assert.equal(inWindow(cycle, at("2026-11-12T11:59:00Z")), false);
  assert.equal(inWindow(cycle, at("2026-11-12T12:00:00Z")), true);
  assert.equal(inWindow(cycle, at("2026-11-12T23:44:00Z")), true);
  assert.equal(inWindow(cycle, at("2026-11-12T23:45:00Z")), false);
  assert.equal(inWindow({}, at("2026-11-12T20:00:00Z")), false);
});

test("the budget is 90% of what the estimate leaves, never negative", () => {
  // 120k commentary rows -> 138k estimated writes; 250k - 138k - 5k reserve = 107k; 90% = 96,300.
  assert.equal(cycleBudget(120_000), 96_300);
  assert.equal(cycleBudget(300_000), 0);
  // Spending it all still lands under the limit with the estimate 10% low on the other writes.
  const spent = Math.ceil(120_000 * 1.15) + cycleBudget(120_000);
  assert.ok(spent < WRITE_LIMIT);
});

test("one budget per cycle: later runs spend what the first fixed, they do not recompute it", () => {
  const now = at("2026-11-12T20:30:00Z");
  const first = planRun({ manifest: manifest(now), state: null, now });
  assert.equal(first.action, "prune");
  assert.equal(first.remaining, 96_300);
  // The count grew by evening; the budget did not.
  const later = planRun({
    manifest: manifest(now, { cycle: { ...cycle, commentaryRows: 125_000 } }),
    state: { ...first.state, pruned: 90_000 },
    now,
  });
  assert.equal(later.remaining, 6_300);
  const spent = planRun({ manifest: manifest(now), state: { ...first.state, pruned: 96_300 }, now });
  assert.equal(spent.action, "skip");
});

test("a new cycle starts a new record", () => {
  const now = at("2026-11-12T20:30:00Z");
  const old = { cycleStart: "2026-09-14T00:00:00.000Z", budget: 50_000, pruned: 50_000 };
  assert.equal(planRun({ manifest: manifest(now), state: old, now }).remaining, 96_300);
});

test("no prune on a stale count, without a watermark, or outside the window", () => {
  const now = at("2026-11-12T20:30:00Z");
  assert.equal(planRun({ manifest: manifest(now, { updated: new Date(now - MANIFEST_MAX_AGE_MS - 1).toISOString() }), state: null, now }).action, "skip");
  assert.equal(planRun({ manifest: manifest(now, { watermark: undefined }), state: null, now }).action, "skip");
  assert.equal(planRun({ manifest: manifest(now), state: null, now: at("2026-11-01T00:00:00Z") }).action, "skip");
  assert.equal(planRun({ manifest: null, state: null, now }).action, "skip");
});

test("final ids come oldest month first, oldest match first within a month", () => {
  const months = {
    "2026-10": [["300", "2026-10-20"], ["200", "2026-10-02"]],
    "2026-09": [["100", "2026-09-30"]],
    unknown: [["50", null]],
  };
  assert.deepEqual(finalIdsOldestFirst(months), ["50", "100", "200", "300"]);
  assert.deepEqual(finalIdsOldestFirst({ "2026-10": [["x"], ["1"]] }), ["1"]);
});
