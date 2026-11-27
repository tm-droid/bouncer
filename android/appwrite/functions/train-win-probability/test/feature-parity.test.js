import test from "node:test";
import assert from "node:assert/strict";
import * as trainer from "../src/training.js";
import * as reader from "../../match-sync/src/winprob.js";

/**
 * The trainer writes the feature vector; `winprob.js` reads it back at inference. They live in
 * separate Appwrite function archives with no shared package, so the vectors are duplicated code,
 * and a drift between them is the worst kind of bug this system can have: nothing throws, nothing
 * logs, the model simply scores a live match against weights fitted for a different quantity.
 *
 * These assertions are the only thing standing between an edit to one copy and a silently wrong
 * probability. Run over a grid of states rather than one, because a reordered pair of features
 * agrees on any state where the two happen to be equal.
 */

const CHASE_STATES = [];
for (const currentRunRate of [0, 4.2, 7.5, 13.1]) {
  for (const requiredRunRate of [0.1, 6, 8.5, 18]) {
    for (const wicketsInHand of [1, 6, 10]) {
      for (const [ballsRemaining, ballsBudget, format] of [[1, 120, "T20"], [60, 120, "T20"], [180, 300, "ODI"], [40, 100, "HUNDRED"]]) {
        CHASE_STATES.push({
          currentRunRate,
          requiredRunRate,
          wicketsInHand,
          ballsRemaining,
          ballsBudget,
          runsNeeded: Math.round((requiredRunRate * ballsRemaining) / 6),
          runsScored: 64,
          target: 64 + Math.round((requiredRunRate * ballsRemaining) / 6),
          teamStrengthDiff: 0.17,
          parRunRate: trainer.parRunRateFor(format),
          format,
        });
      }
    }
  }
}

const LONG_STATES = [];
for (const lead of [-320, -40, 0, 85, 410]) {
  for (const wicketsInHand of [1, 5, 10]) {
    for (const inningsIndex of [0, 1, 2, 3]) {
      for (const ballsRemaining of [12, 900, 2600]) {
        LONG_STATES.push({
          lead,
          wicketsInHand,
          inningsIndex,
          ballsRemaining,
          ballsTotal: 2700,
          teamStrengthDiff: -0.08,
          isFourthInnings: inningsIndex === 3,
          runsNeeded: inningsIndex === 3 ? Math.max(1 - lead, 0) : 0,
          followedOn: inningsIndex >= 2,
          inningsUsedDiff: inningsIndex % 2 === 0 ? 1 : 0,
          inningsRunRate: 3.4,
          wicketsToTake: wicketsInHand + (inningsIndex < 2 ? 10 : 0),
          scheduledDays: 5,
        });
      }
    }
  }
}

function assertSameVector(a, b, label, state) {
  assert.equal(a.length, b.length, `${label}: length differs for ${JSON.stringify(state)}`);
  for (let i = 0; i < a.length; i++) {
    assert.ok(
      Object.is(a[i], b[i]) || Math.abs(a[i] - b[i]) < 1e-12,
      `${label}: feature ${i} differs (${a[i]} vs ${b[i]}) for ${JSON.stringify(state)}`,
    );
  }
}

test("the chase feature vector is identical in the trainer and the reader", () => {
  for (const state of CHASE_STATES) {
    assertSameVector(trainer.chaseFeatures(state), reader.chaseFeatures(state), "chase", state);
  }
});

test("the first-innings feature vector is identical in the trainer and the reader", () => {
  for (const state of CHASE_STATES) {
    assertSameVector(
      trainer.firstInningsFeatures(state),
      reader.firstInningsFeatures(state),
      "firstInnings",
      state,
    );
  }
});

test("the long-format feature vector is identical in the trainer and the reader", () => {
  for (const state of LONG_STATES) {
    assertSameVector(trainer.longFeatures(state), reader.longFeatures(state), "long", state);
  }
});

test("the reader expects exactly as many features as the trainer emits", () => {
  // winprob.js hardcodes these counts in its `usable` checks, which is what makes it refuse a
  // mismatched artifact instead of scoring against whatever lines up.
  assert.equal(trainer.CHASE_FEATURE_NAMES.length, 16);
  assert.equal(trainer.FIRST_INNINGS_FEATURE_NAMES.length, 11);
  assert.equal(trainer.LONG_FEATURE_NAMES.length, 16);
});

