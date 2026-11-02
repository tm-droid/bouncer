import test from "node:test";
import assert from "node:assert/strict";

import { CACHE_TTL_MS, cacheAgeMs, isFresh, staleFallback } from "../../src/news/newsResult.js";

// Pinning the degradation contract behind the 2026-07-30 outage: the Cricbuzz RapidAPI plan
// answered 429 for five days, and news-sync returned that 429 while holding a perfectly readable
// cached payload. The device rendered a blank tab.

const NOW = 1_754_400_000_000; // fixed clock — Date.now() is never called by these functions
const payload = { storyList: [{ story: { id: 1, hline: "Kohli tons up" } }] };
const cacheWrittenAgo = (ms) => ({ fetchedAt: new Date(NOW - ms).toISOString(), response: payload });

test("a cache inside the TTL is fresh and carries no staleness marker", () => {
  const cached = cacheWrittenAgo(30 * 60 * 1000);
  assert.equal(isFresh(cached, NOW), true);
  assert.equal(cacheAgeMs(cached, NOW), 30 * 60 * 1000);
});

test("a cache past the TTL is not fresh", () => {
  assert.equal(isFresh(cacheWrittenAgo(CACHE_TTL_MS + 1), NOW), false);
});

test("upstream failure with a stale cache serves the cache, marked and aged", () => {
  const fiveDays = 5 * 24 * 60 * 60 * 1000;
  const served = staleFallback(cacheWrittenAgo(fiveDays), NOW);

  assert.deepEqual(served.storyList, payload.storyList);
  assert.equal(served._bouncerStale, true);
  assert.equal(served._bouncerCacheAgeMs, fiveDays);
});

test("upstream failure with no cache at all returns null so the caller surfaces the real error", () => {
  assert.equal(staleFallback(null, NOW), null);
  assert.equal(staleFallback({}, NOW), null);
  assert.equal(staleFallback({ fetchedAt: new Date(NOW).toISOString() }, NOW), null);
});

// A NaN age survives JSON.stringify as `null`, which the client cannot tell apart from "fresh".
// So an unreadable timestamp must still serve the payload, but must not claim an age for it.
test("an unreadable fetchedAt still serves, flagged stale, with no age claimed", () => {
  for (const fetchedAt of [undefined, "not a date", ""]) {
    const served = staleFallback({ fetchedAt, response: payload }, NOW);
    assert.equal(served._bouncerStale, true, `fetchedAt=${fetchedAt}`);
    assert.equal("_bouncerCacheAgeMs" in served, false, `fetchedAt=${fetchedAt}`);
  }
});

test("a fetchedAt in the future is treated as unknown, not as a negative age", () => {
  const served = staleFallback({ fetchedAt: new Date(NOW + 60_000).toISOString(), response: payload }, NOW);
  assert.equal(served._bouncerStale, true);
  assert.equal("_bouncerCacheAgeMs" in served, false);
});

test("the fallback does not mutate the cached payload", () => {
  const cached = cacheWrittenAgo(CACHE_TTL_MS * 2);
  staleFallback(cached, NOW);
  assert.equal("_bouncerStale" in cached.response, false);
});

test("a non-object cached response is not served", () => {
  assert.equal(staleFallback({ fetchedAt: new Date(NOW).toISOString(), response: "oops" }, NOW), null);
  assert.equal(staleFallback({ fetchedAt: new Date(NOW).toISOString(), response: [1, 2] }, NOW), null);
});
