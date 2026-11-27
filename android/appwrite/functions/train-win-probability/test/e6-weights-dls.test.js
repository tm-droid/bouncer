import test from "node:test";
import assert from "node:assert/strict";
import { headSinks } from "../src/build.js";
import { matchRowWeights } from "../src/training.js";

const names = ["a", "b"];
const windowOf = (d) => (d >= "2024-12-27" ? "holdout" : d >= "2024-06-01" ? "valid" : "train");
// A match that yields `n` rows regardless of head, so the sinks see only the E6 logic.
const mk = (id, date, n, method = null) => ({ id, date, method, n });
const rowsFor = (m) =>
  Array.from({ length: m.n }, (_, i) => ({ core: [m.n, i], full: [m.n, i], label: i % 2 }));
const sinksFor = (head, matches) =>
  headSinks({ head, coreNames: names, fullNames: names, matches, windowOf, rowsFor });

test("chase rows weigh 1/sqrt(n): a match's weights sum in proportion to sqrt(n); first innings carries none", () => {
  const matches = [mk("m4", "2023-01-01", 4), mk("m16", "2023-01-02", 16), mk("m100", "2023-01-03", 100)];
  const { core } = sinksFor("chase", matches);
  const w = matchRowWeights(core.train, 0.5);
  const sums = new Map();
  for (let r = 0; r < core.train.length; r++) {
    const n = core.train.X[r * 2]; // the stub stores n in column 0
    sums.set(n, (sums.get(n) ?? 0) + w[r]);
  }
  assert.ok(Math.abs(sums.get(16) / sums.get(4) - 2) < 1e-9, "sqrt(16)/sqrt(4) = 2");
  assert.ok(Math.abs(sums.get(100) / sums.get(4) - 5) < 1e-9, "sqrt(100)/sqrt(4) = 5");
  assert.ok(Math.abs(w.reduce((a, b) => a + b, 0) / w.length - 1) < 1e-9, "mean weight 1");
  assert.ok(core.train.matchIds, "chase train sink tracks matches");
  assert.equal(sinksFor("firstInnings", matches).core.train.matchIds, null, "first innings is unweighted");
});

test("a DLS match adds no first-innings train or valid rows, but keeps chase and holdout rows", () => {
  const matches = [
    mk("a", "2023-01-01", 3),
    mk("dls-train", "2023-02-01", 5, "D/L"),
    mk("dls-valid", "2024-07-01", 7, "D/L"),
    mk("dls-hold", "2025-01-10", 11, "D/L"),
  ];
  const fi = sinksFor("firstInnings", matches).core;
  assert.equal(fi.train.length, 3, "DLS train match dropped");
  assert.equal(fi.valid.length, 0, "DLS valid match dropped");
  assert.equal(fi.holdout.length, 11, "DLS holdout match kept");
  const ch = sinksFor("chase", matches).core;
  assert.equal(ch.train.length, 8, "chase keeps the DLS train match");
  assert.equal(ch.valid.length, 7);
  assert.equal(ch.holdout.length, 11);
});
