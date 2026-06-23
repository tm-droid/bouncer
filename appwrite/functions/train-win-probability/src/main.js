import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import AdmZip from "adm-zip";

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

  log("Pass 1: counting team win rates...");
  const teamStats = {};
  const h2hAccumulator = {};
  // Pass 2 accumulators
  const trainingRows = []; // { features: [5], label: 0|1 }
  // For breakevenRR: sum runs and balls across all first innings
  let firstInningsTotalRuns = 0;
  let firstInningsTotalBalls = 0;

  let parsed = 0;
  let skipped = 0;

  const zip = new AdmZip(zipBuffer);
  zipBuffer = null; // hint GC — large buffer no longer needed once ZIP is loaded

  for (const entry of zip.getEntries()) {
    if (!entry.entryName.endsWith(".json") || entry.isDirectory) continue;
    try {
      const content = entry.getData().toString("utf8");
      const match = JSON.parse(content);
      processMatch(match, teamStats, trainingRows, (runs, balls) => {
        firstInningsTotalRuns += runs;
        firstInningsTotalBalls += balls;
      });
      processH2H(match, h2hAccumulator);
      parsed++;
    } catch (e) {
      if (skipped < 5) error(`Skipped ${entry.entryName}: ${e?.message}`);
      skipped++;
    }
  }
  log(`Parsed ${parsed} matches, skipped ${skipped}`);

  // Finalize win rates
  for (const team of Object.values(teamStats)) {
    team.winRate = team.matchesPlayed > 0 ? team.wins / team.matchesPlayed : 0.5;
    delete team.wins; // not needed downstream
  }

  log(`Collected ${trainingRows.length} raw training rows`);

  // Pass 2 back-fill: teamStrengthDiff is now computable since teamStats is finalised.
  // We already computed and stored features[4] during match processing, but we used
  // pre-finalised win rates (wins/matchesPlayed). Since processMatch is called mid-loop
  // before all matches are processed, feature[4] will be imprecise. Re-derive it now.
  // NOTE: To avoid this complexity we simply re-compute feature[4] from final winRates here.
  for (const row of trainingRows) {
    // row.battingTeam / row.bowlingTeam were stored temporarily for this pass.
    const bWin = teamStats[row._battingTeam]?.winRate ?? 0.5;
    const bwWin = teamStats[row._bowlingTeam]?.winRate ?? 0.5;
    row.features[4] = bWin - bwWin;
    // Clean up temporary fields
    delete row._battingTeam;
    delete row._bowlingTeam;
  }

  // Compute breakeven run rate from first innings data
  const breakevenRR = firstInningsTotalBalls > 0
    ? (firstInningsTotalRuns / firstInningsTotalBalls) * 6
    : 8.0;
  log(`Breakeven RR from data: ${breakevenRR.toFixed(3)}`);

  // Feature standardization
  const NUM_FEATURES = 5;
  const featureMeans = new Array(NUM_FEATURES).fill(0);
  const featureStds = new Array(NUM_FEATURES).fill(1);
  const n = trainingRows.length;

  if (n > 0) {
    // Compute means
    for (const row of trainingRows) {
      for (let j = 0; j < NUM_FEATURES; j++) {
        featureMeans[j] += row.features[j];
      }
    }
    for (let j = 0; j < NUM_FEATURES; j++) featureMeans[j] /= n;

    // Compute stds
    const variance = new Array(NUM_FEATURES).fill(0);
    for (const row of trainingRows) {
      for (let j = 0; j < NUM_FEATURES; j++) {
        const diff = row.features[j] - featureMeans[j];
        variance[j] += diff * diff;
      }
    }
    for (let j = 0; j < NUM_FEATURES; j++) {
      featureStds[j] = Math.sqrt(variance[j] / n) || 1;
    }

    // Standardize in place
    for (const row of trainingRows) {
      for (let j = 0; j < NUM_FEATURES; j++) {
        row.features[j] = (row.features[j] - featureMeans[j]) / featureStds[j];
      }
    }
  }

  log(`Feature means: ${featureMeans.map(v => v.toFixed(4)).join(", ")}`);
  log(`Feature stds:  ${featureStds.map(v => v.toFixed(4)).join(", ")}`);

  // Train logistic regression via mini-batch gradient descent
  const { weights, bias } = trainLogisticRegression(trainingRows, log);

  // Upload model to Appwrite Storage
  const storage = new Storage(client);
  const modelObj = {
    version: 2,
    weights,
    bias,
    featureMeans,
    featureStds,
    featureNames: ["runRateRatio", "wicketsInHand", "ballsRemainingFraction", "deficitPressure", "teamStrengthDiff"],
    breakevenRR,
    teamStats,
    trainingRows: n,
    epochs: EPOCHS,
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
    breakevenRR,
    weights,
    bias,
  });
};

/**
 * Pass 1 + Pass 2 combined: update teamStats win rates and collect training rows from
 * 2nd-innings chases in completed T20/IT20 matches.
 *
 * @param {object} raw - Cricsheet match JSON
 * @param {object} teamStats - mutable map of team → stats (accumulates across all calls)
 * @param {Array}  trainingRows - mutable array to push { features, label, _battingTeam, _bowlingTeam }
 * @param {Function} onFirstInnings - called with (runs, balls) for each first innings processed
 */
