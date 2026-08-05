// Sentry wiring for bouncer-generate-commentary.
//
// Ported from match-sync/src/observability.js, minus the upstream-source probe, which is a
// match-sync concern. The context key differs deliberately — an event's context block should say
// which function produced it.
//
// Why this exists, measured on 2026-08-04: this function is the only path an on-device commentary
// request can take (the device stopped holding provider keys in `26ce990`), and it had no error
// reporting of any kind. The Android side's `AppwriteClient.executeFunction` covers the transport —
// SDK uninitialized, guard-blocked, network throw, and an execution Appwrite marks FAILED — but
// Appwrite only marks an execution FAILED on a 5xx. Every in-band 4xx this function returns
// (`429 rate_limited`, `402 read_budget_exceeded`, `400` bad body) arrives on the device as
// `status: completed` with a body that has no `commentary` field, which `optString("commentary")`
// turns into a blank string and the caller turns into null — the same null as "nothing to narrate".
// A provider outage and an idle over were indistinguishable, which is precisely the defect class
// `8494a6b` and the `ai_generated` column were introduced to kill everywhere else in this pipeline.
//
// It also ran the wrong provider keys for eight weeks (see f164fd4) without a single event.

import * as Sentry from "@sentry/node";

let initialized = false;

/**
 * Initialize Sentry once per container. No DSN configured = no-op, so `node --test` and any
 * local run stay offline and silent without a branch at every call site.
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
    // No tracing: this is a short request/response handler and errors are the signal. Latency is
    // already measurable from `execution.duration` without paying for spans.
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
    scope.setContext("generate-commentary", context);
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
    scope.setContext("generate-commentary", context);
    Sentry.captureException(e);
  });
}

/**
 * Flush before the container is frozen.
 *
 * An Appwrite container stops executing the moment the handler's promise settles, so an unflushed
 * queue is simply lost. This is the whole reason the module exists as a wrapper rather than a
 * `report()` sprinkled at each `return` — a single `finally` cannot be forgotten at a new one.
 */
export async function flushObservability() {
  if (!isEnabled()) return;
  try {
    await Sentry.flush(2000);
  } catch {
    // Losing a report is not worth failing a request over.
  }
}
