import test from "node:test";
import assert from "node:assert/strict";

import {
  __backlogEntries,
  __resetBacklog,
  __setBatchFn,
  __setVerifyBatchFn,
  drainBacklog,
  resetRewriteBatcher,
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

test("a large backlog drains oldest-first, a few batches per run", async () => {
  const calls = [];
  useStubs(writer({ calls }), checker(() => false));
  const base = { matchId: "bk-7", innings: 1, clean: "Lomror to Abell, no run", firstSeenMs: Date.now() };
  __resetBacklog(Array.from({ length: 50 }, (_, i) => ({ ...base, rowId: `r${i}`, ts: 50 - i, sortKey: 50 - i, ball: `${i}.1` })));
  const result = await drainBacklog({ db: fakeDb(), log: silent, error: silent });
  assert.equal(result.written, 36);
  assert.equal(result.held, 14);
  // ts runs 50..1 as i runs 0..49, so the 14 left are the NEWEST: rows r0..r13.
  assert.deepEqual(__backlogEntries().map((e) => e.rowId).sort(), Array.from({ length: 14 }, (_, i) => `r${i}`).sort());
});
