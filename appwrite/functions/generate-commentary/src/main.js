import { Client, Databases, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { createHash } from "crypto";

import { generateVerifiedLine, sanitize, stripPreamble } from "./llm.js";
import {
  identify,
  checkRateLimit,
  blockOffender,
  seedOffenders,
  activeOffenders,
} from "./rateLimit.js";
import {
  initObservability,
  report,
  reportError,
  flushObservability,
} from "./observability.js";

const DB_ID = process.env.APPWRITE_AI_CACHE_DB_ID ?? "bouncer-db";
const COL_ID = process.env.APPWRITE_AI_CACHE_COL_ID ?? "ai-commentary-cache";
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const OFFENDER_FILE = "rate-limit-offenders";
const TTL_MS = 60 * 60 * 1000;
const PERM_EXPIRES = "2099-01-01T00:00:00.000Z";
const READ_ANY = ["read(\"any\")"];

// Org-level monthly read budget exhausted — every DB read 402s project-wide.
const isBudget402 = (e) => e?.code === 402 || e?.type === "limit_databases_reads_exceeded";

// Offenders are loaded once per cold start, not per request: the whole point of the in-memory
// limiter is that the common path touches no storage and no database.
let offendersLoaded = false;

async function loadOffenders({ storage, log, error }) {
  if (offendersLoaded) return;
  offendersLoaded = true;
  try {
    const raw = await storage.getFileDownload(BUCKET_ID, OFFENDER_FILE);
    // node-appwrite 15 hands back an already-parsed object for application/json and bytes
    // otherwise — the same trap that silently disabled five storage readers in July.
    const parsed = typeof raw === "object" && !Buffer.isBuffer(raw) ? raw : JSON.parse(Buffer.from(raw).toString("utf8"));
    const seeded = seedOffenders(parsed);
    if (seeded) log(`Seeded ${seeded} rate-limit offender(s)`);
  } catch (e) {
    if (e?.code !== 404) error(`Could not read ${OFFENDER_FILE}: ${e?.message}`);
  }
}

async function persistOffenders({ storage, error }) {
  const payload = JSON.stringify(activeOffenders());
  try {
    await storage.deleteFile(BUCKET_ID, OFFENDER_FILE).catch(() => {});
    await storage.createFile(
      BUCKET_ID,
      OFFENDER_FILE,
      InputFile.fromBuffer(Buffer.from(payload, "utf8"), "rate-limit-offenders.json"),
      READ_ANY
    );
  } catch (e) {
    // Non-fatal: the in-memory block still holds for this container.
    error(`Failed to persist ${OFFENDER_FILE}: ${e?.message}`);
  }
}

/**
 * Every return path flushes, and a new one cannot forget to.
 *
 * `report()` queues; an Appwrite container stops executing the instant the handler settles, so a
 * queued-but-unflushed event is lost. Putting the flush in a `finally` around the whole handler is
 * the only arrangement where adding a `return res.json(...)` later cannot silently reintroduce the
 * problem. The catch is not swallowing anything — it re-throws after reporting, so Appwrite still
 * marks the execution FAILED and the device still sees it.
 */
export default async (context) => {
  initObservability({ log: context.log });
  try {
    return await handle(context);
  } catch (e) {
    reportError(e, { stage: "unhandled" });
    throw e;
  } finally {
    await flushObservability();
  }
};

async function handle({ req, res, log, error }) {
  if (!process.env.APPWRITE_API_KEY) {
    // The function cannot read its cache or write one. Nothing on the device can tell this apart
    // from a provider outage, and it is a deployment defect, not a runtime one.
    report("generate-commentary is missing APPWRITE_API_KEY — every request will 500", {}, "error");
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  const db = new Databases(client);
  const storage = new Storage(client);

  // Rate limiting replaces the old x-bouncer-secret check, which was extractable from the APK and
  // therefore protected nothing. This function is the only commentary generator now, with no
  // on-device fallback, so every call it serves is real provider spend.
  await loadOffenders({ storage, log, error });
  const caller = identify(req);
  const verdict = checkRateLimit(caller.id);
  if (!verdict.allowed) {
    const until = blockOffender(caller.id);
    await persistOffenders({ storage, error });
    error(
      `[RATE-LIMIT] ${caller.kind} ${caller.id} blocked on the ${verdict.window} window ` +
        `(${verdict.count ?? "?"} calls) until ${new Date(until).toISOString()}`
    );
    // A blocked device gets no commentary for an hour. Worth an event whether the cause is a
    // runaway client or a limit set too low for a real match — the two are not distinguishable
    // from the block itself, and neither is visible anywhere else.
    report("generate-commentary blocked a caller on the rate limiter", {
      callerKind: caller.kind,
      window: verdict.window,
      calls: verdict.count ?? -1,
      blockedUntil: new Date(until).toISOString(),
    });
    return res.json(
      { error: "rate_limited", window: verdict.window, retryAfterMs: verdict.retryAfterMs },
      429
    );
  }

  let body;
  try {
    body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
    if (!body || typeof body !== "object" || Array.isArray(body)) throw new Error();
  } catch {
    return res.json({ error: "Body must be a JSON object" }, 400);
  }

  const {
    cacheKey,
    prompt,
    matchComplete = false,
    maxTokens = 512,
    temperature = 0.7,
    systemInstruction = "",
    // Hallucination check, ported from the device when the provider keys left the app. Off by
    // default so recap/summary prompts — which have no source snippets to check against — do not
    // pay for a verdict that cannot mean anything.
    verify = false,
    snippet1 = "",
    snippet2 = "",
    ball = "",
    bowler = "",
    batsman = "",
  } = body;
  if (!cacheKey || !prompt) {
    return res.json({ error: "Missing cacheKey or prompt" }, 400);
  }

  const docId = createHash("sha256").update(cacheKey).digest("hex").slice(0, 36);
  const nowMs = Date.now();

  // Check cache first
  try {
    const doc = await db.getDocument(DB_ID, COL_ID, docId);
    const isPermanent = doc.expires_at === PERM_EXPIRES;
    const isExpired = !isPermanent && new Date(doc.expires_at).getTime() < nowMs;
    if (!isExpired) {
      log(`Cache HIT: ${cacheKey}`);
      return res.json({ commentary: doc.commentary, source: "cache" });
    }
    log(`Cache STALE: ${cacheKey}`);
  } catch (e) {
    if (isBudget402(e)) {
      // Abort before spending LLM tokens: cache reads AND the follow-up write are both dead.
      error(`[READ-BUDGET-402] limit_databases_reads_exceeded — aborting generation`);
      // Org-wide and plan-level: every device is getting this, and no retry helps until the budget
      // resets. `AppwriteReadGuard` latches on the device side but only for quotas it observes
      // itself — a 402 raised inside a function is invisible to it.
      report("generate-commentary aborted — org read budget exhausted (402)", {
        appwriteType: e?.type ?? "unknown",
      }, "error");
      return res.json({ error: "read_budget_exceeded" }, 402);
    }
    if (e?.code !== 404) error(`Cache read error: ${e?.message}`);
    log(`Cache MISS: ${cacheKey}`);
  }

  const attempts = [];
  const generated = await generateVerifiedLine(prompt, {
    maxTokens,
    temperature,
    systemInstruction: systemInstruction || undefined,
    verify,
    context: { snippet1, snippet2, ball, bowler, batsman },
    log,
    error,
    attempts,
  });

  if (!generated.line) {
    // 503 with the reason, so the device can tell "nobody answered" from "the answer was rejected"
    // — and so the caller's own reporting says which, instead of a bare null.
    error(`Generation failed (${generated.reason}): ${attempts.join("; ")}`);
    // The device's visible degradation — "Ball delivered. Play continues." on the ball path, the
    // raw scraper sentence on the event path. `attempts` is the whole diagnosis: it separates a
    // revoked key from a rate limit from a model id that no longer exists, which a bare
    // `providers_exhausted` does not.
    report("generate-commentary produced no line — the device falls back to placeholder text", {
      reason: generated.reason,
      attempts: attempts.join("; "),
      ball,
      verify,
    });
    return res.json({ error: generated.reason, attempts }, 503);
  }

  // stripPreamble MUST run before sanitize collapses newlines. This path skipped both entirely,
  // which is how model preamble ("Since there is no specific ball number...") reached the shared
  // cache from the device path while match-sync was stripping it correctly.
  const commentary = sanitize(stripPreamble(generated.line));
  if (!commentary) {
    error("Generation produced only preamble");
    // A provider answered and the answer was entirely meta ("Since there is no specific ball
    // number..."). Distinct from an exhausted chain: the chain is healthy and the prompt or the
    // model is not. Silently it looks the same to the device.
    report("generate-commentary discarded a line that was nothing but preamble", {
      reason: "empty_after_strip",
      provider: generated.provider ?? "unknown",
      ball,
    });
    return res.json({ error: "empty_after_strip", attempts }, 503);
  }

  // Write to cache
  const expiresAt = matchComplete ? PERM_EXPIRES : new Date(nowMs + TTL_MS).toISOString();
  try {
    await db.createDocument(DB_ID, COL_ID, docId,
      { cache_key: cacheKey, commentary, expires_at: expiresAt },
      READ_ANY
    );
  } catch (e) {
    if (e?.code !== 409) {
      error(`Cache write error: ${e?.message}`);
      // 409 is the deterministic row id doing its job. Anything else means a line that has already
      // been paid for never reaches the shared cache, so every other device regenerates it — the
      // provider spend this whole three-tier cache exists to avoid.
      report("generate-commentary could not cache a generated line — other devices will regenerate it", {
        appwriteCode: e?.code ?? -1,
        appwriteType: e?.type ?? "unknown",
      });
    }
  }

  return res.json({ commentary, source: "generated", provider: generated.provider, verified: generated.verified });
}
