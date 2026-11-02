// Two guards, both for defects that are invisible at runtime.
//
// 1. isHighStakesBall decides whether a delivery is worth a second provider call. Too greedy and
//    the narrowing saves nothing; too shy and a wicket ships unverified. The cases below are the
//    ones that actually bite: uppercase event markers versus the ordinary prose that contains the
//    same letters ("outside off", "sixth", "fourth").
// 2. deadProviders separates a provider that is permanently dead from one that is briefly busy.
//    It exists because that distinction was undetectable for an unknown period. It counted 429
//    alone until 2026-09-15, which is how Cerebras and SambaNova sat on 402 for six weeks without
//    ever raising it — so the 402 cases below are the ones that matter most.
import { test } from "node:test";
import assert from "node:assert/strict";

import { isHighStakesBall, deadProviders, resetProviderHealth, generateVerifiedLine } from "../src/llm.js";

test("wickets and boundaries are verified", () => {
  for (const text of [
    "Starc to Gill, FOUR, cracked through cover point",
    "Jansen to Banton, SIX, into the second tier",
    "Rashid to Root, OUT! Bowled him through the gate",
    "Ashwin to Smith, out Caught by Kohli!!",
    "Boult to Head, out Lbw b Boult",
    "Cummins to Duckett, out Run out (Smith)",
    "Starc to Root, WICKET",
  ]) {
    assert.equal(isHighStakesBall(text), true, text);
  }
});

test("ordinary deliveries are not — including the prose that merely looks like an event", () => {
  for (const text of [
    "Bumrah to Root, no run",
    "Bumrah to Head, 1 run, worked away to midwicket",
    // The three false-positive traps. A case-insensitive regex matches all of them.
    "Starc to Gill, no run, beaten outside off stump",
    "Bumrah to Head, no run, driven out to deep cover",
    "Rashid to Root, 2 runs, the sixth delivery of a fine over",
    "Boult to Smith, 1 run, his fourth single of the innings",
    "",
  ]) {
    assert.equal(isHighStakesBall(text), false, text);
  }
});

test("a provider is only called dead once it has been asked enough times", async () => {
  resetProviderHealth();
  const original = globalThis.fetch;
  process.env.GROQ_API_KEY = "k";
  process.env.CEREBRAS_API_KEY = "k";
  process.env.SAMBANOVA_API_KEY = "k";
  // groq answers, sambanova is never reached, cerebras is unreachable — only groq accumulates.
  globalThis.fetch = async () =>
    new Response(JSON.stringify({ choices: [{ message: { content: "18.5: a line." } }] }), { status: 200 });
  try {
    for (let i = 0; i < 10; i++) await generateVerifiedLine("p", {});
    assert.deepEqual(deadProviders(), [], "a provider that answers 200 is never dead");
  } finally {
    globalThis.fetch = original;
  }
});

test("a provider that 429s on every call is reported once, then not again", async () => {
  resetProviderHealth();
  const original = globalThis.fetch;
  process.env.GROQ_API_KEY = "k";
  delete process.env.CEREBRAS_API_KEY;
  delete process.env.SAMBANOVA_API_KEY;
  // Three of the four live rungs read this one variable. Leaving it set makes "groq only" a lie on
  // any machine that has a Gemini key, and the test would then measure the wrong chain.
  delete process.env.GEMINI_API_KEY;
  globalThis.fetch = async () => new Response("Rate limit exceeded", { status: 429 });
  try {
    // Four calls is under the threshold — a burst must not be mistaken for a dead account.
    for (let i = 0; i < 4; i++) await generateVerifiedLine("p", {});
    assert.deepEqual(deadProviders(), [], "4 calls is not yet evidence");

    await generateVerifiedLine("p", {});
    const dead = deadProviders();
    assert.equal(dead.length, 1);
    assert.equal(dead[0].provider, "groq");
    assert.equal(dead[0].calls, dead[0].failed, "dead means 100% failed, not merely mostly");
    assert.equal(dead[0].status, 429, "the status rides along — 429 is wait, 402 is pay");

    // Self-throttled: a dead provider stays dead, and match-sync runs every minute.
    await generateVerifiedLine("p", {});
    assert.deepEqual(deadProviders(), [], "reported at most once per container");
  } finally {
    globalThis.fetch = original;
  }
});

