import test from "node:test";
import assert from "node:assert/strict";
import {
  resolveMatchTeams, teamsFromTitle, codeMatchesName, teamStatsOf, teamKey,
  genderFromTitle, stripWomenSuffix,
} from "../src/teamnames.js";

// A slice of the real corpus, keyed as model v5 keys it, including the two names that collide with
// county short codes and one country present in both genders.
const NAMES_T20_MALE = [
  "India", "Australia", "South Africa", "West Indies", "United States of America",
  "Papua New Guinea", "Sri Lanka", "Suriname", "Kenya", "Brazil",
];
const STATS = {};
for (const [i, name] of NAMES_T20_MALE.entries()) {
  STATS[teamKey(name, "male", "T20")] = { winRate: 0.4 + i * 0.02 };
}
STATS[teamKey("India", "female", "T20")] = { winRate: 0.58 };
STATS[teamKey("Australia", "female", "T20")] = { winRate: 0.79 };
STATS[teamKey("India", "male", "TEST")] = { winRate: 0.55 };
STATS[teamKey("Australia", "male", "TEST")] = { winRate: 0.62 };

const T20 = { teamStats: STATS, format: "T20" };

test("a title splits into its two sides", () => {
  assert.deepEqual(teamsFromTitle("Surrey vs Kent, Group A"), ["Surrey", "Kent"]);
  assert.deepEqual(teamsFromTitle("India vs Australia, 1st T20I"), ["India", "Australia"]);
  assert.equal(teamsFromTitle("Some Tournament Final"), null, "no 'vs' is not a two-sided title");
});

test("an international fixture resolves both sides to corpus names", () => {
  assert.deepEqual(
    resolveMatchTeams({ ...T20, title: "India vs Australia, 1st T20I", battingTeamName: "IND" }),
    { battingTeam: "India", bowlingTeam: "Australia", gender: "male" },
  );
});

test("the batting side is attributed from the short code, either way round", () => {
  const title = "India vs Australia, 1st T20I";
  assert.equal(resolveMatchTeams({ ...T20, title, battingTeamName: "AUS" }).battingTeam, "Australia");
  assert.equal(resolveMatchTeams({ ...T20, title, battingTeamName: "IND" }).battingTeam, "India");
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
    resolveMatchTeams({ ...T20, title: "Surrey vs Kent, Group A", battingTeamName: "SUR" }),
    null,
  );
});

test("one recognised side is not enough", () => {
  // Peru has no corpus entry, so Brazil's win rate must not be used against an assumed-average Peru.
  assert.equal(
    resolveMatchTeams({ ...T20, title: "Brazil vs Peru, 1st Match", battingTeamName: "BRA" }),
    null,
  );
  // A scratch XI is not the national side it is named after.
  assert.equal(
    resolveMatchTeams({ ...T20, title: "Sri Lanka XI vs India, 3-Day Warm-up Match", battingTeamName: "SL" }),
    null,
  );
});

test("an unattributable batting side resolves to nothing rather than guessing orientation", () => {
  const title = "India vs Australia, 1st T20I";
  // Matches neither side: picking one would invert the probability half the time.
  assert.equal(resolveMatchTeams({ ...T20, title, battingTeamName: "ZIM" }), null);
  assert.equal(resolveMatchTeams({ ...T20, title, battingTeamName: "" }), null);
  assert.equal(resolveMatchTeams({ ...T20, title, battingTeamName: null }), null);
});

test("resolution is case-insensitive on both the title and the code", () => {
  assert.deepEqual(
    resolveMatchTeams({ ...T20, title: "india vs AUSTRALIA, 1st T20I", battingTeamName: "ind" }),
    { battingTeam: "India", bowlingTeam: "Australia", gender: "male" },
  );
});

test("an empty or missing model yields no resolution instead of throwing", () => {
  const title = "India vs Australia, 1st T20I";
  assert.equal(resolveMatchTeams({ title, battingTeamName: "IND", teamStats: {} }), null);
  assert.equal(resolveMatchTeams({ title, battingTeamName: "IND" }), null);
  assert.equal(resolveMatchTeams({}), null);
});

test("team stats are read from either model shape", () => {
  // evaluate() accepts a versioned model and a bare stats object; resolution must read the same
  // table it will, or names get validated against one map and looked up in another.
  assert.deepEqual(teamStatsOf({ version: 5, teamStats: STATS }), STATS);
  assert.deepEqual(teamStatsOf(STATS), STATS);
  assert.deepEqual(teamStatsOf(null), {});
});

test("a women's fixture reads the women's slice, not the men's", () => {
  // Cricsheet stores a women's match as the plain country name with gender female, so the "Women"
  // suffix Cricbuzz puts in the title has to come off the name and go into the key. Getting this
  // wrong does not fail loudly: it hands the women's match Australia's men's win rate.
  assert.equal(genderFromTitle("Australia Women vs India Women, 1st T20I"), "female");
  assert.equal(genderFromTitle("Australia vs India, 1st T20I"), "male");
  assert.equal(stripWomenSuffix("London Spirit Women"), "London Spirit");
  assert.equal(stripWomenSuffix("Australia Women's"), "Australia");

  const resolved = resolveMatchTeams({
    ...T20,
    title: "Australia Women vs India Women, 1st T20I",
    battingTeamName: "AUS-W",
  });
  assert.deepEqual(resolved, { battingTeam: "Australia", bowlingTeam: "India", gender: "female" });
});

test("a format with no corpus slice resolves to nothing rather than borrowing another's", () => {
  // India and Australia are both in the corpus, but not under a T10 key. Falling back to their T20
  // rates would be a strength difference invented from a different game.
  assert.equal(
    resolveMatchTeams({ teamStats: STATS, format: "T10", title: "India vs Australia, 1st T10", battingTeamName: "IND" }),
    null,
  );
  assert.equal(
    resolveMatchTeams({ teamStats: STATS, format: "TEST", title: "India vs Australia, 1st Test", battingTeamName: "IND" })
      ?.battingTeam,
    "India",
  );
});

test("a code that is neither a prefix nor initials attributes nothing, rather than guessing", () => {
  // The Hundred squeezes its codes to consonants: Cricbuzz serves LDNW for London Spirit and MILW
  // for MI London, captured from match 145256 on 2026-08-07. Neither is a prefix of its full name
  // nor its initials, so neither side claims the code and the fixture keeps the neutral 0.5.
  // Failing closed is the designed behaviour — the alternative is a 50/50 chance of inverting the
  // probability — but it means a Hundred fixture gets no strength signal, which is a coverage gap
  // and not a bug.
  const stats = {
    "London Spirit|female|HUNDRED": { winRate: 0.32 },
    "MI London|female|HUNDRED": { winRate: 0.48 },
  };
  const title = "MI London Women vs London Spirit Women, 23rd Match";
  assert.equal(resolveMatchTeams({ title, battingTeamName: "MILW", teamStats: stats, format: "HUNDRED" }), null);
  assert.equal(resolveMatchTeams({ title, battingTeamName: "LDNW", teamStats: stats, format: "HUNDRED" }), null);
  // The gender marker alone is still stripped, so a code that DOES follow the rules still works.
  assert.equal(
    resolveMatchTeams({ title, battingTeamName: "MI London-W", teamStats: stats, format: "HUNDRED" })?.battingTeam,
    "MI London",
  );
});
