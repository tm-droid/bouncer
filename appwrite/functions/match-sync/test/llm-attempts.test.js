import test from "node:test";
import assert from "node:assert/strict";

import { generateLine } from "../src/llm.js";

// A null from generateLine is what makes match-sync write raw Cricbuzz text into match-commentary
// as if it were AI commentary. For a month that null carried no reason with it, so "no key
// configured", "bad model id" and "rate limited" were the same event — none of them observable.
// These tests pin the reason channel, not the network.

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

test("with no keys configured, every provider records why it was skipped", async () => {
  await withoutProviderKeys(async () => {
    const attempts = [];
    const line = await generateLine("18.5: Bumrah to Root, no run", { attempts });

    assert.equal(line, null, "no provider can answer without a key");
    assert.equal(attempts.length, 4, "all four rungs must account for themselves");
    assert.deepEqual(
      attempts,
      [
        // Rung order, and it is asserted as order: gemini-3.5-flash-lite leads because it was
        // measured more accurate on real deliveries than gpt-oss-20b.
        "gemini-flash-lite: no GEMINI_API_KEY",
        "groq: no GROQ_API_KEY",
        // Three rungs, one key: the missing variable is named once per RUNG, not once per key.
        // Collapsing them would hide which models the chain still believes it has.
        "gemini-3.6-flash: no GEMINI_API_KEY",
        "gemini-3-flash-preview: no GEMINI_API_KEY",
      ],
      "the reason must name the missing variable, not just say 'failed'"
    );
  });
});

test("attempts is optional — callers that do not pass it still work", async () => {
  await withoutProviderKeys(async () => {
    // The write loop is the only caller that needs the reasons; a missing array must not throw,
    // or an unrelated call site takes the whole sync run down with it.
    const line = await generateLine("18.5: Bumrah to Root, no run", {});
    assert.equal(line, null);
  });
});
