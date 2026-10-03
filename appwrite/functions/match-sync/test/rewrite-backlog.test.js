import test from "node:test";
import assert from "node:assert/strict";

import {
  __backlogEntries,
  __resetBacklog,
  __setBacklogContext,
  __setBatchFn,
  __settleHeldWork,
  __setVerifyBatchFn,
  backlogOrder,
  dedicatedEntries,
  drainBacklog,
  isEcho,
  resetRewriteBatcher,
  sourceConflictNote,
  syncMatch,
} from "../src/main.js";
import { resetThrottleState } from "../src/cricbuzz.js";
import { createHash } from "crypto";

// Raw Cricbuzz text is never stored. A delivery with no verified line is held and offered to the
// writer again on later runs; until 2026-09-27 it was written raw, 2-3% of every day's rows, ~90%
// of them lines the checker had correctly rejected ("Mahipal Lomror sends a blistering drive…"
// when Lomror was bowling).

const realFetch = globalThis.fetch;
const silent = () => {};

/** A live page with a non-terminal miniscore and [items] as its commentary window. */
function livePage(items) {
  const mini = {
    batsmanStriker: { name: "Migael Pretorius", runs: "12", balls: "9" },
    inningsId: 1,
    overs: "162.6",
    batTeam: { teamScore: 540, teamWkts: 7 },
    matchScoreDetails: {
      matchFormat: "TEST",
      inningsScoreList: [{ inningsId: 1, batTeamId: "1", batTeamName: "SOM", score: 540, wickets: 7, overs: 162.6 }],
    },
    status: "Day 3: Stumps",
  };
  const push = (o) => `<script>self.__next_f.push([1,${JSON.stringify(JSON.stringify(o))}])</script>`;
  return `<html><body>${push(mini)}${push({ commentaryList: items })}</body></html>`;
}

function fakeDb({ failCreate = () => null } = {}) {
  const writes = [];
  return {
    writes,
    listDocuments: async () => ({ documents: [] }),
    updateDocument: async (_db, table, rowId, data) => { writes.push({ op: "update", table, rowId, data }); return { $id: rowId }; },
    createDocument: async (_db, table, rowId, data) => {
      const err = failCreate(rowId);
      if (err) throw err;
      writes.push({ op: "create", table, rowId, data });
      return { $id: rowId };
    },
  };
}

// Writer: echoes a line per key unless the key is in `drop`. Checker: FAILs every item `failIf` matches.
function writer({ drop = new Set(), provider = "gemini-flash-lite", calls = [] } = {}) {
  return async (items, opts) => {
    calls.push({ keys: items.map((i) => i.key), exclude: opts.exclude ?? [] });
    const other = (opts.exclude ?? []).includes(provider) ? "groq" : provider;
    if (opts.used) opts.used.provider = other;
    return new Map(items.filter((i) => !drop.has(i.key)).map((i) => [i.key, `${other} wrote ${i.key}.`]));
  };
}
function checker(failIf) {
  return async (items) => ({ verdicts: new Map(items.map((i) => [i.key, !failIf(i)])), available: true });
}

let restoreBatch;
let restoreVerify;
function useStubs(batch, verify) {
  restoreBatch = __setBatchFn(batch);
  restoreVerify = __setVerifyBatchFn(verify);
}

test.beforeEach(() => {
  resetThrottleState();
  __resetBacklog();
});

test.afterEach(() => {
  globalThis.fetch = realFetch;
  if (restoreBatch !== undefined) __setBatchFn(restoreBatch);
  if (restoreVerify !== undefined) __setVerifyBatchFn(restoreVerify);
  restoreBatch = restoreVerify = undefined;
  resetRewriteBatcher();
});

async function sync(matchId, items, db, alerts = []) {
  globalThis.fetch = async () => new Response(livePage(items), { status: 200, headers: { "content-type": "text/html" } });
  await syncMatch({
    db, matchId, title: "Somerset vs Sussex", commentaryCap: 0, storeCommentary: true,
    alreadyFinalised: false, model: null, log: silent, error: silent, alerts,
  });
  return db.writes.filter((w) => w.table === "match-commentary");
}