test("the team-stats key is built identically on both sides", () => {
  for (const [team, gender, format] of [
    ["India", "male", "T20"], ["Australia", "female", "ODI"], ["  Kent  ", "male", "TEST"],
    ["London Spirit", "female", "HUNDRED"],
  ]) {
    assert.equal(trainer.teamKey(team, gender, format), reader.teamKey(team, gender, format));
  }
  assert.equal(trainer.teamKey("India"), reader.teamKey("India"), "the defaults must agree too");
});

test("par run rates agree, and the artifact's own copy overrides the reader's constants", () => {
  for (const format of ["T20", "ODI", "HUNDRED", "T10"]) {
    assert.equal(trainer.PAR_RUN_RATE[format], reader.FORMAT_PAR_RUN_RATE[format]);
  }
  // A retrain can move these without a matching deploy of the reader, which is the point of
  // shipping them in the model.
  assert.equal(reader.parRunRate("T20", { parRunRate: { T20: 9.9 } }), 9.9);
  assert.equal(reader.parRunRate("T20", null), trainer.PAR_RUN_RATE.T20);
});

test("prior shrinkage behaves identically in the trainer and the reader", () => {
  const priors = [0.37, 0.23, 0.40];
  const probs = [0.1, 0.2, 0.7];
  for (const progress of [0, 0.1, 0.25, 0.5, 1]) {
    assert.deepEqual(
      trainer.shrinkToPrior(probs, priors, progress, 0.25),
      reader.shrinkToPrior(probs, priors, progress, 0.25),
    );
  }
  assert.equal(reader.MODEL_VERSION, trainer.MODEL_VERSION, "reader and writer must agree on the contract");
});

// ── rolling par and time-varying terms (D1) ──────────────────────────────────

import * as ctx from "../src/contextmath.js";
import * as readerCtx from "../../match-sync/src/contextmath.js";
import { rollingParTable } from "../src/build.js";

const entry = (date, runs, balls = 240, formatClass = "T20", gender = "male") => ({ formatClass, gender, date, runs, balls });
const ramp = (n, start = "2024-01-01", runs = 300) =>
  Array.from({ length: n }, (_, i) => entry(new Date(Date.parse(start) + i * 864e5).toISOString().slice(0, 10), runs));

test("rolling par is as of the match date: same-day matches and later ones are excluded", () => {
  const rows = [...ramp(40), entry("2024-02-10", 600), entry("2024-02-10", 600), entry("2024-02-11", 300)];
  const at = ctx.rollingParAsOf(rows);
  assert.equal(at[0].N, null, "fewer than 30 earlier matches");
  assert.equal(at[29].N, null, "29 earlier matches is still thin");
  assert.equal(at[30].N, 7.5, "30 earlier matches at 300 runs per 240 balls");
  // Both 2024-02-10 high-scoring matches see only strictly earlier days, so neither sees the other.
  assert.equal(at[40].N, at[41].N);
  assert.equal(at[40].N, 7.5);
  assert.ok(at[42].N > 7.5, "the next day sees both");
});

test("the window is 300 matches or 365 days, per format and gender", () => {
  const rows = [...ramp(320, "2022-01-01", 240), ...ramp(40, "2024-01-01", 360), entry("2025-01-01", 300)];
  const at = ctx.rollingParAsOf(rows);
  const last = at[at.length - 1];
  assert.ok(Math.abs(last.N - 6.4) < 1e-9, "the last 300 matches: 260 at 6.0 and 40 at 9.0");
  assert.equal(last.D, 9, "only the 2024 matches are inside 365 days of 2025-01-01");
  const other = ctx.rollingParAsOf([...ramp(40), entry("2025-01-01", 300, 240, "ODI"), entry("2025-01-01", 300, 240, "T20", "female")]);
  assert.equal(other[40].N, null);
  assert.equal(other[41].N, null);
});

test("the shipped table is the latest window, and a thin window is left out", () => {
  const table = ctx.rollingParLatest([...ramp(35), ...ramp(5, "2024-03-01", 300).map((e) => ({ ...e, formatClass: "ODI" }))]);
  assert.equal(table["T20|male"].N, 7.5);
  assert.equal(table["T20|male"].D, 7.5);
  assert.equal(table["ODI|male"], undefined);
  assert.equal(ctx.shippedRollingPar(table, "chase", "T20", "male"), 7.5);
  assert.equal(ctx.shippedRollingPar(table, "firstInnings", "ODI", "male"), null);
  assert.equal(ctx.shippedRollingPar(undefined, "chase", "T20", "male"), null);
});

