import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

import {
  asCricbuzzLine, ballKey, cricinfoMatchUrl, disagreement, joinCricinfoMatch, joinName, labelsInGap, parseCricinfoMatchPage, slimMatch,
} from "../src/cricinfo.js";
import { parseBallOutcome, resetThrottleState } from "../src/cricbuzz.js";
import { classifyEvent } from "../src/llm.js";
import {
  CRICINFO_FILL_SUFFIX,
  __backlogEntries,
  __resetBacklog,
  __resetCricinfoGate,
  __resetSuperseded,
  __setBatchFn,
  __setCricinfoList,
  __setVerifyBatchFn,
  cricinfoFills,
  cricinfoMatchStep,
  fillOverHistory,
  isEcho,
  resetRewriteBatcher,
  secondReportNote,
  syncMatch,
} from "../src/main.js";

/**
 * Cricinfo is a second source for a ball Cricbuzz also describes, and the only source for a ball
 * Cricbuzz skipped. Both uses turn on the two sites meaning the same delivery by the same label,
 * so the fixture is a real page (India v West Indies, 2026-10-06, trimmed to the fields read) and
 * the Cricbuzz lines beside it are that match's own.
 */

const FIXTURE = fs.readFileSync(new URL("./fixtures/cricinfo-commentary.html", import.meta.url), "utf8");
const page = parseCricinfoMatchPage(FIXTURE);
const ballAt = (label) => page.balls.find((b) => b.label === label);
const silent = () => {};
const START = Date.parse("2026-10-06T13:30:00.000Z");
const LISTED = slimMatch({
  objectId: 1529230, slug: "india-vs-west-indies-1st-t20i", series: { slug: "west-indies-in-india-2026-27", objectId: 1529215 },
  state: "LIVE", startTime: "2026-10-06T13:30:00.000Z", hasCommentary: true, ballByBallSource: "feedback",
  teams: [{ team: { longName: "West Indies", abbreviation: "WI" } }, { team: { longName: "India", abbreviation: "IND" } }],
});
const SUMMARY = { startDate: String(START), teams: [{ name: "India" }, { name: "West Indies" }] };

test("a page yields its deliveries oldest first, labelled the way Cricbuzz labels them", () => {
  assert.equal(page.balls.length, 8);
  assert.deepEqual(page.balls.map((b) => b.label), ["17.6", "18.1", "18.2", "18.3", "18.4", "18.5", "18.6", "19.1"]);
  const wicket = ballAt("18.5");
  assert.equal(wicket.wicket, true);
  assert.equal(wicket.dismissal, "c Naman Dhir b Yadav");
  assert.equal(ballAt("18.2").extra, "BYE");
  assert.ok(!/[<\[\]]/.test(page.balls.map((b) => b.text).join(" ")), "no markup and no bracket reaches a source line");
});

test("every completed over of the innings is on the page, and an over in progress is not", () => {
  assert.equal(page.overs.length, 19);
  assert.deepEqual(page.overs.at(-1), { innings: 1, over: 19, runs: 11, score: 171, wickets: 9, team: "WI" });
});

test("a match joins on both sides and the start time, in either listing order", () => {
  assert.equal(joinCricinfoMatch(SUMMARY, [LISTED])?.objectId, "1529230");
  assert.equal(joinName("India Under-19s"), joinName("India U19"));
  assert.equal(joinName("United States of America"), joinName("USA"));
  // The same two sides a day later is another match.
  assert.equal(joinCricinfoMatch({ ...SUMMARY, startDate: String(START + 24 * 3600 * 1000) }, [LISTED]), null);
  assert.equal(joinCricinfoMatch({ ...SUMMARY, teams: [{ name: "India A" }, { name: "West Indies" }] }, [LISTED]), null);
  // Two candidates is not a guess between two.
  assert.equal(joinCricinfoMatch(SUMMARY, [LISTED, { ...LISTED, objectId: "9" }]), null);
});

test("a page address is built only from slugs and ids of the shape Cricinfo gives them", () => {
  assert.equal(
    cricinfoMatchUrl(LISTED, "ball-by-ball-commentary"),
    "https://www.espncricinfo.com/series/west-indies-in-india-2026-27-1529215/india-vs-west-indies-1st-t20i-1529230/ball-by-ball-commentary",
  );
  assert.equal(cricinfoMatchUrl({ ...LISTED, slug: "x/../../admin" }, "ball-by-ball-commentary"), null);
  assert.equal(cricinfoMatchUrl(LISTED, "anything-else"), null);
});

