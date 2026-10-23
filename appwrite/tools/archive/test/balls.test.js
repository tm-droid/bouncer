import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { BITS, CricbuzzBlockedError, ballsBlock, crawlInnings, deliveriesOf, inningsProblems, recordOf } from "../src/balls.js";
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
  assert.ok(balls.p.every(([n, id]) => n && /^\d+$/.test(id)));
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
