import test from "node:test";
import assert from "node:assert/strict";

import {
  BOUNDARY_STREAK,
  COLLAPSE_WICKETS,
  COOLDOWN_BALLS,
  SECURED,
  SIX_STREAK,
  SWING,
  deliveryOf,
  detectTrigger,
  mustMentionOf,
  newSituationState,
  situationFacts,
  validSituationLine,
} from "../src/situation.js";

const NOW = 1_791_000_000_000;
let tick = 0;
/** One delivery, a second after the last. `over.ball` labels; `what` is Cricbuzz's outcome clause. */
function ball(label, what, { bowler = "Shivam Dube", batter = "Hasan Nawaz", innings = 2 } = {}) {
  return deliveryOf(`${bowler} to ${batter}, ${what}`, { ball: label, innings, ts: NOW - 60_000 + ++tick * 1000 });
}
const feed = (state, balls, opts = {}) => detectTrigger(state, balls, { now: NOW, ...opts });

// The mirror is deliberate, as in slow-tier.test.js: a test that imports the constant it checks
// cannot catch a wrong constant.
test("the thresholds are the ones the user asked for", () => {
  assert.equal(SIX_STREAK, 2);
  assert.equal(BOUNDARY_STREAK, 3);
  assert.equal(COLLAPSE_WICKETS, 3);
  assert.equal(COOLDOWN_BALLS, 12);
  assert.equal(SWING, 0.15);
  assert.equal(SECURED, 0.9);
});

test("a quiet over triggers nothing", () => {
  const state = newSituationState();
  assert.equal(feed(state, [ball("5.1", "no run"), ball("5.2", "1 run"), ball("5.3", "FOUR"), ball("5.4", "no run")]), null);
});

test("two sixes in a row is a line about the batter", () => {
  const state = newSituationState();
  const t = feed(state, [ball("19.3", "no run"), ball("19.4", "SIX"), ball("19.5", "SIX")]);
  assert.equal(t.type, "streak");
  assert.equal(t.player, "Hasan Nawaz");
  assert.equal(t.sixes, 2);
  assert.equal(mustMentionOf(t), "Hasan Nawaz");
});

test("a streak is the batter's own: his partner's ball and a wide do not break it", () => {
  const state = newSituationState();
  const t = feed(state, [
    ball("8.1", "FOUR"),
    ball("8.2", "1 run", { batter: "Saim Ayub" }),
    ball("8.3", "wide"),
    ball("8.3", "FOUR"),
    ball("8.4", "FOUR"),
  ]);
  // Nawaz: four, then Ayub faces one, a wide, then Nawaz again: four, four — his last three balls.
  assert.equal(t.type, "streak");
  assert.equal(t.player, "Hasan Nawaz");
  assert.equal(t.boundaries, 3);
});

test("three boundaries off three balls faced is a streak", () => {
  const state = newSituationState();
  const t = feed(state, [ball("8.1", "FOUR"), ball("8.2", "wide"), ball("8.2", "FOUR"), ball("8.3", "FOUR")]);
  assert.equal(t.type, "streak");
  assert.equal(t.boundaries, 3);
});

test("two wickets in an over is a line about the bowler, and a run out is not his", () => {
  const strike = feed(newSituationState(), [ball("12.1", "out Bowled!!"), ball("12.2", "no run", { batter: "Usman Khan" }), ball("12.3", "out Lbw!!", { batter: "Usman Khan" })]);
  assert.equal(strike.type, "strike");
  assert.equal(strike.player, "Shivam Dube");
  const runOut = feed(newSituationState(), [ball("12.1", "out Bowled!!"), ball("12.3", "out Usman Khan Run Out!!", { batter: "Usman Khan" })]);
  assert.equal(runOut, null);
});

test("three wickets inside three overs is a collapse", () => {
  const state = newSituationState();
  const t = feed(state, [
    ball("10.2", "out Caught!!"),
    ball("11.4", "out Bowled!!", { bowler: "Axar Patel", batter: "A" }),
    ball("12.5", "out Lbw!!", { bowler: "Ravi Bishnoi", batter: "B" }),
  ]);
  assert.equal(t.type, "collapse");
  assert.equal(t.wickets, 3);
});

