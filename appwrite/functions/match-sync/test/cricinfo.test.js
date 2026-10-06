import test from "node:test";
import assert from "node:assert/strict";

import { fetchCricinfoPredictions, mergeCricinfo, parseCricinfoPage } from "../src/cricinfo.js";
import { __resetCricinfoGate, cricinfoStep } from "../src/main.js";

/**
 * Cricinfo's prediction is captured, never shown, and whether its page answers the container at
 * all is uncertain. So a block must read as a block, cost one request an hour, and never fail the
 * sync it rides on.
 */

const match = (objectId, extra = {}) => ({
  objectId, slug: `match-${objectId}`, format: "T20", state: "LIVE", startTime: "2026-10-06T13:30:00.000Z",
  teams: [{ team: { objectId: 4, longName: "West Indies" }, score: "49/3", scoreInfo: "6.4/20 ov" }, { team: { objectId: 6, longName: "India" } }],
  liveInning: 1, liveOvers: 6.04, statusText: "India chose to field.", liveInningPredictions: null, ...extra,
});
// The shape captured from India v West Indies on 2026-10-06.
const PREDICTION = { inningNumber: 1, oversUnique: 6.04, score: 164, winProbability: 34.74 };

function pageOf(matches) {
  // The same match object appears in more than one place on the real page.
  const data = { props: { editionDetails: { trendingMatches: { matches } }, appPageProps: { data: { content: { matches } } } } };
  return `<html><body><script id="__NEXT_DATA__" type="application/json">${JSON.stringify(data)}</script></body></html>`;
}
const respond = (body, status = 200) => async () => new Response(body, { status });

test("the page yields every match once and a row only where a prediction is published", () => {
  const parsed = parseCricinfoPage(pageOf([match(1529230, { liveInningPredictions: PREDICTION }), match(2), match(3, { state: "POST" })]), 1000);
  assert.equal(parsed.matches, 3);
  assert.equal(parsed.live, 2);
  assert.equal(parsed.rows.length, 1);
  assert.deepEqual(parsed.rows[0].predictions, PREDICTION);
  assert.equal(parsed.rows[0].cricinfoId, 1529230);
  assert.deepEqual(parsed.rows[0].teams.map((t) => t.name), ["West Indies", "India"]);
  assert.equal(parsed.rows[0].at, 1000);
});

test("a block, a redesigned page and a dead connection are each reported, never thrown", async () => {
  const blocked = await fetchCricinfoPredictions({ fetchImpl: respond("<HTML>Access Denied</HTML>", 403) });
  assert.deepEqual({ ok: blocked.ok, reason: blocked.reason, status: blocked.status }, { ok: false, reason: "http 403", status: 403 });
  const redesigned = await fetchCricinfoPredictions({ fetchImpl: respond("<html>no data here</html>") });
  assert.deepEqual({ ok: redesigned.ok, reason: redesigned.reason }, { ok: false, reason: "no data block" });
  const dead = await fetchCricinfoPredictions({ fetchImpl: async () => { throw new Error("ECONNRESET"); } });
  assert.deepEqual({ ok: dead.ok, reason: dead.reason, status: dead.status }, { ok: false, reason: "ECONNRESET", status: null });
  const fine = await fetchCricinfoPredictions({ fetchImpl: respond(pageOf([match(1, { liveInningPredictions: PREDICTION })])), now: 5 });
  assert.equal(fine.ok, true);
  assert.equal(fine.rows.length, 1);
});

test("samples merge one per ball, so a poll that finds the same ball adds nothing", () => {
  const row = (overs, at) => ({ at, cricinfoId: 1, predictions: { ...PREDICTION, oversUnique: overs } });
  const merged = mergeCricinfo([row(6.04, 1), row(7.01, 2)], row(7.01, 3));
  assert.deepEqual(merged.map((r) => [r.predictions.oversUnique, r.at]), [[6.04, 1], [7.01, 3]]);
  assert.equal(mergeCricinfo(null, row(1.01, 9)).length, 1);
});

function bucket() {
  const files = new Map();
  return {
    files,
    getFileDownload: async (_b, id) => {
      if (!files.has(id)) { const e = new Error("nf"); e.code = 404; throw e; }
      return files.get(id);
    },
    deleteFile: async (_b, id) => { files.delete(id); },
    createFile: async (_b, id) => { files.set(id, { samples: ["written"] }); },
  };
}

test("the step polls every five minutes, stores predictions, and logs what came back", async () => {
  __resetCricinfoGate();
  const storage = bucket();
  const logs = [];
  let calls = 0;
  const fetchPredictions = async () => {
    calls++;
    return { ok: true, status: 200, bytes: 500000, matches: 29, live: 8, rows: [{ at: 1, cricinfoId: 1529230, predictions: PREDICTION }] };
  };
  const args = { storage, log: (m) => logs.push(m), error: (m) => { throw new Error(m); }, fetchPredictions };
  const t0 = 1_000_000_000;
  await cricinfoStep({ ...args, now: t0 });
  await cricinfoStep({ ...args, now: t0 + 60_000 });
  await cricinfoStep({ ...args, now: t0 + 4 * 60_000 });
  assert.equal(calls, 1, "the next four one-minute runs do not poll");
  await cricinfoStep({ ...args, now: t0 + 5 * 60_000 });
  assert.equal(calls, 2);
  assert.deepEqual([...storage.files.keys()], ["wci-1529230"]);
  assert.match(logs[0], /\[CRICINFO\] status=200 bytes=500000 matches=29 live=8 predictions=1/);
});

test("a refusal backs off for an hour and does not fail the run", async () => {
  __resetCricinfoGate();
  const logs = [];
  let calls = 0;
  const fetchPredictions = async () => { calls++; return { ok: false, reason: "http 403", status: 403, bytes: 435, rows: [] }; };
  const args = { storage: bucket(), log: (m) => logs.push(m), error: () => {}, fetchPredictions };
  const t0 = 2_000_000_000;
  await cricinfoStep({ ...args, now: t0 });
  await cricinfoStep({ ...args, now: t0 + 10 * 60_000 });
  await cricinfoStep({ ...args, now: t0 + 59 * 60_000 });
  assert.equal(calls, 1);
  await cricinfoStep({ ...args, now: t0 + 61 * 60_000 });
  assert.equal(calls, 2);
  assert.match(logs[0], /\[CRICINFO\] unavailable \(http 403\) status=403/);

  // A step that throws inside is swallowed: the sync it rides on must finish.
  __resetCricinfoGate();
  assert.equal(await cricinfoStep({ ...args, now: t0, fetchPredictions: async () => { throw new Error("boom"); } }), null);
});
