import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import {
  DAILY_CALL_CAP, createHighlightlyFallback, dismissalText, highlightlyKey, joinMatch, mapDetail,
} from "../src/highlightly.js";

const fixture = (name) => JSON.parse(readFileSync(new URL(`./fixtures/${name}`, import.meta.url), "utf8"));
const AFG = fixture("highlightly-afg-zim.json");
const WOMEN = fixture("highlightly-ind-zim-women.json");
const START = Date.parse("2026-10-23T06:00:00Z");
const clone = (o) => JSON.parse(JSON.stringify(o));

test("Afghanistan v Zimbabwe maps to the parseScorecard shape, totals verified", () => {
  const { card, reason } = mapDetail(AFG, { teams: ["Afghanistan", "Zimbabwe"] });
  assert.equal(reason, undefined);
  assert.equal(card.source, "highlightly");
  assert.deepEqual(Object.keys(card), ["innings", "source"]);
  const [zim, afg] = card.innings;
  assert.equal(zim.teamName, "Zimbabwe");
  assert.equal(zim.score, "169-10");
  assert.equal(zim.total, "169-10 (43.3 Ov)");
  assert.equal(zim.overs, "43.3 Ov");
  assert.equal(zim.fallOfWickets.length, 10);
  assert.deepEqual(zim.fallOfWickets[0], { score: "22", overs: "4.2", batsman: "Tadiwanashe Marumani", number: 1 });
  assert.equal(afg.score, "170-3");
  assert.equal(afg.total, "170-3 (34.3 Ov)");
  assert.equal(afg.extras, "8 (lb 2, w 6)");
  assert.equal(afg.batsmen[0].name, "Rahmanullah Gurbaz");
  assert.equal(afg.batsmen[0].dismissal, "c Wessly Madhevere b Newman Nyamhuri");
  assert.equal(afg.batsmen[1].dismissal, "not out");
  assert.equal(afg.batsmen.length, 5, "players who did not bat are not rows");
  assert.deepEqual(Object.keys(afg.batsmen[0]).sort(), ["balls", "dismissal", "fours", "name", "profileUrl", "runs", "sixes", "strikeRate"]);
  assert.deepEqual(Object.keys(afg.bowlers[0]).sort(), ["economy", "maidens", "name", "noBalls", "overs", "profileUrl", "runs", "wickets", "wides"]);
  assert.equal(afg.bowlers[0].economy, "3.83");
  assert.equal(afg.partnerships.length, 4);
  assert.equal(afg.partnerships[0].runs, "68");
});

test("women's fixture maps and keeps the Women suffix in the labels", () => {
  const { card, reason } = mapDetail(WOMEN, { teams: ["India Women", "Zimbabwe Women"] });
  assert.equal(reason, undefined);
  assert.equal(card.innings[0].teamName, "Zimbabwe Women");
  assert.equal(card.innings[1].score, "200-1");
  assert.equal(card.innings[0].score, "197-10");
});

test("a wrong total is refused, not written", () => {
  const bad = clone(AFG);
  bad.statistics[1].team.inningBatsmen[0].runs += 1;
  assert.equal(mapDetail(bad, { teams: ["Afghanistan", "Zimbabwe"] }).reason, "total_mismatch");
  const noFow = clone(AFG);
  noFow.statistics[0].team.fallOfWickets.pop();
  assert.equal(mapDetail(noFow, { teams: ["Afghanistan", "Zimbabwe"] }).reason, "wickets_mismatch");
});

test("a missing field or unknown dismissal gives up", () => {
  const a = clone(AFG);
  a.statistics[1].team.inningBowlers[0].concededRuns = null;
  assert.equal(mapDetail(a, { teams: ["Afghanistan", "Zimbabwe"] }).reason, "bowler_unreadable");
  const b = clone(AFG);
  b.statistics[1].team.inningBatsmen[0].dismissalStatus = "timed out";
  assert.equal(mapDetail(b, { teams: ["Afghanistan", "Zimbabwe"] }).reason, "batter_unreadable");
  assert.equal(mapDetail(AFG, { teams: ["Afghanistan", "Kenya"] }).reason, "team_label");
  assert.equal(mapDetail({ statistics: [] }, { teams: [] }).reason, "innings_count");
});

