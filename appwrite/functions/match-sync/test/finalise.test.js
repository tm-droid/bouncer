import test from "node:test";
import assert from "node:assert/strict";

import { syncMatch } from "../src/main.js";
import { resetThrottleState } from "../src/cricbuzz.js";

/**
 * The last write a match ever gets.
 *
 * `match-live-detail` and `match-scorecard` are written only for a match in `live-matches.json`,
 * and only on the detail tier — once per SLOW_TIER_INTERVAL_MS for a match nobody has open. The
 * moment the match ends it leaves that list and nothing writes those rows again, so what the tier
 * last left behind is what every device reads forever: routinely a mid-innings score a quarter of
 * an hour short of the result, against a Recent list that is re-scraped every minute and correct.
 *
 * So a terminal status forces one detail tier pass regardless of budget — and exactly one, because
 * the match can sit in the live list for several more runs after the result appears.
 */

const realFetch = globalThis.fetch;

/** A live page carrying a miniscore with [status], in Cricbuzz's flight-payload shape. */
function livePage(status) {
  const payload = {
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
  };
  const inner = JSON.stringify(payload);
  return `<html><body><script>self.__next_f.push([1,${JSON.stringify(inner)}])</script></body></html>`;
}

// `parseInnings` keys the body off `#scard-<header id>`; without that pair it returns null and the
// card is empty, which is indistinguishable here from "finalisation did not write one".
const SCORECARD_PAGE = `<html><body>
  <div id="team-1-innings-1">
    <div class="font-bold">Southern Brave</div>
    <span class="font-bold">189/6</span>
    <span>(20 Ov)</span>
  </div>
  <div id="scard-team-1-innings-1">
    <div class="grid scorecard-bat-grid">
      <span class="hover:underline">Sean Dickson</span>
    </div>
  </div>
</body></html>`;

function stubFetch() {
  const urls = [];
  globalThis.fetch = async (url) => {
    urls.push(String(url));
    const body = String(url).includes("scorecard") ? SCORECARD_PAGE : livePage(STATUS);
    return new Response(body, { status: 200, headers: { "content-type": "text/html" } });
  };
  return urls;
}

/** Mutated per test; `stubFetch` reads it when a request actually arrives. */
let STATUS = "Southern Brave won by 39 runs";

function fakeDb() {
  const writes = [];
  const record = (op) => async (dbId, table, rowId, data) => {
    writes.push({ op, table, rowId, data });
    return { $id: rowId };
  };
  return {
    writes,
    updateDocument: record("update"),
    createDocument: record("create"),
  };
}

const silent = () => {};

async function run({ db, matchId, commentaryCap, alreadyFinalised }) {
  return syncMatch({
    db,
    matchId,
    title: "Southern Brave vs Birmingham Phoenix",
    commentaryCap,
    // The commentary half is a separate decision and a separate budget; this test is only about
    // which runs write the detail and scorecard rows.
    storeCommentary: false,
    alreadyFinalised,
    model: null,
    log: silent,
    error: silent,
  });
}

test.beforeEach(() => {
  resetThrottleState();
  STATUS = "Southern Brave won by 39 runs";
  stubFetch();
});

test.afterEach(() => {
  globalThis.fetch = realFetch;
});

test("a result writes the final rows even with no detail-tier budget", async () => {
  // commentaryCap 0 is the state of every unwatched match on most runs. Before this, that match's
  // row simply stopped at whatever the last slow pass wrote.
  const db = fakeDb();
  const result = await run({ db, matchId: "finalise-1", commentaryCap: 0 });

  assert.equal(result.finalised, true);
  const tables = db.writes.map((w) => w.table);
  assert.ok(tables.includes("match-live-detail"), "the detail row must carry the result");
  assert.ok(tables.includes("match-scorecard"), "and the scorecard must be the completed one");
});

test("the scorecard is fetched on the finalising run even though the tier skipped it", async () => {
  const urls = stubFetch();
  await run({ db: fakeDb(), matchId: "finalise-2", commentaryCap: 0 });
  assert.ok(urls.some((u) => u.includes("scorecard")), "no scorecard fetch, no final card");
});

test("finalisation happens once, not on every run until the match leaves the live list", async () => {
  // A finished match can stay in `live-matches.json` for several more runs. Re-finalising each
  // time is a page fetch and two writes per minute for a match that cannot change again.
  const db = fakeDb();
  const again = await run({ db, matchId: "finalise-3", commentaryCap: 0, alreadyFinalised: true });

  assert.notEqual(again.finalised, true);
  assert.equal(db.writes.length, 0, "no writes on a match already finalised");
});

test("a match still being played is not finalised", async () => {
  STATUS = "Birmingham Phoenix need 96 runs in 44 balls";
  const db = fakeDb();
  const result = await run({ db, matchId: "finalise-4", commentaryCap: 0 });

  assert.notEqual(result.finalised, true);
  assert.equal(db.writes.length, 0, "an unviewed live match still pays no detail write");
});

test("a hollow parse is not recorded as the match's final word", async () => {
  // parseLiveDetail returns an all-null skeleton for a page with no miniscore. Marking that
  // finalised would burn the one chance to write the real result.
  globalThis.fetch = async () =>
    new Response("<html><body>no miniscore here</body></html>", {
      status: 200,
      headers: { "content-type": "text/html" },
    });

  const db = fakeDb();
  const result = await run({ db, matchId: "finalise-5", commentaryCap: 0 });
  assert.notEqual(result.finalised, true);
  assert.equal(db.writes.length, 0);
});

test("a match on the detail tier still writes exactly as it did before", async () => {
  const db = fakeDb();
  STATUS = "Birmingham Phoenix need 96 runs in 44 balls";
  await run({ db, matchId: "finalise-6", commentaryCap: 10 });
  assert.ok(db.writes.some((w) => w.table === "match-live-detail"));
});
