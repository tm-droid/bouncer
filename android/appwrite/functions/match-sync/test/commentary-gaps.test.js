import { test } from "node:test";
import assert from "node:assert/strict";
import { findCommentaryGaps } from "../src/cricbuzz.js";

const over = (innings, ...labels) => labels.map((ball) => ({ innings, ball }));

test("reports the real hole from match 161777", () => {
  // Verbatim from match-commentary on 2026-09-12: Cricbuzz stalled thirteen minutes, then
  // republished a backlog that jumped 12.5 -> 13.4. 13.1, 13.2 and 13.3 never appeared at all.
  const gaps = findCommentaryGaps(
    over(1, "12.1", "12.2", "12.3", "12.4", "12.5", "13.4", "13.5", "13.6", "14.1"),
  );
  assert.deepEqual(gaps, [{ innings: 1, after: "12.5", before: "13.4", missing: 3 }]);
});

test("a contiguous over reports nothing", () => {
  assert.deepEqual(findCommentaryGaps(over(1, "12.5", "12.6", "13.1", "13.2")), []);
});

test("a repeated label is an extra, not a gap", () => {
  // A wide carries the same ball number as the delivery that follows it, and Cricbuzz republishes
  // earlier deliveries with fresh timestamps. Neither may fire this.
  assert.deepEqual(findCommentaryGaps(over(1, "18.1", "18.1", "18.2", "18.2", "18.3")), []);
});

test("a backwards label is a republish, not a gap", () => {
  assert.deepEqual(findCommentaryGaps(over(1, "12.4", "12.1", "12.2")), []);
  assert.deepEqual(findCommentaryGaps(over(1, "13.2", "12.5", "13.3")), []);
});

test("an innings change is not a gap", () => {
  const balls = [...over(1, "19.5", "19.6"), ...over(2, "0.1", "0.2")];
  assert.deepEqual(findCommentaryGaps(balls), []);
});

test("counts a whole skipped over, and several holes separately", () => {
  assert.deepEqual(findCommentaryGaps(over(1, "12.5", "14.1")), [
    { innings: 1, after: "12.5", before: "14.1", missing: 6 },
  ]);
  assert.deepEqual(findCommentaryGaps(over(1, "1.1", "1.4", "1.5", "3.1")), [
    { innings: 1, after: "1.1", before: "1.4", missing: 2 },
    { innings: 1, after: "1.5", before: "3.1", missing: 6 },
  ]);
});

test("reports a hole once, not against every later ball in the over", () => {
  const gaps = findCommentaryGaps(over(1, "12.5", "13.4", "13.5", "13.6"));
  assert.equal(gaps.length, 1);
});

// The seam is deliberately undercounted by one: whether 12.6 is missing cannot be known from one
// run's batch, because an earlier run may already hold it — which is what happened in the real case.
test("does not count the tail of the source over", () => {
  assert.deepEqual(findCommentaryGaps(over(1, "12.5", "13.1")), []);
});

test("The Hundred's flat counter neither opens nor closes a gap", () => {
  assert.deepEqual(findCommentaryGaps(over(1, "60", "72", "73")), []);
  // And it resets, so a delivery either side of it is not compared across it.
  assert.deepEqual(findCommentaryGaps(over(1, "12.5", "72", "13.4")), []);
});

test("degenerate input returns nothing rather than throwing", () => {
  assert.deepEqual(findCommentaryGaps(undefined), []);
  assert.deepEqual(findCommentaryGaps([]), []);
  assert.deepEqual(findCommentaryGaps([null, {}, { ball: "" }, { ball: "$undefined" }]), []);
  assert.deepEqual(findCommentaryGaps(over(1, "not a ball", "12.1", "12.2")), []);
});

test("a null innings is still one innings, and is compared", () => {
  assert.deepEqual(findCommentaryGaps(over(null, "12.5", "13.4")), [
    { innings: null, after: "12.5", before: "13.4", missing: 3 },
  ]);
});

test("a ball elsewhere in the same batch is not missing", () => {
  // Match 172671, the run of 08:47 on 2026-10-09: all of 26.1 to 26.4 were written, and the run
  // reported 26.3 and 26.4 lost, because the batch is in timestamp order and a 26.5 came early.
  assert.deepEqual(findCommentaryGaps(over(1, "26.1", "26.2", "26.5", "26.6", "26.3", "26.4")), []);
});

test("an out-of-order batch still reports the balls that really are absent", () => {
  assert.deepEqual(
    findCommentaryGaps(over(1, "26.1", "26.2", "26.6", "26.3")),
    [{ innings: 1, after: "26.2", before: "26.6", missing: 2 }],
  );
});

test("the same label in another innings does not fill a hole", () => {
  const balls = [...over(1, "26.3"), ...over(2, "26.1", "26.2", "26.5")];
  assert.deepEqual(findCommentaryGaps(balls), [{ innings: 2, after: "26.2", before: "26.5", missing: 2 }]);
});