test("dismissal wording", () => {
  assert.equal(dismissalText({ dismissalStatus: "caught", dismissalBowler: { name: "A" }, dismissalFielders: [{ name: "A" }] }), "c & b A");
  assert.equal(dismissalText({ dismissalStatus: "lbw", dismissalBowler: { name: "A" } }), "lbw b A");
  assert.equal(dismissalText({ dismissalStatus: "stumped", dismissalBowler: { name: "A" }, dismissalFielders: [{ name: "K" }] }), "st K b A");
  assert.equal(dismissalText({ dismissalStatus: "run out", dismissalFielders: [{ name: "K" }] }), "run out (K)");
  assert.equal(dismissalText({ dismissalStatus: "retired hurt" }), null);
});

const item = (id, home, away, startTime = "2026-10-23T06:00:00.000Z", description = "Finished") => ({
  id, startTime, homeTeam: { name: home }, awayTeam: { name: away }, state: { description },
});

test("join: exactly one finished candidate, either home/away order", () => {
  const r = joinMatch([item("1", "Zimbabwe", "Afghanistan"), item("2", "India", "Kenya")], { teams: ["Afghanistan", "Zimbabwe"], startMs: START });
  assert.equal(r.item.id, "1");
});

test("join: ambiguity, absence, women's mix-up, unfinished and distant dates all give up", () => {
  const two = [item("1", "Afghanistan", "Zimbabwe"), item("2", "Zimbabwe", "Afghanistan", "2026-10-23T12:00:00.000Z")];
  assert.equal(joinMatch(two, { teams: ["Afghanistan", "Zimbabwe"], startMs: START }).reason, "ambiguous");
  assert.equal(joinMatch([], { teams: ["Afghanistan", "Zimbabwe"], startMs: START }).reason, "none");
  assert.equal(joinMatch([item("1", "Afghanistan Women", "Zimbabwe Women")], { teams: ["Afghanistan", "Zimbabwe"], startMs: START }).reason, "none");
  assert.equal(joinMatch([item("1", "Afghanistan", "Zimbabwe", undefined, "Live")], { teams: ["Afghanistan", "Zimbabwe"], startMs: START }).reason, "not_finished");
  assert.equal(joinMatch([item("1", "Afghanistan", "Zimbabwe", "2026-10-26T06:00:00.000Z")], { teams: ["Afghanistan", "Zimbabwe"], startMs: START }).reason, "none");
  assert.equal(joinMatch(two, { teams: null, startMs: START }).reason, "no_fixture");
});

test("join: a day of timezone slack is allowed", () => {
  const r = joinMatch([item("1", "Afghanistan", "Zimbabwe", "2026-10-22T22:00:00.000Z")], { teams: ["Afghanistan", "Zimbabwe"], startMs: START });
  assert.equal(r.item.id, "1");
});

function harness({ cap, stored = null, list, detail, now = START + 8 * 3600_000 } = {}) {
  const calls = [];
  const saves = [];
  const fetchImpl = async (url, init) => {
    calls.push(url);
    assert.equal(init.headers["x-rapidapi-host"], "cricket-highlights-api.p.rapidapi.com");
    const ok = (body) => ({ ok: true, status: 200, json: async () => body });
    return url.includes("/matches?date=") ? ok(list ?? { data: [item("54490627", "Afghanistan", "Zimbabwe")], pagination: { totalCount: 1 } }) : ok(detail ?? AFG);
  };
  const fallback = createHighlightlyFallback({
    apiKey: "test-key", fetchImpl, cap, now: () => now,
    loadState: async () => stored,
    saveState: async (s) => { saves.push(s); },
  });
  return { fallback, calls, saves };
}
const ASK = { matchId: "m1", title: "Afghanistan vs Zimbabwe, 2nd ODI", startMs: START };

