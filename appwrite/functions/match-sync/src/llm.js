// LLM provider chain + sanitization for server-side commentary generation.
// Mirrors generate-commentary/src/main.js (PROVIDERS) and the Kotlin
// CommentaryFusionService.SYSTEM_INSTRUCTION + CommentaryFormatter.sanitize.

// Duplicated in generate-commentary/src/main.js and the Kotlin CommentaryFusionService.providers.
// No shared config channel exists across Kotlin + two Node functions without codegen or a fetched
// manifest, so a model bump is a three-place edit — change all three together.
const PROVIDERS = [
  { name: "groq",      url: "https://api.groq.com/openai/v1/chat/completions",  model: "llama-3.3-70b-versatile",     keyEnv: "GROQ_API_KEY" },
  { name: "cerebras",  url: "https://api.cerebras.ai/v1/chat/completions",      model: "llama-3.3-70b",               keyEnv: "CEREBRAS_API_KEY" },
  { name: "sambanova", url: "https://api.sambanova.ai/v1/chat/completions",     model: "Meta-Llama-3.3-70B-Instruct", keyEnv: "SAMBANOVA_API_KEY" },
];

// Kept byte-identical to CommentaryFusionService.SYSTEM_INSTRUCTION. The same ball can be narrated
// by either, so a drift between them shows up as two different house styles in one match.
export const SYSTEM_INSTRUCTION =
  "You are a professional cricket broadcast commentator. Write exactly one vivid, natural commentary line.\n" +
  "STRICT FORMAT: Begin with the ball number (e.g., 18.5: ...). One sentence only. No ellipsis at the end.\n" +
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

const META = /^(since there|i('| wi)ll |here('| i)s|given (the|that|there)|note:|as an ai|based on the|to create)/i;

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
  // Same-line preamble ("Since there is no ... 18.5: ...") → cut to the first ball number.
  if (META.test(out)) {
    const m = out.match(/(\d{1,3}\.\d:?\s.*)$/);
    if (m) out = m[1].trim();
  }
  return out;
}

// Try each provider in order; return the first non-empty completion, or null.
export async function generateLine(prompt, { maxTokens = 100, temperature = 0.7, log, error } = {}) {
  for (const provider of PROVIDERS) {
    const apiKey = process.env[provider.keyEnv];
    if (!apiKey) continue;
    try {
      const resp = await fetch(provider.url, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
        body: JSON.stringify({
          model: provider.model,
          messages: [
            { role: "system", content: SYSTEM_INSTRUCTION },
            { role: "user", content: prompt },
          ],
          max_tokens: maxTokens,
          temperature,
        }),
        signal: AbortSignal.timeout(15_000),
      });
      if (!resp.ok) {
        error?.(`${provider.name} HTTP ${resp.status}`);
        continue;
      }
      const data = await resp.json();
      const content = data.choices?.[0]?.message?.content?.trim();
      if (content) {
        log?.(`Generated via ${provider.name}`);
        return content;
      }
    } catch (e) {
      error?.(`${provider.name} error: ${e?.message}`);
    }
  }
  return null;
}
