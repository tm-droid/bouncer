import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import {
  archiveTeamName, buildContext, contextFileId, fetchForecast, hasElevens, joinEleven, mergeTables,
  pickGround, shardIdsFor, sideContextFor, FORECAST_TTL_MS, SQUAD_RETRY_MS,
} from "../src/context.js";
import { BUILDS_PER_RUN, createContextStore, newBudget } from "../src/contextstore.js";
import { pairKey, shardOf } from "../src/contextmath.js";
import { parseMatchMeta } from "../src/cricbuzz.js";
import { parseSquads } from "../src/squads.js";

const fixture = (name) =>
  readFileSync(new URL(`../../../../app/src/test/resources/${name}`, import.meta.url), "utf8");

// IND v WI, ODI — the captured team sheet, against tables shaped as the trainer publishes them.
const squads = parseSquads(fixture("cricbuzz_match_squads_xi.html"));
const meta = {
  venue: { id: "27", name: "Eden Gardens", city: "Kolkata", country: "India", timezone: "+05:30", lat: 22.56, lon: 88.34 },
  toss: { winnerId: "2", winnerName: "India", decision: "Batting" },
  dayNight: true,
  startMs: 1791000000000,
  teams: [{ id: "2", name: "India", shortName: "IND" }, { id: "10", name: "West Indies", shortName: "WI" }],
};
const model = {
  asOf: "2026-09-28",
  teamStats: {
    "India|male|ODI": { winRate: 0.62, matchesPlayed: 400, elo: 1720, form: 0.7 },
    "West Indies|male|ODI": { winRate: 0.44, matchesPlayed: 380, elo: 1480, form: 0.35 },
    "India|female|ODI": { winRate: 0.55, matchesPlayed: 120, elo: 1600, form: 0.5 },
  },
  parRunRate: { T20: 8.3, ODI: 5.6, HUNDRED: 8.6 },
  means: { "male|ODI": { runsPerBall: 0.85, runsPerOut: 30, bowlRunsPerBall: 0.85, wicketsPerBall: 0.03, chaseWin: 0.5, firstRatio: 0.9, draw: 0.01 } },
};
const rated = (name, i) => [`pid-${name}`, [name], { ODI: [1.2, 1.1, 1.3, 1.05, 900, i > 6 ? 1500 : 0, 820, 20, 25, i > 6 ? 30 : 0] }, "male"];
const tables = {
  rosters: {
    "India|male": squads.team1.playingXi.map((p, i) => rated(p.name, i)),
    "West Indies|male": squads.team2.playingXi.slice(0, 8).map((p, i) => rated(p.name, i)),
  },
  venues: { "eden gardens": [{ city: "kolkata", slices: { "male|ODI": [20, 19.5, 20, 18, 11, 0] }, visits: { "India|male": 14, "West Indies|male": 2 } }] },
  h2h: { [pairKey("India", "West Indies", "male", "ODI")]: [30, 19, 10] },
  surnames: {},
};
const build = (overrides = {}) => buildContext({ matchId: "151543", meta, squads, model, format: "ODI", gender: "male", tables, now: 1000, ...overrides });

test("a side is found in the archive by name, within its own gender and format", () => {
  assert.equal(archiveTeamName("India", model.teamStats, "male", "ODI"), "India");
  assert.equal(archiveTeamName("India Women", model.teamStats, "female", "ODI"), "India");
  assert.equal(archiveTeamName("india", model.teamStats, "male", "ODI"), "India");
  assert.equal(archiveTeamName("India", model.teamStats, "male", "T20"), null, "no record in that format");
  assert.equal(archiveTeamName("Mars", model.teamStats, "male", "ODI"), null);
});

test("a ground is chosen by city when its name is shared, and alone when it is unique", () => {
  const two = [{ city: "bristol" }, { city: "taunton" }];
  assert.equal(pickGround(two, "Taunton").city, "taunton");
  assert.equal(pickGround(two, "Hove"), null, "two candidates and no city match is no ground");
  assert.equal(pickGround([{ city: "bangalore" }], "Bengaluru").city, "bangalore", "one ground of that name is that ground");
  assert.equal(pickGround(undefined, "Kolkata"), null);
});

