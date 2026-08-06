import test from "node:test";
import assert from "node:assert/strict";
import { classifyResponse, parseRemaining } from "../src/main.js";

// The bug these cover: `fetchHighlights` used to return a bare `[]` for a 429, a 5xx, an
// unrecognised body and a genuine empty day alike, and the caller logged "Highlightly returned 0
// highlights" for every one of them. A quota failure and a quiet Tuesday were indistinguishable in
// the execution log, which is how a six-day outage stayed invisible.

const ok = (items, totalCount = items.length) => ({ data: items, pagination: { totalCount } });
const headers = (map) => ({ get: (k) => map[k] ?? null });

test("a 429 is quota, never an empty item list", () => {
  const r = classifyResponse(429, { message: "Too many requests" });
  assert.equal(r.outcome, "quota");
});

test("a quota message on a non-429 status is still quota", () => {
  // RapidAPI has answered quota exhaustion as 200 and as 403 depending on the plan; matching only
  // on the status code would let one of those through as `http_error` and, worse, retry it.
  assert.equal(classifyResponse(403, { message: "You have exceeded the MONTHLY quota" }).outcome, "quota");
  assert.equal(classifyResponse(200, { message: "rate limit reached" }).outcome, "quota");
});

test("a 5xx is http_error, not an empty day", () => {
  assert.equal(classifyResponse(503, { message: "upstream unavailable" }).outcome, "http_error");
});

test("an unrecognised body shape is unknown_shape — the API-changed signal", () => {
  const r = classifyResponse(200, { items: [{ id: 1 }] });
  assert.equal(r.outcome, "unknown_shape");
  assert.deepEqual(r.items, []);
});

test("a well-formed empty day is ok with zero items, not an error", () => {
  // 2026-08-06 genuinely returned `totalCount: 0`. This is the common case and must stay quiet.
  const r = classifyResponse(200, ok([], 0));
  assert.equal(r.outcome, "ok");
  assert.equal(r.items.length, 0);
  assert.equal(r.totalCount, 0);
});

test("all four documented body shapes are accepted", () => {
  assert.equal(classifyResponse(200, [{ id: 1 }]).shape, "array");
  assert.equal(classifyResponse(200, { data: [{ id: 1 }] }).shape, "data");
  assert.equal(classifyResponse(200, { highlights: [{ id: 1 }] }).shape, "highlights");
  assert.equal(classifyResponse(200, { results: [{ id: 1 }] }).shape, "results");
});

test("totalCount falls back to the item count when pagination is absent", () => {
  // Absent pagination means "this is all there is", not "there are zero" — a 0 here would stop
  // paging on a response that had more.
  assert.equal(classifyResponse(200, { data: [{ id: 1 }, { id: 2 }] }).totalCount, 2);
});

test("parseRemaining reads the live header name", () => {
  // Verified against a real RapidAPI response 2026-08-06: `x-ratelimit-requests-remaining: 53`.
  // The docs do not name this header, so it is pinned here rather than trusted.
  assert.equal(parseRemaining(headers({ "x-ratelimit-requests-remaining": "53" })), 53);
  assert.equal(parseRemaining(headers({ "x-ratelimit-requests-remaining": "0" })), 0);
});

test("a missing or unparseable remaining header is null, not zero", () => {
  // Null must not read as "0 left" — that would latch the back-window off for good the moment
  // RapidAPI renamed or dropped the header.
  assert.equal(parseRemaining(headers({})), null);
  assert.equal(parseRemaining(headers({ "x-ratelimit-requests-remaining": "" })), null);
  assert.equal(parseRemaining(headers({ "x-ratelimit-requests-remaining": "unknown" })), null);
  assert.equal(parseRemaining(undefined), null);
});
