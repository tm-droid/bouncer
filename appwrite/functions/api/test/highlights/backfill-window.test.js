import test from "node:test";
import assert from "node:assert/strict";
import { planDates, isSweep, dedupeById } from "../../src/highlights/main.js";

// BACKFILL_DAYS = 3, WORKING_SLOT_MINUTE = 15, SWEEP_EVERY_HOURS = 4.
// Mirrored here rather than imported: a test that reads the constant it is checking cannot catch a
// wrong constant. The budget these encode is 24 + 6*3 = 42 requests/day against a verified cap of
// 100 (`x-ratelimit-requests-limit: 100`), on a `*/15` schedule that is deliberately not changed.

const at = (iso) => Date.parse(iso);

test("three runs in four do nothing and spend nothing", () => {
  // The `*/15` schedule fires 96 times a day. Fetching on all of them would spend 96 of the 100
  // requests on `today` alone, leaving the look-back permanently starved.
  assert.deepEqual(planDates(at("2026-08-06T09:15:04Z")), []);
  assert.deepEqual(planDates(at("2026-08-06T09:30:12Z")), []);
  assert.deepEqual(planDates(at("2026-08-06T09:45:19Z")), []);
});

test("the top-of-hour run fetches today only", () => {
  assert.deepEqual(planDates(at("2026-08-06T09:00:04Z")), ["2026-08-06"]);
});

test("schedule drift inside the first quarter hour still counts as the working slot", () => {
  // Observed Appwrite start times run seconds late and have been minutes late. Keying on
  // `minute === 0` would drop a working slot entirely whenever that happened.
  assert.deepEqual(planDates(at("2026-08-06T09:00:00Z")), ["2026-08-06"]);
  assert.deepEqual(planDates(at("2026-08-06T09:14:59Z")), ["2026-08-06"]);
});

test("every fourth hour sweeps the back-window, newest first", () => {
  assert.deepEqual(planDates(at("2026-08-06T08:00:03Z")), [
    "2026-08-06",
    "2026-08-05",
    "2026-08-04",
    "2026-08-03",
  ]);
});

test("the sweep hour is the hour, not the slot — a :30 run in a sweep hour still does nothing", () => {
  assert.deepEqual(planDates(at("2026-08-06T08:30:03Z")), []);
});

test("sweeps land six times a day", () => {
  const sweepHours = [];
  for (let h = 0; h < 24; h++) {
    const dates = planDates(at(`2026-08-06T${String(h).padStart(2, "0")}:05:00Z`));
    if (isSweep(dates)) sweepHours.push(h);
  }
  assert.deepEqual(sweepHours, [0, 4, 8, 12, 16, 20]);
  // 24 working slots + 6 sweeps x 3 extra dates = 42 requests/day, cap 100.
  assert.equal(24 + sweepHours.length * 3, 42);
});

test("the back-window crosses a month boundary correctly", () => {
  // Naive day arithmetic on the date string would produce "2026-08-00".
  assert.deepEqual(planDates(at("2026-09-01T00:02:00Z")), [
    "2026-09-01",
    "2026-08-31",
    "2026-08-30",
    "2026-08-29",
  ]);
});

test("the window reaches back far enough for the clips that were actually lost", () => {
  // hl-16287 covers a 2026-08-01 match and was still absent from the table on 2026-08-05 — four
  // days of publication lag is what this has to survive. A 3-day look-back run every 4h covers a
  // clip published any time up to the end of day+3.
  const dates = planDates(at("2026-08-04T08:00:00Z"));
  assert.ok(dates.includes("2026-08-01"));
});

test("a clip appearing under two dates is written once", () => {
  const a = { id: 16292, title: "one" };
  const b = { id: 16292, title: "one again" };
  const c = { id: 16287, title: "two" };
  assert.deepEqual(dedupeById([a, b, c]).map((h) => h.id), [16292, 16287]);
});

test("dedupe keeps the first occurrence, which is the newest date", () => {
  // Dates are fetched newest first, so the first copy carries the freshest metadata.
  const out = dedupeById([{ id: 1, title: "from today" }, { id: 1, title: "from three days ago" }]);
  assert.equal(out[0].title, "from today");
});

test("dedupe on an empty list is empty, not a throw", () => {
  assert.deepEqual(dedupeById([]), []);
});
