import { test } from "node:test";
import assert from "node:assert/strict";
import {
  BLOCK_BACKOFF_MS,
  CricbuzzBlockedError,
  MAX_FETCH_ATTEMPTS,
  MIN_REQUEST_GAP_MS,
  fetchHtml,
  isBlockStatus,
  resetThrottleState,
} from "../src/cricbuzz-http.js";

// The module-scope clocks are the production behaviour, so every test resets them first.
const withStubbedFetch = async (impl, body) => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, opts) => { calls.push({ url, opts }); return impl(url, opts, calls.length); };
  try {
    resetThrottleState();
    return await body(calls);
  } finally {
    globalThis.fetch = original;
    resetThrottleState();
  }
};

const ok = (text = "<html>hi</html>") => ({ ok: true, status: 200, text: async () => text });
const fail = (status) => ({ ok: false, status, text: async () => `blocked body ${status}` });

test("sends the full browser header set, not User-Agent alone", async () => {
  await withStubbedFetch(() => ok(), async (calls) => {
    await fetchHtml("https://www.cricbuzz.com/x");
    const h = calls[0].opts.headers;
    // Sending User-Agent alone is what ANDROID-35 traced the 403 burst to.
    for (const key of ["User-Agent", "Accept", "Accept-Language", "Referer", "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Upgrade-Insecure-Requests"]) {
      assert.ok(h[key], `missing ${key}`);
    }
  });
});

test("rotates the User-Agent across requests", async () => {
  await withStubbedFetch(() => ok(), async (calls) => {
    for (let i = 0; i < 3; i++) await fetchHtml("https://www.cricbuzz.com/x");
    const seen = new Set(calls.map((c) => c.opts.headers["User-Agent"]));
    assert.ok(seen.size >= 3, `expected rotation, saw ${seen.size} distinct agents`);
  });
});

test("spaces consecutive requests by MIN_REQUEST_GAP_MS", async () => {
  await withStubbedFetch(() => ok(), async () => {
    const started = Date.now();
    await fetchHtml("https://www.cricbuzz.com/a");
    await fetchHtml("https://www.cricbuzz.com/b");
    const elapsed = Date.now() - started;
    assert.ok(
      elapsed >= MIN_REQUEST_GAP_MS,
      `two requests took ${elapsed}ms, expected at least ${MIN_REQUEST_GAP_MS}ms of spacing`
    );
  });
});

test("concurrent callers queue onto slots instead of bursting", async () => {
  await withStubbedFetch(() => ok(), async () => {
    const started = Date.now();
    // The real burst was 24 requests leaving at once from one datacenter IP.
    await Promise.all([1, 2, 3].map(() => fetchHtml("https://www.cricbuzz.com/x")));
    assert.ok(Date.now() - started >= MIN_REQUEST_GAP_MS * 2);
  });
});

test("a 403 raises the backoff window and later calls throw CricbuzzBlockedError", async () => {
  await withStubbedFetch((_u, _o, n) => (n === 1 ? fail(403) : ok()), async (calls) => {
    await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/x"), /HTTP 403/);
    await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/y"), CricbuzzBlockedError);
    // The second call must not have reached the network at all.
    assert.equal(calls.length, 1);
  });
});

test("a 429 latches the same backoff as a 403", async () => {
  await withStubbedFetch((_u, _o, n) => (n === 1 ? fail(429) : ok()), async (calls) => {
    await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/x"), /HTTP 429/);
    await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/y"), CricbuzzBlockedError);
    assert.equal(calls.length, 1);
  });
});

test("a 500 is a plain failure and does not latch the backoff", async () => {
  await withStubbedFetch((_u, _o, n) => (n === 1 ? fail(500) : ok("second")), async () => {
    await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/x"), /HTTP 500/);
    // A server error is transient and unrelated to rate limiting; standing down 90s would be wrong.
    assert.equal(await fetchHtml("https://www.cricbuzz.com/y"), "second");
  });
});

test("a failure carries the response body snippet for diagnosis", async () => {
  await withStubbedFetch(() => fail(403), async () => {
    // The 403 that started this was diagnosed blind because the body was never read.
    const err = await fetchHtml("https://www.cricbuzz.com/x").catch((e) => e);
    assert.equal(err.status, 403);
    assert.equal(err.blocked, true);
    assert.match(err.bodySnippet, /blocked body 403/);
  });
});

