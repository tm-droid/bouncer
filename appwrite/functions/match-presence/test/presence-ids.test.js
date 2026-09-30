import test from "node:test";
import assert from "node:assert/strict";

import { presenceIds } from "../src/main.js";

test("a single matchId still works", () => {
  assert.deepEqual(presenceIds({ matchId: "151543" }), ["151543"]);
});

test("a batch is sanitised, de-duplicated and capped", () => {
  assert.deepEqual(presenceIds({ matchIds: ["1", "1", "2/x", ""], matchId: "3" }), ["1", "2x", "3"]);
  const many = Array.from({ length: 25 }, (_, i) => String(i));
  assert.equal(presenceIds({ matchIds: many }).length, 10);
});

test("nothing usable is empty", () => {
  assert.deepEqual(presenceIds({}), []);
  assert.deepEqual(presenceIds({ matchIds: "not-an-array" }), []);
});
