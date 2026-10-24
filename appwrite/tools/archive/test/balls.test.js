import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { BITS, CricbuzzBlockedError, ballsBlock, ballsOfOvers, batterProblems, crawlInnings, deliveriesOf, dropReposts, inningsProblems, overToken, reconcile, recordOf } from "../src/balls.js";
import { parseBallOutcome } from "../src/ball-outcome.js";
import { isEligible, probe, run } from "../backfill-balls.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const fixture = (n) => JSON.parse(fs.readFileSync(path.join(here, "fixtures", n), "utf8"));
const page1 = fixture("page-21646-2.json"); // 19.2 .. 16.3, newest first
const page2 = fixture("page-19317-2.json"); // 14.5 .. 12.2 with a wide and a six

const item = (t, text, extra = {}) => ({
  commType: "commentary", ballMetric: t, commText: text, event: ["none", "all"], timestamp: 1_000_000 + Math.round(t * 100),
  batsmanDetails: { playerId: 1, playerName: "Nabi" }, bowlerDetails: { playerId: 9, playerName: "Rankin" }, inningsId: 1, ...extra,
});
const conv = (items) => deliveriesOf(items.map((i) => recordOf(i)).filter(Boolean), new Map());

test("paging stops on an empty page and walks back by the oldest timestamp", async () => {
  const pages = [page1, page2, []];
  const asked = [];
  const st = { cursor: 9e15, done: 0, recs: [] };
  await crawlInnings({ st, fetchPage: async (c) => (asked.push(c), pages.shift()) });
  assert.equal(st.done, 1);
  assert.equal(asked.length, 3);
  assert.ok(asked[1] < asked[0] && asked[1] === Math.min(...page1.map((i) => i.timestamp)));
});

test("a repeated page is dropped by key and stops the walk", async () => {
  const st = { cursor: 9e15, done: 0, recs: [] };
  const asked = [];
  const pages = [page1, page1];
  // The second page repeats the first; the cursor moves nothing so the walk stops without doubling.
  await crawlInnings({ st, fetchPage: async (c) => (asked.push(c), pages.shift()) });
  const keys = st.recs.map((r) => `${r.ts}:${r.t}`);
  assert.equal(new Set(keys).size, keys.length);
  assert.equal(st.done, 1);
  assert.equal(st.recs.length, page1.filter((i) => i.ballMetric != null).length);
});

test("budget stops without finishing and resumes from the cursor", async () => {
  const st = { cursor: 9e15, done: 0, recs: [] };
  let n = 0;
  assert.equal(await crawlInnings({ st, canRequest: () => n < 1, fetchPage: async () => (n++, page1) }), "budget");
  assert.equal(st.done, 0);
  const cursor = st.cursor;
  await crawlInnings({ st, fetchPage: async (c) => (assert.equal(c, cursor), []) });
  assert.equal(st.done, 1);
});

test("fixture conversion: ball labels, four, six, wide, 2 runs", () => {
  const ds = conv(page1).map((o) => o.d);
  const at = (over, ball) => ds.find((d) => d[0] === over && d[1] === ball);
  assert.deepEqual(at(19, 2).slice(5, 8), [4, 0, 0]);
  assert.deepEqual(at(18, 4).slice(5, 8), [6, 0, 0]);
  assert.deepEqual(at(16, 6).slice(5, 8), [2, 0, 0]);
  const w = conv(page2).map((o) => o.d).filter((d) => d[7] & BITS.WIDE);
  assert.equal(w.length, 2);
  assert.deepEqual(w[0].slice(5, 8), [0, 1, 1]);
});

