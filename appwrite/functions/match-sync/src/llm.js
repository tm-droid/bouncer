// LLM provider chain + sanitization + event classification for server-side commentary generation.
//
// VENDORED: byte-identical copies live at
//   appwrite/functions/match-sync/src/llm.js
//   appwrite/functions/generate-commentary/src/llm.js
// and LlmJsVendorParityTest fails the build if they diverge. Copy one over the other to resync;
// this header is part of the compared bytes, so it stays identical in both.
//
// Duplicated rather than shared because it has to be: tools/deploy-function.mjs builds each
// deployment with `tar -C <that function's dir> .`, so nothing outside a function's own directory
// reaches the container.
//
// The two functions are the two halves of one pipeline — match-sync narrates a ball for everyone,
// generate-commentary narrates it for the device that asked — so drift shows up as two house
// styles inside one match. It had already happened when the parity test was added: match-sync had
// classifyEvent and the dropped-catch/no-ball wicket veto and generate-commentary did not, while
// generate-commentary's own header asserted the two files were byte-identical.
//
// Also mirrors the Kotlin CommentaryFusionService.SYSTEM_INSTRUCTION and
// CommentaryFormatter.sanitize — see CommentaryConstantParityTest for that half.

// The model ids live ONLY here and in the generate-commentary mirror of this file — the Kotlin side
// stopped holding a provider list when the keys moved server-side in 26ce990, so a model bump is a
// two-file edit, both of them this one.
//
// A model id can go to zero quota without the key or the account being wrong, and the chain cannot
// tell that apart from load. Measured 2026-08-05: `Meta-Llama-3.3-70B-Instruct` answered EVERY
// request with 429 "Rate limit exceeded" — at zero concurrency, on a cold key, three attempts ten
// seconds apart — while `DeepSeek-V3.1` and `DeepSeek-V3.2` returned 200 on the same key in the
// same second, and `/v1/models` listed all of them. So SambaNova had been contributing nothing to
// the chain for an unknown period while looking, in `attempts`, exactly like a provider that was
// merely busy. Swapped to DeepSeek-V3.1: 3/3 on the ball-prefix format contract and 3/3 self-verify
// PASS on the fixture balls, ~13 RPM before its own 429, versus 0.
// Gemini's OpenAI-compatible endpoint. Every Gemini rung posts here; only `model` differs, and
// that is the whole point — see GEMINI_QUOTA_IS_PER_MODEL below.
const GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";

// WHY THREE GEMINI RUNGS ON ONE KEY (measured 2026-09-16, live account)
//
// Gemini's free allowance is `GenerateRequestsPerMinutePerProjectPerModel-FreeTier` — the 429 body
// names it, with `quotaDimensions: {model}` and `quotaValue: "15"`. Per project PER MODEL, so each
// distinct model id is a distinct 15 RPM / 500 RPD bucket. Three ids is three allowances on one
// key, which is how the chain gets back above one reachable provider without anyone paying.
//
// An alias is NOT a fourth bucket. Bursting `gemini-flash-lite-latest` returns
// `quotaDimensions: {"model":"gemini-3.5-flash-lite"}` — it bills the model it resolves to. Same
// for `gemini-flash-latest` and `gemini-pro-latest`. Adding one would look like free capacity and
// deliver none.
//
// `tokenFloor` is not tuning either. 3.6-flash and 3-flash-preview are reasoning models: at
// `max_tokens: 512` they returned `completion_tokens: 18` against `total_tokens: 717` and
// `finish_reason: length` — ~490 tokens spent thinking before any prose. This is the gpt-oss trap
// one rung down, and the fix is the same shape as `reasoning_effort` on groq: give the budget the
// model actually needs. 3.5-flash-lite needs none of it and stays at the caller's 100.
//
// Rejected, so nobody re-adds them: `gemini-3.1-flash-lite` (fast, separate bucket, but dropped
// "ramp" and invented "sharp bouncer" across two runs — a hallucination ships, where a timeout
// merely falls through); `gemini-3.7-flash` and `gemini-flash-latest` (16s timeout / 503 against a
// 15s AbortSignal); the Pro ids (429 on the very first call); every `gemini-2.5-*` (404 "no longer
// available to new users"); and OpenRouter's `:free` models, which answered
// "temporarily rate-limited upstream" 429 on every call without even consuming the key's 50/day.
// A rung that is configured and never answers is the cerebras/sambanova defect, not a fallback.
const PROVIDERS = [
  // `reasoning_effort: low` is load-bearing, not tuning: gpt-oss is a reasoning model and spends
  // the token budget on reasoning before it writes any `content`. At match-sync's per-ball
  // budget it returned `empty completion` every time — a 200 with nothing in it, which the
  // chain correctly treats as a failed generation. Sent only to groq; the Gemini endpoint rejects
  // it alongside its own `thinking_level`.
  { name: "groq",                   account: "groq",   url: "https://api.groq.com/openai/v1/chat/completions", model: "openai/gpt-oss-20b",     keyEnv: "GROQ_API_KEY",   tokenFloor: 0,    extraBody: { reasoning_effort: "low" } },
  { name: "gemini-flash-lite",      account: "gemini", url: GEMINI_URL,                                        model: "gemini-3.5-flash-lite",  keyEnv: "GEMINI_API_KEY", tokenFloor: 0 },
  { name: "gemini-3.6-flash",       account: "gemini", url: GEMINI_URL,                                        model: "gemini-3.6-flash",       keyEnv: "GEMINI_API_KEY", tokenFloor: 2048 },
  { name: "gemini-3-flash-preview", account: "gemini", url: GEMINI_URL,                                        model: "gemini-3-flash-preview", keyEnv: "GEMINI_API_KEY", tokenFloor: 2048 },
];

