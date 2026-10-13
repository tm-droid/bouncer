// Sentry wiring for bouncer-highlights-sync.
//
// This function had no error reporting of any kind, and the outage it hid was not a crash. Between
// 2026-07-30 and 2026-08-05 exactly one highlight row was written while clips for 2026-07-29 and
// 2026-08-01 sat available upstream the whole time. Every execution in that window was
// `status: completed`, HTTP 200. There was nothing to see unless you opened the execution log for
// the specific minute it happened in.
//
// What is reported here is therefore shaped around that: the failures worth an event are the ones
// that LOOK like a successful empty day. A genuine zero for one date is not reported — with
// upstream volume at 1-7 clips/day it is the common case, and an alert that fires daily is not an
// alert.

import * as Sentry from "@sentry/node";

let initialized = false;

/**
 * Initialize Sentry once per container. No DSN configured = no-op, so `node --test` and any local
 * run stay offline and silent without a branch at every call site.
 */
export function initObservability({ log } = {}) {
  if (initialized) return isEnabled();
  initialized = true;
  const dsn = process.env.SENTRY_DSN;
  if (!dsn) {
    log?.("SENTRY_DSN unset — server-side error reporting disabled");
    return false;
  }
  Sentry.init({
    dsn,
    environment: process.env.SENTRY_ENVIRONMENT ?? "production",
    // No tracing: this is a 15-minute cron with a fixed shape. Errors are the signal.
    tracesSampleRate: 0,
  });
  return true;
}

export function isEnabled() {
  return Boolean(process.env.SENTRY_DSN);
}

/** Report a degradation with structured context. Safe to call when Sentry is disabled. */
export function report(message, context = {}, level = "warning") {
  if (!isEnabled()) return;
  Sentry.withScope((scope) => {
    scope.setLevel(level);
    scope.setContext("highlights-sync", context);
    for (const [key, value] of Object.entries(context)) {
      if (typeof value === "string" || typeof value === "number") scope.setTag(key, String(value));
    }
    Sentry.captureMessage(message);
  });
}

/** Report a thrown error with structured context. Safe to call when Sentry is disabled. */
export function reportError(e, context = {}) {
  if (!isEnabled()) return;
  Sentry.withScope((scope) => {
    scope.setContext("highlights-sync", context);
    Sentry.captureException(e);
  });
}

/** Flush before the container is frozen — a cron invocation exits faster than Sentry's queue drains. */
export async function flushObservability() {
  if (!isEnabled()) return;
  try {
    await Sentry.flush(2000);
  } catch {
    // Losing a report is not worth failing a sync run over.
  }
}
