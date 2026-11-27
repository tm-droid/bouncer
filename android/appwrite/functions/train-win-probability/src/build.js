/**
 * Everything between the archive bytes and the published tables, with no Appwrite in it.
 *
 * Exported so a full run can be reproduced against a local copy of the archive — the holdout
 * numbers are only meaningful if they can be re-measured, and re-measuring them through a
 * duplicate of this pipeline would verify the duplicate rather than the code that ships.
 *
 * ## What changed in v6, and what was measured
 *
 * v5 knew a team by one number: its win rate over the whole training window. That window includes
 * the match being trained on, so the term read its own result, the model leaned on it, and the
 * calibration temperature had to undo the damage (1.87 on the pre-match model). v6 computes every
 * team, ground and player term AS OF each match (`asOfState.js`) and drops that term.
 *
 * Measured on the same temporal holdout (2024-12-27 onward), log-loss, with 25% of players
 * withheld to stand in for names that will not join:
 *
 *                     v5      as-of team   + ground   + elevens
 *   chase           0.3973     0.3869       0.3746     0.3693
 *   first innings   0.6110     0.5790       0.5685     0.5601
 *   pre-match       0.6775     0.6340          —       0.6295
 *
 * Measured and left out, because each was worth less than 0.001 once the above were in: the toss
 * (0.0004), the current partnership (0.0008), and the last five overs — read against the innings'
 * own rate and against the rate the chase still requires (0.0006). Boosted trees were fitted on
 * the same columns and did not beat the logistic model on any head (chase 0.3672 against 0.3667,
 * with worse calibration), so the model stays linear. Weather was backfilled from Open-Meteo and
 * made every head worse — Tests even on the weather that actually happened, limited overs when
 * the holdout was scored on forecasts as a live match is (chase 0.3873 against 0.3830, first
 * innings 0.5902 against 0.5806) — so no head reads it. The Test model is unchanged: across four
 * rolling folds no context group moved it outside its own noise.
 *
 * ## Two models per head
 *
 * An eleven is known only once a team sheet is published and enough of its names join to the
 * archive. A single model trained to tolerate a missing eleven is worse in both cases than a model
 * fitted for each, so every head ships `core` (teams and ground) and `full` (plus both elevens),
 * and the reader uses `full` only when `sideContext` carries an eleven for both sides.
 */

import AdmZip from "adm-zip";
import { AsOfState, sideContextOf } from "./asOfState.js";
import {
  NEUTRAL_CONTEXT,
  RP_WINDOW,
  SHARDS,
  UNKNOWN_RATING,
  XI_MIN_KNOWN,
  contextFeatureNames,
  contextFeatures,
  hasTimeVaryingTerms,
  rollingParAsOf,
  rollingParLatest,
  shardOf,
  squash,
  withRollingPar,
  xiStrength,
} from "./contextmath.js";
import { richSkeletonOf } from "./corpus.js";
import {
  CHASE_FEATURE_NAMES,
  FIRST_INNINGS_FEATURE_NAMES,
  LONG_FEATURE_NAMES,
  MODEL_VERSION,
  PAR_RUN_RATE,
  PREMATCH_FEATURE_NAMES,
  PRIOR_SETTLE_PROGRESS,
  RowSink,
  baseRate,
  binaryMetrics,
  chaseFeatures,
  classBaseRates,
  expectedCalibrationError,
  firstInningsFeatures,
  walkLongRows,
  fitTemperature,
  matchRowWeights,
  holdoutCutoffDate,
  inningsTotals,
  isLimitedOvers,
  isLongTrainable,
  longFeatures,
  multiclassMetrics,
  parRunRateFor,
  predictClasses,
  prematchFeatures,
  sigmoid,
  trainBinary,
  trainMulticlass,
  unpackDelivery,
} from "./training.js";

export const HOLDOUT_FRACTION = 0.2;
// Carved out of the training window, never the holdout: early stopping needs a window the optimiser
// can see, and the holdout has to stay untouched or its numbers mean nothing.
export const VALIDATION_FRACTION = 0.12;

// Row caps. Sinks are built and trained one head at a time and released before the next.
const CAP_TRAIN = 400_000;
const CAP_VALID = 200_000;
const CAP_HOLDOUT = 300_000;

// A smaller step and more patience than v5's 0.05 / 8. At 0.05 the fit stopped inside its first
// few noisy epochs, and two fits of the same columns differed by more than a feature group is
// worth — which is not a model anyone can compare against last week's.
const BINARY_OPTIONS = { epochs: 200, batchSize: 512, learningRate: 0.02, l2: 1e-4, patience: 12 };
// Tests are the small corpus here — ~900 matches, and rows within one are near-duplicates, so the
// effective sample size is closer to the match count than the row count. It starts overfitting
// after a couple of epochs, and the long patience exists so validation rather than the epoch budget
// is what stops it. Holdout log-loss is monotone in the step size across the range swept —
// 0.6078 at 0.02, 0.5965 at 0.03, 0.5888 at 0.05 — because a larger step reaches a usable region
// before the memorisation sets in. L2 does not substitute: 0.001 through 0.1 all scored worse.
const LONG_OPTIONS = { epochs: 200, batchSize: 512, learningRate: 0.05, l2: 1e-4, patience: 20 };