test("the context carries both sides, the ground, the record and the elevens", () => {
  const c = build();
  assert.equal(c.rev, 1);
  assert.deepEqual(c.teams.map((t) => [t.key, t.known, t.elo, t.visits]), [["India", true, 1720, 14], ["West Indies", true, 1480, 2]]);
  assert.equal(c.venue.known, true);
  assert.equal(c.venue.matches, 20);
  assert.ok(c.venue.scoring > 1, "this ground scores above the format");
  assert.deepEqual(c.h2h, { games: 30, firstWins: 19, secondWins: 10 });
  assert.equal(c.teams[0].joined, 11);
  assert.equal(c.teams[1].joined, 8);
  assert.equal(hasElevens(c), true, "eight of eleven joined is enough");
  assert.equal(c.teams[0].members[0].pid, "pid-Rohit Sharma");
  assert.deepEqual(c.teams[0].members[0].record, { innings: 25, runs: 820, average: 41, strikeRate: 91.1, wickets: 0 });
  // 5.6 an over for 50 overs is 280; the format's sides make 0.9 of that, and this ground a little more.
  assert.equal(c.venue.typicalFirstInnings, Math.round(280 * 0.9 * c.venue.scoring));
  assert.equal(c.modelAsOf, "2026-09-28");
});

test("read from either side the context is the same facts with the signs turned", () => {
  const c = build();
  const india = sideContextFor(c, "2");
  const windies = sideContextFor(c, "10");
  assert.ok(Math.abs(india.eloDiff - 0.6) < 1e-12);
  assert.ok(Math.abs(india.eloDiff + windies.eloDiff) < 1e-12);
  assert.ok(india.h2hAdvantage > 0 && Math.abs(india.h2hAdvantage + windies.h2hAdvantage) < 1e-12);
  assert.ok(india.familiarityDiff > 0);
  assert.equal(india.hasVenue, true);
  assert.equal(india.venueScoring, windies.venueScoring, "a ground is the same ground for both sides");
  assert.ok(india.xi.own.batAvg > 0);
  assert.equal(sideContextFor(c, "999"), null, "an id that is neither side has no context");
});

test("too few joined names is no eleven, and then neither side has one", () => {
  const thin = { ...tables, rosters: { ...tables.rosters, "West Indies|male": tables.rosters["West Indies|male"].slice(0, 5) } };
  const c = build({ tables: thin });
  assert.equal(c.teams[1].joined, 5);
  assert.equal(c.teams[1].xi, null);
  assert.equal(hasElevens(c), false);
  assert.equal(sideContextFor(c, "2").xi, null, "the model's with-elevens path needs both");
});

test("a player new to the side is found among everyone with that surname's letter", () => {
  const roster = [];
  const surnamePools = { s: [{ id: "pid-rg", names: ["RG Sharma"], ratings: { ODI: [1.4, 1.2, 1.1, 1, 5000, 0] }, gender: "male" }] };
  const players = Array.from({ length: 11 }, (_, i) => ({ id: String(i), name: i === 0 ? "Rohit Sharma" : `Nobody ${i}` }));
  const joined = joinEleven({ players, roster, surnamePools, gender: "male", format: "ODI" });
  assert.equal(joined.joined, 1);
  assert.equal(joined.members[0].pid, "pid-rg");
  assert.equal(joined.strength, null);
  const women = joinEleven({ players, roster, surnamePools, gender: "female", format: "ODI" });
  assert.equal(women.joined, 0, "a man's record is never joined to a women's side");
});

test("one known side against one unknown side is not an edge", () => {
  const c = build({ meta: { ...meta, teams: [meta.teams[0], { id: "77", name: "Atlantis", shortName: "ATL" }] } });
  assert.equal(c.teams[1].known, false);
  const side = sideContextFor(c, "2");
  assert.equal(side.eloDiff, 0);
  assert.equal(side.winRateDiff, 0);
  assert.equal(c.h2h, null);
});

test("before the toss there is a context with no elevens, and nothing invented for them", () => {
  const c = build({ squads: parseSquads(fixture("cricbuzz_match_squads_preview.html")), meta: { ...meta, toss: null } });
  assert.equal(hasElevens(c), false);
  assert.equal(c.toss, null);
  assert.equal(c.teams[0].listed, 0);
});

test("the live page's own meta builds a context with an unknown ground", () => {
  const live = parseMatchMeta(fixture("cricbuzz_commentary_overball.html"));
  const c = buildContext({ matchId: "143584", meta: live, squads: null, model, format: "ODI", gender: "male", tables: mergeTables([]), now: 1 });
  assert.equal(c.venue.name, "Roseworth Terrace");
  assert.equal(c.venue.known, false);
  assert.equal(c.venue.scoring, 1);
  assert.equal(sideContextFor(c, "141").hasVenue, false);
  assert.deepEqual(c.toss, { winnerId: "141", winnerName: "Durham", decision: "Bowling" });
});

