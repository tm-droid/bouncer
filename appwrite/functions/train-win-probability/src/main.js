import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import AdmZip from "adm-zip";
import {
  MODEL_VERSION,
  NUM_FEATURES,
  FEATURE_NAMES,
  chaseRows,
  matchDate,
  isSupportedMatch,
  holdoutCutoffDate,
  inningsTotals,
  fitStandardizer,
  applyStandardizer,
  evaluateModel,
  baseRate,
} from "./training.js";

const CRICSHEET_URL = "https://cricsheet.org/downloads/t20s_json.zip";
const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";
const TEAM_STATS_FILE_ID = "team-stats";
const H2H_FILE_ID = "h2h-stats";

// Logistic regression hyperparameters
const EPOCHS = 30;
const BATCH_SIZE = 256;
const LEARNING_RATE = 0.1;
const L2_LAMBDA = 0.001;
const MAX_ROWS = 200_000;
const MAX_HOLDOUT_ROWS = 50_000;
const HOLDOUT_FRACTION = 0.2;

const fmt = (v) => (v == null ? "n/a" : v.toFixed(4));

/** Bounded-size sample of a stream, uniform over everything seen. */
function reservoirPush(sink, row, cap) {
  sink._seen = (sink._seen ?? 0) + 1;
  if (sink.length < cap) {
    sink.push(row);
    return;
  }
  const idx = Math.floor(Math.random() * sink._seen);
  if (idx < cap) sink[idx] = row;
}

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    error("APPWRITE_API_KEY is not set");
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);

  log("Downloading cricsheet T20 ZIP...");
  let zipBuffer;
  try {
    const resp = await fetch(CRICSHEET_URL, { signal: AbortSignal.timeout(120_000) });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    zipBuffer = Buffer.from(await resp.arrayBuffer());
    log(`Downloaded ${(zipBuffer.length / 1024 / 1024).toFixed(1)} MB`);
  } catch (e) {
    error(`Failed to download cricsheet: ${e?.message}`);
    return res.json({ error: e?.message }, 500);
  }

  const zip = new AdmZip(zipBuffer);
  zipBuffer = null; // hint GC — large buffer no longer needed once ZIP is loaded

  const jsonEntries = zip.getEntries().filter(
    (e) => !e.isDirectory && e.entryName.endsWith(".json")
  );

  // ---------------------------------------------------------------------------
  // Pass 1: read `info` only — collect match dates so the temporal split can be chosen, and
  // accumulate H2H (descriptive, so it legitimately spans the whole corpus).
  // ---------------------------------------------------------------------------
  log("Pass 1: reading match info and choosing the temporal split...");
  const h2hAccumulator = {};
  const dates = [];
  let parsed = 0;
  let skipped = 0;

  const readEntry = (entry) => JSON.parse(entry.getData().toString("utf8"));

  for (const entry of jsonEntries) {
    try {
      const match = readEntry(entry);
      processH2H(match, h2hAccumulator);
      parsed++;
      if (isSupportedMatch(match?.info)) {
        const d = matchDate(match.info);
        if (d) dates.push(d);
      }
    } catch (e) {
      if (skipped < 5) error(`Skipped ${entry.entryName}: ${e?.message}`);
      skipped++;
    }
  }
  log(`Parsed ${parsed} matches, skipped ${skipped}, ${dates.length} datable T20s`);

  const cutoff = holdoutCutoffDate(dates, HOLDOUT_FRACTION);
  if (cutoff) {
    log(`Holdout window starts ${cutoff} (${Math.round(HOLDOUT_FRACTION * 100)}% of matches)`);
  } else {
    log("Too few datable matches for a holdout — training on everything, metrics are in-sample");
  }
  const inTrainWindow = (d) => cutoff == null || d == null || d < cutoff;

  // ---------------------------------------------------------------------------
  // Pass 2: team win rates from the TRAINING WINDOW ONLY.
  //
  // This is the leakage fix. Win rates used to be finalised over the entire corpus and then
  // back-filled into every row's features[4], so a 2014 row carried the outcome of matches played
  // in 2023 — the model could read the future through a feature that looked static.
  // ---------------------------------------------------------------------------
  log("Pass 2: accumulating team stats from the training window...");
  const teamStats = {};
  let firstInningsTotalRuns = 0;
  let firstInningsTotalBalls = 0;

  for (const entry of jsonEntries) {
    let match;
    try {
      match = readEntry(entry);
    } catch {
      continue;
    }
    const info = match?.info;
    if (!isSupportedMatch(info)) continue;

    const training = inTrainWindow(matchDate(info));
    // Descriptive per-team aggregates are published to clients for every match, but the winRate
    // that feeds the model must not see the holdout.
    accumulateTeamStats(match, teamStats, training);

    if (training) {
      const first = inningsTotals(match.innings?.[0]);
      if (first.balls > 0) {
        firstInningsTotalRuns += first.runs;
        firstInningsTotalBalls += first.balls;
      }
    }
  }

  for (const team of Object.values(teamStats)) {
    team.winRate = team.trainMatches > 0 ? team.trainWins / team.trainMatches : 0.5;
    delete team.trainWins;
    delete team.trainMatches;
  }

  const breakevenRR = firstInningsTotalBalls > 0
    ? (firstInningsTotalRuns / firstInningsTotalBalls) * 6
    : 8.0;
  log(`Breakeven RR from training window: ${breakevenRR.toFixed(3)}`);

  // ---------------------------------------------------------------------------
  // Pass 3: build feature rows, split by match date.
  // ---------------------------------------------------------------------------
  log("Pass 3: building feature rows...");
  const winRateFor = (team) => teamStats[team]?.winRate ?? 0.5;
  const trainingRows = [];
  const holdoutRows = [];

  for (const entry of jsonEntries) {
    let match;
    try {
      match = readEntry(entry);
    } catch {
      continue;
    }
    if (!isSupportedMatch(match?.info)) continue;
    const rows = chaseRows(match, winRateFor);
    if (rows.length === 0) continue;

    const sink = inTrainWindow(matchDate(match.info)) ? trainingRows : holdoutRows;
    for (const row of rows) {
      // Reservoir sampling keeps the row count bounded. Sampling is per-sink so the holdout is
      // never starved by a large training set.
      reservoirPush(sink, row, sink === trainingRows ? MAX_ROWS : MAX_HOLDOUT_ROWS);
    }
  }

  log(`Rows: ${trainingRows.length} training, ${holdoutRows.length} holdout`);
  if (holdoutRows.length === 0) {
    log("WARNING: holdout is empty — reported metrics are in-sample only");
  }

  // Standardizer is fit on the training rows ONLY, then applied to both. Fitting it over all rows
  // (as before) leaks the holdout's distribution into training.
  const standardizer = fitStandardizer(trainingRows);
  applyStandardizer(trainingRows, standardizer);
  applyStandardizer(holdoutRows, standardizer);
  const featureMeans = standardizer.means;
  const featureStds = standardizer.stds;
  const n = trainingRows.length;

  log(`Feature means: ${featureMeans.map(v => v.toFixed(4)).join(", ")}`);
  log(`Feature stds:  ${featureStds.map(v => v.toFixed(4)).join(", ")}`);

  // Train logistic regression via mini-batch gradient descent
  const { weights, bias } = trainLogisticRegression(trainingRows, log);

  // Held-out metrics. The previous version reported training log-loss only, which cannot detect
  // either overfitting or the leakage above.
  const trainMetrics = evaluateModel(trainingRows, weights, bias);
  const holdoutMetrics = evaluateModel(holdoutRows, weights, bias);
  const holdoutBaseRate = baseRate(holdoutRows);
  log(
    `Train  — log-loss ${fmt(trainMetrics.logLoss)}, accuracy ${fmt(trainMetrics.accuracy)} (${trainMetrics.rows} rows)`
  );
  log(
    `Holdout — log-loss ${fmt(holdoutMetrics.logLoss)}, accuracy ${fmt(holdoutMetrics.accuracy)} (${holdoutMetrics.rows} rows)`
  );
  if (holdoutBaseRate != null) {
    log(`Holdout base rate (chasing side wins): ${fmt(holdoutBaseRate)} — accuracy must beat this`);
  }

  // Upload model to Appwrite Storage
  const storage = new Storage(client);
  const modelObj = {
    // v3: ballsRemainingFraction is now relative to the match's own innings budget rather than a
    // hardcoded 120, and features[4] is computed from training-window win rates only. A v2 reader
    // would misinterpret both, so win-probability refuses anything but v3 on its learned path.
    version: MODEL_VERSION,
    weights,
    bias,
    featureMeans,
    featureStds,
    featureNames: FEATURE_NAMES,
    breakevenRR,
    teamStats,
    trainingRows: n,
    epochs: EPOCHS,
    holdout: {
      cutoffDate: cutoff,
      rows: holdoutMetrics.rows,
      logLoss: holdoutMetrics.logLoss,
      accuracy: holdoutMetrics.accuracy,
      baseRate: holdoutBaseRate,
    },
    train: { logLoss: trainMetrics.logLoss, accuracy: trainMetrics.accuracy },
  };
  const modelJson = JSON.stringify(modelObj);
  const buffer = Buffer.from(modelJson, "utf8");

  try {
    await storage.deleteFile(STATS_BUCKET_ID, TEAM_STATS_FILE_ID).catch(() => {});
    await storage.createFile(
      STATS_BUCKET_ID,
      TEAM_STATS_FILE_ID,
      InputFile.fromBuffer(buffer, "team-stats.json"),
      ["read(\"any\")"]
    );
    log(`Uploaded model (${Object.keys(teamStats).length} teams, ${n} training rows, ${(buffer.length / 1024).toFixed(0)} KB)`);
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
    log(`H2H stats written: ${h2hPairs} team pairs (${(h2hBuffer.length / 1024).toFixed(0)} KB)`);
  } catch (e) {
    error(`Failed to upload H2H stats: ${e?.message}`);
  }

  return res.json({
    parsed,
    skipped,
    teams: Object.keys(teamStats).length,
    h2hPairs,
    trainingRows: n,
    holdoutRows: holdoutMetrics.rows,
    holdoutCutoffDate: cutoff,
    trainLogLoss: trainMetrics.logLoss,
    trainAccuracy: trainMetrics.accuracy,
    holdoutLogLoss: holdoutMetrics.logLoss,
    holdoutAccuracy: holdoutMetrics.accuracy,
    holdoutBaseRate,
    breakevenRR,
    weights,
    bias,
  });
};