test("no-ball, bye, leg-bye, non-boundary and wicket", () => {
  const [nb, bye, lb, run4, wk] = conv([
    item(1.1, "A to B, no ball, FOUR", { timestamp: 1, event: ["four"] }),
    item(1.2, "A to B, byes, 2 runs", { timestamp: 2 }),
    item(1.3, "A to B, 1 lb", { timestamp: 3 }),
    item(1.4, "A to B, 4 runs", { timestamp: 4 }),
    item(1.5, "A to B, out Caught by C!!", { timestamp: 5, event: ["wicket", "all"] }),
  ]).map((o) => o.d);
  assert.deepEqual(nb.slice(5, 8), [4, 1, BITS.NOBALL]);
  assert.deepEqual(bye.slice(5, 8), [0, 2, BITS.BYE]);
  assert.deepEqual(lb.slice(5, 8), [0, 1, BITS.LEGBYE]);
  assert.deepEqual(run4.slice(5, 8), [4, 0, BITS.NON_BOUNDARY]);
  assert.deepEqual(wk[8], ["caught", wk[2]]);
});

test("the totals check flags a gap and a missing ball; partial lands in the file", () => {
  const items = [item(0.1, "A to B, 1 run", { timestamp: 1 }), item(0.2, "A to B, 4 runs", { timestamp: 2, event: ["four"] })];
  const ds = conv(items);
  assert.deepEqual(inningsProblems(ds, { score: "5", overs: "0.2" }), []);
  assert.match(inningsProblems(ds, { score: "9-1", overs: "0.2" }).join(), /runs 5 != total 9/);
  assert.match(inningsProblems(ds, { score: "5", overs: "0.3" }).join(), /legal balls 2 != overs 0.3/);
  const recs = items.map((i) => recordOf(i));
  const bad = ballsBlock([{ id: 1, recs }], [{ score: "9", overs: "0.2" }]);
  assert.equal(bad.balls.inn[0].partial, 1);
  assert.equal(ballsBlock([{ id: 1, recs }], [{ score: "5", overs: "0.2" }]).balls.inn[0].partial, undefined);
});

test("output holds no commentary text", () => {
  const recs = [...page1, ...page2].map((i) => recordOf(i)).filter(Boolean);
  const { balls } = ballsBlock([{ id: 1, recs }], [{ score: "1", overs: "1" }]);
  const strings = [];
  JSON.stringify(balls, (k, v) => (typeof v === "string" && strings.push(v), v));
  const longestName = Math.max(...balls.p.map((x) => x[0].length));
  assert.ok(strings.every((s) => s.length <= Math.max(longestName, 24)), strings.filter((s) => s.length > 24).join());
  assert.ok(!JSON.stringify(balls).includes("<b>"));
  assert.ok(balls.p.every(([n, id]) => n && /^\d*$/.test(id))); // batters carry an id only when the scorecard names them
});

test("vendored outcome parser equals match-sync's", async (t) => {
  const original = path.resolve(here, "../../../functions/match-sync/src/cricbuzz.js");
  if (!fs.existsSync(original)) return t.skip("match-sync is not beside this copy");
  const theirs = (await import(original)).parseBallOutcome;
  for (const s of ["A to B, no run", "A to B, 1 run", "A to B, FOUR", "A to B, SIX", "A to B, 1 wd", "A to B, wide", "A to B, leg byes, 1 run", "A to B, byes, FOUR", "A to B, no ball, 2 runs", "A to B, out Bowled!!", "A to B, 3 nb", "rubbish"]) {
    assert.deepEqual(parseBallOutcome(s), theirs(s), s);
  }
});

// ── CLI ──
const siteWith = (files) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "balls-"));
  fs.mkdirSync(path.join(dir, "m"));
  for (const f of files) fs.writeFileSync(path.join(dir, "m", `${f.id}.json`), JSON.stringify(f));
  return dir;
};
const matchFile = (id, extra = {}) => ({
  id, source: "cricbuzz-backfill", rows: [], other: "keep", meta: { startDate: String(Date.UTC(2019, 1, 21)), teams: [{ teamId: "96" }] },
  scorecard: { innings: [{ score: "5", overs: "0.2" }] }, ...extra,
});
const resp = (status, body) => ({ status, ok: status < 400, text: async () => JSON.stringify(body) });

