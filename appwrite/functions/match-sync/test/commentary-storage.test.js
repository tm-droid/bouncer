import test from "node:test";
import assert from "node:assert/strict";

import { commentaryCapFor, commentaryMatchSet, commentaryOrigin } from "../src/main.js";
import { classifyEvent, outcomeClause } from "../src/llm.js";
import { parseCommentary } from "../src/cricbuzz.js";

// ── Storage is no longer gated on a viewer ───────────────────────────────────
//
// It used to be: `commentaryCapFor` decided how many rows a match could WRITE, so a live match
// nobody had open stored one delivery per 45 minutes. Match 163017 ended up with 226 rows spread
// across four days — overs 84 to 88 never captured at all, one FOUR in the whole innings, and an
// Overview whose newest line was hours old. All three device-side reports were that one fact.

test("every live match stores commentary, viewer or not", () => {
  const live = [{ id: "watched" }, { id: "slow" }, { id: "idle" }];
  const set = commentaryMatchSet(live, new Set(["watched"]), new Set(["slow"]));
  assert.equal(set.size, 3);
  for (const m of live) assert.ok(set.has(m.id), `${m.id} is not storing commentary`);
});

test("the cap keeps the per-run write count bounded, viewers first", () => {
  const live = Array.from({ length: 40 }, (_, i) => ({ id: `m${i}` }));
  const set = commentaryMatchSet(live, new Set(["m39"]), new Set(["m38"]));
  assert.equal(set.size, 16);
  assert.ok(set.has("m39"), "a viewed match lost its slot to an idle one");
  assert.ok(set.has("m38"), "a slow-tier match lost its slot to an idle one");
});

test("the viewer gate now budgets LLM rewrites, and only those", () => {
  const viewed = new Set(["watched"]);
  const slow = new Set(["unwatched"]);
  assert.equal(commentaryCapFor("watched", viewed, slow), 10);
  assert.equal(commentaryCapFor("unwatched", viewed, slow), 2);
  // Still 0 — but 0 rewrites, not 0 rows. The row is written with ai_generated: false.
  assert.equal(commentaryCapFor("idle", viewed, slow), 0);
});

// ── The overturned review ────────────────────────────────────────────────────
//
// Both fixtures are verbatim rows from match 163017, stored as event: "WICKET" and rendered as the
// entire contents of the commentary tab's Wickets filter. Cricbuzz writes the on-field decision and
// the review in one item, so the line says OUT and then says the batter survived.

const REVIEW_SURVIVED = [
  "Manav Suthar to Pasindu Sooriyabandara, no run, one gets two for Manav Suthar or has he? " +
    "Pasindu Sooriyabandara has been given OUT LBW, but he reviews.The line looks good, but is " +
    "there an inside edge? Yes, a big spike when the ball was next to bat - good review and " +
    "Sooriyabandara survives",
  "Saransh Jain to Lahiru Kumara, no run, Lahiru Kumara has been given OUT LBW, but he's " +
    "reviewed straightaway. Was there an inside edge? Looks like it has and UltgraEdge confirms " +
    "the same - the original call will be reversed.",
];

test("an on-field OUT the batter reviewed away is not a wicket", () => {
  for (const line of REVIEW_SURVIVED) {
    assert.equal(outcomeClause(line), "no run");
    assert.equal(classifyEvent(line), "NONE", line.slice(0, 60));
  }
});

test("a real dismissal is still a wicket, however the description reads", () => {
  assert.equal(classifyEvent("Bumrah to Root, out Caught by Pant, reviewed and upheld"), "WICKET");
  assert.equal(classifyEvent("THATS OUT!! Caught!! Kohli has to go"), "WICKET");
});

