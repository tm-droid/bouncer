import test from "node:test";
import assert from "node:assert/strict";

import { buildEvalLog, outcomeOf, prematchMoved, rivalSample } from "../src/evallog.js";
import { parseMatchMeta, resetThrottleState } from "../src/cricbuzz.js";
import { pruneEvalLogs, syncMatch, writeEvalLog } from "../src/main.js";

/**
 * The evaluation log is the only record of what the model said about a finished match. A wrong
 * outcome or a mis-oriented side here is scored later as the model's error, so both are decided
 * by team id and anything unresolved is `unknown`, never a guess.
 */

/** A live page in Cricbuzz's flight-payload shape: a miniscore, a match header, and extras. */
function page({ status = "Southern Brave won by 39 runs", result = {}, extra = {} } = {}) {
  const payload = {
    miniscore: {
      batsmanStriker: { name: "Sean Dickson", runs: "31", balls: "20" },
      inningsId: 2,
      overs: "18.2",
      batTeam: { teamScore: 150, teamWkts: 10 },
      matchScoreDetails: {
        matchFormat: "T20",
        inningsScoreList: [
          { inningsId: 1, batTeamId: "1", batTeamName: "SOB", score: 189, wickets: 6, overs: 20.0 },
          { inningsId: 2, batTeamId: "2", batTeamName: "BPH", score: 150, wickets: 10, overs: 18.2 },
        ],
      },
      status,
    },
    matchHeader: {
      matchId: 99,
      result,
      team1: { id: 2, name: "Birmingham Phoenix", shortName: "BPH" },
      team2: { id: 1, name: "Southern Brave", shortName: "SOB" },
    },
    ...extra,
  };
  return `<html><body><script>self.__next_f.push([1,${JSON.stringify(JSON.stringify(payload))}])</script></body></html>`;
}

test("the result is read from the match header by team id", () => {
  const won = { resultType: "win", winningTeam: "Southern Brave", winningteamId: 1, winningMargin: 39 };
  assert.deepEqual(parseMatchMeta(page({ result: won })).result, { type: "win", winnerId: "1" });
  assert.deepEqual(parseMatchMeta(page({ result: { resultType: "draw", winningTeam: "" } })).result, { type: "draw", winnerId: null });
  // `{}` is what the header carries until the match ends.
  assert.equal(parseMatchMeta(page({ result: {} })).result, null);
});

test("an unpopulated Cricbuzz win probability is absent, a populated one is handed on raw", () => {
  assert.equal(parseMatchMeta(page({ extra: { winProbability: "$undefined" } })).rivalWinProbability, null);
  assert.equal(parseMatchMeta(page()).rivalWinProbability, null);
  const raw = { team1: { id: 2, percent: 37 }, team2: { id: 1, percent: 63 } };
  assert.deepEqual(parseMatchMeta(page({ extra: { winProbability: raw } })).rivalWinProbability, raw);
  assert.equal(rivalSample("$undefined"), null);
  assert.equal(rivalSample(null), null);
  assert.equal(rivalSample(raw), JSON.stringify(raw));
  assert.equal(rivalSample("x".repeat(900)).length, 400);
});

test("the outcome is relative to the first-innings side, and never guessed", () => {
  assert.equal(outcomeOf({ type: "win", winnerId: "1" }, "1", "2"), "team1");
  assert.equal(outcomeOf({ type: "win", winnerId: 2 }, "1", "2"), "team2");
  // A winner that is neither side, or a win with no id, cannot be scored.
  assert.equal(outcomeOf({ type: "win", winnerId: "7" }, "1", "2"), "unknown");
  assert.equal(outcomeOf({ type: "win", winnerId: null }, "1", "2"), "unknown");
  assert.equal(outcomeOf({ type: "win", winnerId: "1" }, null, "2"), "unknown");
  assert.equal(outcomeOf({ type: "tie" }, "1", "2"), "tie");
  assert.equal(outcomeOf({ type: "draw" }, "1", "2"), "draw");
  assert.equal(outcomeOf({ type: "noresult" }, "1", "2"), "no_result");
  assert.equal(outcomeOf({ type: "abandon" }, "1", "2"), "no_result");
  assert.equal(outcomeOf({ type: "something-new" }, "1", "2"), "unknown");
  assert.equal(outcomeOf(null, "1", "2"), "unknown");
});

test("the log keeps only stamped overs and names the second side by id", () => {
  const body = buildEvalLog({
    matchId: 99,
    title: "Southern Brave vs Birmingham Phoenix",
    format: "T20",
    gender: "male",
    team1Id: 1,
    otherTeamIds: ["2", "1"],
    result: { type: "win", winnerId: "1" },
    status: "Southern Brave won by 39 runs",
    overs: [
      { innings: 1, over: 1, runs: 8, score: 8, wickets: 0, p: 0.52 },
      { innings: 1, over: 2, runs: 4, score: 12, wickets: 1 },
      { innings: 2, over: 1, runs: 6, score: 6, wickets: 0, p: 0.71 },
    ],
    prematch: { teamId: 2, p: 0.4444, at: 1 },
    modelVersion: 6,
    hadContext: true,
    hadElevens: false,
    now: 1000,
  });
  assert.equal(body.team1Id, "1");
  assert.equal(body.team2Id, "2");
  assert.equal(body.outcome, "team1");
  assert.deepEqual(body.points, [
    { innings: 1, over: 1, score: 8, wickets: 0, p: 0.52 },
    { innings: 2, over: 1, score: 6, wickets: 0, p: 0.71 },
  ]);
  assert.deepEqual(body.prematch, { teamId: "2", p: 0.444 });
  assert.deepEqual(body.cricbuzz, []);
  assert.equal(body.endedAt, 1000);
});

