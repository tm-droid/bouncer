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
  if (!resp.ok) throw new Error(`Token exchange HTTP ${resp.status}`);
  cachedToken = (await resp.json()).access_token;
  cachedTokenExpiresAt = now + TOKEN_TTL_MS;
  return cachedToken;
}

/**
 * Sends a data-only message to [topic]. FCM data values must be strings, so every value is
 * stringified and nulls are dropped. Resolves true on HTTP 2xx, false otherwise — never throws.
 *
 * Data-only on purpose: the device decides whether to show it (per-match preferences, the global
 * toggles, quiet hours), which a notification message would take out of its hands in the
 * background. `android.priority: HIGH` is what lets a data message wake a dozing device.
 */
export async function sendTopicMessage(topic, data, {
  env = process.env,
  fetchFn = globalThis.fetch,
  now = Date.now(),
  onError,
} = {}) {
  if (!fcmConfigured(env)) return false;
  try {
    const token = await accessToken(env.FIREBASE_SERVICE_ACCOUNT_JSON, fetchFn, now);
    const stringData = Object.fromEntries(
      Object.entries(data).filter(([, v]) => v !== null && v !== undefined).map(([k, v]) => [k, String(v)])
    );
    const resp = await fetchFn(
      `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ message: { topic, data: stringData, android: { priority: "HIGH" } } }),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      }
    );
    if (resp.status === 401) {
      // A revoked or expired token must not stick for the container's remaining life.
      cachedToken = null;
      cachedTokenExpiresAt = 0;
    }
    if (!resp.ok) {
      onError?.(`FCM send to ${topic} failed: HTTP ${resp.status}`);
      return false;
    }
    return true;
  } catch (e) {
    onError?.(`FCM send to ${topic} error: ${e?.message}`);
    return false;
  }
}

/** Test seam: drops the cached access token. */
export function resetFcmTokenCache() {
  cachedToken = null;
  cachedTokenExpiresAt = 0;
}