test("rollingParTable stamps each limited-overs match and ignores Tests and undated ones", () => {
  const inn = (runs, balls) => ({ balls: Array.from({ length: balls }, (_, i) => ({ runs: i < runs ? 1 : 0, legal: true })) });
  const mk = (date, formatClass = "T20") => ({ formatClass, gender: "male", date, innings: [inn(100, 120), inn(80, 120)] });
  const matches = [mk("2024-01-01"), mk("2024-01-02"), mk("2024-01-03", "TEST"), { ...mk("2024-01-04"), date: null }];
  const table = rollingParTable(matches);
  assert.equal(matches[2].rollingPar, undefined);
  assert.equal(matches[3].rollingPar, undefined);
  assert.deepEqual(matches[0].rollingPar, { N: null, D: null });
  assert.deepEqual(table, {}, "two matches never reach 30");
});

test("the par moves exactly the replaced columns, identically in the trainer and the reader", () => {
  for (const [head, build, readBuild, fmt] of [
    ["chase", trainer.chaseFeatures, reader.chaseFeatures, CHASE_STATES[60]],
    ["firstInnings", trainer.firstInningsFeatures, reader.firstInningsFeatures, CHASE_STATES[101]],
  ]) {
    const a = build(fmt);
    const b = build({ ...fmt, parRunRate: fmt.parRunRate + 1.3 });
    const moved = a.map((v, i) => (v === b[i] ? -1 : i)).filter((i) => i >= 0);
    assert.deepEqual(moved.sort((x, y) => x - y), ctx.RP_INDEX[head], `${head}: the par touches only the replaced columns`);
    assert.deepEqual(readBuild({ ...fmt, parRunRate: fmt.parRunRate + 1.3 }), b);
    assert.deepEqual(ctx.withRollingPar(head, a, b), b);
    assert.deepEqual(readerCtx.withRollingPar(head, a, b), b);
  }
});

test("time-varying terms are first-innings only, scale by the balls-left fraction, and agree across copies", () => {
  const s = { ...CHASE_STATES[101], ballsRemaining: 30, ballsBudget: 120 };
  const c = { eloDiff: 0.5, formDiff: 0.1, h2hAdvantage: 0.05, winRateDiff: 0.02, venueScoring: 1.1, venueChaseAdvantage: 0.03, venueDrawAdvantage: 0, familiarityDiff: 0.2, hasVenue: true, xi: null };
  const base = trainer.firstInningsFeatures(s);
  const plain = ctx.contextFeatures("firstInnings", base, s, c, false);
  const tv = ctx.contextFeatures("firstInnings", base, s, c, false, true);
  const names = ctx.contextFeatureNames("firstInnings", trainer.FIRST_INNINGS_FEATURE_NAMES, false, true);
  assert.equal(tv.length, names.length);
  assert.ok(ctx.hasTimeVaryingTerms(names));
  assert.ok(!ctx.hasTimeVaryingTerms(ctx.contextFeatureNames("firstInnings", trainer.FIRST_INNINGS_FEATURE_NAMES, false)));
  assert.deepEqual(tv.slice(0, plain.length), plain, "the plain columns come first, unchanged");
  const extra = tv.slice(plain.length);
  assert.equal(extra.length, names.filter((n) => n.endsWith(ctx.TV_SUFFIX)).length);
  const first = names.findIndex((n) => n.endsWith(ctx.TV_SUFFIX));
  const source = names.indexOf(names[first].slice(0, -ctx.TV_SUFFIX.length));
  assert.ok(Math.abs(extra[0] - tv[source] * 0.25) < 1e-12, "each appended column is its context column times balls left over budget")
  assert.deepEqual(readerCtx.contextFeatures("firstInnings", base, s, c, false, true), tv);
  assert.deepEqual(ctx.contextFeatures("chase", trainer.chaseFeatures(s), s, c, false, true), ctx.contextFeatures("chase", trainer.chaseFeatures(s), s, c, false), "chase never takes the terms");
});