test("a match with nothing to score writes no log", () => {
  const overs = [{ innings: 1, over: 1, runs: 8, score: 8, wickets: 0 }];
  assert.equal(buildEvalLog({ matchId: 1, team1Id: 1, overs, result: { type: "win", winnerId: "1" } }), null);
});

test("a pre-match figure is rewritten only when it has moved or changed sides", () => {
  assert.equal(prematchMoved(undefined, "1", 0.6), true);
  assert.equal(prematchMoved({ teamId: "1", p: 0.6 }, "1", 0.605), false);
  assert.equal(prematchMoved({ teamId: "1", p: 0.6 }, "1", 0.63), true);
  assert.equal(prematchMoved({ teamId: "1", p: 0.6 }, "2", 0.6), true);
  assert.equal(prematchMoved(undefined, null, 0.6), false);
  assert.equal(prematchMoved(undefined, "1", NaN), false);
});

function fakeStorage() {
  const files = new Map();
  return {
    files,
    deleteFile: async (_bucket, id) => { files.delete(id); },
    createFile: async (_bucket, id, input) => { files.set(id, input); return { $id: id }; },
  };
}

test("the log is written under its own prefix, and a failed write does not throw", async () => {
  const storage = fakeStorage();
  const args = {
    matchId: "99",
    title: "Southern Brave vs Birmingham Phoenix",
    detail: { team1Id: "1", team2Id: "2", status: "Southern Brave won by 39 runs", situation: { matchFormat: "T20" } },
    context: null,
    meta: { result: { type: "win", winnerId: "2" }, teams: [{ id: "2" }, { id: "1" }] },
    winProb: null,
    overs: [{ innings: 2, over: 5, runs: 9, score: 40, wickets: 1, p: 0.3 }],
    rival: [],
    model: { version: 6 },
    log: () => {},
    error: () => {},
  };
  assert.equal(await writeEvalLog({ storage, ...args }), true);
  assert.deepEqual([...storage.files.keys()], ["wpe-99"]);

  const broken = { deleteFile: async () => {}, createFile: async () => { throw new Error("503"); } };
  const errors = [];
  assert.equal(await writeEvalLog({ storage: broken, ...args, error: (m) => errors.push(m) }), false);
  assert.equal(errors.length, 1);
});

test("pruning deletes only old evaluation logs, and re-checks every candidate itself", async () => {
  const now = Date.parse("2026-12-20T04:00:00Z");
  const deleted = [];
  const storage = {
    listFiles: async () => ({ files: [
      { $id: "wpe-151543", $updatedAt: "2026-10-01T00:00:00.000+00:00" },
      { $id: "winprob-manifest", $updatedAt: "2026-09-01T00:00:00.000+00:00" },
      { $id: "wpe-999", $updatedAt: "2026-12-01T00:00:00.000+00:00" },
    ] }),
    deleteFile: async (_bucket, id) => { deleted.push(id); },
  };
  assert.equal(await pruneEvalLogs({ storage, log: () => {}, error: (m) => { throw new Error(m); }, now }), 1);
  assert.deepEqual(deleted, ["wpe-151543"]);
});

test("the run that ends a match hands its result and over history to the finaliser", async () => {
  const realFetch = globalThis.fetch;
  resetThrottleState();
  const won = { resultType: "win", winningTeam: "Southern Brave", winningteamId: 1 };
  const scorecard = `<html><body>
    <div id="team-1-innings-1"><div class="font-bold">Southern Brave</div><span class="font-bold">189/6</span><span>(20 Ov)</span></div>
    <div id="scard-team-1-innings-1"><div class="grid scorecard-bat-grid"><span class="hover:underline">Sean Dickson</span></div></div>
  </body></html>`;
  globalThis.fetch = async (url) =>
    new Response(String(url).includes("scorecard") ? scorecard : page({ result: won }), { status: 200, headers: { "content-type": "text/html" } });
  try {
    let handed = null;
    const record = async (_db, _table, rowId) => ({ $id: rowId });
    const result = await syncMatch({
      db: { updateDocument: record, createDocument: record, getDocument: async () => { const e = new Error("nf"); e.code = 404; throw e; } },
      matchId: "99",
      title: "Southern Brave vs Birmingham Phoenix",
      commentaryCap: 0,
      storeCommentary: false,
      alreadyFinalised: false,
      model: null,
      log: () => {},
      error: () => {},
      onFinalised: async (f) => { handed = f; },
    });
    assert.equal(result.finalised, true);
    assert.deepEqual(handed.meta.result, { type: "win", winnerId: "1" });
    assert.ok(Array.isArray(handed.overs));
    assert.deepEqual(handed.rival, []);
  } finally {
    globalThis.fetch = realFetch;
  }
});
