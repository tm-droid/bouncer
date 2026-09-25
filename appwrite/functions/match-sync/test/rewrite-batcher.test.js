import test from "node:test";
import assert from "node:assert/strict";

import { __queueRewrite as queueRewrite, __setBatchFn, __setVerifyBatchFn, resetRewriteBatcher } from "../src/main.js";

// The batcher is what makes "rewrite every ball" affordable, and it is also the one piece that can
// hang a whole run: `handleRun` awaits every match, each match awaits its queued deliveries, and a
// promise that is never resolved stalls the function until its 120s timeout. So the property under
// test is not just "it batches" — it is "every queued item is resolved, on every path".

const KEYS = ["GROQ_API_KEY", "GEMINI_API_KEY", "CEREBRAS_API_KEY", "SAMBANOVA_API_KEY"];

const reply = (text) =>
  new Response(JSON.stringify({ choices: [{ message: { content: text }, finish_reason: "stop" }] }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });

// The fact-check runs inside the batch now; these tests are about batching, so it passes everything
// unless a test swaps in its own checker.
const passAll = async (items) => ({ verdicts: new Map(items.map((i) => [i.key, true])), available: true });

async function withStub(handler, body, checker = passAll) {
  const saved = KEYS.map((k) => [k, process.env[k]]);
  const savedFetch = globalThis.fetch;
  const prevChecker = __setVerifyBatchFn(checker);
  for (const k of KEYS) process.env[k] = "test-key";
  const calls = [];
  globalThis.fetch = async (_url, init) => {
    const sent = JSON.parse(init?.body ?? "{}");
    calls.push(sent);
    return handler(sent, calls.length);
  };
  try {
    return { result: await body(), calls };
  } finally {
    globalThis.fetch = savedFetch;
    __setVerifyBatchFn(prevChecker);
    resetRewriteBatcher();
    for (const [k, v] of saved) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
}

// Echo back one line per key the prompt asked about, so a batch of N resolves N lines.
const echo = (sent) => {
  const keys = [...sent.messages[1].content.matchAll(/^\[([^\]]+)\]:/gm)].map((m) => m[1]);
  return reply(keys.map((k) => `[${k}]: rewritten ${k}.`).join("\n"));
};

test("deliveries queued concurrently become ONE provider call", async () => {
  // This is the entire saving: ~7 deliveries arrive per run across the live matches, and they must
  // leave as one call, not seven. They are queued from separate concurrent syncMatch calls, which
  // is exactly what Promise.all models here.
  const { result, calls } = await withStub(echo, () =>
    Promise.all(
      ["154682|10.5", "161802|7.1", "142662|56.6", "143251|105.4"].map((key) =>
        queueRewrite({ key, text: `raw text for ${key}` })
      )
    )
  );
  assert.equal(calls.length, 1, "four deliveries, one call");
  assert.deepEqual(result.map((r) => r.line), [
    "rewritten 154682|10.5.",
    "rewritten 161802|7.1.",
    "rewritten 142662|56.6.",
    "rewritten 143251|105.4.",
  ]);
});

test("each delivery gets ITS OWN line back, not the batch's first", async () => {
  const { result } = await withStub(echo, () =>
    Promise.all([
      queueRewrite({ key: "A|1.1", text: "a" }),
      queueRewrite({ key: "B|2.2", text: "b" }),
    ])
  );
  assert.equal(result[0].line, "rewritten A|1.1.");
  assert.equal(result[1].line, "rewritten B|2.2.");
});

test("a delivery the model skipped resolves to null rather than hanging", async () => {
  // Every queued promise must settle. A skipped key resolving to null is a raw row; a skipped key
  // never resolving is a stalled run.
  const { result, calls } = await withStub(
    (sent) => {
      const keys = [...sent.messages[1].content.matchAll(/^\[([^\]]+)\]:/gm)].map((m) => m[1]);
      return reply(`[${keys[0]}]: only the first.`);
    },
    () => Promise.all([queueRewrite({ key: "A|1.1", text: "a" }), queueRewrite({ key: "B|2.2", text: "b" })])
  );
  assert.equal(calls.length, 1);
  assert.equal(result[0].line, "only the first.");
  assert.equal(result[1].line, null, "the skipped delivery resolves null, and the run continues");
});

test("a provider failure resolves every queued delivery", async () => {
  const { result } = await withStub(
    () => new Response("boom", { status: 500 }),
    () => Promise.all([queueRewrite({ key: "A|1.1", text: "a" }), queueRewrite({ key: "B|2.2", text: "b" })])
  );
  assert.deepEqual(result.map((r) => r.line), [null, null]);
});

