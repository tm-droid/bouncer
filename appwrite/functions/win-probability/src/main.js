import { Client, Storage } from "node-appwrite";
import { evaluate, formatFromText, MODEL_VERSION } from "./winprob.js";

const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";
const TEAM_STATS_FILE_ID = "team-stats";

// Warm cache: loaded once per container instance
let cachedModel = null;

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    log("APPWRITE_API_KEY not set — model load will fail, using heuristic fallback");
  }

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

  // Load model (cached across warm invocations); retry on next invocation if it fails
  if (!cachedModel) {
    try {
      const storage = new Storage(client);
      const fileBuffer = await storage.getFileDownload(STATS_BUCKET_ID, TEAM_STATS_FILE_ID);
      const text = Buffer.from(fileBuffer).toString("utf8");
      cachedModel = JSON.parse(text);
      if (cachedModel.version === MODEL_VERSION) {
        log(`Loaded v${MODEL_VERSION} model (${Object.keys(cachedModel.teamStats ?? {}).length} teams, ${cachedModel.trainingRows ?? "?"} training rows)`);
      } else {
        // Fail closed onto the heuristic. A v2 artifact was trained with a hardcoded 120-ball
        // denominator, so its feature scaling is valid for T20 only; feeding it format-correct
        // fractions would be silently wrong with no error anywhere.
        log(`Model is version ${cachedModel.version ?? "legacy"}, not v${MODEL_VERSION} — using heuristic and team win rates only`);
      }
    } catch (e) {
      error(`Failed to load model: ${e?.message}`);
      // Leave cachedModel null so next invocation retries
    }
  }

  // `format` may be sent directly, or derived from whatever descriptive text the caller has.
  const format = body.format ?? formatFromText(body.matchType, body.matchTitle, body.series);

  const result = evaluate({
    runsNeeded: body.runsNeeded ?? 0,
    ballsRemaining: body.ballsRemaining ?? 0,
    wicketsLost: body.wicketsLost ?? 0,
    currentRunRate: body.currentRunRate ?? 0,
    requiredRunRate: body.requiredRunRate ?? 0,
    battingTeam: body.battingTeam ?? "",
    bowlingTeam: body.bowlingTeam ?? "",
    format,
    totalBalls: body.totalBalls ?? null,
    model: cachedModel,
  });

  // battingTeamIndex is echoed back so the caller never has to re-derive which side the number is
  // about. The old response called this field `team1WinProbability` while computing it from the
  // batting team, which inverted the number whenever team 2 was batting.
  return res.json({
    ...result,
    format,
    battingTeamIndex: Number.isInteger(body.battingTeamIndex) ? body.battingTeamIndex : null,
  });
};
