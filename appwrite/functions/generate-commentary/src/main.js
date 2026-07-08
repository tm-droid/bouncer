import { Client, Databases } from "node-appwrite";
import { createHash } from "crypto";

const DB_ID = process.env.APPWRITE_AI_CACHE_DB_ID ?? "bouncer-db";
const COL_ID = process.env.APPWRITE_AI_CACHE_COL_ID ?? "ai-commentary-cache";
const TTL_MS = 60 * 60 * 1000;
const PERM_EXPIRES = "2099-01-01T00:00:00.000Z";

// Org-level monthly read budget exhausted — every DB read 402s project-wide.
const isBudget402 = (e) => e?.code === 402 || e?.type === "limit_databases_reads_exceeded";

const PROVIDERS = [
  { name: "groq",      url: "https://api.groq.com/openai/v1/chat/completions",      model: "llama-3.3-70b-versatile",         keyEnv: "GROQ_API_KEY" },
  { name: "cerebras",  url: "https://api.cerebras.ai/v1/chat/completions",           model: "llama-3.3-70b",                   keyEnv: "CEREBRAS_API_KEY" },
  { name: "sambanova", url: "https://api.sambanova.ai/v1/chat/completions",          model: "Meta-Llama-3.3-70B-Instruct",     keyEnv: "SAMBANOVA_API_KEY" },
];

export default async ({ req, res, log, error }) => {
  const expectedSecret = process.env.BOUNCER_FUNCTION_SECRET;
  if (expectedSecret) {
    const provided = req.headers["x-bouncer-secret"];
    if (!provided || provided !== expectedSecret) {
      return res.json({ error: "Unauthorized" }, 401);
    }
  }

  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }

  let body;
  try {
    body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
    if (!body || typeof body !== "object" || Array.isArray(body)) throw new Error();
  } catch {
    return res.json({ error: "Body must be a JSON object" }, 400);
  }

  const { cacheKey, prompt, matchComplete = false, maxTokens = 512, temperature = 0.7, systemInstruction = "" } = body;
  if (!cacheKey || !prompt) {
    return res.json({ error: "Missing cacheKey or prompt" }, 400);
  }

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  const db = new Databases(client);

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

  // Generate via LLM providers
  let commentary = null;
  for (const provider of PROVIDERS) {
    const apiKey = process.env[provider.keyEnv];
    if (!apiKey) continue;
    try {
      const messages = systemInstruction
        ? [{ role: "system", content: systemInstruction }, { role: "user", content: prompt }]
        : [{ role: "user", content: prompt }];
      const resp = await fetch(provider.url, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": `Bearer ${apiKey}` },
        body: JSON.stringify({ model: provider.model, messages, max_tokens: maxTokens, temperature }),
        signal: AbortSignal.timeout(15_000),
      });
      if (!resp.ok) { error(`${provider.name} HTTP ${resp.status}`); continue; }
      const data = await resp.json();
      commentary = data.choices?.[0]?.message?.content?.trim();
      if (commentary) { log(`Generated via ${provider.name}`); break; }
    } catch (e) {
      error(`${provider.name} error: ${e?.message}`);
    }
  }

  if (!commentary) return res.json({ error: "All providers failed" }, 503);

  // Write to cache
  const expiresAt = matchComplete ? PERM_EXPIRES : new Date(nowMs + TTL_MS).toISOString();
  try {
    await db.createDocument(DB_ID, COL_ID, docId,
      { cache_key: cacheKey, commentary, expires_at: expiresAt },
      ["read(\"any\")"]
    );
  } catch (e) {
    if (e?.code !== 409) error(`Cache write error: ${e?.message}`);
  }

  return res.json({ commentary, source: "generated" });
};