test("a rejected line gets a second writer in the same run, and the retry is what is stored", async () => {
  const calls = [];
  // First writer's line fails; the retry excludes it and a different rung answers.
  useStubs(writer({ calls }), checker((i) => i.line.startsWith("gemini-flash-lite")));
  const now = Date.now();
  const rows = await sync("bk-1", [{ commText: "Mahipal Lomror to Migael Pretorius, FOUR", timestamp: now, ballMetric: "162.1" }], fakeDb());

  assert.equal(calls.length, 2, "one retry, no more");
  assert.deepEqual(calls[1].exclude, ["gemini-flash-lite"], "the rung that got it wrong is not asked again");
  assert.equal(rows.length, 1);
  assert.match(rows[0].data.commentary, /^162\.1: groq wrote /);
  assert.equal(rows[0].data.provider, "groq");
  assert.equal(rows[0].data.ai_generated, true);
  assert.equal(__backlogEntries().length, 0);
});

test("a line rejected twice is held — no row, raw or otherwise — but its alert still goes out", async () => {
  useStubs(writer(), checker(() => true));
  const now = Date.now();
  const db = fakeDb();
  const alerts = [];
  const rows = await sync("bk-2", [{ commText: "Mahipal Lomror to Migael Pretorius, FOUR", timestamp: now, ballMetric: "162.1" }], db, alerts);

  assert.equal(rows.length, 0, "nothing is written until a line passes the checker");
  const held = __backlogEntries();
  assert.equal(held.length, 1);
  assert.equal(held[0].clean, "Mahipal Lomror to Migael Pretorius, FOUR");
  assert.equal(held[0].sortKey, now);
  assert.equal(alerts.length, 1, "a FOUR alert does not wait for the prose");
});

test("a held delivery is written by a later run with its original sort_key, and alerts only once", async () => {
  useStubs(writer(), checker(() => true));
  const ts = Date.now();
  const db = fakeDb();
  const alerts = [];
  await sync("bk-3", [{ commText: "Mahipal Lomror to Migael Pretorius, SIX", timestamp: ts, ballMetric: "162.5" }], db, alerts);
  assert.equal(__backlogEntries().length, 1);

  // Next run: the writers recover.
  __setVerifyBatchFn(checker(() => false));
  const result = await drainBacklog({ db, log: silent, error: silent });

  assert.deepEqual(result, { written: 1, held: 0 });
  const rows = db.writes.filter((w) => w.table === "match-commentary");
  assert.equal(rows.length, 1);
  assert.equal(rows[0].data.sort_key, ts, "a late row sorts where the ball was bowled");
  assert.equal(rows[0].data.ball, "162.5");
  assert.equal(rows[0].data.event, "SIX", "event still comes from the raw text");
  assert.equal(rows[0].data.runs, 6);
  assert.equal(alerts.length, 1, "the drain sends no second alert");
});

test("a held delivery is not queued again by the match sync while the backlog owns it", async () => {
  // A cold container re-seeds the watermark from the newest WRITTEN row, which sits below a held
  // ball, so the live page offers that ball again. The backlog already owns it.
  const calls = [];
  useStubs(writer({ calls }), checker(() => true));
  const ts = Date.now();
  const rowId = createHash("sha256").update(`bk-4:${ts}`).digest("hex").slice(0, 36);
  __resetBacklog([{ matchId: "bk-4", rowId, ts, sortKey: ts, ball: "162.3", innings: 1, clean: "Mahipal Lomror to Tom Abell, 1 run", firstSeenMs: ts }]);
  const rows = await sync("bk-4", [{ commText: "Mahipal Lomror to Tom Abell, 1 run", timestamp: ts, ballMetric: "162.3" }], fakeDb());
  assert.equal(calls.length, 0, "the backlog, not the page, retries it");
  assert.equal(rows.length, 0);
  assert.equal(__backlogEntries().length, 1);
});

test("a backlog write that fails keeps the delivery held; a 409 releases it", async () => {
  useStubs(writer(), checker(() => false));
  const base = { innings: 1, clean: "Lomror to Abell, no run", firstSeenMs: Date.now() };
  __resetBacklog([
    { ...base, matchId: "bk-5", rowId: "r-fail", ts: 1, sortKey: 1, ball: "1.1" },
    { ...base, matchId: "bk-5", rowId: "r-409", ts: 2, sortKey: 2, ball: "1.2" },
  ]);
  const db = fakeDb({
    failCreate: (rowId) => (rowId === "r-fail" ? Object.assign(new Error("boom"), { code: 500 })
      : rowId === "r-409" ? Object.assign(new Error("exists"), { code: 409 }) : null),
  });
  await drainBacklog({ db, log: silent, error: silent });
  assert.deepEqual(__backlogEntries().map((e) => e.rowId), ["r-fail"]);
});

