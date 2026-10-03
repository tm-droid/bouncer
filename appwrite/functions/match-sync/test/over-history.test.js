import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { parseOverHistory } from "../src/cricbuzz.js";
import { mergeOverHistory } from "../src/main.js";

// Captured live page (Durham, second innings, overs 47-49) — the same markup the Kotlin
// commentary tests pin.
const fixture = (name) =>
  readFileSync(new URL(`../../../../app/src/test/resources/${name}`, import.meta.url), "utf8");

test("each over separator on the page becomes one point, with its innings", () => {
  assert.deepEqual(parseOverHistory(fixture("cricbuzz_commentary_overball.html")), [
    { innings: 2, over: 47, runs: 4, score: 295, wickets: 7, team: "DUR" },
    { innings: 2, over: 48, runs: 9, score: 304, wickets: 7, team: "DUR" },
    { innings: 2, over: 49, runs: 15, score: 319, wickets: 7, team: "DUR" },
  ]);
});

test("a format without overs has no points, not invented ones", () => {
  assert.deepEqual(parseOverHistory(fixture("cricbuzz_commentary_hundred.html")), []);
});

test("merging keeps every innings' overs, sorted, the newer reading winning", () => {
  const stored = [
    { innings: 1, over: 1, runs: 4, score: 4, wickets: 0, team: "A" },
    { innings: 1, over: 2, runs: 3, score: 7, wickets: 0, team: "A" },
  ];
  const fresh = [
    { innings: 1, over: 2, runs: 5, score: 9, wickets: 0, team: "A" },
    { innings: 2, over: 1, runs: 6, score: 6, wickets: 1, team: "B" },
  ];
  assert.deepEqual(mergeOverHistory(stored, fresh), [
    { innings: 1, over: 1, runs: 4, score: 4, wickets: 0, team: "A" },
    { innings: 1, over: 2, runs: 5, score: 9, wickets: 0, team: "A" },
    { innings: 2, over: 1, runs: 6, score: 6, wickets: 1, team: "B" },
  ]);
});

test("merging ignores malformed stored entries", () => {
  assert.deepEqual(mergeOverHistory([null, { over: 1 }, "x"], []), []);
});

test("the newest over of the innings is stamped once, and a later reading of it keeps the stamp", async () => {
  const { stampProbability } = await import("../src/main.js");
  const history = [
    { innings: 1, over: 1, runs: 4, score: 4, wickets: 0, team: "A" },
    { innings: 1, over: 2, runs: 3, score: 7, wickets: 0, team: "A" },
  ];
  stampProbability(history, 1, 0.61234);
  assert.equal(history[1].p, 0.612);
  assert.equal(history[0].p, undefined, "an older over is not given a later moment's number");
  stampProbability(history, 1, 0.7);
  assert.equal(history[1].p, 0.612, "stamped once");

  const fresh = [{ innings: 1, over: 2, runs: 5, score: 9, wickets: 0, team: "A" }];
  const merged = mergeOverHistory(history, fresh);
  assert.deepEqual(merged[1], { innings: 1, over: 2, runs: 5, score: 9, wickets: 0, team: "A", p: 0.612 });

  stampProbability(history, 2, 0.4);
  stampProbability(history, null, 0.4);
  stampProbability(history, 1, NaN);
  stampProbability(undefined, 1, 0.4);
  assert.equal(history.filter((e) => e.p != null).length, 1, "no over of that innings, no innings, no number: nothing stamped");
});
