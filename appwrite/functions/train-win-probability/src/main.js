import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import AdmZip from "adm-zip";
import {
  MODEL_VERSION,
  NUM_FEATURES,
  FEATURE_NAMES,
  NUM_LONG_FEATURES,
  LONG_FEATURE_NAMES,
  PRIOR_SETTLE_PROGRESS,
  chaseRows,
  longRows,
  matchDate,
  formatClassOf,
  teamKey,
  isStatsCountable,
  isChaseTrainable,
  isLongTrainable,
  holdoutCutoffDate,
  inningsTotals,
  fitStandardizer,
  applyStandardizer,
  evaluateModel,
  baseRate,
  trainMulticlass,
  evaluateMulticlass,
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
  // Pass 1: parse every match once, keeping a small record per match rather than the match itself.
  //
  // Two passes over the archive, not three. The temporal split has to be chosen before win rates
  // can be accumulated, and win rates have to exist before feature rows can be built — three
  // dependent stages. Retaining a ~100-byte record per match instead of re-reading the ZIP for the
  // middle stage collapses that to two: 22,524 records is a few MB, where retaining the parsed
  // matches measured 353 MB and would risk the function's memory ceiling.
  // ---------------------------------------------------------------------------
  log("Pass 1: reading match info and choosing the temporal split...");
  const h2hAccumulator = {};
  const dates = [];
  const records = [];
  const teamStats = {};
  let parsed = 0;
  let skipped = 0;

  const readEntry = (entry) => JSON.parse(entry.getData().toString("utf8"));

  for (const entry of jsonEntries) {
    try {
      const match = readEntry(entry);
      processH2H(match, h2hAccumulator);
      parsed++;
      const info = match?.info;
      if (!isStatsCountable(info)) continue;
      const date = matchDate(info);
      if (date) dates.push(date);
      records.push({
        teams: info.teams,
        gender: info.gender,
        formatClass: formatClassOf(info),
        date,
        winner: info.outcome?.winner ?? null,
        firstInnings: isChaseTrainable(info) ? inningsTotals(match.innings?.[0]) : null,
      });
      // Descriptive aggregates do not depend on the holdout split, so they are accumulated here
      // while the innings are in hand rather than costing a record field per team.
      accumulateAggregates(match, teamStats);
    } catch (e) {
      if (skipped < 5) error(`Skipped ${entry.entryName}: ${e?.message}`);
      skipped++;
    }
  }
  log(`Parsed ${parsed} matches, skipped ${skipped}, ${records.length} usable, ${dates.length} datable`);

  const cutoff = holdoutCutoffDate(dates, HOLDOUT_FRACTION);
  if (cutoff) {
    log(`Holdout window starts ${cutoff} (${Math.round(HOLDOUT_FRACTION * 100)}% of matches)`);
  } else {
    log("Too few datable matches for a holdout — training on everything, metrics are in-sample");
  }
  const inTrainWindow = (d) => cutoff == null || d == null || d < cutoff;

  // ---------------------------------------------------------------------------
  // Team win rates from the TRAINING WINDOW ONLY, keyed by (team, gender, format).
  //
  // The window is the leakage fix: win rates used to be finalised over the entire corpus and then
  // back-filled into every row's features[4], so a 2014 row carried the outcome of matches played
  // in 2023 — the model could read the future through a feature that looked static.
  //
  // The key is the v4 fix. A bare team name pooled Australia's men's and women's sides and their
  // Tests, ODIs and T20s into one number: 0.642, against a men's-T20 0.545, a women's-ODI 0.842
  // and a women's-multi-day 0.286. Harmless only while match-sync's lookup never hit; the moment
  // it did, that blend went straight into the logit.
  // ---------------------------------------------------------------------------
  log("Accumulating win rates from the training window...");
  let firstInningsTotalRuns = 0;
  let firstInningsTotalBalls = 0;

  for (const rec of records) {
    const training = inTrainWindow(rec.date);
    if (!training) continue;
    for (const team of rec.teams) {
      const key = teamKey(team, rec.gender, rec.formatClass);
      if (teamStats[key]) teamStats[key].trainMatches++;
    }
    if (rec.winner) {
      const key = teamKey(rec.winner, rec.gender, rec.formatClass);
      if (teamStats[key]) teamStats[key].trainWins++;
    }
    if (rec.firstInnings?.balls > 0) {
      firstInningsTotalRuns += rec.firstInnings.runs;
      firstInningsTotalBalls += rec.firstInnings.balls;
    }
  }

  for (const team of Object.values(teamStats)) {
    team.winRate = team.trainMatches > 0 ? team.trainWins / team.trainMatches : 0.5;
    delete team.trainWins;
    delete team.trainMatches;
  }
  log(`Team stat keys: ${Object.keys(teamStats).length}`);

  const breakevenRR = firstInningsTotalBalls > 0
    ? (firstInningsTotalRuns / firstInningsTotalBalls) * 6
    : 8.0;
  log(`Breakeven RR from training window: ${breakevenRR.toFixed(3)}`);

  // ---------------------------------------------------------------------------
  // Pass 3: build feature rows, split by match date.
  // ---------------------------------------------------------------------------
  log("Pass 2: building feature rows...");
  const trainingRows = [];
  const holdoutRows = [];
  const longTrainingRows = [];
  const longHoldoutRows = [];

  for (const entry of jsonEntries) {
    let match;
    try {
      match = readEntry(entry);
    } catch {
      continue;
    }
    const info = match?.info;
    if (!isStatsCountable(info)) continue;

    // The strength lookup is scoped to this match's own gender and format, so a Test row reads
    // Test win rates and a women's T20 row reads women's T20 win rates.
    const formatClass = formatClassOf(info);
    const winRateFor = (team) => teamStats[teamKey(team, info.gender, formatClass)]?.winRate ?? 0.5;
    const training = inTrainWindow(matchDate(info));

    // A Test is not a long T20: pooling the two collapsed holdout accuracy from 0.619 to 0.506,
    // so long-format matches train a separate three-outcome model and never enter the chase rows,
    // whose features all assume a fixed innings budget.
    if (isChaseTrainable(info)) {
      const sink = training ? trainingRows : holdoutRows;
      for (const row of chaseRows(match, winRateFor)) {
        // Reservoir sampling keeps the row count bounded. Sampling is per-sink so the holdout is
        // never starved by a large training set.
        reservoirPush(sink, row, training ? MAX_ROWS : MAX_HOLDOUT_ROWS);
      }
    } else if (isLongTrainable(info)) {
      const sink = training ? longTrainingRows : longHoldoutRows;
      for (const row of longRows(match, winRateFor)) {
        reservoirPush(sink, row, training ? MAX_ROWS : MAX_HOLDOUT_ROWS);
      }
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

  // ---------------------------------------------------------------------------
  // Long-format model: three outcomes, because a Test can be drawn and a two-class model has no
  // way to say so. Roughly a quarter of all Test positions end in a draw.
  // ---------------------------------------------------------------------------
  let longModel = null;
  if (longTrainingRows.length > 0) {
    const longStandardizer = fitStandardizer(longTrainingRows, NUM_LONG_FEATURES);
    applyStandardizer(longTrainingRows, longStandardizer);
    applyStandardizer(longHoldoutRows, longStandardizer);
    const priors = classBaseRates(longTrainingRows);
    const fitted = trainMulticlass(longTrainingRows, {
      epochs: EPOCHS,
      batchSize: BATCH_SIZE,
      learningRate: LEARNING_RATE,
      l2: L2_LAMBDA,
      numFeatures: NUM_LONG_FEATURES,
    });
    const longHoldoutMetrics = evaluateMulticlass(longHoldoutRows, fitted);
    log(
      `Long-format holdout — log-loss ${fmt(longHoldoutMetrics.logLoss)}, accuracy ` +
      `${fmt(longHoldoutMetrics.accuracy)} (${longHoldoutMetrics.rows} rows), ` +
      `priors ${priors.map(fmt).join("/")}`
    );
    longModel = {
      weights: fitted.weights,
      biases: fitted.biases,
      featureMeans: longStandardizer.means,
      featureStds: longStandardizer.stds,
      featureNames: LONG_FEATURE_NAMES,
      // Consumed at inference: a linear model extrapolates with unearned confidence at the edge of
      // its training data, and the start of a Test is exactly that edge. Without this it returned
      // 73% before a ball had been bowled, against a 39% base rate.
      classPriors: priors,
      priorSettleProgress: PRIOR_SETTLE_PROGRESS,
      trainingRows: longTrainingRows.length,
      holdout: {
        rows: longHoldoutMetrics.rows,
        logLoss: longHoldoutMetrics.logLoss,
        accuracy: longHoldoutMetrics.accuracy,
      },
    };
  } else {
    log("No long-format training rows — the Test model is omitted from this build");
  }

  // Upload model to Appwrite Storage
  const storage = new Storage(client);
  const modelObj = {
    // v4: `teamStats` is keyed "team|gender|format" rather than by bare team name, so a v3 reader
    // would miss every lookup and silently fall back to 0.5 — the exact defect v4 exists to fix.
    // It also carries a second model for long-format matches. winprob.js refuses any version but
    // its own on the learned path and degrades to the heuristic, which is the safe reading here.
    version: MODEL_VERSION,
    weights,
    bias,
    featureMeans,
    featureStds,
    featureNames: FEATURE_NAMES,
    breakevenRR,
    teamStats,
    longModel,
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
function accumulateAggregates(raw, teamStats) {
  const info = raw?.info;
  const teams = info?.teams ?? [];
  const gender = info?.gender;
  const formatClass = formatClassOf(info);
  const keyOf = (team) => teamKey(team, gender, formatClass);

  for (const team of teams) {
    const key = keyOf(team);
    teamStats[key] ??= {
      matchesPlayed: 0,
      totalRunsScored: 0,
      totalRunsConceded: 0,
      totalWicketsLost: 0,
      winRate: 0.5,
      trainWins: 0,
      trainMatches: 0,
    };
    teamStats[key].matchesPlayed++;
  }

  for (const inning of raw?.innings ?? []) {
    const battingKey = keyOf(inning.team);
    if (!teamStats[battingKey]) continue;
    const { runs } = inningsTotals(inning);
    let wickets = 0;
    for (const over of inning.overs ?? []) {
      for (const ball of over.deliveries ?? []) {
        if (ball.wickets?.length) wickets += ball.wickets.length;
      }
    }
    teamStats[battingKey].totalRunsScored += runs;
    teamStats[battingKey].totalWicketsLost += wickets;
    const bowlingTeam = teams.find((t) => t !== inning.team);
    if (bowlingTeam && teamStats[keyOf(bowlingTeam)]) {
      teamStats[keyOf(bowlingTeam)].totalRunsConceded += runs;
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