test("a batch nobody answered is not retried in the same second", async () => {
  const calls = [];
  // No provider at all: every rung failed at the transport level.
  useStubs(async (items, opts) => { calls.push(items.length); return new Map(); }, checker(() => false));
  await sync("bk-6", [{ commText: "Lomror to Abell, no run", timestamp: Date.now(), ballMetric: "162.4" }], fakeDb());
  assert.equal(calls.length, 1);
  assert.equal(__backlogEntries().length, 1, "held for the next run instead");
});

test("a large live backlog drains newest-first, two batches per run", async () => {
  const calls = [];
  useStubs(writer({ calls }), checker(() => false));
  const base = { matchId: "bk-7", innings: 1, clean: "Lomror to Abell, no run", firstSeenMs: 0 };
  __resetBacklog(Array.from({ length: 50 }, (_, i) => ({ ...base, rowId: `r${i}`, ts: i + 1, sortKey: i + 1, ball: `${i}.1` })));
  const result = await drainBacklog({ db: fakeDb(), log: silent, error: silent, liveIds: new Set(["bk-7"]) });
  assert.equal(result.written, 24);
  assert.equal(result.held, 26);
  assert.deepEqual(calls.map((c) => c.keys.length), [12, 12], "full batches, never more than twelve");
  // ts runs 1..50 with i, so the 26 left are the OLDEST: rows r0..r25.
  assert.deepEqual(__backlogEntries().map((e) => e.rowId).sort(), Array.from({ length: 26 }, (_, i) => `r${i}`).sort());
});

test("held deliveries are served viewers first, then live, then finished — newest ball first in each", () => {
  const e = (matchId, ts) => ({ matchId, rowId: `${matchId}-${ts}`, ts });
  const order = backlogOrder(
    [e("done", 9), e("live", 2), e("seen", 1), e("live", 5), e("done", 3), e("seen", 4)],
    { viewers: new Set(["seen"]), liveIds: new Set(["seen", "live"]) }
  );
  assert.deepEqual(order.map((x) => x.rowId), ["seen-4", "seen-1", "live-5", "live-2", "done-9", "done-3"]);
});

test("a large finished-match backlog gets no dedicated batch outside the pre-reset window, and loses nothing", async () => {
  const calls = [];
  useStubs(writer({ calls }), checker(() => false));
  const base = { innings: 1, clean: "Lomror to Abell, no run", firstSeenMs: 0 };
  __resetBacklog([
    ...Array.from({ length: 30 }, (_, i) => ({ ...base, matchId: "done", rowId: `d${i}`, ts: i + 1, sortKey: i + 1, ball: `${i}.1` })),
    { ...base, matchId: "live", rowId: "l0", ts: 100, sortKey: 100, ball: "9.1" },
  ]);
  // Noon Pacific: twelve hours from the reset.
  const noon = Date.parse("2026-10-03T19:00:00Z");
  const picked = dedicatedEntries({ liveIds: new Set(["live"]), now: noon });
  assert.deepEqual(picked.map((x) => x.rowId), ["l0"], "the live match is served; yesterday's waits for a spare slot");

  // 22:00 Pacific: inside the three hours before the buckets reset and whatever is left expires.
  const late = Date.parse("2026-10-04T05:00:00Z");
  assert.equal(dedicatedEntries({ liveIds: new Set(["live"]), now: late }).length, 24);
  assert.equal(__backlogEntries().length, 31, "selection never removes a held delivery");
});

test("a small finished-match backlog — the ordinary retry trickle — is drained at once", () => {
  const base = { matchId: "done", innings: 1, clean: "Lomror to Abell, no run", firstSeenMs: 0 };
  __resetBacklog(Array.from({ length: 24 }, (_, i) => ({ ...base, rowId: `d${i}`, ts: i + 1, sortKey: i + 1, ball: `${i}.1` })));
  assert.equal(dedicatedEntries({ now: Date.parse("2026-10-03T19:00:00Z") }).length, 24);
});