test("eligibility: backfill by date, others only when AFG with neither rows nor balls", () => {
  assert.ok(isEligible(matchFile("1"), undefined));
  assert.ok(!isEligible(matchFile("1", { balls: {} }), undefined));
  assert.ok(!isEligible(matchFile("1", { meta: { startDate: String(Date.UTC(2016, 0, 1)) } }), undefined));
  assert.ok(isEligible(matchFile("2", { source: undefined }), undefined));
  assert.ok(!isEligible(matchFile("2", { source: undefined, rows: [{}] }), undefined));
  assert.ok(!isEligible(matchFile("2", { source: undefined, meta: { startDate: String(Date.UTC(2019, 1, 1)), teams: [{ teamId: "5" }] } }), undefined));
});

test("run writes balls without touching other fields, then a 403 stops and persists state", async () => {
  const site = siteWith([matchFile("10"), matchFile("11")]);
  const items = [item(0.2, "A to B, 4 runs", { event: ["four"], timestamp: 2000 }), item(0.1, "A to B, 1 run", { timestamp: 1000 })];
  let calls = 0;
  const fetchImpl = async (url) => {
    calls++;
    if (url.includes("/10/")) return resp(200, calls === 1 ? items : []);
    return resp(403, {});
  };
  const r = await run({ site, delayMs: 0, fetchImpl, log: () => {} });
  assert.equal(r.blocked, true);
  const f10 = JSON.parse(fs.readFileSync(path.join(site, "m/10.json"), "utf8"));
  assert.equal(f10.other, "keep");
  assert.equal(f10.balls.inn[0].partial, undefined);
  assert.equal(f10.balls.inn[0].b.length, 2);
  assert.ok(!fs.readFileSync(path.join(site, "m/11.json"), "utf8").includes('"balls"'));
  const state = JSON.parse(fs.readFileSync(path.join(site, "balls-state.json"), "utf8"));
  assert.equal(state.matches["10"].done, 1);
  assert.ok(!state.matches["11"].done);
  assert.equal(r.remaining, 1);
});

test("a 403 mid-innings keeps the records fetched so far and resumes", async () => {
  const site = siteWith([matchFile("20")]);
  const items = [item(0.2, "A to B, 4 runs", { event: ["four"], timestamp: 2000 }), item(0.1, "A to B, 1 run", { timestamp: 1000 })];
  let n = 0;
  const r1 = await run({ site, delayMs: 0, log: () => {}, fetchImpl: async () => (++n === 1 ? resp(200, items) : resp(429, {})) });
  assert.equal(r1.blocked, true);
  const st = JSON.parse(fs.readFileSync(path.join(site, "balls-state.json"), "utf8")).matches["20"].inn["1"];
  assert.equal(st.recs.length, 2);
  assert.equal(st.cursor, 1000);
  const r2 = await run({ site, delayMs: 0, log: () => {}, fetchImpl: async (u) => (assert.ok(u.endsWith("/1000")), resp(200, [])) });
  assert.equal(r2.blocked, false);
  assert.equal(JSON.parse(fs.readFileSync(path.join(site, "m/20.json"), "utf8")).balls.inn[0].b.length, 2);
});

test("max-requests budget ends the run cleanly", async () => {
  const site = siteWith([matchFile("30")]);
  const r = await run({ site, delayMs: 0, maxRequests: 1, log: () => {}, fetchImpl: async () => resp(200, [item(0.1, "A to B, 1 run", { timestamp: 500 })]) });
  assert.equal(r.requests, 1);
  assert.equal(r.blocked, false);
  assert.equal(r.remaining, 1);
});

test("probe: 0 on 200s, 2 on 403", async () => {
  assert.equal(await probe({ delayMs: 0, log: () => {}, fetchImpl: async () => resp(200, page1) }), 0);
  assert.equal(await probe({ delayMs: 0, log: () => {}, fetchImpl: async () => resp(403, {}) }), 2);
});

test("CricbuzzBlockedError carries the status", () => {
  assert.equal(new CricbuzzBlockedError(429).status, 429);
});

