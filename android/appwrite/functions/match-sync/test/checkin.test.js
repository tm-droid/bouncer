// The cron monitor exists to catch failures that prevent code from running, so the one thing it
// must never do is become a failure that prevents code from running. These pin that: with no DSN
// it is inert, and closing a check-in that was never opened is a no-op rather than a throw.
import { test } from "node:test";
import assert from "node:assert/strict";

import { startCheckIn, finishCheckIn } from "../src/observability.js";

test("with no SENTRY_DSN the monitor is inert", () => {
  const saved = process.env.SENTRY_DSN;
  delete process.env.SENTRY_DSN;
  try {
    assert.equal(startCheckIn(), null, "no check-in id is minted when Sentry is off");
    assert.doesNotThrow(() => finishCheckIn(null, "ok"));
    assert.doesNotThrow(() => finishCheckIn("some-id", "error"));
  } finally {
    if (saved === undefined) delete process.env.SENTRY_DSN;
    else process.env.SENTRY_DSN = saved;
  }
});

test("closing a check-in that was never opened does nothing, even with a DSN set", () => {
  const saved = process.env.SENTRY_DSN;
  // A syntactically valid DSN pointing nowhere: isEnabled() is true, so this exercises the
  // null-checkInId guard rather than the disabled short-circuit. Sentry buffers and never sends.
  process.env.SENTRY_DSN = "https://abc@o0.ingest.sentry.io/0";
  try {
    assert.doesNotThrow(() => finishCheckIn(null, "ok"), "a PROBE_SOURCES run closes nothing");
  } finally {
    if (saved === undefined) delete process.env.SENTRY_DSN;
    else process.env.SENTRY_DSN = saved;
  }
});
