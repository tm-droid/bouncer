import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  bodyFileId,
  extractParagraphs,
  formatSubstitutions,
  hasUnresolvedTokens,
  listedStoryIds,
  needsRefetch,
  resolveTokens,
  stripTokens,
  shapeHint,
  withoutTokens,
} from "../../src/news/articleBody.js";
import { claimFetch } from "../../src/news/main.js";

// Captured from RapidAPI on 2026-10-08, unedited.
const captured = JSON.parse(readFileSync(new URL("./fixtures/detail-140424.json", import.meta.url), "utf8"));

test("a captured story has its bold and link placeholders resolved to words", () => {
  const paragraphs = extractParagraphs(captured);
  assert.equal(paragraphs.length, 7, "seven text blocks, two ads skipped");
  assert.match(paragraphs[0], /eight-wicket win in the third and final game in Harare\. Zimbabwe put up/);
  assert.match(paragraphs[6], /^Brief scores: Zimbabwe 142\/6 in 20 overs/);
  assert.equal(hasUnresolvedTokens(paragraphs), false);
  // The link's target is not words and must not be written into the sentence.
  assert.ok(paragraphs.every((p) => !p.includes("cricbuzz://")));
});

test("a link's words win over its target, whichever is listed first", () => {
  const format = [
    { type: "links", value: [{ id: "@L0$", value: "the final" }] },
    { type: "urls", value: [{ id: "@L0$", value: "cricbuzz://match?id=1" }] },
  ];
  assert.equal(resolveTokens("won @L0$ easily", formatSubstitutions(format)), "won the final easily");
});

test("a placeholder with no entry is removed, never shown", () => {
  assert.equal(resolveTokens("before @I3$ after", formatSubstitutions([])), "before  after");
  const detail = { content: [{ content: { contentType: "text", contentValue: "before @I3$ after" } }] };
  assert.deepEqual(extractParagraphs(detail), ["before after"]);
});

test("a stored body with a placeholder is recognised as unresolved", () => {
  assert.equal(hasUnresolvedTokens(["fine", "in the @L0$."]), true);
  assert.equal(hasUnresolvedTokens(["an email a@b.com costs $5"]), false);
  assert.equal(hasUnresolvedTokens(undefined), false);
});

test("only stories the index lists may be fetched", () => {
  const index = { response: { storyList: [{ story: { id: 140424 } }, { ad: {} }, { story: { id: "7" } }] } };
  assert.deepEqual([...listedStoryIds(index)], ["140424", "7"]);
  assert.equal(listedStoryIds(null).size, 0);
  assert.equal(listedStoryIds({ response: {} }).size, 0);
});

// A detail payload in the shape RapidAPI returns it: text blocks interleaved with images and ads.
const detail = {
  id: 140155,
  headline: "BCB eyes bilateral series against Nepal in 2027",
  content: [
    { content: { contentType: "image", contentValue: { id: "1072492", caption: "BCB" } } },
    {
      content: {
        contentType: "text",
        contentValue: { content: "Bangladesh Cricket Board officials said on Saturday\n(September 12)  that they are looking to play." },
      },
    },
    { content: { contentType: "ad", contentValue: { layout: "leaderboard" } } },
    { content: { contentType: "text", contentValue: { content: "The decision was taken after the invitation." } } },
  ],
};

test("extractParagraphs keeps text blocks in order and skips images and ads", () => {
  assert.deepEqual(extractParagraphs(detail), [
    "Bangladesh Cricket Board officials said on Saturday (September 12) that they are looking to play.",
    "The decision was taken after the invitation.",
  ]);
});

test("extractParagraphs accepts a contentValue that is a bare string", () => {
  const bare = { content: [{ content: { contentType: "text", contentValue: "A single line." } }] };
  assert.deepEqual(extractParagraphs(bare), ["A single line."]);
});

test("extractParagraphs drops blank text blocks", () => {
  const blank = {
    content: [
      { content: { contentType: "text", contentValue: { content: "   " } } },
      { content: { contentType: "text", contentValue: { content: "Real." } } },
    ],
  };
  assert.deepEqual(extractParagraphs(blank), ["Real."]);
});