// Real pages of 17685 (UAE v AFG, 2016-12), text cut to the outcome clause.
const boundary = fixture("page-17685-boundary.json"); // last 3 items of innings 2, then innings 1's newest 7
const runout = fixture("page-17685-1-runout.json"); // over 16 of innings 1: 16.5 is a run out with "1 run completed"
const gap = fixture("page-17685-2-gap.json"); // overs 12-13 of innings 2: Cricbuzz has no 13.1 and no 13.3

test("walking innings 2 past its first ball runs into innings 1: foreign items are dropped and end the walk", async () => {
  const own = boundary.filter((i) => i.inningsId === 2);
  const other = boundary.filter((i) => i.inningsId === 1);
  assert.ok(own.length === 3 && other.length === 7);
  const pages = [own, other];
  const asked = [];
  const st = { cursor: 9e15, done: 0, recs: [] };
  const why = await crawlInnings({ st, innings: 2, fetchPage: async (c) => (asked.push(c), pages.shift()) });
  assert.equal(why, "foreign");
  assert.equal(st.done, 1);
  assert.equal(st.recs.length, 2); // 0.2 and 0.1; the separator carries no ball
  assert.deepEqual(st.recs.map((r) => r.t).sort(), [1, 2]);
  assert.equal(asked.length, 2);
});

test("a mixed page keeps only the asked innings", async () => {
  const st = { cursor: 9e15, done: 0, recs: [] };
  await crawlInnings({ st, innings: 1, fetchPage: async () => boundary.slice(0, 5) });
  assert.ok(st.recs.every((r) => r.t >= 190)); // innings 1's 19.x only
});

test("same timestamp key dedupes; two wides sharing over.ball are both kept", async () => {
  const wide = (ts) => item(7.5, "A to B, wide", { timestamp: ts });
  const st = { cursor: 9e15, done: 0, recs: [] };
  await crawlInnings({ st, innings: 1, fetchPage: async () => [wide(2000), wide(1000)] });
  assert.equal(st.recs.length, 2);
});

test("a run out with runs completed scores them to the striker", () => {
  const r = runout.filter((i) => i.ballMetric === 16.5).map((i) => recordOf(i))[0];
  assert.equal(r.runs, 1);
  assert.equal(r.kind, null);
  assert.ok(r.ev & 4);
  assert.equal(recordOf(item(1.1, "A to B, out Bowled!! B b A 3(2)", { event: ["wicket", "all"] })).runs, 0);
});

test("over separators become score checkpoints and the log says which balls and overs are off", async () => {
  const st = { cursor: 9e15, done: 0, recs: [] };
  await crawlInnings({ st, innings: 2, fetchPage: async () => gap });
  assert.deepEqual(st.cp, { 12: 79, 14: 106 });
  const ds = deliveriesOf(st.recs, new Map());
  const msg = inningsProblems(ds, { score: "106-3", overs: "20" }, { cp: st.cp }).join(" | ");
  assert.match(msg, /no commentary for ball 12\.6 13\.1 13\.3( \||$)/);
  assert.match(msg, /over 14: -6/);
});

test("batter runs must equal the scorecard's, joined by profile id then by name", () => {
  const people = [["Rohan Mustafa", "9835"], ["Shahzad", ""]];
  const ds = [{ d: [0, 1, 0, -1, -1, 4, 0, 0] }, { d: [0, 2, 0, -1, -1, 1, 0, 0] }, { d: [0, 3, 1, -1, -1, 2, 0, 0] }];
  const card = (a, b) => ({ batsmen: [
    { name: "Rohan Mustafa", runs: String(a), profileUrl: "https://www.cricbuzz.com/profiles/9835/rohan-mustafa" },
    { name: "Shahzad (wk)", runs: String(b), profileUrl: "" },
  ] });
  assert.deepEqual(batterProblems(ds, card(5, 2), people), []);
  assert.match(batterProblems(ds, card(6, 2), people).join(), /rohan mustafa 5 != 6/);
  assert.match(batterProblems(ds, card(5, 3), people).join(), /shahzad 2 != 3/);
  assert.match(batterProblems(ds, { batsmen: [{ name: "Other", runs: "1", profileUrl: "https://x/profiles/77/o" }] }, people).join(), /not on the scorecard/);
  // Wired in: the same deliveries pass the totals but the innings goes partial on a batter mismatch.
  const recs = [item(0.1, "A to Nabi, 1 run"), item(0.2, "A to Nabi, FOUR", { event: ["four", "all"] })].map((i) => recordOf(i));
  const ok = { score: "5", overs: "0.2", batsmen: [{ name: "Nabi", runs: "5", profileUrl: "https://x/profiles/1/nabi" }] };
  assert.equal(ballsBlock([{ id: 1, recs }], [ok]).balls.inn[0].partial, undefined);
  const bad = { ...ok, batsmen: [{ name: "Nabi", runs: "4", profileUrl: "https://x/profiles/1/nabi" }] };
  assert.equal(ballsBlock([{ id: 1, recs }], [bad]).balls.inn[0].partial, 1);
});

