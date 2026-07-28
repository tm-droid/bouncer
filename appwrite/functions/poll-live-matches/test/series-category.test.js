import test from "node:test";
import assert from "node:assert/strict";
import { seriesCategory, isWomensSeries } from "../src/series.js";

test("a women's T20 competition keeps its T20 classification AND is flagged women's", () => {
  const name = "ICC Women's T20 World Cup";
  assert.equal(seriesCategory(name), "LEAGUE");
  assert.equal(isWomensSeries(name), true);
});

test("the Women's Premier League is flagged, not swallowed by the League rule", () => {
  assert.equal(isWomensSeries("Women's Premier League 2026"), true);
});

test("WPL by acronym is flagged", () => {
  assert.equal(isWomensSeries("WPL 2026"), true);
});

test("men's competitions are not flagged", () => {
  for (const name of ["Indian Premier League", "ICC T20 World Cup", "Ashes", "Ranji Trophy"]) {
    assert.equal(isWomensSeries(name), false, `${name} must not be flagged women's`);
  }
});

test("the flag keys on a whole word, so near-misses do not trip it", () => {
  assert.equal(isWomensSeries("Wanderers Trophy"), false);
  assert.equal(isWomensSeries("Women"), true);
  assert.equal(isWomensSeries("Womens Series"), true);
});

test("format categories are unchanged for non-women's series", () => {
  assert.equal(seriesCategory("Indian Premier League"), "LEAGUE");
  assert.equal(seriesCategory("Ranji Trophy"), "DOMESTIC");
  assert.equal(seriesCategory("Australia tour of England"), "INTERNATIONAL");
});
