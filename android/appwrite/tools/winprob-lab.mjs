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
    ["gamesLogDiff@e5g", "team"],
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

/**
 * E2: era. The par run rate is a constant per format; scoring has risen. `rpN` / `rpD` recompute the
 * par-dependent shipped features from an as-of rolling mean (previous RP_MATCHES matches, previous
 * 365 days, same format and gender, strictly earlier dates). They replace the originals when
 * opts.rp names the tag. `eraY` / `eraP` are required rate times the era (years since 2015, or the
 * rolling par over the constant par); they are kept through opts.terms like the tv terms.
 */
const RP_TAGS = ["rpN", "rpD"];
const RP_MATCHES = 300;
const RP_MIN_MATCHES = 30;
const RP_INDEX = { chase: [3, 4, 8, 10, 11, 12], first: [0, 1, 5] };
const RP_NAMES = { chase: RP_INDEX.chase.map((i) => T.CHASE_FEATURE_NAMES[i]), first: RP_INDEX.first.map((i) => T.FIRST_INNINGS_FEATURE_NAMES[i]) };
TV_TAGS.push("eraY", "eraP");
for (const head of ["chase", "first"]) {
  for (const tag of RP_TAGS) COLUMNS[head].push(...RP_NAMES[head].map((n) => [`${n}@${tag}`, "base"]));
}
COLUMNS.chase.push(["rrr*era@eraY", "base"], ["rrr*rp@eraP", "base"]);
// E3: zero here, overwritten per fold in slice(). `res` replaces the hand-set wicket factor.
COLUMNS.chase.push(["runsNeededRate@res", "base"]);
COLUMNS.first.push(["projectedOverPar@res", "base"], ["zScore@twoStage", "base"]);
// E5: zero here, overwritten per fold in slice() when opts.e5g is set (log1p games difference).
COLUMNS.chase.push(["gamesLogDiff@e5g", "team"]);
COLUMNS.first.push(["gamesLogDiff@e5g", "team"]);

// E5: Elo update-rule variants, each a full as-of walk (same decided-match rule as AsOfState.update).
// K0 is the base K; `kg` makes K = K0 * max(kMin, 1/sqrt(1 + games/g0)) per side; `mov` scales the delta by
// ln(1 + norm) * 2.2 / (winnerRatingLead * 0.001 + 2.2); `tau` pulls both ratings toward the slice mean by
// 1 - exp(-daysIdle/tau) before the match; `m` shrinks each side's deviation from 1500 by games/(games+m).
let ACTIVE_ELO = null;
const ELO_SPECS = {
  prod: { K0: 24 },
  kg1: { K0: 48, kg: { g0: 10, kMin: 0.5 } },
  kg2: { K0: 64, kg: { g0: 20, kMin: 0.375 } },
  mov90: { K0: 90, mov: true },
  mov120: { K0: 120, mov: true },
  idle365: { K0: 24, tau: 365 },
  idle730: { K0: 24, tau: 730 },
  shrink10: { K0: 24, m: 10 },
  shrink30: { K0: 24, m: 30 },
};
const eloTables = new Map();

/** Margin of a decided limited-overs result in [0, ~1]: runs/first total batting first; (wickets in hand + balls left)/2 chasing; 0.3 when unknown. */
function marginNorm(match) {
  if (!match.winner || !T.isLimitedOvers(match.formatClass) || match.innings.length < 2 || !match.ballsBudget) return 0.3;
  const a = T.inningsTotals(match.innings[0]);
  const b = T.inningsTotals(match.innings[1]);
  if (match.winner === match.innings[0].team) return Math.max(0, (a.runs - b.runs) / Math.max(a.runs, 1));
  if (match.winner === match.innings[1].team) return 0.5 * (10 - b.wickets) / 10 + 0.5 * Math.max(match.ballsBudget - b.legalBalls, 0) / match.ballsBudget;
  return 0.3;
}

