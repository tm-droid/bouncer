import test from "node:test";
import assert from "node:assert/strict";

import { matchStarts, startAlert } from "../src/main.js";

const NOW = 1_800_000_000_000;
const m = (id) => ({ id, status: "Toss won", teams: [{ name: "India" }, { name: "Australia" }] });

test("first run after deploy records live matches without announcing them", () => {
  const r = matchStarts({ matches: [m(1), m(2)], previous: undefined, now: NOW });
  assert.equal(r.starts.length, 0);
  assert.deepEqual(Object.keys(r.started).sort(), ["1", "2"]);
});

test("a newly live match is announced once", () => {
  const first = matchStarts({ matches: [m(1)], previous: { 1: NOW - 1000 }, now: NOW });
  assert.equal(first.starts.length, 0);
  const second = matchStarts({ matches: [m(1), m(3)], previous: first.started, now: NOW + 60_000 });
  assert.deepEqual(second.starts.map((x) => x.id), [3]);
  const third = matchStarts({ matches: [m(3)], previous: second.started, now: NOW + 120_000 });
  assert.equal(third.starts.length, 0, "a match that drops off and returns is not re-announced");
});

test("old ids are pruned", () => {
  const r = matchStarts({ matches: [], previous: { 9: NOW - 7 * 24 * 3600 * 1000 }, now: NOW });
  assert.deepEqual(r.started, {});
});

test("start alert names both sides", () => {
  assert.deepEqual(startAlert(m(5)), {
    type: "match_start", match_id: "5", title: "India vs Australia", body: "Toss won", match_title: "India vs Australia",
  });
});