// An unrecognised payload must return [] rather than throw: the caller turns an empty body into a
// 503 with a shape hint, and a throw would lose the hint.
test("extractParagraphs returns an empty list for an unrecognised payload", () => {
  assert.deepEqual(extractParagraphs(null), []);
  assert.deepEqual(extractParagraphs({}), []);
  assert.deepEqual(extractParagraphs({ content: "not an array" }), []);
  assert.deepEqual(extractParagraphs({ content: [{ nope: true }] }), []);
});

test("shapeHint names the top-level keys and the block types", () => {
  const hint = shapeHint(detail);
  assert.match(hint, /keys=\[id,headline,content\]/);
  assert.match(hint, /blocks=\[image,text,ad,text\]/);
});

test("shapeHint says when content is not an array", () => {
  assert.match(shapeHint({ story: {} }), /content:not-an-array/);
});

test("bodyFileId is derived from the story id", () => {
  assert.equal(bodyFileId("140155"), "news-body-140155");
});

// A stored body that reads as unresolved is fetched again. If what is fetched could read as
// unresolved too, every request for that story would call upstream.
test("what extractParagraphs returns can never read as unresolved", () => {
  const detail = {
    format: [{ type: "bold", value: [{ id: "@B0$", value: "see @L9$ and @B0$" }] }],
    content: [{ content: { contentType: "text", contentValue: "start @B0$ end @X1$" } }],
  };
  const paragraphs = extractParagraphs(detail);
  assert.deepEqual(paragraphs, ["start see and end"]);
  assert.equal(hasUnresolvedTokens(paragraphs), false);
});

test("a body cached with placeholders is still servable without them", () => {
  assert.deepEqual(
    withoutTokens(["won in the @L0$. Then more.", "@B0$ Zimbabwe 142/6", "@B1$", 7]),
    ["won in the. Then more.", "Zimbabwe 142/6"],
  );
  assert.equal(hasUnresolvedTokens(withoutTokens(["a @L0$ b"])), false);
});

// Removing a placeholder can leave another behind. One pass over "@B@B0$0$" leaves "@B0$".
test("stripping placeholders leaves none, however they nest", () => {
  for (const text of ["@B@B0$0$", "@L@L@L1$2$3$", "a @B0$@B@B1$0$ b", "@@B0$B0$"]) {
    const out = stripTokens(text);
    assert.equal(hasUnresolvedTokens([out]), false, `${text} -> ${out}`);
  }
  const detail = { content: [{ content: { contentType: "text", contentValue: "x @B@B0$0$ y" } }] };
  assert.equal(hasUnresolvedTokens(extractParagraphs(detail)), false);
  assert.equal(hasUnresolvedTokens(withoutTokens(["x @B@B0$0$ y"])), false);
});

// The decision to fetch again must not rest on what the text looks like alone.
test("a body this code stored is never fetched again, whatever its text looks like", () => {
  assert.equal(needsRefetch({ paragraphs: ["in the @L0$."] }), true, "stored before placeholders were resolved");
  assert.equal(needsRefetch({ paragraphs: ["in the @L0$."], formatResolved: true }), false);
  assert.equal(needsRefetch({ paragraphs: ["plain"] }), false);
  assert.equal(needsRefetch(null), false);
});

test("a story is asked upstream once per hold, whatever came of it", () => {
  const attempts = new Map();
  const t0 = 1_800_000_000_000;
  assert.equal(claimFetch("1", t0, attempts), true);
  // Arriving together: the second finds the claim already taken.
  assert.equal(claimFetch("1", t0, attempts), false);
  assert.equal(claimFetch("1", t0 + 9 * 60_000, attempts), false);
  assert.equal(claimFetch("2", t0, attempts), true, "another story is its own claim");
  assert.equal(claimFetch("1", t0 + 10 * 60_000, attempts), true, "the hold has passed");
});

test("a container asks upstream for a bounded number of stories per hold", () => {
  const attempts = new Map();
  const t0 = 1_800_000_000_000;
  let granted = 0;
  for (let i = 0; i < 500; i += 1) if (claimFetch(String(i), t0 + i, attempts)) granted += 1;
  assert.equal(granted, 40);
  assert.equal(attempts.size, 40);
  // The window moves on and the allowance comes back.
  assert.equal(claimFetch("later", t0 + 11 * 60_000, attempts), true);
  assert.equal(attempts.size, 1);
});
