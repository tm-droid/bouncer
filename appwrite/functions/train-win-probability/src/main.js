import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import AdmZip from "adm-zip";
import {
  MODEL_VERSION,
  PAR_RUN_RATE,
  CHASE_FEATURE_NAMES,
  FIRST_INNINGS_FEATURE_NAMES,
  LONG_FEATURE_NAMES,
  PRIOR_SETTLE_PROGRESS,
  RowSink,
  skeletonOf,
  inningsTotals,
  chaseRows,
  firstInningsRows,
  longRows,
  teamKey,
  isLimitedOvers,
  isLongTrainable,
  shrunkWinRate,
  holdoutCutoffDate,
  trainBinary,
  trainMulticlass,
  fitTemperature,
  binaryMetrics,
  multiclassMetrics,
  expectedCalibrationError,
  baseRate,
  classBaseRates,
} from "./training.js";

// Every match Cricsheet publishes, each exactly once. The archive this replaced — t20s_json.zip —
// held 5,602 matches and 109 teams, all of them national sides, so every franchise fixture the app
// shows resolved to no team strength at all. This is 22,524 matches across 392 teams and 1,093
// competitions. Assembling the same coverage from per-competition archives would double-count:
// a match appears in both its league archive and its format archive, and a team's own archive too.
const CRICSHEET_URL = "https://cricsheet.org/downloads/all_json.zip";
const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";
const TEAM_STATS_FILE_ID = "team-stats";
const H2H_FILE_ID = "h2h-stats";

const HOLDOUT_FRACTION = 0.2;
// Carved out of the training window, never the holdout: early stopping needs a window the optimiser
// can see, and the holdout has to stay untouched or its numbers mean nothing.
const VALIDATION_FRACTION = 0.12;

// Row caps. Sinks are built and trained one model at a time and released before the next, so peak
// memory is one model's three sinks — roughly 60 MB at these sizes — not all nine.
const CAP_TRAIN = 400_000;
const CAP_VALID = 200_000;
const CAP_HOLDOUT = 300_000;

const BINARY_OPTIONS = { epochs: 100, batchSize: 512, learningRate: 0.05, l2: 1e-4, patience: 8 };
// Tests are the small corpus here — ~900 matches, and rows within one are near-duplicates, so the
// effective sample size is closer to the match count than the row count. It starts overfitting
// after a couple of epochs, and the long patience exists so validation rather than the epoch budget
// is what stops it. Holdout log-loss is monotone in the step size across the range swept —
// 0.6078 at 0.02, 0.5965 at 0.03, 0.5888 at 0.05 — because a larger step reaches a usable region
// before the memorisation sets in. L2 does not substitute: 0.001 through 0.1 all scored worse.
const LONG_OPTIONS = { epochs: 200, batchSize: 512, learningRate: 0.05, l2: 1e-4, patience: 20 };

const fmt = (v) => (v == null ? "n/a" : v.toFixed(4));
const mb = (bytes) => (bytes / 1024 / 1024).toFixed(0);

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    error("APPWRITE_API_KEY is not set");
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);

  log("Downloading the Cricsheet archive...");
  let zipBuffer;
  try {
    const resp = await fetch(CRICSHEET_URL, { signal: AbortSignal.timeout(180_000) });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    zipBuffer = Buffer.from(await resp.arrayBuffer());
    log(`Downloaded ${mb(zipBuffer.length)} MB`);
  } catch (e) {
    error(`Failed to download cricsheet: ${e?.message}`);
    return res.json({ error: e?.message }, 500);
  }

  const { model: modelObj, h2hAccumulator, parsed, skipped } = buildModel({ zipBuffer, log, error });
  zipBuffer = null;

  const storage = new Storage(client);
  const buffer = Buffer.from(JSON.stringify(modelObj), "utf8");

  try {
    await storage.deleteFile(STATS_BUCKET_ID, TEAM_STATS_FILE_ID).catch(() => {});
    await storage.createFile(
      STATS_BUCKET_ID,
      TEAM_STATS_FILE_ID,
      InputFile.fromBuffer(buffer, "team-stats.json"),
      ["read(\"any\")"]
    );
    log(`Uploaded model (${Object.keys(modelObj.teamStats).length} team keys, ${mb(buffer.length)} MB)`);
  } catch (e) {
    error(`Failed to upload model: ${e?.message}`);
    return res.json({ error: e?.message }, 500);
  }

  // Write H2H stats (non-fatal — model upload already succeeded)
  let h2hPairs = 0;
  try {
    const h2hFinal = finalizeH2H(h2hAccumulator);
    h2hPairs = Object.keys(h2hFinal).length;
    const h2hBuffer = Buffer.from(JSON.stringify({ version: 1, h2h: h2hFinal }), "utf8");
    await storage.deleteFile(STATS_BUCKET_ID, H2H_FILE_ID).catch(() => {});
    await storage.createFile(
      STATS_BUCKET_ID,
      H2H_FILE_ID,
      InputFile.fromBuffer(h2hBuffer, "h2h-stats.json"),
      ["read(\"any\")"]
    );
    log(`H2H stats written: ${h2hPairs} team pairs (${mb(h2hBuffer.length)} MB)`);
  } catch (e) {
    error(`Failed to upload H2H stats: ${e?.message}`);
  }

  return res.json({
    parsed,
    skipped,
    teamKeys: Object.keys(modelObj.teamStats).length,
    h2hPairs,
    holdoutCutoff: modelObj.windows.holdoutCutoff,
    chase: modelObj.chase?.holdout ?? null,
    firstInnings: modelObj.firstInnings?.holdout ?? null,
    long: modelObj.long?.holdout ?? null,
    peakRssMb: Number(mb(process.memoryUsage().rss)),
  });
};

