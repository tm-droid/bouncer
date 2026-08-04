import test from "node:test";
import assert from "node:assert/strict";

import {
  fetchHtml,
  isBlockStatus,
  resetThrottleState,
  CricbuzzBlockedError,
  MIN_REQUEST_GAP_MS,
  BLOCK_BACKOFF_MS,
} from "../src/cricbuzz.js";

// Cricbuzz is the only commentary source this function has, so a block costs a whole tick for every
// live match. ANDROID-35 was four consecutive per-minute 403s that began ~4 minutes after a viewer
// raised one match from one fetch per 45 minutes to two per minute — a rate limiter, reachable by
// our own request pattern.

const realFetch = globalThis.fetch;

function stubFetch(handler) {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, headers: options?.headers ?? {}, at: Date.now() });
    return handler(calls.length, url);
  };
  return calls;
}

const ok = (body = "<html></html>") =>
  new Response(body, { status: 200, headers: { "content-type": "text/html" } });
const fail = (status, body) => new Response(body, { status });

test.beforeEach(() => resetThrottleState());
test.afterEach(() => {
  globalThis.fetch = realFetch;
});

test("requests carry a full browser header set, not a bare User-Agent", async () => {
  const calls = stubFetch(() => ok());
  await fetchHtml("https://www.cricbuzz.com/live-cricket-scores/1");

  const headers = calls[0].headers;
  for (const key of ["User-Agent", "Accept", "Accept-Language", "Referer", "Sec-Fetch-Mode"]) {
    assert.ok(headers[key], `missing ${key}`);
  }
});

test("the User-Agent rotates between requests", async () => {
  // One static UA marks every request from this container as the same client, which is half of
  // what a bot filter keys on.
  const calls = stubFetch(() => ok());
  for (let i = 0; i < 4; i++) {
    await fetchHtml(`https://www.cricbuzz.com/live-cricket-scores/${i}`);
  }

  const agents = calls.map((c) => c.headers["User-Agent"]);
  assert.equal(agents.length, 4);
  assert.ok(new Set(agents).size > 1, `expected rotation, got ${new Set(agents).size} distinct`);
});

test("concurrent callers are spaced instead of bursting", async () => {
  // main.js fans out over up to 12 matches, each fetching a live page and a scorecard — 24
  // simultaneous requests from one IP every 60s before this.
  const calls = stubFetch(() => ok());
  const started = Date.now();
  await Promise.all([1, 2, 3, 4].map((i) => fetchHtml(`https://www.cricbuzz.com/x/${i}`)));

  assert.equal(calls.length, 4);
  const elapsed = Date.now() - started;
  assert.ok(
    elapsed >= MIN_REQUEST_GAP_MS * 3,
    `four requests must span at least three gaps, took ${elapsed}ms`
  );
});

test("a 403 carries the response body, not just the status", async () => {
  stubFetch(() => fail(403, "<html><head><title>Access Denied</title></head><body>blocked</body></html>"));

  await assert.rejects(
    () => fetchHtml("https://www.cricbuzz.com/live-cricket-scores/152507"),
    (e) => {
      assert.match(e.message, /HTTP 403/);
      assert.match(e.message, /Access Denied/, "the body snippet is the whole point");
      assert.equal(e.status, 403);
      assert.equal(e.blocked, true);
      return true;
    }
  );
});

test("after a block, the next call stands down instead of retrying into it", async () => {
  let hits = 0;
  stubFetch(() => {
    hits++;
    return fail(429, "slow down");
  });

  await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/x/1"));
  assert.equal(hits, 1);

  // The second call must not reach the network at all.
  await assert.rejects(
    () => fetchHtml("https://www.cricbuzz.com/x/2"),
    (e) => {
      assert.ok(e instanceof CricbuzzBlockedError);
      assert.ok(e.retryAfterMs > 0 && e.retryAfterMs <= BLOCK_BACKOFF_MS);
      assert.equal(e.blocked, true);
      return true;
    }
  );
  assert.equal(hits, 1, "no second request was made");
});

test("only 403 and 429 count as a block", () => {
  assert.equal(isBlockStatus(403), true);
  assert.equal(isBlockStatus(429), true);
  // A 500 or a 404 is not the host refusing us — backing off every match for a minute over one
  // bad page would be a self-inflicted outage.
  assert.equal(isBlockStatus(500), false);
  assert.equal(isBlockStatus(404), false);
});

test("a non-block failure does not open the backoff window", async () => {
  let hits = 0;
  stubFetch(() => {
    hits++;
    return fail(500, "boom");
  });

  await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/x/1"));
  await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/x/2"));
  assert.equal(hits, 2, "both calls should have reached the network");
});
