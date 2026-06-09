import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import AdmZip from "adm-zip";

const CRICSHEET_URL = "https://cricsheet.org/downloads/t20s_json.zip";
const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";
const TEAM_STATS_FILE_ID = "team-stats";

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

  log("Parsing match files...");
  const teamStats = {};
  let parsed = 0;
  let skipped = 0;

  const zip = new AdmZip(zipBuffer);
  zipBuffer = null; // hint GC — 200MB buffer no longer needed once ZIP is loaded
  for (const entry of zip.getEntries()) {
    if (!entry.entryName.endsWith(".json") || entry.isDirectory) continue;
    try {
      const content = entry.getData().toString("utf8");
      const match = JSON.parse(content);
      processMatch(match, teamStats);
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

  // Upload to Appwrite Storage
  const storage = new Storage(client);
  const statsJson = JSON.stringify(teamStats);
  const buffer = Buffer.from(statsJson, "utf8");

  try {
    // Delete old file if exists, ignore 404
    await storage.deleteFile(STATS_BUCKET_ID, TEAM_STATS_FILE_ID).catch(() => {});
    await storage.createFile(
      STATS_BUCKET_ID,
      TEAM_STATS_FILE_ID,
      InputFile.fromBuffer(buffer, "team-stats.json"),
      ["read(\"any\")"]
    );
    log(`Uploaded team-stats.json (${Object.keys(teamStats).length} teams, ${(buffer.length / 1024).toFixed(0)} KB)`);
  } catch (e) {
    error(`Failed to upload stats: ${e?.message}`);
    return res.json({ error: e?.message }, 500);
  }

  return res.json({ parsed, skipped, teams: Object.keys(teamStats).length });
};

function processMatch(raw, teamStats) {
  const info = raw?.info;
  if (!info) return;
  if (info.match_type !== "T20" && info.match_type !== "IT20") return;

  const teams = info.teams ?? [];
  if (teams.length !== 2) return;

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
}