test("the two sites agree on a ball when bowler, batter, runs and wicket all match", () => {
  const cb = (clean) => ({ clean, runs: parseBallOutcome(clean).runs, wicket: classifyEvent(clean) === "WICKET" });
  assert.equal(disagreement(cb("Mayank Yadav to Sherfane Rutherford, SIX, a slower one"), ballAt("18.3")), null);
  // Cricinfo's header is a short name: "Arshdeep" for Arshdeep Singh.
  assert.equal(disagreement(cb("Arshdeep Singh to Sherfane Rutherford, FOUR, overpitched"), ballAt("17.6")), null);
  assert.equal(disagreement(cb("Mayank Yadav to Sherfane Rutherford, FOUR, a slower one"), ballAt("18.3")), "runs");
  assert.equal(disagreement(cb("Mayank Yadav to Akeal Hosein, SIX, a slower one"), ballAt("18.3")), "batter");
  assert.equal(disagreement(cb("Mayank Yadav to Sherfane Rutherford, no run, defended"), ballAt("18.5")), "wicket");
});

test("a Cricinfo delivery written as a Cricbuzz line reads the same through the outcome parsers", () => {
  const wicket = asCricbuzzLine(ballAt("18.5"));
  assert.equal(classifyEvent(wicket), "WICKET");
  assert.match(wicket, /^Mayank Yadav to Rutherford, out caught, c Naman Dhir b Yadav, /);
  assert.deepEqual(parseBallOutcome(asCricbuzzLine(ballAt("18.2"))), { runs: 1, kind: "BYE" });
  assert.equal(classifyEvent(asCricbuzzLine(ballAt("18.3"))), "SIX");
  assert.equal(asCricbuzzLine({ ...ballAt("18.3"), extra: "WIDE" }), null);
});

test("a hole spans the target over's earlier balls and whole overs between, never the source over's tail", () => {
  assert.deepEqual(labelsInGap("12.5", "13.4"), ["13.1", "13.2", "13.3"]);
  assert.deepEqual(labelsInGap("3.1", "3.4"), ["3.2", "3.3"]);
  assert.equal(labelsInGap("12.5", "14.2").length, 7);
  assert.deepEqual(labelsInGap("12.5", "12.6"), []);
  assert.deepEqual(labelsInGap("Ball 71", "Ball 73"), []);
});

test("a second report is attached only when Cricinfo describes the same event", () => {
  const cricinfo = { balls: new Map(page.balls.map((b) => [ballKey(b.innings, b.label, b.extra), b])) };
  const note = secondReportNote({ clean: "Mayank Yadav to Sherfane Rutherford, SIX, a slower one", ball: "18.3", innings: 1, cricinfo, matchId: "1" });
  assert.match(note, /^ \[Second report: .+\]$/);
  assert.equal(secondReportNote({ clean: "Mayank Yadav to Sherfane Rutherford, FOUR, a slower one", ball: "18.3", innings: 1, cricinfo, matchId: "1" }), "");
  assert.equal(secondReportNote({ clean: "Mayank Yadav to Sherfane Rutherford, SIX, a slower one", ball: "18.3", innings: 2, cricinfo, matchId: "1" }), "");
  // A wide shares its label with the ball after it and is never joined.
  assert.equal(secondReportNote({ clean: "Mayank Yadav to Sherfane Rutherford, wide", ball: "18.3", innings: 1, cricinfo, matchId: "1" }), "");
  assert.equal(secondReportNote({ clean: "x to y, SIX", ball: "18.3", innings: 1, cricinfo: null, matchId: "1" }), "");
});

test("a line that carries the second report whole is an echo", () => {
  const source = "Bumrah to Root, no run, on a length [Second report: Back of a length outside off, he shoulders arms and lets it go]";
  assert.equal(isEcho("18.5: Back of a length outside off, he shoulders arms and lets it go.", source), true);
  assert.equal(isEcho("18.5: Root leaves a ball angled across him.", source), false);
});

test("Cricinfo's overs fill only what the history lacks", () => {
  const history = [{ innings: 1, over: 18, runs: 99, score: 999, wickets: 8, team: "WI", p: 0.4 }];
  const filled = fillOverHistory(history, page.overs);
  assert.equal(filled.length, 19);
  assert.deepEqual(filled.find((e) => e.over === 18), history[0]);
  assert.equal(fillOverHistory(history, []), history);
});

