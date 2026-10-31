import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { parseSquads, squadsUrl } from "../src/squads.js";

// The pages `MatchSquadsRscTest` pins on the device: IND v WI 2nd ODI (151543, in progress) and
// PAK v BAN (171051, preview), captured 2026-09-30.
const fixture = (name) =>
  readFileSync(new URL(`../../../../android/app/src/test/resources/${name}`, import.meta.url), "utf8");

test("after the toss each side has a playing eleven and a bench", () => {
  const squads = parseSquads(fixture("cricbuzz_match_squads_xi.html"));
  assert.equal(squads.hasPlayingXi, true);
  assert.equal(squads.team1.teamId, "2");
  assert.equal(squads.team1.shortName, "IND");
  assert.equal(squads.team1.playingXi.length, 11);
  assert.equal(squads.team1.bench.length, 4);
  assert.deepEqual(squads.team1.squad, []);
  assert.deepEqual(squads.team1.playingXi[0], { id: "576", name: "Rohit Sharma", role: "Batter", captain: false, keeper: false });
  assert.equal(squads.team1.playingXi.find((p) => p.captain).name, "Shubman Gill");
  assert.equal(squads.team2.teamId, "10");
  assert.equal(squads.team2.playingXi.length, 11);
  const hope = squads.team2.playingXi.find((p) => p.captain);
  assert.equal(hope.name, "Shai Hope");
  assert.equal(hope.keeper, true, "captain and keeper can be the same player");
});

test("before the toss there is a squad and no eleven", () => {
  const squads = parseSquads(fixture("cricbuzz_match_squads_preview.html"));
  assert.equal(squads.hasPlayingXi, false);
  assert.equal(squads.team1.shortName, "PAK");
  assert.equal(squads.team1.squad.length, 15);
  assert.deepEqual(squads.team1.playingXi, []);
  assert.equal(squads.team2.squad.length, 13);
});

test("a page without team sheets is null, not an empty squad", () => {
  assert.equal(parseSquads(fixture("cricbuzz_points_table.html")), null);
  assert.equal(squadsUrl("151543"), "https://www.cricbuzz.com/cricket-match-squads/151543");
});
