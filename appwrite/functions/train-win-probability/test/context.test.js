import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { AsOfState, sideContextOf } from "../src/asOfState.js";
import { buildShards, limitedOversRows, prematchContextRows, publishProblems, thinnedElevens, JOIN_FAILURE } from "../src/build.js";
import {
  NEUTRAL_CONTEXT, SHARDS, contextFeatureNames, contextFeatures, familiarityDiff, h2hAdvantage,
  pairKey, shardOf, sideContext, venueKey, xiStrength,
} from "../src/contextmath.js";
import { richSkeletonOf } from "../src/corpus.js";
import { CHASE_FEATURE_NAMES, FIRST_INNINGS_FEATURE_NAMES, PREMATCH_FEATURE_NAMES, chaseFeatures } from "../src/training.js";

const here = (p) => new URL(p, import.meta.url);

/** A two-innings T20 in Cricsheet's shape. `first` bats first and scores `runs[0]`. */
function rawMatch({ date, first, second, winner, runs = [160, 150], venue = "Eden Gardens, Kolkata", city = "Kolkata", xi = true }) {
  const squad = (team) => Array.from({ length: 11 }, (_, i) => `${team} P${i + 1}`);
  const innings = (team, bowlers, total) => ({
    team,
    overs: Array.from({ length: 20 }, (_, o) => ({
      over: o,
      deliveries: Array.from({ length: 6 }, (_, b) => {
        const ball = o * 6 + b;
        const scored = ball < total / 2 ? 2 : 0;
        return { batter: `${team} P1`, bowler: `${bowlers} P11`, non_striker: `${team} P2`, runs: { batter: scored, extras: 0, total: scored } };
      }),
    })),
  });
  const registry = {};
  for (const name of [...squad(first), ...squad(second)]) registry[name] = `id-${name}`;
  return {
    info: {
      teams: [first, second], gender: "male", match_type: "T20", overs: 20, dates: [date], venue, city,
      toss: { winner: first, decision: "bat" },
      outcome: { winner },
      players: xi ? { [first]: squad(first), [second]: squad(second) } : undefined,
      registry: { people: registry },
    },
    innings: [innings(first, second, runs[0]), innings(second, first, runs[1])],
  };
}

test("contextmath.js is byte-identical in every function that carries it", () => {
  const canonical = readFileSync(here("../../match-sync/src/contextmath.js"), "utf8");
  for (const fn of ["train-win-probability", "win-probability"]) {
    assert.equal(readFileSync(here(`../../${fn}/src/contextmath.js`), "utf8"), canonical, `${fn}/src/contextmath.js has drifted`);
  }
});

test("a match's context holds nothing from that match or any later one", () => {
  const history = [
    rawMatch({ date: "2024-01-01", first: "A", second: "B", winner: "A" }),
    rawMatch({ date: "2024-01-08", first: "A", second: "B", winner: "A" }),
  ].map(richSkeletonOf);
  const target = richSkeletonOf(rawMatch({ date: "2024-02-01", first: "A", second: "B", winner: "B" }));

  const contextWith = (later) => {
    const state = new AsOfState();
    for (const m of history) state.update(m);
    const ctx = state.contextFor(target);
    const side = sideContextOf(ctx, target, "A");
    state.update(target);
    for (const m of later) state.update(m);
    return JSON.stringify(side);
  };
  const future = [
    rawMatch({ date: "2024-03-01", first: "B", second: "A", winner: "B", runs: [220, 100] }),
    rawMatch({ date: "2024-03-08", first: "B", second: "A", winner: "B", runs: [230, 90] }),
  ].map(richSkeletonOf);
  assert.equal(contextWith([]), contextWith(future), "matches played afterwards must not reach the row");

  const side = JSON.parse(contextWith([]));
  assert.ok(side.eloDiff > 0, "A won both earlier matches");
  assert.ok(side.h2hAdvantage > 0);
  assert.equal(side.hasVenue, false, "two matches is short of a known ground");
});

test("the head-to-head and the Elo lead flip with the side they are read from", () => {
  const state = new AsOfState();
  for (let i = 0; i < 4; i++) state.update(richSkeletonOf(rawMatch({ date: `2024-01-0${i + 1}`, first: "A", second: "B", winner: "A" })));
  const next = richSkeletonOf(rawMatch({ date: "2024-02-01", first: "B", second: "A", winner: "A" }));
  const ctx = state.contextFor(next);
  const a = sideContextOf(ctx, next, "A");
  const b = sideContextOf(ctx, next, "B");
  assert.ok(a.eloDiff > 0 && Math.abs(a.eloDiff + b.eloDiff) < 1e-12);
  assert.ok(a.h2hAdvantage > 0 && Math.abs(a.h2hAdvantage + b.h2hAdvantage) < 1e-12);
  assert.equal(a.hasVenue, true, "four matches at the ground");
  assert.equal(h2hAdvantage(0, 0, 0), 0, "no meetings is even, not unknown");
  assert.equal(h2hAdvantage(3, 1, 5), -h2hAdvantage(1, 3, 5), "a tie counts for neither side");
});

