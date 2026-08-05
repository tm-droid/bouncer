// LLM provider chain + sanitization for server-side commentary generation.
// Mirrors generate-commentary/src/main.js (PROVIDERS) and the Kotlin
// CommentaryFusionService.SYSTEM_INSTRUCTION + CommentaryFormatter.sanitize.

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
const PROVIDERS = [
  { name: "groq",      url: "https://api.groq.com/openai/v1/chat/completions",  model: "llama-3.3-70b-versatile",     keyEnv: "GROQ_API_KEY" },
  { name: "cerebras",  url: "https://api.cerebras.ai/v1/chat/completions",      model: "gemma-4-31b",                 keyEnv: "CEREBRAS_API_KEY" },
  { name: "sambanova", url: "https://api.sambanova.ai/v1/chat/completions",     model: "DeepSeek-V3.1",               keyEnv: "SAMBANOVA_API_KEY" },
];

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
          max_tokens: maxTokens,
          temperature,
        }),
        signal: AbortSignal.timeout(15_000),
      });
      if (!resp.ok) {
        error?.(`${provider.name} HTTP ${resp.status}`);
        note(provider.name, `HTTP ${resp.status}`);
        unavailable?.add(provider.name);
        continue;
      }
      const data = await resp.json();
      const content = data.choices?.[0]?.message?.content?.trim();
      if (content) {
        log?.(`Generated via ${provider.name}`);
        if (used) used.provider = provider.name;
        return content;
      }
      note(provider.name, "empty completion");
    } catch (e) {
      error?.(`${provider.name} error: ${e?.message}`);
      note(provider.name, e?.message ?? "threw");
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
    maxTokens: 16,
    temperature: 0,
    systemInstruction: VERIFY_SYSTEM_INSTRUCTION,
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
