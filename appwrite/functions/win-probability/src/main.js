import { Client, Storage } from "node-appwrite";

const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";
const TEAM_STATS_FILE_ID = "team-stats";

// Warm cache: loaded once per container instance
let cachedModel = null;

export default async ({ req, res, log, error }) => {
  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY ?? "");

  // Parse request body
  let body;
  try {
    body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
  } catch (e) {
    return res.json({ error: "Invalid JSON body" }, 400);
  }

  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return res.json({ error: "Body must be a JSON object" }, 400);
  }

  const {
    runsNeeded = 0,
    ballsRemaining = 0,
    wicketsLost = 0,
    currentRunRate = 0,
    requiredRunRate = 0,
    battingTeam = "",
    bowlingTeam = "",
  } = body;

  if (ballsRemaining <= 0) {
    return res.json({ team1WinProbability: 0.5, team2WinProbability: 0.5, confidence: 0.0 });
  }

  // Load model (cached across warm invocations); retry on next invocation if it fails
  if (!cachedModel) {
    try {
      const storage = new Storage(client);
      const fileBuffer = await storage.getFileDownload(STATS_BUCKET_ID, TEAM_STATS_FILE_ID);
      const text = Buffer.from(fileBuffer).toString("utf8");
      cachedModel = JSON.parse(text);
      if (cachedModel.version === 2) {
        log(`Loaded v2 model (${Object.keys(cachedModel.teamStats ?? {}).length} teams, ${cachedModel.trainingRows ?? "?"} training rows)`);
      } else {
        log(`Loaded legacy model (${Object.keys(cachedModel).length} teams)`);
      }
    } catch (e) {
      error(`Failed to load model: ${e?.message}`);
      // Leave cachedModel null so next invocation retries
    }
  }

  const probability = calculateWinProbability({
    runsNeeded,
    ballsRemaining,
    wicketsLost,
    currentRunRate,
    requiredRunRate,
    battingTeam,
    bowlingTeam,
    model: cachedModel,
  });

  return res.json(probability);
};

function calculateWinProbability({ runsNeeded, ballsRemaining, wicketsLost, currentRunRate, requiredRunRate, battingTeam, bowlingTeam, model }) {
  const wicketsInHand = 10 - wicketsLost;

  // Detect v2 learned model
  const isV2 = model != null && model.version === 2 && Array.isArray(model.weights);

  let rawProb;

  if (isV2) {
    // --- Learned model path ---
    const teamStats = model.teamStats ?? {};
    const battingWinRate = teamStats[battingTeam]?.winRate ?? 0.5;
    const bowlingWinRate = teamStats[bowlingTeam]?.winRate ?? 0.5;

    if (requiredRunRate > 0) {
      // Chase (2nd innings) inference
      const crr = currentRunRate;
      const rrr = requiredRunRate;

      const runRateRatio = crr / Math.max(rrr, 0.1);
      const wicketsInHandNorm = wicketsInHand / 10;
      const ballsRemainingFraction = ballsRemaining / 120;
      const deficitPressure = runsNeeded / Math.max(ballsRemaining, 1);
      const teamStrengthDiff = battingWinRate - bowlingWinRate;

      const features = [runRateRatio, wicketsInHandNorm, ballsRemainingFraction, deficitPressure, teamStrengthDiff];

      // Standardize using training statistics
      const scaled = features.map(
        (x, i) => (x - (model.featureMeans[i] ?? 0)) / (model.featureStds[i] || 1)
      );

      let logit = model.bias ?? 0;
      for (let i = 0; i < model.weights.length; i++) {
        logit += model.weights[i] * scaled[i];
      }
      rawProb = sigmoid(logit);
    } else {
      // First-innings inference using data-driven breakeven RR
      const breakevenRR = model.breakevenRR ?? 8.0;
      rawProb = sigmoid(
        (currentRunRate - breakevenRR) * 0.5 + (wicketsInHand / 10 - 0.5) * 1.5
      );
    }
  } else {
    // --- Legacy / fallback hardcoded formula ---
    const FIRST_INNINGS_BREAKEVEN_RR = 8.0;
    const oversRemaining = ballsRemaining / 6;

    if (requiredRunRate > 0) {
      const rrRatio = currentRunRate / requiredRunRate;
      const wicketFactor = (wicketsInHand / 10) * 0.3;
      const oversFactor = Math.min(oversRemaining / 20, 1.0) * 0.1;
      rawProb = sigmoid((rrRatio - 1.0) * 3 + wicketFactor - oversFactor);
    } else {
      rawProb = sigmoid((currentRunRate - FIRST_INNINGS_BREAKEVEN_RR) * 0.5 + (wicketsInHand / 10 - 0.5) * 1.5);
    }

    // Team adjustment (legacy only — v2 bakes team strength into the feature vector)
    const teamStats = model ?? {};
    const battingWinRate = teamStats[battingTeam]?.winRate ?? 0.5;
    const bowlingWinRate = teamStats[bowlingTeam]?.winRate ?? 0.5;
    const teamAdjustment = (battingWinRate - bowlingWinRate) * 0.1;
    rawProb = rawProb + teamAdjustment;
  }

  // Clamp to [0.03, 0.97]
  rawProb = Math.min(0.97, Math.max(0.03, rawProb));

  const confidence = Math.abs(rawProb - 0.5) * 2;

  return {
    team1WinProbability: rawProb,
    team2WinProbability: 1 - rawProb,
    confidence,
  };
}

function sigmoid(x) {
  return 1 / (1 + Math.exp(-x));
}