test("a 402 is latched for the container — the provider is not called a second time", async () => {
  resetProviderHealth();
  const original = globalThis.fetch;
  process.env.GROQ_API_KEY = "k";
  delete process.env.CEREBRAS_API_KEY;
  delete process.env.SAMBANOVA_API_KEY;
  // Three of the four live rungs read this one variable. Leaving it set makes "groq only" a lie on
  // any machine that has a Gemini key, and the test would then measure the wrong chain.
  delete process.env.GEMINI_API_KEY;
  let calls = 0;
  globalThis.fetch = async () => {
    calls += 1;
    return new Response(
      JSON.stringify({ error: { code: "PAYMENT_METHOD_REQUIRED", message: "A payment method is required." } }),
      { status: 402 },
    );
  };
  try {
    const first = [];
    await generateVerifiedLine("p", { attempts: first });
    assert.equal(calls, 1);
    // By entry, not by index: this test is about what groq did, not where groq sits in the ladder.
    assert.match(first.find((a) => a.startsWith("groq:")) ?? "", /^groq: HTTP 402 PAYMENT_METHOD_REQUIRED/);

    // The line that matters: `unavailable` is per-line, so without the container-scoped latch this
    // would be a second doomed round trip, and one per delivery for as long as the account is unpaid.
    const second = [];
    await generateVerifiedLine("p", { attempts: second });
    assert.equal(calls, 1, "a 402 provider is not asked again in this container");
    assert.equal(
      second.find((a) => a.startsWith("groq:")),
      "groq: HTTP 402 (latched for this container)",
      "skipped, but still recorded — a chain that silently loses a rung is the original defect",
    );
  } finally {
    globalThis.fetch = original;
  }
});

test("one 402 is conclusive — it does not wait for the call threshold", async () => {
  resetProviderHealth();
  const original = globalThis.fetch;
  process.env.GROQ_API_KEY = "k";
  delete process.env.CEREBRAS_API_KEY;
  delete process.env.SAMBANOVA_API_KEY;
  // Three of the four live rungs read this one variable. Leaving it set makes "groq only" a lie on
  // any machine that has a Gemini key, and the test would then measure the wrong chain.
  delete process.env.GEMINI_API_KEY;
  globalThis.fetch = async () => new Response(JSON.stringify({ error: { code: "payment_required" } }), { status: 402 });
  try {
    await generateVerifiedLine("p", {});
    const dead = deadProviders();
    assert.equal(dead.length, 1, "a latched provider can never reach minCalls — the ratio must not gate it");
    assert.equal(dead[0].provider, "groq");
    assert.equal(dead[0].status, 402);
  } finally {
    globalThis.fetch = original;
  }
});

test("a 429 is not latched — the provider is asked again on the next line", async () => {
  resetProviderHealth();
  const original = globalThis.fetch;
  process.env.GROQ_API_KEY = "k";
  delete process.env.CEREBRAS_API_KEY;
  delete process.env.SAMBANOVA_API_KEY;
  // Three of the four live rungs read this one variable. Leaving it set makes "groq only" a lie on
  // any machine that has a Gemini key, and the test would then measure the wrong chain.
  delete process.env.GEMINI_API_KEY;
  let calls = 0;
  globalThis.fetch = async () => {
    calls += 1;
    return new Response("Rate limit exceeded", { status: 429 });
  };
  try {
    await generateVerifiedLine("p", {});
    await generateVerifiedLine("p", {});
    assert.equal(calls, 2, "a rate-limited provider recovers by the next ball and must be retried");
  } finally {
    globalThis.fetch = original;
  }
});

