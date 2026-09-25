import { createSign } from "crypto";

/**
 * FCM HTTP v1 topic sender, shared by `poll-live-matches` and `match-sync`.
 *
 * Vendored byte-identically into both functions (each deploys only its own directory) and pinned
 * by `FcmJsVendorParityTest`. Edit one, copy it to the other.
 *
 * Every function here swallows its own failures into `onError` and resolves: an alert is worth
 * less than the run that produced it, and a throw here must never cost a sync.
 */

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TIMEOUT_MS = 8_000;
// Google issues the token for an hour. Refreshed five minutes early so a token minted near the end
// of its life is never sent on a request that outlives it.
const TOKEN_TTL_MS = 55 * 60 * 1000;

let cachedToken = null;
let cachedTokenExpiresAt = 0;
// One exchange in flight at a time. The sends of a run start together, so on a cold cache every one
// of them used to mint its own token — and a failed exchange failed only the send that made it,
// which is how a run came to report "1 of 2 alerts sent" (ANDROID-4X).
let tokenInFlight = null;
// One retry. A send is a single small POST, and FCM's own guidance for 429/5xx is to retry; more
// than one would hold the run open on an FCM outage for an alert that is minutes stale anyway.
const MAX_SEND_ATTEMPTS = 2;

/** Both secrets present. Callers log this once per run so an unset secret is visible in the logs. */
export function fcmConfigured(env = process.env) {
  return Boolean(env.FIREBASE_SERVICE_ACCOUNT_JSON && env.FIREBASE_PROJECT_ID);
}

/**
 * The per-match topic a device subscribes to when it follows a match's alerts.
 * FCM topic names allow `[a-zA-Z0-9-_.~%]`, so anything else in an id is dropped.
 */
export function matchTopic(matchId) {
  return `match_${String(matchId).replace(/[^a-zA-Z0-9-_.~%]/g, "")}`;
}

async function accessToken(serviceAccountJson, fetchFn, now) {
  if (cachedToken && now < cachedTokenExpiresAt) return cachedToken;
  if (!tokenInFlight) {
    tokenInFlight = exchangeToken(serviceAccountJson, fetchFn, now).finally(() => { tokenInFlight = null; });
  }
  return tokenInFlight;
}

async function exchangeToken(serviceAccountJson, fetchFn, now) {
  const sa = JSON.parse(serviceAccountJson);
  const iat = Math.floor(now / 1000);
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(JSON.stringify({
    iss: sa.client_email,
    scope: SCOPE,
    aud: TOKEN_URL,
    iat,
    exp: iat + 3600,
  })).toString("base64url");
  const sigInput = `${header}.${payload}`;
  const sign = createSign("RSA-SHA256");
  sign.update(sigInput);
  const jwt = `${sigInput}.${sign.sign(sa.private_key, "base64url")}`;

  const resp = await fetchFn(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!resp.ok) {
    const e = new Error(`Token exchange HTTP ${resp.status}`);
    e.status = resp.status;
    throw e;
  }
  cachedToken = (await resp.json()).access_token;
  cachedTokenExpiresAt = now + TOKEN_TTL_MS;
  return cachedToken;
}

/** FCM v1's error body names the cause (`UNAVAILABLE`, `QUOTA_EXCEEDED`, `INVALID_ARGUMENT`…). */
async function errorStatusOf(resp) {
  try {
    const body = JSON.parse(await resp.text());
    return body?.error?.status ?? body?.error?.details?.[0]?.errorCode ?? null;
  } catch {
    return null;
  }
}

const RETRYABLE_ERROR_CODES = new Set(["ETIMEDOUT", "ECONNRESET", "ECONNREFUSED", "ENETUNREACH", "EAI_AGAIN", "UND_ERR_CONNECT_TIMEOUT", "UND_ERR_SOCKET"]);

/** A transport failure or timeout — worth one more try. A malformed service account is not. */
function isRetryableThrow(e) {
  if (!e) return false;
  if (e.name === "TimeoutError" || e.name === "AbortError") return true;
  if (typeof e.status === "number") return e.status === 429 || e.status >= 500;
  if (RETRYABLE_ERROR_CODES.has(e.code) || RETRYABLE_ERROR_CODES.has(e.cause?.code)) return true;
  return e instanceof TypeError && /fetch failed/i.test(e.message ?? "");
}

/**
 * Sends a data-only message to [topic]. FCM data values must be strings, so every value is
 * stringified and nulls are dropped. Resolves true on HTTP 2xx, false otherwise — never throws.
 *
 * Data-only on purpose: the device decides whether to show it (per-match preferences, the global
 * toggles, quiet hours), which a notification message would take out of its hands in the
 * background. `android.priority: HIGH` is what lets a data message wake a dozing device.
 *
 * A 401, 429, 5xx, timeout or transport failure is retried once (a 401 with a fresh token).
 * [onError] receives the message and a `{ topic, status, fcmStatus }` detail — the status alone
 * was all that ever reached the log, and FCM's own error code is what says which of those it was.
 */
export async function sendTopicMessage(topic, data, {
  env = process.env,
  fetchFn = globalThis.fetch,
  now = Date.now(),
  onError,
} = {}) {
  if (!fcmConfigured(env)) return false;
  const stringData = Object.fromEntries(
    Object.entries(data).filter(([, v]) => v !== null && v !== undefined).map(([k, v]) => [k, String(v)])
  );
  for (let attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt += 1) {
    const last = attempt === MAX_SEND_ATTEMPTS;
    try {
      const token = await accessToken(env.FIREBASE_SERVICE_ACCOUNT_JSON, fetchFn, now);
      const resp = await fetchFn(
        `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
          body: JSON.stringify({ message: { topic, data: stringData, android: { priority: "HIGH" } } }),
          signal: AbortSignal.timeout(TIMEOUT_MS),
        }
      );
      if (resp.ok) return true;
      if (resp.status === 401) {
        // A revoked or expired token must not stick for the container's remaining life.
        cachedToken = null;
        cachedTokenExpiresAt = 0;
      }
      const retryable = resp.status === 401 || resp.status === 429 || resp.status >= 500;
      if (retryable && !last) continue;
      const fcmStatus = typeof resp.text === "function" ? await errorStatusOf(resp) : null;
      onError?.(
        `FCM send to ${topic} failed: HTTP ${resp.status}${fcmStatus ? ` ${fcmStatus}` : ""} (attempt ${attempt})`,
        { topic, status: resp.status, fcmStatus }
      );
      return false;
    } catch (e) {
      if (isRetryableThrow(e) && !last) continue;
      onError?.(`FCM send to ${topic} error: ${e?.message} (attempt ${attempt})`, {
        topic,
        status: typeof e?.status === "number" ? e.status : null,
        fcmStatus: e?.name ?? null,
      });
      return false;
    }
  }
  return false;
}

/** Test seam: drops the cached access token. */
export function resetFcmTokenCache() {
  cachedToken = null;
  cachedTokenExpiresAt = 0;
  tokenInFlight = null;
}
