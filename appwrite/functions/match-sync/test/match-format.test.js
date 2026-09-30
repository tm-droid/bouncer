import { test } from "node:test";
import assert from "node:assert/strict";
import { parseFormats } from "../src/poll.js";

// Shaped like the real page: a Next.js flight payload, so every quote arrives escaped.
const page = (objects) => objects.map((o) => JSON.stringify(o).replace(/"/g, '\\"')).join(",");

test("reads the format Cricbuzz states for each fixture", () => {
  const html = page([
    { matchInfo: { matchId: 162187, matchDesc: "124th Match", matchFormat: "ODI" } },
    { matchInfo: { matchId: 144904, matchDesc: "24th Match", matchFormat: "HUN" } },
  ]);
  assert.deepEqual(parseFormats(html), { 162187: "ODI", 144904: "HUN" });
});

test("a fixture without a format does not inherit its neighbour's", () => {
  // The regression this guards: `[^{}]` must not cross an object boundary, or 166533 — which
  // Cricbuzz does not list a format for — would silently take the next fixture's HUN.
  const html = page([
    { matchInfo: { matchId: 166533, matchDesc: "3rd Match" } },
    { matchInfo: { matchId: 144904, matchDesc: "24th Match", matchFormat: "HUN" } },
  ]);
  const found = parseFormats(html);
  assert.equal(found["166533"], undefined);
  assert.equal(found["144904"], "HUN");
});

test("the first format wins when a page repeats a fixture", () => {
  const html = page([
    { matchInfo: { matchId: 143803, matchFormat: "ODI" } },
    { matchInfo: { matchId: 143803, matchFormat: "T20" } },
  ]);
  assert.equal(parseFormats(html)["143803"], "ODI");
});

test("junk in, empty out — never throws", () => {
  assert.deepEqual(parseFormats(""), {});
  assert.deepEqual(parseFormats(null), {});
  assert.deepEqual(parseFormats("<html>no payload here</html>"), {});
});