/**
 * How often a player on a live team sheet fails to join to the archive, as `[share of sides,
 * share of that side's players missed]`.
 *
 * Measured 2026-10-03 on 637 players across 52 live and upcoming team sheets: full-member national
 * sides missed about one player in ten, domestic sides with a recent roster about a third, and
 * sides the archive has no roster for (found only through the whole-archive fallback) well over
 * half. The model is fitted on elevens thinned the same way, so the weight it gives an eleven is
 * the weight a live, partly-joined eleven deserves.
 */
export const JOIN_FAILURE = Object.freeze([[0.5, 0.12], [0.3, 0.35], [0.2, 0.7]]);

/** How far back a player must have appeared to be published for joining. */
const ROSTER_MONTHS = 30;

const fmt = (v) => (v == null ? "n/a" : v.toFixed(4));
const mb = (bytes) => (bytes / 1024 / 1024).toFixed(0);

function lcg(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

/**
 * Each side's eleven as a live match would see it: every player kept or withheld at the side's
 * own miss rate, and no eleven at all once fewer than `XI_MIN_KNOWN` are left.
 */
export function thinnedElevens(match, ctx, random) {
  const out = {};
  for (const name of match.teams) {
    const members = match.xi?.[name] ?? [];
    if (members.length < 9) {
      out[name] = null;
      continue;
    }
    let pick = random();
    let missRate = JOIN_FAILURE[JOIN_FAILURE.length - 1][1];
    for (const [share, rate] of JOIN_FAILURE) {
      if (pick < share) {
        missRate = rate;
        break;
      }
      pick -= share;
    }
    let known = 0;
    const ratings = members.map((idx) => {
      if (random() < missRate) return UNKNOWN_RATING;
      known++;
      return ctx.ratings[idx];
    });
    out[name] = known >= XI_MIN_KNOWN ? xiStrength(ratings) : null;
  }
  return out;
}

// ── rows ─────────────────────────────────────────────────────────────────────

/**
 * One row per delivery of a limited-overs innings, in both shapes: `core` always, `full` when the
 * match has an eleven for both sides.
 */
export function* limitedOversRows(head, match) {
  if (!isLimitedOvers(match.formatClass) || !match.winner || match.innings.length < 2) return;
  const chase = head === "chase";
  const inning = match.innings[chase ? 1 : 0];
  const batting = inning.team;
  const context = match.context?.[batting];
  if (!context) return;
  const budget = match.ballsBudget;
  const par = parRunRateFor(match.formatClass);
  const target = chase ? inningsTotals(match.innings[0]).runs + 1 : 0;
  const label = match.winner === batting ? 1 : 0;

  let runsScored = 0;
  let wicketsLost = 0;
  let ballsFaced = 0;
  for (const code of inning.balls) {
    const d = unpackDelivery(code);
    runsScored += d.runs;
    wicketsLost += d.wickets;
    if (d.legal) ballsFaced++;

    const ballsRemaining = budget - ballsFaced;
    if (ballsRemaining <= 0 || wicketsLost >= 10) continue;
    const runsNeeded = target - runsScored;
    if (chase && runsNeeded <= 0) continue;

    const s = {
      runsScored,
      currentRunRate: (runsScored * 6) / Math.max(ballsFaced, 1),
      requiredRunRate: chase ? Math.max((runsNeeded * 6) / ballsRemaining, 0.1) : 0,
      wicketsInHand: 10 - wicketsLost,
      ballsRemaining,
      ballsBudget: budget,
      runsNeeded,
      target,
      teamStrengthDiff: 0,
      parRunRate: par,
      format: match.formatClass,
    };
    const builder = chase ? chaseFeatures : firstInningsFeatures;
    let base = builder(s);
    // The par-relative columns are read against the par as of the match's date; every other term
    // (the venue shape included) keeps the constant par. Serving does the same with the shipped par.
    const rolling = match.rollingPar?.[RP_WINDOW[head]];
    if (rolling != null) base = withRollingPar(head, base, builder({ ...s, parRunRate: rolling }));
    const tv = head === "firstInnings";
    yield {
      core: contextFeatures(head, base, s, context, false, tv),
      full: context.xi ? contextFeatures(head, base, s, context, true, tv) : null,
      label,
    };
  }
}

/** One row per decided match, oriented to the first-named side. */
export function* prematchContextRows(match) {
  if (!match.winner || match.teams.length !== 2) return;
  const first = match.teams[0];
  const context = match.context?.[first];
  if (!context) return;
  const s = { teamStrengthDiff: 0, format: match.formatClass };
  const base = prematchFeatures(s);
  yield {
    core: contextFeatures("prematch", base, s, context, false),
    full: context.xi ? contextFeatures("prematch", base, s, context, true) : null,
    label: match.winner === first ? 1 : 0,
  };
}

// ── fitting ──────────────────────────────────────────────────────────────────

/**
 * The serving clamp (`PROB_FLOOR` / `PROB_CEILING` in match-sync's winprob.js; a test pins these to
 * it). The reader never publishes a probability outside it, so a holdout score taken without it
 * credits the model with confidence the app never shows.
 */
export const SERVED_FLOOR = 0.03;
export const SERVED_CEILING = 0.97;

/** Holdout log-loss of what is actually served: the temperature-scaled probability, clamped. */
function servedLogLoss(sink, weights, bias, temperature) {
  if (sink.length === 0) return null;
  const k = sink.numFeatures;
  let loss = 0;
  for (let i = 0; i < sink.length; i++) {
    let logit = bias;
    for (let j = 0; j < k; j++) logit += weights[j] * sink.X[i * k + j];
    const p = Math.min(SERVED_CEILING, Math.max(SERVED_FLOOR, sigmoid(logit / temperature)));
    loss -= sink.y[i] === 1 ? Math.log(p) : Math.log(1 - p);
  }
  return loss / sink.length;
}

function fitTier({ label, sinks, featureNames, log }) {
  if (sinks.train.length === 0) {
    log(`${label}: no training rows — model omitted`);
    return null;
  }
  const standardizer = sinks.train.fitStandardizer();
  for (const sink of Object.values(sinks)) sink.applyStandardizer(standardizer);

  const rowWeight = sinks.train.matchIds ? matchRowWeights(sinks.train, 0.5) : null;
  const fit = trainBinary(sinks.train, sinks.valid, { ...BINARY_OPTIONS, rowWeight });
  const temperature = fitTemperature(sinks.valid, fit.weights, fit.bias);
  const holdout = binaryMetrics(sinks.holdout, fit.weights, fit.bias, temperature);
  const ece = expectedCalibrationError(sinks.holdout, fit.weights, fit.bias, temperature);
  const rate = baseRate(sinks.holdout);
  log(
    `${label}: ${sinks.train.length}/${sinks.train.seen} training rows, stopped at epoch ${fit.epoch} — ` +
    `holdout log-loss ${fmt(holdout.logLoss)} (T=${temperature.toFixed(3)}), accuracy ${fmt(holdout.accuracy)}, ` +
    `Brier ${fmt(holdout.brier)}, ECE ${fmt(ece)}, base rate ${fmt(rate)}`
  );
  const rest = sinks.rest
    ? { ...binaryMetrics(sinks.rest, fit.weights, fit.bias, temperature), served: servedLogLoss(sinks.rest, fit.weights, fit.bias, temperature) }
    : null;
  return {
    model: {
      weights: fit.weights,
      bias: fit.bias,
      temperature,
      featureMeans: standardizer.means,
      featureStds: standardizer.stds,
      featureNames,
      trainingRows: sinks.train.length,
      holdout: {
        rows: holdout.rows,
        seen: sinks.holdout.seen,
        logLoss: holdout.logLoss,
        servedLogLoss: servedLogLoss(sinks.holdout, fit.weights, fit.bias, temperature),
        accuracy: holdout.accuracy,
        brier: holdout.brier,
        ece,
        baseRate: rate,
      },
    },
    rest,
  };
}

/** True when a match must not train or validate this head: a DLS match on the first innings. */
export function dropsDls(head, window, match) {
  return head === "firstInnings" && window !== "holdout" && match.method != null;
}

export function headSinks({ head, coreNames, fullNames, matches, windowOf, rowsFor }) {
  // Chase rows of one match are near-duplicates: weight each row 1/sqrt(n) (lab E6).
  const weighted = head === "chase";
  const core = {
    train: new RowSink(CAP_TRAIN, coreNames.length, 20260806, weighted),
    valid: new RowSink(CAP_VALID, coreNames.length),
    holdout: new RowSink(CAP_HOLDOUT, coreNames.length),
    // Holdout rows that will be served by `core` because their match has no eleven.
    rest: new RowSink(CAP_HOLDOUT, coreNames.length, 77),
  };
  const full = {
    train: new RowSink(CAP_TRAIN, fullNames.length, 5, weighted),
    valid: new RowSink(CAP_VALID, fullNames.length, 6),
    holdout: new RowSink(CAP_HOLDOUT, fullNames.length, 7),
  };
  let matchId = 0;
  for (const match of matches) {
    const window = windowOf(match.date);
    matchId++;
    // Rain-reduced matches distort the first innings' scoring-rate curve: out of train and valid only (lab E6).
    if (dropsDls(head, window, match)) continue;
    for (const row of rowsFor(match)) {
      core[window].push(row.core, row.label, matchId);
      if (row.full) full[window].push(row.full, row.label, matchId);
      else if (window === "holdout") core.rest.push(row.core, row.label);
    }
  }
  return { core, full };
}

/**
 * Fit one head's two models and say what the pair scores as served.
 *
 * `system` is the number that matters: every holdout row scored by `full` where the match had an
 * eleven for both sides and by `core` where it did not — which is exactly how the reader chooses.
 */
function fitHead({ label, head, baseNames, matches, windowOf, rowsFor, log }) {
  const tv = head === "firstInnings";
  const coreNames = contextFeatureNames(head, baseNames, false, tv);
  const fullNames = contextFeatureNames(head, baseNames, true, tv);
  const { core, full } = headSinks({ head, coreNames, fullNames, matches, windowOf, rowsFor });

  const coreFit = fitTier({ label: `${label} (teams and ground)`, sinks: core, featureNames: coreNames, log });
  const fullFit = fitTier({ label: `${label} (with elevens)`, sinks: full, featureNames: fullNames, log });
  if (!coreFit) return null;

  const withEleven = full.holdout.seen;
  const without = core.rest.seen;
  const share = withEleven + without > 0 ? withEleven / (withEleven + without) : 0;
  const system =
    fullFit && coreFit.rest?.logLoss != null
      ? share * fullFit.model.holdout.logLoss + (1 - share) * coreFit.rest.logLoss
      : coreFit.model.holdout.logLoss;
  const servedSystem =
    fullFit && coreFit.rest?.served != null
      ? share * fullFit.model.holdout.servedLogLoss + (1 - share) * coreFit.rest.served
      : coreFit.model.holdout.servedLogLoss;
  log(
    `${label}: as served — log-loss ${fmt(system)}, clamped to [${SERVED_FLOOR}, ${SERVED_CEILING}] ${fmt(servedSystem)} ` +
    `(${(share * 100).toFixed(0)}% of holdout rows had both elevens)`
  );

  return {
    core: coreFit.model,
    full: fullFit?.model ?? null,
    holdout: { logLoss: system, servedLogLoss: servedSystem, elevenShare: share, coreOnly: coreFit.model.holdout.logLoss },
  };
}

/** The three-outcome Test model: v5's features, with strength and follow-on read as of the match. */
function fitLongModel({ matches, windowOf, log }) {
  const k = LONG_FEATURE_NAMES.length;
  const sinks = {
    train: new RowSink(CAP_TRAIN, k),
    valid: new RowSink(CAP_VALID, k),
    holdout: new RowSink(CAP_HOLDOUT, k),
  };
  // Strength is as of each match (walkLongRows), never including its own result.
  for (const { match, rows } of walkLongRows(matches, isLongTrainable)) {
    const sink = sinks[windowOf(match.date)];
    for (const row of rows) sink.push(row.features, row.label);
  }
  if (sinks.train.length === 0) {
    log("Test model: no training rows — omitted from this build");
    return null;
  }

  const standardizer = sinks.train.fitStandardizer();
  for (const sink of Object.values(sinks)) sink.applyStandardizer(standardizer);

  const priors = classBaseRates(sinks.train);
  const fit = trainMulticlass(sinks.train, sinks.valid, LONG_OPTIONS);
  const holdout = multiclassMetrics(sinks.holdout, fit);
  const holdoutPriors = classBaseRates(sinks.holdout) ?? priors;
  // What a model that only knew the class shares would score. Log-loss for three classes has no
  // 0.693 landmark, so without this the number is unreadable.
  const priorLogLoss = holdoutPriors.reduce((acc, share, c) => acc - share * Math.log(priors[c] + 1e-7), 0);

  log(
    `Test model: ${sinks.train.length} training rows, stopped at epoch ${fit.epoch} — ` +
    `holdout log-loss ${fmt(holdout.logLoss)}, accuracy ${fmt(holdout.accuracy)} ` +
    `(${holdout.rows} rows), priors ${priors.map(fmt).join("/")}, ` +
    `priors-only log-loss ${fmt(priorLogLoss)}, uniform ${fmt(Math.log(3))}`
  );

  return {
    weights: fit.weights,
    biases: fit.biases,
    featureMeans: standardizer.means,
    featureStds: standardizer.stds,
    featureNames: LONG_FEATURE_NAMES,
    // Consumed at inference: a linear model extrapolates with unearned confidence at the edge of
    // its training data, and the start of a Test is exactly that edge. Without this it returned 73%
    // before a ball had been bowled, against a 39% base rate.
    classPriors: priors,
    priorSettleProgress: PRIOR_SETTLE_PROGRESS,
    trainingRows: sinks.train.length,
    holdout: {
      rows: holdout.rows,
      logLoss: holdout.logLoss,
      accuracy: holdout.accuracy,
      priorLogLoss,
      // Marks a model whose rows count only legal deliveries as balls bowled. Before that fix the
      // Test log-loss was measured on a leaking feature, so the gate compares only against a
      // published model that carries this mark.
      leakFixed: true,
    },
  };
}

// ── published tables ─────────────────────────────────────────────────────────

/**
 * The tables match-sync joins a live match against, spread over `SHARDS` files.
 *
 * One file would be about a megabyte and match-sync would fetch all of it to read two rosters, a
 * ground and one head-to-head record. Keyed by hash, a match touches at most four small files.
 *
 * `wpx-<hh>`: `{ v, asOf, rosters: { "team|gender": [player] }, venues: { name: [ground] }, h2h: { pairKey: [decided, firstWins] } }`
 * `wps-<c>`:  `{ v, asOf, people: [player] }` — everyone whose surname starts with that letter,
 *             for a player who is not in either side's roster (a debutant for that side).
 *
 * A player is `[id, [names], { format: [avg, strike, recent, bowl, batBalls, bowlBalls] }, gender]`.
 */
export function buildShards(state, asOf) {
  const since = new Date(new Date(`${asOf}T00:00:00Z`).getTime() - ROSTER_MONTHS * 30.44 * 86_400_000).toISOString().slice(0, 10);
  const files = new Map();
  // Every shard is written, even an empty one, so a key that is absent is a miss and not a 404.
  for (let i = 0; i < SHARDS; i++) files.set(`wpx-${i.toString(16).padStart(2, "0")}`, { v: 1, asOf, rosters: {}, venues: {}, h2h: {} });
  const shard = (kind, key) => files.get(`wpx-${shardOf(`${kind}:${key}`)}`);

  const players = state.exportPlayers(since);
  const surnames = new Map();
  for (const p of players) {
    const gender = p.teams[0].slice(p.teams[0].lastIndexOf("|") + 1);
    const entry = [p.id, p.names, p.ratings, gender];
    for (const team of p.teams) (shard("r", team).rosters[team] ??= []).push(entry);
    const letters = new Set(p.names.map((n) => squash(n).split(" ").pop()?.[0]).filter((c) => c && /[a-z]/.test(c)));
    for (const letter of letters.size ? letters : ["_"]) {
      const id = `wps-${letter}`;
      let file = surnames.get(id);
      if (!file) surnames.set(id, (file = { v: 1, asOf, people: [] }));
      file.people.push(entry);
    }
  }
  for (const [name, grounds] of Object.entries(state.exportVenues())) shard("v", name).venues[name] = grounds;
  for (const [key, value] of Object.entries(state.exportH2h())) shard("h", key).h2h[key] = value;

  for (const [id, file] of surnames) files.set(id, file);
  return { files, players: players.length };
}

// ── the build ────────────────────────────────────────────────────────────────

/**
 * @returns `{ model, shards, raws, parsed, skipped }` — `raws` is handed back so the caller can
 *          build the device's head-to-head file from the same single pass over the archive.
 */
/**
 * Gives each limited-overs match its as-of rolling par (`match.rollingPar = {N, D}`, null for a
 * thin window) and returns the table serving reads: the same windows taken at the end of the data.
 * Runs and balls are counted over every innings, so this must run before innings are dropped.
 */
export function rollingParTable(matches) {
  const rated = matches.filter((m) => isLimitedOvers(m.formatClass) && m.date);
  const entries = rated.map((m) => {
    let runs = 0;
    let balls = 0;
    for (const inning of m.innings) {
      const t = inningsTotals(inning);
      runs += t.runs;
      balls += t.legalBalls;
    }
    return { formatClass: m.formatClass, gender: m.gender, date: m.date, runs, balls };
  });
  rollingParAsOf(entries).forEach((rp, i) => {
    rated[i].rollingPar = rp;
  });
  return rollingParLatest(entries);
}

export function buildModel({ zipBuffer, log, error, onRaw = null }) {
  log("Reading the archive...");
  let zip = new AdmZip(zipBuffer);
  const entries = zip.getEntries().filter((e) => !e.isDirectory && e.entryName.endsWith(".json"));

  const matches = [];
  let parsed = 0;
  let skipped = 0;

  // Indexed, and the slot is cleared as we go. `adm-zip` caches each entry's decompressed bytes on
  // the entry object the moment `getData()` is called and never releases them, so holding the array
  // holds a second, inflated copy of the whole archive — on top of the compressed buffer it already
  // keeps a reference to, and the skeleton being built here. That is what killed the first
  // production run: 512 MB, no logs, a bare 500 at 43 seconds. Dropping each reference lets the
  // decompressed copy be collected as soon as its match has been reduced to bytes.
  for (let i = 0; i < entries.length; i++) {
    const entry = entries[i];
    entries[i] = null;
    try {
      const raw = JSON.parse(entry.getData().toString("utf8"));
      parsed++;
      onRaw?.(raw);
      const match = richSkeletonOf(raw);
      if (!match) continue;
      match.file = entry.entryName;
      matches.push(match);
    } catch (e) {
      if (skipped < 5) error(`Skipped ${entry.entryName}: ${e?.message}`);
      skipped++;
    }
  }
  entries.length = 0;
  zip = null;
  log(`Parsed ${parsed}, skipped ${skipped}, ${matches.length} modelled, RSS ${mb(process.memoryUsage().rss)} MB`);

  // Date order is what makes "as of" mean anything; the file name only makes the order stable.
  matches.sort((a, b) => String(a.date ?? "").localeCompare(String(b.date ?? "")) || a.file.localeCompare(b.file));
  const dates = matches.map((m) => m.date).filter(Boolean);
  const rolling = rollingParTable(matches);
  const holdoutCutoff = holdoutCutoffDate(dates, HOLDOUT_FRACTION);
  const trainingDates = holdoutCutoff ? dates.filter((d) => d < holdoutCutoff) : dates;
  const validCutoff = holdoutCutoffDate(trainingDates, VALIDATION_FRACTION);
  log(`Windows — training < ${validCutoff}, validation < ${holdoutCutoff}, holdout >= ${holdoutCutoff}`);

  const windowOf = (date) => {
    if (holdoutCutoff && date && date >= holdoutCutoff) return "holdout";
    if (validCutoff && date && date >= validCutoff) return "valid";
    return "train";
  };

  // ---------------------------------------------------------------------------
  // One walk in date order. Each match reads what was known before it, then is folded in. After
  // this the per-delivery player bytes are dropped: the rows need only the score.
  // ---------------------------------------------------------------------------
  const state = new AsOfState();
  const random = lcg(20261003);
  for (const match of matches) {
    const ctx = state.contextFor(match);
    const elevens = thinnedElevens(match, ctx, random);
    match.context = {};
    for (const side of match.teams) match.context[side] = sideContextOf(ctx, match, side, (name) => elevens[name]);
    state.update(match);
    for (const inning of match.innings) {
      inning.batter = inning.bowler = inning.nonStriker = inning.batRuns = inning.out = null;
    }
    match.people = match.peopleNames = match.xi = null;
  }
  const asOf = dates[dates.length - 1] ?? new Date().toISOString().slice(0, 10);
  const teamStats = state.exportTeams();
  log(`As-of walk done: ${Object.keys(teamStats).length} team keys, RSS ${mb(process.memoryUsage().rss)} MB`);

  const chase = fitHead({
    label: "chase",
    head: "chase",
    baseNames: CHASE_FEATURE_NAMES,
    matches,
    windowOf,
    rowsFor: (m) => limitedOversRows("chase", m),
    log,
  });
  const firstInnings = fitHead({
    label: "first innings",
    head: "firstInnings",
    baseNames: FIRST_INNINGS_FEATURE_NAMES,
    matches,
    windowOf,
    rowsFor: (m) => limitedOversRows("firstInnings", m),
    log,
  });
  const prematch = fitHead({
    label: "pre-match",
    head: "prematch",
    baseNames: PREMATCH_FEATURE_NAMES,
    matches,
    windowOf,
    rowsFor: (m) => prematchContextRows(m),
    log,
  });

  const long = fitLongModel({ matches, windowOf, log });

  const shards = buildShards(state, asOf);
  log(`Published tables: ${shards.files.size} files, ${shards.players} players, RSS ${mb(process.memoryUsage().rss)} MB`);

  return {
    model: {
      version: MODEL_VERSION,
      trainedAt: new Date().toISOString(),
      asOf,
      parRunRate: PAR_RUN_RATE,
      rollingPar: rolling,
      chase,
      firstInnings,
      prematch,
      long,
      teamStats,
      means: state.exportMeans(),
      windows: { validCutoff, holdoutCutoff, matches: matches.length },
    },
    shards: shards.files,
    parsed,
    skipped,
  };
}

// ── the publish gate ─────────────────────────────────────────────────────────

function scoreTier(sub, features) {
  let logit = sub.bias;
  for (let i = 0; i < sub.weights.length; i++) {
    logit += (sub.weights[i] * (features[i] - sub.featureMeans[i])) / (sub.featureStds[i] || 1);
  }
  return sigmoid(logit / (sub.temperature > 0 ? sub.temperature : 1));
}

/** How much worse than the published model a refit may score before it is refused. */
export const GATE_LOG_LOSS_SLACK = 0.02;
export const GATE_MAX_ECE = 0.04;

/** How far a probability may move the wrong way along a monotone axis before the grid reports it. */
export const GATE_MONOTONE_SLACK = 0.005;

/** A neutral context that also carries two empty elevens, so the `full` tier can be scored. */
const NEUTRAL_WITH_XI = Object.freeze({ ...NEUTRAL_CONTEXT, xi: { own: xiStrength(null), opp: xiStrength(null) } });

const FORMAT_BUDGET = { T20: 120, ODI: 300, HUNDRED: 100 };

/** The chase head's probability for the side batting second, at a state read off the grid. */
function chaseP(sub, ctx, withXi, format, runsNeeded, ballsRemaining, wicketsLost) {
  const budget = FORMAT_BUDGET[format];
  const par = PAR_RUN_RATE[format];
  const s = {
    currentRunRate: par,
    requiredRunRate: Math.max((runsNeeded * 6) / ballsRemaining, 0.1),
    wicketsInHand: 10 - wicketsLost,
    ballsRemaining,
    ballsBudget: budget,
    runsNeeded,
    target: Math.round((par * budget) / 6),
    teamStrengthDiff: 0,
    parRunRate: par,
    format,
  };
  return scoreTier(sub, contextFeatures("chase", chaseFeatures(s), s, ctx, withXi));
}

/**
 * More wickets in hand, or fewer runs needed off the same balls, never lowers the chasing side's
 * probability. Returns the first violation as a sentence, or null. The grid is coarse on purpose:
 * it is a property check, not a calibration check, and a fitted model may wobble by a hair between
 * neighbouring cells.
 */
function chaseMonotonicityProblem(sub, ctx, withXi, format) {
  const budget = FORMAT_BUDGET[format];
  const parPerBall = PAR_RUN_RATE[format] / 6;
  const lostGrid = [0, 3, 6, 9];
  for (const ballFraction of [0.25, 0.6]) {
    const balls = Math.round(budget * ballFraction);
    const runsGrid = [0.4, 0.8, 1.2, 1.6].map((m) => Math.max(1, Math.round(parPerBall * balls * m)));
    for (const runs of runsGrid) {
      for (let i = 1; i < lostGrid.length; i++) {
        const fewer = chaseP(sub, ctx, withXi, format, runs, balls, lostGrid[i - 1]);
        const more = chaseP(sub, ctx, withXi, format, runs, balls, lostGrid[i]);
        if (more > fewer + GATE_MONOTONE_SLACK) {
          return `${format}: ${runs} needed off ${balls} scored ${fmt(more)} with ${lostGrid[i]} wickets lost, above ${fmt(fewer)} with ${lostGrid[i - 1]} lost`;
        }
      }
    }
    for (const lost of lostGrid) {
      for (let i = 1; i < runsGrid.length; i++) {
        const fewer = chaseP(sub, ctx, withXi, format, runsGrid[i - 1], balls, lost);
        const more = chaseP(sub, ctx, withXi, format, runsGrid[i], balls, lost);
        if (more > fewer + GATE_MONOTONE_SLACK) {
          return `${format}: with ${lost} lost off ${balls} balls, ${runsGrid[i]} needed scored ${fmt(more)}, above ${fmt(fewer)} for ${runsGrid[i - 1]} needed`;
        }
      }
    }
  }
  return null;
}

/** A T20 first innings `runs` for `lost` after `ballsBowled`, scored for the side batting. */
function firstInningsP(sub, ctx, withXi, runs, lost, ballsBowled) {
  const s = {
    runsScored: runs,
    currentRunRate: ballsBowled > 0 ? (runs * 6) / ballsBowled : 0,
    wicketsInHand: 10 - lost,
    ballsRemaining: 120 - ballsBowled,
    ballsBudget: 120,
    teamStrengthDiff: 0,
    parRunRate: PAR_RUN_RATE.T20,
    format: "T20",
  };
  return scoreTier(sub, contextFeatures("firstInnings", firstInningsFeatures(s), s, ctx, withXi, hasTimeVaryingTerms(sub.featureNames)));
}

/** The Test head's class probabilities at the first ball of a five-day match. */
function longNeutralProbabilities(long) {
  const s = {
    lead: 0, wicketsInHand: 10, inningsIndex: 0, ballsRemaining: 2700, ballsTotal: 2700,
    teamStrengthDiff: 0, isFourthInnings: false, runsNeeded: 0, followedOn: false,
    inningsUsedDiff: 0, inningsRunRate: 3, wicketsToTake: 20, scheduledDays: 5,
  };
  const standardised = longFeatures(s).map((f, j) => (f - long.featureMeans[j]) / (long.featureStds[j] || 1));
  return predictClasses(standardised, long);
}

/**
 * Reasons this model must not replace the published one; empty when it may.
 *
 * Two kinds of check. The holdout numbers are compared with the model already serving — the
 * window moves a week at a time, so a refit that is suddenly 0.02 worse is a broken refit, not a
 * harder week. And a handful of positions whose answer is not in doubt are scored outright,
 * because a model can post a fine average while being wrong about a side needing one run.
 *
 * Every tier a head carries (`core`, and `full` when there is one) gets the same checks.
 */
export function publishProblems(model, previous = null) {
  const problems = [];
  const tiersOf = (built) =>
    ["core", "full"].filter((t) => built[t]).map((t) => ({
      name: t,
      sub: built[t],
      ctx: t === "full" ? NEUTRAL_WITH_XI : NEUTRAL_CONTEXT,
      withXi: t === "full",
      tag: (head) => (t === "full" ? `${head} (with elevens)` : head),
    }));

  for (const head of ["chase", "firstInnings", "prematch"]) {
    const built = model[head];
    if (!built?.core) {
      problems.push(`${head}: no model was fitted`);
      continue;
    }
    for (const tier of ["core", "full"]) {
      const h = built[tier]?.holdout;
      if (h && h.ece > GATE_MAX_ECE) problems.push(`${head}.${tier}: ECE ${fmt(h.ece)} is above ${GATE_MAX_ECE}`);
    }
    // Raw and as served (clamped) are each compared like with like; a regression on either blocks.
    // The clamp can hide a raw regression, so the as-served figure never replaces the raw one.
    const was = previous?.[head]?.holdout;
    if (typeof was?.logLoss === "number" && built.holdout.logLoss > was.logLoss + GATE_LOG_LOSS_SLACK) {
      problems.push(`${head}: log-loss ${fmt(built.holdout.logLoss)} against ${fmt(was.logLoss)} for the published model`);
    }
    const nowServed = built.holdout.servedLogLoss;
    if (typeof nowServed === "number" && typeof was?.servedLogLoss === "number" && nowServed > was.servedLogLoss + GATE_LOG_LOSS_SLACK) {
      problems.push(`${head}: as-served log-loss ${fmt(nowServed)} against ${fmt(was.servedLogLoss)} for the published model`);
    }
  }

  // The Test head. A corpus without Tests has none and that is fine, but a refit that drops a head
  // the manifest already serves is a loss, not a quiet difference.
  const long = model.long;
  const servingLong = previous?.long?.holdout;
  if (!long) {
    if (servingLong) problems.push("long: no Test model was fitted, but the published model has one");
  } else {
    const h = long.holdout;
    if (!(long.trainingRows > 0)) problems.push("long: the Test model has no training rows");
    if (!Number.isFinite(h?.logLoss)) {
      problems.push(`long: holdout log-loss ${h?.logLoss} is not a finite number`);
    } else if (servingLong?.leakFixed === true && typeof servingLong.logLoss === "number" && h.logLoss > servingLong.logLoss + GATE_LOG_LOSS_SLACK) {
      // Skipped against a published model without `leakFixed`: its figure was measured on the
      // balls-bowled leak, so a drop against it is the fix, not a regression.
      problems.push(`long: log-loss ${fmt(h.logLoss)} against ${fmt(servingLong.logLoss)} for the published model`);
    }
    const probs = longNeutralProbabilities(long);
    const sum = probs.reduce((a, b) => a + b, 0);
    if (probs.some((p) => !Number.isFinite(p) || p < 0) || Math.abs(sum - 1) > 1e-6) {
      problems.push(`long: class probabilities at the first ball are ${probs.map(fmt).join("/")}, which do not sum to 1`);
    }
  }
  if (problems.length) return problems;

  for (const { sub, ctx, withXi, tag } of tiersOf(model.chase)) {
    // Sanity cells keep their own current rate (8), as before, rather than the grid's par rate.
    const cell = (runsNeeded, ballsRemaining, wicketsLost) => {
      const s = {
        currentRunRate: 8,
        requiredRunRate: Math.max((runsNeeded * 6) / ballsRemaining, 0.1),
        wicketsInHand: 10 - wicketsLost,
        ballsRemaining,
        ballsBudget: 120,
        runsNeeded,
        target: 160,
        teamStrengthDiff: 0,
        parRunRate: PAR_RUN_RATE.T20,
        format: "T20",
      };
      return scoreTier(sub, contextFeatures("chase", chaseFeatures(s), s, ctx, withXi));
    };
    const name = tag("chase");
    if (!(cell(2, 30, 2) > 0.9)) problems.push(`${name}: 2 needed off 30 with 8 wickets scored ${fmt(cell(2, 30, 2))}`);
    if (!(cell(60, 6, 8) < 0.1)) problems.push(`${name}: 60 needed off 6 scored ${fmt(cell(60, 6, 8))}`);
    if (!(cell(40, 30, 2) > cell(40, 30, 8))) problems.push(`${name}: losing six wickets did not lower the probability`);
    for (const format of Object.keys(FORMAT_BUDGET)) {
      const violation = chaseMonotonicityProblem(sub, ctx, withXi, format);
      if (violation) {
        problems.push(`${name}: not monotone, ${violation}`);
        break;
      }
    }
  }

  for (const { sub, ctx, withXi, tag } of tiersOf(model.firstInnings)) {
    const name = tag("firstInnings");
    // 230 for 4 after twenty overs, and 70 all out: positions whose side is not in doubt.
    const big = firstInningsP(sub, ctx, withXi, 230, 4, 120);
    const collapse = firstInningsP(sub, ctx, withXi, 70, 10, 120);
    if (!(big > 0.6)) problems.push(`${name}: 230 for 4 after twenty overs scored ${fmt(big)}`);
    if (!(collapse < 0.4)) problems.push(`${name}: 70 all out scored ${fmt(collapse)}`);
  }

  for (const { sub, ctx, withXi, tag } of tiersOf(model.prematch)) {
    const name = tag("pre-match");
    const state = { format: "T20" };
    const base = prematchFeatures({ teamStrengthDiff: 0, format: "T20" });
    const even = scoreTier(sub, contextFeatures("prematch", base, state, ctx, withXi));
    if (!(even > 0.4 && even < 0.6)) problems.push(`${name}: two unknown sides scored ${fmt(even)}`);
    const stronger = scoreTier(sub, contextFeatures("prematch", base, state, { ...ctx, eloDiff: 0.75 }, withXi));
    if (!(stronger > even)) problems.push(`${name}: a 300-point Elo lead did not raise the probability`);
  }
  return problems;
}
