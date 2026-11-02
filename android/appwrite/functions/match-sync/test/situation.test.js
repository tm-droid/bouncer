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

// ── triggers read from the state of the match, with its context ─────────────────────────────────

const { stateTrigger } = await import("../src/situation.js");

const liveDetail = (over = {}) => ({
  batsmen: [{ name: "Shai Hope", runs: "48", balls: "61" }, { name: "Keacy Carty", runs: "20", balls: "30" }],
  score: "150/2 (25)",
  team1Score: "352/7",
  team1Name: "IND",
  situation: { inningsId: 2, battingTeamName: "WI", bowlingTeamName: "IND", target: 353, runsNeeded: 203 },
  ...over,
});
const matchContext = {
  format: "ODI",
  venue: { name: "Eden Gardens", known: true, matches: 20, typicalFirstInnings: 271 },
  teams: [{ members: [{ name: "Shai Hope", record: { innings: 131, runs: 5210, average: 48.2 } }] }, { members: [] }],
};
const withDelivery = () => {
  const state = newSituationState();
  state.recent.push({ innings: 2, over: 24, ts: 1000, legal: true, event: "RUN" });
  return state;
};

test("a batter crossing fifty is a milestone; a batter first seen past it is not", () => {
  const state = withDelivery();
  assert.equal(stateTrigger(state, { detail: liveDetail(), meta: null, context: matchContext }), null, "the first sighting only records");
  const reached = liveDetail({ batsmen: [{ name: "Shai Hope", runs: "52", balls: "64" }, { name: "Keacy Carty", runs: "20", balls: "30" }] });
  const t = stateTrigger(state, { detail: reached, meta: null, context: matchContext });
  assert.deepEqual({ type: t.type, player: t.player, mark: t.mark }, { type: "milestone", player: "Shai Hope", mark: 50 });

  const cold = withDelivery();
  assert.equal(stateTrigger(cold, { detail: reached, meta: null, context: matchContext }), null, "52 on a cold container is not a fifty being reached");
  assert.equal(stateTrigger(cold, { detail: reached, meta: null, context: matchContext }), null, "and nothing fires while the score stands still");
});

test("a milestone's facts carry the word that licenses it, and the batter's recorded figures", () => {
  const trigger = { type: "milestone", player: "Shai Hope", mark: 50, innings: 2, over: 24, ts: 1000 };
  const detail = liveDetail({ batsmen: [{ name: "Shai Hope", runs: "52", balls: "64" }] });
  const facts = situationFacts(trigger, detail, matchContext);
  assert.match(facts, /Event: Shai Hope has reached a half-century \(fifty\)\./);
  assert.match(facts, /Shai Hope: 52 runs off 64 balls\./);
  assert.match(facts, /Shai Hope in 131 recorded one-day innings: 5210 runs at an average of 48\.2\./);

  assert.ok(validSituationLine("Shai Hope brings up his half-century off 64 balls, and West Indies need 203 more.".replace("West Indies", "WI"), facts, "Shai Hope"));
  assert.equal(validSituationLine("Shai Hope reaches a superb century off 64 balls for WI today.", facts, "Shai Hope"), null, "a century is not in these facts");
  assert.equal(validSituationLine("Shai Hope reaches fifty, the best of his career so far for WI.", facts, "Shai Hope"), null, "the archive is not a career");
  assert.equal(situationFacts(trigger, detail, null).includes("recorded"), false, "no context, no record claimed");
});

test("without a milestone trigger the old ban on milestone words still holds", () => {
  const facts = situationFacts({ type: "streak", player: "Shai Hope", sixes: 2 }, liveDetail(), matchContext);
  assert.equal(validSituationLine("Shai Hope launches two sixes on his way to a fifty for WI.", facts, "Shai Hope"), null);
});

test("a partnership passing a hundred fires once, and waits its turn behind another line", () => {
  const state = withDelivery();
  const at = (runs, balls) => ({ partnership: { runs, balls } });
  assert.equal(stateTrigger(state, { detail: liveDetail(), meta: at(96, 110), context: null }), null);
  const t = stateTrigger(state, { detail: liveDetail(), meta: at(101, 114), context: null });
  assert.deepEqual({ type: t.type, runs: t.runs, balls: t.balls, player: t.player, partner: t.partner },
    { type: "partnership", runs: 101, balls: 114, player: "Shai Hope", partner: "Keacy Carty" });
  assert.equal(stateTrigger(state, { detail: liveDetail(), meta: at(104, 117), context: null }), null, "already past the mark");

  const busy = withDelivery();
  busy.ballsSinceLine = 3;
  stateTrigger(busy, { detail: liveDetail(), meta: at(45, 50), context: null });
  assert.equal(stateTrigger(busy, { detail: liveDetail(), meta: at(52, 58), context: null }), null, "inside the cooldown");
  const facts = situationFacts(t, liveDetail(), null);
  assert.match(facts, /Shai Hope and Keacy Carty have put on 101 runs together off 114 balls/);
});

test("a first innings ending at a ground the archive knows is set against its usual score", () => {
  const state = withDelivery();
  const first = liveDetail({ situation: { inningsId: 1 }, team1Score: "340/6" });
  assert.equal(stateTrigger(state, { detail: first, meta: null, context: matchContext }), null);
  const t = stateTrigger(state, { detail: liveDetail(), meta: null, context: matchContext });
  assert.deepEqual({ type: t.type, team: t.team, total: t.total, typical: t.typical, innings: t.innings },
    { type: "par", team: "IND", total: 352, typical: 271, innings: 1 });
  const facts = situationFacts(t, liveDetail(), matchContext);
  assert.equal(facts, "Event: IND have finished their innings on 352.\nUsual first-innings score in the 20 recorded one-day matches at Eden Gardens: 271.");
  assert.ok(validSituationLine("IND finish on 352, well above the usual 271 at Eden Gardens.", facts, "IND"));
  assert.equal(validSituationLine("IND finish on 352, the highest total ever at Eden Gardens.", facts, "IND"), null);

  const unknown = withDelivery();
  stateTrigger(unknown, { detail: first, meta: null, context: { ...matchContext, venue: { name: "Somewhere", known: false } } });
  assert.equal(stateTrigger(unknown, { detail: liveDetail(), meta: null, context: { ...matchContext, venue: { name: "Somewhere", known: false } } }), null, "no usual score, no line");
  const cold = withDelivery();
  assert.equal(stateTrigger(cold, { detail: liveDetail(), meta: null, context: matchContext }), null, "a container that never saw the first innings says nothing");
});
