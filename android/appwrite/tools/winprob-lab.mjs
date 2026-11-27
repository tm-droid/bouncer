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
    this.length = 0;
  }

  push(features, label, matchIndex, window, mask, firstSide = 1) {
    if (features.length !== this.k) throw new Error(`${this.name}: ${features.length} features for ${this.k} columns`);
    if (this.length === this.capacity) this.grow();
    this.X.set(features, this.length * this.k);
    this.y[this.length] = label;
    this.match[this.length] = matchIndex;
    this.window[this.length] = window;
    this.mask[this.length] = mask;
    this.firstSide[this.length] = firstSide;
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

function limitedOversRows(match, ctx, winRateFor, kind, weather, emit, random) {
  if (!T.isLimitedOvers(match.formatClass) || !match.winner || match.innings.length < 2) return;
  const chase = kind === "chase";
  const inning = match.innings[chase ? 1 : 0];
  const batting = inning.team;
  const bowling = match.teams.find((t) => t !== batting) ?? "";
  if (!ctx.teams[batting] || !ctx.teams[bowling]) return;
  const budget = match.ballsBudget;
  const par = T.parRunRateFor(match.formatClass);
  const parTotal = (par * budget) / 6;
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

    const currentRunRate = (s.runs * 6) / Math.max(s.legalBalls, 1);
    const wf = (10 - s.wickets) / 10;
    let base;
    let venueShape;
    if (chase) {
      base = T.chaseFeatures({
        currentRunRate,
        requiredRunRate: Math.max((runsNeeded * 6) / ballsRemaining, 0.1),
        wicketsInHand: 10 - s.wickets,
        ballsRemaining,
        ballsBudget: budget,
        runsNeeded,
        target,
        teamStrengthDiff: strength,
        parRunRate: par,
        format: match.formatClass,
      });
      venueShape = target / parTotal / scoring - target / parTotal;
    } else {
      base = T.firstInningsFeatures({
        runsScored: s.runs,
        currentRunRate,
        wicketsInHand: 10 - s.wickets,
        ballsRemaining,
        ballsBudget: budget,
        teamStrengthDiff: strength,
        parRunRate: par,
        format: match.formatClass,
      });
      const projected = s.runs + currentRunRate * (ballsRemaining / 6) * (0.55 + 0.45 * wf);
      venueShape = projected / parTotal / scoring - projected / parTotal;
    }

    const xi = chase
      ? [own?.batAvg ?? 0, own?.batStrike ?? 0, own?.batRecent ?? 0, opp?.bowl ?? 0, coverage, xiKnown]
      : [own?.batAvg ?? 0, own?.batStrike ?? 0, own?.batRecent ?? 0, opp?.bowl ?? 0,
        opp?.batAvg ?? 0, opp?.batStrike ?? 0, own?.bowl ?? 0, coverage, xiKnown];
    const recentRate = (s.recentRuns * 6) / recentBalls;
    // Momentum against what the chase still asks for, not against the innings so far.
    const vsRequired = chase && s.haveRecent ? clamp((recentRate - (runsNeeded * 6) / ballsRemaining) / par, -3, 3) : 0;

    emit(
      [
        ...base,
        ...team,
        scoring - 1, ctx.venue.chaseAdvantage, venueShape, fam, venueKnown,
        ...toss,
        ...xi,
        ...s.state,
        s.haveRecent ? (recentRate - currentRunRate) / par : 0, s.haveRecent ? s.recentWickets / 3 : 0, s.haveRecent ? 1 : 0,
        ...(chase ? [vsRequired, vsRequired * (1 - ballsRemaining / budget)] : []),
        s.partnershipRuns / (par * 5), Math.min(s.partnershipBalls, 120) / 60, 1,
        ...weather,
      ],
      label,
      batting,
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
  const emitTo = (store, perRow) => (features, label, side) => {
    const rowMask = perRow
      ? (maskRandom() < MASK.recent.rate ? MASK.recent.bit : 0) | (maskRandom() < MASK.partnership.rate ? MASK.partnership.bit : 0)
      : 0;
    store.push(features, label, m, window, matchMask | rowMask, side === undefined || side === match.teams[0] ? 1 : 0);
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
  return { X, y, match, length: rows.length, numFeatures: k };
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
    const probs = probsOf(features);
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
  return { rows, logLoss: total / Math.max(rows, 1), accuracy: correct / Math.max(rows, 1), brier: brier / Math.max(rows, 1), ece, loss };
}

const foldCache = new Map();

function fit(store, groups, modelClass, classes) {
  const columns = [];
  store.columns.forEach(([, group], idx) => {
    if (groups.includes(group)) columns.push(idx);
  });
  if (!foldCache.has(store)) foldCache.set(store, foldWindows(store));
  const parts = { full: [], mix: [], bare: [] };
  const matchOf = [];
  const details = [];
  for (const windows of foldCache.get(store)) {
    const sinks = {
      train: slice(store, windows, WINDOW.train, columns, "mix"),
      valid: slice(store, windows, WINDOW.valid, columns, "mix"),
      full: slice(store, windows, WINDOW.holdout, columns, "none"),
      mix: slice(store, windows, WINDOW.holdout, columns, "mix"),
      bare: slice(store, windows, WINDOW.holdout, columns, "all"),
    };
    let probsOf;
    if (modelClass === "linear") {
      standardize(sinks);
      if (classes === 1) {
        const model = T.trainBinary(sinks.train, sinks.valid, BINARY_OPTIONS);
        const temperature = T.fitTemperature(sinks.valid, model.weights, model.bias);
        probsOf = (f) => {
          let z = model.bias;
          for (let j = 0; j < f.length; j++) z += model.weights[j] * f[j];
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
  }
  const allMatches = new Int32Array(matchOf.reduce((n, m) => n + m.length, 0));
  let at = 0;
  for (const m of matchOf) {
    allMatches.set(m, at);
    at += m.length;
  }
  return {
    groups,
    modelClass,
    detail: details.join(" "),
    columns: columns.length,
    full: summarise(parts.full),
    mix: summarise(parts.mix),
    bare: summarise(parts.bare),
    matchOf: allMatches,
  };
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
  return row;
}

for (const name of wanted) {
  const store = wide[name];
  if (!store || store.length === 0) continue;
  const classes = name === "long" ? 3 : 1;
  const groupsOf = [...new Set(store.columns.map(([, g]) => g))];
  const optional = groupsOf.filter((g) => !["base", "strength", "team"].includes(g) && (g !== "weather" || weatherByFile));
  console.log(`\n== ${name}: ${store.length} rows; ${FOLDS > 1 ? `${FOLDS} rolling folds` : `holdout >= ${holdoutCutoff}`}; join-fail ${JOIN_FAIL} ==`);
  const rows = [];
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
  report.models[name] = { rows, selected: chosen.slice(root.length) };
}

for (const head of Object.keys(report.a1)) {
  const row = report.models[head]?.rows?.find((r) => r.label === "all groups");
  if (row) report.a1[head].labAllGroups = row.logLoss;
}
for (const head of Object.keys(report.a1)) console.log(`a1 ${head}: ${JSON.stringify(report.a1[head])}`);

if (outPath) {
  fs.writeFileSync(outPath, JSON.stringify(report, null, 2));
  log(`Wrote ${outPath}`);
}