function processMatch(raw, teamStats, trainingRows, onFirstInnings) {
  const info = raw?.info;
  if (!info) return;
  if (info.match_type !== "T20" && info.match_type !== "IT20") return;

  const teams = info.teams ?? [];
  if (teams.length !== 2) return;

  // --- Pass 1: team win rate bookkeeping ---
  for (const team of teams) {
    if (!teamStats[team]) {
      teamStats[team] = {
        matchesPlayed: 0,
        wins: 0,
        totalRunsScored: 0,
        totalRunsConceded: 0,
        totalWicketsLost: 0,
        winRate: 0.5,
      };
    }
    teamStats[team].matchesPlayed++;
  }

  const outcome = info.outcome;
  if (outcome?.winner) {
    const winner = outcome.winner;
    if (teamStats[winner]) teamStats[winner].wins++;
  }

  // Batting stats from innings
  const innings = raw?.innings ?? [];
  for (const inning of innings) {
    const battingTeam = inning.team;
    if (!teamStats[battingTeam]) continue;
    let runs = 0;
    let wickets = 0;
    for (const over of inning.overs ?? []) {
      for (const ball of over.deliveries ?? []) {
        runs += ball.runs?.total ?? 0;
        if (ball.wickets?.length) wickets += ball.wickets.length;
      }
    }
    teamStats[battingTeam].totalRunsScored += runs;
    teamStats[battingTeam].totalWicketsLost += wickets;
    const bowlingTeam = teams.find(t => t !== battingTeam);
    if (bowlingTeam && teamStats[bowlingTeam]) {
      teamStats[bowlingTeam].totalRunsConceded += runs;
    }
  }

  // --- Pass 2: emit training rows from completed chases ---
  // Only process matches with a known winner (completed)
  if (!outcome?.winner) return;
  // Need at least 2 innings
  if (innings.length < 2) return;

  const firstInning = innings[0];
  const secondInning = innings[1];
  if (!firstInning || !secondInning) return;

  // Compute first innings total by summing all deliveries
  let firstInningsTotal = 0;
  let firstInningsBalls = 0;
  for (const over of firstInning.overs ?? []) {
    for (const ball of over.deliveries ?? []) {
      firstInningsTotal += ball.runs?.total ?? 0;
      firstInningsBalls++;
    }
  }

  // Report first innings totals for breakeven RR computation
  if (firstInningsBalls > 0) {
    onFirstInnings(firstInningsTotal, firstInningsBalls);
  }

  const targetRuns = firstInningsTotal + 1;
  const chasingTeam = secondInning.team;
  const bowlingTeam = teams.find(t => t !== chasingTeam) ?? "";
  const chasingTeamWon = outcome.winner === chasingTeam;
  const label = chasingTeamWon ? 1 : 0;

  // Walk deliveries in the chase, emitting one row per delivery (post-ball state)
  let runsScored = 0;
  let wicketsFallen = 0;
  let ballsFaced = 0;

  for (const over of secondInning.overs ?? []) {
    for (const ball of over.deliveries ?? []) {
      // Update state with this delivery
      runsScored += ball.runs?.total ?? 0;
      wicketsFallen += ball.wickets?.length ?? 0;

      const runsNeeded = targetRuns - runsScored;
      const ballsLeft = 120 - ballsFaced; // ballsFaced not yet incremented: balls left before this delivery lands

      ballsFaced++; // ballsFaced ≥ 1 here; crr division is always safe

      // Skip if innings effectively over
      if (ballsLeft <= 0) continue;
      if (wicketsFallen >= 10) continue;
      // Skip if chase is already won
      if (runsNeeded <= 0) continue;

      const crr = (runsScored * 6) / ballsFaced;
      const rrr = (runsNeeded * 6) / ballsLeft;

      // Clamp rrr to match inference behaviour (avoids skipping valid rows)
      const rrrClamped = Math.max(rrr, 0.1);

      const runRateRatio = crr / rrrClamped;
      const wicketsInHand = (10 - wicketsFallen) / 10;
      const ballsRemainingFraction = ballsLeft / 120;
      const deficitPressure = runsNeeded / Math.max(ballsLeft, 1);
      // teamStrengthDiff uses current (partial) win rates; will be recalculated
      // after all matches are processed from final win rates.
      const teamStrengthDiff = 0; // placeholder; overwritten in main after all matches parsed

      const features = [
        runRateRatio,
        wicketsInHand,
        ballsRemainingFraction,
        deficitPressure,
        teamStrengthDiff,
      ];

      // Reservoir sampling: keep max MAX_ROWS rows using a global counter so
      // acceptance probability is always 1/totalRowsSeen (never stuck at ≈1.0).
      trainingRows._totalRowsSeen = (trainingRows._totalRowsSeen ?? 0) + 1;
      const totalRowsSeen = trainingRows._totalRowsSeen;
      if (trainingRows.length < MAX_ROWS) {
        trainingRows.push({ features, label, _battingTeam: chasingTeam, _bowlingTeam: bowlingTeam });
      } else {
        const swapIdx = Math.floor(Math.random() * totalRowsSeen);
        if (swapIdx < MAX_ROWS) {
          trainingRows[swapIdx] = { features, label, _battingTeam: chasingTeam, _bowlingTeam: bowlingTeam };
        }
      }
    }
  }
}

/**
 * Mini-batch gradient descent logistic regression with L2 regularization.
 * Returns { weights: number[], bias: number }.
 */
function trainLogisticRegression(rows, log) {
  const NUM_FEATURES = 5;
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