/**
 * Everything between the archive bytes and the artifact, with no Appwrite in it.
 *
 * Exported so a full run can be reproduced against a local copy of the archive — the holdout
 * numbers in the comments above are only meaningful if they can be re-measured, and re-measuring
 * them through a duplicate of this pipeline would verify the duplicate rather than the code that
 * ships.
 */
export function buildModel({ zipBuffer, log, error }) {
  // ---------------------------------------------------------------------------
  // One pass over the archive, into a compact in-memory skeleton.
  //
  // The predecessor decompressed and re-parsed the whole archive a second time, because the
  // temporal split has to be chosen before win rates can be accumulated and win rates have to exist
  // before rows can be built. Keeping a byte per delivery instead removes that dependency: the
  // corpus is ~14 MB once packed, so every later stage reads it from memory.
  // ---------------------------------------------------------------------------
  log("Reading the archive...");
  const zip = new AdmZip(zipBuffer);
  const entries = zip.getEntries().filter((e) => !e.isDirectory && e.entryName.endsWith(".json"));

  const matches = [];
  const h2hAccumulator = {};
  const dates = [];
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
      processH2H(raw, h2hAccumulator);
      const match = skeletonOf(raw);
      if (!match) continue;
      matches.push(match);
      if (match.date) dates.push(match.date);
    } catch (e) {
      if (skipped < 5) error(`Skipped ${entry.entryName}: ${e?.message}`);
      skipped++;
    }
  }
  entries.length = 0;
  log(`Parsed ${parsed}, skipped ${skipped}, ${matches.length} modelled, RSS ${mb(process.memoryUsage().rss)} MB`);

  dates.sort();
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
  // Team win rates, keyed by (team, gender, format). TWO tables, for two different jobs.
  //
  // `trainingRates` covers the training window only. That is the leakage fix: win rates used to be
  // finalised over the whole corpus and then back-filled into every row's strength feature, so a
  // 2014 row carried the outcome of matches played in 2023 — the model could read the future
  // through a feature that looked static.
  //
  // `teamStats` — the table that ships — covers everything. Using the training-window table at
  // inference was a real defect: 53 of the corpus's 392 teams debuted after the 2023-12-02 cutoff
  // and therefore had no entry at all, so a live match between two of them scored with both sides
  // at the neutral 0.5. Every 2026 franchise rebrand landed in that hole — MI London, Sunrisers
  // Leeds, Galle Gallants, Colombo Kaps were all in the archive and all unscoreable.
  //
  // This is not the leakage the split exists to prevent. Leakage is a training row learning its own
  // future; a live match being scored today is not in the corpus at all, so no amount of corpus
  // history can encode its result. The weights were fitted against the training-window estimate and
  // are served the whole-corpus one, which is the same quantity measured on more matches.
  //
  // The key stops a bare team name from pooling Australia's men's and women's sides and their
  // Tests, ODIs and T20s into one number (0.642, against a men's-T20 0.545 and a women's-ODI
  // 0.842). Harmless only while match-sync's lookup never hit; the moment it did, that blend went
  // straight into the logit.
  // ---------------------------------------------------------------------------
  const trainingCounters = {};
  const allCounters = {};
  for (const match of matches) {
    const inTraining = windowOf(match.date) === "train";
    for (const team of match.teams) {
      const key = teamKey(team, match.gender, match.formatClass);
      bump(allCounters, key, "matches");
      if (inTraining) bump(trainingCounters, key, "matches");
    }
    if (match.winner) {
      const key = teamKey(match.winner, match.gender, match.formatClass);
      bump(allCounters, key, "wins");
      if (inTraining) bump(trainingCounters, key, "wins");
    }
  }
  const trainingRates = {};
  for (const [key, c] of Object.entries(trainingCounters)) {
    trainingRates[key] = shrunkWinRate(c.wins, c.matches);
  }
  const teamStats = {};
  for (const [key, c] of Object.entries(allCounters)) {
    teamStats[key] = { winRate: shrunkWinRate(c.wins, c.matches), matchesPlayed: c.matches };
  }
  log(
    `Team stat keys: ${Object.keys(teamStats).length} published, ` +
    `${Object.keys(trainingRates).length} in the training window`
  );

  const winRateLookup = (match) => (team) =>
    trainingRates[teamKey(team, match.gender, match.formatClass)] ?? 0.5;

  // ---------------------------------------------------------------------------
  // Three models, trained and released one at a time.
  // ---------------------------------------------------------------------------
  const chase = fitBinaryModel({
    label: "chase",
    matches,
    windowOf,
    winRateLookup,
    accepts: (m) => isLimitedOvers(m.formatClass),
    rowsFor: chaseRows,
    featureNames: CHASE_FEATURE_NAMES,
    log,
  });

  const firstInnings = fitBinaryModel({
    label: "first innings",
    matches,
    windowOf,
    winRateLookup,
    accepts: (m) => isLimitedOvers(m.formatClass),
    rowsFor: firstInningsRows,
    featureNames: FIRST_INNINGS_FEATURE_NAMES,
    log,
  });

  const long = fitLongModel({ matches, windowOf, winRateLookup, log });

  log(`RSS after training: ${mb(process.memoryUsage().rss)} MB`);

  return {
    model: {
      // v5 carries three models rather than one and a differently shaped feature vector for each,
      // so a v4 reader would produce numbers from the wrong weights rather than fail. winprob.js
      // refuses any version but its own on the learned path and degrades to the heuristic, which is
      // the safe reading here.
      version: MODEL_VERSION,
      parRunRate: PAR_RUN_RATE,
      chase,
      firstInnings,
      long,
      teamStats,
      windows: { validCutoff, holdoutCutoff, matches: matches.length },
    },
    h2hAccumulator,
    parsed,
    skipped,
  };
}

