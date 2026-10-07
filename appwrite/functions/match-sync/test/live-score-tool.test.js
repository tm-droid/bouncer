import test from "node:test";
import assert from "node:assert/strict";

import { cricbuzzForTeam1, cricinfoFor, cricinfoForTeam1, report, withRivals } from "../../../tools/winprob-live-score.mjs";

/**
 * The report's rival columns. Both shapes are the ones captured from India v West Indies
 * (Cricbuzz 151565, Cricinfo 1529230) on 2026-10-06; a figure read against the wrong side would
 * score a rival as badly wrong when it was right, so orientation is what these pin.
 */

// West Indies (id 10) batted first, so they are the log's team1. Cricbuzz lists India first.
const CB = (ind, wi) => JSON.stringify({
  matchId: 151565, drawTiePercent: 0,
  team1: { id: 2, percent: ind, name: "India", shortName: "IND" },
  team2: { id: 10, percent: wi, name: "West Indies", shortName: "WI" },
  lastUpdatedOver: 8.4,
});
const CI = (inningNumber, oversUnique, winProbability, extra = {}) => ({
  at: 1, cricinfoId: 1529230, startTime: "2026-10-06T13:30:00.000Z",
  teams: [{ id: 4, name: "West Indies" }, { id: 6, name: "India" }],
  predictions: { inningNumber, oversUnique, score: 164, winProbability }, ...extra,
});
const LOG = {
  matchId: "151565", title: "West Indies vs India", format: "T20", team1Id: "10", team2Id: "2", outcome: "team2",
  endedAt: Date.parse("2026-10-06T16:41:00Z"),
  points: [
    { innings: 1, over: 9, score: 76, wickets: 3, p: 0.206 },
    { innings: 2, over: 9, score: 94, wickets: 2, p: 0.05 },
    { innings: 2, over: 14, score: 164, wickets: 2, p: 0.03 },
  ],
  cricbuzz: [{ innings: 1, over: 9, at: 1, raw: CB(67, 33) }, { innings: 2, over: 9, at: 2, raw: CB(88, 12) }],
};

test("Cricbuzz's figure is read for the log's team1 by id, not by Cricbuzz's own team order", () => {
  assert.equal(cricbuzzForTeam1(CB(67, 33), "10"), 0.33);
  assert.equal(cricbuzzForTeam1(CB(67, 33), "2"), 0.67);
  // Neither side is the log's team1, a percent that is not one, or text that is not JSON: no value.
  assert.equal(cricbuzzForTeam1(CB(67, 33), "99"), null);
  assert.equal(cricbuzzForTeam1(CB(670, 33), "2"), null);
  assert.equal(cricbuzzForTeam1("{not json", "2"), null);
});

test("Cricinfo's figure is the batting side's, so it flips for the chase", () => {
  // `oversUnique` 8.06 is the sixth ball of the ninth over: nine overs bowled.
  const samples = [CI(1, 8.06, 44.28), CI(1, 9.04, 47.34), CI(2, 8.05, 87.0), CI(2, 9.02, 86.5)];
  // First innings: West Indies are batting, and they are team1.
  assert.ok(Math.abs(cricinfoForTeam1(samples, 1, 9) - 0.4428) < 1e-9);
  // The chase: India are batting, so team1's chance is the rest. 8.05 is 8.83 overs, 9.02 is 9.33.
  assert.ok(Math.abs(cricinfoForTeam1(samples, 2, 9) - (1 - 0.87)) < 1e-9);
  // No sample within half an over: no value, rather than a figure from another moment.
  assert.equal(cricinfoForTeam1(samples, 2, 14), null);
  assert.equal(cricinfoForTeam1(samples, 1, 3), null);
});

test("a Cricinfo match joins a log on both team names and the start time, or not at all", () => {
  const ours = [CI(1, 9.0, 44)];
  assert.equal(cricinfoFor(LOG, ours).length, 1, "an older log is joined on its title and the time it ended");
  assert.equal(cricinfoFor({ ...LOG, teams: [{ id: "2", name: "India" }, { id: "10", name: "West Indies" }], startMs: Date.parse("2026-10-06T13:30:00Z") }, ours).length, 1);
  // The same fixture a week later, and a different opponent.
  assert.equal(cricinfoFor(LOG, [CI(1, 9.0, 44, { startTime: "2026-10-13T13:30:00.000Z" })]).length, 0);
  assert.equal(cricinfoFor(LOG, [CI(1, 9.0, 44, { teams: [{ name: "West Indies" }, { name: "Indonesia" }] })]).length, 0);
  assert.equal(cricinfoFor({ ...LOG, title: "no sides here" }, ours).length, 0);
});

test("the report compares on the points both published, and says when that is one match", () => {
  const joined = withRivals(LOG, [CI(1, 9.0, 44.28), CI(2, 9.0, 87.0)]);
  assert.deepEqual(joined.points.map((p) => [p.cb ?? null, p.ci == null ? null : Math.round(p.ci * 1000) / 1000]), [[0.33, 0.443], [0.12, 0.13], [null, null]]);
  const text = report([LOG], { rounds: 50, cricinfo: [CI(1, 9.0, 44.28), CI(2, 9.0, 87.0)] });
  assert.match(text, /ours \(vs Cricbuzz\)\s+1 matches\s+2 points/);
  assert.match(text, /ours \(vs Cricinfo\)\s+1 matches\s+2 points/);
  assert.match(text, /One match shows who was most confident/);
});