test("a fresh batch fills its spare slots from the backlog, at no extra call", async () => {
  const calls = [];
  useStubs(writer({ calls }), checker(() => false));
  const base = { matchId: "old", innings: 1, clean: "Lomror to Abell, no run", firstSeenMs: 0 };
  __resetBacklog(Array.from({ length: 20 }, (_, i) => ({ ...base, rowId: `h${i}`, ts: i + 1, sortKey: i + 1, ball: `${i}.1` })));
  const db = fakeDb();
  __setBacklogContext({ db, log: silent, error: silent, viewers: new Set(), liveIds: new Set(), startedMs: Date.now() });

  const now = Date.now();
  const rows = await sync("bk-8", [{ commText: "Lomror to Abell, 1 run", timestamp: now, ballMetric: "3.1" }], db);
  await __settleHeldWork();

  assert.equal(calls.length, 1, "one writer call carries the fresh ball and the held ones");
  assert.equal(calls[0].keys.length, 12, "the batch goes out full, and no larger than the probed size");
  assert.equal(rows.filter((r) => r.data.match_id === "bk-8").length, 1);
  const late = db.writes.filter((w) => w.table === "match-commentary" && w.data.match_id === "old");
  assert.equal(late.length, 11);
  // Newest held first: ts 20 down to 10.
  assert.deepEqual(late.map((w) => w.data.sort_key).sort((a, b) => b - a), Array.from({ length: 11 }, (_, i) => 20 - i));
  assert.equal(__backlogEntries().length, 9);
});

test("a held delivery rides in at most one batch per run, and a rejected one stays held", async () => {
  const calls = [];
  // Every line fails the checker, on the first writer and on the retry.
  useStubs(writer({ calls }), checker(() => true));
  const base = { matchId: "old", innings: 1, clean: "Lomror to Abell, no run", firstSeenMs: 0 };
  __resetBacklog(Array.from({ length: 5 }, (_, i) => ({ ...base, rowId: `h${i}`, ts: i + 1, sortKey: i + 1, ball: `${i}.1` })));
  const db = fakeDb();
  __setBacklogContext({ db, log: silent, error: silent, viewers: new Set(), liveIds: new Set(), startedMs: Date.now() });

  await sync("bk-9", [{ commText: "Lomror to Abell, 1 run", timestamp: Date.now(), ballMetric: "3.1" }], db);
  await sync("bk-9", [{ commText: "Lomror to Abell, 2 runs", timestamp: Date.now() + 1, ballMetric: "3.2" }], db);
  await __settleHeldWork();

  const firstPass = calls.filter((c) => c.exclude.length === 0);
  assert.equal(firstPass[0].keys.length, 6, "five held ride with the first fresh ball");
  assert.equal(firstPass[1].keys.length, 1, "none of them is offered again by the next batch in the run");
  assert.equal(db.writes.filter((w) => w.table === "match-commentary").length, 0, "nothing unverified is written");
  assert.equal(__backlogEntries().filter((e) => e.matchId === "old").length, 5, "and nothing is dropped");
});

// ── A source that contradicts itself (match 151554, ball 15.5, 2026-10-03) ───────────────────────

const STUCK = "Gudakesh Motie to Ruturaj Gaikwad, 1 run, tad short outside off, Rohit Sharma carves it through the off-side";
const PAIR = [{ name: "Ruturaj Gaikwad" }, { name: "Rohit Sharma" }];

test("a description naming the non-striker as the batter earns a note; ordinary lines do not", () => {
  assert.match(sourceConflictNote(STUCK, PAIR), /faced by Ruturaj Gaikwad, but the description names Rohit Sharma/);
  // Both named: running between the wickets, not a slip.
  assert.equal(sourceConflictNote("Gudakesh Motie to Ruturaj Gaikwad, 1 run, Gaikwad pushes to cover, Sharma calls him through", PAIR), "");
  assert.equal(sourceConflictNote("Gudakesh Motie to Ruturaj Gaikwad, no run, defended", PAIR), "");
  // The header's batter is not one of the live pair: a ball from another partnership, or the
  // other innings, where Sharma is a fielder. Nothing can be inferred.
  assert.equal(sourceConflictNote("Gudakesh Motie to Shai Hope, 1 run, Rohit Sharma fields at cover", PAIR), "");
  assert.equal(sourceConflictNote("Jansen to Banton, FOUR", []), "");
  assert.equal(sourceConflictNote("Drinks are on the field", PAIR), "");
});