function bump(counters, key, field) {
  counters[key] ??= { wins: 0, matches: 0 };
  counters[key][field]++;
}

/**
 * Build rows for one binary model, fit it, and return the artifact the reader consumes.
 *
 * The sinks are local so they are collectable as soon as this returns — the whole point of doing
 * one model at a time rather than building nine sinks and then training three times.
 */
function fitBinaryModel({ label, matches, windowOf, winRateLookup, accepts, rowsFor, featureNames, log }) {
  const k = featureNames.length;
  const sinks = {
    train: new RowSink(CAP_TRAIN, k),
    valid: new RowSink(CAP_VALID, k),
    holdout: new RowSink(CAP_HOLDOUT, k),
  };

  for (const match of matches) {
    if (!accepts(match)) continue;
    const sink = sinks[windowOf(match.date)];
    for (const row of rowsFor(match, winRateLookup(match))) sink.push(row.features, row.label);
  }
  if (sinks.train.length === 0) {
    log(`${label}: no training rows — model omitted`);
    return null;
  }

  const standardizer = sinks.train.fitStandardizer();
  for (const sink of Object.values(sinks)) sink.applyStandardizer(standardizer);

  const fit = trainBinary(sinks.train, sinks.valid, BINARY_OPTIONS);
  const temperature = fitTemperature(sinks.valid, fit.weights, fit.bias);
  const holdout = binaryMetrics(sinks.holdout, fit.weights, fit.bias, temperature);
  const uncalibrated = binaryMetrics(sinks.holdout, fit.weights, fit.bias);
  const rate = baseRate(sinks.holdout);

  log(
    `${label}: ${sinks.train.length}/${sinks.train.seen} training rows, stopped at epoch ${fit.epoch} — ` +
    `holdout log-loss ${fmt(uncalibrated.logLoss)} -> ${fmt(holdout.logLoss)} calibrated (T=${temperature.toFixed(3)}), ` +
    `accuracy ${fmt(holdout.accuracy)}, Brier ${fmt(holdout.brier)}, ` +
    `ECE ${fmt(expectedCalibrationError(sinks.holdout, fit.weights, fit.bias, temperature))}, ` +
    `base rate ${fmt(rate)}`
  );

  return {
    weights: fit.weights,
    bias: fit.bias,
    temperature,
    featureMeans: standardizer.means,
    featureStds: standardizer.stds,
    featureNames,
    trainingRows: sinks.train.length,
    holdout: {
      rows: holdout.rows,
      logLoss: holdout.logLoss,
      accuracy: holdout.accuracy,
      brier: holdout.brier,
      baseRate: rate,
    },
  };
}

