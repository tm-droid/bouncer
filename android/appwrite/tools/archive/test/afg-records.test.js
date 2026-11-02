import { test } from "node:test";
import assert from "node:assert/strict";
import { buildRecords, recordMatch, resultOf } from "../src/afg-records.js";

const team = (teamId, name, score = "100/5") => ({ teamId, name, score });
const file = (id, format, status, a, b, extra = {}) => ({
  id,
  source: "cricbuzz-backfill",
  meta: { id, matchFormat: format, status, startDate: String(Date.UTC(2020, 5, Number(id) % 28 + 1)), teams: [a, b] },
  scorecard: { innings: [{}, {}] },
  ...extra,
});
const AFG = team("96", "Afghanistan");
const ZIM = team("11", "Zimbabwe");
const IRE = team("27", "IRELAND");

test("results: winner by name, abbreviation or initials; tie, draw, no result", () => {
  const t = [AFG, ZIM];
  assert.deepEqual(resultOf("Afghanistan won by 5 wkts", t), { winner: 0 });
  assert.deepEqual(resultOf("Zim won by 3 runs (DLS Method)", t), { winner: 1 });
  assert.deepEqual(resultOf("HK won by 30 runs", [AFG, team("1", "Hong Kong")]), { winner: 1 });
  assert.deepEqual(resultOf("Match tied (Ireland won the Super Over)", t), { r: "tie" });
  assert.deepEqual(resultOf("Match drawn", t), { r: "draw" });
  assert.deepEqual(resultOf("No result due to rain", t), { r: "no result" });
  assert.ok("unresolved" in resultOf("Nobody won by 5 runs", t));
});

test("a match with no ball bowled, a women's side or a non-Afghan non-league match is not a record row", () => {
  assert.equal(recordMatch({ ...file("1", "ODI", "Afghanistan won by 1 run", AFG, ZIM), scorecard: { innings: [] } }), null);
  assert.equal(recordMatch(file("2", "ODI", "x won by 1 run", team("5", "India Women"), AFG)), null);
  assert.equal(recordMatch({ ...file("3", "ODI", "India won by 1 run", team("5", "India"), ZIM), source: undefined }), null);
  assert.equal(recordMatch(file("4", "FC", "Afghanistan won by 1 run", AFG, ZIM)), null);
});

test("an unrendered status falls back to the higher score", () => {
  const f = file("5", "ODI", "$undefined", team("96", "Afghanistan", "258/4"), team("26", "Canada", "257/7"));
  assert.deepEqual(recordMatch(f).result, { winner: 0 });
});

test("team and pair records use the Cricsheet schemas, and opponents get no team file", () => {
  const { files, matches, unresolved } = buildRecords([
    file("10", "ODI", "Afghanistan won by 5 wkts", AFG, IRE),
    file("11", "ODI", "Ireland won by 5 wkts", AFG, team("27", "Ireland")),
    file("12", "T20", "Match tied", AFG, ZIM),
    file("13", "TEST", "Match drawn", AFG, ZIM),
    file("14", "T20", "Kabul won by 3 wkts", team("900", "Kabul Zwanan"), team("901", "Balkh Legends")),
  ]);
  assert.equal(matches, 5);
  assert.deepEqual(unresolved, []);
  const afg = files.get("s/team/afghanistan.json");
  assert.deepEqual(afg.f, { ODI: [2, 1, 1, 0, 0, 0], T20I: [1, 0, 0, 1, 0, 0], Test: [1, 0, 0, 0, 1, 0] });
  assert.deepEqual(afg.y["2020"], [4, 1, 1, 1, 1, 0]);
  assert.equal(files.has("s/team/ireland.json"), false);
  const pair = files.get("s/h2h/afghanistan__ireland.json");
  assert.deepEqual(pair.t, ["Afghanistan", "Ireland"]);
  assert.deepEqual(pair.f.ODI, [2, 1, 1, 0, 0, 0]);
  assert.deepEqual(pair.m.map((r) => r.slice(2, 4)), [["ODI", 1], ["ODI", 0]]);
  assert.deepEqual(files.get("s/h2h/afghanistan__zimbabwe.json").f.T20I, [1, 0, 0, 1, 0, 0]);
  assert.deepEqual(files.get("s/team/kabul-zwanan.json").f.T20, [1, 1, 0, 0, 0, 0]);
  assert.deepEqual(files.get("s/h2h/balkh-legends__kabul-zwanan.json").t, ["Balkh Legends", "Kabul Zwanan"]);
});
