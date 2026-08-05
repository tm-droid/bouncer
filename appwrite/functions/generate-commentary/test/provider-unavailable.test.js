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

const PROVIDER_KEYS = ["GROQ_API_KEY", "CEREBRAS_API_KEY", "SAMBANOVA_API_KEY"];

function providerOf(url) {
  if (url.includes("groq.com")) return "groq";
  if (url.includes("cerebras.ai")) return "cerebras";
  if (url.includes("sambanova.ai")) return "sambanova";
  throw new Error(`unexpected provider url ${url}`);
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

  const calls = { groq: 0, cerebras: 0, sambanova: 0 };
  globalThis.fetch = async (url) => {
    const name = providerOf(String(url));
    calls[name] += 1;
    return handler(name, calls[name]);
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

  // groq and cerebras are down. sambanova generates, then rejects its own line — which forces the
  // hallucination retry, so this one call exercises all four traversals.
  const { result, calls } = await withStubbedProviders(
    (name, nth) => {
      if (name !== "sambanova") return rateLimited();
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
  assert.equal(calls.cerebras, 1, "same for cerebras");
  assert.equal(calls.sambanova, 2, "sambanova generated, then verified its own line");

  assert.equal(result.line, null);
  assert.equal(result.reason, "verification_failed", "the retry had no provider left to try");

  // The reason channel is what reaches Sentry. A dead provider repeated four times there is noise
  // that hides the one attempt that mattered.
  assert.deepEqual(
    attempts.filter((a) => a.startsWith("groq:")),
    ["groq: HTTP 429"],
    "groq accounts for itself exactly once"
  );
});

test("the skip does not survive the call — a recovered provider is asked again on the next line", async () => {
  // Appwrite reuses containers across executions, so this is the difference between a one-ball
  // hiccup and a provider switched off for the life of the container.
  let line = 1;
  const { calls } = await withStubbedProviders(
    (name) => (name === "groq" && line === 1 ? rateLimited() : completion("18.6: driven for four.")),
    async () => {
      await generateVerifiedLine("18.5: Bumrah to Root, no run", { verify: false });
      line = 2;
      await generateVerifiedLine("18.6: Bumrah to Root, FOUR", { verify: false });
    }
  );

  assert.equal(calls.groq, 2, "groq is asked again on the second line despite failing on the first");
  assert.equal(calls.cerebras, 1, "only the first line had to fall through");
});