test("the striker is the name in the line, not batsmanDetails (the batter after the ball)", () => {
  // Real 17685 innings 1: 0.1 "to Najeeb Tarakai, 1 run" carries batsmanDetails Mohammad Shahzad (next striker).
  const mk = (t, text, after) => item(t, text, { batsmanDetails: { playerId: after, playerName: "x" } });
  const recs = [
    mk(0.1, "Ahmed Raza to Najeeb Tarakai, 1 run", 3014),
    mk(0.2, "Ahmed Raza to Mohammad Shahzad, 1 run", 9973),
    mk(0.3, "Ahmed Raza to Najeeb Tarakai, FOUR", 9973),
  ].map((i) => recordOf(i));
  const card = { score: "6", overs: "0.3", batsmen: [
    { name: "Najeeb Tarakai", runs: "5", profileUrl: "https://www.cricbuzz.com/profiles/9973/najeeb-tarakai" },
    { name: "Mohammad Shahzad (wk)", runs: "1", profileUrl: "https://www.cricbuzz.com/profiles/3014/mohammad-shahzad-wk" },
  ] };
  const { balls, problems } = ballsBlock([{ id: 1, recs }], [card]);
  assert.deepEqual(problems, []);
  assert.deepEqual(balls.inn[0].b.map((d) => balls.p[d[2]][1]), ["9973", "3014", "9973"]);
});

test("a short commentary name resolves to the one scorecard batter it ends, exact name first", () => {
  const roster = [{ name: "Shahzad", runs: "1", profileUrl: "https://x/profiles/5/s" }, { name: "Rameez Shahzad (c)", runs: "1", profileUrl: "https://x/profiles/6/r" }];
  const recs = [item(0.1, "A to Shahzad, 1 run"), item(0.2, "A to Rameez Shahzad, 1 run"), item(0.3, "A to Stranger, no run")].map((i) => recordOf(i));
  const { balls } = ballsBlock([{ id: 1, recs }], [{ score: "2", overs: "0.3", batsmen: roster }]);
  assert.deepEqual(balls.inn[0].b.map((d) => balls.p[d[2]]), [["Shahzad", "5"], ["Rameez Shahzad", "6"], ["Stranger", ""]]);
});

test("an ambiguous short name is settled by who Cricbuzz put at the crease after the previous ball", () => {
  const batsmen = [
    { name: "Mohammad Shahzad", runs: "1", profileUrl: "https://x/profiles/10042/m" },
    { name: "Rameez Shahzad", runs: "1", profileUrl: "https://x/profiles/11568/r" },
  ];
  const recs = [
    item(0.1, "A to Rameez Shahzad, 1 run", { batsmanDetails: { playerId: 10042, playerName: "Mohammad Shahzad" } }),
    item(0.2, "A to Shahzad, 1 run", { batsmanDetails: { playerId: 11568, playerName: "Rameez Shahzad" } }),
  ].map((i) => recordOf(i));
  const { balls, problems } = ballsBlock([{ id: 1, recs }], [{ score: "2", overs: "0.2", batsmen }]);
  assert.deepEqual(problems, []);
  assert.deepEqual(balls.inn[0].b.map((d) => balls.p[d[2]][1]), ["11568", "10042"]);
});

