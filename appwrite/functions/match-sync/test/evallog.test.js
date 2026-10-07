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
    getFileDownload: async () => { const e = new Error("nf"); e.code = 404; throw e; },
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

  const broken = { ...fakeStorage(), deleteFile: async () => {}, createFile: async () => { throw new Error("503"); } };
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

test("an ended match is taken from the recent list once, and only if it was followed live", async () => {
  const { endedMatches, nextSlowState, finalKey } = await import("../src/main.js");
  const recent = [{ id: 1, title: "A vs B" }, { id: 2, title: "C vs D" }, { id: 3, title: "E vs F" }, { id: 4, title: "G vs H" }];
  const started = { 1: 10, 2: 10, 4: 10 };
  const state = { [finalKey("2")]: 50 };
  // 2 is already finalised; 3 was never seen live.
  assert.deepEqual(endedMatches({ recent, started, state, liveIds: new Set() }).map((m) => m.id), [1, 4]);
  assert.deepEqual(endedMatches({ recent, started, state, liveIds: new Set(), limit: 1 }).map((m) => m.id), [1]);
  // Still on the live list this run: it will be synced as live, not twice.
  assert.deepEqual(endedMatches({ recent, started, state, liveIds: new Set(["1"]) }).map((m) => m.id), [4]);
  // No recent list or no record of what was live: nothing is guessed.
  assert.deepEqual(endedMatches({ recent: null, started, state, liveIds: new Set() }), []);
  assert.deepEqual(endedMatches({ recent, started: null, state, liveIds: new Set() }), []);

  // The marker is kept by age, whatever list the match is on this run. Two matches trading the
  // last place of the recent list used to lose their markers on alternate runs.
  const { FINAL_MARKER_TTL_MS } = await import("../src/main.js");
  const kept = nextSlowState({ state, processed: [], liveIds: new Set(), now: 99, finalised: ["1"] });
  assert.deepEqual(kept, { [finalKey("2")]: 50, [finalKey("1")]: 99 });
  assert.deepEqual(endedMatches({ recent, started, state: kept, liveIds: new Set() }).map((m) => m.id), [4]);
  const aged = nextSlowState({ state: kept, processed: [], liveIds: new Set(), now: 50 + FINAL_MARKER_TTL_MS });
  assert.deepEqual(aged, { [finalKey("1")]: 99 }, "a marker goes only once it is older than the poll can remember its match");
  // The slow-tier clock is still pruned to what is live.
  assert.deepEqual(nextSlowState({ state: { 7: 10, 8: 10 }, processed: [], liveIds: new Set(["7"]), now: 20 }), { 7: 10 });
});

test("rival samples survive a container restart: stored as they arrive, merged at the end", async () => {
  const { mergeRival, RIVAL_PREFIX } = await import("../src/evallog.js");
  const { rivalStoreFor } = await import("../src/main.js");
  const a = { innings: 1, over: 1, at: 10, raw: "a" };
  const b = { innings: 1, over: 2, at: 20, raw: "b" };
  const b2 = { innings: 1, over: 2, at: 25, raw: "b2" };
  assert.deepEqual(mergeRival([a, b], [b2]), [a, b2]);
  assert.deepEqual(mergeRival(null, undefined), []);

  // A store over a fake bucket: a missing file is an empty list, a failed read is null.
  const bucket = new Map();
  const storage = {
    getFileDownload: async (_b, id) => {
      if (id.endsWith("-broken")) throw new Error("503");
      if (!bucket.has(id)) { const e = new Error("nf"); e.code = 404; throw e; }
      return bucket.get(id);
    },
    deleteFile: async (_b, id) => { bucket.delete(id); },
    createFile: async (_b, id, input) => { bucket.set(id, JSON.parse(Buffer.from(await input.arrayBuffer?.() ?? []).toString("utf8") || "{}")); },
  };
  const store = rivalStoreFor({ storage, error: () => {} });
  assert.deepEqual(await store.load("77"), []);
  assert.equal(await store.load("77-broken"), null);

  const realFetch = globalThis.fetch;
  resetThrottleState();
  const raw = { team1: 37, team2: 63 };
  globalThis.fetch = async () =>
    new Response(page({ status: "Birmingham Phoenix need 40 runs", extra: { winProbability: raw } }), { status: 200, headers: { "content-type": "text/html" } });
  try {
    // What an earlier container stored for this match before it was replaced.
    const earlier = [{ innings: 1, over: 4, at: 5, raw: "earlier" }];
    const saved = [];
    const record = async (_db, _table, rowId) => ({ $id: rowId });
    await syncMatch({
      db: { updateDocument: record, createDocument: record, getDocument: async () => { const e = new Error("nf"); e.code = 404; throw e; } },
      matchId: "77", title: "Southern Brave vs Birmingham Phoenix", commentaryCap: 0, storeCommentary: false,
      alreadyFinalised: false, model: null, log: () => {}, error: () => {},
      rivalStore: { load: async () => earlier, save: async (id, samples) => { saved.push({ id, samples }); } },
    });
    assert.equal(saved.length, 1);
    assert.equal(saved[0].id, "77");
    assert.equal(saved[0].samples.length, 2, "the stored sample is kept beside the new one");
    assert.equal(saved[0].samples[0].raw, "earlier");
    assert.equal(saved[0].samples[1].raw, JSON.stringify(raw));
    assert.equal(RIVAL_PREFIX, "wpr-");
  } finally {
    globalThis.fetch = realFetch;
  }
});