/** Per match, as of the match: Elo difference teams[0] - teams[1] over 400 (shrunk if the spec says so) and each side's games. */
function eloTable(name) {
  if (eloTables.has(name)) return eloTables.get(name);
  const spec = ELO_SPECS[name];
  const teams = new Map();
  const slices = new Map();
  const d = new Float64Array(matches.length);
  const g0 = new Int32Array(matches.length);
  const g1 = new Int32Array(matches.length);
  const get = (key, sl) => {
    let t = teams.get(key);
    if (!t) {
      t = { elo: 1500, games: 0, last: null };
      teams.set(key, t);
      sl.sum += 1500;
      sl.n += 1;
    }
    return t;
  };
  for (let i = 0; i < matches.length; i++) {
    const match = matches[i];
    const [first, second] = match.teams;
    const sk = `${match.gender}|${match.formatClass}`;
    let sl = slices.get(sk);
    if (!sl) slices.set(sk, (sl = { sum: 0, n: 0 }));
    const a = get(`${first}|${sk}`, sl);
    const b = get(`${second}|${sk}`, sl);
    const day = match.date ? Date.parse(match.date) / 86400000 : null;
    if (spec.tau && day !== null) {
      const mean = sl.sum / sl.n;
      for (const t of [a, b]) {
        if (t.last !== null && day > t.last) {
          const pull = (mean - t.elo) * (1 - Math.exp(-(day - t.last) / spec.tau));
          t.elo += pull;
          sl.sum += pull;
        }
        t.last = day;
      }
    }
    const shrink = (t) => (spec.m ? ((t.elo - 1500) * t.games) / (t.games + spec.m) : t.elo - 1500);
    d[i] = (shrink(a) - shrink(b)) / 400;
    g0[i] = a.games;
    g1[i] = b.games;
    const drawn = !match.winner && /^(draw|tie)$/i.test(String(match.result ?? ""));
    if (match.winner || drawn) {
      const scoreA = match.winner === first ? 1 : match.winner === second ? 0 : 0.5;
      const expectedA = 1 / (1 + 10 ** ((b.elo - a.elo) / 400));
      let mult = 1;
      if (spec.mov) {
        const lead = scoreA === 0.5 ? 0 : scoreA === 1 ? a.elo - b.elo : b.elo - a.elo;
        mult = (Math.log(1 + marginNorm(match)) * 2.2) / (lead * 0.001 + 2.2);
      }
      const kOf = (t) => spec.K0 * (spec.kg ? Math.max(spec.kg.kMin, 1 / Math.sqrt(1 + t.games / spec.kg.g0)) : 1);
      const base = scoreA - expectedA;
      const da = kOf(a) * mult * base;
      const db = kOf(b) * mult * base;
      a.elo += da;
      b.elo -= db;
      sl.sum += da - db;
      a.games += 1;
      b.games += 1;
    }
  }
  const out = { d, g0, g1 };
  eloTables.set(name, out);
  return out;
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
    // E3 raw state: runs, wickets, target (0 in a first innings), rolling-par runs per ball.
    this.raw = new Float32Array(this.capacity * 4);
    this.length = 0;
  }

  push(features, label, matchIndex, window, mask, firstSide = 1, rem = 0, budget = 0, raw = null) {
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
    if (raw) this.raw.set(raw, this.length * 4);
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
    this.raw = widen(this.raw, Float32Array, 4);
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
  const baseAt = (p) => (chase
    ? T.chaseFeatures({
      currentRunRate, requiredRunRate, wicketsInHand: 10 - s.wickets, ballsRemaining, ballsBudget: budget,
      runsNeeded, target, teamStrengthDiff: strength, parRunRate: p, format: o.format,
    })
    : T.firstInningsFeatures({
      runsScored: s.runs, currentRunRate, wicketsInHand: 10 - s.wickets, ballsRemaining, ballsBudget: budget,
      teamStrengthDiff: strength, parRunRate: p, format: o.format,
    }));
  const base = baseAt(par);
  let venueShape;
  if (chase) {
    venueShape = target / parTotal / scoring - target / parTotal;
  } else {
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
  // E2 columns, after every tv term so TV_SOURCE indices are untouched.
  const rolling = { rpN: o.rp?.N ?? par, rpD: o.rp?.D ?? par };
  for (const tag of RP_TAGS) {
    const alt = baseAt(rolling[tag]);
    for (const i of RP_INDEX[chase ? "chase" : "first"]) v.push(alt[i]);
  }
  if (chase) {
    const rr = clamp(requiredRunRate / par, 0, 4);
    v.push(rr * (o.eraYears ?? 0) / 10, rr * (rolling.rpN / par - 1));
    v.push(0);
  } else v.push(0, 0);
  v.push(0); // E5 gamesLogDiff@e5g placeholder
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
        rp: rollingParFor.get(match), eraYears: match.date ? new Date(match.date).getUTCFullYear() - 2015 : 0,
      }),
      label,
      batting,
      ballsRemaining,
      budget,
      [s.runs, s.wickets, target ?? 0, rollingParFor.get(match)?.N ?? par],
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
      0, // E5 gamesLogDiff@e5g placeholder
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