test("a commentary name in initials resolves to the scorecard batter it abbreviates", () => {
  const batsmen = [
    { name: "Jason Mohammed", runs: "4", profileUrl: "https://x/profiles/8603/j" },
    { name: "Mahendra Singh Dhoni", runs: "1", profileUrl: "https://x/profiles/265/m" },
  ];
  const recs = [item(0.1, "A to J Mohammed, FOUR"), item(0.2, "A to MS Dhoni, 1 run"), item(0.3, "A to J Smith, no run")].map((i) => recordOf(i));
  const { balls, problems } = ballsBlock([{ id: 1, recs }], [{ score: "5", overs: "0.3", batsmen }]);
  assert.deepEqual(problems, []);
  assert.deepEqual(balls.inn[0].b.map((d) => balls.p[d[2]]), [["Jason Mohammed", "8603"], ["Mahendra Singh Dhoni", "265"], ["J Smith", ""]]);
});

test("a nickname resolves to the id Cricbuzz keeps on strike after his dot balls (19316: Andy -> Andrew Balbirnie)", () => {
  const batsmen = [
    { name: "Andrew Balbirnie", runs: "5", profileUrl: "https://x/profiles/6710/a" },
    { name: "Paul Stirling", runs: "0", profileUrl: "https://x/profiles/1114/p" },
  ];
  const andrew = { batsmanDetails: { playerId: 6710, playerName: "Andrew Balbirnie" } };
  const paul = { batsmanDetails: { playerId: 1114, playerName: "Paul Stirling" } };
  const recs = [
    item(0.1, "A to Andy Balbirnie, no run", andrew),
    item(0.2, "A to Andy Balbirnie, FOUR", andrew),
    item(0.3, "A to Andy Balbirnie, 1 run", paul),
    item(0.4, "A to Stirling, no run", paul),
  ].map((i) => recordOf(i));
  const { balls, problems } = ballsBlock([{ id: 1, recs }], [{ score: "5", overs: "0.4", batsmen }]);
  assert.deepEqual(problems, []);
  assert.deepEqual(balls.inn[0].b.map((d) => balls.p[d[2]][1]), ["6710", "6710", "6710", "1114"]);
});

test("one vote, or a split vote, does not rename a batter", () => {
  const batsmen = [
    { name: "Andrew Balbirnie", runs: "0", profileUrl: "https://x/profiles/6710/a" },
    { name: "Paul Stirling", runs: "0", profileUrl: "https://x/profiles/1114/p" },
  ];
  const bd = (id) => ({ batsmanDetails: { playerId: id } });
  const striker = (texts) => {
    const recs = texts.map(([t, id], k) => item(0.1 + k / 10, t, bd(id))).map((i) => recordOf(i));
    const { balls } = ballsBlock([{ id: 1, recs }], [{ score: "0", overs: "0.4", batsmen }]);
    return balls.p[balls.inn[0].b[0][2]];
  };
  assert.deepEqual(striker([["A to Andy Balbirnie, no run", 6710]]), ["Andy Balbirnie", ""]);
  const split = [["A to Andy Balbirnie, no run", 6710], ["A to Andy Balbirnie, no run", 6710], ["A to Andy Balbirnie, no run", 1114], ["A to Andy Balbirnie, no run", 1114]];
  assert.deepEqual(striker(split), ["Andy Balbirnie", ""]);
});

test("one player under two people entries (bowler 'Nabi' and batter 'Mohammad Nabi') is summed by id", () => {
  const people = [["Nabi", "3007"], ["Mohammad Nabi", "3007"]];
  const ds = [{ d: [0, 1, 0, -1, -1, 4, 0, 0] }, { d: [0, 2, 1, -1, -1, 9, 0, 0] }];
  const card = { batsmen: [{ name: "Mohammad Nabi", runs: "13", profileUrl: "https://x/profiles/3007/n" }] };
  assert.deepEqual(batterProblems(ds, card, people), []);
});

