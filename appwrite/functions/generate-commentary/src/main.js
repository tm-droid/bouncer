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

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
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
    return res.json({ error: generated.reason, attempts }, 503);
  }

  // stripPreamble MUST run before sanitize collapses newlines. This path skipped both entirely,
  // which is how model preamble ("Since there is no specific ball number...") reached the shared
  // cache from the device path while match-sync was stripping it correctly.
  const commentary = sanitize(stripPreamble(generated.line));
  if (!commentary) {
    error("Generation produced only preamble");
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
    if (e?.code !== 409) error(`Cache write error: ${e?.message}`);
  }

  return res.json({ commentary, source: "generated", provider: generated.provider, verified: generated.verified });
};