test("the note reaches the writer and the checker, and never the stored row's classification", async () => {
  const seen = { writer: [], checker: [] };
  useStubs(
    async (items, opts) => {
      seen.writer.push(...items.map((i) => i.text));
      if (opts.used) opts.used.provider = "gemini-flash-lite";
      return new Map(items.map((i) => [i.key, "Motie drops short and it is carved through the off-side for a single."]));
    },
    async (items) => {
      seen.checker.push(...items.map((i) => i.source));
      return { verdicts: new Map(items.map((i) => [i.key, true])), available: true };
    }
  );
  const push = (o) => `<script>self.__next_f.push([1,${JSON.stringify(JSON.stringify(o))}])</script>`;
  const mini = {
    batsmanStriker: { name: "Ruturaj Gaikwad", runs: "12", balls: "9" },
    batsmanNonStriker: { name: "Rohit Sharma", runs: "30", balls: "22" },
    inningsId: 1,
    overs: "15.5",
    batTeam: { teamScore: 120, teamWkts: 1 },
    matchScoreDetails: { matchFormat: "T20", inningsScoreList: [{ inningsId: 1, batTeamId: "1", batTeamName: "IND", score: 120, wickets: 1, overs: 15.5 }] },
    status: "India opt to bat",
  };
  const ts = Date.now();
  globalThis.fetch = async () => new Response(
    `<html><body>${push(mini)}${push({ commentaryList: [{ commText: STUCK, timestamp: ts, ballMetric: "15.5" }] })}</body></html>`,
    { status: 200, headers: { "content-type": "text/html" } }
  );
  const db = fakeDb();
  await syncMatch({
    db, matchId: "bk-10", title: "India vs West Indies", commentaryCap: 0, storeCommentary: true,
    alreadyFinalised: false, model: null, log: silent, error: silent, alerts: [],
  });
  assert.match(seen.writer[0], /\[Scorer's note: this ball was faced by Ruturaj Gaikwad/);
  assert.match(seen.checker[0], /\[Scorer's note:/, "a checker without the note failed the unnamed line 3/3");
  const rows = db.writes.filter((w) => w.table === "match-commentary");
  assert.equal(rows.length, 1);
  assert.equal(rows[0].data.runs, 1, "runs still come from the raw clause");
  assert.doesNotMatch(rows[0].data.commentary, /Scorer's note/);
});

test("a writer that returns the source unchanged has not written a line", async () => {
  assert.equal(isEcho(`15.5: ${STUCK}.`, STUCK), true);
  assert.equal(isEcho("15.5: Motie drops short and it is carved away for a single.", STUCK), false);
  assert.equal(isEcho("", ""), false);

  const calls = [];
  // Rung one echoes; the retry on another rung writes a real line.
  useStubs(
    async (items, opts) => {
      const echo = !(opts.exclude ?? []).includes("groq");
      calls.push(echo ? "echo" : "wrote");
      if (opts.used) opts.used.provider = echo ? "groq" : "gemini-flash-lite";
      return new Map(items.map((i) => [i.key, echo ? `${i.text}.` : "Abell works it away for one."]));
    },
    checker(() => false)
  );
  const rows = await sync("bk-11", [{ commText: "Lomror to Abell, 1 run, worked away", timestamp: Date.now(), ballMetric: "3.1" }], fakeDb());
  assert.deepEqual(calls, ["echo", "wrote"]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].data.provider, "gemini-flash-lite");
  assert.match(rows[0].data.commentary, /Abell works it away/);
});

test("a delivery rejected three times stops getting dedicated batches, and is still held", async () => {
  const calls = [];
  useStubs(writer({ calls }), checker(() => true));
  const noon = Date.parse("2026-10-03T19:00:00Z");
  __resetBacklog([{ matchId: "live", rowId: "stuck", ts: 5, sortKey: 5, ball: "15.5", innings: 1, clean: STUCK, firstSeenMs: 0 }]);
  const run = async () => {
    await drainBacklog({ db: fakeDb(), log: silent, error: silent, liveIds: new Set(["live"]), now: noon });
    await __settleHeldWork();
  };
  await run();
  await run();
  await run();
  assert.equal(__backlogEntries()[0].rejections, 3);
  const before = calls.length;
  await run();
  assert.equal(calls.length, before, "the fourth run spends no call on it");
  assert.equal(__backlogEntries().length, 1, "it is not dropped");
  // …but the pre-reset sweep still offers it, and so does a spare slot in a fresh batch.
  assert.equal(dedicatedEntries({ liveIds: new Set(["live"]), now: Date.parse("2026-10-04T05:00:00Z") }).length, 1);
});
