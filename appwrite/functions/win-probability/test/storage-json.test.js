import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseJsonFile, fileText } from "../src/storageJson.js";

const here = (p) => fileURLToPath(new URL(p, import.meta.url));

// The shape node-appwrite 15 actually returns for an application/json file, and the one every
// reader assumed it would never see.
test("an already-parsed object passes straight through", () => {
  const map = { 153904: 1785287019229 };
  assert.deepEqual(parseJsonFile(map), map);
});

test("bytes are still decoded", () => {
  const bytes = Buffer.from(JSON.stringify({ version: 3 }), "utf8");
  assert.deepEqual(parseJsonFile(bytes), { version: 3 });
});

test("an ArrayBuffer and a typed-array view are decoded", () => {
  const bytes = Buffer.from('{"a":1}', "utf8");
  const view = new Uint8Array(bytes);
  assert.deepEqual(parseJsonFile(view), { a: 1 });
  assert.deepEqual(parseJsonFile(view.buffer.slice(view.byteOffset, view.byteOffset + view.byteLength)), { a: 1 });
});

test("a JSON string is parsed", () => {
  assert.deepEqual(parseJsonFile('{"a":1}'), { a: 1 });
});

test("a missing file is null, not a throw", () => {
  assert.equal(parseJsonFile(null), null);
  assert.equal(parseJsonFile(undefined), null);
});

test("genuinely malformed bytes still throw", () => {
  assert.throws(() => parseJsonFile(Buffer.from("not json", "utf8")));
});

test("fileText handles the non-JSON files too", () => {
  // live-count is a bare number written as plain text; poll-live-matches parseInts it.
  assert.equal(fileText(Buffer.from("7", "utf8")), "7");
  assert.equal(parseInt(fileText(7)), 7);
  assert.equal(fileText("7"), "7");
  assert.equal(fileText(null), "");
});

test("the reader regression it fixes", () => {
  // Reproduces the exact failure: every call site did this unconditionally, and the resulting
  // throw was swallowed by a catch that reported "file missing".
  assert.throws(
    () => Buffer.from({ 153904: 1 }),
    /must be of type string or an instance of Buffer/,
  );
  assert.deepEqual(parseJsonFile({ 153904: 1 }), { 153904: 1 });
});

test("storageJson.js is byte-identical across every function that reads storage", () => {
  const canonical = readFileSync(here("../src/storageJson.js"), "utf8");
  for (const fn of ["match-sync", "match-presence", "poll-live-matches", "news-sync"]) {
    const copy = readFileSync(here(`../../${fn}/src/storageJson.js`), "utf8");
    assert.equal(copy, canonical, `${fn}/src/storageJson.js has drifted`);
  }
});