test("a ground is keyed by name and city, whichever way Cricsheet writes the name", () => {
  assert.equal(venueKey("Eden Gardens", "Kolkata"), venueKey("Eden Gardens, Kolkata", "Kolkata"));
  assert.notEqual(venueKey("County Ground", "Bristol"), venueKey("County Ground", "Taunton"));
  assert.equal(venueKey("", "Kolkata"), null);
  assert.equal(pairKey("B", "A", "male", "T20"), pairKey("A", "B", "male", "T20"));
});

test("familiarity is zero for equal experience and bounded for a one-sided ground", () => {
  assert.equal(familiarityDiff(5, 5), 0);
  assert.ok(familiarityDiff(40, 1) > 0.9 && familiarityDiff(40, 1) <= 1);
  assert.equal(familiarityDiff(0, 100000), -1);
});

test("an eleven nobody could identify is exactly neutral", () => {
  const unknown = { avg: 1, strike: 1, recent: 1, bowl: 1, batBalls: 0, bowlBalls: 0 };
  assert.deepEqual(xiStrength(new Array(11).fill(unknown)), { batAvg: 0, batStrike: 0, batRecent: 0, bowl: 0, coverage: 0 });
  assert.equal(NEUTRAL_CONTEXT.xi, null);
  assert.equal(sideContext({ own: null, opp: null, venue: null }).eloDiff, 0);
});

test("the context vector is the base without its strength term, then team, ground, elevens", () => {
  const s = { currentRunRate: 8, requiredRunRate: 9, wicketsInHand: 7, ballsRemaining: 60, ballsBudget: 120, runsNeeded: 90, target: 170, teamStrengthDiff: 0.3, parRunRate: 8.3, format: "T20" };
  const base = chaseFeatures(s);
  const side = { batAvg: 0.2, batStrike: 0.1, batRecent: 0.3, bowl: 0.4, coverage: 0.9 };
  const c = { ...NEUTRAL_CONTEXT, eloDiff: 0.5, venueScoring: 1.1, hasVenue: true, xi: { own: side, opp: { ...side, bowl: -0.1, coverage: 0.6 } } };

  const core = contextFeatures("chase", base, s, c, false);
  const full = contextFeatures("chase", base, s, c, true);
  const names = contextFeatureNames("chase", CHASE_FEATURE_NAMES, true);
  assert.equal(core.length, contextFeatureNames("chase", CHASE_FEATURE_NAMES, false).length);
  assert.equal(full.length, names.length);
  assert.ok(!names.includes("teamStrengthDiff"), "the all-time strength term is gone");
  assert.ok(!core.includes(0.3), "and so is its value");
  assert.equal(full[names.indexOf("eloDiff")], 0.5);
  assert.equal(full[names.indexOf("oppBowl")], -0.1);
  assert.equal(full[names.indexOf("xiCoverage")], 0.6, "coverage is the weaker side's");
  const parTotal = (8.3 * 120) / 6;
  assert.ok(Math.abs(full[names.indexOf("targetVsVenue")] - (170 / parTotal / 1.1 - 170 / parTotal)) < 1e-12);
  assert.equal(contextFeatureNames("firstInnings", FIRST_INNINGS_FEATURE_NAMES, true).length, 10 + 4 + 5 + 8);
  assert.equal(contextFeatureNames("prematch", PREMATCH_FEATURE_NAMES, false).length, 3 + 4 + 3);
});

test("rows come in both shapes only when the match has an eleven for both sides", () => {
  const state = new AsOfState();
  const build = (raw, elevens) => {
    const match = richSkeletonOf(raw);
    const ctx = state.contextFor(match);
    const xi = elevens ? thinnedElevens(match, ctx, () => 0.4) /* lightest band, and no player missed */ : { A: null, B: null };
    match.context = Object.fromEntries(match.teams.map((t) => [t, sideContextOf(ctx, match, t, (n) => xi[n])]));
    return match;
  };
  const withXi = build(rawMatch({ date: "2024-01-01", first: "A", second: "B", winner: "A" }), true);
  const chase = [...limitedOversRows("chase", withXi)];
  assert.ok(chase.length > 50);
  assert.ok(chase.every((r) => r.full && r.full.length === r.core.length + 5 && r.label === 0), "B chased and lost");
  assert.equal([...prematchContextRows(withXi)][0].label, 1);

  const without = build(rawMatch({ date: "2024-01-02", first: "A", second: "B", winner: "A" }), false);
  assert.ok([...limitedOversRows("firstInnings", without)].every((r) => r.full === null));
});

