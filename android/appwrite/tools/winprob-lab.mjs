#!/usr/bin/env node
//
// Win-probability feature lab.
//
// Measures, on the trainer's own temporal holdout, what each group of context features is worth —
// team form, ground, toss, players, momentum — and whether boosted trees beat the logistic model
// once those features exist. Nothing here ships: the output is a table, and the trainer adopts
// only what the table supports.
//
// Every comparison is on IDENTICAL rows. All candidate columns are built once into one wide
// matrix per model and a configuration is a subset of columns, so a difference between two
// configurations cannot come from two different samples.
//
// USAGE
//   node --max-old-space-size=6144 appwrite/tools/winprob-lab.mjs [--zip path] [--models chase,first,long,prematch]
//                                                                  [--gbt] [--out report.json]
//                                                                  [--weather features.json] [--weather-holdout forecast.json]
//
// The archive defaults to ~/Library/Caches/bouncer-winprob-lab/all_json.zip.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const trainerDir = path.resolve(here, "../functions/train-win-probability");
const require = createRequire(path.join(trainerDir, "package.json"));
const AdmZip = require("adm-zip");

const T = await import(path.join(trainerDir, "src/training.js"));
const { richSkeletonOf, outIndex } = await import(path.join(trainerDir, "src/corpus.js"));
const { AsOfState, playerRating, xiStrength } = await import(path.join(trainerDir, "src/asOfState.js"));
const Build = await import(path.join(trainerDir, "src/build.js"));
const { sideContextOf } = await import(path.join(trainerDir, "src/asOfState.js"));
const { contextFeatureNames } = await import(path.join(trainerDir, "src/contextmath.js"));
const { trainGbt, gbtLogits } = await import(path.join(here, "lab/gbt.js"));

const argv = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = argv.indexOf(name);
  return i === -1 ? fallback : argv[i + 1];
};
const zipPath = flag("--zip", path.join(os.homedir(), "Library/Caches/bouncer-winprob-lab/all_json.zip"));
const wanted = new Set(flag("--models", "chase,first,long,prematch").split(","));
const withGbt = argv.includes("--gbt");
const outPath = flag("--out", null);
const weatherPath = flag("--weather", null);
// What was FORECAST for a match, used for holdout rows in place of what happened. A live match is
// served a forecast, so a holdout scored on the weather that turned out would credit the model
// with hindsight it will never have.
const weatherHoldoutPath = flag("--weather-holdout", null);

// Mirrors train-win-probability/src/main.js; the lab has to split where the trainer splits.
const HOLDOUT_FRACTION = 0.2;
const VALIDATION_FRACTION = 0.12;
// The trainer's own settings are `--lr 0.05 --patience 8`. They stop within a few noisy epochs, and
// two fits of the same columns then differ by more than a small group is worth, so the lab
// compares configurations at a smaller step by default and the trainer adopts what it settles on.
const LEARNING_RATE = Number(flag("--lr", "0.02"));
const PATIENCE = Number(flag("--patience", "12"));
const BINARY_OPTIONS = { epochs: 200, batchSize: 512, learningRate: LEARNING_RATE, l2: 1e-4, patience: PATIENCE };
const LONG_OPTIONS = { epochs: 200, batchSize: 512, learningRate: 0.05, l2: 1e-4, patience: 20 };
/** Share of players whose rating is withheld, standing in for a name that could not be joined. */
const JOIN_FAIL = Number(flag("--join-fail", "0"));
/** Rolling-origin folds. One is the trainer's own split; more pool several later windows. */
const FOLDS = Number(flag("--folds", "1"));
const MIN_GAIN = 0.001;

/** Share of per-delivery rows kept. Sampled once, so every configuration sees the same rows. */
const ROW_RATE = { chase: 0.22, first: 0.22, long: 1, prematch: 1 };

/**
 * How often each optional group is withheld from a training row, so the model learns what to do
 * when the live page or a name join does not supply it. Replace with the rates match-sync's
 * `[META]` log lines measure.
 */
const MASK = Object.freeze({
  // A ground the corpus has not seen is already in the training rows as itself — no visits, the
  // format's own scoring — and a ground whose name will not join looks exactly the same.
  venue: { bit: 1, rate: 0 },
  xi: { bit: 2, rate: 0.05 },
  state: { bit: 4, rate: 0.05 },
  recent: { bit: 8, rate: 0.1 },
  partnership: { bit: 16, rate: 0.1 },
  toss: { bit: 32, rate: 0 },
  weather: { bit: 64, rate: 0.2 },
});

const log = (...a) => console.error(...a);
const fmt = (v, d = 4) => (v == null || Number.isNaN(v) ? "n/a" : v.toFixed(d));

