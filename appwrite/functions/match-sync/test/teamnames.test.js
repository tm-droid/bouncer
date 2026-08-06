import test from "node:test";
import assert from "node:assert/strict";
import { resolveMatchTeams, teamsFromTitle, codeMatchesName, teamStatsOf } from "../src/teamnames.js";

// A slice of the real corpus, including the two names that collide with county short codes.
const STATS = {
  India: { winRate: 0.61 },
  Australia: { winRate: 0.64 },
  "South Africa": { winRate: 0.5 },
  "West Indies": { winRate: 0.43 },
  "United States of America": { winRate: 0.5 },
  "Papua New Guinea": { winRate: 0.55 },
  "Sri Lanka": { winRate: 0.42 },
  Suriname: { winRate: 0.25 },
  Kenya: { winRate: 0.53 },
};

test("a title splits into its two sides", () => {
  assert.deepEqual(teamsFromTitle("Surrey vs Kent, Group A"), ["Surrey", "Kent"]);
  assert.deepEqual(teamsFromTitle("India vs Australia, 1st T20I"), ["India", "Australia"]);
  assert.equal(teamsFromTitle("Some Tournament Final"), null, "no 'vs' is not a two-sided title");
});

test("an international fixture resolves both sides to corpus names", () => {
  assert.deepEqual(
    resolveMatchTeams({ title: "India vs Australia, 1st T20I", battingTeamName: "IND", teamStats: STATS }),
    { battingTeam: "India", bowlingTeam: "Australia" },
  );
});

test("the batting side is attributed from the short code, either way round", () => {
  const title = "India vs Australia, 1st T20I";
  assert.equal(resolveMatchTeams({ title, battingTeamName: "AUS", teamStats: STATS }).battingTeam, "Australia");
  assert.equal(resolveMatchTeams({ title, battingTeamName: "IND", teamStats: STATS }).battingTeam, "India");
});

test("codes that are neither a prefix nor initials still resolve", () => {
  // SA and WI are initials; USA and PNG are neither and need the irregular table.
  assert.ok(codeMatchesName("SA", "South Africa"));
  assert.ok(codeMatchesName("WI", "West Indies"));
  assert.ok(codeMatchesName("USA", "United States of America"));
  assert.ok(codeMatchesName("PNG", "Papua New Guinea"));
  assert.ok(codeMatchesName("SL", "Sri Lanka"));
});

test("a domestic fixture resolves to nothing rather than to a colliding country", () => {
  // The reason this module exists. "SUR" is Surrey here and Suriname in the corpus; "KEN" is Kent
  // and Kenya. Both were live on 2026-08-05 as Surrey vs Kent. Borrowing Suriname's win rate would
  // be a wrong number where the 0.5 fallback is merely an absent one.
  assert.equal(
    resolveMatchTeams({ title: "Surrey vs Kent, Group A", battingTeamName: "SUR", teamStats: STATS }),
    null,
  );
});

test("one recognised side is not enough", () => {
  // Peru has no corpus entry, so Brazil's win rate must not be used against an assumed-average Peru.
  assert.equal(
    resolveMatchTeams({ title: "Brazil vs Peru, 1st Match", battingTeamName: "BRA", teamStats: STATS }),
    null,
  );
  // A scratch XI is not the national side it is named after.
  assert.equal(
    resolveMatchTeams({ title: "Sri Lanka XI vs India, 3-Day Warm-up Match", battingTeamName: "SL", teamStats: STATS }),
    null,
  );
});

test("an unattributable batting side resolves to nothing rather than guessing orientation", () => {
  const title = "India vs Australia, 1st T20I";
  // Matches neither side: picking one would invert the probability half the time.
  assert.equal(resolveMatchTeams({ title, battingTeamName: "ZIM", teamStats: STATS }), null);
  assert.equal(resolveMatchTeams({ title, battingTeamName: "", teamStats: STATS }), null);
  assert.equal(resolveMatchTeams({ title, battingTeamName: null, teamStats: STATS }), null);
});

test("resolution is case-insensitive on both the title and the code", () => {
  assert.deepEqual(
    resolveMatchTeams({ title: "india vs AUSTRALIA, 1st T20I", battingTeamName: "ind", teamStats: STATS }),
    { battingTeam: "India", bowlingTeam: "Australia" },
  );
});

test("an empty or missing model yields no resolution instead of throwing", () => {
  const title = "India vs Australia, 1st T20I";
  assert.equal(resolveMatchTeams({ title, battingTeamName: "IND", teamStats: {} }), null);
  assert.equal(resolveMatchTeams({ title, battingTeamName: "IND" }), null);
  assert.equal(resolveMatchTeams({}), null);
});

test("team stats are read from either model shape", () => {
  // evaluate() accepts a v3 model and a bare stats object; resolution must read the same table it
  // will, or names get validated against one map and looked up in another.
  assert.deepEqual(teamStatsOf({ version: 3, teamStats: STATS }), STATS);
  assert.deepEqual(teamStatsOf(STATS), STATS);
  assert.deepEqual(teamStatsOf(null), {});
});