test("more deliveries than fit one batch are split, and all still resolve", async () => {
  // A Cricbuzz backlog republish arrives as one burst — the 13-minute hole on 2026-09-12 is the
  // recorded case. It must not become one enormous prompt, and nothing may be dropped on the floor.
  const keys = Array.from({ length: 15 }, (_, i) => `M|${i}.1`);
  const { result, calls } = await withStub(echo, () =>
    Promise.all(keys.map((key) => queueRewrite({ key, text: `raw ${key}` })))
  );
  assert.ok(calls.length >= 2, `15 deliveries must span more than one call, got ${calls.length}`);
  assert.equal(result.filter((r) => r.line).length, 15, "every delivery still comes back");
});

test("resetRewriteBatcher settles anything still queued", async () => {
  // The safety net. Nothing may survive a run boundary holding an unresolved promise.
  const saved = KEYS.map((k) => [k, process.env[k]]);
  for (const k of KEYS) delete process.env[k];
  try {
    const p = queueRewrite({ key: "A|1.1", text: "a" });
    resetRewriteBatcher();
    assert.deepEqual(await p, { line: null, attempts: [] });
  } finally {
    for (const [k, v] of saved) if (v !== undefined) process.env[k] = v;
  }
});

test("a batch generator that THROWS still resolves every queued delivery", async () => {
  // The hang path. generateBatch returns an empty Map for provider failures rather than throwing,
  // so no fetch stub reaches the catch — and an unreachable catch is precisely where a stalled run
  // would hide if that behaviour ever changed. `handleRun` awaits every match and every match
  // awaits its deliveries, so one unresolved promise costs the whole run its 120s timeout.
  const restore = __setBatchFn(async () => {
    throw new Error("batch exploded");
  });
  try {
    const settled = await Promise.all([
      queueRewrite({ key: "A|1.1", text: "a", error: () => {} }),
      queueRewrite({ key: "B|2.2", text: "b", error: () => {} }),
    ]);
    assert.deepEqual(settled.map((r) => r.line), [null, null], "both resolve, neither hangs");
  } finally {
    __setBatchFn(restore);
    resetRewriteBatcher();
  }
});

test("a batched row records the rung that answered, not a placeholder", async () => {
  // The provider column exists so the ladder's real mix is a GROUP BY rather than a guess. A batch
  // that tagged every row "batch" would answer the question with the implementation detail.
  const { result } = await withStub(echo, () =>
    Promise.all([queueRewrite({ key: "A|1.1", text: "a" }), queueRewrite({ key: "B|2.2", text: "b" })])
  );
  for (const r of result) {
    assert.equal(r.provider, "gemini-flash-lite", "the rung that actually answered");
  }
});

test("every rewritten line is fact-checked against its OWN raw text, and a FAIL stores raw", async () => {
  const seen = [];
  const checker = async (items) => {
    seen.push(...items);
    return { verdicts: new Map(items.map((i) => [i.key, i.key !== "2|1.2"])), available: true };
  };
  const { result } = await withStub(
    echo,
    () => Promise.all(["1|1.1", "2|1.2"].map((key) => queueRewrite({ key, text: `raw ${key}` }))),
    checker
  );
  // Every ball, not just wickets and boundaries: both lines went to the checker.
  assert.deepEqual(seen.map((i) => [i.key, i.source]), [["1|1.1", "raw 1|1.1"], ["2|1.2", "raw 2|1.2"]]);
  assert.equal(result[0].line, "rewritten 1|1.1.");
  assert.equal(result[1].line, null, "a FAIL is stored as raw text, never as the rejected line");
  assert.equal(result[1].reason, "verification_failed");
});

test("a line the checker did not answer is unverified, and fails closed", async () => {
  const { result } = await withStub(
    echo,
    () => Promise.all(["1|1.1", "2|1.2"].map((key) => queueRewrite({ key, text: `raw ${key}` }))),
    async () => ({ verdicts: new Map([["1|1.1", true]]), available: true })
  );
  assert.equal(result[0].line, "rewritten 1|1.1.");
  assert.equal(result[1].line, null);
  assert.equal(result[1].reason, "verifier_unavailable");
});

test("no checker at all means every line is stored raw, and says why", async () => {
  const { result } = await withStub(
    echo,
    () => Promise.all(["1|1.1"].map((key) => queueRewrite({ key, text: `raw ${key}` }))),
    async () => ({ verdicts: new Map(), available: false })
  );
  assert.equal(result[0].line, null);
  assert.equal(result[0].reason, "verifier_unavailable");
});

test("a checker that throws still resolves every queued delivery", async () => {
  const { result } = await withStub(
    echo,
    () => Promise.all(["1|1.1", "2|1.2"].map((key) => queueRewrite({ key, text: `raw ${key}` }))),
    async () => { throw new Error("checker blew up"); }
  );
  assert.deepEqual(result.map((r) => r.line), [null, null]);
});