test("a refused page stops every Cricinfo request for an hour; another failure stops only that match", async () => {
  __resetCricinfoGate();
  __setCricinfoList([LISTED]);
  let calls = 0;
  const failing = (status) => async () => { calls++; return { ok: false, reason: `http ${status}`, status, bytes: 10 }; };
  const ok = async () => { calls++; return { ok: true, status: 200, bytes: 10, ...page }; };
  const step = (fetchMatch, now, matchId = "151565") => cricinfoMatchStep({ matchId, summary: SUMMARY, log: silent, error: silent, now, fetchMatch });

  assert.equal((await step(ok, 1000)).balls.size, 8);
  // A 404 is that page's own: the balls already held stay usable and only this match waits.
  assert.equal((await step(failing(404), 2000)).balls.size, 8);
  await step(ok, 3000);
  assert.equal(calls, 2, "the failed match is not asked again inside the hour");
  await step(ok, 3000, "999");
  assert.equal(calls, 3, "another match still is");

  await step(failing(403), 4000, "999");
  await step(ok, 5000, "777");
  assert.equal(calls, 4, "a refusal stops everything");
  __resetCricinfoGate();
});

test("a match Cricinfo does not list, or has no commentary on, is left alone", async () => {
  __resetCricinfoGate();
  __setCricinfoList([{ ...LISTED, hasCommentary: false }]);
  const never = async () => { throw new Error("must not fetch"); };
  assert.equal(await cricinfoMatchStep({ matchId: "1", summary: SUMMARY, log: silent, error: silent, fetchMatch: never }), null);
  __setCricinfoList([]);
  assert.equal(await cricinfoMatchStep({ matchId: "1", summary: SUMMARY, log: silent, error: silent, fetchMatch: never }), null);
});

// ── Through syncMatch ────────────────────────────────────────────────────────

const realFetch = globalThis.fetch;
let restoreBatch;
let restoreVerify;
test.afterEach(() => {
  globalThis.fetch = realFetch;
  if (restoreBatch !== undefined) __setBatchFn(restoreBatch);
  if (restoreVerify !== undefined) __setVerifyBatchFn(restoreVerify);
  restoreBatch = restoreVerify = undefined;
  resetRewriteBatcher();
});

function livePage(items) {
  const mini = {
    batsmanStriker: { name: "Sherfane Rutherford", runs: "50", balls: "30" },
    inningsId: 1,
    overs: "18.4",
    batTeam: { teamScore: 160, teamWkts: 8 },
    matchScoreDetails: {
      matchFormat: "T20",
      inningsScoreList: [{ inningsId: 1, batTeamId: "1", batTeamName: "WI", score: 160, wickets: 8, overs: 18.4 }],
    },
    status: "India opt to bowl",
  };
  const push = (o) => `<script>self.__next_f.push([1,${JSON.stringify(JSON.stringify(o))}])</script>`;
  return `<html><body>${push(mini)}${push({ commentaryList: items })}</body></html>`;
}

function fakeDb() {
  const writes = [];
  return {
    writes,
    listDocuments: async () => ({ documents: [] }),
    updateDocument: async (_db, table, rowId, data) => { writes.push({ op: "update", table, rowId, data }); return { $id: rowId }; },
    createDocument: async (_db, table, rowId, data) => { writes.push({ op: "create", table, rowId, data }); return { $id: rowId }; },
    deleteDocument: async (_db, table, rowId) => { writes.push({ op: "delete", table, rowId }); return {}; },
  };
}

function reset({ failChecks = false } = {}) {
  resetThrottleState();
  __resetBacklog();
  __resetSuperseded();
  __setCricinfoList([]);
  const seen = [];
  restoreBatch = __setBatchFn(async (items, opts) => {
    if (opts.used) opts.used.provider = "gemini-flash-lite";
    seen.push(...items);
    return new Map(items.map((i) => [i.key, "Rutherford meets it and the ball runs away."]));
  });
  restoreVerify = __setVerifyBatchFn(async (items) => ({ verdicts: new Map(items.map((i) => [i.key, !failChecks])), available: true }));
  return seen;
}

const item = (label, ts, commText) => {
  const [over, ball] = label.split(".").map(Number);
  return { commText, timestamp: ts, ballMetric: label, inningsId: 1, overNumber: over, ballNbr: ball };
};
const cricinfoOf = (labels) => async () => ({
  balls: new Map(page.balls.filter((b) => labels.includes(b.label)).map((b) => [ballKey(b.innings, b.label, b.extra), b])),
  overs: [],
});

async function sync(matchId, items, db, cricinfoFor) {
  globalThis.fetch = async () => new Response(livePage(items), { status: 200, headers: { "content-type": "text/html" } });
  await syncMatch({
    db, matchId, title: "West Indies vs India", commentaryCap: 0, storeCommentary: true,
    alreadyFinalised: false, model: null, log: silent, error: silent, alerts: [], cricinfoFor,
  });
  return db.writes.filter((w) => w.table === "match-commentary" && w.op === "create");
}

