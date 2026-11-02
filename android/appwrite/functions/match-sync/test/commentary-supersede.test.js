import test from "node:test";
import assert from "node:assert/strict";

import {
  __backlogEntries,
  __resetBacklog,
  __resetSuperseded,
  __setBatchFn,
  __setSituationWriter,
  __setVerifyBatchFn,
  drainBacklog,
  resetRewriteBatcher,
  syncMatch,
} from "../src/main.js";
import { resetThrottleState } from "../src/cricbuzz.js";

const realFetch = globalThis.fetch;
const silent = () => {};

/** A live page with a non-terminal miniscore and [items] as its commentary window. */
function livePage(items) {
  const mini = {
    batsmanStriker: { name: "Sahan Arachchige", runs: "12", balls: "9" },
    inningsId: 1,
    overs: "10.3",
    batTeam: { teamScore: 80, teamWkts: 3 },
    matchScoreDetails: {
      matchFormat: "T20",
      inningsScoreList: [{ inningsId: 1, batTeamId: "1", batTeamName: "SL", score: 80, wickets: 3, overs: 10.3 }],
    },
    status: "Sri Lanka opt to bat",
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

async function sync(matchId, items, db, alerts = []) {
  globalThis.fetch = async () => new Response(livePage(items), { status: 200, headers: { "content-type": "text/html" } });
  await syncMatch({
    db, matchId, title: "Sri Lanka vs Bangladesh", commentaryCap: 0, storeCommentary: true,
    alreadyFinalised: false, model: null, log: silent, error: silent, alerts,
  });
  return db.writes.filter((w) => w.table === "match-commentary");
}

let restoreBatch;
let restoreVerify;
test.afterEach(() => {
  globalThis.fetch = realFetch;
  if (restoreBatch !== undefined) __setBatchFn(restoreBatch);
  if (restoreVerify !== undefined) __setVerifyBatchFn(restoreVerify);
  restoreBatch = restoreVerify = undefined;
  resetRewriteBatcher();
});

function reset({ failChecks = false } = {}) {
  resetThrottleState();
  __resetBacklog();
  __resetSuperseded();
  restoreBatch = __setBatchFn(async (items, opts) => {
    if (opts.used) opts.used.provider = "gemini-flash-lite";
    return new Map(items.map((i) => [i.key, "Arachchige lifts it and Mustafizur takes the catch."]));
  });
  restoreVerify = __setVerifyBatchFn(async (items) => ({ verdicts: new Map(items.map((i) => [i.key, !failChecks])), available: true }));
}

// Match 171061, 10.3, as Cricbuzz published it on 2026-10-03: a single, then the same delivery
// again under a new timestamp as a catch, then the single once more.
const T = Date.now() - 60_000;
const single = (timestamp) => ({ commText: "Rishad Hossain to Sahan Arachchige, 1 run", timestamp, ballMetric: "10.3" });
const phantom = (timestamp) => ({ commText: "Rishad Hossain to Sahan Arachchige, out Caught by Mustafizur Rahman!! Lifted to long-on", timestamp, ballMetric: "10.3" });

test("a corrected delivery deletes the row it withdrew, each time", async () => {
  reset();
  const db = fakeDb();
  await sync("sup-1", [single(T)], db);
  await sync("sup-1", [phantom(T + 1000)], db);
  const ops = await sync("sup-1", [single(T + 2000)], db);
  const creates = ops.filter((w) => w.op === "create");
  const deletes = ops.filter((w) => w.op === "delete");
  assert.equal(creates.length, 3);
  assert.deepEqual(deletes.map((w) => w.rowId), [creates[0].rowId, creates[1].rowId]);
});

test("a wide and the legal ball after it are two deliveries", async () => {
  reset();
  const db = fakeDb();
  const ops = await sync("sup-2", [
    { commText: "Nasir Lone to Smaran Ravichandran, wide", timestamp: T, ballMetric: "88.4" },
    { commText: "Nasir Lone to Smaran Ravichandran, FOUR", timestamp: T + 1000, ballMetric: "88.4" },
  ], db);
  assert.equal(ops.filter((w) => w.op === "create").length, 2);
  assert.equal(ops.filter((w) => w.op === "delete").length, 0);
});

test("a correction alerts only when it changes what happened", async () => {
  reset();
  const db = fakeDb();
  const alerts = [];
  await sync("sup-3", [phantom(T)], db, alerts);
  assert.equal(alerts.length, 1, "the wicket as first published");
  await sync("sup-3", [phantom(T + 1000)], db, alerts);
  assert.equal(alerts.length, 1, "the same wicket republished is not a second alert");
});

test("a withdrawn version still held is dropped when its correction is written", async () => {
  reset({ failChecks: true });
  const db = fakeDb();
  await sync("sup-4", [phantom(T)], db);
  assert.equal(__backlogEntries().length, 1, "the catch has no verified line and is held");
  await sync("sup-4", [single(T + 1000)], db);
  assert.equal(__backlogEntries().length, 0);
  assert.equal(db.writes.filter((w) => w.table === "match-commentary" && w.op === "create").length, 1);
});

// ── Situation lines, end to end ──────────────────────────────────────────────

test("two sixes in a row store one situation line, between the overs, checked against the facts", async () => {
  reset();
  const restore = __setSituationWriter(async (facts) => {
    assert.match(facts, /Sahan Arachchige has hit sixes off the last 2 balls faced/);
    return { line: "Sahan Arachchige is cutting loose now with sixes off successive balls.", provider: "gemini-flash-lite" };
  });
  try {
    const db = fakeDb();
    const six = (timestamp, label) => ({ commText: "Rishad Hossain to Sahan Arachchige, SIX", timestamp, ballMetric: label });
    await sync("sit-1", [six(T, "10.4"), six(T + 1000, "10.5")], db);
    const notes = () => db.writes.filter((w) => w.op === "create" && w.data.ball === "");
    assert.equal(notes().length, 0, "held until the over is done, or it would split the over on the device");
    await sync("sit-1", [{ commText: "Saif Hassan to Sahan Arachchige, no run", timestamp: T + 9000, ballMetric: "11.1" }], db);
    assert.equal(notes().length, 1);
    const row = notes()[0].data;
    assert.equal(row.sort_key, T + 9000 - 1);
    assert.equal(row.provider, "situation:gemini-flash-lite");
    assert.equal(row.event, "NONE");
    assert.equal("runs" in row, false);
  } finally {
    __setSituationWriter(restore);
  }
});

test("a situation line that adds a fact is not stored", async () => {
  reset();
  const restore = __setSituationWriter(async () => ({ line: "Sahan Arachchige races to a brilliant fifty with two more sixes.", provider: "groq" }));
  try {
    const db = fakeDb();
    const six = (timestamp, label) => ({ commText: "Rishad Hossain to Sahan Arachchige, SIX", timestamp, ballMetric: label });
    await sync("sit-2", [six(T, "10.4"), six(T + 1000, "10.5"), { commText: "Saif Hassan to Sahan Arachchige, no run", timestamp: T + 9000, ballMetric: "11.1" }], db);
    assert.equal(db.writes.filter((w) => w.op === "create" && w.data.ball === "").length, 0);
  } finally {
    __setSituationWriter(restore);
  }
});

test("a held delivery whose source is bare is written from the template, with no writer call", async () => {
  reset();
  let calls = 0;
  __setBatchFn(async () => { calls++; return new Map(); });
  __resetBacklog([{ matchId: "sup-5", rowId: "held-1", ts: T, sortKey: T, ball: "0.2", innings: 1, clean: "Chinelle Henry to Mupachikwa, no run", firstSeenMs: 0 }]);
  const db = fakeDb();
  const result = await drainBacklog({ db, log: silent, error: silent, liveIds: new Set(["sup-5"]) });
  assert.equal(calls, 0);
  assert.equal(result.written, 1);
  assert.equal(db.writes[0].data.provider, "template");
  assert.equal(db.writes[0].data.ai_generated, false);
  assert.equal(db.writes[0].data.runs, 0);
});
