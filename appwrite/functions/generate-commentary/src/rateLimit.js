// Per-device rate limiting for bouncer-generate-commentary.
//
// This replaces BOUNCER_FUNCTION_SECRET, which was compiled into the APK as a BuildConfig string
// and therefore extractable with `unzip` + `strings` — a shared secret in a public artifact gates
// nothing. The function is `execute: ["any"]` regardless, so what actually needs protecting is
// spend: every call that misses the cache costs real provider tokens, and this function is now the
// only commentary generation path, with no on-device fallback behind it.
//
// **Deliberately no database.** A read+write per call would add two billed Appwrite DB operations
// to every generation, on the exact quota whose exhaustion (402 limit_databases_reads_exceeded,
// 2026-07-09) took down every read in the app. The cure would cost more than the disease. State
// lives in module scope, which survives warm invocations — the same mechanism match-sync already
// relies on for its watermark cache.
//
// **Known gap, stated rather than implied:** cold starts and parallel containers each get their own
// window, so this bounds cost-abuse and runaway client loops. It is not authentication and does not
// stop a determined attacker. The offender file below is what carries a repeat abuser across
// containers.

/** Sliding windows. Legitimate use is a few calls a minute while one match is open. */
export const WINDOWS = [
  { name: "5m", ms: 5 * 60 * 1000, max: 40 },
  { name: "1h", ms: 60 * 60 * 1000, max: 400 },
];

/** Devices tracked in memory. Bounded so a flood of unique ids cannot grow the map without limit. */
const MAX_TRACKED_DEVICES = 500;

/** How long a device stays blocked after it lands on the offender list. */
export const OFFENDER_BLOCK_MS = 60 * 60 * 1000;

// deviceId -> ascending call timestamps, trimmed to the longest window.
const calls = new Map();
// deviceId -> epoch ms when the block expires. Seeded from storage on cold start.
const blockedUntil = new Map();

/**
 * The identity a request is limited on.
 *
 * A missing header is not a bypass: an older build (or a hand-rolled caller that simply omits it)
 * falls back to the client IP Appwrite already supplies. Anonymous-but-limited beats unlimited.
 */
export function identify(req) {
  const device = req.headers?.["x-bouncer-device"];
  if (typeof device === "string" && device.trim()) return { id: device.trim(), kind: "device" };
  const ip = req.headers?.["x-appwrite-client-ip"];
  if (typeof ip === "string" && ip.trim()) return { id: `ip:${ip.trim()}`, kind: "ip" };
  return { id: "unidentified", kind: "none" };
}

/** Evict the least-recently-seen device once the map is full. Map preserves insertion order. */
function trimTracked() {
  while (calls.size > MAX_TRACKED_DEVICES) {
    const oldest = calls.keys().next().value;
    if (oldest === undefined) break;
    calls.delete(oldest);
  }
}

/**
 * Record a call and report whether it is allowed.
 *
 * @returns `{ allowed: true }`, or `{ allowed: false, window, retryAfterMs, count }`.
 */
export function checkRateLimit(id, now = Date.now()) {
  const blockUntil = blockedUntil.get(id) ?? 0;
  if (now < blockUntil) {
    return { allowed: false, window: "offender", retryAfterMs: blockUntil - now, count: null };
  }

  const longest = Math.max(...WINDOWS.map((w) => w.ms));
  const history = (calls.get(id) ?? []).filter((t) => now - t < longest);

  for (const window of WINDOWS) {
    const inWindow = history.filter((t) => now - t < window.ms);
    if (inWindow.length >= window.max) {
      // Not recorded: a blocked call must not extend its own window, or a client in a retry loop
      // would never see the window drain.
      calls.set(id, history);
      const oldestInWindow = inWindow[0];
      return {
        allowed: false,
        window: window.name,
        retryAfterMs: Math.max(0, window.ms - (now - oldestInWindow)),
        count: inWindow.length,
      };
    }
  }

  history.push(now);
  // Re-insert so the key moves to the end of the iteration order for LRU eviction.
  calls.delete(id);
  calls.set(id, history);
  trimTracked();
  return { allowed: true };
}

/** Block [id] for [OFFENDER_BLOCK_MS]. Returns the expiry so the caller can persist it. */
export function blockOffender(id, now = Date.now()) {
  const until = now + OFFENDER_BLOCK_MS;
  blockedUntil.set(id, until);
  return until;
}

/** Seed the in-memory block list from a persisted map, dropping entries that already expired. */
export function seedOffenders(map, now = Date.now()) {
  if (!map || typeof map !== "object" || Array.isArray(map)) return 0;
  let seeded = 0;
  for (const [id, until] of Object.entries(map)) {
    if (typeof until === "number" && until > now) {
      blockedUntil.set(id, until);
      seeded++;
    }
  }
  return seeded;
}

/** The still-active blocks, for persisting back to storage. Written only when one is added. */
export function activeOffenders(now = Date.now()) {
  const out = {};
  for (const [id, until] of blockedUntil.entries()) {
    if (until > now) out[id] = until;
  }
  return out;
}

/** Test seam — the module-scope maps are the point in production, so tests need a reset. */
export function resetRateLimitState() {
  calls.clear();
  blockedUntil.clear();
}