// Not in the chain, still worth probing. Both answer 200 on `GET /v1/models` and 402 on every
// completion (spent Cerebras trial, unfunded SambaNova), so calling them costs a doomed 15s round
// trip per cold container and buys nothing — but `probeProviders` is exactly how a funded account
// would be noticed, and dropping them from the probe too would make that unanswerable.
const PROBE_ONLY_PROVIDERS = [
  { name: "cerebras",  account: "cerebras",  url: "https://api.cerebras.ai/v1/chat/completions",  model: "gpt-oss-120b", keyEnv: "CEREBRAS_API_KEY" },
  { name: "sambanova", account: "sambanova", url: "https://api.sambanova.ai/v1/chat/completions", model: "DeepSeek-V3.1", keyEnv: "SAMBANOVA_API_KEY" },
];

/** A provider's billing identity. Several rungs share one Gemini key, project and balance. */
const accountOf = (provider) => provider.account ?? provider.name;


/**
 * What each provider key can actually reach, straight from `GET /v1/models`.
 *
 * The chain reports failures per call, and a failed call cannot distinguish "this model is behind a
 * paywall for my tier" from "my key is dead" from "the account is out of credit" — Groq answers 404
 * `model_not_found` for all three. The model list can: a key that lists models is a live key, and
 * what it lists is what that tier may ask for. This is the same check that diagnosed SambaNova's
 * silent 429s on 2026-08-05, made repeatable instead of a one-off curl.
 *
 * Never throws, returns no secrets, and is read-only.
 */
export async function probeProviders() {
  const out = [];
  for (const provider of [...PROVIDERS, ...PROBE_ONLY_PROVIDERS]) {
    const apiKey = process.env[provider.keyEnv];
    if (!apiKey) {
      out.push({ provider: provider.name, configured: provider.model, error: `no ${provider.keyEnv}` });
      continue;
    }
    const url = provider.url.replace(/\/chat\/completions$/, "/models");
    try {
      const resp = await fetch(url, {
        headers: { Authorization: `Bearer ${apiKey}` },
        signal: AbortSignal.timeout(15_000),
      });
      const body = await resp.text();
      if (!resp.ok) {
        out.push({
          provider: provider.name,
          configured: provider.model,
          status: resp.status,
          error: body.slice(0, PROVIDER_ERROR_DETAIL_MAX),
        });
        continue;
      }
      let ids = [];
      try {
        ids = (JSON.parse(body)?.data ?? []).map((m) => m?.id).filter(Boolean);
      } catch {
        ids = [];
      }
      out.push({
        provider: provider.name,
        configured: provider.model,
        status: resp.status,
        configuredIsListed: ids.includes(provider.model),
        models: ids.slice(0, PROBE_MODEL_LIST_MAX),
        modelCount: ids.length,
      });
    } catch (e) {
      out.push({ provider: provider.name, configured: provider.model, error: e?.message ?? "threw" });
    }
  }
  return out;
}

const PROBE_MODEL_LIST_MAX = 40;

// How many times a provider must have been asked before "it 429s every time" is evidence of
// anything. Below this it is indistinguishable from bad luck during a burst.
const DEAD_PROVIDER_MIN_CALLS = 5;

// Per-container tally of how each provider actually answered.
//
// `attempts` already records every failure, but only per line, and only reaches Sentry on a 503 —
// so a provider that is permanently dead and one that is briefly busy write the identical
// `sambanova: HTTP 429` string and nothing aggregates them. That is exactly how SambaNova sat at
// zero for an unknown period behind a model id whose quota had been withdrawn (2026-08-05): the
// symptom was visible on every single call and invisible in every alert.
//
// Module scope is deliberate and is the opposite choice from `unavailable` above. That set must not
// outlive one line, because a rate-limited provider recovers. This must outlive one line, because
// one line cannot tell the difference between dead and busy — only the ratio across many can.
// An Appwrite container is recycled often enough that a recovered provider clears on its own.
const health = new Map();

// Providers that answered 402, held for the container's life.
//
// This is module scope for the opposite reason to `unavailable` below. A 429 clears by the next
// ball, so blacklisting on one is wrong. A 402 does not clear: it is account state — a spent free
// trial (Cerebras answers `param: "quota"`) or an account with no payment method (SambaNova answers
// `balance_units: 0`) — and it holds until someone opens a billing page. Measured 2026-09-15 on
// both accounts: every model they list answered 402, including ones the chain has never used, so it
// is not a model id and no fallback inside that provider reaches past it.
//
// Left per-line, as it was from 2026-08-04, the chain paid two doomed round trips — each with a 15s
// `AbortSignal.timeout` — on every single delivery for six weeks.
//
// Keyed by ACCOUNT, not rung. Three Gemini rungs share one key, one project and one balance, so a
// 402 from any of them is a statement about all three; latching only the rung that saw it would
// leave the chain re-discovering the same dead account twice more per line.
const paymentBlocked = new Set();

