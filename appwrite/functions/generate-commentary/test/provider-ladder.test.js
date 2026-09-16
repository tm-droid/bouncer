import test from "node:test";
import assert from "node:assert/strict";

import { chainDepth, drainUnknownQuotaIds, generateLine, probeProviders, resetProviderHealth, verifyLine } from "../src/llm.js";

// The ladder became four rungs on two accounts on 2026-09-16, when cerebras and sambanova were
// dropped for answering 402 to every call and three Gemini model ids took their place. Gemini bills
// `GenerateRequestsPerMinutePerProjectPerModel-FreeTier` — per project PER MODEL — so three ids on
// one key are three allowances, and the rungs are distinguishable only by the `model` in the body.
//
// That shape brings three failure modes the chain had never had to model, and each one is silent:
// a reasoning rung that needs a bigger token budget than the caller asked for, a completion severed
// mid-sentence by that budget, and a 429 that means "come back tomorrow" rather than "come back in
// twenty seconds". All three pinned here.

const PROVIDER_KEYS = ["GROQ_API_KEY", "GEMINI_API_KEY", "CEREBRAS_API_KEY", "SAMBANOVA_API_KEY"];

const MODEL_TO_PROVIDER = {
  "openai/gpt-oss-20b": "groq",
  "gemini-3.5-flash-lite": "gemini-flash-lite",
  "gemini-3.6-flash": "gemini-3.6-flash",
  "gemini-3-flash-preview": "gemini-3-flash-preview",
};

const completion = (text, finish = "stop") =>
  new Response(JSON.stringify({ choices: [{ message: { content: text }, finish_reason: finish }] }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });

/**
 * A Gemini 429, in the exact shape the live endpoint returns: the error object wrapped in a
 * one-element ARRAY, with the quota window named in `details[].violations[].quotaId`. Both facts
 * were measured against the live key on 2026-09-16 and both matter — an unwrapped body parses to
 * nothing useful, and the quotaId is the only thing separating a 20-second wait from a whole day.
 */
const quota429 = (quotaId, quotaValue = "15") =>
  new Response(
    JSON.stringify([
      {
        error: {
          code: 429,
          message: "You exceeded your current quota, please check your plan and billing details.",
          status: "RESOURCE_EXHAUSTED",
          details: [
            { "@type": "type.googleapis.com/google.rpc.QuotaFailure", violations: [{ quotaId, quotaValue }] },
          ],
        },
      },
    ]),
    { status: 429, headers: { "Content-Type": "application/json" } }
  );

