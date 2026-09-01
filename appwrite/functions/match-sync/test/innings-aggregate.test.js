import test from "node:test";
import assert from "node:assert/strict";
import { teamInningsSides } from "../src/cricbuzz.js";

// `inningsScoreList` as Cricbuzz publishes it: one entry per innings, flat, in innings order.
const inn = (inningsId, batTeamName, score, wickets, overs) => ({
  inningsId, batTeamName, score, wickets, overs,
});

test("a limited-overs match keeps the shape it always had", () => {
  const sides = teamInningsSides([
    inn(1, "India", 248, 3, 42.3),
    inn(2, "England", 187, 8, 35.1),
  ], "ODI");
  assert.deepEqual(sides, [
    { name: "India", score: "248/3", overs: "42.3" },
    { name: "England", score: "187/8", overs: "35.1" },
  ]);
});

test("an all-out limited-overs innings still shows its wickets", () => {
  const [india] = teamInningsSides([inn(1, "India", 229, 10, 48.4)], "T20");
  assert.equal(india.score, "229/10");
});

test("a Test side that has batted twice carries both innings", () => {
  const sides = teamInningsSides([
    inn(1, "India", 266, 10, 89.2),
    inn(2, "England", 214, 10, 71.4),
    inn(3, "India", 83, 2, 25.1),
  ], "TEST");
  // Indexing the flat list as [0]/[1] dropped this third entry entirely, which is the bug.
  assert.deepEqual(sides[0], { name: "India", score: "266 & 83/2", overs: "25.1" });
  assert.deepEqual(sides[1], { name: "England", score: "214", overs: "71.4" });
});

test("a fourth innings lands on the side that is batting it", () => {
  const sides = teamInningsSides([
    inn(1, "Australia", 400, 8, 120.0),
    inn(2, "South Africa", 250, 10, 88.5),
    inn(3, "Australia", 180, 4, 45.2),
    inn(4, "South Africa", 120, 3, 33.4),
  ], "FC");
  assert.equal(sides[0].score, "400/8 & 180/4");
  assert.equal(sides[1].score, "250 & 120/3");
  assert.equal(sides[1].overs, "33.4");
});

test("the side order is first-innings side first, and both are named", () => {
  const sides = teamInningsSides([
    inn(1, "England", 300, 10, 95.0),
    inn(2, "India", 40, 1, 12.0),
  ], "TEST");
  // The device needs the names: its own team order is Cricbuzz's listing, not innings order.
  assert.deepEqual(sides.map((s) => s.name), ["England", "India"]);
});

test("no innings yet yields no sides", () => {
  assert.deepEqual(teamInningsSides([], "TEST"), []);
});