// Providers whose PER-DAY quota is spent, held until that quota resets.
//
// The third kind of refusal, and the one the chain had no shape for. `unavailable` is per-line
// because a rate limit clears by the next ball — true of Gemini's per-minute bucket (15 RPM,
// retryDelay ~19s) and false of its per-day one, which holds until midnight Pacific. Treating the
// daily one as transient re-asks a spent rung on every delivery for the rest of the day: the exact
// cost `paymentBlocked` exists to stop, in a status that is not 402.
//
// Google states which it is. A 429 body carries
// `details[].@type = "...QuotaFailure"` with `violations[].quotaId`, and the id spells out the
// window: `GenerateRequestsPerMinutePerProjectPerModel-FreeTier` versus a PerDay sibling. Measured
// 2026-09-16: the per-minute id is confirmed verbatim, with `quotaValue: "15"`. The per-day id has
// NOT been seen — reaching it costs 500 calls — so `PER_DAY_QUOTA_RE` is an inference, and
// `unrecognizedQuotaIds` below exists precisely so the real id is learned from production instead
// of staying a guess.
const dailyExhausted = new Map();

// Quota ids seen on a 429 that match neither window. Drained once per container by the caller.
const unknownQuotaIds = new Set();

const PER_DAY_QUOTA_RE = /PerDay/i;
const PER_MINUTE_QUOTA_RE = /PerMinute/i;

/**
 * Midnight Pacific, as an epoch. Google resets RPD quotas there, and a container that outlives the
 * reset must start asking again rather than sitting out until it is recycled.
 *
 * Derived from the zone's own offset via `Intl` rather than a hardcoded -08:00, because the offset
 * changes twice a year and a fixed one would be an hour wrong for half of every season.
 */
function nextPacificMidnight(now = Date.now()) {
  const zone = "America/Los_Angeles";
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false,
  }).formatToParts(new Date(now));
  const get = (t) => Number(parts.find((x) => x.type === t)?.value);
  // How far "now" is into the Pacific day, in ms. Adding the remainder lands on the next midnight.
  const intoDay = ((get("hour") % 24) * 3600 + get("minute") * 60 + get("second")) * 1000;
  return now + (86_400_000 - intoDay);
}

/**
 * Classify a 429 body and, when it names a per-day quota, latch the rung until the reset.
 *
 * Returns the matched violation so the caller can put the numbers in `attempts` — a skip nobody can
 * read is how the last two outages stayed invisible.
 */
function noteQuota429(providerName, parsedBody, now = Date.now()) {
  const err = parsedBody?.error ?? parsedBody;
  const details = Array.isArray(err?.details) ? err.details : [];
  const failure = details.find((d) => String(d?.["@type"] ?? "").includes("QuotaFailure"));
  const violation = failure?.violations?.[0];
  const quotaId = violation?.quotaId;
  if (!quotaId) return null;
  if (PER_DAY_QUOTA_RE.test(quotaId)) {
    dailyExhausted.set(providerName, nextPacificMidnight(now));
    return { quotaId, quotaValue: violation?.quotaValue, window: "day" };
  }
  if (PER_MINUTE_QUOTA_RE.test(quotaId)) return { quotaId, quotaValue: violation?.quotaValue, window: "minute" };
  // Neither window. Do NOT latch on a shape we do not understand — over-latching silently removes a
  // working rung, which is worse than re-asking one that is busy.
  unknownQuotaIds.add(quotaId);
  return { quotaId, quotaValue: violation?.quotaValue, window: "unknown" };
}

/** True while this rung's per-day quota is still spent. Expired entries clear themselves. */
function dailyQuotaSpent(providerName, now = Date.now()) {
  const until = dailyExhausted.get(providerName);
  if (until === undefined) return false;
  if (now >= until) {
    dailyExhausted.delete(providerName);
    return false;
  }
  return true;
}

/**
 * Rungs that could answer right now, and why each of the others could not.
 *
 * `generateVerifiedLine` retries a failed fact-check with `exclude: [used.provider]` — deliberately
 * a DIFFERENT provider — so a chain with one reachable rung cannot retry at all, and every
 * verification FAIL becomes a raw-text row. That is not a degraded state the caller can infer from
 * anything else: each rung's own skip reason looks ordinary, and only the count is alarming. It ran
 * that way for six weeks. The caller reports on `depth < 2`.
 *
 * `unavailable` is the per-line Set, passed in when there is one.
 */
export function chainDepth(unavailable, now = Date.now()) {
  const rungs = PROVIDERS.map((provider) => {
    if (!process.env[provider.keyEnv]) return { provider: provider.name, reachable: false, reason: `no ${provider.keyEnv}` };
    if (paymentBlocked.has(accountOf(provider))) return { provider: provider.name, reachable: false, reason: "HTTP 402 (account)" };
    if (dailyQuotaSpent(provider.name, now)) return { provider: provider.name, reachable: false, reason: "daily quota spent" };
    if (unavailable?.has(provider.name)) return { provider: provider.name, reachable: false, reason: "failed earlier this line" };
    return { provider: provider.name, reachable: true, reason: null };
  });
  return { depth: rungs.filter((r) => r.reachable).length, rungs };
}

/** Quota ids the classifier did not recognise. Drains, so the caller reports each one once. */
export function drainUnknownQuotaIds() {
  const out = [...unknownQuotaIds];
  unknownQuotaIds.clear();
  return out;
}

