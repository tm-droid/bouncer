import test from "node:test";
import assert from "node:assert/strict";

import { __setRetry503DelayMs, chainDepth, drainUnknownQuotaIds, generateBatch, generateLine, generateVerifiedLine, probeProviders, resetProviderHealth, verifyBatch, verifyLine } from "../src/llm.js";

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
// The second Gemini project is opt-in per test (`withSecondProject`): its rungs share model ids
// with the first project's, so the model-keyed stub below cannot tell them apart.
delete process.env.GEMINI_API_KEY_2;

const MODEL_TO_PROVIDER = {
  "openai/gpt-oss-20b": "groq",
  "gemini-3.5-flash-lite": "gemini-flash-lite",
  "gemini-3.6-flash": "gemini-3.6-flash",
  "gemini-3-flash-preview": "gemini-3-flash-preview",
  "gemini-3.1-flash-lite-preview": "gemini-3.1-flash-lite-preview",
  "qwen/qwen3.8-27b": "groq-qwen",
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
  assert.equal(sent.groq, 1024, "gpt-oss reasons before it answers — it is floored like the Gemini reasoning rungs");
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

test("a verify sent to groq carries groq's floor, not the verdict's 256", async () => {
  // Production, 2026-09-25: `groq: empty completion (length)` on every verify. gpt-oss spent the
  // 256 on reasoning, every high-stakes ball fell to the 2048-floor Gemini rungs, and those spent
  // their daily buckets by evening (ANDROID-38 / ANDROID-4T).
  const { seen } = await withLadder(
    (name) => (name === "groq" ? completion("PASS") : quota429("x-PerMinute-y")),
    () => verifyLine({ line: "19.2: Rahul takes it.", snippet1: "Bumrah to Maxwell, out Caught by Rahul" })
  );
  assert.equal(seen.find((s) => s.provider === "groq")?.maxTokens, 1024);
});

test("a line nobody could verify is verifier_unavailable, and is not regenerated", async () => {
  // Opposite diagnosis to verification_failed: that one is a hallucination, this one a spent
  // chain. Recording both as verification_failed is how a quota outage read as a quality problem.
  const { result, seen } = await withLadder(
    (name, nth) =>
      name === "gemini-flash-lite" && nth === 1 ? completion("19.2: Rahul takes it.") : quota429("x-PerMinute-y"),
    () =>
      generateVerifiedLine("19.2: Bumrah to Maxwell, out Caught by Rahul", {
        verify: true,
        context: { snippet1: "Bumrah to Maxwell, out Caught by Rahul", ball: "19.2" },
      })
  );
  assert.equal(result.line, null);
  assert.equal(result.reason, "verifier_unavailable");

  const outcome = {};
  await withLadder(() => quota429("x-PerMinute-y"), () => verifyLine({ line: "x." }, { outcome }));
  assert.equal(outcome.available, false);
  // One generation, then one verify walk over every rung — and no second generation after it.
  assert.equal(seen.filter((x) => x.provider === "gemini-flash-lite").length, 2, "flash-lite: the generate, then its verify turn");
  assert.ok(seen.length <= 7, "no second generation is spent on a chain that cannot verify it");
});

test("a per-DAY 429 latches the rung; a per-MINUTE 429 does not", async () => {
  const perDay = "GenerateRequestsPerDayPerProjectPerModel-FreeTier";
  const perMinute = "GenerateRequestsPerMinutePerProjectPerModel-FreeTier";

  const attempts = [];
  const { seen } = await withLadder(
    (name) => {
      if (name === "groq") return quota429(perMinute, "30");
      if (name === "gemini-flash-lite") return quota429(perDay, "500");
      // Busy too, so the walk reaches groq — this rung sits between the two under test.
      if (name === "gemini-3.1-flash-lite-preview") return quota429(perMinute, "15");
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
  // The FIRST rung is the one that must be re-asked; naming it keeps this honest if the order moves.
  const FIRST = "gemini-flash-lite";
  const { result, seen } = await withLadder(
    (name) => (name === FIRST ? quota429("SomeQuotaNobodyPredicted", "7") : completion("19.2: taken.")),
    async () => {
      await generateLine("first ball");
      await generateLine("second ball");
      return { first: drainUnknownQuotaIds(), second: drainUnknownQuotaIds() };
    }
  );

  assert.equal(seen.filter((s) => s.provider === FIRST).length, 2, "an unclassified 429 must not latch the rung");
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
    assert.equal(depth, 2, "one groq key reaches two rungs: the writer and the verify-only checker");
    assert.deepEqual(
      rungs.filter((r) => !r.reachable).map((r) => r.reason),
      [
        "no GEMINI_API_KEY", "no GEMINI_API_KEY_2", "no GEMINI_API_KEY", "no GEMINI_API_KEY_2",
        "no GEMINI_API_KEY", "no GEMINI_API_KEY_2", "no GEMINI_API_KEY", "no GEMINI_API_KEY_2",
      ]
    );

    // Depth 1 is the state that broke verification for six weeks: generation still works, so every
    // other signal looks healthy, while `generateVerifiedLine`'s retry has nobody to fall back to.
    process.env.GEMINI_API_KEY = "k";
    assert.equal(chainDepth().depth, 6, "both keys present restores the full ladder");

    // The per-line Set narrows it further without being module state.
    assert.equal(chainDepth(new Set(["groq", "gemini-3.6-flash"])).depth, 4);
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

test("the verify-only rung never writes, and is asked FIRST for a verdict", async () => {
  // qwen3.8-27b invented "Babar Zaman" and turned a catch into a run-out when asked to write
  // (probed 2026-09-25), and was 8/8 as a checker. It must stay on the right side of that line.
  const { seen: writing } = await withLadder(
    () => completion("[1|1.1]: Bumrah beats the edge."),
    () => generateBatch([{ key: "1|1.1", text: "1.1 Bumrah to Root, no run" }])
  );
  assert.ok(!writing.some((s) => s.provider === "groq-qwen"), "a verify-only rung wrote a line");

  const { seen: checking } = await withLadder(
    () => completion("[#1]: PASS"),
    () => verifyBatch([{ key: "1|1.1", source: "1.1 Bumrah to Root, no run", line: "Bumrah beats the edge." }])
  );
  assert.equal(checking[0].provider, "groq-qwen");
});

test("verifyBatch matches verdicts by key, and anything it cannot read is left unverified", async () => {
  const items = ["a|1", "b|2", "c|3", "d|4"].map((key) => ({ key, source: `src ${key}`, line: `line ${key}` }));
  const { result } = await withLadder(
    // Reordered, one bolded, one unknown key, one duplicated, one missing (d|4).
    // Tags, not keys: #1..#4 are a|1..d|4. Reordered, bolded, an unknown tag, a duplicate, #4 missing.
    () => completion("[#2]: FAIL\n[#1]: **PASS**\n[#9]: PASS\n[#3]: PASS\n[#3]: PASS"),
    () => verifyBatch(items)
  );
  assert.equal(result.available, true);
  assert.equal(result.verdicts.get("a|1"), true);
  assert.equal(result.verdicts.get("b|2"), false);
  assert.equal(result.verdicts.get("c|3"), false, "a key answered twice is trusted neither time");
  assert.equal(result.verdicts.has("d|4"), false, "an unanswered key is absent, so the caller fails closed");
  assert.equal(result.verdicts.size, 3, "an invented tag is dropped");
});

test("verifyBatch with no rung answering reports unavailable", async () => {
  const { result } = await withLadder(
    () => quota429("x-PerMinute-y"),
    () => verifyBatch([{ key: "a|1", source: "s", line: "l" }])
  );
  assert.equal(result.available, false);
  assert.equal(result.verdicts.size, 0);
});

test("a groq tokens-per-day 429 pauses that rung instead of re-asking it every call", async () => {
  // 399 re-asks of an exhausted gpt-oss-20b on 2026-09-25: groq names its window in prose, not in
  // a QuotaFailure, so the Gemini classifier never latched it.
  const groqTpd = () =>
    new Response(
      JSON.stringify({ error: { message: "Rate limit reached for model `openai/gpt-oss-20b` in organization `org_x` service tier `on_demand` on tokens per day (TPD): Limit 200000, Used 199800, Requested 800.", type: "tokens", code: "rate_limit_exceeded" } }),
      { status: 429, headers: { "Content-Type": "application/json" } }
    );
  const { seen } = await withLadder(
    (name) => (name === "groq" ? groqTpd() : quota429("x-PerMinute-y")),
    async () => {
      await generateLine("first ball");
      await generateLine("second ball");
    }
  );
  assert.equal(seen.filter((s) => s.provider === "groq").length, 1, "asked once, then paused");
});

test("a 503 is retried once on the same rung; a second 503 moves on", async () => {
  const prev = __setRetry503DelayMs(0);
  const overloaded = () => new Response(JSON.stringify([{ error: { code: 503, message: "This model is currently experiencing high demand.", status: "UNAVAILABLE" } }]), { status: 503 });
  try {
    // Busy once, then fine: the same rung answers, nothing further down is asked.
    const once = await withLadder(
      (name, nth) => (name === "gemini-flash-lite" && nth === 1 ? overloaded() : completion("19.2: Rahul takes it.")),
      () => generateLine("19.2: Bumrah to Maxwell, out Caught by Rahul")
    );
    assert.equal(once.result, "19.2: Rahul takes it.");
    assert.deepEqual(once.seen.map((s) => s.provider), ["gemini-flash-lite", "gemini-flash-lite"]);

    // Busy twice: exactly one retry, then the next rung.
    const twice = await withLadder(
      (name) => (name === "gemini-flash-lite" ? overloaded() : completion("19.2: Rahul takes it.")),
      () => generateLine("19.2: Bumrah to Maxwell, out Caught by Rahul")
    );
    assert.equal(twice.seen.filter((s) => s.provider === "gemini-flash-lite").length, 2);
    assert.equal(twice.seen[2].provider, "gemini-3.1-flash-lite-preview");
  } finally {
    __setRetry503DelayMs(prev);
  }
});

test("the checker is sent short tags, so a long key cannot eat its reply budget", async () => {
  // On 2026-09-25 the budget ran out part-way down the list once keys carried a timestamp, and the
  // first five balls of an over came back unanswered.
  const items = Array.from({ length: 12 }, (_, n) => ({ key: `142706|18.${n}|175881729${n}000`, source: `src ${n}`, line: `line ${n}` }));
  const { result, seen } = await withLadder(
    () => completion(items.map((_, n) => `[#${n + 1}]: PASS`).join("\n")),
    () => verifyBatch(items)
  );
  assert.equal(result.verdicts.size, 12);
  assert.equal(result.verdicts.get("142706|18.0|1758817290000"), true);
  assert.ok(seen[0].maxTokens >= 16 * 12, "the budget covers every verdict line");
});

// ── Second Gemini project (2026-10-03) ───────────────────────────────────────────────────────────
//
// The free quota is per PROJECT per model, so a key from a different project is four more buckets
// on the same four model ids. What has to hold is that the two projects fail independently: a
// latch that leaked across would turn 1,040 extra calls a day into none, with every rung's own
// skip reason looking ordinary.

/** Like `withLadder`, with both projects keyed. The stub tells them apart by the bearer token. */
async function withSecondProject(handler, body) {
  const keys = [...PROVIDER_KEYS, "GEMINI_API_KEY_2"];
  const saved = keys.map((k) => [k, process.env[k]]);
  const savedFetch = globalThis.fetch;
  for (const k of keys) process.env[k] = "test-key";
  process.env.GEMINI_API_KEY_2 = "second-project-key";
  resetProviderHealth();

  const seen = [];
  globalThis.fetch = async (_url, init) => {
    const sent = JSON.parse(init?.body ?? "{}");
    const base = MODEL_TO_PROVIDER[sent.model];
    if (!base) throw new Error(`unexpected model ${sent.model}`);
    const second = init.headers.Authorization === "Bearer second-project-key";
    const name = second ? `${base}-2` : base;
    seen.push(name);
    return handler(name);
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

test("each second-project rung sits directly behind its twin", async () => {
  const { seen } = await withSecondProject(
    () => quota429("x-PerMinute-y"),
    () => generateLine("19.2: Bumrah to Maxwell, no run")
  );
  assert.deepEqual(seen, [
    "gemini-flash-lite", "gemini-flash-lite-2",
    "gemini-3.1-flash-lite-preview", "gemini-3.1-flash-lite-preview-2",
    "groq",
    "gemini-3.6-flash", "gemini-3.6-flash-2",
    "gemini-3-flash-preview", "gemini-3-flash-preview-2",
  ]);
});

test("a spent per-day bucket on one project leaves the same model on the other answering", async () => {
  const perDay = "GenerateRequestsPerDayPerProjectPerModel-FreeTier";
  const handler = (name) =>
    name === "gemini-flash-lite" ? quota429(perDay, "500") : completion("19.2: Maxwell defends.");
  const { result, seen } = await withSecondProject(handler, async () => {
    const first = await generateLine("19.2: Bumrah to Maxwell, no run");
    const second = await generateLine("19.3: Bumrah to Maxwell, no run");
    return [first, second];
  });
  assert.deepEqual(result, ["19.2: Maxwell defends.", "19.2: Maxwell defends."]);
  // The first project's rung is asked once, latches, and is skipped; its twin answers both lines.
  assert.deepEqual(seen, ["gemini-flash-lite", "gemini-flash-lite-2", "gemini-flash-lite-2"]);
});

test("a 402 on one project latches that project alone", async () => {
  const handler = (name) => {
    if (name.endsWith("-2")) return completion("19.2: Maxwell defends.");
    if (name.startsWith("gemini")) return new Response(JSON.stringify({ error: { message: "payment required" } }), { status: 402 });
    return quota429("x-PerMinute-y");
  };
  const { result, seen } = await withSecondProject(handler, () => generateLine("19.2: Bumrah to Maxwell, no run"));
  assert.equal(result, "19.2: Maxwell defends.");
  assert.deepEqual(seen, ["gemini-flash-lite", "gemini-flash-lite-2"], "the other project is still asked, and answers");
});
