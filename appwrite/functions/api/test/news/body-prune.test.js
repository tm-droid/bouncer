import { test } from "node:test";
import assert from "node:assert/strict";
import {
  BODY_ID_PREFIX,
  BODY_RETENTION_MS,
  selectBodiesToPrune,
} from "../../src/news/articleBody.js";

const NOW = Date.UTC(2026, 8, 12, 16, 0, 0);
const ago = (ms) => new Date(NOW - ms).toISOString();
const DAY = 24 * 60 * 60 * 1000;

const file = (id, ageMs) => ({ $id: id, $createdAt: ago(ageMs) });
const ids = (files) => selectBodiesToPrune(files, NOW).map((f) => f.$id);

test("selects a body past retention and spares one inside it", () => {
  const files = [
    file("news-body-100", 20 * DAY),
    file("news-body-200", 1 * DAY),
    file("news-body-300", BODY_RETENTION_MS + 1000),
    file("news-body-400", BODY_RETENTION_MS - 1000),
  ];
  assert.deepEqual(ids(files), ["news-body-100", "news-body-300"]);
});

// The reason this function exists. cricket-stats is shared, and every one of these files is
// load-bearing for a different feature: deleting one is a silent outage somewhere else. Storage
// `search` is fuzzy and DOES return news-cache for "news-body" (measured 2026-09-12), so the
// prefix is re-checked here rather than trusted from the query.
test("never selects an infrastructure file, however old", () => {
  const infra = [
    "news-cache", "team-stats", "h2h-stats", "active-series", "player-index",
    "live-matches", "live-winprob", "slow-sync-state", "active-viewers",
    "poll-state", "recent-matches", "upcoming-matches", "live-count", "rate-limit-offenders",
  ].map((id) => file(id, 365 * DAY));
  assert.deepEqual(ids(infra), []);
  // And it still finds the one real body hidden among them.
  assert.deepEqual(ids([...infra, file("news-body-999", 365 * DAY)]), ["news-body-999"]);
});

test("a name that merely contains the prefix is not a body", () => {
  assert.deepEqual(ids([file("cache-news-body-1", 365 * DAY)]), []);
});

test("honours the batch cap", () => {
  const many = Array.from({ length: 200 }, (_, i) => file(`news-body-${i}`, 365 * DAY));
  assert.equal(selectBodiesToPrune(many, NOW).length, 50);
  assert.equal(selectBodiesToPrune(many, NOW, { max: 7 }).length, 7);
});

// An age we cannot read is not an old age. Defaulting the other way would delete a file exactly
// when we understand it least.
test("an unreadable createdAt is never selected", () => {
  const files = [
    { $id: "news-body-1", $createdAt: "not a date" },
    { $id: "news-body-2" },
    { $id: "news-body-3", $createdAt: null },
    // In the future — clock skew, not age.
    { $id: "news-body-4", $createdAt: new Date(NOW + 10 * DAY).toISOString() },
  ];
  assert.deepEqual(ids(files), []);
});

test("degenerate inputs return nothing rather than throwing", () => {
  assert.deepEqual(selectBodiesToPrune(undefined, NOW), []);
  assert.deepEqual(selectBodiesToPrune(null, NOW), []);
  assert.deepEqual(selectBodiesToPrune([], NOW), []);
  assert.deepEqual(selectBodiesToPrune([null, {}, 7, "x"], NOW), []);
});

test("the prefix is exactly what bodyFileId writes", () => {
  assert.equal(BODY_ID_PREFIX, "news-body-");
});
