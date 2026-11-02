// Sentry wiring + upstream-source probe for bouncer-match-sync.
//
// This function had no error reporting of any kind. Everything it does that can silently degrade —
// an exhausted provider chain writing raw Cricbuzz text into `match-commentary` as if it were
// generated, a commentary write that 4xx'd, a presence file that could not be read — was visible
// only by opening the execution log for the one minute it happened in. A month-long outage
// (`ffe9656`, `clean is not defined`) is exactly what that buys.

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
    // No tracing: this is a one-minute cron with a fixed shape. Errors are the signal.
    tracesSampleRate: 0,
  });
  return true;
}

export function isEnabled() {
  return Boolean(process.env.SENTRY_DSN);
}

const UNTAGGED_KEYS = new Set(["attempts", "matchIds", "summary", "reason", "balls"]);
const MAX_TAG_LENGTH = 64;

/** Report a degradation with structured context. Safe to call when Sentry is disabled. */
export function report(message, context = {}, level = "warning") {
  if (!isEnabled()) return;
  Sentry.withScope((scope) => {
    scope.setLevel(level);
    scope.setContext("match-sync", context);
    for (const [key, value] of Object.entries(context)) {
      if (typeof value !== "string" && typeof value !== "number") continue;
      const text = String(value);
      // A tag is an index, not a payload. `attempts` is a joined free-text provider log that is
      // near-unique per ball, and tagging it gave the highest-volume issue in the project an
      // unbounded-cardinality tag. The value stays in the context above, where it is searchable
      // without being indexed.
      if (UNTAGGED_KEYS.has(key) || text.length > MAX_TAG_LENGTH) continue;
      scope.setTag(key, text);
    }
    Sentry.captureMessage(message);
  });
}

// Container-scoped, like every other latch in this function. A condition that holds for hours
// (a spent chain, a list longer than the cap, an upstream outage) used to raise one event per
// one-minute run — 3,221 for the chain-depth alert in a week — which buries every other signal in
// the project and says nothing the first event did not. A new container re-reports once, which is
// the right amount: it is a fresh observation.
const lastReportedAt = new Map();

/** True when [key] has not been reported within [intervalMs]; records the report when it has not. */
export function shouldReport(key, intervalMs, now = Date.now()) {
  const last = lastReportedAt.get(key);
  if (last !== undefined && now - last < intervalMs) return false;
  lastReportedAt.set(key, now);
  return true;
}

/** Test seam. */
export function resetReportThrottle() {
  lastReportedAt.clear();
}

/**
 * [report], at most once per [intervalMs] for a given [key]. Put whatever makes the condition a
 * DIFFERENT condition into the key (the dropped ids, the blocked rungs), so a change re-reports at
 * once while a steady state stays quiet.
 */
export function reportThrottled(key, intervalMs, message, context = {}, level = "warning") {
  if (!shouldReport(key, intervalMs)) return;
  report(message, context, level);
}

/** Report a thrown error with structured context. Safe to call when Sentry is disabled. */
export function reportError(e, context = {}) {
  if (!isEnabled()) return;
  Sentry.withScope((scope) => {
    scope.setContext("match-sync", context);
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

// ── Cron monitor (dead-man's switch) ─────────────────────────────────────────
//
// Everything else in this file reports a failure by RUNNING code. That is precisely what a whole
// class of outage denies us: on 2026-08-05 a deployment built fine and then died at module load
// ("Cannot find module @sentry/node/build/esm/index.js"), so `Sentry.init` never executed and 22
// minutes of one-minute cron failures produced not one event. The only detectable signal there is
// the ABSENCE of a heartbeat, which by definition has to be judged somewhere else.
//
// Sentry Crons is that somewhere else, and is preferred over a separate uptime vendor for one
// reason worth writing down: a missed check-in arrives as an ordinary issue tagged
// `monitor.slug`, in the same stream as the passthrough and dead-provider events, so the timeline
// correlates instead of living in a second inbox nobody opens.
const MONITOR_SLUG = "bouncer-match-sync";

// Upserted with every check-in, so the monitor cannot drift from the schedule it is monitoring and
// there is no console step to forget.
//
// checkinMargin 2 rather than 1: this runs every minute, and a single late tick is noise. Sentry's
// consecutive-failure threshold (set on the monitor, not here) is what decides how many misses in a
// row constitute an outage. maxRuntime 3 sits above the observed worst case of ~9s but well under
// the function's 120s timeout, so a hung run is caught rather than quietly retried next minute.
const MONITOR_CONFIG = {
  schedule: { type: "crontab", value: "* * * * *" },
  checkinMargin: 2,
  maxRuntime: 3,
  timezone: "UTC",
};

/** Open a check-in. Returns null when Sentry is disabled, which every caller treats as "skip". */
export function startCheckIn() {
  if (!isEnabled()) return null;
  return Sentry.captureCheckIn({ monitorSlug: MONITOR_SLUG, status: "in_progress" }, MONITOR_CONFIG);
}

/**
 * Close a check-in as "ok" or "error".
 *
 * Never throws: a monitoring call that can fail a sync run has inverted its own purpose.
 */
export function finishCheckIn(checkInId, status) {
  if (!isEnabled() || !checkInId) return;
  try {
    Sentry.captureCheckIn({ checkInId, monitorSlug: MONITOR_SLUG, status }, MONITOR_CONFIG);
  } catch {
    // Nothing to do — the missed check-in is itself the alert.
  }
}

// ── Upstream-source probe ────────────────────────────────────────────────────

const PROBE_TARGETS = [
  { name: "cricinfo-live", url: "https://www.espncricinfo.com/live-cricket-score" },
  { name: "cricbuzz-live", url: "https://www.cricbuzz.com/cricket-match/live-scores" },
];

// The three markers CricInfoScraper.kt actually parses. Their presence/absence is the difference
// between "blocked", "reachable but redesigned" and "reachable and parseable".
const PROBE_MARKERS = ["__NEXT_DATA__", "ds-no-tap-higlight", "ds-text-tight-l"];

/**
 * Fetch each upstream source from inside the Appwrite container and log what came back.
 *
 * Exists because "can we reach Cricinfo?" has a different answer from a laptop, a phone and a
 * datacenter, and the whole question of whether Cricinfo can be restored as a server-side second
 * commentary source turns on the datacenter answer. Gated on PROBE_SOURCES so it costs nothing on
 * the per-minute schedule; left in place so the next markup drift is one execution away from an
 * answer rather than a redeploy.
 */
export async function probeSources({ log, error }) {
  const results = [];
  for (const target of PROBE_TARGETS) {
    try {
      const resp = await fetch(target.url, {
        headers: {
          "User-Agent":
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
          Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "Accept-Language": "en-US,en;q=0.9",
        },
        signal: AbortSignal.timeout(15_000),
      });
      const body = await resp.text();
      const markers = PROBE_MARKERS.filter((m) => body.includes(m));
      const result = { name: target.name, status: resp.status, bytes: body.length, markers };
      results.push(result);
      log(`[PROBE] ${target.name} status=${resp.status} bytes=${body.length} markers=[${markers.join(",")}]`);
    } catch (e) {
      const result = { name: target.name, status: null, error: e?.message };
      results.push(result);
      error(`[PROBE] ${target.name} threw: ${e?.message}`);
    }
  }
  return results;
}