function noteHealth(name, outcome, status) {
  const h = health.get(name) ?? { calls: 0, failed: 0, statuses: new Map(), reported: false };
  h.calls += 1;
  if (outcome !== "ok") {
    h.failed += 1;
    const key = status ?? "threw";
    h.statuses.set(key, (h.statuses.get(key) ?? 0) + 1);
  }
  health.set(name, h);
}

/**
 * Providers that have been asked enough times to judge and failed every single one.
 *
 * The tally counted 429 alone until 2026-09-15, which left it blind to the exact outage it exists
 * for. Cerebras and SambaNova answered 402 to 100% of calls for six weeks: every call landed in
 * `calls` and none in the failure column, so the ratio never reached 1 and this alert never fired.
 * The state was legible in every per-line `attempts` string and in no aggregate — which is the
 * failure mode the comment above `health` describes, reproduced by the fix for it.
 *
 * Any non-200 counts now, and the dominant status rides along, because the statuses need opposite
 * responses: 429 is wait, 402 is pay, 404 is a model id.
 *
 * A 402 skips the call threshold entirely. `minCalls` exists because one 429 cannot be told from
 * bad luck in a burst — but one 402 can: it is the provider stating its account is unfunded, and
 * `paymentBlocked` then stops the chain calling it a second time, so its `calls` can never reach
 * the threshold. Requiring the ratio here would leave the alert exactly as blind as it was.
 *
 * Each is returned at most once per container (`reported`), because the caller reports on a
 * per-run basis and a dead provider stays dead — the signal is worth one event, not one a minute.
 */
export function deadProviders(minCalls = DEAD_PROVIDER_MIN_CALLS) {
  const dead = [];
  for (const [name, h] of health) {
    if (h.reported) continue;
    if (!h.statuses.has(402) && (h.calls < minCalls || h.failed !== h.calls)) continue;
    h.reported = true;
    const [status] = [...h.statuses].sort((a, b) => b[1] - a[1])[0] ?? [null];
    dead.push({ provider: name, calls: h.calls, failed: h.failed, status });
  }
  return dead;
}

/** Test seam — both tallies are module state and would otherwise leak between cases. */
export function resetProviderHealth() {
  health.clear();
  paymentBlocked.clear();
  dailyExhausted.clear();
  unknownQuotaIds.clear();
}

// Kept byte-identical to CommentaryFusionService.SYSTEM_INSTRUCTION. The same ball can be narrated
// by either, so a drift between them shows up as two different house styles in one match.
//
// There is deliberately NO example ball number here. The previous wording ended "(e.g., 18.5: ...)",
// and when the ball number was missing from the user prompt the model emitted the example — 33% of
// the commentary cache became "18.5:" across a year of unrelated matches.
export const SYSTEM_INSTRUCTION =
  "You are a professional cricket broadcast commentator. Write exactly one vivid, natural commentary line.\n" +
  "STRICT FORMAT: Begin with the ball number exactly as given in the input, followed by a colon. Never invent, guess or alter it; if the input has no ball number, begin with the action instead. One sentence only. No ellipsis at the end.\n" +
  "HALLUCINATION RULES: Use ONLY player names and match facts present in the input. Never invent runs, wickets, boundaries, or events not stated in the input. The atmosphere is yours — the facts are not.\n" +
  "STYLE: Cinematic, broadcast-quality. Sharp active verbs. Specific detail from the input. No clichés.\n" +
  "Output ONLY the commentary line itself — no preamble, no explanation, no 'Since there is...' framing.";

const HTML_TAG_REGEX = /<[^>]+>/g;
const AI_FUSED_PREFIX = /^\[AI Fused]\s*/;
const MULTI_SPACE = /\s{2,}/g;

// Port of CommentaryFormatter.sanitize
export function sanitize(text) {
  return String(text || "")
    .replace(AI_FUSED_PREFIX, "")
    .replace(HTML_TAG_REGEX, "")
    .replace(MULTI_SPACE, " ")
    .trim();
}