test("isBlockStatus covers exactly 403 and 429", () => {
  assert.equal(isBlockStatus(403), true);
  assert.equal(isBlockStatus(429), true);
  assert.equal(isBlockStatus(500), false);
  assert.equal(isBlockStatus(200), false);
});

test("the backoff window is the documented 90 seconds", () => {
  assert.equal(BLOCK_BACKOFF_MS, 90_000);
});

// ── Transport retry (ANDROID-3D) ────────────────────────────────────────────
//
// `connect ETIMEDOUT 23.0.220.165:443` reaching Cricbuzz from the Appwrite container skipped that
// match for that minute, 272 times. The request never reached the host, so one retry is free of any
// politeness question — unlike a 403, which is the host answering.

/** undici's shape: a TypeError wrapping an AggregateError of per-address connect errors. */
const connectFailure = (code) => {
  const err = new TypeError("fetch failed");
  err.cause = Object.assign(new Error(`connect ${code} 23.0.220.165:443`), { code });
  return err;
};

test("retries once after a connect timeout and returns the retry's body", async () => {
  await withStubbedFetch(
    (_u, _o, n) => { if (n === 1) throw connectFailure("ETIMEDOUT"); return ok("recovered"); },
    async (calls) => {
      assert.equal(await fetchHtml("https://www.cricbuzz.com/x"), "recovered");
      assert.equal(calls.length, 2);
    },
  );
});

test("retries a connect error reported inside an AggregateError", async () => {
  await withStubbedFetch(
    (_u, _o, n) => {
      if (n > 1) return ok("recovered");
      const err = new TypeError("fetch failed");
      err.cause = Object.assign(new AggregateError([]), {
        errors: [{ code: "ENETUNREACH" }, { code: "ETIMEDOUT" }],
      });
      throw err;
    },
    async (calls) => {
      assert.equal(await fetchHtml("https://www.cricbuzz.com/x"), "recovered");
      assert.equal(calls.length, 2);
    },
  );
});

test("gives up after one retry rather than looping", async () => {
  await withStubbedFetch(() => { throw connectFailure("ETIMEDOUT"); }, async (calls) => {
    // The original transport error is rethrown unchanged; the code is on its cause, as undici
    // reports it, which is also where isRetryableTransportError reads it from.
    const err = await fetchHtml("https://www.cricbuzz.com/x").catch((e) => e);
    assert.equal(err.cause.code, "ETIMEDOUT");
    assert.equal(calls.length, MAX_FETCH_ATTEMPTS);
  });
});

test("never retries an HTTP status — a 403 is the host saying stop", async () => {
  await withStubbedFetch(() => fail(403), async (calls) => {
    await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/x"), /HTTP 403/);
    assert.equal(calls.length, 1);
  });
});

test("a non-transport error is not retried", async () => {
  await withStubbedFetch(() => { throw new TypeError("Invalid URL"); }, async (calls) => {
    await assert.rejects(() => fetchHtml("https://www.cricbuzz.com/x"), /Invalid URL/);
    assert.equal(calls.length, 1);
  });
});

test("the retry queues on the shared slot rather than bursting", async () => {
  await withStubbedFetch(
    (_u, _o, n) => { if (n === 1) throw connectFailure("ECONNRESET"); return ok(); },
    async () => {
      const started = Date.now();
      await fetchHtml("https://www.cricbuzz.com/x");
      // Two slots taken, so the second waits out MIN_REQUEST_GAP_MS behind the first.
      assert.ok(Date.now() - started >= MIN_REQUEST_GAP_MS, "retry skipped the request spacing");
    },
  );
});

test("interactive fetch obeys absolute deadline before upstream and never retries", async () => {
  await withStubbedFetch(() => { throw new DOMException('timeout', 'TimeoutError'); }, async calls => {
    await assert.rejects(fetchHtml('https://www.cricbuzz.com/x', 1000, { deadlineAt: Date.now() - 1, maxAttempts: 1 }), /deadline/);
    assert.equal(calls.length, 0);
    await assert.rejects(fetchHtml('https://www.cricbuzz.com/x', 1000, { deadlineAt: Date.now() + 2000, maxAttempts: 1 }), /timeout/);
    assert.equal(calls.length, 1);
  });
});
