import { test } from "node:test";
import assert from "node:assert/strict";
import { bodyFileId, extractParagraphs, shapeHint } from "../src/articleBody.js";

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