function lcg(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

// ── column definitions ───────────────────────────────────────────────────────

/** [name, group]. `base` columns are the shipped v5 features, in the trainer's own order. */
const col = (group, names) => names.map((n) => [n, group]);
// The shipped features, with the strength term in a group of its own. That term is a win rate
// taken over the whole training window, so a training row's own result is inside it; `team` holds
// the same quantity computed as of the match, and the two are compared rather than assumed.
const shipped = (names) => names.map((n) => [n, n === "teamStrengthDiff" ? "strength" : "base"]);

const TEAM = col("team", ["eloDiff", "formDiff", "h2hAdvantage", "asOfStrengthDiff"]);
const TOSS = col("toss", ["tossWonByBatting"]);
const STATE = col("state", ["remainingBatQuality", "creaseQuality", "creaseSet", "hasState"]);
const WEATHER = col("weather", ["rainMm", "humidity", "dewSpread", "windKph", "cloud", "hasWeather"]);

/** The shrunk-rate variants (balls of par-rate prior) and the runs-above-par one, for the crr experiment. */
const CRR_K = [12, 24, 48, 96];
const CRR_VARIANTS = ["a", ...CRR_K.map((k) => `k${k}`), "par"];
/** Baseline columns that read the current run rate; a variant drops them and, but for "a", adds its own `name@variant`. */
const CRR_DROP = {
  chase: ["logRateRatio", "currentRateOverPar", "rateGapOverPar", "urgentRateGap", "last5RateDelta"],
  first: ["currentRateOverPar", "projectedOverPar", "projectedVsVenue", "last5RateDelta"],
};
const crrExtraColumns = (head) => {
  const out = [];
  for (const v of CRR_VARIANTS.slice(1)) {
    const par = v === "par";
    if (head === "chase") {
      out.push(...(par
        ? [["aboveParRuns@par", "base"], ["aboveParUrgent@par", "base"], ["last5VsPar@par", "recent"]]
        : [[`logRateRatio@${v}`, "base"], [`currentRateOverPar@${v}`, "base"], [`rateGapOverPar@${v}`, "base"], [`urgentRateGap@${v}`, "base"], [`last5RateDelta@${v}`, "recent"]]));
    } else {
      out.push(...(par
        ? [["aboveParRuns@par", "base"], ["projectedOverPar@par", "base"], ["projectedVsVenue@par", "venue"], ["last5VsPar@par", "recent"]]
        : [[`currentRateOverPar@${v}`, "base"], [`projectedOverPar@${v}`, "base"], [`projectedVsVenue@${v}`, "venue"], [`last5RateDelta@${v}`, "recent"]]));
    }
  }
  return out;
};

const COLUMNS = {
  chase: [
    ...shipped(T.CHASE_FEATURE_NAMES),
    ...TEAM,
    ...col("venue", ["venueScoring", "venueChaseAdvantage", "targetVsVenue", "familiarityDiff", "hasVenue"]),
    ...TOSS,
    ...col("xi", ["batAvg", "batStrike", "batRecent", "oppBowl", "xiCoverage", "hasXi"]),
    ...STATE,
    ...col("recent", ["last5RateDelta", "last5Wickets", "hasLast5", "last5VsRequired", "lateLast5VsRequired"]),
    ...col("partnership", ["partnershipRuns", "partnershipBalls", "hasPartnership"]),
    ...WEATHER,
    ...crrExtraColumns("chase"),
  ],
  first: [
    ...shipped(T.FIRST_INNINGS_FEATURE_NAMES),
    ...TEAM,
    ...col("venue", ["venueScoring", "venueChaseAdvantage", "projectedVsVenue", "familiarityDiff", "hasVenue"]),
    ...TOSS,
    ...col("xi", ["batAvg", "batStrike", "batRecent", "oppBowl", "oppBatAvg", "oppBatStrike", "ownBowl", "xiCoverage", "hasXi"]),
    ...STATE,
    ...col("recent", ["last5RateDelta", "last5Wickets", "hasLast5"]),
    ...col("partnership", ["partnershipRuns", "partnershipBalls", "hasPartnership"]),
    ...WEATHER,
    ...crrExtraColumns("first"),
  ],
  long: [
    ...shipped(T.LONG_FEATURE_NAMES),
    ...TEAM,
    ...col("venue", ["venueDrawAdvantage", "familiarityDiff", "hasVenue"]),
    ...TOSS,
    ...col("xi", ["batAvgDiff", "bowlDiff", "batRecentDiff", "xiCoverage", "hasXi"]),
    ...STATE,
    ...col("recent", ["last10RateDelta", "last10Wickets", "hasLast10"]),
    ...col("partnership", ["partnershipRuns", "partnershipBalls", "hasPartnership"]),
    ...WEATHER,
    // Rain matters to a Test through the time it takes away, which depends on how much is left.
    ...col("weather", ["rainWithTimeLeft", "rainLate", "cloudWithTimeLeft"]),
  ],
  prematch: [
    ...shipped(T.PREMATCH_FEATURE_NAMES),
    ...TEAM,
    ...col("venue", ["familiarityDiff", "venueScoring", "hasVenue"]),
    ...col("toss", ["tossWonByFirst", "firstBatsFirst"]),
    ...col("xi", ["batAvgDiff", "batStrikeDiff", "batRecentDiff", "bowlDiff", "xiCoverage", "hasXi"]),
    ...WEATHER,
  ],
};

/**
 * E4: time-varying terms. Each context column also enters multiplied by the balls-left fraction
 * (tvB) and the wickets-in-hand fraction (tvW); the chase head adds required rate times each
 * (rrrB, rrrW). A tv column keeps its source group, so a group that is left out takes its terms too.
 * Source columns exclude the crr-dependent ones, so the terms do not depend on the crr variant.
 */
const TV_GROUPS = ["team", "venue", "toss", "xi", "state", "recent", "partnership", "weather"];
const TV_TAGS = ["tvB", "tvW", "rrrW", "rrrB"];
const TV_SOURCE = {};
for (const head of ["chase", "first"]) {
  const src = [];
  COLUMNS[head].forEach(([name, group], idx) => {
    if (TV_GROUPS.includes(group) && !name.includes("@") && !(CRR_DROP[head] ?? []).includes(name)) src.push(idx);
  });
  TV_SOURCE[head] = src;
  COLUMNS[head].push(
    ...src.map((i) => [`${COLUMNS[head][i][0]}*bf@tvB`, COLUMNS[head][i][1]]),
    ...src.map((i) => [`${COLUMNS[head][i][0]}*wf@tvW`, COLUMNS[head][i][1]]),
    ...(head === "chase" ? [["rrr*wf@rrrW", "base"], ["rrr*bf@rrrB", "base"]] : []),
  );
}

/** Which mask bits blank a group. The crease and the remaining order are unknowable without an XI. */
const GROUP_BITS = {
  venue: MASK.venue.bit,
  xi: MASK.xi.bit,
  state: MASK.xi.bit | MASK.state.bit,
  recent: MASK.recent.bit,
  partnership: MASK.partnership.bit,
  toss: MASK.toss.bit,
  weather: MASK.weather.bit,
};

// ── wide row store ───────────────────────────────────────────────────────────

class Wide {
  constructor(name) {
    this.name = name;
    this.columns = COLUMNS[name];
    this.k = this.columns.length;
    this.capacity = 1 << 16;
    this.X = new Float32Array(this.capacity * this.k);
    this.y = new Uint8Array(this.capacity);
    this.match = new Int32Array(this.capacity);
    this.window = new Uint8Array(this.capacity);
    this.mask = new Uint8Array(this.capacity);
    // Whether the row is oriented to the match's first-named side — what the strength term needs
    // to be rebuilt for a fold whose training window is not the trainer's.
    this.firstSide = new Uint8Array(this.capacity);
    // Balls remaining and the innings' ball budget, for the balls-remaining bands (limited overs only).
    this.rem = new Uint16Array(this.capacity);
    this.budget = new Uint16Array(this.capacity);
    this.length = 0;
  }

  push(features, label, matchIndex, window, mask, firstSide = 1, rem = 0, budget = 0) {
    if (features.length !== this.k) throw new Error(`${this.name}: ${features.length} features for ${this.k} columns`);
    if (this.length === this.capacity) this.grow();
    this.X.set(features, this.length * this.k);
    this.y[this.length] = label;
    this.match[this.length] = matchIndex;
    this.window[this.length] = window;
    this.mask[this.length] = mask;
    this.firstSide[this.length] = firstSide;
    this.rem[this.length] = rem;
    this.budget[this.length] = budget;
    this.length++;
  }

  grow() {
    const capacity = this.capacity * 2;
    const widen = (old, Type, width = 1) => {
      const next = new Type(capacity * width);
      next.set(old);
      return next;
    };
    this.X = widen(this.X, Float32Array, this.k);
    this.y = widen(this.y, Uint8Array);
    this.match = widen(this.match, Int32Array);
    this.window = widen(this.window, Uint8Array);
    this.mask = widen(this.mask, Uint8Array);
    this.firstSide = widen(this.firstSide, Uint8Array);
    this.rem = widen(this.rem, Uint16Array);
    this.budget = widen(this.budget, Uint16Array);
    this.capacity = capacity;
  }
}

const WINDOW = { train: 0, valid: 1, holdout: 2 };

// ── context features ─────────────────────────────────────────────────────────

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

function teamFeatures(ctx, side, other) {
  const a = ctx.teams[side];
  const b = ctx.teams[other];
  const firstNamed = Object.keys(ctx.teams)[0];
  const h2h = side === firstNamed ? ctx.h2h.firstAdvantage : -ctx.h2h.firstAdvantage;
  return [(a.elo - b.elo) / 400, a.form - b.form, h2h, a.winRate - b.winRate];
}

function familiarity(ctx, side, other) {
  return clamp(Math.log((ctx.venue.visits[side] + 1) / (ctx.venue.visits[other] + 1)) / 3, -1, 1);
}

const hasVenue = (ctx) => (ctx.venue.matches >= 3 ? 1 : 0);

function tossFeature(match, side) {
  if (!match.toss) return 0;
  return match.toss.winner === side ? 1 : -1;
}

/**
 * Walks one innings and yields its state after every delivery, with the in-match context: the
 * partnership, the last five (or ten) overs, who is at the crease and who is still to come.
 */
function* inningsStates(match, inning, ctx, recentBalls) {
  const xi = match.xi?.[inning.team] ?? [];
  const xiKnown = xi.length >= 9;
  const out = new Uint8Array(match.people.length);
  const faced = new Uint16Array(match.people.length);
  const cumRuns = [0];
  const cumWickets = [0];
  let runs = 0;
  let wickets = 0;
  let legalBalls = 0;
  let pRuns = 0;
  let pBalls = 0;

  for (let i = 0; i < inning.balls.length; i++) {
    const d = T.unpackDelivery(inning.balls[i]);
    runs += d.runs;
    wickets += d.wickets;
    pRuns += d.runs;
    if (d.legal) {
      legalBalls++;
      pBalls++;
      faced[inning.batter[i]]++;
      cumRuns.push(runs);
      cumWickets.push(wickets);
    } else {
      cumRuns[cumRuns.length - 1] = runs;
      cumWickets[cumWickets.length - 1] = wickets;
    }
    const dismissed = outIndex(inning.out[i]);
    if (dismissed >= 0) out[dismissed] = 1;
    if (d.wickets > 0) {
      pRuns = 0;
      pBalls = 0;
    }

    const crease = [inning.batter[i], inning.nonStriker[i]].filter((idx) => !out[idx]);
    let creaseQuality = 0;
    let creaseSet = 0;
    for (const idx of crease) {
      const r = ctx.ratings[idx];
      creaseQuality += ((r.avg - 1) + (r.strike - 1)) / 2;
      creaseSet += Math.min(faced[idx], 30) / 30;
    }
    if (crease.length) {
      creaseQuality /= crease.length;
      creaseSet /= crease.length;
    }
    let remaining = 0;
    let remainingN = 0;
    if (xiKnown) {
      for (const idx of xi) {
        if (out[idx]) continue;
        remaining += ctx.ratings[idx].avg - 1;
        remainingN++;
      }
    }

    const haveRecent = legalBalls >= recentBalls;
    yield {
      d,
      runs,
      wickets,
      legalBalls,
      partnershipRuns: pRuns,
      partnershipBalls: pBalls,
      recentRuns: haveRecent ? runs - cumRuns[legalBalls - recentBalls] : 0,
      recentWickets: haveRecent ? wickets - cumWickets[legalBalls - recentBalls] : 0,
      haveRecent,
      state: [remainingN ? remaining / remainingN : 0, creaseQuality, creaseSet, xiKnown ? 1 : 0],
    };
  }
}

// ── row builders ─────────────────────────────────────────────────────────────


/**
 * One limited-overs feature vector in COLUMNS order, from a state and a context. Shared by the row
 * builder and the fixed-chase probe, so both read the same code. `servingStart` sets the current rate to the
 * required rate, as serving does at the first ball of a chase; otherwise it is runs per over so far.
 */
function limitedVector(o) {
  const { chase, s, budget, par, target, strength, team, scoring, venueKnown, fam, toss, xi, recentBalls, weather } = o;
  const parTotal = (par * budget) / 6;
  const ballsRemaining = budget - s.legalBalls;
  const runsNeeded = target - s.runs;
  const wf = (10 - s.wickets) / 10;
  const requiredRunRate = Math.max((runsNeeded * 6) / ballsRemaining, 0.1);
  const currentRunRate = o.servingStart ? requiredRunRate : (s.runs * 6) / Math.max(s.legalBalls, 1);
  let base;
  let venueShape;
  if (chase) {
    base = T.chaseFeatures({
      currentRunRate, requiredRunRate, wicketsInHand: 10 - s.wickets, ballsRemaining, ballsBudget: budget,
      runsNeeded, target, teamStrengthDiff: strength, parRunRate: par, format: o.format,
    });
    venueShape = target / parTotal / scoring - target / parTotal;
  } else {
    base = T.firstInningsFeatures({
      runsScored: s.runs, currentRunRate, wicketsInHand: 10 - s.wickets, ballsRemaining, ballsBudget: budget,
      teamStrengthDiff: strength, parRunRate: par, format: o.format,
    });
    const projected = s.runs + currentRunRate * (ballsRemaining / 6) * (0.55 + 0.45 * wf);
    venueShape = projected / parTotal / scoring - projected / parTotal;
  }
  const recentRate = (s.recentRuns * 6) / recentBalls;
  // Momentum against what the chase still asks for, not against the innings so far.
  const vsRequired = chase && s.haveRecent ? clamp((recentRate - (runsNeeded * 6) / ballsRemaining) / par, -3, 3) : 0;

  // Variant columns. Every crr term, interactions included, recomputed from a different rate.
  const extras = [];
  const bf = Math.max(ballsRemaining, chase ? 1 : 0) / budget;
  const recentVs = (rate) => (s.haveRecent ? (recentRate - rate) / par : 0);
  const projectedAt = (rate) => s.runs + rate * (Math.max(ballsRemaining, 0) / 6) * (0.55 + 0.45 * wf);
  const venueAt = (projected) => projected / parTotal / scoring - projected / parTotal;
  for (const v of CRR_VARIANTS.slice(1)) {
    if (v === "par") {
      const above = (s.runs - (par * s.legalBalls) / 6) / parTotal;
      if (chase) extras.push(above, above / (0.2 + bf), recentVs(par));
      else {
        const projected = projectedAt(par);
        extras.push(above, projected / parTotal, venueAt(projected), recentVs(par));
      }
    } else {
      const k = Number(v.slice(1));
      const crr = (s.runs * 6 + k * par) / (s.legalBalls + k);
      if (chase) {
        const gap = (requiredRunRate - crr) / par;
        extras.push(Math.log((crr + 0.5) / (requiredRunRate + 0.5)), crr / par, gap, gap / (0.2 + bf), recentVs(crr));
      } else {
        const projected = projectedAt(crr);
        extras.push(crr / par, projected / parTotal, venueAt(projected), recentVs(crr));
      }
    }
  }

  const v = [
    ...base,
    ...team,
    scoring - 1, o.chaseAdvantage, venueShape, fam, venueKnown,
    ...toss,
    ...xi,
    ...s.state,
    recentVs(currentRunRate), s.haveRecent ? s.recentWickets / 3 : 0, s.haveRecent ? 1 : 0,
    ...(chase ? [vsRequired, vsRequired * (1 - ballsRemaining / budget)] : []),
    s.partnershipRuns / (par * 5), Math.min(s.partnershipBalls, 120) / 60, 1,
    ...weather,
    ...extras,
  ];
  const source = TV_SOURCE[chase ? "chase" : "first"];
  const bfRaw = Math.max(ballsRemaining, 0) / budget;
  for (const i of source) v.push(v[i] * bfRaw);
  for (const i of source) v.push(v[i] * wf);
  if (chase) {
    const rr = clamp(requiredRunRate / par, 0, 4);
    v.push(rr * wf, rr * bfRaw);
  }
  return v;
}

function limitedOversRows(match, ctx, winRateFor, kind, weather, emit, random) {
  if (!T.isLimitedOvers(match.formatClass) || !match.winner || match.innings.length < 2) return;
  const chase = kind === "chase";
  const inning = match.innings[chase ? 1 : 0];
  const batting = inning.team;
  const bowling = match.teams.find((t) => t !== batting) ?? "";
  if (!ctx.teams[batting] || !ctx.teams[bowling]) return;
  const budget = match.ballsBudget;
  const par = T.parRunRateFor(match.formatClass);
  const target = chase ? T.inningsTotals(match.innings[0]).runs + 1 : 0;
  const label = match.winner === batting ? 1 : 0;
  const strength = winRateFor(batting) - winRateFor(bowling);
  const recentBalls = match.formatClass === "HUNDRED" ? 25 : 30;

  const team = teamFeatures(ctx, batting, bowling);
  const scoring = ctx.venue.scoring;
  const venueKnown = hasVenue(ctx);
  const fam = familiarity(ctx, batting, bowling);
  const own = ctx.xi[batting];
  const opp = ctx.xi[bowling];
  const xiKnown = own && opp ? 1 : 0;
  const coverage = xiKnown ? Math.min(own.coverage, opp.coverage) : 0;
  const toss = [tossFeature(match, batting)];

  for (const s of inningsStates(match, inning, ctx, recentBalls)) {
    const ballsRemaining = budget - s.legalBalls;
    if (ballsRemaining <= 0 || s.wickets >= 10) continue;
    const runsNeeded = target - s.runs;
    if (chase && runsNeeded <= 0) continue;
    if (random() >= ROW_RATE[kind]) continue;

    const xi = chase
      ? [own?.batAvg ?? 0, own?.batStrike ?? 0, own?.batRecent ?? 0, opp?.bowl ?? 0, coverage, xiKnown]
      : [own?.batAvg ?? 0, own?.batStrike ?? 0, own?.batRecent ?? 0, opp?.bowl ?? 0,
        opp?.batAvg ?? 0, opp?.batStrike ?? 0, own?.bowl ?? 0, coverage, xiKnown];
    emit(
      limitedVector({
        chase, s, budget, par, target, strength, team, scoring, chaseAdvantage: ctx.venue.chaseAdvantage,
        venueKnown, fam, toss, xi, recentBalls, weather, format: match.formatClass,
      }),
      label,
      batting,
      ballsRemaining,
      budget,
    );
  }
}

function longRowsWide(match, ctx, winRateFor, weather, emit) {
  if (!T.isLongTrainable(match.formatClass) || match.innings.length === 0) return;
  const ballsTotal = T.longMatchBalls(match.scheduledDays);
  const runsByTeam = new Map(match.teams.map((t) => [t, 0]));
  let ballsBowled = 0;
  const followedOn = match.innings.length > 2 && match.innings[1]?.team === match.innings[2]?.team;

  for (let idx = 0; idx < match.innings.length; idx++) {
    const inning = match.innings[idx];
    const batting = inning.team;
    if (!runsByTeam.has(batting)) continue;
    const bowling = match.teams.find((t) => t !== batting) ?? "";
    const isFourthInnings = idx === 3;
    let ownUsed = 0;
    let oppUsed = 0;
    for (let j = 0; j <= idx; j++) {
      if (match.innings[j]?.team === batting) ownUsed++;
      else oppUsed++;
    }
    const label = T.longOutcomeFor(match, batting);
    if (label == null) return;
    const strength = winRateFor(batting) - winRateFor(bowling);
    const team = teamFeatures(ctx, batting, bowling);
    const own = ctx.xi[batting];
    const opp = ctx.xi[bowling];
    const xiKnown = own && opp ? 1 : 0;
    const xi = [
      (own?.batAvg ?? 0) - (opp?.batAvg ?? 0),
      (own?.bowl ?? 0) - (opp?.bowl ?? 0),
      (own?.batRecent ?? 0) - (opp?.batRecent ?? 0),
      xiKnown ? Math.min(own.coverage, opp.coverage) : 0,
      xiKnown,
    ];
    const venue = [ctx.venue.drawAdvantage, familiarity(ctx, batting, bowling), hasVenue(ctx)];
    const toss = [tossFeature(match, batting)];
    const startRuns = runsByTeam.get(batting);
    let ballsThisOver = 0;
    let illegal = 0;

    for (const s of inningsStates(match, inning, ctx, 60)) {
      ballsBowled++;
      if (s.d.legal) ballsThisOver++;
      else illegal++;
      if (ballsThisOver < 6) continue;
      ballsThisOver = 0;
      const ballsRemaining = ballsTotal - ballsBowled;
      if (ballsRemaining <= 0 || s.wickets >= 10) continue;

      const lead = startRuns + s.runs - runsByTeam.get(bowling);
      const wicketsInHand = 10 - s.wickets;
      const inningsRunRate = (s.runs * 6) / Math.max(s.legalBalls, 1);
      const base = T.longFeatures({
        lead,
        wicketsInHand,
        inningsIndex: idx,
        ballsRemaining,
        ballsTotal,
        teamStrengthDiff: strength,
        isFourthInnings,
        runsNeeded: isFourthInnings ? Math.max(1 - lead, 0) : 0,
        followedOn,
        inningsUsedDiff: ownUsed - oppUsed,
        inningsRunRate,
        wicketsToTake: wicketsInHand + (ownUsed < 2 ? 10 : 0),
        scheduledDays: match.scheduledDays,
      });
      emit(
        [
          ...base,
          ...team,
          ...venue,
          ...toss,
          ...xi,
          ...s.state,
          s.haveRecent ? (s.recentRuns / 10 - inningsRunRate) / T.TEST_PAR_RUN_RATE : 0,
          s.haveRecent ? s.recentWickets / 3 : 0,
          s.haveRecent ? 1 : 0,
          s.partnershipRuns / 100, Math.min(s.partnershipBalls, 360) / 120, 1,
          ...weather,
          weather[0] * (ballsRemaining / ballsTotal), weather[0] * (1 - ballsRemaining / ballsTotal), weather[4] * (ballsRemaining / ballsTotal),
        ],
        label,
        batting,
      );
    }
    void illegal;
    runsByTeam.set(batting, startRuns + T.inningsTotals(inning).runs);
  }
}

function prematchRowWide(match, ctx, winRateFor, weather, emit) {
  if (!match.winner || match.teams.length !== 2) return;
  const [first, second] = match.teams;
  const a = ctx.xi[first];
  const b = ctx.xi[second];
  const xiKnown = a && b ? 1 : 0;
  const battedFirst = match.innings[0]?.team;
  emit(
    [
      ...T.prematchFeatures({ teamStrengthDiff: winRateFor(first) - winRateFor(second), format: match.formatClass }),
      ...teamFeatures(ctx, first, second),
      familiarity(ctx, first, second), ctx.venue.scoring - 1, hasVenue(ctx),
      tossFeature(match, first), battedFirst ? (battedFirst === first ? 1 : -1) : 0,
      (a?.batAvg ?? 0) - (b?.batAvg ?? 0), (a?.batStrike ?? 0) - (b?.batStrike ?? 0),
      (a?.batRecent ?? 0) - (b?.batRecent ?? 0), (a?.bowl ?? 0) - (b?.bowl ?? 0),
      xiKnown ? Math.min(a.coverage, b.coverage) : 0, xiKnown,
      ...weather,
    ],
    match.winner === first ? 1 : 0,
  );
}

// ── corpus ───────────────────────────────────────────────────────────────────

log(`Reading ${zipPath} ...`);
const zip = new AdmZip(zipPath);
const entries = zip.getEntries().filter((e) => !e.isDirectory && e.entryName.endsWith(".json"));
const matches = [];
for (let i = 0; i < entries.length; i++) {
  const entry = entries[i];
  entries[i] = null;
  try {
    const match = richSkeletonOf(JSON.parse(entry.getData().toString("utf8")));
    if (match) {
      match.file = entry.entryName;
      matches.push(match);
    }
  } catch {
    // Unreadable entries are skipped, as the trainer skips them.
  }
}
matches.sort((a, b) => String(a.date ?? "").localeCompare(String(b.date ?? "")) || a.file.localeCompare(b.file));
log(`${matches.length} matches, RSS ${(process.memoryUsage().rss / 1048576).toFixed(0)} MB`);

const dates = matches.map((m) => m.date).filter(Boolean).sort();
let holdoutCutoff = T.holdoutCutoffDate(dates, HOLDOUT_FRACTION);
let validCutoff = T.holdoutCutoffDate(dates.filter((d) => d < holdoutCutoff), VALIDATION_FRACTION);
// --cutoffs <validCutoff>,<holdoutCutoff> pins the windows (e.g. the trainer's 2024-03-08,2025-01-27).
const pinned = flag("--cutoffs", null);
if (pinned) [validCutoff, holdoutCutoff] = pinned.split(",");
log(`Windows — training < ${validCutoff}, validation < ${holdoutCutoff}, holdout >= ${holdoutCutoff}`);
const windowOf = (date) => (date && date >= holdoutCutoff ? WINDOW.holdout : date && date >= validCutoff ? WINDOW.valid : WINDOW.train);

// The shipped strength feature: training-window win rates, exactly as the trainer builds them.
const counters = {};
for (const match of matches) {
  if (windowOf(match.date) !== WINDOW.train) continue;
  for (const team of match.teams) {
    const c = (counters[T.teamKey(team, match.gender, match.formatClass)] ??= { wins: 0, matches: 0 });
    c.matches++;
  }
  if (match.winner) (counters[T.teamKey(match.winner, match.gender, match.formatClass)] ??= { wins: 0, matches: 0 }).wins++;
}
const trainingRate = (match) => (team) => {
  const c = counters[T.teamKey(team, match.gender, match.formatClass)];
  return c ? T.shrunkWinRate(c.wins, c.matches) : 0.5;
};

const weatherByFile = weatherPath ? JSON.parse(fs.readFileSync(weatherPath, "utf8")) : null;
const forecastByFile = weatherHoldoutPath ? JSON.parse(fs.readFileSync(weatherHoldoutPath, "utf8")) : null;
const NO_WEATHER = [0, 0, 0, 0, 0, 0];

const wide = Object.fromEntries([...wanted].map((name) => [name, new Wide(name)]));
const state = new AsOfState();
const rowRandom = lcg(20261003);
const maskRandom = lcg(771);
const joinRandom = lcg(9091);

for (let m = 0; m < matches.length; m++) {
  const match = matches[m];
  const ctx = state.contextFor(match);
  if (JOIN_FAIL > 0) {
    const unknown = playerRating(null, ctx.means);
    ctx.ratings = ctx.ratings.map((r) => (joinRandom() < JOIN_FAIL ? unknown : r));
    for (const name of match.teams) {
      const members = match.xi?.[name] ?? [];
      ctx.xi[name] = members.length >= 9 ? xiStrength(members.map((i) => ctx.ratings[i])) : null;
    }
  }
  const window = windowOf(match.date);
  const winRateFor = trainingRate(match);
  const matchMask =
    (maskRandom() < MASK.venue.rate ? MASK.venue.bit : 0) |
    (maskRandom() < MASK.xi.rate ? MASK.xi.bit : 0) |
    (maskRandom() < MASK.state.rate ? MASK.state.bit : 0) |
    (maskRandom() < MASK.weather.rate ? MASK.weather.bit : 0);
  const weather =
    (forecastByFile && window === WINDOW.holdout ? forecastByFile[match.file] : weatherByFile?.[match.file]) ?? NO_WEATHER;
  const emitTo = (store, perRow) => (features, label, side, rem = 0, budget = 0) => {
    const rowMask = perRow
      ? (maskRandom() < MASK.recent.rate ? MASK.recent.bit : 0) | (maskRandom() < MASK.partnership.rate ? MASK.partnership.bit : 0)
      : 0;
    store.push(features, label, m, window, matchMask | rowMask, side === undefined || side === match.teams[0] ? 1 : 0, rem, budget);
  };
  if (wide.chase) limitedOversRows(match, ctx, winRateFor, "chase", weather, emitTo(wide.chase, true), rowRandom);
  if (wide.first) limitedOversRows(match, ctx, winRateFor, "first", weather, emitTo(wide.first, true), rowRandom);
  if (wide.long) longRowsWide(match, ctx, winRateFor, weather, emitTo(wide.long, true));
  if (wide.prematch) prematchRowWide(match, ctx, winRateFor, weather, emitTo(wide.prematch, false));
  state.update(match);
}
for (const w of Object.values(wide)) log(`${w.name}: ${w.length} rows x ${w.k} columns`);

// ── v6 as served ─────────────────────────────────────────────────────────────
//
// What the published v6 system scores on the same windows: the trainer's own feature set, context
// and elevens (thinned at JOIN_FAILURE), a `core` and a `full` model per head, and the system
// figure share*full + (1-share)*core.rest, clamped to the serving range. It is a second as-of walk
// at row rate 1 with the trainer's sinks, caps and seeds, so it can be set against the trainer's
// published figures. Nothing here reads the lab's own columns.
const asServed = {};
if (!argv.includes("--no-as-served")) {
  const CAPS = { train: 400_000, valid: 200_000, holdout: 300_000 };
  const NAME = { chase: "chase", first: "firstInnings", prematch: "prematch" };
  const BASE = { chase: T.CHASE_FEATURE_NAMES, first: T.FIRST_INNINGS_FEATURE_NAMES, prematch: T.PREMATCH_FEATURE_NAMES };
  const WIN = ["train", "valid", "holdout"];
  const servedLoss = (sink, fitted, temperature) => {
    if (sink.length === 0) return null;
    const k = sink.numFeatures;
    let loss = 0;
    for (let i = 0; i < sink.length; i++) {
      let z = fitted.bias;
      for (let j = 0; j < k; j++) z += fitted.weights[j] * sink.X[i * k + j];
      const p = Math.min(Build.SERVED_CEILING, Math.max(Build.SERVED_FLOOR, T.sigmoid(z / temperature)));
      loss -= sink.y[i] === 1 ? Math.log(p) : Math.log(1 - p);
    }
    return loss / sink.length;
  };
  const tier = (sinks) => {
    if (sinks.train.length === 0) return null;
    const std = sinks.train.fitStandardizer();
    for (const sink of Object.values(sinks)) sink.applyStandardizer(std);
    const fitted = T.trainBinary(sinks.train, sinks.valid, BINARY_OPTIONS);
    const temperature = T.fitTemperature(sinks.valid, fitted.weights, fitted.bias);
    const holdout = T.binaryMetrics(sinks.holdout, fitted.weights, fitted.bias, temperature);
    return {
      temperature,
      epoch: fitted.epoch,
      holdout,
      served: servedLoss(sinks.holdout, fitted, temperature),
      ece: T.expectedCalibrationError(sinks.holdout, fitted.weights, fitted.bias, temperature),
      rest: sinks.rest
        ? { ...T.binaryMetrics(sinks.rest, fitted.weights, fitted.bias, temperature), served: servedLoss(sinks.rest, fitted, temperature) }
        : null,
    };
  };

  // The trainer's walk, verbatim: elevens thinned with one stream, context set per side.
  const servedState = new AsOfState();
  const servedRandom = lcg(20261003);
  for (const match of matches) {
    const ctx = servedState.contextFor(match);
    const elevens = Build.thinnedElevens(match, ctx, servedRandom);
    match.context = {};
    for (const side of match.teams) match.context[side] = sideContextOf(ctx, match, side, (name) => elevens[name]);
    servedState.update(match);
  }

  for (const head of ["chase", "first", "prematch"]) {
    if (!wanted.has(head)) continue;
    const trainerHead = NAME[head];
    const coreK = contextFeatureNames(trainerHead, BASE[head], false).length;
    const fullK = contextFeatureNames(trainerHead, BASE[head], true).length;
    const core = Object.fromEntries(WIN.map((w) => [w, new T.RowSink(CAPS[w], coreK)]));
    core.rest = new T.RowSink(CAPS.holdout, coreK, 77);
    const full = Object.fromEntries(WIN.map((w, i) => [w, new T.RowSink(CAPS[w], fullK, 5 + i)]));
    for (const match of matches) {
      const w = WIN[windowOf(match.date)];
      const rows = head === "prematch" ? Build.prematchContextRows(match) : Build.limitedOversRows(trainerHead, match);
      for (const row of rows) {
        core[w].push(row.core, row.label);
        if (row.full) full[w].push(row.full, row.label);
        else if (w === "holdout") core.rest.push(row.core, row.label);
      }
    }
    const coreFit = tier(core);
    const fullFit = tier(full);
    const share = full.holdout.seen + core.rest.seen > 0 ? full.holdout.seen / (full.holdout.seen + core.rest.seen) : 0;
    const mix = (a, b) => (fullFit && b != null ? share * a + (1 - share) * b : null);
    asServed[head] = {
      coreOnly: coreFit?.holdout.logLoss ?? null,
      coreOnlyServed: coreFit?.served ?? null,
      system: mix(fullFit?.holdout.logLoss, coreFit?.rest?.logLoss) ?? coreFit?.holdout.logLoss ?? null,
      systemServed: mix(fullFit?.served, coreFit?.rest?.served) ?? coreFit?.served ?? null,
      elevenShare: share,
      coreEce: coreFit?.ece ?? null,
      fullEce: fullFit?.ece ?? null,
      rows: { core: core.holdout.seen, full: full.holdout.seen, rest: core.rest.seen },
      epochs: { core: coreFit?.epoch, full: fullFit?.epoch },
    };
    log(`as served ${head}: system ${fmt(asServed[head].system)} (clamped ${fmt(asServed[head].systemServed)}), core only ${fmt(asServed[head].coreOnly)}, eleven share ${fmt(share, 3)}`);
  }
}

// ── fitting ──────────────────────────────────────────────────────────────────

const UNUSED = 3;

/**
 * Which window each match falls in, per fold.
 *
 * One fold is the trainer's split. With more, the matches that produced rows for this model are
 * cut by date: the last 60% become `FOLDS` consecutive holdout blocks, and each block's model is
 * trained on everything before it. That is legitimate only because every context feature is
 * as-of — and it is the only way a corpus of ~900 Tests yields enough holdout matches to tell a
 * real gain from noise.
 */
function foldWindows(store) {
  if (FOLDS <= 1) {
    const windows = new Uint8Array(matches.length);
    for (let m = 0; m < matches.length; m++) windows[m] = windowOf(matches[m].date);
    return [windows];
  }
  const used = [...new Set(store.match.subarray(0, store.length))].sort((a, b) => a - b);
  const firstHoldout = Math.floor(used.length * 0.4);
  const block = Math.floor((used.length - firstHoldout) / FOLDS);
  const out = [];
  for (let f = 0; f < FOLDS; f++) {
    const start = firstHoldout + f * block;
    const end = f === FOLDS - 1 ? used.length : start + block;
    const validStart = Math.floor(start * (1 - VALIDATION_FRACTION));
    const windows = new Uint8Array(matches.length).fill(UNUSED);
    for (let i = 0; i < used.length; i++) {
      windows[used[i]] = i < validStart ? WINDOW.train : i < start ? WINDOW.valid : i < end ? WINDOW.holdout : UNUSED;
    }
    out.push(windows);
  }
  return out;
}

/** A sink-shaped view of some rows and columns, with masks applied where asked. */
const foldRates = new Map();

/** Shrunk win rates over one fold's training window — the strength term as the trainer builds it. */
function strengthFor(windows) {
  let rates = foldRates.get(windows);
  if (rates) return rates;
  const tally = new Map();
  const at = (key) => tally.get(key) ?? tally.set(key, { wins: 0, matches: 0 }).get(key);
  for (let m = 0; m < matches.length; m++) {
    if (windows[m] !== WINDOW.train) continue;
    const match = matches[m];
    for (const team of match.teams) at(T.teamKey(team, match.gender, match.formatClass)).matches++;
    if (match.winner) at(T.teamKey(match.winner, match.gender, match.formatClass)).wins++;
  }
  rates = (match, team) => {
    const c = tally.get(T.teamKey(team, match.gender, match.formatClass));
    return c ? T.shrunkWinRate(c.wins, c.matches) : 0.5;
  };
  foldRates.set(windows, rates);
  return rates;
}

function slice(store, windows, window, columns, maskMode) {
  const strengthColumn = FOLDS > 1 ? columns.findIndex((c) => store.columns[c][1] === "strength") : -1;
  const rateOf = strengthColumn >= 0 ? strengthFor(windows) : null;
  const rows = [];
  for (let i = 0; i < store.length; i++) if (windows[store.match[i]] === window) rows.push(i);
  const k = columns.length;
  const X = new Float32Array(rows.length * k);
  const y = new Uint8Array(rows.length);
  const match = new Int32Array(rows.length);
  const index = Int32Array.from(rows);
  const bits = columns.map((c) => GROUP_BITS[store.columns[c][1]] ?? 0);
  for (let r = 0; r < rows.length; r++) {
    const i = rows[r];
    const mask = maskMode === "none" ? 0 : maskMode === "all" ? 0xff : store.mask[i];
    for (let j = 0; j < k; j++) X[r * k + j] = bits[j] & mask ? 0 : store.X[i * store.k + columns[j]];
    if (rateOf) {
      const m = matches[store.match[i]];
      const diff = rateOf(m, m.teams[0]) - rateOf(m, m.teams[1]);
      X[r * k + strengthColumn] = store.firstSide[i] ? diff : -diff;
    }
    y[r] = store.y[i];
    match[r] = store.match[i];
  }
  return { X, y, match, index, length: rows.length, numFeatures: k };
}

function standardize(sinks) {
  const { train } = sinks;
  const k = train.numFeatures;
  const means = new Float64Array(k);
  const stds = new Float64Array(k).fill(1);
  for (let i = 0; i < train.length; i++) for (let j = 0; j < k; j++) means[j] += train.X[i * k + j];
  for (let j = 0; j < k; j++) means[j] /= Math.max(train.length, 1);
  const variance = new Float64Array(k);
  for (let i = 0; i < train.length; i++) for (let j = 0; j < k; j++) variance[j] += (train.X[i * k + j] - means[j]) ** 2;
  for (let j = 0; j < k; j++) stds[j] = Math.sqrt(variance[j] / Math.max(train.length, 1)) || 1;
  for (const sink of Object.values(sinks)) {
    for (let i = 0; i < sink.length; i++) for (let j = 0; j < k; j++) sink.X[i * k + j] = (sink.X[i * k + j] - means[j]) / stds[j];
  }
  return { means, stds };
}

/** Per-row loss, the stated probability of the outcome the bar shows, and whether it happened. */
function score(sink, probsOf, classes) {
  const eps = 1e-7;
  const loss = new Float64Array(sink.length);
  const stated = new Float32Array(sink.length);
  const happened = new Uint8Array(sink.length);
  let correct = 0;
  const features = new Array(sink.numFeatures);
  for (let i = 0; i < sink.length; i++) {
    for (let j = 0; j < sink.numFeatures; j++) features[j] = sink.X[i * sink.numFeatures + j];
    const probs = probsOf(features, i, sink);
    const y = sink.y[i];
    const pTrue = classes === 1 ? (y ? probs[0] : 1 - probs[0]) : probs[y];
    loss[i] = -Math.log(Math.min(1 - eps, Math.max(eps, pTrue)));
    if (classes === 1) {
      if ((probs[0] >= 0.5 ? 1 : 0) === y) correct++;
      stated[i] = probs[0];
      happened[i] = y;
    } else {
      let best = 0;
      for (let c = 1; c < probs.length; c++) if (probs[c] > probs[best]) best = c;
      if (best === y) correct++;
      stated[i] = probs[2];
      happened[i] = y === 2 ? 1 : 0;
    }
  }
  return { loss, stated, happened, correct };
}

function summarise(parts) {
  const rows = parts.reduce((n, p) => n + p.loss.length, 0);
  const loss = new Float64Array(rows);
  const stated = new Float32Array(rows);
  const happened = new Uint8Array(rows);
  const bins = 20;
  const sum = new Float64Array(bins);
  const hits = new Float64Array(bins);
  const counts = new Float64Array(bins);
  let at = 0;
  let total = 0;
  let correct = 0;
  let brier = 0;
  for (const p of parts) {
    loss.set(p.loss, at);
    stated.set(p.stated, at);
    happened.set(p.happened, at);
    at += p.loss.length;
    correct += p.correct;
    for (let i = 0; i < p.loss.length; i++) {
      total += p.loss[i];
      brier += (p.stated[i] - p.happened[i]) ** 2;
      const b = Math.min(bins - 1, Math.floor(p.stated[i] * bins));
      sum[b] += p.stated[i];
      hits[b] += p.happened[i];
      counts[b]++;
    }
  }
  let ece = 0;
  for (let b = 0; b < bins; b++) if (counts[b]) ece += (counts[b] / rows) * Math.abs(sum[b] / counts[b] - hits[b] / counts[b]);
  return { rows, logLoss: total / Math.max(rows, 1), accuracy: correct / Math.max(rows, 1), brier: brier / Math.max(rows, 1), ece, loss, stated, happened };
}

const foldCache = new Map();

/**
 * E8: recalibration on the validation split only. "phaseT" is one temperature per phase (balls-left
 * fraction >= 0.75, 0.4-0.75, < 0.4) replacing the single one; "platt" is intercept + slope on the
 * tempered logit, shrunk toward (0, 1) by a factor picked by 2-fold cross-fitting on validation
 * (folds by match parity), since the in-sample best factor is always 1.
 */
const phaseOf = (fraction) => (fraction >= 0.75 ? 0 : fraction >= 0.4 ? 1 : 2);
const softplus = (x) => (x > 30 ? x : Math.log1p(Math.exp(x)));
const bceAt = (z, y) => (y ? softplus(-z) : softplus(z));
function goldenMin(f, lo, hi) {
  const g = (Math.sqrt(5) - 1) / 2;
  let a = lo;
  let b = hi;
  let c = b - g * (b - a);
  let d = a + g * (b - a);
  let fc = f(c);
  let fd = f(d);
  for (let i = 0; i < 40; i++) {
    if (fc < fd) { b = d; d = c; fd = fc; c = b - g * (b - a); fc = f(c); }
    else { a = c; c = d; fc = fd; d = a + g * (b - a); fd = f(d); }
  }
  return (a + b) / 2;
}
function fitPlatt(z, y, rows) {
  let a = 0;
  let s = 1;
  for (let it = 0; it < 30; it++) {
    let g0 = 0, g1 = 0, h00 = 1e-6, h01 = 0, h11 = 1e-6;
    for (const i of rows) {
      const p = 1 / (1 + Math.exp(-(a + s * z[i])));
      const r = p - y[i];
      const w = p * (1 - p);
      g0 += r; g1 += r * z[i];
      h00 += w; h01 += w * z[i]; h11 += w * z[i] * z[i];
    }
    const det = h00 * h11 - h01 * h01;
    if (!(det > 1e-12)) break;
    const da = (h11 * g0 - h01 * g1) / det;
    const ds = (h00 * g1 - h01 * g0) / det;
    a -= da;
    s -= ds;
    if (Math.abs(da) + Math.abs(ds) < 1e-8) break;
  }
  return [a, s];
}
function fitRecal(kind, valid, store, model, temperature) {
  const n = valid.length;
  const raw = new Float64Array(n);
  const f = new Array(valid.numFeatures);
  const phase = new Uint8Array(n);
  for (let i = 0; i < n; i++) {
    let z = model.bias;
    for (let j = 0; j < valid.numFeatures; j++) z += model.weights[j] * valid.X[i * valid.numFeatures + j];
    raw[i] = z;
    const row = valid.index[i];
    phase[i] = phaseOf(store.rem[row] / store.budget[row]);
  }
  if (kind === "phaseT") {
    const scales = [0, 1, 2].map((k) => {
      const rows = [];
      for (let i = 0; i < n; i++) if (phase[i] === k) rows.push(i);
      if (rows.length < 300) return temperature;
      return goldenMin((t) => { let l = 0; for (const i of rows) l += bceAt(raw[i] / t, valid.y[i]); return l; }, 0.2, 6);
    });
    return { scales, apply: (z, k) => z / scales[k] };
  }
  const z = raw.map((x) => x / temperature);
  const all = [...Array(n).keys()];
  const folds = [all.filter((i) => valid.match[i] % 2 === 0), all.filter((i) => valid.match[i] % 2 === 1)];
  const lambdas = [0, 0.25, 0.5, 0.75, 1];
  const cross = lambdas.map((lam) => {
    let l = 0;
    for (let k = 0; k < 2; k++) {
      const [a, s] = fitPlatt(z, valid.y, folds[k]);
      for (const i of folds[1 - k]) l += bceAt(lam * a + (1 + lam * (s - 1)) * z[i], valid.y[i]);
    }
    return l;
  });
  const lambda = lambdas[cross.indexOf(Math.min(...cross))];
  const [a, s] = fitPlatt(z, valid.y, all);
  const ia = lambda * a;
  const is = 1 + lambda * (s - 1);
  return { lambda, intercept: ia, slope: is, apply: (x) => ia + (is * x) / temperature };
}

function fit(store, groups, modelClass, classes, windowsList = null, variant = null, opts = {}) {
  const columns = [];
  const terms = opts.terms ?? [];
  // A crr variant swaps the run-rate columns for its own `name@variant` ones; "a" only drops them.
  // A time-varying column (E4) is kept only when its tag is in opts.terms.
  const dropped = variant ? CRR_DROP[store.name] ?? [] : [];
  store.columns.forEach(([name, group], idx) => {
    if (!groups.includes(group)) return;
    const at = name.indexOf("@");
    if (at >= 0) {
      const tag = name.slice(at + 1);
      if (TV_TAGS.includes(tag) ? !terms.includes(tag) : tag !== variant) return;
    } else if (dropped.includes(name)) return;
    columns.push(idx);
  });
  if (!windowsList && !foldCache.has(store)) foldCache.set(store, foldWindows(store));
  const parts = { full: [], mix: [], bare: [] };
  const matchOf = [];
  const indexOf = [];
  const details = [];
  const models = [];
  const recals = [];
  for (const windows of windowsList ?? foldCache.get(store)) {
    const sinks = {
      train: slice(store, windows, WINDOW.train, columns, "mix"),
      valid: slice(store, windows, WINDOW.valid, columns, "mix"),
      full: slice(store, windows, WINDOW.holdout, columns, "none"),
      mix: slice(store, windows, WINDOW.holdout, columns, "mix"),
      bare: slice(store, windows, WINDOW.holdout, columns, "all"),
    };
    let probsOf;
    if (modelClass === "linear") {
      const { means, stds } = standardize(sinks);
      if (classes === 1) {
        const model = T.trainBinary(sinks.train, sinks.valid, BINARY_OPTIONS);
        const temperature = T.fitTemperature(sinks.valid, model.weights, model.bias);
        models.push({ columns, means, stds, weights: model.weights, bias: model.bias, temperature });
        const rec = opts.recal ? fitRecal(opts.recal, sinks.valid, store, model, temperature) : null;
        if (rec) recals.push({ kind: opts.recal, scales: rec.scales, lambda: rec.lambda, intercept: rec.intercept, slope: rec.slope });
        probsOf = (f, i, sink) => {
          let z = model.bias;
          for (let j = 0; j < f.length; j++) z += model.weights[j] * f[j];
          if (rec) {
            const row = sink.index[i];
            return [T.sigmoid(rec.apply(z, phaseOf(store.rem[row] / store.budget[row])))];
          }
          return [T.sigmoid(z / temperature)];
        };
        details.push(`e${model.epoch} T=${temperature.toFixed(2)}`);
      } else {
        const model = T.trainMulticlass(sinks.train, sinks.valid, LONG_OPTIONS);
        probsOf = (f) => T.predictClasses(f, model);
        details.push(`e${model.epoch}`);
      }
    } else {
      const model = trainGbt(sinks.train, sinks.valid, { classes });
      probsOf = (f) => {
        const logits = gbtLogits(model, f);
        return classes === 1 ? [T.sigmoid(logits[0])] : T.softmax(logits);
      };
      details.push(`${model.rounds}r`);
    }
    for (const key of ["full", "mix", "bare"]) parts[key].push(score(sinks[key], probsOf, classes));
    matchOf.push(sinks.full.match);
    indexOf.push(sinks.full.index);
  }
  const cat = (list) => {
    const out = new Int32Array(list.reduce((n, m) => n + m.length, 0));
    let at = 0;
    for (const m of list) {
      out.set(m, at);
      at += m.length;
    }
    return out;
  };
  const allMatches = cat(matchOf);
  return {
    groups,
    modelClass,
    detail: details.join(" "),
    columns: columns.length,
    full: summarise(parts.full),
    mix: summarise(parts.mix),
    bare: summarise(parts.bare),
    matchOf: allMatches,
    index: cat(indexOf),
    models,
    recals,
  };
}

/**
 * The one fit-and-score path every report block uses. `fit` mirrors the trainer (build.js): population
 * standardiser from train, trainBinary with the trainer's options, temperature on valid, probabilities
 * clamped at 1e-7. A config is fitted once per run and shared, so two blocks cannot disagree on a baseline.
 */
const fitMemo = new Map();
const windowIds = new Map();
function fitShared(store, groups, modelClass, classes, windowsList = null, variant = null, opts = {}) {
  let w = "folds";
  if (windowsList) {
    const id = windowsList.length === 1 ? windowsList[0] : windowsList;
    if (!windowIds.has(id)) windowIds.set(id, windowIds.size);
    w = `w${windowIds.get(id)}`;
  }
  const key = [store.name, groups.join("+"), modelClass, classes, w, variant ?? "", (opts.terms ?? []).join("+"), opts.recal ?? ""].join("|");
  if (!fitMemo.has(key)) fitMemo.set(key, fit(store, groups, modelClass, classes, windowsList, variant, opts));
  return fitMemo.get(key);
}

/** Logs (never throws) when two blocks report different baselines for one config. */
const baselineChecks = [];
function checkBaseline(label, a, b) {
  const diff = Math.abs(a - b);
  baselineChecks.push({ label, a, b, diff });
  if (diff > 1e-6) console.log(`  !! BASELINE MISMATCH ${label}: ${a} vs ${b} (diff ${diff})`);
}

/** Bootstrap over matches of the mean per-row loss difference (candidate − baseline). */
function bootstrap(baseline, candidate, matchOf, rounds = 400, seed = 31) {
  const byMatch = new Map();
  for (let i = 0; i < matchOf.length; i++) {
    let e = byMatch.get(matchOf[i]);
    if (!e) byMatch.set(matchOf[i], (e = { diff: 0, n: 0 }));
    e.diff += candidate[i] - baseline[i];
    e.n += 1;
  }
  const list = [...byMatch.values()];
  const random = lcg(seed);
  const samples = new Float64Array(rounds);
  for (let r = 0; r < rounds; r++) {
    let diff = 0;
    let n = 0;
    for (let i = 0; i < list.length; i++) {
      const e = list[Math.floor(random() * list.length)];
      diff += e.diff;
      n += e.n;
    }
    samples[r] = diff / Math.max(n, 1);
  }
  samples.sort();
  return { lo: samples[Math.floor(rounds * 0.025)], hi: samples[Math.floor(rounds * 0.975)], matches: list.length };
}

/** P(chasing side wins) in a neutral 172-run T20 chase, from the last fold's fitted linear model. */
const PROBE_STATES = [["0 balls", 0, 0], ["1 dot ball", 0, 1], ["1 single", 1, 1], ["15-run over", 15, 6]];
function chaseProbe(store, r) {
  const m = r.models.at(-1);
  if (!m) return null;
  const out = {};
  for (const [label, runs, balls] of PROBE_STATES) {
    const v = limitedVector({
      chase: true,
      s: { runs, legalBalls: balls, wickets: 0, partnershipRuns: runs, partnershipBalls: balls, recentRuns: 0, recentWickets: 0, haveRecent: false, state: [0, 0, 0, 0] },
      budget: 120, par: T.parRunRateFor("T20"), target: 172, strength: 0, team: [0, 0, 0, 0], scoring: 1, chaseAdvantage: 0,
      venueKnown: 0, fam: 0, toss: [0], xi: [0, 0, 0, 0, 0, 0], recentBalls: 30, weather: [0, 0, 0, 0, 0, 0],
      format: "T20", servingStart: balls === 0,
    });
    if (v.length !== store.k) throw new Error(`probe vector ${v.length} for ${store.k} columns`);
    let z = m.bias;
    m.columns.forEach((c, j) => { z += (m.weights[j] * (v[c] - m.means[j])) / m.stds[j]; });
    out[label] = 1 / (1 + Math.exp(-z / m.temperature));
  }
  return out;
}

/**
 * Men's T20 holdout chases, rows with at most 6 legal balls bowled, by target band: the actual
 * chase win rate (match level) against each model's mean p (per match, then across matches).
 * Also the rows at exactly 0 balls bowled (training emits a state only after a delivery, so these
 * are a wide or no-ball before the first legal ball) with the crr they carry.
 */
const TARGET_BANDS = [["<=140", 0, 140], ["141-160", 141, 160], ["161-180", 161, 180], ["181+", 181, 1e9]];
function earlyChaseTable(store, baseline, models) {
  const crrColumn = store.columns.findIndex(([name]) => name === "currentRunRate" || name === "crr" || name === "currentRateOverPar");
  const bandRows = TARGET_BANDS.map(() => new Map());
  const zero = { rows: 0, matches: new Set(), crr: [] };
  const spans = [["baseline", baseline], ...Object.entries(models)];
  const targetOf = new Map();
  for (let i = 0; i < baseline.index.length; i++) {
    const row = baseline.index[i];
    const bowled = store.budget[row] - store.rem[row];
    if (store.budget[row] !== 120 || bowled > 6) continue;
    const m = store.match[row];
    const match = matches[m];
    if (match.formatClass !== "T20" || match.gender !== "male") continue;
    if (!targetOf.has(m)) targetOf.set(m, T.inningsTotals(match.innings[0]).runs + 1);
    const target = targetOf.get(m);
    const b = TARGET_BANDS.findIndex(([, lo, hi]) => target >= lo && target <= hi);
    const byMatch = bandRows[b];
    if (!byMatch.has(m)) byMatch.set(m, { y: store.y[row], p: spans.map(() => []) });
    spans.forEach(([, r], k) => byMatch.get(m).p[k].push(r.full.stated[i]));
    if (bowled === 0) {
      zero.rows++;
      zero.matches.add(m);
      if (crrColumn >= 0) zero.crr.push(store.X[row * store.k + crrColumn]);
    }
  }
  const mean = (xs) => xs.reduce((a, b) => a + b, 0) / Math.max(xs.length, 1);
  const bands = TARGET_BANDS.map(([label], b) => {
    const list = [...bandRows[b].values()];
    const out = { band: label, matches: list.length, actual: list.length ? mean(list.map((e) => e.y)) : null };
    spans.forEach(([name], k) => { out[name] = list.length ? mean(list.map((e) => mean(e.p[k]))) : null; });
    return out;
  });
  return { filter: "male T20 holdout chases, legal balls bowled <= 6", bands, atZeroBalls: { rows: zero.rows, matches: zero.matches.size, crrMean: zero.crr.length ? mean(zero.crr) : null, crrColumn } };
}

const report = { cutoffs: { validCutoff, holdoutCutoff }, masks: MASK, joinFail: JOIN_FAIL, folds: FOLDS, models: {} };

// a1: the lab's holdout against the trainer's published figures (2026-11-23 windows). Pass
// --cutoffs 2024-03-08,2025-01-27 for a like-for-like read; the corpus has since grown.
const REFERENCE = {
  chase: { core: 0.3765, asServed: 0.3765 },
  first: { core: 0.566, asServed: 0.5639 },
  prematch: { core: 0.6266, asServed: 0.6241 },
  long: { core: 0.6034, asServed: null },
};
report.asServed = asServed;
report.a1 = {};
for (const [head, ref] of Object.entries(REFERENCE)) {
  const mine = asServed[head];
  const lab = report.models?.[head];
  report.a1[head] = {
    refCore: ref.core,
    refAsServed: ref.asServed,
    labCoreOnly: mine?.coreOnly ?? null,
    labAsServed: mine?.system ?? null,
    labAsServedClamped: mine?.systemServed ?? null,
    diffCore: mine?.coreOnly != null ? mine.coreOnly - ref.core : null,
    diffAsServed: mine?.system != null && ref.asServed != null ? mine.system - ref.asServed : null,
  };
}

function line(label, r, baseline) {
  const delta = baseline ? r.full.logLoss - baseline.full.logLoss : 0;
  const ci = baseline ? bootstrap(baseline.full.loss, r.full.loss, r.matchOf) : null;
  const row = {
    label,
    modelClass: r.modelClass,
    columns: r.columns,
    logLoss: r.full.logLoss,
    delta,
    ci: ci ? [ci.lo, ci.hi] : null,
    ece: r.full.ece,
    brier: r.full.brier,
    accuracy: r.full.accuracy,
    mixLogLoss: r.mix.logLoss,
    bareLogLoss: r.bare.logLoss,
    detail: r.detail,
  };
  console.log(
    `  ${label.padEnd(34)} ${r.modelClass.padEnd(6)} ${String(r.columns).padStart(3)}c  ` +
    `ll ${fmt(row.logLoss)}  d ${delta >= 0 ? "+" : ""}${fmt(delta)}` +
    (ci ? ` [${fmt(ci.lo)},${fmt(ci.hi)}]` : " ".repeat(19)) +
    `  ece ${fmt(row.ece)}  acc ${fmt(row.accuracy, 3)}  mix ${fmt(row.mixLogLoss)}  bare ${fmt(row.bareLogLoss)}  (${r.detail})`
  );
  fitLog.push({ label, r });
  return row;
}

// ── bands, calibration, latest season ───────────────────────────────────────

const fitLog = [];
const sigmoid = (x) => 1 / (1 + Math.exp(-x));
const percentile = (xs, q) => {
  const a = Float64Array.from(xs).sort();
  return a[Math.min(a.length - 1, Math.floor(a.length * q))];
};

/** Balls-remaining bands, edges scaled to a 120-ball innings (ODI x2.5, The Hundred x5/6). */
const BANDS = ["0-24", "25-60", "61-120", "121+"];
function bandsOf(store, baseline, r) {
  const groups = BANDS.map(() => []);
  for (let i = 0; i < r.index.length; i++) {
    const budget = store.budget[r.index[i]];
    if (!budget) continue;
    const scaled = (store.rem[r.index[i]] * 120) / budget;
    groups[scaled < 25 ? 0 : scaled < 61 ? 1 : scaled < 121 ? 2 : 3].push(i);
  }
  return groups.map((rows, b) => {
    if (!rows.length) return { band: BANDS[b], rows: 0, matches: 0, delta: null, ci: null };
    const base = Float64Array.from(rows, (i) => baseline.full.loss[i]);
    const cand = Float64Array.from(rows, (i) => r.full.loss[i]);
    const mo = Int32Array.from(rows, (i) => r.matchOf[i]);
    let d = 0;
    for (let i = 0; i < rows.length; i++) d += cand[i] - base[i];
    const ci = bootstrap(base, cand, mo);
    return { band: BANDS[b], rows: rows.length, matches: ci.matches, delta: d / rows.length, ci: [ci.lo, ci.hi] };
  });
}

/** Logistic recalibration of logit(p) on the outcome, with a match-clustered bootstrap. */
const CAL_STEP = 0.05;
const CAL_OFF = 300;
const CAL_BINS = 601;
function calibrationOf(stated, happened, matchOf, rounds = 200, seed = 41) {
  const per = new Map();
  for (let i = 0; i < stated.length; i++) {
    const p = Math.min(1 - 1e-6, Math.max(1e-6, stated[i]));
    const bin = Math.max(0, Math.min(CAL_BINS - 1, Math.round(Math.log(p / (1 - p)) / CAL_STEP) + CAL_OFF));
    let e = per.get(matchOf[i]);
    if (!e) per.set(matchOf[i], (e = new Map()));
    const c = e.get(bin);
    if (c) {
      c[0]++;
      c[1] += happened[i];
    } else e.set(bin, [1, happened[i]]);
  }
  const list = [...per.values()].map((e) => [...e.entries()]);
  const solve = (n, y) => {
    let a = 0;
    let s = 1;
    for (let it = 0; it < 30; it++) {
      let g0 = 0, g1 = 0, h00 = 0, h01 = 0, h11 = 0;
      for (let b = 0; b < CAL_BINS; b++) {
        if (!n[b]) continue;
        const z = (b - CAL_OFF) * CAL_STEP;
        const p = sigmoid(a + s * z);
        const w = n[b] * p * (1 - p);
        const res = y[b] - n[b] * p;
        g0 += res; g1 += res * z; h00 += w; h01 += w * z; h11 += w * z * z;
      }
      const det = h00 * h11 - h01 * h01;
      if (!(det > 1e-12)) break;
      const da = (h11 * g0 - h01 * g1) / det;
      const ds = (h00 * g1 - h01 * g0) / det;
      a += da;
      s += ds;
      if (Math.abs(da) + Math.abs(ds) < 1e-9) break;
    }
    return [a, s];
  };
  const tally = (pick) => {
    const n = new Float64Array(CAL_BINS);
    const y = new Float64Array(CAL_BINS);
    for (const e of pick) for (const [b, c] of e) { n[b] += c[0]; y[b] += c[1]; }
    return solve(n, y);
  };
  const [intercept, slope] = tally(list);
  const random = lcg(seed);
  const as = [];
  const ss = [];
  for (let r = 0; r < rounds; r++) {
    const pick = [];
    for (let i = 0; i < list.length; i++) pick.push(list[Math.floor(random() * list.length)]);
    const [a, s] = tally(pick);
    as.push(a);
    ss.push(s);
  }
  return { rows: stated.length, intercept, interceptCI: [percentile(as, 0.025), percentile(as, 0.975)], slope, slopeCI: [percentile(ss, 0.025), percentile(ss, 0.975)] };
}

/** One extra fold: train on everything before the last 12 months of the corpus, score those months. */
const latestSeason = argv.includes("--latest-season");
let latest = null;
if (latestSeason) {
  const last = matches.reduce((d, m) => (m.date && m.date > d ? m.date : d), "");
  const cut = new Date(Date.parse(last) - 365 * 864e5).toISOString().slice(0, 10);
  const prior = [];
  const windows = new Uint8Array(matches.length).fill(UNUSED);
  matches.forEach((m, i) => {
    if (!m.date) return;
    if (m.date >= cut) windows[i] = WINDOW.holdout;
    else prior.push(i);
  });
  const validStart = Math.floor(prior.length * (1 - VALIDATION_FRACTION));
  prior.forEach((m, i) => { windows[m] = i < validStart ? WINDOW.train : WINDOW.valid; });
  latest = { windows, cut, last };
}
report.bands = {};
report.calibration = {};
report.latestSeason = latestSeason ? { cutoff: latest.cut, last: latest.last, heads: {} } : null;

/** The Test head as the trainer fits it (build.js fitLongModel): same rows, caps, windows and loss. */
function trainerLong() {
  const k = T.LONG_FEATURE_NAMES.length;
  const sinks = [new T.RowSink(400_000, k), new T.RowSink(200_000, k), new T.RowSink(300_000, k)];
  for (const { match, rows } of T.walkLongRows(matches, T.isLongTrainable)) {
    const sink = sinks[windowOf(match.date)];
    for (const row of rows) sink.push(row.features, row.label);
  }
  const [train, valid, holdout] = sinks;
  if (!train.length) return null;
  const standardizer = train.fitStandardizer();
  for (const sink of sinks) sink.applyStandardizer(standardizer);
  const model = T.trainMulticlass(train, valid, LONG_OPTIONS);
  const metrics = T.multiclassMetrics(holdout, model);
  return { logLoss: metrics.logLoss, accuracy: metrics.accuracy, rows: metrics.rows, trainRows: train.length, epoch: model.epoch };
}

for (const name of wanted) {
  const store = wide[name];
  if (!store || store.length === 0) continue;
  const classes = name === "long" ? 3 : 1;
  const groupsOf = [...new Set(store.columns.map(([, g]) => g))];
  const optional = groupsOf.filter((g) => !["base", "strength", "team"].includes(g) && (g !== "weather" || weatherByFile));
  console.log(`\n== ${name}: ${store.length} rows; ${FOLDS > 1 ? `${FOLDS} rolling folds` : `holdout >= ${holdoutCutoff}`}; join-fail ${JOIN_FAIL} ==`);
  const rows = [];
  fitLog.length = 0;
  const baseline = fit(store, ["base", "strength"], "linear", classes);
  rows.push(line("v5 features", baseline));
  const asOf = fit(store, ["base", "team"], "linear", classes);
  rows.push(line("as-of team instead of strength", asOf, baseline));

  // Everything below is built on the as-of team features: they are what a live match is served.
  const root = ["base", "team"];
  const single = [];
  for (const group of optional) {
    const r = fit(store, [...root, group], "linear", classes);
    rows.push(line(`+ ${group}`, r, baseline));
    single.push({ group, r });
  }

  // Forward selection. A group is added only when it lowers the loss by MIN_GAIN over what is
  // already chosen AND a bootstrap over matches puts the whole interval below zero.
  let chosen = root;
  let current = asOf;
  let pool = [...optional];
  while (pool.length) {
    let best = null;
    for (const group of pool) {
      const r = chosen.length === root.length ? single.find((s) => s.group === group).r : fit(store, [...chosen, group], "linear", classes);
      if (!best || r.full.logLoss < best.r.full.logLoss) best = { group, r };
    }
    const gain = current.full.logLoss - best.r.full.logLoss;
    const ci = bootstrap(current.full.loss, best.r.full.loss, best.r.matchOf);
    if (gain < MIN_GAIN || ci.hi >= 0) {
      console.log(`  (stop: next best "${best.group}" gains ${fmt(gain)}, interval [${fmt(ci.lo)},${fmt(ci.hi)}])`);
      break;
    }
    chosen = [...chosen, best.group];
    current = best.r;
    pool = pool.filter((g) => g !== best.group);
    rows.push(line(`forward: ${chosen.slice(root.length).join("+")}`, current, baseline));
  }

  const everything = fit(store, [...root, ...optional], "linear", classes);
  rows.push(line("all groups", everything, baseline));

  if (withGbt) {
    rows.push(line(`selected: ${chosen.slice(root.length).join("+") || "team only"}`, fit(store, chosen, "gbt", classes), baseline));
    rows.push(line("all groups", fit(store, [...root, ...optional], "gbt", classes), baseline));
  }
  if (name === "chase" || name === "first") {
    report.bands[name] = fitLog.slice(1).map(({ label, r }) => ({ label, modelClass: r.modelClass, bands: bandsOf(store, baseline, r) }));
  }
  report.calibration[name] = fitLog.map(({ label, r }) => ({ label, modelClass: r.modelClass, ...calibrationOf(r.full.stated, r.full.happened, r.matchOf) }));

  if (latestSeason) {
    const one = [latest.windows];
    const base = fitShared(store, ["base", "strength"], "linear", classes, one);
    const asOfLatest = fitShared(store, root, "linear", classes, one);
    const all = fitShared(store, [...root, ...optional], "linear", classes, one);
    const ci = bootstrap(base.full.loss, all.full.loss, all.matchOf);
    report.latestSeason.heads[name] = {
      rows: base.full.rows,
      matches: ci.matches,
      baseline: base.full.logLoss,
      asOfTeam: asOfLatest.full.logLoss,
      allGroups: all.full.logLoss,
      deltaAllGroups: all.full.logLoss - base.full.logLoss,
      ci: [ci.lo, ci.hi],
      ece: all.full.ece,
    };
    console.log(`  latest season (>= ${latest.cut}): base ${fmt(base.full.logLoss)} as-of ${fmt(asOfLatest.full.logLoss)} all ${fmt(all.full.logLoss)} (${base.full.rows} rows)`);
  }

  if (name === "chase" || name === "first") {
    // The run-rate experiment: each variant at all groups against the baseline at all groups.
    const all = [...root, ...optional];
    const one = latestSeason ? [latest.windows] : null;
    const baseLatest = one ? fitShared(store, all, "linear", classes, one) : null;
    const cal = (r) => calibrationOf(r.full.stated, r.full.happened, r.matchOf);
    const entry = (label, r, variant) => {
      const row = line(`crr ${label}`, r, everything);
      const out = {
        ...row,
        deltaEce: r.full.ece - everything.full.ece,
        bands: bandsOf(store, everything, r),
        calibration: (({ slope, slopeCI, intercept }) => ({ slope, slopeCI, intercept }))(cal(r)),
        probe: name === "chase" ? chaseProbe(store, r) : null,
      };
      out.earlyBand = out.bands[2];
      if (one) {
        const l = fitShared(store, all, "linear", classes, one, variant);
        const ci = bootstrap(baseLatest.full.loss, l.full.loss, l.matchOf);
        out.latestSeason = { logLoss: l.full.logLoss, delta: l.full.logLoss - baseLatest.full.logLoss, ci: [ci.lo, ci.hi], ece: l.full.ece, deltaEce: l.full.ece - baseLatest.full.ece };
      }
      out.gate = row.delta <= -0.001 && row.ci[1] < 0 && out.deltaEce <= 0.005 ? "PASS" : "FAIL";
      return out;
    };
    const baseOut = {
      logLoss: everything.full.logLoss,
      ece: everything.full.ece,
      bands: bandsOf(store, everything, everything),
      calibration: (({ slope, slopeCI, intercept }) => ({ slope, slopeCI, intercept }))(cal(everything)),
      probe: name === "chase" ? chaseProbe(store, everything) : null,
      latestSeason: baseLatest ? { logLoss: baseLatest.full.logLoss, ece: baseLatest.full.ece } : null,
    };
    const variants = {};
    const fits = {};
    for (const v of CRR_VARIANTS) {
      fits[v] = fitShared(store, all, "linear", classes, null, v);
      variants[v] = entry(v, fits[v], v);
    }
    report.crr = report.crr ?? {};
    report.crr[name] = { baseline: baseOut, variants };
    if (name === "chase") {
      report.crr.earlyChase = earlyChaseTable(store, everything, { a: fits.a, k48: fits.k48, par: fits.par });
      console.log(`  earlyChase ${JSON.stringify(report.crr.earlyChase)}`);
    }
    console.log(`  probe baseline ${JSON.stringify(baseOut.probe)}`);
    for (const v of CRR_VARIANTS) console.log(`  probe ${v} ${JSON.stringify(variants[v].probe)} gate ${variants[v].gate}`);
  }
  if (name === "chase" || name === "first") {
    // E4 (time-varying terms) and E8 (recalibration on validation), each against its own base.
    const all = [...root, ...optional];
    const one = latestSeason ? [latest.windows] : null;
    const bases = name === "chase" ? [["baseline", null], ["k48", "k48"]] : [["baseline", null]];
    const e4 = { tvB: ["tvB"], tvW: ["tvW"], tvBW: ["tvB", "tvW"] };
    if (name === "chase") Object.assign(e4, { rrrW: ["rrrW"], rrrB: ["rrrB"], rrr: ["rrrW", "rrrB"], "tvBW+rrr": ["tvB", "tvW", "rrrW", "rrrB"] });
    report.e4e8 = report.e4e8 ?? {};
    report.e4e8[name] = {};
    for (const [baseKey, variant] of bases) {
      const B = variant ? fitShared(store, all, "linear", classes, null, variant) : everything;
      const BL = one ? fitShared(store, all, "linear", classes, one, variant) : null;
      if (!variant) checkBaseline(`${name} e4e8 vs crr`, B.full.logLoss, report.crr[name].baseline.logLoss);
      const fitsHere = {};
      const specs = {};
      const run = (label, opts) => {
        specs[label] = opts;
        fitsHere[label] = fitShared(store, all, "linear", classes, null, variant, opts);
        const r = fitsHere[label];
        const row = line(label, r, B);
        const out = { ...row, deltaEce: r.full.ece - B.full.ece, ece: r.full.ece, opts, recal: r.recals?.[r.recals.length - 1] ?? null, bands: bandsOf(store, B, r) };
        if (one) {
          const l = fitShared(store, all, "linear", classes, one, variant, opts);
          const ci = bootstrap(BL.full.loss, l.full.loss, l.matchOf);
          out.latestSeason = { logLoss: l.full.logLoss, delta: l.full.logLoss - BL.full.logLoss, ci: [ci.lo, ci.hi], ece: l.full.ece, deltaEce: l.full.ece - BL.full.ece };
        }
        out.gate = row.delta <= -0.001 && row.ci[1] < 0 && out.deltaEce <= 0.005 ? "PASS" : "FAIL";
        results[label] = out;
        console.log(`  e4e8 ${name}/${baseKey} ${label}: d ${fmt(row.delta)} [${fmt(row.ci[0])}, ${fmt(row.ci[1])}] dECE ${fmt(out.deltaEce)} ${out.gate}`);
      };
      const results = {};
      for (const [label, terms] of Object.entries(e4)) run(label, { terms });
      for (const recal of ["phaseT", "platt"]) run(recal, { recal });
      const best = (labels) => labels.reduce((a, b) => (results[b].delta < results[a].delta ? b : a));
      const bestE4 = best(Object.keys(e4));
      const bestE8 = best(["phaseT", "platt"]);
      run(`${bestE4}+${bestE8}`, { terms: e4[bestE4], recal: bestE8 });
      report.e4e8[name][baseKey] = {
        baseline: { logLoss: B.full.logLoss, ece: B.full.ece, latestSeason: BL ? { logLoss: BL.full.logLoss, ece: BL.full.ece } : null },
        bestE4,
        bestE8,
        variants: results,
        earlyChase: name === "chase" ? earlyChaseTable(store, B, fitsHere) : null,
      };
      if (name === "chase") console.log(`  e4e8 earlyChase ${baseKey} ${JSON.stringify(report.e4e8[name][baseKey].earlyChase.bands)}`);
    }
  }
  report.repro = baselineChecks;
  report.models[name] = { rows, selected: chosen.slice(root.length) };
}

for (const head of Object.keys(report.a1)) {
  const row = report.models[head]?.rows?.find((r) => r.label === "all groups");
  if (row) report.a1[head].labAllGroups = row.logLoss;
}
if (wanted.has("long")) {
  const t = trainerLong();
  if (t) {
    Object.assign(report.a1.long, { labTrainerLike: t.logLoss, labCoreOnly: t.logLoss, diffCore: t.logLoss - REFERENCE.long.core, trainerLike: t });
  }
}
for (const head of Object.keys(report.a1)) console.log(`a1 ${head}: ${JSON.stringify(report.a1[head])}`);

if (outPath) {
  fs.writeFileSync(outPath, JSON.stringify(report, null, 2));
  log(`Wrote ${outPath}`);
}