// Over summaries, read from Cricbuzz's own over separators (shapes measured on 103852, 109351,
// 148415 and 16933; the app's BallOutcome.kt pins the same token arithmetic).
const sep = (n, summary, ts) => ({ commType: "over-separator", timestamp: ts, inningsId: 1, overSeparator: { overNumber: n, overSummary: summary, batTeamObj: { teamScore: "0-0" } } });
const recs = (...items) => items.map((i) => recordOf(i)).filter(Boolean);

test("over-summary tokens: totals include the extra's penalty, P is a penalty and not a ball", () => {
  assert.deepEqual(overToken("0"), { runs: 0, kind: null });
  assert.deepEqual(overToken("W"), { runs: 0, kind: null, w: 1 });
  assert.deepEqual(overToken("Wd"), { runs: 1, kind: "WIDE" });
  assert.deepEqual(overToken("Wd5"), { runs: 5, kind: "WIDE" });
  assert.deepEqual(overToken("N"), { runs: 1, kind: "NOBALL" });
  assert.deepEqual(overToken("N4"), { runs: 5, kind: "NOBALL" });
  assert.deepEqual(overToken("NB1"), { runs: 2, kind: "NOBALL" });
  assert.deepEqual(overToken("L3"), { runs: 3, kind: "LEGBYE" });
  assert.deepEqual(overToken("B4"), { runs: 4, kind: "BYE" });
  assert.deepEqual(overToken("P"), { runs: 5, pen: 1 });
  assert.equal(overToken("?"), null);
});

test("crawl keeps each over's summary", async () => {
  const st = { cursor: 9e15, done: 0 };
  const page = [sep(1, "0 1 ", 50), item(0.2, "A to B, 1 run", { timestamp: 20 }), item(0.1, "A to B, no run", { timestamp: 10 })];
  let n = 0;
  await crawlInnings({ st, innings: 1, fetchPage: async () => (n++ ? [] : page) });
  assert.deepEqual(st.os, { 1: "0 1" });
});

test("the summary settles an unreadable wicket line and a no-ball's leg byes (103852, 109351)", () => {
  const rs = recs(
    item(0.1, "A to B, no run", { timestamp: 1 }),
    item(0.2, "A to B, New baller gets the wicket!! A dream delivery", { timestamp: 2, event: ["wicket"] }),
    item(0.3, "A to B, no ball, leg byes, 1 run, late on the flick", { timestamp: 3 }),
    item(0.3, "A to B, 1 run", { timestamp: 4 }),
  );
  assert.equal(rs[1].runs, null);
  const out = reconcile(rs, { 1: "0 W N1 1" }, { 1: 3 });
  const ds = deliveriesOf(out, new Map());
  assert.equal(ds.filter((o) => o.unreadable).length, 0);
  assert.deepEqual(ds[1].d.slice(5, 8), [0, 0, 0]);
  assert.deepEqual(ds[2].d.slice(5, 8), [0, 2, BITS.NOBALL | BITS.LEGBYE]);
});

test("a penalty in the summary lands on the next ball as penalty extras (148415)", () => {
  const rs = recs(item(0.1, "A to B, no run", { timestamp: 1 }), item(0.2, "A to B, 4 runs", { timestamp: 2, event: ["four"] }));
  const ds = deliveriesOf(reconcile(rs, { 1: "P 0 4" }, { 1: 9 }), new Map());
  assert.deepEqual(ds[0].d.slice(5, 8), [0, 5, BITS.PENALTY]);
  assert.deepEqual(ds[1].d.slice(5, 8), [4, 0, 0]);
});

