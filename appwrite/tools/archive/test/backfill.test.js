import { test } from "node:test";
import assert from "node:assert/strict";
import {
  backfillIndexRow,
  cardProblems,
  isAfghanistanMatch,
  isFinished,
  isLeagueMatch,
  matchFile,
  matchHeaderOf,
  matchesOfSeries,
  metaOf,
  officialsOf,
  seriesOfArchive,
} from "../src/backfill.js";
import { mergeIndex, monthOf, yearsOf } from "../src/archive.js";

const info = (t1, t2, extra = {}) => ({ matchId: 1, matchFormat: "ODI", team1: { teamId: t1 }, team2: { teamId: t2 }, ...extra });

test("only team 96 in a senior format is an Afghanistan match; the league takes any team", () => {
  assert.equal(isAfghanistanMatch(info(96, 5)), true);
  assert.equal(isAfghanistanMatch(info(5, 96)), true);
  assert.equal(isAfghanistanMatch(info(5, 6)), false);
  assert.equal(isAfghanistanMatch(info(96, 5, { matchFormat: "FC" })), false);
  assert.equal(isLeagueMatch(info(500, 501, { matchFormat: "T20" }), "Afghanistan Premier League 2018"), true);
  assert.equal(isLeagueMatch(info(500, 501, { matchFormat: "T20" }), "Big Bash"), false);
});

test("series and matches come out of flight payloads", () => {
  const archive = [{ internationalData: { seriesMapProto: [{ series: [{ id: 7, name: "A" }, { id: "x", name: "bad" }] }] }, leagueData: { seriesMapProto: [{ series: [{ id: 8, name: "B" }] }] } }];
  assert.deepEqual(seriesOfArchive(archive).map((s) => s.id), ["7", "8"]);
  const series = [{ matchDetails: [{ matchDetailsMap: { match: [{ matchInfo: info(96, 2, { matchId: 55 }) }] } }, { adDetail: {} }] }];
  assert.deepEqual(matchesOfSeries(series).map((m) => m.matchId), [55]);
});

test("a card adds up when batters' runs plus extras are the total", () => {
  const inn = (runs, extras, score) => ({ batsmen: runs.map((r) => ({ runs: String(r) })), extras, score });
  assert.deepEqual(cardProblems({ innings: [inn([10, 20], "7 (b 0, lb 1, w 6)", "37-2")] }), []);
  assert.equal(cardProblems({ innings: [inn([10, 20], "7 (b 0)", "40-2")] }).length, 1);
  assert.deepEqual(cardProblems({ innings: [] }), []);
});

const header = {
  matchId: 9,
  matchDescription: "1st ODI",
  matchFormat: "ODI",
  matchStartTimestamp: 1444980600000,
  state: "Complete",
  series: { id: 3, name: "Zim v Afg" },
  team1: { id: 96, name: "Afghanistan", playerDetails: [{ id: 1, name: "Nabi", fullName: "Mohammad Nabi", captain: true }] },
  team2: { id: 12, name: "Zimbabwe", playerDetails: [] },
  umpire1: { id: 5, name: "U One", country: "ENG" },
  umpire2: { id: 6, name: "U Two", country: "BAN" },
  umpire3: { id: 7, name: "U Three", country: "ENG" },
  referee: { id: 8, name: "R Ef", country: "SL" },
  playersOfTheMatch: [{ id: 3, name: "Masakadza", fullName: "Wellington Masakadza" }],
};

test("the header is found in a payload and reduced to names", () => {
  assert.equal(matchHeaderOf([{ a: [{ b: header }] }]).matchId, 9);
  const o = officialsOf(header);
  assert.deepEqual(o.umpires.map((u) => u.name), ["U One", "U Two"]);
  assert.equal(o.thirdUmpire.name, "U Three");
  assert.equal(o.referee.name, "R Ef");
  assert.equal(o.playersOfTheMatch[0].name, "Wellington Masakadza");
  assert.equal(o.squads["96"][0].name, "Mohammad Nabi");
});

test("the match file and index row have the shapes the archive writes", () => {
  const detail = { status: "Zimbabwe won by 8 wkts", team1Id: "96", team1Score: "122/10", team1Overs: "35", team2Id: "12", team2Score: "126/2", team2Overs: "30" };
  const meta = metaOf({ id: 9, header, info: null, detail });
  assert.equal(meta.teams[0].score, "122/10");
  assert.equal(meta.teams[1].name, "Zimbabwe");
  assert.equal(isFinished(meta), true);
  const file = matchFile({ id: 9, nowMs: 0, meta, detail, scorecard: { innings: [] }, officials: officialsOf(header), venue: { name: "Queens" } });
  assert.equal(file.final, 1);
  assert.deepEqual(file.rows, []);
  assert.equal(file.source, "cricbuzz-backfill");
  assert.deepEqual(Object.keys(file).sort(), ["detail", "final", "finalisedAt", "id", "info", "meta", "rows", "scorecard", "source", "v"]);
  const row = backfillIndexRow(meta, { name: "Queens" });
  assert.deepEqual(row, ["9", "2015-10-16", "ODI", "male", "Afghanistan", "Zimbabwe", "Zimbabwe won by 8 wkts", "Zim v Afg", "Queens"]);
  assert.equal(monthOf(row), "2015-10");
  assert.deepEqual(yearsOf({ "2015-10": mergeIndex([], [row]) }), { 2015: { 10: 1 } });
});

test("a match that has not finished is not final", () => {
  assert.equal(isFinished({ status: "Day 2: Stumps", state: "In Progress" }), false);
});

test("the header with officials wins over a bare one earlier in the page", () => {
  const bare = { matchId: 9, matchDescription: "x", team1: { id: 1 }, team2: { id: 2 } };
  assert.equal(matchHeaderOf([bare, { n: header }]).umpire1.name, "U One");
  assert.equal(matchHeaderOf([bare]), bare);
});
