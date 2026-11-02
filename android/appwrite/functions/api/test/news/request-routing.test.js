import { test } from "node:test";
import assert from "node:assert/strict";
import { bodyObject } from "../../src/news/main.js";

// The Appwrite runtime exposes `bodyJson` as a GETTER over JSON.parse(bodyRaw). On the headline
// path the device sends an empty string, so merely reading the property throws
// `SyntaxError: Unexpected end of JSON input` — which took the whole function to a 500 and broke
// the News tab. `??` is no defence: the throw is on property access.
const throwingBodyJson = (raw) => ({
  get bodyJson() { return JSON.parse(raw); },
  bodyRaw: raw,
  body: raw,
});

test("an empty body is the headline path, not a crash", () => {
  assert.equal(bodyObject(throwingBodyJson("")), null);
});

test("a story request is read through the throwing getter", () => {
  assert.deepEqual(bodyObject(throwingBodyJson('{"storyId":140155}')), { storyId: 140155 });
});

test("falls back to bodyRaw when bodyJson is absent", () => {
  assert.deepEqual(bodyObject({ bodyRaw: '{"storyId":7}' }), { storyId: 7 });
  assert.deepEqual(bodyObject({ body: '{"storyId":8}' }), { storyId: 8 });
});

test("a non-JSON body is the headline path", () => {
  assert.equal(bodyObject(throwingBodyJson("not json")), null);
  assert.equal(bodyObject(throwingBodyJson("   ")), null);
});

test("a JSON scalar is not a body object", () => {
  assert.equal(bodyObject(throwingBodyJson("42")), null);
  assert.equal(bodyObject(throwingBodyJson('"x"')), null);
  assert.equal(bodyObject(throwingBodyJson("null")), null);
});

test("degenerate requests never throw", () => {
  assert.equal(bodyObject(undefined), null);
  assert.equal(bodyObject(null), null);
  assert.equal(bodyObject({}), null);
});
