import test from "node:test";
import assert from "node:assert/strict";

import { generateVerifiedLine } from "../src/llm.js";

// One line can traverse the provider chain four times: generate, verify, the hallucination retry,
// and that retry's verify. Until 2026-08-05 each traversal restarted at groq, so a provider that was
// simply down was re-asked every time. With a revoked Groq key in `bouncer-generate-commentary` the
// burst run on 2026-08-04 measured 129 wasted round trips across 40 rows — and a *timing out*
// provider is worse than a 403 one, because each re-ask spends 15s of a 30s function budget.
//
// These tests pin the two halves of the fix: within a line a dead provider is asked once, and
// across lines it is asked again. The second half matters as much as the first — Appwrite reuses a
// container across executions, so a skip with any lifetime beyond the call would blacklist a
// recovered provider until the container was recycled.

const PROVIDER_KEYS = ["GROQ_API_KEY", "GEMINI_API_KEY"];

// Routing is by MODEL, not by URL, and that is now load-bearing: three of the four rungs post to
// the same Gemini endpoint and are distinct only in the body. A url-based stub would collapse them
// into one and quietly stop testing the fallback between them.
const MODEL_TO_PROVIDER = {
  "openai/gpt-oss-20b": "groq",
  "gemini-3.5-flash-lite": "gemini-flash-lite",
  "gemini-3.6-flash": "gemini-3.6-flash",
  "gemini-3-flash-preview": "gemini-3-flash-preview",
};

const PROVIDER_ORDER = ["gemini-flash-lite", "groq", "gemini-3.6-flash", "gemini-3-flash-preview"];

function providerOf(init) {
  const model = JSON.parse(init?.body ?? "{}").model;
  const name = MODEL_TO_PROVIDER[model];
  if (!name) throw new Error(`unexpected provider model ${model}`);
  return name;
}

function completion(text) {
  return new Response(JSON.stringify({ choices: [{ message: { content: text } }] }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

const rateLimited = () => new Response("rate limited", { status: 429 });

/**
 * Run [body] with every provider key set and `fetch` replaced by [handler], which is given the
 * provider name and that provider's 1-based call count. Returns the per-provider call counts.
 */
async function withStubbedProviders(handler, body) {
  const savedKeys = PROVIDER_KEYS.map((k) => [k, process.env[k]]);
  const savedFetch = globalThis.fetch;
  for (const k of PROVIDER_KEYS) process.env[k] = "test-key";

  const calls = Object.fromEntries(PROVIDER_ORDER.map((n) => [n, 0]));
  globalThis.fetch = async (url, init) => {
    const name = providerOf(init);
    calls[name] += 1;
    return handler(name, calls[name], JSON.parse(init?.body ?? "{}"));
  };

  try {
    return { result: await body(), calls };
  } finally {
    globalThis.fetch = savedFetch;
    for (const [k, v] of savedKeys) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
}

test("a provider that fails is asked once per line, not once per chain traversal", async () => {
  const attempts = [];

  // Every rung but the LAST is down. The last generates, then rejects its own line — which forces
  // the hallucination retry, so this one call exercises all four traversals. It has to be the last
  // rung: `generateVerifiedLine` retries with `exclude: [used.provider]`, so if anything sat below
  // it the retry would succeed and this test would stop measuring re-asks.
  const LAST = "gemini-3-flash-preview";
  const { result, calls } = await withStubbedProviders(
    (name, nth) => {
      if (name !== LAST) return rateLimited();
      return completion(nth === 1 ? "18.5: Bumrah bowls a jaffa." : "FAIL");
    },
    () =>
      generateVerifiedLine("18.5: Bumrah to Root, no run", {
        verify: true,
        context: { snippet1: "Bumrah to Root, no run", ball: "18.5" },
        attempts,
      })
  );

  assert.equal(calls.groq, 1, "groq answered 429 on the generate — nothing after may re-ask it");
  assert.equal(calls["gemini-flash-lite"], 1, "same for the first Gemini rung");
  assert.equal(calls["gemini-3.6-flash"], 1, "and the second");
  assert.equal(calls[LAST], 2, "the last rung generated, then verified its own line");

  assert.equal(result.line, null);
  assert.equal(result.reason, "verification_failed", "the retry had no provider left to try");

  // The reason channel is what reaches Sentry. A dead provider repeated four times there is noise
  // that hides the one attempt that mattered.
  assert.deepEqual(
    attempts.filter((a) => a.startsWith("groq:")),
    // The body is deliberately part of the note: a 429 with "rate limited" is a busy provider and a
    // 429 with a quota message is a dead one, and the status alone cannot tell them apart. This
    // expectation predates that and had been failing since providerErrorDetail landed.
    ["groq: HTTP 429 rate limited"],
    "groq accounts for itself exactly once"
  );
});

test("the skip does not survive the call — a recovered provider is asked again on the next line", async () => {
  // Appwrite reuses containers across executions, so this is the difference between a one-ball
  // hiccup and a provider switched off for the life of the container.
  let line = 1;
  const FIRST = PROVIDER_ORDER[0];
  const { calls } = await withStubbedProviders(
    (name) => (name === FIRST && line === 1 ? rateLimited() : completion("18.6: driven for four.")),
    async () => {
      await generateVerifiedLine("18.5: Bumrah to Root, no run", { verify: false });
      line = 2;
      await generateVerifiedLine("18.6: Bumrah to Root, FOUR", { verify: false });
    }
  );

  assert.equal(calls[FIRST], 2, "the first rung is asked again on the second line despite failing on the first");
  assert.equal(calls[PROVIDER_ORDER[1]], 1, "only the first line had to fall through");
});
