import test from "node:test";
import assert from "node:assert/strict";

import { shouldPersistState } from "../src/poll.js";

const NOW = 1_800_000_000_000;
const base = {
  live: { hash: "a", writtenAt: NOW - 60_000 },
  recent: { hash: "r", writtenAt: NOW - 60_000 },
  liveCount: 3,
  startedIds: { 1: NOW - 3_600_000 },
};

test("a changed live hash alone waits for the persist interval", () => {
  const next = { ...base, live: { hash: "b", writtenAt: NOW } };
  assert.equal(shouldPersistState({ persisted: base, next, persistedAt: NOW - 60_000, now: NOW }), false);
  assert.equal(shouldPersistState({ persisted: base, next, persistedAt: NOW - 600_000, now: NOW }), true);
});

test("a durable field is written the run it changes", () => {
  const next = { ...base, liveCount: 4 };
  assert.equal(shouldPersistState({ persisted: base, next, persistedAt: NOW, now: NOW }), true);
  const started = { ...base, startedIds: { ...base.startedIds, 2: NOW } };
  assert.equal(shouldPersistState({ persisted: base, next: started, persistedAt: NOW, now: NOW }), true);
});

test("nothing changed, nothing written, however long ago the last write was", () => {
  assert.equal(shouldPersistState({ persisted: base, next: { ...base }, persistedAt: 0, now: NOW }), false);
});

test("a cold container with no file writes on its first change", () => {
  assert.equal(shouldPersistState({ persisted: {}, next: base, persistedAt: 0, now: NOW }), true);
});
