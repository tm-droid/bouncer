import test from "node:test";
import assert from "node:assert/strict";

import { findGround, shardIdsFor } from "../src/context.js";
import { shardOf } from "../src/contextmath.js";

const g = (city, tag) => ({ city, tag });

test("a ground Cricbuzz writes with initials joins the archive's plain name", () => {
  const venues = { waca: [g("perth", "w")] };
  assert.equal(findGround(venues, { name: "W.A.C.A. Ground", city: "Perth" })?.tag, "w");
});

test("a renamed ground joins by the archive's name when the city agrees", () => {
  const venues = { "holkar cricket stadium": [g("indore", "h"), g("", "x")] };
  assert.equal(findGround(venues, { name: "Holkar Stadium", city: "Indore" })?.tag, "h");
  const kotambi = { "kotambi stadium": [g("vadodara", "k")] };
  assert.equal(findGround(kotambi, { name: "BCA Stadium, Kotambi", city: "Vadodara" })?.tag, "k");
  const pca = { "maharaja yadavindra singh international cricket stadium": [g("new chandigarh", "p")] };
  assert.equal(findGround(pca, { name: "PCA International Cricket Stadium, Ground A", city: "New Chandigarh" })?.tag, "p");
});

test("a renamed ground joins even when the two sources spell its city differently", () => {
  const venues = { "al amerat cricket ground oman cricket ministry turf 1": [g("al amarat", "a")] };
  assert.equal(findGround(venues, { name: "Al Amerat Cricket Ground (Ministry Turf 1)", city: "Al Amerat" })?.tag, "a");
});

test("a dropped trailing word joins the archive's name when the city agrees", () => {
  assert.equal(findGround({ "icc academy": [g("dubai", "i")] }, { name: "ICC Academy Ground", city: "Dubai" })?.tag, "i");
});

test("an alias never joins a ground in another city, or one the city cannot pick", () => {
  assert.equal(findGround({ waca: [g("perth", "w")] }, { name: "W.A.C.A. Ground", city: "Sydney" }), null);
  assert.equal(findGround({ waca: [g("perth", "a"), g("perth", "b")] }, { name: "W.A.C.A. Ground", city: "Perth" }), null);
  assert.equal(findGround({ waca: [g("perth", "w")] }, { name: "W.A.C.A. Ground", city: null }), null);
});

test("the plain name still wins over an alias", () => {
  const venues = { "holkar stadium": [g("indore", "own")], "holkar cricket stadium": [g("indore", "alias")] };
  assert.equal(findGround(venues, { name: "Holkar Stadium", city: "Indore" })?.tag, "own");
});

test("the shards of the alias names are fetched too", () => {
  const ids = shardIdsFor({ teamKeys: [], gender: "male", format: "T20", venue: { name: "W.A.C.A. Ground" } });
  assert.ok(ids.includes(`wpx-${shardOf("v:waca")}`));
});