/**
 * Accumulate descriptive per-team aggregates.
 *
 * [countTowardWinRate] gates only the win/played counters that feed the model's teamStrengthDiff
 * feature. Runs and wickets are descriptive and published to clients for every match, but the win
 * rate must come from the training window alone — computing it over the whole corpus and then
 * back-filling it into every row (as this function's predecessor did) let a 2014 row carry the
 * outcome of a 2023 match.
 */
function accumulateTeamStats(raw, teamStats, countTowardWinRate) {
  const info = raw?.info;
  const teams = info?.teams ?? [];

  for (const team of teams) {
    if (!teamStats[team]) {
      teamStats[team] = {
        matchesPlayed: 0,
        totalRunsScored: 0,
        totalRunsConceded: 0,
        totalWicketsLost: 0,
        winRate: 0.5,
        trainWins: 0,
        trainMatches: 0,
      };
    }
    teamStats[team].matchesPlayed++;
    if (countTowardWinRate) teamStats[team].trainMatches++;
  }

  const winner = info?.outcome?.winner;
  if (countTowardWinRate && winner && teamStats[winner]) {
    teamStats[winner].trainWins++;
  }

  for (const inning of raw?.innings ?? []) {
    const battingTeam = inning.team;
    if (!teamStats[battingTeam]) continue;
    const { runs } = inningsTotals(inning);
    let wickets = 0;
    for (const over of inning.overs ?? []) {
      for (const ball of over.deliveries ?? []) {
        if (ball.wickets?.length) wickets += ball.wickets.length;
      }
    }
    teamStats[battingTeam].totalRunsScored += runs;
    teamStats[battingTeam].totalWicketsLost += wickets;
    const bowlingTeam = teams.find((t) => t !== battingTeam);
    if (bowlingTeam && teamStats[bowlingTeam]) {
      teamStats[bowlingTeam].totalRunsConceded += runs;
    }
  }
}