test("the log names both sides and the start time, which is what a Cricinfo match is joined on", () => {
  const body = buildEvalLog({
    matchId: 151565, team1Id: 10, otherTeamIds: ["2"], result: { type: "win", winnerId: "2" },
    overs: [{ innings: 1, over: 1, score: 5, wickets: 0, p: 0.18 }],
    teams: [{ id: "2", name: "India", shortName: "IND" }, { id: "10", name: "West Indies", shortName: "WI" }],
    startMs: 1791293400000,
  });
  assert.deepEqual(body.teams, [{ id: "2", name: "India" }, { id: "10", name: "West Indies" }]);
  assert.equal(body.startMs, 1791293400000);
});

test("a pre-match figure is recorded by the fixture's own team id, with or without a context", async () => {
  const { fixtureTeamId, notePrematch, PREMATCH_TTL_MS } = await import("../src/evallog.js");
  // The shape `matchlist.js` writes: `teamId`, a string, empty when Cricbuzz gave none.
  const fixture = { id: 173737, title: "Kuwait vs Thailand", teams: [{ teamId: "527", name: "Kuwait" }, { teamId: "303", name: "Thailand" }] };
  assert.equal(fixtureTeamId(fixture, "Kuwait"), "527");
  assert.equal(fixtureTeamId({ teams: [{ teamId: "388", name: "Zimbabwe Women" }] }, "zimbabwe  women"), "388");
  assert.equal(fixtureTeamId({ teams: [{ teamId: "", name: "Kuwait" }] }, "Kuwait"), null);
  // A side the fixture does not name has no id, and so no entry: the orientation is not guessed.
  assert.equal(fixtureTeamId(fixture, "Qatar"), null);
  assert.equal(fixtureTeamId({ title: "A vs B" }, "A"), null);

  const t0 = 1_000_000;
  const first = notePrematch({}, 173737, "527", 0.6584, t0);
  assert.deepEqual(first, { 173737: { teamId: "527", p: 0.658, at: t0 } });
  // The number drifts in its third decimal every run; that is not worth a file write.
  assert.equal(notePrematch(first, 173737, "527", 0.662, t0 + 60_000), null);
  const moved = notePrematch(first, 173737, "527", 0.71, t0 + 120_000);
  assert.equal(moved[173737].p, 0.71);
  // A week-old fixture is dropped when the file is next rewritten.
  const later = notePrematch(first, 999, "1", 0.5, t0 + PREMATCH_TTL_MS + 1);
  assert.deepEqual(Object.keys(later), ["999"]);
});

test("a first innings that is over is scored as the chase it has become", async () => {
  const { chaseStart } = await import("../src/main.js");
  const live = { battingTeamIsTeam1: true, runsScored: 171, wicketsLost: 9, ballsBowled: 114, target: null, long: null };
  assert.equal(chaseStart(live, "T20"), null, "an innings still in progress is scored as it is");
  // All out with four balls unused: scored on its own this read as ten down mid-innings (0.03).
  const allOut = chaseStart({ ...live, wicketsLost: 10, ballsBowled: 116 }, "T20");
  assert.deepEqual(allOut, { runsNeeded: 172, runsScored: 0, ballsRemaining: 120, wicketsLost: 0, currentRunRate: 8.6, requiredRunRate: 8.6 });
  // A full innings: no balls left, which the model cannot score at all.
  assert.deepEqual(chaseStart({ ...live, wicketsLost: 6, ballsBowled: 120 }, "T20"), allOut);
  assert.equal(chaseStart({ ...live, runsScored: 300, wicketsLost: 10, ballsBowled: 280 }, "ODI").ballsRemaining, 300);
  // Not a first innings, not limited overs, or a format with no allotment: left alone.
  assert.equal(chaseStart({ ...live, wicketsLost: 10, battingTeamIsTeam1: false }, "T20"), null);
  assert.equal(chaseStart({ ...live, wicketsLost: 10, target: 172 }, "T20"), null);
  assert.equal(chaseStart({ ...live, wicketsLost: 10, long: { lead: 0 } }, "TEST"), null);
  assert.equal(chaseStart({ ...live, wicketsLost: 10 }, "UNKNOWN"), null);
  assert.equal(chaseStart(null, "T20"), null);
});