test("a match touches the shards of its two rosters, its ground and its record", () => {
  const ids = shardIdsFor({ teamKeys: ["India", "West Indies"], gender: "male", format: "ODI", venue: meta.venue });
  assert.ok(ids.includes(`wpx-${shardOf("r:India|male")}`));
  assert.ok(ids.includes(`wpx-${shardOf("v:eden gardens")}`));
  assert.ok(ids.includes(`wpx-${shardOf(`h:${pairKey("India", "West Indies", "male", "ODI")}`)}`));
  assert.ok(ids.length <= 4);
  assert.deepEqual(shardIdsFor({ teamKeys: [null, null], gender: "male", format: "ODI", venue: null }), []);
  assert.equal(contextFileId("151543"), "mc-151543");
});

test("a forecast keeps the hours ahead and says where it came from", async () => {
  const now = 1_790_000_000_000;
  const hour = Math.floor(now / 3_600_000) * 3600;
  const time = Array.from({ length: 72 }, (_, i) => hour - 5 * 3600 + i * 3600);
  const fill = (v) => time.map(() => v);
  const fetchImpl = async () => new Response(JSON.stringify({ hourly: {
    time, temperature_2m: fill(29.44), relative_humidity_2m: fill(71), dew_point_2m: fill(23.1), precipitation: fill(0.2),
    precipitation_probability: fill(40), wind_speed_10m: fill(12.6), cloud_cover: fill(55),
  } }));
  const f = await fetchForecast({ lat: 22.56, lon: 88.34 }, fetchImpl, now);
  assert.equal(f.source, "open-meteo");
  assert.equal(f.fetchedAt, now);
  assert.equal(f.hours.length, 30);
  assert.ok(f.hours[0].t >= now - 3_600_000, "hours already gone are dropped");
  assert.deepEqual({ ...f.hours[0], t: 0 }, { t: 0, tempC: 29.4, humidity: 71, dewC: 23.1, rainMm: 0.2, rainChance: 40, windKph: 13, cloud: 55 });
  assert.equal(await fetchForecast({ lat: null, lon: null }, fetchImpl, now), null);
});

// ── the store: what is fetched, and how often ───────────────────────────────────────────────────

function harness({ squadsHtml = fixture("cricbuzz_match_squads_xi.html") } = {}) {
  const files = new Map([
    [`wpx-${shardOf("r:India|male")}`, { rosters: { "India|male": tables.rosters["India|male"] } }],
    [`wpx-${shardOf("r:West Indies|male")}`, { rosters: { "West Indies|male": tables.rosters["West Indies|male"] } }],
    [`wpx-${shardOf("v:eden gardens")}`, { venues: tables.venues }],
  ]);
  const calls = { pages: [], writes: [], forecasts: 0, reads: [] };
  let clock = 1_790_000_000_000;
  const store = createContextStore({
    readJson: async (id) => {
      calls.reads.push(id);
      return files.get(id) ?? null;
    },
    writeJson: async (id, value) => {
      calls.writes.push(id);
      files.set(id, value);
    },
    fetchPage: async (url) => {
      calls.pages.push(url);
      return squadsHtml;
    },
    parseMatchMeta: () => meta,
    parseSquads,
    squadsUrl: (id) => `squads/${id}`,
    matchUrl: (id) => `match/${id}`,
    fetchForecast: async () => {
      calls.forecasts++;
      return { source: "open-meteo", fetchedAt: clock, hours: [{ t: clock }] };
    },
    log: () => {},
    error: (m) => { throw new Error(m); },
    now: () => clock,
  });
  return { store, calls, files, advance: (ms) => { clock += ms; } };
}
const ask = (h, extra = {}) => h.store.ensure({ matchId: "151543", meta, model, format: "ODI", gender: "male", budget: newBudget(), ...extra });

test("a context is built once, stored, and then served from memory", async () => {
  const h = harness();
  const first = await ask(h);
  assert.equal(hasElevens(first), true);
  assert.deepEqual(h.calls.pages, ["squads/151543"]);
  assert.ok(h.calls.writes.includes("mc-151543"));
  assert.ok(first.forecast, "the forecast is fetched with the first build");

  const pagesBefore = h.calls.pages.length;
  const writesBefore = h.calls.writes.length;
  const again = await ask(h);
  assert.equal(again.rev, first.rev);
  assert.equal(h.calls.pages.length, pagesBefore, "nothing is fetched for a context that is complete");
  assert.equal(h.calls.writes.length, writesBefore);
  assert.equal(h.store.get("151543").rev, first.rev);
});

