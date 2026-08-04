import test from "node:test";
import assert from "node:assert/strict";

import {
  identify,
  checkRateLimit,
  blockOffender,
  seedOffenders,
  activeOffenders,
  resetRateLimitState,
  WINDOWS,
  OFFENDER_BLOCK_MS,
} from "../src/rateLimit.js";

// This is the only thing standing between an anonymous, execute:["any"] function and real provider
// spend, now that generate-commentary is the sole commentary generator and the shared secret it
// replaced turned out to be extractable from the APK.

const FIVE_MIN = WINDOWS[0];
const HOUR = WINDOWS[1];

test.beforeEach(() => resetRateLimitState());

test("a device is identified by its header", () => {
  const who = identify({ headers: { "x-bouncer-device": "abc-123" } });
  assert.deepEqual(who, { id: "abc-123", kind: "device" });
});

test("a missing device header falls back to the client IP, never to unlimited", () => {
  // An older build, or anything that simply omits the header, must not get a free pass.
  const who = identify({ headers: { "x-appwrite-client-ip": "14.100.110.48" } });
  assert.equal(who.kind, "ip");
  assert.equal(who.id, "ip:14.100.110.48");

  const anonymous = identify({ headers: {} });
  assert.equal(anonymous.kind, "none");
  assert.equal(anonymous.id, "unidentified", "still a bucket, just a shared one");
});

test("calls under the limit are allowed", () => {
  for (let i = 0; i < FIVE_MIN.max; i++) {
    assert.equal(checkRateLimit("dev", 1_000 + i).allowed, true, `call ${i + 1} should pass`);
  }
});

test("the short window trips at its cap and reports when to retry", () => {
  const start = 1_000_000;
  for (let i = 0; i < FIVE_MIN.max; i++) checkRateLimit("dev", start + i);

  const blocked = checkRateLimit("dev", start + FIVE_MIN.max);
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.window, "5m");
  assert.equal(blocked.count, FIVE_MIN.max);
  assert.ok(blocked.retryAfterMs > 0 && blocked.retryAfterMs <= FIVE_MIN.ms);
});

test("a blocked call does not extend its own window", () => {
  // Otherwise a client in a retry loop keeps pushing the window forward and never drains out of it.
  const start = 2_000_000;
  for (let i = 0; i < FIVE_MIN.max; i++) checkRateLimit("dev", start + i);
  for (let i = 0; i < 10; i++) checkRateLimit("dev", start + FIVE_MIN.max + i);

  // Once the original burst ages out, the device is serviceable again.
  const afterWindow = start + FIVE_MIN.ms + 1;
  assert.equal(checkRateLimit("dev", afterWindow).allowed, true);
});

test("the hourly window catches a flood paced to slip under the 5-minute cap", () => {
  // One call every 8s = 37.5 per 5 minutes, always under the short cap, but 450 in an hour.
  // The whole run spans 3592s, inside the sliding hour, so nothing ages out and the hourly cap is
  // the only thing that can stop it.
  const SPACING_MS = 8_000;
  const ATTEMPTS = 450;
  assert.ok((FIVE_MIN.ms / SPACING_MS) < FIVE_MIN.max, "the pace must not trip the short window");
  assert.ok((ATTEMPTS - 1) * SPACING_MS < HOUR.ms, "the run must fit inside one sliding hour");

  let allowed = 0;
  for (let i = 0; i < ATTEMPTS; i++) {
    if (checkRateLimit("dev", i * SPACING_MS).allowed) allowed++;
  }
  assert.equal(allowed, HOUR.max, "the hourly cap is what bounds a paced flood");
});

test("devices are limited independently", () => {
  const start = 3_000_000;
  for (let i = 0; i < FIVE_MIN.max; i++) checkRateLimit("dev-a", start + i);
  assert.equal(checkRateLimit("dev-a", start + FIVE_MIN.max).allowed, false);
  assert.equal(checkRateLimit("dev-b", start + FIVE_MIN.max).allowed, true);
});

test("an offender stays blocked across the window it breached", () => {
  const now = 4_000_000;
  const until = blockOffender("dev", now);
  assert.equal(until, now + OFFENDER_BLOCK_MS);

  const blocked = checkRateLimit("dev", now + 1000);
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.window, "offender");

  assert.equal(checkRateLimit("dev", until + 1).allowed, true, "the block expires");
});

test("offenders survive a cold start via the persisted map, expired ones do not", () => {
  const now = 5_000_000;
  const seeded = seedOffenders({ live: now + 60_000, stale: now - 60_000 }, now);
  assert.equal(seeded, 1);
  assert.equal(checkRateLimit("live", now).allowed, false);
  assert.equal(checkRateLimit("stale", now).allowed, true);

  const persisted = activeOffenders(now);
  assert.deepEqual(Object.keys(persisted), ["live"]);
});

test("a malformed offender file is ignored rather than throwing", () => {
  // The file is written by this function alone, but a truncated or hand-edited one must not take
  // commentary generation down with it.
  assert.equal(seedOffenders(null), 0);
  assert.equal(seedOffenders(["not", "a", "map"]), 0);
  assert.equal(seedOffenders({ device: "not-a-number" }), 0);
});