test("end to end: the trainer's row vector equals the vector serving builds for the same state", async () => {
  const { limitedOversRows } = await import("../src/build.js");
  const rows = (runs, n, wicketEvery = 0) => Array.from({ length: n }, (_, i) => trainer.packDelivery(runs, wicketEvery && (i + 1) % wicketEvery === 0 ? 1 : 0, true));
  const c = { eloDiff: 0.5, formDiff: 0.1, h2hAdvantage: 0.05, winRateDiff: 0.02, venueScoring: 1.1, venueChaseAdvantage: 0.03, venueDrawAdvantage: 0, familiarityDiff: 0.2, hasVenue: true, xi: null };
  const match = {
    formatClass: "T20", gender: "male", winner: "B", ballsBudget: 120, teams: ["A", "B"],
    innings: [{ team: "A", balls: rows(2, 60, 7) }, { team: "B", balls: rows(1, 40, 9) }],
    context: { A: c, B: c },
    rollingPar: { N: 9.1, D: 8.3 },
  };
  const shipped = { "T20|male": { N: match.rollingPar.N, D: match.rollingPar.D } };
  const target = trainer.inningsTotals(match.innings[0]).runs + 1;

  for (const head of ["chase", "firstInnings"]) {
    const chase = head === "chase";
    const inning = match.innings[chase ? 1 : 0];
    const produced = [...limitedOversRows(head, match)];
    assert.ok(produced.length > 10, `${head}: rows were produced`);
    const names = readerCtx.contextFeatureNames(head, chase ? trainer.CHASE_FEATURE_NAMES : trainer.FIRST_INNINGS_FEATURE_NAMES, false, !chase);

    let runs = 0, wickets = 0, faced = 0, k = 0;
    for (const code of inning.balls) {
      const d = trainer.unpackDelivery(code);
      runs += d.runs; wickets += d.wickets; if (d.legal) faced++;
      const ballsRemaining = 120 - faced;
      const runsNeeded = target - runs;
      if (ballsRemaining <= 0 || wickets >= 10 || (chase && runsNeeded <= 0)) continue;
      // The state serving derives from a live scoreboard, written out independently of the trainer.
      const state = {
        runsScored: runs, currentRunRate: (runs * 6) / Math.max(faced, 1), requiredRunRate: chase ? Math.max((runsNeeded * 6) / ballsRemaining, 0.1) : 0,
        wicketsInHand: 10 - wickets, ballsRemaining, ballsBudget: 120, runsNeeded, target: chase ? target : 0,
        teamStrengthDiff: 0, parRunRate: 8.0, format: "T20",
      };
      state.parRunRate = trainer.parRunRateFor("T20");
      const builder = chase ? reader.chaseFeatures : reader.firstInningsFeatures;
      let base = builder(state);
      const rolling = readerCtx.shippedRollingPar(shipped, head, "T20", "male");
      if (rolling !== null) base = readerCtx.withRollingPar(head, base, builder({ ...state, parRunRate: rolling }));
      const served = readerCtx.contextFeatures(head, base, state, c, false, readerCtx.hasTimeVaryingTerms(names));
      assert.deepEqual(produced[k].core, served, `${head}: ball ${k}`);
      assert.equal(served.length, names.length, `${head}: width follows the names`);
      k++;
    }
    assert.equal(k, produced.length, `${head}: same number of states`);
  }
});

test("the table buildModel publishes as model.rollingPar is keyed format|gender with both windows", () => {
  const inn = (runs, balls) => ({ balls: Array.from({ length: balls }, (_, i) => trainer.packDelivery(i < runs ? 1 : 0, 0, true)) });
  const day = (i) => new Date(Date.UTC(2023, 0, 1 + i)).toISOString().slice(0, 10);
  const matches = [];
  for (let i = 0; i < 60; i++) {
    matches.push({ formatClass: "T20", gender: i % 2 ? "male" : "female", date: day(i), innings: [inn(120, 120), inn(100, 120)] });
    matches.push({ formatClass: "ODI", gender: "male", date: day(i), innings: [inn(250, 300), inn(200, 300)] });
  }
  const table = rollingParTable(matches);
  assert.deepEqual(Object.keys(table).sort(), ["ODI|male", "T20|female", "T20|male"]);
  for (const entry of Object.values(table)) {
    assert.ok(Number.isFinite(entry.N) && Number.isFinite(entry.D), "both windows present");
  }
  assert.ok(Math.abs(table["ODI|male"].N - (6 * 450) / 600) < 1e-9);
});