/** Same, for the three-outcome Test model. */
function fitLongModel({ matches, windowOf, winRateLookup, log }) {
  const k = LONG_FEATURE_NAMES.length;
  const sinks = {
    train: new RowSink(CAP_TRAIN, k),
    valid: new RowSink(CAP_VALID, k),
    holdout: new RowSink(CAP_HOLDOUT, k),
  };
  for (const match of matches) {
    if (!isLongTrainable(match.formatClass)) continue;
    const sink = sinks[windowOf(match.date)];
    for (const row of longRows(match, winRateLookup(match))) sink.push(row.features, row.label);
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
    },
  };
}

// ── Head-to-head helpers ──────────────────────────────────────────────────────

function h2hInningsTotal(innings) {
  return (innings?.overs ?? []).reduce(
    (t, o) => t + (o.deliveries ?? []).reduce((s, d) => s + (d.runs?.total ?? 0), 0), 0
  );
}

function h2hInningsWickets(innings) {
  return (innings?.overs ?? []).reduce(
    (t, o) => t + (o.deliveries ?? []).filter(d => (d.wickets?.length ?? 0) > 0).length, 0
  );
}

function processH2H(raw, acc) {
  const info = raw?.info;
  if (!info) return;
  if (info.match_type !== "T20" && info.match_type !== "IT20") return;
  const teams = info.teams ?? [];
  if (teams.length !== 2) return;

  const [t1, t2] = [...teams].sort();
  const key = `${t1}|||${t2}`;

  if (!acc[key]) {
    acc[key] = { team1: t1, team2: t2, total: 0, t1Wins: 0, t2Wins: 0, draws: 0,
      t1RunsList: [], t2RunsList: [], recent: [] };
  }
  const h = acc[key];
  h.total++;

  const winner = info.outcome?.winner;
  if (winner === t1) h.t1Wins++;
  else if (winner === t2) h.t2Wins++;
  else h.draws++;

  const inn1 = raw.innings?.[0];
  const inn2 = raw.innings?.[1];
  const r1 = h2hInningsTotal(inn1), w1 = h2hInningsWickets(inn1);
  const r2 = h2hInningsTotal(inn2), w2 = h2hInningsWickets(inn2);

  const t1BatFirst = inn1?.team === t1;
  h.t1RunsList.push(t1BatFirst ? r1 : r2);
  h.t2RunsList.push(t1BatFirst ? r2 : r1);

  const by = info.outcome?.by;
  const margin = by?.runs ? `${by.runs} runs` : by?.wickets ? `${by.wickets} wickets` : null;

  h.recent.push({
    date: info.dates?.[0] ?? "",
    venue: info.venue ?? "",
    winner: winner ?? null,
    t1Score: t1BatFirst ? `${r1}/${w1}` : `${r2}/${w2}`,
    t2Score: t1BatFirst ? `${r2}/${w2}` : `${r1}/${w1}`,
    format: info.match_type,
    margin,
  });
}

function finalizeH2H(acc) {
  const result = {};
  for (const [key, h] of Object.entries(acc)) {
    if (h.total < 2) continue;
    h.recent.sort((a, b) => b.date.localeCompare(a.date));
    result[key] = {
      team1: h.team1,
      team2: h.team2,
      totalMatches: h.total,
      team1Wins: h.t1Wins,
      team2Wins: h.t2Wins,
      draws: h.draws,
      lastWinner: h.recent[0]?.winner ?? null,
      lastFive: h.recent.slice(0, 5)
        .map(m => !m.winner ? "D" : m.winner === h.team1 ? "W1" : "W2")
        .reverse(),
      team1HighestScore: h.t1RunsList.length ? String(Math.max(...h.t1RunsList)) : "0",
      team1LowestScore:  h.t1RunsList.length ? String(Math.min(...h.t1RunsList)) : "0",
      team2HighestScore: h.t2RunsList.length ? String(Math.max(...h.t2RunsList)) : "0",
      team2LowestScore:  h.t2RunsList.length ? String(Math.min(...h.t2RunsList)) : "0",
      recentMatches: h.recent.slice(0, 10).map(m => ({
        date: m.date, venue: m.venue, winner: m.winner,
        team1Score: m.t1Score, team2Score: m.t2Score,
        format: m.format, margin: m.margin,
      })),
    };
  }
  return result;
}
