import { Client, Storage } from "node-appwrite";

const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";
const TEAM_STATS_FILE_ID = "team-stats";

// Warm cache: loaded once per container instance
let cachedTeamStats = null;

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

  // Load team stats (cached across warm invocations)
  if (!cachedTeamStats) {
    try {
      const storage = new Storage(client);
      const fileBuffer = await storage.getFileDownload(STATS_BUCKET_ID, TEAM_STATS_FILE_ID);
      const text = Buffer.from(fileBuffer).toString("utf8");
      cachedTeamStats = JSON.parse(text);
      log(`Loaded team stats (${Object.keys(cachedTeamStats).length} teams)`);
    } catch (e) {
      error(`Failed to load team stats: ${e?.message}`);
      // Leave cachedTeamStats null so next invocation retries
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
    teamStats: cachedTeamStats ?? {},
  });

  return res.json(probability);
};

function calculateWinProbability({ runsNeeded, ballsRemaining, wicketsLost, currentRunRate, requiredRunRate, battingTeam, bowlingTeam, teamStats }) {
  const FIRST_INNINGS_BREAKEVEN_RR = 8.0; // T20 par score heuristic
  const wicketsInHand = 10 - wicketsLost;
  const oversRemaining = ballsRemaining / 6;

  let rawProb = 0.5;
  if (requiredRunRate > 0) {
    const rrRatio = currentRunRate / requiredRunRate;
    const wicketFactor = (wicketsInHand / 10) * 0.3;
    const oversFactor = Math.min(oversRemaining / 20, 1.0) * 0.1;
    rawProb = sigmoid((rrRatio - 1.0) * 3 + wicketFactor - oversFactor);
  } else {
    rawProb = sigmoid((currentRunRate - FIRST_INNINGS_BREAKEVEN_RR) * 0.5 + (wicketsInHand / 10 - 0.5) * 1.5);
  }

  const battingWinRate = teamStats[battingTeam]?.winRate ?? 0.5;
  const bowlingWinRate = teamStats[bowlingTeam]?.winRate ?? 0.5;
  const teamAdjustment = (battingWinRate - bowlingWinRate) * 0.1;
  rawProb = Math.min(0.97, Math.max(0.03, rawProb + teamAdjustment));

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