// E2: as-of rolling par per match, per format x gender. Only matches strictly before the match's date
// count (same-date matches excluded), so nothing from the match or later leaks in. Fewer than
// RP_MIN_MATCHES matches in the window falls back to the constant par.
const rollingParFor = new Map();
{
  const groups = new Map();
  for (const m of matches) {
    if (!T.isLimitedOvers(m.formatClass) || !m.date) continue;
    let runs = 0;
    let balls = 0;
    for (const inning of m.innings) {
      const t = T.inningsTotals(inning);
      runs += t.runs;
      balls += t.legalBalls;
    }
    const key = `${m.formatClass}|${m.gender}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push({ m, day: Date.parse(m.date) / 864e5, date: m.date, runs, balls });
  }
  for (const list of groups.values()) {
    const cr = [0];
    const cb = [0];
    for (const e of list) {
      cr.push(cr[cr.length - 1] + e.runs);
      cb.push(cb[cb.length - 1] + e.balls);
    }
    let hi = 0;
    let lo = 0;
    for (let i = 0; i < list.length; i++) {
      const e = list[i];
      while (hi < list.length && list[hi].date < e.date) hi++;
      while (lo < hi && list[lo].day < e.day - 365) lo++;
      const rate = (a, b) => (b - a >= RP_MIN_MATCHES && cb[b] > cb[a] ? (6 * (cr[b] - cr[a])) / (cb[b] - cb[a]) : null);
      const fallback = T.parRunRateFor(e.m.formatClass);
      rollingParFor.set(e.m, { N: rate(Math.max(0, hi - RP_MATCHES), hi) ?? fallback, D: rate(lo, hi) ?? fallback });
    }
  }
  log(`rolling par for ${rollingParFor.size} limited-overs matches`);
}

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
  const emitTo = (store, perRow) => (features, label, side, rem = 0, budget = 0, raw = null) => {
    const rowMask = perRow
      ? (maskRandom() < MASK.recent.rate ? MASK.recent.bit : 0) | (maskRandom() < MASK.partnership.rate ? MASK.partnership.bit : 0)
      : 0;
    store.push(features, label, m, window, matchMask | rowMask, side === undefined || side === match.teams[0] ? 1 : 0, rem, budget, raw);
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

// ── E3: estimated resources and two-stage ────────────────────────────────────

/**
 * V(balls left, wickets lost): mean runs still to come in a first innings, per format x gender, from one
 * fold's TRAINING matches only. First innings only (a chase stops at the target and would bias V low),
 * and only innings that ran their course (all out or the full budget). A coarse grid of RES_BINS
 * ball-fraction bins x 10 wickets. Cells under RES_MIN_CELL states are treated as missing. The table
 * is smoothed to be non-decreasing in balls left (weighted PAV along each wicket row) and then
 * non-increasing in wickets lost (running minimum, which preserves the first property); queries
 * interpolate linearly between bin centres, anchored at V(0 balls) = 0. The factor the models use is
 * h = V(b, w) / V(b, 0): the share of the remaining resource that survives the wickets lost.
 */
const RES_BINS = 20;
const RES_MIN_CELL = 30;
const resTables = new Map();
const resStatsLog = [];

function violations(value, present) {
  let pairs = 0;
  for (let bin = 0; bin < RES_BINS; bin++) {
    for (let w = 0; w < 10; w++) {
      const a = bin * 10 + w;
      if (!present[a]) continue;
      if (w < 9 && present[a + 1] && value[a + 1] > value[a]) pairs++;
      if (bin < RES_BINS - 1 && present[a + 10] && value[a + 10] < value[a]) pairs++;
    }
  }
  return pairs;
}

function smoothResources(sum, n) {
  const size = RES_BINS * 10;
  const present = new Uint8Array(size);
  const raw = new Float64Array(size);
  for (let i = 0; i < size; i++) {
    if (n[i] >= RES_MIN_CELL) { present[i] = 1; raw[i] = sum[i] / n[i]; }
  }
  const before = violations(raw, present);
  const cells = present.reduce((a, b) => a + b, 0);
  const v = new Float64Array(size);
  for (let w = 0; w < 10; w++) {
    const xs = [];
    for (let bin = 0; bin < RES_BINS; bin++) if (present[bin * 10 + w]) xs.push(bin);
    const vals = new Float64Array(RES_BINS);
    const wts = new Float64Array(RES_BINS);
    for (let bin = 0; bin < RES_BINS; bin++) {
      const i = bin * 10 + w;
      if (present[i]) { vals[bin] = raw[i]; wts[bin] = n[i]; continue; }
      wts[bin] = 1e-6;
      if (xs.length) {
        const left = xs.filter((x) => x < bin).pop();
        const right = xs.find((x) => x > bin);
        vals[bin] = left === undefined ? raw[right * 10 + w] : right === undefined ? raw[left * 10 + w]
          : raw[left * 10 + w] + ((raw[right * 10 + w] - raw[left * 10 + w]) * (bin - left)) / (right - left);
      } else vals[bin] = w > 0 ? v[bin * 10 + w - 1] : 0;
    }
    // Weighted pool-adjacent-violators, non-decreasing in bin.
    const blocks = [];
    for (let bin = 0; bin < RES_BINS; bin++) {
      blocks.push({ w: wts[bin], m: vals[bin], len: 1 });
      while (blocks.length > 1 && blocks[blocks.length - 2].m > blocks[blocks.length - 1].m) {
        const b = blocks.pop();
        const a = blocks[blocks.length - 1];
        a.m = (a.m * a.w + b.m * b.w) / (a.w + b.w);
        a.w += b.w;
        a.len += b.len;
      }
    }
    let bin = 0;
    for (const b of blocks) for (let j = 0; j < b.len; j++) v[(bin++) * 10 + w] = b.m;
  }
  for (let bin = 0; bin < RES_BINS; bin++) for (let w = 1; w < 10; w++) v[bin * 10 + w] = Math.min(v[bin * 10 + w], v[bin * 10 + w - 1]);
  const after = violations(v, new Uint8Array(size).fill(1));
  return { v, cells, before, after };
}

function resourcesFor(windows) {
  const cached = resTables.get(windows);
  if (cached) return cached;
  const acc = new Map();
  for (let m = 0; m < matches.length; m++) {
    if (windows[m] !== WINDOW.train) continue;
    const match = matches[m];
    if (!T.isLimitedOvers(match.formatClass) || !match.innings.length || !match.ballsBudget) continue;
    const inning = match.innings[0];
    const budget = match.ballsBudget;
    const total = T.inningsTotals(inning);
    if (total.wickets < 10 && total.legalBalls < budget) continue;
    const key = `${match.formatClass}|${match.gender}`;
    const cell = acc.get(key) ?? acc.set(key, { sum: new Float64Array(RES_BINS * 10), n: new Float64Array(RES_BINS * 10) }).get(key);
    let runs = 0;
    let wickets = 0;
    let legal = 0;
    const add = () => {
      const left = budget - legal;
      if (left <= 0 || wickets > 9) return;
      const i = Math.min(RES_BINS - 1, Math.floor((left * RES_BINS) / budget)) * 10 + wickets;
      cell.sum[i] += total.runs - runs;
      cell.n[i]++;
    };
    add();
    for (let i = 0; i < inning.balls.length; i++) {
      const d = T.unpackDelivery(inning.balls[i]);
      runs += d.runs;
      wickets += d.wickets;
      if (d.legal) { legal++; add(); }
    }
  }
  const tables = new Map();
  const stats = { cells: 0, before: 0, after: 0, classes: 0 };
  for (const [key, cell] of acc) {
    const t = smoothResources(cell.sum, cell.n);
    tables.set(key, t.v);
    stats.cells += t.cells; stats.before += t.before; stats.after += t.after; stats.classes++;
  }
  const at = (v, w, f) => {
    const x = f * RES_BINS - 0.5;
    if (x <= 0) return (v[w] * f) / (0.5 / RES_BINS);
    if (x >= RES_BINS - 1) return v[(RES_BINS - 1) * 10 + w];
    const j = Math.floor(x);
    return v[j * 10 + w] + (v[(j + 1) * 10 + w] - v[j * 10 + w]) * (x - j);
  };
  const out = {
    stats,
    factor(key, f, wickets, handSet) {
      const v = tables.get(key);
      if (!v) return handSet;
      const w = Math.min(wickets, 9);
      const full = at(v, 0, f);
      return full > 1e-9 ? Math.min(1, Math.max(0.05, at(v, w, f) / full)) : handSet;
    },
  };
  resTables.set(windows, out);
  resStatsLog.push({ key: resStatsLog.length, ...stats });
  console.log(`  e3 V table fold ${resStatsLog.length - 1}: ${stats.classes} classes, ${stats.cells} cells, monotone violations before smoothing ${stats.before}, after ${stats.after}`);
  return out;
}

const STAGE1_K = 6;
const stage1Tables = new Map();
const finalFirst = new Map();

/** Stage-1 features of a first-innings row, all in units of the as-of par total. */
function stage1Features(store, i) {
  const runs = store.raw[i * 4];
  const wickets = store.raw[i * 4 + 1];
  const parN = store.raw[i * 4 + 3];
  const budget = store.budget[i];
  const rem = store.rem[i];
  const parTotal = (parN * budget) / 6;
  const crr = (runs * 6) / Math.max(budget - rem, 1);
  const wf = (10 - wickets) / 10;
  const proj = (crr * (rem / 6)) / parTotal;
  return { x: [1, proj, proj * wf, rem / budget, (wickets / 10) * (rem / budget), wickets / 10], base: runs / parTotal, bin: Math.min(RES_BINS - 1, Math.floor((rem * RES_BINS) / budget)) };
}

/**
 * Stage 1: per format x gender, a linear model of (final total - runs) / parTotal from the training
 * rows, and the residual SD per balls-left bin. Stage 2 is the z score
 * (mu - parTotal) / sigma = (runs/parTotal + prediction - 1) / sigmaBin.
 */
function twoStageFor(store, windows) {
  const memo = stage1Tables.get(store) ?? stage1Tables.set(store, new Map()).get(store);
  if (memo.has(windows)) return memo.get(windows);
  const finalOf = (m) => finalFirst.get(m) ?? finalFirst.set(m, T.inningsTotals(matches[m].innings[0]).runs).get(m);
  const keyOf = (i) => { const m = matches[store.match[i]]; return `${m.formatClass}|${m.gender}`; };
  const fitSet = new Map();
  const get = (key) => fitSet.get(key) ?? fitSet.set(key, { xtx: new Float64Array(STAGE1_K * STAGE1_K), xty: new Float64Array(STAGE1_K), rows: [] }).get(key);
  for (let i = 0; i < store.length; i++) {
    if (windows[store.match[i]] !== WINDOW.train) continue;
    const f = stage1Features(store, i);
    const parTotal = (store.raw[i * 4 + 3] * store.budget[i]) / 6;
    const y = (finalOf(store.match[i]) - store.raw[i * 4]) / parTotal;
    const s = get(keyOf(i));
    for (let a = 0; a < STAGE1_K; a++) {
      s.xty[a] += f.x[a] * y;
      for (let b = 0; b < STAGE1_K; b++) s.xtx[a * STAGE1_K + b] += f.x[a] * f.x[b];
    }
    s.rows.push(i);
  }
  const models = new Map();
  for (const [key, s] of fitSet) {
    const A = Array.from({ length: STAGE1_K }, (_, a) => [...Array.from({ length: STAGE1_K }, (_, b) => s.xtx[a * STAGE1_K + b] + (a === b ? 1e-6 : 0)), s.xty[a]]);
    for (let c = 0; c < STAGE1_K; c++) {
      let p = c;
      for (let r = c + 1; r < STAGE1_K; r++) if (Math.abs(A[r][c]) > Math.abs(A[p][c])) p = r;
      [A[c], A[p]] = [A[p], A[c]];
      for (let r = 0; r < STAGE1_K; r++) {
        if (r === c) continue;
        const k = A[r][c] / A[c][c];
        for (let j = c; j <= STAGE1_K; j++) A[r][j] -= k * A[c][j];
      }
    }
    const beta = A.map((row, c) => row[STAGE1_K] / row[c]);
    const sq = new Float64Array(RES_BINS);
    const cnt = new Float64Array(RES_BINS);
    let sqAll = 0;
    for (const i of s.rows) {
      const f = stage1Features(store, i);
      const parTotal = (store.raw[i * 4 + 3] * store.budget[i]) / 6;
      const e = (finalOf(store.match[i]) - store.raw[i * 4]) / parTotal - f.x.reduce((a, v, j) => a + v * beta[j], 0);
      sq[f.bin] += e * e; cnt[f.bin]++; sqAll += e * e;
    }
    const pooled = Math.sqrt(sqAll / Math.max(s.rows.length, 1));
    const sigma = Array.from(sq, (v, b) => Math.max(0.02, cnt[b] >= RES_MIN_CELL ? Math.sqrt(v / cnt[b]) : pooled));
    models.set(key, { beta, sigma, pooled });
  }
  const out = (i) => {
    const mod = models.get(keyOf(i));
    if (!mod) return 0;
    const f = stage1Features(store, i);
    const pred = f.x.reduce((a, v, j) => a + v * mod.beta[j], 0);
    return (f.base + pred - 1) / mod.sigma[f.bin];
  };
  memo.set(windows, out);
  return out;
}

/** The per-fold value of the resource column for row i of a limited-overs store. */
function resColumnValue(store, i, tab) {
  const m = matches[store.match[i]];
  const runs = store.raw[i * 4];
  const wickets = store.raw[i * 4 + 1];
  const budget = store.budget[i];
  const rem = store.rem[i];
  const wf = (10 - wickets) / 10;
  const chase = store.name === "chase";
  const h = tab.factor(`${m.formatClass}|${m.gender}`, rem / budget, wickets, chase ? 0.3 + 0.7 * wf : 0.55 + 0.45 * wf);
  if (chase) return (store.raw[i * 4 + 2] - runs) / (Math.max(rem, 1) * h);
  const crr = (runs * 6) / Math.max(budget - rem, 1);
  return (runs + crr * (rem / 6) * h) / ((store.raw[i * 4 + 3] * budget) / 6);
}

function slice(store, windows, window, columns, maskMode) {
  const strengthColumn = FOLDS > 1 ? columns.findIndex((c) => store.columns[c][1] === "strength") : -1;
  const rateOf = strengthColumn >= 0 ? strengthFor(windows) : null;
  const resColumn = columns.findIndex((c) => store.columns[c][0].endsWith("@res"));
  const stageColumn = columns.findIndex((c) => store.columns[c][0].endsWith("@twoStage"));
  const resTab = resColumn >= 0 ? resourcesFor(windows) : null;
  const stageOf = stageColumn >= 0 ? twoStageFor(store, windows) : null;
  const eloCols = ["eloDiff", "eloDiff*bf@tvB", "eloDiff*wf@tvW"].map((n) => columns.findIndex((c) => store.columns[c][0] === n));
  const gamesColumn = columns.findIndex((c) => store.columns[c][0] === "gamesLogDiff@e5g");
  const eloT = ACTIVE_ELO && eloCols.some((c) => c >= 0) ? eloTable(ACTIVE_ELO) : null;
  const gamesT = gamesColumn >= 0 ? eloTable(ACTIVE_ELO ?? "prod") : null;
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
    if (eloT || gamesT) {
      const sign = store.firstSide[i] ? 1 : -1;
      const mi = store.match[i];
      if (eloT) {
        const e = sign * eloT.d[mi];
        const lim = store.name === "chase" || store.name === "first";
        const bf = lim ? Math.max(store.rem[i], 0) / store.budget[i] : 0;
        const wf = lim ? (10 - store.raw[i * 4 + 1]) / 10 : 0;
        if (eloCols[0] >= 0 && !(bits[eloCols[0]] & mask)) X[r * k + eloCols[0]] = e;
        if (eloCols[1] >= 0 && !(bits[eloCols[1]] & mask)) X[r * k + eloCols[1]] = e * bf;
        if (eloCols[2] >= 0 && !(bits[eloCols[2]] & mask)) X[r * k + eloCols[2]] = e * wf;
      }
      if (gamesT && !(bits[gamesColumn] & mask)) X[r * k + gamesColumn] = sign * (Math.log1p(gamesT.g0[mi]) - Math.log1p(gamesT.g1[mi]));
    }
    if (resTab) X[r * k + resColumn] = resColumnValue(store, i, resTab);
    if (stageOf) X[r * k + stageColumn] = stageOf(i);
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

/**
 * E2 (b): T.trainBinary with a per-row weight on the gradient. Same optimiser, same stopping rule on
 * the UNWEIGHTED validation window; weights are normalised to mean 1 so l2 and the learning rate
 * mean what they do elsewhere.
 */
function trainBinaryWeighted(train, valid, options, rowWeight) {
  const { epochs = 100, batchSize = 512, learningRate = 0.05, l2 = 1e-4, patience = 8, seed = 12345 } = options;
  const k = train.numFeatures;
  const weights = new Float64Array(k);
  let bias = 0;
  const mW = new Float64Array(k);
  const vW = new Float64Array(k);
  const gradW = new Float64Array(k);
  let mB = 0;
  let vB = 0;
  let step = 0;
  let rng = seed >>> 0;
  const random = () => ((rng = (rng * 1664525 + 1013904223) >>> 0) / 4294967296);
  const order = new Int32Array(train.length);
  for (let i = 0; i < train.length; i++) order[i] = i;
  let best = { logLoss: Infinity, weights: Array.from(weights), bias, epoch: -1 };
  let since = 0;
  for (let epoch = 0; epoch < epochs; epoch++) {
    for (let i = train.length - 1; i > 0; i--) {
      const j = Math.floor(random() * (i + 1));
      const tmp = order[i];
      order[i] = order[j];
      order[j] = tmp;
    }
    for (let start = 0; start < train.length; start += batchSize) {
      const end = Math.min(start + batchSize, train.length);
      gradW.fill(0);
      let gradB = 0;
      for (let i = start; i < end; i++) {
        const row = order[i];
        const off = row * k;
        let z = bias;
        for (let j = 0; j < k; j++) z += weights[j] * train.X[off + j];
        const err = (T.sigmoid(z) - train.y[row]) * rowWeight[row];
        gradB += err;
        for (let j = 0; j < k; j++) gradW[j] += err * train.X[off + j];
      }
      const n = end - start;
      step++;
      const c1 = 1 - 0.9 ** step;
      const c2 = 1 - 0.999 ** step;
      for (let j = 0; j < k; j++) {
        const g = gradW[j] / n + l2 * weights[j];
        mW[j] = 0.9 * mW[j] + 0.1 * g;
        vW[j] = 0.999 * vW[j] + 0.001 * g * g;
        weights[j] -= learningRate * (mW[j] / c1) / (Math.sqrt(vW[j] / c2) + 1e-8);
      }
      const gb = gradB / n;
      mB = 0.9 * mB + 0.1 * gb;
      vB = 0.999 * vB + 0.001 * gb * gb;
      bias -= learningRate * (mB / c1) / (Math.sqrt(vB / c2) + 1e-8);
    }
    const { logLoss } = T.binaryMetrics(valid.length > 0 ? valid : train, weights, bias);
    if (logLoss < best.logLoss - 1e-5) {
      best = { logLoss, weights: Array.from(weights), bias, epoch };
      since = 0;
    } else if (++since >= patience) break;
  }
  return { ...best, validLogLoss: best.logLoss };
}

/** Recency weights: 0.5 ^ (age / halfLife) with age from the fold's validation cutoff, mean 1. */
function recencyWeights(train, windows, halfLifeYears) {
  let cutoff = "9999";
  windows.forEach((w, m) => {
    if (w === WINDOW.valid && matches[m].date && matches[m].date < cutoff) cutoff = matches[m].date;
  });
  const end = Date.parse(cutoff);
  const out = new Float64Array(train.length);
  let sum = 0;
  for (let r = 0; r < train.length; r++) {
    const d = matches[train.match[r]].date;
    const age = d ? Math.max(0, (end - Date.parse(d)) / (864e5 * 365.25)) : halfLifeYears * 4;
    out[r] = 0.5 ** (age / halfLifeYears);
    sum += out[r];
  }
  for (let r = 0; r < train.length; r++) out[r] *= train.length / sum;
  return out;
}

function fit(store, groups, modelClass, classes, windowsList = null, variant = null, opts = {}) {
  ACTIVE_ELO = opts.elo ?? null;
  const columns = [];
  const terms = opts.terms ?? [];
  // A crr variant swaps the run-rate columns for its own `name@variant` ones; "a" only drops them.
  // A time-varying column (E4) is kept only when its tag is in opts.terms.
  const dropped = variant ? CRR_DROP[store.name] ?? [] : [];
  store.columns.forEach(([name, group], idx) => {
    if (!groups.includes(group)) return;
    const at = name.indexOf("@");
    if (opts.res && (name === T.CHASE_FEATURE_NAMES[7] && store.name === "chase"
      || store.name === "first" && name === `${T.FIRST_INNINGS_FEATURE_NAMES[5]}${opts.rp ? `@${opts.rp}` : ""}`)) return;
    if (at >= 0) {
      const tag = name.slice(at + 1);
      if (tag === "res") { if (!opts.res) return; }
      else if (tag === "twoStage") { if (!opts.twoStage) return; }
      else if (tag === "e5g") { if (!opts.e5g) return; }
      else if (RP_TAGS.includes(tag)) { if (tag !== opts.rp) return; }
      else if (TV_TAGS.includes(tag) ? !terms.includes(tag) : tag !== variant) return;
    } else if (dropped.includes(name)) return;
    else if (opts.rp && RP_NAMES[store.name]?.includes(name)) return;
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
        const model = opts.halfLife
          ? trainBinaryWeighted(sinks.train, sinks.valid, BINARY_OPTIONS, recencyWeights(sinks.train, windows, opts.halfLife))
          : T.trainBinary(sinks.train, sinks.valid, BINARY_OPTIONS);
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
  const key = [store.name, groups.join("+"), modelClass, classes, w, variant ?? "", (opts.terms ?? []).join("+"), opts.recal ?? "", opts.rp ?? "", opts.halfLife ?? "", opts.res ? "res" : "", opts.twoStage ? "ts" : "", opts.elo ?? "", opts.e5g ? "e5g" : ""].join("|");
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
  if (name === "chase" || name === "first") {
    // E2: rolling par (a), recency weights (b), era x rrr (c, chase), best a + best b (d). Chase base: plain baseline; first base: baseline + tvB.
    const all = [...root, ...optional];
    const one = latestSeason ? [latest.windows] : null;
    const baseTerms = name === "first" ? ["tvB"] : [];
    const B = fitShared(store, all, "linear", classes, null, null, baseTerms.length ? { terms: baseTerms } : {});
    const BL = one ? fitShared(store, all, "linear", classes, one, null, baseTerms.length ? { terms: baseTerms } : {}) : null;
    checkBaseline(`${name} e2 vs ${name === "first" ? "e4e8 tvB" : "crr"}`, B.full.logLoss, name === "first" ? report.e4e8.first.baseline.variants.tvB.logLoss : report.crr[name].baseline.logLoss);
    const fitsHere = {};
    const results = {};
    const run = (label, opts) => {
      const o = { ...opts, terms: [...baseTerms, ...(opts.terms ?? [])] };
      fitsHere[label] = fitShared(store, all, "linear", classes, null, null, o);
      const r = fitsHere[label];
      const row = line(label, r, B);
      const out = { ...row, deltaEce: r.full.ece - B.full.ece, ece: r.full.ece, opts: o, bands: bandsOf(store, B, r) };
      if (one) {
        const l = fitShared(store, all, "linear", classes, one, null, o);
        const ci = bootstrap(BL.full.loss, l.full.loss, l.matchOf);
        out.latestSeason = { logLoss: l.full.logLoss, delta: l.full.logLoss - BL.full.logLoss, ci: [ci.lo, ci.hi], ece: l.full.ece, deltaEce: l.full.ece - BL.full.ece };
      }
      out.gate = row.delta <= -0.001 && row.ci[1] < 0 && out.deltaEce <= 0.005 ? "PASS" : "FAIL";
      results[label] = out;
      console.log(`  e2 ${name} ${label}: d ${fmt(row.delta)} [${fmt(row.ci[0])}, ${fmt(row.ci[1])}] dECE ${fmt(out.deltaEce)} ${out.gate}`);
    };
    const best = (labels) => labels.reduce((a, b) => (results[b].delta < results[a].delta ? b : a));
    const aLabels = ["rpN", "rpD"];
    for (const t of aLabels) run(t, { rp: t });
    const hLabels = [0.5, 1, 2, 4].map((h) => `hl${h}`);
    for (const h of [0.5, 1, 2, 4]) run(`hl${h}`, { halfLife: h });
    if (name === "chase") {
      run("eraY", { terms: ["eraY"] });
      run("eraP", { terms: ["eraP"] });
    }
    const bestA = best(aLabels);
    const bestB = best(hLabels);
    run(`${bestA}+${bestB}`, { rp: bestA, halfLife: Number(bestB.slice(2)) });
    report.e2 = report.e2 ?? {};
    report.e2[name] = {
      baseline: { logLoss: B.full.logLoss, ece: B.full.ece, latestSeason: BL ? { logLoss: BL.full.logLoss, ece: BL.full.ece } : null },
      bestA,
      bestB,
      variants: results,
      earlyChase: name === "chase" ? earlyChaseTable(store, B, fitsHere) : null,
    };
    if (name === "chase") console.log(`  e2 earlyChase ${JSON.stringify(report.e2.chase.earlyChase.bands)}`);
  }
  if (name === "chase" || name === "first") {
    // E3: (a) fitted resource table replaces the hand-set wicket factor, (c) two-stage z on first innings.
    // (b) estResources x rolling-par era scaling is skipped: not cheap. The venueShape and crr extras keep 0.55 + 0.45 wf.
    const all = [...root, ...optional];
    const one = latestSeason ? [latest.windows] : null;
    const baseOpts = name === "first" ? { terms: ["tvB"], rp: "rpN" } : { rp: "rpD" };
    const B = fitShared(store, all, "linear", classes, null, null, baseOpts);
    const BL = one ? fitShared(store, all, "linear", classes, one, null, baseOpts) : null;
    checkBaseline(`${name} e3 vs e2 ${name === "first" ? "rpN" : "rpD"}`, B.full.logLoss, report.e2[name].variants[name === "first" ? "rpN" : "rpD"].logLoss);
    const fitsHere = {};
    const results = {};
    const run = (label, opts) => {
      const o = { ...baseOpts, ...opts };
      fitsHere[label] = fitShared(store, all, "linear", classes, null, null, o);
      const r = fitsHere[label];
      const row = line(label, r, B);
      const out = { ...row, deltaEce: r.full.ece - B.full.ece, ece: r.full.ece, opts: o, bands: bandsOf(store, B, r) };
      if (one) {
        const l = fitShared(store, all, "linear", classes, one, null, o);
        const ci = bootstrap(BL.full.loss, l.full.loss, l.matchOf);
        out.latestSeason = { logLoss: l.full.logLoss, delta: l.full.logLoss - BL.full.logLoss, ci: [ci.lo, ci.hi], ece: l.full.ece, deltaEce: l.full.ece - BL.full.ece };
      }
      out.gate = row.delta <= -0.001 && row.ci[1] < 0 && out.deltaEce <= 0.005 ? "PASS" : "FAIL";
      results[label] = out;
      console.log(`  e3 ${name} ${label}: d ${fmt(row.delta)} [${fmt(row.ci[0])}, ${fmt(row.ci[1])}] dECE ${fmt(out.deltaEce)} ${out.gate}`);
    };
    run("estResources", { res: true });
    if (name === "first") {
      run("twoStage", { twoStage: true });
      run("estResources+twoStage", { res: true, twoStage: true });
    }
    report.e3 = report.e3 ?? {};
    report.e3[name] = {
      baseline: { logLoss: B.full.logLoss, ece: B.full.ece, latestSeason: BL ? { logLoss: BL.full.logLoss, ece: BL.full.ece } : null },
      variants: results,
      earlyChase: name === "chase" ? earlyChaseTable(store, B, fitsHere) : null,
      monotonicity: resStatsLog.map((x) => ({ fold: x.key, cells: x.cells, violationsBefore: x.before, violationsAfter: x.after })),
    };
    if (name === "chase") console.log(`  e3 earlyChase ${JSON.stringify(report.e3.chase.earlyChase.bands)}`);
    console.log(`  e3 monotonicity ${JSON.stringify(report.e3[name].monotonicity)}`);
  }
  {
    // E5: Elo update-rule variants (as-of walk), scored on every head against that head's baseline.
    // (e) fitted Elo-to-logit scale is skipped: eloDiff is a linear column in a logistic model, so its coefficient is the scale.
    const all = [...root, ...optional];
    const one = latestSeason ? [latest.windows] : null;
    const baseOpts = name === "first" ? { terms: ["tvB"], rp: "rpN" } : name === "chase" ? { rp: "rpD" } : {};
    const B = fitShared(store, all, "linear", classes, null, null, baseOpts);
    const BL = one ? fitShared(store, all, "linear", classes, one, null, baseOpts) : null;
    if (name === "chase") checkBaseline("chase e5 vs e3 baseline", B.full.logLoss, 0.360229);
    if (name === "first") checkBaseline("first e5 vs e3 baseline", B.full.logLoss, 0.548808);
    const results = {};
    const run = (label, opts) => {
      const o = { ...baseOpts, ...opts };
      const r = fitShared(store, all, "linear", classes, null, null, o);
      const row = line(label, r, B);
      const out = { ...row, deltaEce: r.full.ece - B.full.ece, ece: r.full.ece, opts: o, spec: o.elo ? ELO_SPECS[o.elo] : null };
      if (one) {
        const l = fitShared(store, all, "linear", classes, one, null, o);
        const ci = bootstrap(BL.full.loss, l.full.loss, l.matchOf);
        out.latestSeason = { logLoss: l.full.logLoss, delta: l.full.logLoss - BL.full.logLoss, ci: [ci.lo, ci.hi], ece: l.full.ece, deltaEce: l.full.ece - BL.full.ece };
      }
      out.gate = row.delta <= -0.001 && row.ci[1] < 0 && out.deltaEce <= 0.005 ? "PASS" : "FAIL";
      results[label] = out;
      console.log(`  e5 ${name} ${label}: d ${fmt(row.delta)} [${fmt(row.ci[0])}, ${fmt(row.ci[1])}] dECE ${fmt(out.deltaEce)} ${out.gate}`);
    };
    run("prodRecomputed", { elo: "prod" });
    checkBaseline(`${name} e5 prod walk vs baseline`, results.prodRecomputed.logLoss, B.full.logLoss);
    for (const [label, key] of [["kByGames1", "kg1"], ["kByGames2", "kg2"], ["mov90", "mov90"], ["mov120", "mov120"], ["idle365", "idle365"], ["idle730", "idle730"], ["shrink10", "shrink10"], ["shrink30", "shrink30"]]) run(label, { elo: key });
    run("gamesLog", { e5g: true });
    const best = (labels) => labels.filter((l) => results[l].delta < 0).sort((a, b) => results[a].delta - results[b].delta)[0] ?? null;
    const picks = [best(["kByGames1", "kByGames2"]), best(["mov90", "mov120"]), best(["idle365", "idle730"]), best(["shrink10", "shrink30"])].filter(Boolean);
    const combo = { K0: 24 };
    for (const l of picks) {
      const sp = results[l].spec;
      if (sp.kg) { combo.kg = sp.kg; combo.K0 = sp.K0; }
      if (sp.mov) { combo.mov = true; combo.K0 = sp.K0; }
      if (sp.tau) combo.tau = sp.tau;
      if (sp.m) combo.m = sp.m;
    }
    if (combo.kg && combo.mov) combo.K0 = ELO_SPECS.mov90.K0;
    ELO_SPECS.combo = combo;
    if (picks.length >= 2) run("combined", { elo: "combo", e5g: results.gamesLog.delta < 0 });
    report.e5 = report.e5 ?? {};
    report.e5[name] = {
      baseline: { logLoss: B.full.logLoss, ece: B.full.ece, latestSeason: BL ? { logLoss: BL.full.logLoss, ece: BL.full.ece } : null },
      variants: results,
      combinedFrom: picks,
      combinedSpec: combo,
    };
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