/** Run [body] with every key present and fetch stubbed. Returns the recorded request bodies. */
async function withLadder(handler, body) {
  const saved = PROVIDER_KEYS.map((k) => [k, process.env[k]]);
  const savedFetch = globalThis.fetch;
  for (const k of PROVIDER_KEYS) process.env[k] = "test-key";
  resetProviderHealth();

  const seen = [];
  globalThis.fetch = async (_url, init) => {
    const sent = JSON.parse(init?.body ?? "{}");
    const name = MODEL_TO_PROVIDER[sent.model];
    if (!name) throw new Error(`unexpected model ${sent.model}`);
    seen.push({ provider: name, maxTokens: sent.max_tokens, extra: sent.reasoning_effort });
    return handler(name, seen.filter((s) => s.provider === name).length);
  };

  try {
    return { result: await body(), seen };
  } finally {
    globalThis.fetch = savedFetch;
    resetProviderHealth();
    for (const [k, v] of saved) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
}

test("a reasoning rung is sent its own token floor, not the caller's budget", async () => {
  // gemini-3.6-flash at max_tokens 512 returned completion_tokens 18 against total_tokens 717 —
  // the budget went on reasoning before a word of prose. Same trap as gpt-oss, one rung down.
  const { seen } = await withLadder(
    (name) => (name === "gemini-3-flash-preview" ? completion("19.2: Rahul takes it.") : quota429("x-PerMinute-y")),
    () => generateLine("19.2: Bumrah to Maxwell, out Caught", { maxTokens: 100 })
  );

  const sent = Object.fromEntries(seen.map((s) => [s.provider, s.maxTokens]));
  assert.equal(sent.groq, 100, "groq has no floor — the caller's budget stands");
  assert.equal(sent["gemini-flash-lite"], 100, "flash-lite answered fine at 100 and must not be inflated");
  assert.equal(sent["gemini-3.6-flash"], 2048, "the reasoning rungs need room to think first");
  assert.equal(sent["gemini-3-flash-preview"], 2048);
});

test("a completion severed mid-sentence is a failure, not a short line", async () => {
  const { result, seen } = await withLadder(
    (name) =>
      name === "groq"
        ? completion("19.2: Maxwell's ambitious ramp off Bumrah produces only a feather of", "length")
        : completion("19.2: Rahul dives forward and takes it."),
    () => generateLine("19.2: Bumrah to Maxwell, out Caught")
  );

  assert.equal(result, "19.2: Rahul dives forward and takes it.", "the truncated line must not be returned");
  assert.ok(seen.some((s) => s.provider === "gemini-flash-lite"), "the chain fell through to the next rung");
});

test("a line that ends on punctuation is kept even when the budget ran out", async () => {
  // `length` alone is not the defect — a model can finish its sentence on its last allowed token.
  // Rejecting on the status would throw away good lines and make the guard a regression.
  const { result } = await withLadder(
    () => completion("19.2: Rahul dives forward and takes it.", "length"),
    () => generateLine("19.2: Bumrah to Maxwell, out Caught")
  );
  assert.equal(result, "19.2: Rahul dives forward and takes it.");
});

test("the truncation guard does not eat a verification verdict", async () => {
  // The verdict is the bare word PASS, by instruction — no terminal punctuation. A guard applied
  // here would fail every fact-check closed, which is the exact outage (ANDROID-38) this chain
  // already had once.
  const { result } = await withLadder(
    () => completion("PASS", "length"),
    () => verifyLine({ line: "19.2: Rahul takes it.", snippet1: "Bumrah to Maxwell, out Caught by Rahul" })
  );
  assert.equal(result, true, "PASS must still be read as a pass");
});

test("a per-DAY 429 latches the rung; a per-MINUTE 429 does not", async () => {
  const perDay = "GenerateRequestsPerDayPerProjectPerModel-FreeTier";
  const perMinute = "GenerateRequestsPerMinutePerProjectPerModel-FreeTier";

  const attempts = [];
  const { seen } = await withLadder(
    (name) => {
      if (name === "groq") return quota429(perMinute, "30");
      if (name === "gemini-flash-lite") return quota429(perDay, "500");
      return completion("19.2: Rahul takes it.");
    },
    async () => {
      await generateLine("first ball", { attempts });
      await generateLine("second ball", { attempts });
    }
  );

  const countFor = (p) => seen.filter((s) => s.provider === p).length;
  assert.equal(countFor("groq"), 2, "a per-minute 429 clears by the next ball — groq is asked again");
  assert.equal(countFor("gemini-flash-lite"), 1, "a per-day 429 holds until the reset — asked once, then skipped");

  assert.ok(
    attempts.some((a) => a.startsWith("gemini-flash-lite: daily quota spent")),
    "the skip is recorded, because a chain that quietly loses a rung is the original defect"
  );
  assert.ok(
    attempts.some((a) => a.includes(perDay)),
    "and the first refusal carries the quota id, so the window is diagnosable from Sentry alone"
  );
});

test("an unrecognised quota window is reported rather than latched", async () => {
  // The per-day id is INFERRED from the per-minute one that was measured. If Google spells it
  // differently, latching on a guess would switch off a working rung; not reporting it would leave
  // the guess wrong forever. So: never latch, always surface.
  // Drained INSIDE the run: withLadder resets module state on the way out, as every test here
  // needs it to, so a drain after the fact would read an empty set and pass for the wrong reason.
  const { result, seen } = await withLadder(
    (name) => (name === "groq" ? quota429("SomeQuotaNobodyPredicted", "7") : completion("19.2: taken.")),
    async () => {
      await generateLine("first ball");
      await generateLine("second ball");
      return { first: drainUnknownQuotaIds(), second: drainUnknownQuotaIds() };
    }
  );

  assert.equal(seen.filter((s) => s.provider === "groq").length, 2, "an unclassified 429 must not latch the rung");
  assert.deepEqual(result.first, ["SomeQuotaNobodyPredicted"], "but it must be reported");
  assert.deepEqual(result.second, [], "and drained, so the caller reports it once");
});

test("a 402 latches the whole account, not just the model that saw it", async () => {
  // Three Gemini rungs share one key, one project and one balance. A 402 from any of them is a
  // statement about all three; latching only the rung that saw it would re-discover the same dead
  // account twice more on every line.
  const attempts = [];
  const { seen } = await withLadder(
    (name) =>
      name === "gemini-flash-lite"
        ? new Response(JSON.stringify({ error: { code: 402, message: "quota" } }), { status: 402 })
        : quota429("x-PerMinute-y"),
    () => generateLine("19.2: Bumrah to Maxwell", { attempts })
  );

  assert.equal(seen.filter((s) => s.provider.startsWith("gemini")).length, 1, "only the first Gemini rung is called");
  for (const rung of ["gemini-3.6-flash", "gemini-3-flash-preview"]) {
    assert.ok(
      attempts.some((a) => a === `${rung}: HTTP 402 (gemini account latched for this container)`),
      `${rung} must record that its account, not it, is the thing that is blocked`
    );
  }
});

test("chainDepth counts what could actually answer, and says why the rest cannot", async () => {
  const saved = PROVIDER_KEYS.map((k) => [k, process.env[k]]);
  resetProviderHealth();
  try {
    process.env.GROQ_API_KEY = "k";
    delete process.env.GEMINI_API_KEY;

    const { depth, rungs } = chainDepth();
    assert.equal(depth, 1, "one key configured means one reachable rung");
    assert.deepEqual(
      rungs.filter((r) => !r.reachable).map((r) => r.reason),
      ["no GEMINI_API_KEY", "no GEMINI_API_KEY", "no GEMINI_API_KEY"]
    );

    // Depth 1 is the state that broke verification for six weeks: generation still works, so every
    // other signal looks healthy, while `generateVerifiedLine`'s retry has nobody to fall back to.
    process.env.GEMINI_API_KEY = "k";
    assert.equal(chainDepth().depth, 4, "both keys present restores the full ladder");

    // The per-line Set narrows it further without being module state.
    assert.equal(chainDepth(new Set(["groq", "gemini-3.6-flash"])).depth, 2);
  } finally {
    resetProviderHealth();
    for (const [k, v] of saved) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
});

test("the probe recognises a Gemini model id despite its models/ prefix", async () => {
  // `configuredIsListed` is the whole point of probeProviders: it is how "my model id is wrong" is
  // told apart from "my key is dead". Gemini returns `models/gemini-3.5-flash-lite` where every
  // other provider returns a bare id, so a raw includes() reported false for all three Gemini rungs
  // while they were answering 200 — a permanent false alarm sitting on the one diagnostic signal.
  const saved = PROVIDER_KEYS.map((k) => [k, process.env[k]]);
  const savedFetch = globalThis.fetch;
  for (const k of PROVIDER_KEYS) process.env[k] = "test-key";

  globalThis.fetch = async (url) =>
    String(url).includes("generativelanguage")
      ? new Response(
          JSON.stringify({
            data: [
              { id: "models/gemini-3.5-flash-lite" },
              { id: "models/gemini-3.6-flash" },
              { id: "models/gemini-3-flash-preview" },
            ],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      : new Response(JSON.stringify({ data: [{ id: "openai/gpt-oss-20b" }] }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });

  try {
    const byName = Object.fromEntries((await probeProviders()).map((p) => [p.provider, p]));
    for (const rung of ["gemini-flash-lite", "gemini-3.6-flash", "gemini-3-flash-preview"]) {
      assert.equal(byName[rung].configuredIsListed, true, `${rung} is listed and must be reported as listed`);
    }
    assert.equal(byName.groq.configuredIsListed, true, "a bare id must keep working");
    assert.ok(
      byName["gemini-flash-lite"].models.includes("gemini-3.5-flash-lite"),
      "the reported list is normalised too, so it can be compared against PROVIDERS by eye"
    );
  } finally {
    globalThis.fetch = savedFetch;
    for (const [k, v] of saved) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
});