test("a ball both sites describe reaches the writer with both, and is stored under the writer's name", async () => {
  const seen = reset();
  const rows = await sync("ci-1", [
    item("18.3", 1000, "Mayank Yadav to Sherfane Rutherford, SIX, can't bowl there"),
    item("18.4", 2000, "Mayank Yadav to Sherfane Rutherford, no run"),
  ], fakeDb(), cricinfoOf(["18.3", "18.4"]));
  assert.equal(rows.length, 2);
  assert.ok(seen.every((i) => i.text.includes(" [Second report: ")), "both deliveries carry Cricinfo's text");
  // 18.4 is a bare scorer's line on Cricbuzz; with a description from Cricinfo it is written, not templated.
  assert.deepEqual(rows.map((r) => r.data.provider), ["gemini-flash-lite", "gemini-flash-lite"]);
  assert.ok(rows.every((r) => !r.data.commentary.includes("Second report")));
});

test("without Cricinfo nothing changes: a bare line is templated and nothing is appended", async () => {
  const seen = reset();
  const rows = await sync("ci-2", [
    item("18.3", 1000, "Mayank Yadav to Sherfane Rutherford, SIX, can't bowl there"),
    item("18.4", 2000, "Mayank Yadav to Sherfane Rutherford, no run"),
  ], fakeDb(), async () => null);
  assert.deepEqual(rows.map((r) => r.data.provider), ["gemini-flash-lite", "template"]);
  assert.ok(seen.every((i) => !i.text.includes("Second report")));
});

test("a ball Cricbuzz skipped is written from Cricinfo's, in its place, and marked", async () => {
  reset();
  const rows = await sync("ci-3", [
    item("18.1", 1000, "Mayank Yadav to Akeal Hosein, FOUR, lofts through the line"),
    item("18.5", 5000, "Mayank Yadav to Sherfane Rutherford, out Caught by Naman Dhir!! In the slot"),
  ], fakeDb(), cricinfoOf(["18.2", "18.3", "18.4"]));
  assert.deepEqual(rows.map((r) => r.data.ball), ["18.1", "18.2", "18.3", "18.4", "18.5"]);
  const filled = rows.filter((r) => r.data.provider.endsWith(CRICINFO_FILL_SUFFIX));
  assert.deepEqual(filled.map((r) => r.data.ball), ["18.2", "18.3", "18.4"]);
  assert.deepEqual(filled.map((r) => r.data.sort_key), [1001, 1002, 1003]);
  assert.equal(filled.find((r) => r.data.ball === "18.3").data.event, "SIX");
  assert.equal(filled.find((r) => r.data.ball === "18.2").data.extra, "BYE");
  assert.ok(filled.every((r) => r.data.ai_generated === true));
});

test("a filled ball with no verified line is held like any other, and keeps its mark", async () => {
  reset({ failChecks: true });
  const rows = await sync("ci-4", [
    item("18.1", 1000, "Mayank Yadav to Akeal Hosein, FOUR, lofts through the line"),
    item("18.3", 5000, "Mayank Yadav to Sherfane Rutherford, SIX, a slower one"),
  ], fakeDb(), cricinfoOf(["18.2"]));
  assert.equal(rows.length, 0);
  const held = __backlogEntries().find((e) => e.ball === "18.2");
  assert.equal(held.fill, true);
  assert.equal(held.ts, 1001);
});

test("a label already stored is not a hole when Cricbuzz republishes around it", async () => {
  reset();
  // 18.2 and 18.3 arrive from Cricbuzz and are stored…
  await sync("ci-6", [
    item("18.2", 2000, "Mayank Yadav to Akeal Hosein, byes, 1 run, down the leg side"),
    item("18.3", 3000, "Mayank Yadav to Sherfane Rutherford, SIX, a slower one"),
  ], fakeDb(), async () => null);
  const cricinfo = { balls: new Map(page.balls.map((b) => [ballKey(b.innings, b.label, b.extra), b])) };
  const fresh = [
    { timestamp: 1000, ballMetric: { label: "18.1", over: 18, ball: 1 }, innings: 1 },
    { timestamp: 5000, ballMetric: { label: "18.4", over: 18, ball: 4 }, innings: 1 },
  ];
  assert.deepEqual(cricinfoFills({ matchId: "ci-5", fresh, cricinfo, fallbackInnings: 1 }).map((f) => f.ballMetric.label), ["18.2", "18.3"]);
  // …so 18.1 republished beside 18.4 leaves nothing to fill for that match.
  assert.deepEqual(cricinfoFills({ matchId: "ci-6", fresh, cricinfo, fallbackInnings: 1 }), []);
  // An innings boundary and an unlabelled item are not holes.
  assert.deepEqual(cricinfoFills({ matchId: "ci-5", fresh: [fresh[0], { ...fresh[1], innings: 2 }], cricinfo, fallbackInnings: 1 }), []);
});