test("the outcome clause outranks a boundary word in the description", () => {
  // "he needs six off four" is a sentence a commentator writes about a dot ball.
  assert.equal(classifyEvent("Starc to Smith, no run, and he still needs SIX off FOUR"), "NONE");
  assert.equal(classifyEvent("Starc to Smith, FOUR, through the covers"), "FOUR");
  assert.equal(classifyEvent("Starc to Smith, six, into the crowd"), "SIX");
});

// ── Per-item innings ─────────────────────────────────────────────────────────

test("parseCommentary carries the innings the delivery belongs to", () => {
  const html = `<script>self.__next_f.push([1,${JSON.stringify(
    JSON.stringify({
      commentaryList: [
        { commText: "Suthar to Kumara, no run", timestamp: 2, ballMetric: "89.3", inningsId: 2 },
        { commText: "Jain to Nuwantha, 1 run", timestamp: 3, ballMetric: "0.1", inningsId: 3 },
      ],
    })
  )}])</script>`;
  const items = parseCommentary(html);
  assert.deepEqual(
    items.map((i) => [i.ballMetric?.label, i.innings]),
    [["0.1", 3], ["89.3", 2]],
    "newest-first, each with its own innings"
  );
});

test("a missing innings is null, never a neighbour's number", () => {
  const html = `<script>self.__next_f.push([1,${JSON.stringify(
    JSON.stringify({ c: [{ commText: "Suthar to Kumara, no run", timestamp: 1, ballMetric: "89.3" }] })
  )}])</script>`;
  assert.equal(parseCommentary(html)[0].innings, null);
});

// ── Where the text came from ──────────────────────────────────────────────────
//
// `ai_generated` was added so "is the stored commentary actually AI?" was answerable from storage
// rather than inferred. It stops one question short: a false row is either a chain that failed or a
// delivery with no rewrite budget, and those need opposite responses. `provider` closes that, and
// is derived from the same input as `ai_generated` so the two can never disagree.

test("origin: no rewrite budget is cricbuzz, and not a failure", () => {
  // The common case by a distance — ~97% of rows on 2026-09-16. It must not look like a defect, or
  // the signal that a defect produces is buried under it.
  assert.deepEqual(commentaryOrigin(null), { aiGenerated: false, provider: "cricbuzz" });
});

test("origin: a rewritten row names the rung that wrote it", () => {
  assert.deepEqual(
    commentaryOrigin({ line: "19.2: Rahul takes it.", provider: "gemini-flash-lite", verified: true }),
    { aiGenerated: true, provider: "gemini-flash-lite" }
  );
  assert.deepEqual(
    commentaryOrigin({ line: "19.2: Rahul takes it.", provider: "groq" }),
    { aiGenerated: true, provider: "groq" }
  );
});

test("origin: a failed rewrite carries WHY, because the two reasons are different bugs", () => {
  // providers_exhausted = nobody answered (keys, quota, network).
  // verification_failed = somebody answered and was rejected as a hallucination.
  // Same stored text either way, so without this they are one undifferentiated blob.
  assert.deepEqual(
    commentaryOrigin({ line: null, reason: "providers_exhausted" }),
    { aiGenerated: false, provider: "cricbuzz:providers_exhausted" }
  );
  assert.deepEqual(
    commentaryOrigin({ line: null, reason: "verification_failed" }),
    { aiGenerated: false, provider: "cricbuzz:verification_failed" }
  );
});

test("origin: the two columns never contradict each other", () => {
  // The invariant the single function exists to hold: ai_generated is true exactly when provider
  // names a rung, and false exactly when it names cricbuzz.
  for (const g of [
    null,
    { line: "x", provider: "groq" },
    { line: "x", provider: undefined },
    { line: null, reason: "providers_exhausted" },
    { line: null, reason: undefined },
  ]) {
    const { aiGenerated, provider } = commentaryOrigin(g);
    assert.equal(aiGenerated, !provider.startsWith("cricbuzz"), `disagreement for ${JSON.stringify(g)}`);
    assert.ok(provider.length <= 48, "must fit the column");
  }
});
