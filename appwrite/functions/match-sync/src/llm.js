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
const PROVIDERS = [
  { name: "groq",      url: "https://api.groq.com/openai/v1/chat/completions",  model: "llama-3.3-70b-versatile",     keyEnv: "GROQ_API_KEY" },
  { name: "cerebras",  url: "https://api.cerebras.ai/v1/chat/completions",      model: "gemma-4-31b",                 keyEnv: "CEREBRAS_API_KEY" },
  { name: "sambanova", url: "https://api.sambanova.ai/v1/chat/completions",     model: "DeepSeek-V3.1",               keyEnv: "SAMBANOVA_API_KEY" },
];

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

function noteHealth(name, outcome) {
  const h = health.get(name) ?? { calls: 0, rateLimited: 0, reported: false };
  h.calls += 1;
  if (outcome === "rate_limited") h.rateLimited += 1;
  health.set(name, h);
}

/**
 * Providers that have been asked enough times to judge and answered 429 to every single one.
 *
 * Each is returned at most once per container (`reported`), because the caller reports on a
 * per-run basis and a dead provider stays dead — the signal is worth one event, not one a minute.
 */
export function deadProviders(minCalls = DEAD_PROVIDER_MIN_CALLS) {
  const dead = [];
  for (const [name, h] of health) {
    if (h.reported || h.calls < minCalls || h.rateLimited !== h.calls) continue;
    h.reported = true;
    dead.push({ provider: name, calls: h.calls, rateLimited: h.rateLimited });
  }
  return dead;
}

/** Test seam — the tally is module state and would otherwise leak between cases. */
export function resetProviderHealth() {
  health.clear();
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
async function providerErrorDetail(resp) {
  try {
    const body = await resp.text();
    if (!body) return "";
    let parsed;
    try {
      parsed = JSON.parse(body);
    } catch {
      return body.slice(0, PROVIDER_ERROR_DETAIL_MAX);
    }
    const err = parsed?.error ?? parsed;
    const code = err?.code ?? err?.type ?? "";
    const message = err?.message ?? "";
    return [code, message].filter(Boolean).join(": ").slice(0, PROVIDER_ERROR_DETAIL_MAX);
  } catch {
    return "";
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
        // The status alone is not diagnosable. Groq answers 404 both for a decommissioned model
        // and for one this key's tier cannot reach; a 402 may be a spent budget or an unpaid
        // account. Those need different actions, and the difference is only in the body — which is
        // exactly what "attempts: groq: HTTP 404" left out while the chain sat down for weeks.
        const detail = await providerErrorDetail(resp);
        error?.(`${provider.name} HTTP ${resp.status}${detail ? ` (${detail})` : ""}`);
        note(provider.name, `HTTP ${resp.status}${detail ? ` ${detail}` : ""}`);
        noteHealth(provider.name, resp.status === 429 ? "rate_limited" : "error");
        unavailable?.add(provider.name);
        continue;
      }
      // A 200 is a 200 whether or not the completion was usable — an empty completion is a prompt
      // problem, not a provider that is refusing to serve us, and folding it into the tally would
      // let a bad prompt look like a dead account.
      noteHealth(provider.name, "ok");
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
const UPPER_SIX_RE = /\bSIX\b/;
const UPPER_FOUR_RE = /\bFOUR\b/;
const UPPER_WICKET_RE = /\b(?:OUT|WICKET)\b/;
const UPPER_EVENT_RE = /\b(?:FOUR|SIX|OUT|WICKET)\b/;

// The other dismissal shape Cricbuzz writes, where "out" is lowercase and the method follows:
// "Ashwin to Smith, out Caught by Kohli". Case-insensitive is safe HERE because the method word
// is what carries the meaning — "out" alone is not enough to match.
const DISMISSAL_RE = /\bout\s+(?:caught|bowled|lbw|stumped|run\s+out|hit\s+wicket|retired)\b/i;

// Mirrors TimelineEventExtractor.wicketVetoPattern: a dropped catch, or a dismissal off a no-ball
// or a free hit, is not a wicket.
const WICKET_VETO_RE = /\b(?:dropped|drops\s+(?:it|him|her)|no\s*[- ]?ball|free\s*hit)\b/i;

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
  if (!WICKET_VETO_RE.test(t) && (UPPER_WICKET_RE.test(t) || DISMISSAL_RE.test(t))) return "WICKET";
  if (UPPER_SIX_RE.test(t)) return "SIX";
  if (UPPER_FOUR_RE.test(t)) return "FOUR";
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