test("a provider that sometimes succeeds is busy, not dead", async () => {
  resetProviderHealth();
  const original = globalThis.fetch;
  process.env.GROQ_API_KEY = "k";
  delete process.env.CEREBRAS_API_KEY;
  delete process.env.SAMBANOVA_API_KEY;
  // Three of the four live rungs read this one variable. Leaving it set makes "groq only" a lie on
  // any machine that has a Gemini key, and the test would then measure the wrong chain.
  delete process.env.GEMINI_API_KEY;
  let n = 0;
  globalThis.fetch = async () =>
    ++n === 3
      ? new Response(JSON.stringify({ choices: [{ message: { content: "a line" } }] }), { status: 200 })
      : new Response("Rate limit exceeded", { status: 429 });
  try {
    for (let i = 0; i < 8; i++) await generateVerifiedLine("p", {});
    assert.deepEqual(deadProviders(), [], "one success in the window disqualifies it");
  } finally {
    globalThis.fetch = original;
  }
});

// 3. The verification call's token budget. Every provider in the chain is a reasoning model, and
//    reasoning tokens come out of the same budget as the answer — so a budget sized for the word
//    "PASS" alone is spent before the verdict is ever emitted. That is not a hypothetical: at 16 the
//    verify call returned an empty completion on every ball while the generate call (100) was fine,
//    verification failed closed, and 1,858 deliveries were stored as raw Cricbuzz text (ANDROID-38).
async function withGroqOnly(handler, body) {
  const saved = {
    GROQ_API_KEY: process.env.GROQ_API_KEY,
    CEREBRAS_API_KEY: process.env.CEREBRAS_API_KEY,
    SAMBANOVA_API_KEY: process.env.SAMBANOVA_API_KEY,
    GEMINI_API_KEY: process.env.GEMINI_API_KEY,
  };
  const savedFetch = globalThis.fetch;
  process.env.GROQ_API_KEY = "test-key";
  delete process.env.CEREBRAS_API_KEY;
  delete process.env.SAMBANOVA_API_KEY;
  // Three of the four live rungs read this one variable. Leaving it set makes "groq only" a lie on
  // any machine that has a Gemini key, and the test would then measure the wrong chain.
  delete process.env.GEMINI_API_KEY;

  const bodies = [];
  globalThis.fetch = async (_url, init) => {
    bodies.push(JSON.parse(init.body));
    return handler(bodies.length);
  };
  try {
    return { result: await body(), bodies };
  } finally {
    globalThis.fetch = savedFetch;
    for (const [k, v] of Object.entries(saved)) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
}

const completionOf = (content, finishReason = "stop") =>
  new Response(JSON.stringify({ choices: [{ message: { content }, finish_reason: finishReason }] }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });

test("the verify call asks for more tokens than a bare verdict needs", async () => {
  resetProviderHealth();
  const { result, bodies } = await withGroqOnly(
    (nth) => completionOf(nth === 1 ? "18.5: Bumrah bowls a jaffa." : "PASS"),
    () =>
      generateVerifiedLine("18.5: Bumrah to Root, no run", {
        verify: true,
        context: { snippet1: "Bumrah to Root, no run", ball: "18.5" },
      })
  );

  assert.equal(bodies.length, 2, "one generate, one verify");
  assert.ok(
    bodies[1].max_tokens > 16,
    `verify budget was ${bodies[1].max_tokens} — reasoning tokens consume it before the verdict`
  );
  assert.equal(result.verified, true);
  assert.equal(result.line, "18.5: Bumrah bowls a jaffa.");
});

test("an empty completion records why it was empty", async () => {
  resetProviderHealth();
  const attempts = [];
  await withGroqOnly(
    () => completionOf("", "length"),
    () => generateVerifiedLine("18.5: Bumrah to Root, no run", { attempts })
  );

  // "empty completion" alone cannot distinguish a spent budget from a model that answered nothing,
  // and those need opposite fixes. The other two providers account for their missing keys after it,
  // which is the chain working as intended and not part of this assertion.
  assert.equal(attempts.find((a) => a.startsWith("groq:")), "groq: empty completion (length)");
});