test("a ball missing from the stream is rebuilt from the summary, on strike as Cricbuzz left it (16933)", () => {
  const rs = recs(
    item(0.1, "A to Nabi, 1 run", { timestamp: 1, batsmanDetails: { playerId: 2, playerName: "Shenwari" } }),
    item(0.2, "A to Shenwari, no run", { timestamp: 2, batsmanDetails: { playerId: 2, playerName: "Shenwari" } }),
    item(0.4, "A to Shenwari, 1 run", { timestamp: 4, batsmanDetails: { playerId: 1, playerName: "Nabi" } }),
  );
  const roster = [{ id: "1", name: "Mohammad Nabi" }, { id: "2", name: "Samiullah Shenwari" }];
  const people = new Map();
  const ds = deliveriesOf(reconcile(rs, { 1: "1 0 2 1" }, { 1: 4 }), people, roster);
  assert.deepEqual(ds.map((o) => `${o.d[0]}.${o.d[1]}`), ["0.1", "0.2", "0.3", "0.4"]);
  const names = [...people.keys()];
  assert.equal(names[ds[2].d[2]], "2|Samiullah Shenwari");
  assert.equal(ds[2].d[3], ds[1].d[3]); // the over's bowler
  assert.deepEqual(ds[2].d.slice(5, 8), [2, 0, 0]);
});

test("a summary that cannot be matched to the over's balls changes nothing", () => {
  const rs = recs(item(0.1, "A to B, 1 run", { timestamp: 1 }), item(0.2, "A to B, 4 runs", { timestamp: 2, event: ["four"] }));
  assert.deepEqual(reconcile(rs, { 1: "6 6 6" }), rs); // more tokens, none fit
  assert.deepEqual(reconcile(rs, { 1: "1" }), rs); // fewer tokens than balls
  assert.deepEqual(reconcile(rs, { 1: "1 ?" }), rs); // a token it cannot read
});

test("overs read as Cricbuzz's scorecard writes them, so the legal-ball count is checked", () => {
  assert.equal(ballsOfOvers("18 Ov"), 108);
  assert.equal(ballsOfOvers("19.2 Ov"), 116);
  assert.equal(ballsOfOvers("20"), 120);
  assert.equal(ballsOfOvers("Ov"), null);
  const ds = conv([item(0.1, "A to B, 1 run", { timestamp: 1 }), item(0.3, "A to B, 1 run", { timestamp: 3 })]);
  assert.match(inningsProblems(ds, { score: "2-0", overs: "0.3 Ov" }).join(" | "), /legal balls 2 != overs 0\.3 Ov/);
});

test("a placeholder summary the board contradicts changes nothing (47211: '0 0 0 0 0 0' while the board moved 6)", () => {
  const rs = recs(item(0.1, "A to B, 1 run", { timestamp: 1 }), item(0.2, "A to B, 4 runs", { timestamp: 2, event: ["four"] }));
  assert.deepEqual(reconcile(rs, { 1: "0 0" }, { 1: 5 }), rs);
  assert.deepEqual(reconcile(rs, { 2: "0 0" }, { 2: 5 }), rs); // no board before it: cannot be checked
  const w = recs(item(0.1, "A to B, gets the wicket!!", { timestamp: 1, event: ["wicket"] }), item(0.2, "A to B, 4 runs", { timestamp: 2, event: ["four"] }));
  assert.equal(reconcile(w, { 1: "W 4" }, { 1: 4 })[0].runs, 0);
});

test("a legal ball posted twice keeps its newest line; wides sharing a label are both kept (18005, 47211)", () => {
  const rs = recs(
    item(0.1, "Farooqi to Raza, no run", { timestamp: 1, bowlerDetails: { playerId: 8, playerName: "Farooqi" } }),
    item(0.1, "Fareed to Raza, no run", { timestamp: 5, bowlerDetails: { playerId: 7, playerName: "Fareed" } }),
    item(0.2, "Fareed to Raza, wide", { timestamp: 6 }),
    item(0.2, "Fareed to Raza, wide", { timestamp: 7 }),
    item(0.2, "Fareed to Raza, 1 run", { timestamp: 8 }),
    item(0.3, "Fareed to Raza, 2 runs", { timestamp: 9 }),
    item(0.3, "Fareed to Raza, 1 run", { timestamp: 10 }), // a different outcome: not a re-post, kept
  );
  const out = dropReposts(rs);
  assert.deepEqual(out.map((r) => r.ts), [5, 6, 7, 8, 9, 10]);
  assert.equal(out[0].bowl[1], "Fareed");
  const clean = rs.slice(2, 6);
  assert.equal(dropReposts(clean), clean); // nothing to drop: the same array back
});
