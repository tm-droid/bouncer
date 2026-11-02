import test from "node:test";
import assert from "node:assert/strict";

import { generateVerifiedLine, verifyLine } from "../src/llm.js";

// The hallucination check moved server-side when the provider keys left the APK. These tests pin
// the two behaviours that decide whether a raw Cricbuzz line reaches `match-commentary`: failing
// closed when no provider can answer, and distinguishing "nobody answered" from "the answer was
// rejected". They run offline — no key is set, so every provider declines.

const PROVIDER_KEYS = ["GROQ_API_KEY", "GEMINI_API_KEY", "CEREBRAS_API_KEY", "SAMBANOVA_API_KEY"];

function withoutProviderKeys(run) {
  const saved = PROVIDER_KEYS.map((k) => [k, process.env[k]]);
  for (const k of PROVIDER_KEYS) delete process.env[k];
  try {
    return run();
  } finally {
    for (const [k, v] of saved) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
}

test("verification fails closed when no provider can answer", async () => {
  await withoutProviderKeys(async () => {
    // Caching unverified content is worse than discarding it — the same reasoning the Kotlin
    // implementation carried before the check moved here.
    const passed = await verifyLine({
      line: "18.5: Bumrah bowls a jaffa.",
      snippet1: "Bumrah to Root, no run",
      ball: "18.5",
    });
    assert.equal(passed, false);
  });
});

test("an exhausted chain reports providers_exhausted, not a verification failure", async () => {
  await withoutProviderKeys(async () => {
    const attempts = [];
    const result = await generateVerifiedLine("18.5: Bumrah to Root, no run", {
      verify: true,
      context: { snippet1: "Bumrah to Root, no run", ball: "18.5" },
      attempts,
    });

    assert.equal(result.line, null, "no line means the caller writes raw text");
    assert.equal(result.reason, "providers_exhausted");
    assert.equal(result.verified, false);
    assert.ok(attempts.length >= 3, "every provider accounts for itself");
  });
});

test("FORCE_LLM_FAILURE short-circuits the chain and says so", async () => {
  process.env.FORCE_LLM_FAILURE = "1";
  try {
    const attempts = [];
    const result = await generateVerifiedLine("18.5: Bumrah to Root, no run", { attempts });

    assert.equal(result.line, null);
    assert.equal(result.reason, "providers_exhausted");
    assert.deepEqual(attempts, ["all: forced failure (FORCE_LLM_FAILURE)"]);
  } finally {
    delete process.env.FORCE_LLM_FAILURE;
  }
});

test("verification is skipped entirely when not requested", async () => {
  // Recap prompts have no source snippets to check against; a verdict there could not mean
  // anything, so it must not be paid for.
  process.env.FORCE_LLM_FAILURE = "1";
  try {
    const attempts = [];
    await generateVerifiedLine("Summarize the last over", { verify: false, attempts });
    assert.equal(attempts.length, 1, "one generation attempt, no verification attempt");
  } finally {
    delete process.env.FORCE_LLM_FAILURE;
  }
});