test("a side that loses too many names has no eleven at all, and the rates are a distribution", () => {
  const match = richSkeletonOf(rawMatch({ date: "2024-01-01", first: "A", second: "B", winner: "A" }));
  const ctx = new AsOfState().contextFor(match);
  let call = 0;
  // First draw picks the side's miss rate (the last, heaviest band); every player is then missed.
  const allMissed = thinnedElevens(match, ctx, () => (call++ % 12 === 0 ? 0.99 : 0));
  assert.equal(allMissed.A, null);
  assert.ok(Math.abs(JOIN_FAILURE.reduce((s, [share]) => s + share, 0) - 1) < 1e-12);
});

test("the published tables put a roster, a ground and a head-to-head where the reader will look", () => {
  const state = new AsOfState();
  for (let i = 1; i <= 3; i++) state.update(richSkeletonOf(rawMatch({ date: `2026-09-0${i}`, first: "A", second: "B", winner: "A" })));
  const { files, players } = buildShards(state, "2026-09-03");
  assert.equal(players, 22);
  assert.equal([...files.keys()].filter((id) => id.startsWith("wpx-")).length, SHARDS);

  const roster = files.get(`wpx-${shardOf("r:A|male")}`).rosters["A|male"];
  assert.equal(roster.length, 11);
  const [id, names, ratings, gender] = roster[0];
  assert.equal(id, "id-A P1");
  assert.deepEqual(names, ["A P1"]);
  assert.equal(ratings.T20.length, 10, "six model terms, then runs, dismissals, innings and wickets");
  assert.equal(ratings.T20[8], 3, "three innings batted");
  assert.equal(gender, "male");

  const ground = files.get(`wpx-${shardOf("v:eden gardens")}`).venues["eden gardens"];
  assert.equal(ground[0].city, "kolkata");
  assert.equal(ground[0].slices["male|T20"][0], 3);
  assert.equal(ground[0].visits["A|male"], 3);

  const key = pairKey("A", "B", "male", "T20");
  assert.deepEqual(files.get(`wpx-${shardOf(`h:${key}`)}`).h2h[key], [3, 3, 0]);
  assert.ok(files.get("wps-p").people.length >= 1, "everyone here has the surname P<n>");
});

test("an old roster is not published", () => {
  const state = new AsOfState();
  state.update(richSkeletonOf(rawMatch({ date: "2020-01-01", first: "A", second: "B", winner: "A" })));
  assert.equal(buildShards(state, "2026-09-03").players, 0);
});

// ── the publish gate ────────────────────────────────────────────────────────────────────────────

function tierWith(names, weightsByName) {
  return {
    weights: names.map((n) => weightsByName[n] ?? 0), bias: 0, temperature: 1,
    featureMeans: names.map(() => 0), featureStds: names.map(() => 1),
    holdout: { logLoss: 0.4, ece: 0.01 },
  };
}

function sensibleModel() {
  const chaseNames = contextFeatureNames("chase", CHASE_FEATURE_NAMES, false);
  const preNames = contextFeatureNames("prematch", PREMATCH_FEATURE_NAMES, false);
  const head = (names, w) => ({ core: tierWith(names, w), full: null, holdout: { logLoss: 0.4 } });
  return {
    chase: head(chaseNames, { logRateRatio: 3, wicketsInHand: 2, runsNeededOverPar: -6 }),
    firstInnings: head(contextFeatureNames("firstInnings", FIRST_INNINGS_FEATURE_NAMES, false), {}),
    prematch: head(preNames, { eloDiff: 1 }),
  };
}

test("a sensible model passes the gate", () => {
  assert.deepEqual(publishProblems(sensibleModel()), []);
});

test("a model that is wrong about a side needing two runs is refused", () => {
  const model = sensibleModel();
  model.chase.core.weights = model.chase.core.weights.map((w) => -w);
  assert.ok(publishProblems(model).some((p) => /^chase:/.test(p)));
});

test("a refit far worse than the model serving is refused, a slightly different one is not", () => {
  const previous = { chase: { holdout: { logLoss: 0.37 } } };
  const model = sensibleModel();
  model.chase.holdout.logLoss = 0.38;
  assert.deepEqual(publishProblems(model, previous), []);
  model.chase.holdout.logLoss = 0.45;
  assert.ok(publishProblems(model, previous).some((p) => /log-loss/.test(p)));
});

test("a miscalibrated tier or a missing head is refused", () => {
  const model = sensibleModel();
  model.prematch.core.holdout.ece = 0.09;
  assert.ok(publishProblems(model).some((p) => /ECE/.test(p)));
  const missing = sensibleModel();
  missing.firstInnings = null;
  assert.ok(publishProblems(missing).some((p) => /no model was fitted/.test(p)));
});
