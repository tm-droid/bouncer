import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { buildModel, publishProblems } from "./build.js";
import { MODEL_VERSION } from "./training.js";

// Every match Cricsheet publishes, each exactly once. The archive this replaced — t20s_json.zip —
// held 5,602 matches and 109 teams, all of them national sides, so every franchise fixture the app
// shows resolved to no team strength at all. This is 22,524 matches across 392 teams and 1,093
// competitions. Assembling the same coverage from per-competition archives would double-count:
// a match appears in both its league archive and its format archive, and a team's own archive too.
const CRICSHEET_URL = "https://cricsheet.org/downloads/all_json.zip";
const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";
const H2H_FILE_ID = "h2h-stats";

/**
 * The pointer readers follow to the model.
 *
 * The model used to live in one file that was deleted and recreated in place, so a reader booting
 * in between found nothing and served the heuristic for the life of its container, and a bad
 * refit replaced a good model with no way back. Each run now writes its model under a new id,
 * reads it back, and only then moves this pointer. The previous model stays in the bucket, so
 * rolling back is rewriting one small file.
 */
export const MANIFEST_FILE_ID = "winprob-manifest";

const mb = (bytes) => (bytes / 1024 / 1024).toFixed(2);

/** Whatever the SDK handed back for a JSON download — bytes or an already-parsed object. */
function parseDownload(raw) {
  if (raw == null) return null;
  if (typeof raw === "string") return JSON.parse(raw);
  if (Buffer.isBuffer(raw) || raw instanceof ArrayBuffer || ArrayBuffer.isView(raw)) {
    return JSON.parse(Buffer.from(raw).toString("utf8"));
  }
  return raw;
}

async function putJson(storage, fileId, value) {
  const buffer = Buffer.from(JSON.stringify(value), "utf8");
  await storage.deleteFile(STATS_BUCKET_ID, fileId).catch(() => {});
  await storage.createFile(STATS_BUCKET_ID, fileId, InputFile.fromBuffer(buffer, `${fileId}.json`), ['read("any")']);
  return buffer.length;
}

async function readJson(storage, fileId) {
  try {
    return parseDownload(await storage.getFileDownload(STATS_BUCKET_ID, fileId));
  } catch {
    return null;
  }
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
  const storage = new Storage(client);

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

  const h2hAccumulator = {};
  const built = buildModel({ zipBuffer, log, error, onRaw: (raw) => processH2H(raw, h2hAccumulator) });
  zipBuffer = null;
  const { model, shards, parsed, skipped } = built;

  const previous = await readJson(storage, MANIFEST_FILE_ID);
  const problems = publishProblems(model, previous?.heads ?? null);
  if (problems.length) {
    for (const p of problems) error(`Not publishing: ${p}`);
    return res.json({ published: false, problems, serving: previous?.fileId ?? null }, 500);
  }

  // Tables first, then the model, then the pointer: a reader that follows the pointer must find
  // everything it names already in place.
  let published;
  try {
    let tableBytes = 0;
    const ids = [...shards.keys()];
    for (let i = 0; i < ids.length; i += 6) {
      const sizes = await Promise.all(ids.slice(i, i + 6).map((id) => putJson(storage, id, shards.get(id))));
      tableBytes += sizes.reduce((a, b) => a + b, 0);
    }
    log(`Wrote ${ids.length} table files (${mb(tableBytes)} MB)`);

    const fileId = `winprob-v${MODEL_VERSION}-${model.trainedAt.replace(/[^0-9]/g, "").slice(0, 12)}`;
    const bytes = await putJson(storage, fileId, model);
    const readBack = await readJson(storage, fileId);
    if (readBack?.version !== MODEL_VERSION || !readBack?.chase?.core?.weights) {
      throw new Error(`model file ${fileId} did not read back as a v${MODEL_VERSION} model`);
    }
    const heads = Object.fromEntries(
      ["chase", "firstInnings", "prematch", "long"].map((h) => [h, { holdout: model[h]?.holdout ?? null }])
    );
    await putJson(storage, MANIFEST_FILE_ID, {
      version: MODEL_VERSION,
      fileId,
      previousFileId: previous?.fileId ?? null,
      trainedAt: model.trainedAt,
      asOf: model.asOf,
      heads,
    });
    log(`Published ${fileId} (${Object.keys(model.teamStats).length} team keys, ${mb(bytes)} MB)`);
    // Two models are kept: the one serving and the one before it. The one before that goes.
    if (previous?.previousFileId && previous.previousFileId !== fileId) {
      await storage.deleteFile(STATS_BUCKET_ID, previous.previousFileId).catch(() => {});
    }
    published = fileId;
  } catch (e) {
    error(`Failed to publish the model: ${e?.message}`);
    return res.json({ error: e?.message }, 500);
  }

  // Write H2H stats (non-fatal — model upload already succeeded)
  let h2hPairs = 0;
  try {
    const h2hFinal = finalizeH2H(h2hAccumulator);
    h2hPairs = Object.keys(h2hFinal).length;
    const bytes = await putJson(storage, H2H_FILE_ID, { version: 1, h2h: h2hFinal });
    log(`H2H stats written: ${h2hPairs} team pairs (${mb(bytes)} MB)`);
  } catch (e) {
    error(`Failed to upload H2H stats: ${e?.message}`);
  }

  return res.json({
    published,
    parsed,
    skipped,
    teamKeys: Object.keys(model.teamStats).length,
    h2hPairs,
    holdoutCutoff: model.windows.holdoutCutoff,
    chase: model.chase?.holdout ?? null,
    firstInnings: model.firstInnings?.holdout ?? null,
    prematch: model.prematch?.holdout ?? null,
    long: model.long?.holdout ?? null,
    peakRssMb: Number((process.memoryUsage().rss / 1024 / 1024).toFixed(0)),
  });
};

export { buildModel };

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
