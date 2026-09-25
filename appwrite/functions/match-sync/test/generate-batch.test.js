import test from "node:test";
import assert from "node:assert/strict";

import {
  BATCH_MAX_ITEMS,
  BATCH_SYSTEM_INSTRUCTION,
  SYSTEM_INSTRUCTION,
  generateBatch,
  resetProviderHealth,
} from "../src/llm.js";

// Batching exists because the arithmetic does not work otherwise: 6,672 deliveries a day against a
// ~2,500-call free ladder (assumed, never measured — the real one is smaller) is 31% coverage at one call per ball, which is why the commentary tab was
// mostly raw Cricbuzz text. One call per ~7 deliveries makes every ball affordable.
//
// It batches across MATCHES, not across time. Filling a batch by waiting would delay the ball;
// breadth costs nothing, because ~7 deliveries arrive per run across the live matches anyway.
//
// The whole safety of it rests on one property: a line is matched to its delivery BY KEY. Everything
// below is that property under the ways a model actually misbehaves.

const KEYS = ["GROQ_API_KEY", "GEMINI_API_KEY", "CEREBRAS_API_KEY", "SAMBANOVA_API_KEY"];
const ITEMS = [
  { key: "154682|10.5", text: "Vitel Lawes to Chris Green, 1 run, to long-on" },
  { key: "161802|7.1", text: "Suzie Bates to Nadine de Klerk, out Bowled!!" },
  { key: "142662|56.6", text: "Jake Ball to Dom Bess, 1 run" },
];

const reply = (text) =>
  new Response(JSON.stringify({ choices: [{ message: { content: text }, finish_reason: "stop" }] }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });

async function withStub(body, handler) {
  const saved = KEYS.map((k) => [k, process.env[k]]);
  const savedFetch = globalThis.fetch;
  for (const k of KEYS) process.env[k] = "test-key";
  resetProviderHealth();
  const sent = [];
  globalThis.fetch = async (_url, init) => {
    sent.push(JSON.parse(init?.body ?? "{}"));
    return handler(sent.length);
  };
  try {
    return { result: await body(), sent };
  } finally {
    globalThis.fetch = savedFetch;
    resetProviderHealth();
    for (const [k, v] of saved) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
}

test("the batch prompt is the single-line prompt with the batch rules swapped in", () => {
  // Built by .replace() off SYSTEM_INSTRUCTION so the hallucination and style rules cannot drift
  // between the two paths. A .replace() whose target string moves silently does NOTHING, which
  // would leave the batch path running the single-line prompt and asking for one line — so the
  // replacements are asserted, not assumed.
  assert.notEqual(BATCH_SYSTEM_INSTRUCTION, SYSTEM_INSTRUCTION);
  assert.ok(BATCH_SYSTEM_INSTRUCTION.includes("DIFFERENT, UNRELATED matches"));
  assert.ok(BATCH_SYSTEM_INSTRUCTION.includes("[key] tag exactly as given"));
  assert.ok(BATCH_SYSTEM_INSTRUCTION.includes("One sentence per delivery."));
  assert.ok(BATCH_SYSTEM_INSTRUCTION.includes("HALLUCINATION RULES"), "the rules that matter survive");
  assert.ok(!BATCH_SYSTEM_INSTRUCTION.includes("Write exactly one vivid"), "no single-line leftovers");
  assert.ok(!BATCH_SYSTEM_INSTRUCTION.includes("One sentence only."));
});

test("a well-formed batch maps every key to its own line", async () => {
  const { result, sent } = await withStub(
    () => generateBatch(ITEMS),
    () => reply(ITEMS.map((i, n) => `[${i.key}]: line number ${n}.`).join("\n"))
  );
  assert.equal(result.size, 3);
  assert.equal(result.get("154682|10.5"), "line number 0.");
  assert.equal(result.get("161802|7.1"), "line number 1.");
  assert.equal(result.get("142662|56.6"), "line number 2.");
  assert.equal(sent.length, 1, "three deliveries, ONE provider call — that is the entire point");
});

test("a dropped line leaves its delivery absent, and does not shift the others", async () => {
  // The failure that matters. Position-based matching would give delivery 2 the line written for
  // delivery 3, and a confidently wrong commentary line is far worse than a raw one.
  const { result } = await withStub(
    () => generateBatch(ITEMS),
    () => reply([`[${ITEMS[0].key}]: first.`, `[${ITEMS[2].key}]: third.`].join("\n"))
  );
  assert.equal(result.size, 2);
  assert.equal(result.get("154682|10.5"), "first.");
  assert.equal(result.get("142662|56.6"), "third.", "the third line keeps its own key");
  assert.ok(!result.has("161802|7.1"), "the skipped delivery must fall back to raw text");
});

test("reordered output is still matched correctly", async () => {
  const { result } = await withStub(
    () => generateBatch(ITEMS),
    () => reply([`[${ITEMS[2].key}]: third.`, `[${ITEMS[0].key}]: first.`, `[${ITEMS[1].key}]: second.`].join("\n"))
  );
  assert.equal(result.get("154682|10.5"), "first.");
  assert.equal(result.get("161802|7.1"), "second.");
  assert.equal(result.get("142662|56.6"), "third.");
});

test("invented keys, duplicates and junk lines are dropped, not guessed at", async () => {
  const { result } = await withStub(
    () => generateBatch(ITEMS),
    () =>
      reply(
        [
          "Here are the rewrites:",                 // a header the prompt forbade
          `[${ITEMS[0].key}]: first.`,
          `[${ITEMS[0].key}]: a second go at the same ball.`, // duplicate key
          "[999999|1.1]: a match that is not in this batch.", // invented key
          "just a bare sentence with no tag at all",
        ].join("\n")
      )
  );
  assert.equal(result.size, 1, "only the one real, first-seen key survives");
  assert.equal(result.get("154682|10.5"), "first.", "the FIRST answer wins, not the last");
});

test("an empty or failed generation yields an empty map, never a partial guess", async () => {
  const { result } = await withStub(
    () => generateBatch(ITEMS),
    () => new Response("nope", { status: 500 })
  );
  assert.equal(result.size, 0);
  const { result: empty } = await withStub(() => generateBatch(ITEMS), () => reply(""));
  assert.equal(empty.size, 0);
  assert.equal((await generateBatch([])).size, 0, "no items is not a provider call");
});

test("the token budget scales with the batch, or later lines get truncated away", async () => {
  // One line is ~35 tokens. A fixed budget sized for a single delivery would cut the reply off
  // after the first two or three, and the caller cannot tell a truncated line from a skipped one.
  const { sent } = await withStub(
    () => generateBatch(ITEMS),
    () => reply(ITEMS.map((i) => `[${i.key}]: x.`).join("\n"))
  );
  assert.ok(sent[0].max_tokens >= 120 * ITEMS.length, `max_tokens ${sent[0].max_tokens} must scale with 3 items`);
  assert.ok(BATCH_MAX_ITEMS >= 6 && BATCH_MAX_ITEMS <= 20, "a batch is an over-ish, not a whole innings");
});