test("fallback returns the card with two calls and one persisted write", async () => {
  const { fallback, calls, saves } = harness();
  const card = await fallback(ASK);
  assert.equal(card.source, "highlightly");
  assert.equal(card.innings.length, 2);
  assert.equal(calls.length, 2);
  assert.match(calls[0], /\/matches\?date=2026-10-23/);
  assert.match(calls[1], /\/matches\/54490627$/);
  assert.equal(saves.length, 1);
  assert.equal(saves[0].used, 2);
});

test("a match is tried once a day; the date list is cached for the run", async () => {
  const { fallback, calls } = harness();
  await fallback(ASK);
  assert.equal(await fallback(ASK), null);
  assert.equal(calls.length, 2);
  await fallback({ ...ASK, matchId: "m2" });
  assert.equal(calls.length, 3, "second match on the same date costs the detail call only");
});

test("the daily cap is enforced and persisted state counts", async () => {
  const day = new Date(START + 8 * 3600_000).toISOString().slice(0, 10);
  const full = harness({ stored: { day, used: DAILY_CALL_CAP - 1, tried: {} } });
  assert.equal(await full.fallback(ASK), null);
  assert.equal(full.calls.length, 0);
  assert.equal(full.saves.length, 0);
  const room = harness({ stored: { day, used: DAILY_CALL_CAP - 2, tried: {} } });
  assert.ok(await room.fallback(ASK));
  assert.equal(room.saves[0].used, DAILY_CALL_CAP);
  const yesterday = harness({ stored: { day: "2026-10-01", used: DAILY_CALL_CAP, tried: { m1: "2026-10-01" } } });
  assert.ok(await yesterday.fallback(ASK), "a new day resets the counter and the tried set");
});

test("unreadable state, failed save or ambiguous list make no further requests than allowed", async () => {
  const calls = [];
  const blind = createHighlightlyFallback({
    apiKey: "k", fetchImpl: async (u) => { calls.push(u); throw new Error("no"); },
    loadState: async () => { throw new Error("boom"); }, saveState: async () => {},
  });
  assert.equal(await blind(ASK), null);
  assert.equal(calls.length, 0);

  const unsaved = createHighlightlyFallback({
    apiKey: "k", fetchImpl: async (u) => { calls.push(u); throw new Error("no"); },
    loadState: async () => null, saveState: async () => { throw new Error("disk"); },
  });
  assert.equal(await unsaved(ASK), null);
  assert.equal(calls.length, 0);

  const amb = harness({ list: { data: [item("1", "Afghanistan", "Zimbabwe"), item("2", "Zimbabwe", "Afghanistan", "2026-10-23T09:00:00.000Z")], pagination: { totalCount: 2 } } });
  assert.equal(await amb.fallback(ASK), null);
  assert.equal(amb.calls.length, 1, "no detail call without a unique join");
  const cut = harness({ list: { data: [item("1", "Afghanistan", "Zimbabwe")], pagination: { totalCount: 150 } } });
  assert.equal(await cut.fallback(ASK), null);
});

test("env absent means no fallback object and no calls", () => {
  assert.equal(highlightlyKey({}), "");
  assert.equal(highlightlyKey({ RAPIDAPI_KEY: "a" }), "a");
  assert.equal(highlightlyKey({ HIGHLIGHTLY_API_KEY: "b", RAPIDAPI_KEY: "a" }), "b");
  assert.equal(createHighlightlyFallback({ apiKey: "", loadState: async () => null, saveState: async () => {} }), null);
});

test("syncMatch asks the fallback only on a finalising run with no Cricbuzz card", () => {
  const src = readFileSync(new URL("../src/main.js", import.meta.url), "utf8");
  assert.match(src, /finalising && highlightlyFor && \(detail\.scorecard\?\.innings\?\.length \|\| 0\) === 0/);
});