test("a cold container reads the stored context instead of rebuilding it", async () => {
  const warm = harness();
  const built = await ask(warm);
  const cold = harness();
  cold.files.set("mc-151543", built);
  const got = await ask(cold);
  assert.equal(got.rev, built.rev);
  assert.deepEqual(cold.calls.pages, []);
});

test("with no build budget left a new match gets no context this run, and no fetch", async () => {
  const h = harness();
  const budget = { builds: 0, forecasts: 0 };
  assert.equal(await ask(h, { budget }), null);
  assert.deepEqual(h.calls.pages, []);
  assert.equal(BUILDS_PER_RUN, 2);
});

test("a team sheet without an eleven is asked for again, but not every minute", async () => {
  const h = harness({ squadsHtml: fixture("cricbuzz_match_squads_preview.html") });
  await ask(h);
  assert.equal(h.calls.pages.length, 1);
  h.advance(60_000);
  await ask(h);
  assert.equal(h.calls.pages.length, 1, "one minute later is too soon");
  h.advance(2 * SQUAD_RETRY_MS);
  await ask(h);
  assert.equal(h.calls.pages.length, 2);
  h.advance(SQUAD_RETRY_MS);
  await ask(h);
  assert.equal(h.calls.pages.length, 2, "each miss lengthens the wait");
});

test("an upcoming fixture has its page fetched for meta, and its team sheet is not chased", async () => {
  const h = harness({ squadsHtml: fixture("cricbuzz_match_squads_preview.html") });
  await ask(h, { meta: null, live: false });
  assert.deepEqual(h.calls.pages, ["match/151543", "squads/151543"]);
  h.advance(10 * SQUAD_RETRY_MS);
  await ask(h, { meta: null, live: false });
  assert.equal(h.calls.pages.length, 2);
});

test("a forecast is refreshed hourly, and a new model rebuilds the context", async () => {
  const h = harness();
  const first = await ask(h);
  h.advance(FORECAST_TTL_MS + 1);
  const later = await ask(h);
  assert.equal(h.calls.forecasts, 2);
  assert.equal(later.rev, first.rev + 1);

  const retrained = { ...model, asOf: "2026-10-05" };
  const rebuilt = await ask(h, { model: retrained });
  assert.equal(rebuilt.modelAsOf, "2026-10-05");
  assert.ok(rebuilt.forecast, "a rebuild keeps the forecast it already had");
});

test("pruning deletes only old context files, and re-checks every candidate itself", async () => {
  const { pruneContexts } = await import("../src/main.js");
  const now = Date.parse("2026-10-20T04:00:00Z");
  const deleted = [];
  let queries = null;
  const storage = {
    listFiles: async (_bucket, q) => {
      queries = q;
      return { files: [
        { $id: "mc-151543", $updatedAt: "2026-10-01T00:00:00.000+00:00" },
        // What a fuzzy or ignored query could hand back: another function's state, and a fresh file.
        { $id: "live-matches", $updatedAt: "2026-09-01T00:00:00.000+00:00" },
        { $id: "mc-999", $updatedAt: "2026-10-19T00:00:00.000+00:00" },
      ] };
    },
    deleteFile: async (_bucket, id) => { deleted.push(id); },
  };
  assert.equal(await pruneContexts({ storage, log: () => {}, error: (m) => { throw new Error(m); }, now }), 1);
  assert.deepEqual(deleted, ["mc-151543"]);
  assert.ok(queries.some((q) => String(q).includes("startsWith")), "selected by id prefix, not by search");
});

test("factors follow the probability to team1's side", async () => {
  const { factorsForTeam1 } = await import("../src/main.js");
  const factors = [{ key: "teams", delta: 0.0612 }, { key: "elevens", delta: -0.03 }, { key: "ground", delta: 0.012 }, { key: "x", delta: 0.011 }];
  assert.deepEqual(factorsForTeam1(factors, true), [{ k: "teams", d: 0.061 }, { k: "elevens", d: -0.03 }, { k: "ground", d: 0.012 }]);
  assert.deepEqual(factorsForTeam1(factors, false)[0], { k: "teams", d: -0.061 });
  assert.deepEqual(factorsForTeam1(undefined, true), [], "a Test or the heuristic has none");
});