// Kept in sync with CommentaryFormatter.META_PREAMBLE.
//
// The refusal openers ("I cannot", "I'm sorry", "I apologise") close a gap the 2026-07-31 Hundred
// replay found: the list covered the models explaining themselves but not the models declining.
const META = /^(since there|i('| wi)ll |i cannot|i can'?t|i'm (unable|sorry)|i am (unable|sorry)|i apologi[sz]e|here('| i)s|given (the|that|there)|note:|as an ai|based on the|to create|it seems|there is no|there's no|unfortunately)/i;

// Both ball-label forms: `48.6` (over-based) and `Ball 72` (The Hundred). An over-based-only
// pattern here silently leaves the preamble attached to every Hundred line, because the label it
// is looking for never appears in that format. Mirrors CommentaryFormatter.LEADING_BALL_LABEL.
const LEADING_BALL_LABEL = /((?:\d{1,3}\.\d|Ball\s+\d{1,3}):?\s.*)$/;

// Strip the meta/preamble the models sometimes emit (the current cache pollution:
// "Since there is no specific ball number...", "I'll create a commentary line...").
// MUST run on the raw (multi-line) completion, before sanitize() collapses newlines.
export function stripPreamble(text) {
  const lines = String(text || "")
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  const kept = lines.filter((l) => !META.test(l));
  let out = (kept.length ? kept : lines).join(" ").trim();
  // Same-line preamble ("Since there is no ... 18.5: ...") → cut to the ball label.
  if (META.test(out)) {
    const m = out.match(LEADING_BALL_LABEL);
    if (m) out = m[1].trim();
  }
  return out;
}

/**
 * Try each provider in order; return the first non-empty completion, or null.
 *
 * `attempts` carries why each provider declined. Without it the caller only knew "null", and a
 * chain that was down for a bad model id looked identical to one that was rate-limited or one
 * where no key was configured at all — while raw Cricbuzz text went on being written to
 * `match-commentary` as if it were generated.
 */
/**
 * The provider's own error code/message, trimmed to something that fits a Sentry tag.
 *
 * Never throws and never blocks the fallback: a body that cannot be read is simply no detail.
 */
async function readProviderError(resp) {
  try {
    const body = await resp.text();
    if (!body) return { parsed: null, detail: "" };
    let parsed = null;
    try {
      // Google wraps the error object in a one-element array on this endpoint; every other
      // provider sends it bare. Unwrapping here keeps both the detail string and the quota
      // classifier from having to know that.
      const raw = JSON.parse(body);
      parsed = Array.isArray(raw) ? raw[0] : raw;
    } catch {
      return { parsed: null, detail: body.slice(0, PROVIDER_ERROR_DETAIL_MAX) };
    }
    const err = parsed?.error ?? parsed;
    const code = err?.code ?? err?.type ?? "";
    const message = err?.message ?? "";
    return { parsed, detail: [code, message].filter(Boolean).join(": ").slice(0, PROVIDER_ERROR_DETAIL_MAX) };
  } catch {
    return { parsed: null, detail: "" };
  }
}

const PROVIDER_ERROR_DETAIL_MAX = 160;

export async function generateLine(
  prompt,
  {
    maxTokens = 100,
    temperature = 0.7,
    log,
    error,
    attempts,
    // Providers already tried for this line — the hallucination retry must not re-ask the model
    // that just failed the fact-check.
    exclude = [],
    // Set of providers that already failed at the transport level for THIS line, mutated as they
    // fail. `generateVerifiedLine` threads one Set through the generate, the verify, the retry and
    // the retry's verify, so a provider that is down is asked once per line instead of four times:
    // a key that answers 403 does not answer 200 two hundred milliseconds later, and a provider
    // that times out costs 15s of the function's 30s budget every time it is re-asked. Measured on
    // 2026-08-04 with a revoked Groq key, this was 129 pointless round trips across 40 rows.
    //
    // Deliberately NOT populated by "empty completion" — that is a property of one prompt, and the
    // verify prompt is a different one.
    unavailable,
    // Mutated with { provider } so the caller knows who answered without changing the return type.
    used,
    // Verification is not commentary: sending the broadcast-style system prompt with a
    // PASS/FAIL question is how a fact-checker starts narrating.
    systemInstruction = SYSTEM_INSTRUCTION,
    // Reject a completion that stopped because the budget ran out mid-sentence. On by default
    // because that is what a stored commentary line must never be; `verifyLine` turns it off, since
    // its whole answer is the unpunctuated word PASS.
    requireTerminated = true,
  } = {}
) {
  const note = (provider, reason) => attempts?.push(`${provider}: ${reason}`);

  // Test hook, mirrored on PROBE_SOURCES: exercise the exhausted-chain path — raw passthrough,
  // ai_generated:false, Sentry event — without touching a single provider key. Unset in normal
  // operation, so this is dead code on every real run.
  if (process.env.FORCE_LLM_FAILURE) {
    note("all", "forced failure (FORCE_LLM_FAILURE)");
    error?.("[FORCE_LLM_FAILURE] skipping every provider — generation will fall through to raw text");
    return null;
  }

  for (const provider of PROVIDERS) {
    if (exclude.includes(provider.name) || unavailable?.has(provider.name)) continue;
    // Skipped without a call, but still written to `attempts`. Dropping it silently would shrink
    // the record to the providers that were tried, and a three-rung chain that has quietly become
    // one rung is the failure this file has been bitten by three times.
    if (paymentBlocked.has(accountOf(provider))) {
      // The account is only worth naming when it is not the rung — for groq the two are the same
      // word, but "gemini-3.6-flash: HTTP 402 (gemini account latched…)" says the thing that
      // matters: no other Gemini rung will answer either.
      const scope = accountOf(provider) === provider.name ? "" : `${accountOf(provider)} account `;
      note(provider.name, `HTTP 402 (${scope}latched for this container)`);
      continue;
    }
    if (dailyQuotaSpent(provider.name)) {
      note(provider.name, "daily quota spent (latched until the Pacific reset)");
      continue;
    }
    const apiKey = process.env[provider.keyEnv];
    if (!apiKey) {
      note(provider.name, `no ${provider.keyEnv}`);
      unavailable?.add(provider.name);
      continue;
    }
    try {
      const resp = await fetch(provider.url, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
        body: JSON.stringify({
          model: provider.model,
          messages: [
            { role: "system", content: systemInstruction },
            { role: "user", content: prompt },
          ],
          // A reasoning rung spends most of this before it writes a word, so the floor is a
          // property of the model, not of the caller's appetite for output length.
          max_tokens: Math.max(maxTokens, provider.tokenFloor ?? 0),
          temperature,
          ...(provider.extraBody ?? {}),
        }),
        signal: AbortSignal.timeout(15_000),
      });
      if (!resp.ok) {
        // The status alone is not diagnosable. Groq answers 404 both for a decommissioned model
        // and for one this key's tier cannot reach; a 402 may be a spent budget or an unpaid
        // account. Those need different actions, and the difference is only in the body — which is
        // exactly what "attempts: groq: HTTP 404" left out while the chain sat down for weeks.
        const { parsed, detail } = await readProviderError(resp);
        error?.(`${provider.name} HTTP ${resp.status}${detail ? ` (${detail})` : ""}`);
        noteHealth(provider.name, "error", resp.status);
        // A 402 outlives the line and belongs to the account; see `paymentBlocked`.
        if (resp.status === 402) paymentBlocked.add(accountOf(provider));
        // A 429 is two different failures wearing one status. The body says which.
        const quota = resp.status === 429 ? noteQuota429(provider.name, parsed) : null;
        const quotaNote = quota ? ` [${quota.quotaId}=${quota.quotaValue ?? "?"} per ${quota.window}]` : "";
        note(provider.name, `HTTP ${resp.status}${detail ? ` ${detail}` : ""}${quotaNote}`);
        unavailable?.add(provider.name);
        continue;
      }
      // A 200 is a 200 whether or not the completion was usable — an empty completion is a prompt
      // problem, not a provider that is refusing to serve us, and folding it into the tally would
      // let a bad prompt look like a dead account.
      noteHealth(provider.name, "ok");
      const data = await resp.json();
      const content = data.choices?.[0]?.message?.content?.trim();
      const finishReason = data.choices?.[0]?.finish_reason;
      // A budget that ran out mid-sentence is a failed generation, not a short one. Before this,
      // only an EMPTY completion fell through, so a line severed at "…a brilliant 74 off" was
      // stored and rendered exactly like a finished one. Measured on gemini-3.6-flash at
      // max_tokens 512. Terminal punctuation is the cheap, language-agnostic test for "the model
      // reached the end of its sentence"; `stop` alone is not, because a truncated reply reports
      // `length` while a genuinely complete short reply reports `stop`.
      if (content && requireTerminated && finishReason === "length" && !/[.!?"'\u2019\u201d]$/.test(content)) {
        note(provider.name, `truncated mid-sentence (${content.length} chars, finish_reason length)`);
        continue;
      }
      if (content) {
        log?.(`Generated via ${provider.name}`);
        if (used) used.provider = provider.name;
        return content;
      }
      // The finish reason is the diagnosis: `length` means the budget ran out before any content
      // survived, `stop` means the model genuinely answered with nothing. Opposite fixes, and the
      // bare "empty completion" string could not tell them apart.
      note(provider.name, `empty completion${finishReason ? ` (${finishReason})` : ""}`);
    } catch (e) {
      error?.(`${provider.name} error: ${e?.message}`);
      note(provider.name, e?.message ?? "threw");
      noteHealth(provider.name, "error");
      unavailable?.add(provider.name);
    }
  }
  return null;
}

// Kept byte-identical in intent to CommentaryFusionService.VERIFICATION_PROMPT_TEMPLATE, which this
// replaces. The check moved server-side when the device stopped holding provider keys — the guard
// against invented names and events is worth the same two LLM calls it always cost, it just cannot
// run anywhere the keys are public.
export const VERIFICATION_PROMPT_TEMPLATE = `You are a cricket commentary fact-checker. Verify that the AI-generated commentary does not hallucinate.

Source 1: {snippet1}
Source 2: {snippet2}
AI output: {ai_output}
{ball_field}Bowler: {bowler} | Batsman: {batsman}

Answer ONLY "PASS" if accurate and uses only correct names from the sources, or "FAIL" if it contains hallucinated names, invented details, or unsupported information.
One word: PASS or FAIL.`;

// Cricbuzz capitalises the event itself and lowercases the prose around it, so these are
// deliberately CASE-SENSITIVE: `\bSIX\b` does not match "sixth", `\bFOUR\b` does not match "fourth",
// and `\bOUT\b` does not match the "outside off" / "drove it out to deep cover" that appear on
// almost every delivery. A case-insensitive version of these matches most of the innings.
// The outcome clause is the structural half of this classifier; see classifyEvent.
const UPPER_SIX_RE = /\bSIX\b/;
const UPPER_FOUR_RE = /\bFOUR\b/;
const UPPER_WICKET_RE = /\b(?:OUT|WICKET)\b/;
const UPPER_EVENT_RE = /\b(?:FOUR|SIX|OUT|WICKET)\b/;

// The other dismissal shape Cricbuzz writes, where "out" is lowercase and the method follows:
// "Ashwin to Smith, out Caught by Kohli". Case-insensitive is safe HERE because the method word
// is what carries the meaning — "out" alone is not enough to match.
const DISMISSAL_RE = /\bout\s+(?:caught|bowled|lbw|stumped|run\s+out|hit\s+wicket|retired)\b/i;

// Mirrors TimelineEventExtractor.wicketVetoPattern: a dropped catch, a dismissal off a no-ball or
// a free hit, and an on-field OUT the batter reviewed their way out of, are all not wickets.
//
// The review clause is the one that reached production. Cricbuzz writes the on-field decision and
// the review in the same item — "Pasindu Sooriyabandara has been given OUT LBW, but he reviews.
// … good review and Sooriyabandara survives" — and `UPPER_WICKET_RE` fires on that `OUT`. Two
// overturned LBWs were stored as `event: "WICKET"` on match 163017 and were the entire contents of
// the commentary tab's Wickets filter. `isWicketLine` also drives the "Wicket!" push notification,
// so the same false positive rings a phone.
const WICKET_VETO_RE =
  /\b(?:dropped|drops\s+(?:it|him|her)|no\s*[- ]?ball|free\s*hit|reviews?|reviewed|survives?|overturn(?:ed|s)?|reversed|not\s+out|reprieve)\b/i;

/**
 * What actually happened on this delivery, read from the RAW Cricbuzz text.
 *
 * This exists because the answer is unrecoverable downstream. Every line is rewritten into
 * broadcast prose before it is stored, and SYSTEM_INSTRUCTION asks for "sharp active verbs" and
 * "no clichés" — so a four becomes "races away to the rope" and the token `FOUR` is gone. The
 * commentary tab's Boundaries and Wickets filters were classifying the stored text with the same
 * `\bFOUR\b` / `\bSIX\b` regexes Cricbuzz's own wording satisfies, which meant they matched only
 * the rows where generation FAILED and the raw text was written through. Storing the verdict here,
 * upstream of the rewrite, is the only place the information still exists.
 *
 * Returns "WICKET" | "SIX" | "FOUR" | "NONE". Wicket outranks a boundary mentioned on the same
 * line, matching TimelineEventExtractor.detectBallType, which stays on the device as the fallback
 * for scraped lines and for rows written before this column existed.
 *
 * **Total on purpose — an ordinary delivery is "NONE", never absent.** The device treats a missing
 * column as "the server had no opinion" and classifies the line itself, so if a dot ball were
 * written with no `event` the AI's own prose would be searched for the word "six" — and "he needs
 * six off four" is a sentence a commentator writes. Saying NONE is what makes the absence of a
 * boundary a fact rather than a gap.
 *
 * Deliberately no MILESTONE: see isHighStakesBall — milestones are not reliably marked in
 * `commText`, and guessing at them here would put a guess in the database.
 */
export function classifyEvent(text) {
  const t = String(text || "");
  const clause = outcomeClause(t);
  // The clause is checked BEFORE any marker in the prose, because the prose is where the false
  // positives live. Cricbuzz writes an on-field decision and its review in one item — "Manav Suthar
  // to Pasindu Sooriyabandara, no run, ... has been given OUT LBW, but he reviews ... and
  // Sooriyabandara survives" — and `UPPER_WICKET_RE` fires on that OUT. Two overturned LBWs were
  // stored as WICKET on match 163017 and were the whole contents of the Wickets filter. The clause
  // on such a line is "no run", and a delivery whose clause states runs is not a dismissal,
  // whatever the description goes on to say.
  if (OUTCOME_WICKET_RE.test(clause)) return "WICKET";
  if (SCORING_CLAUSE_RE.test(clause)) return clauseEvent(clause);
  if (!WICKET_VETO_RE.test(t) && (UPPER_WICKET_RE.test(t) || DISMISSAL_RE.test(t))) return "WICKET";
  if (UPPER_SIX_RE.test(t)) return "SIX";
  if (UPPER_FOUR_RE.test(t)) return "FOUR";
  return "NONE";
}

// Clause forms. `OUTCOME_WICKET_RE` is the dismissal clause cricbuzz.js's parseBallOutcome also
// reads; `SCORING_CLAUSE_RE` is every clause that states an outcome in runs, which is a positive
// statement that the ball was NOT a dismissal.
const OUTCOME_WICKET_RE = /^(?:out\b|that'?s out\b)/i;
const SCORING_CLAUSE_RE = /^(?:no run|\d{1,3} runs?|four|six)$/i;

const BALL_OUTCOME = /^[^,]+,\s*([^,]+)/;
const TRAILING_PUNCTUATION = /[.!]+$/;

/**
 * Cricbuzz's outcome clause: the field after "<Bowler> to <Batter>," and before the description.
 *
 * The only STRUCTURAL statement of what a delivery was. Everything after it is prose, and prose
 * about a delivery routinely contains the words for other deliveries.
 *
 * It lives HERE rather than in cricbuzz.js — which is its other caller, via parseBallOutcome —
 * because this file is vendored byte-identically into `generate-commentary`, which has no
 * cricbuzz.js. An import in this direction would break that deployment; the import goes the other
 * way instead. Mirrored on the device by TimelineEventExtractor.outcomeClause.
 *
 * Trailing punctuation is trimmed because callers match the clause whole: the passthrough path
 * writes Cricbuzz's text verbatim, and a "1 run." reaching an anchored `^(\d+) runs?$` would score
 * null and lose a delivery the raw text names outright.
 */
export function outcomeClause(text) {
  const m = String(text || "").match(BALL_OUTCOME);
  return m ? m[1].trim().replace(TRAILING_PUNCTUATION, "") : "";
}

function clauseEvent(clause) {
  if (/^six$/i.test(clause)) return "SIX";
  if (/^four$/i.test(clause)) return "FOUR";
  return "NONE";
}

/**
 * Whether this delivery is worth fact-checking.
 *
 * Verification doubles the provider cost of a line — generate, then verify, then on a FAIL another
 * generate and another verify. Measured 2026-08-05, the combined chain sustains ~48 requests/minute
 * (groq 30 + sambanova ~13 + cerebras 5) against a groq budget of 1000/day, so at two calls a line
 * the whole app can produce roughly 1,700 verified lines a day. Most deliveries are a dot or a
 * single, where the model has almost nothing to invent and the check reliably returns PASS.
 *
 * Wickets and boundaries are the opposite: they carry a fielder, a dismissal method, a shot and a
 * region, they are the lines a reader actually remembers, and they are where an invented detail
 * does real damage. So the check is spent there.
 *
 * Biased toward saying yes. A false positive costs one extra provider call; a false negative ships
 * an unverified wicket. Deliberately NOT covered: milestones (a fifty, a hundred) — they are
 * hallucination-prone too, but they are not reliably marked in `commText` and guessing at them
 * would drag most of the innings back into verification.
 *
 * Shares its vocabulary with [classifyEvent] but is deliberately NOT
 * `classifyEvent(t) !== "NONE"`: that function applies WICKET_VETO_RE, and a dropped catch or a
 * dismissal off a no-ball is exactly the kind of line a model invents a fielder for. Vetoing here
 * would narrow the gate in the direction this doc says not to go.
 */
export function isHighStakesBall(text) {
  const t = String(text || "");
  return UPPER_EVENT_RE.test(t) || DISMISSAL_RE.test(t);
}

// A fact-checker must not be told to write cinematic prose.
const VERIFY_SYSTEM_INSTRUCTION =
  "You are a precise fact-checker. Answer with exactly one word: PASS or FAIL.";

/**
 * Whether [line] is supported by the source snippets.
 *
 * Fails **closed**: no provider available means no verdict, and caching unverified content is worse
 * than discarding it (the same P0-5 reasoning the Kotlin implementation carried). Temperature 0 so
 * the same inputs always produce the same verdict.
 */
export async function verifyLine(
  { line, snippet1 = "", snippet2 = "", ball = "", bowler = "", batsman = "" },
  { log, error, attempts, unavailable } = {}
) {
  const prompt = VERIFICATION_PROMPT_TEMPLATE
    .replace("{snippet1}", snippet1)
    .replace("{snippet2}", snippet2)
    .replace("{ai_output}", line)
    .replace("{ball_field}", ball ? `Ball: ${ball} | ` : "")
    .replace("{bowler}", bowler)
    .replace("{batsman}", batsman);

  const verdict = await generateLine(prompt, {
    // 256, not 16, and not 64. Every provider in the chain is a reasoning model — groq runs
    // gpt-oss-20b with `reasoning_effort: low` — and reasoning tokens come out of the same budget as
    // the answer. At 16 the budget was spent before a PASS could be emitted, so groq returned an
    // empty completion on the verify prompt while answering the generate prompt (maxTokens 100)
    // perfectly well; with the other two providers on 402, verification failed closed on every ball.
    // That is the whole of "match-sync wrote raw Cricbuzz text" (ANDROID-38) — generation was never
    // the broken part.
    //
    // 64 was measured in production on 2026-09-12 and is still not enough: one ball verified, the
    // next returned `empty completion (length)` again. The verdict is four characters; the budget is
    // sized by how long the model thinks before emitting them, which is not a quantity this file can
    // predict. 256 leaves room for that and still costs only what is actually generated.
    maxTokens: 256,
    temperature: 0,
    systemInstruction: VERIFY_SYSTEM_INSTRUCTION,
    // The verdict is the bare word PASS or FAIL — no terminal punctuation, by instruction. The
    // truncation guard would throw away every correct verdict in the chain.
    requireTerminated: false,
    log,
    error,
    attempts,
    unavailable,
  });
  if (!verdict) {
    error?.("Verification unavailable — failing closed (FAIL)");
    return false;
  }
  return verdict.trim().toUpperCase().startsWith("PASS");
}

/**
 * Generate a line and, when [verify] is set, fact-check it — retrying once with a *different*
 * provider before giving up.
 *
 * Returns `{ line, provider, verified, reason }`. A null `line` is the caller's signal to write the
 * raw source text and mark the row `ai_generated: false`; `reason` distinguishes "nobody answered"
 * from "the answer was rejected", which are the same symptom and very different causes.
 */
export async function generateVerifiedLine(prompt, options = {}) {
  const { verify = false, context = {}, attempts, log, error, ...genOptions } = options;
  // One Set for the whole line. Scoped per call, never module-level: a provider that is rate-limited
  // for this ball may well answer the next one, and an Appwrite container is reused across
  // executions, so a longer-lived Set would blacklist a recovered provider for the container's life.
  const unavailable = new Set();
  const pass = { attempts, log, error, unavailable };

  const used = {};
  const first = await generateLine(prompt, { ...genOptions, ...pass, used });
  if (!first) return { line: null, provider: null, verified: false, reason: "providers_exhausted" };
  if (!verify) return { line: first, provider: used.provider, verified: null, reason: null };

  if (await verifyLine({ line: first, ...context }, pass)) {
    return { line: first, provider: used.provider, verified: true, reason: null };
  }

  attempts?.push(`${used.provider ?? "unknown"}: verification FAIL`);
  const retryUsed = {};
  const second = await generateLine(prompt, {
    ...genOptions,
    ...pass,
    exclude: used.provider ? [used.provider] : [],
    used: retryUsed,
  });
  if (second && (await verifyLine({ line: second, ...context }, pass))) {
    log?.(`Hallucination retry succeeded with ${retryUsed.provider}`);
    return { line: second, provider: retryUsed.provider, verified: true, reason: null };
  }

  return { line: null, provider: null, verified: false, reason: "verification_failed" };
}