/**
 * Mini-batch gradient descent logistic regression with L2 regularization.
 * Returns { weights: number[], bias: number }.
 */
function trainLogisticRegression(rows, log) {
  const weights = new Array(NUM_FEATURES).fill(0);
  let bias = 0;
  const n = rows.length;

  if (n === 0) {
    log("No training rows — returning zero weights");
    return { weights, bias };
  }

  for (let epoch = 0; epoch < EPOCHS; epoch++) {
    // Shuffle rows (Fisher-Yates)
    for (let i = n - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      const tmp = rows[i];
      rows[i] = rows[j];
      rows[j] = tmp;
    }

    // Mini-batch updates
    for (let start = 0; start < n; start += BATCH_SIZE) {
      const end = Math.min(start + BATCH_SIZE, n);
      const batchSize = end - start;

      const dw = new Array(NUM_FEATURES).fill(0);
      let db = 0;

      for (let i = start; i < end; i++) {
        const { features, label } = rows[i];
        let logit = bias;
        for (let j = 0; j < NUM_FEATURES; j++) logit += weights[j] * features[j];
        const pred = sigmoid(logit);
        const err = pred - label;
        for (let j = 0; j < NUM_FEATURES; j++) dw[j] += err * features[j];
        db += err;
      }

      // Apply gradients with L2 regularization on weights (not bias)
      for (let j = 0; j < NUM_FEATURES; j++) {
        weights[j] -= LEARNING_RATE * (dw[j] / batchSize + L2_LAMBDA * weights[j]);
      }
      bias -= LEARNING_RATE * (db / batchSize);
    }

    // Log loss every 5 epochs and on the final epoch
    if ((epoch + 1) % 5 === 0 || epoch === EPOCHS - 1) {
      let loss = 0;
      for (const { features, label } of rows) {
        let logit = bias;
        for (let j = 0; j < NUM_FEATURES; j++) logit += weights[j] * features[j];
        const pred = sigmoid(logit);
        const eps = 1e-7;
        loss += -(label * Math.log(pred + eps) + (1 - label) * Math.log(1 - pred + eps));
      }
      loss /= n;
      log(`Epoch ${epoch + 1}/${EPOCHS} — log-loss: ${loss.toFixed(6)}`);
    }
  }

  log(`Training complete. Weights: ${weights.map(w => w.toFixed(4)).join(", ")}, bias: ${bias.toFixed(4)}`);
  return { weights, bias };
}

function sigmoid(x) {
  return 1 / (1 + Math.exp(-x));
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