test("one passage of play gets one line", () => {
  const state = newSituationState();
  assert.ok(feed(state, [ball("19.4", "SIX"), ball("19.5", "SIX")]));
  state.ballsSinceLine = 0; // what main.js does when the line is accepted
  assert.equal(feed(state, [ball("19.6", "SIX")]), null);
});

test("a backlog republish is not something happening now", () => {
  const state = newSituationState();
  const old = (label) => deliveryOf("Shivam Dube to Hasan Nawaz, SIX", { ball: label, innings: 2, ts: NOW - 30 * 60_000 });
  assert.equal(feed(state, [old("3.1"), old("3.2")]), null);
});

test("a swing in win probability is the match turning, named for the side that gained", () => {
  const state = newSituationState();
  const teams = { team1: "IND", team2: "PAK" };
  assert.equal(feed(state, [ball("10.1", "1 run")], { prob: 0.7, ...teams }), null, "the first reading only seeds");
  assert.equal(feed(state, [ball("10.2", "1 run")], { prob: 0.62, ...teams }), null);
  const t = feed(state, [ball("10.3", "1 run")], { prob: 0.5, ...teams });
  assert.equal(t.type, "turn");
  assert.equal(t.team, "PAK");
});

test("a win being secured is said once, and not for a match first seen already decided", () => {
  const teams = { team1: "IND", team2: "PAK" };
  const seenLate = newSituationState();
  assert.equal(feed(seenLate, [ball("18.1", "1 run")], { prob: 0.97, ...teams }), null);
  assert.equal(feed(seenLate, [ball("18.2", "1 run")], { prob: 0.98, ...teams }), null);

  const state = newSituationState();
  feed(state, [ball("16.1", "1 run")], { prob: 0.84, ...teams });
  const t = feed(state, [ball("16.2", "no run")], { prob: 0.92, ...teams });
  assert.equal(t.type, "secured");
  assert.equal(t.team, "IND");
  assert.equal(feed(state, [ball("16.3", "no run")], { prob: 0.95, ...teams }), null);
});

// ── The line is checked against the facts in code ────────────────────────────

const detail = {
  score: "186/5 (19.5)",
  situation: { battingTeamName: "PAK", bowlingTeamName: "IND", target: 212, runsNeeded: 26 },
  batsmen: [{ name: "Hasan Nawaz", runs: "96", balls: "52" }],
  bowler: { name: "Shivam Dube", wickets: "1", runs: "38" },
};
const streak = { type: "streak", player: "Hasan Nawaz", sixes: 2, innings: 2, over: 19, ts: NOW };
const facts = situationFacts(streak, detail);

test("the facts carry the event and only figures the run holds", () => {
  assert.match(facts, /Hasan Nawaz has hit sixes off the last 2 balls faced/);
  assert.match(facts, /PAK 186\/5 \(19\.5\)/);
  assert.match(facts, /Target 212; 26 more needed/);
  assert.match(facts, /Hasan Nawaz: 96 runs off 52 balls/);
});

test("a line that stays inside the facts is kept", () => {
  for (const line of [
    "Hasan Nawaz is taking this chase on single-handedly, back-to-back sixes and PAK need 26 more.",
    "Two sixes in two balls from Nawaz, and suddenly PAK are back in this with 26 needed.",
  ]) assert.equal(validSituationLine(line, facts, "Hasan Nawaz"), line);
});

test("a line that adds a number, a name or a milestone is dropped", () => {
  for (const line of [
    "Hasan Nawaz launches two more sixes and PAK now need 20 from the last over.", // 20 is not a fact
    "Hasan Nawaz launches three sixes in a row to drag PAK back into the chase.", // three sixes
    "Hasan Nawaz takes Jasprit Bumrah apart with back-to-back sixes for PAK.", // Bumrah
    "Hasan Nawaz brings up a brilliant century with back-to-back sixes for PAK.", // no such fact
    "Pakistan are flying now with back-to-back sixes from the middle order.", // does not name him
    "19.5: Hasan Nawaz hits a six.", // a ball label is a delivery line
  ]) assert.equal(validSituationLine(line, facts, "Hasan Nawaz"), null, line);
});
