import { Client, Storage, Databases, Query } from "node-appwrite";
import { createHash } from "crypto";
import {
  liveScoresUrl,
  scorecardUrl,
  fetchHtml,
  parseCommentary,
  parseLiveDetail,
  parseScorecard,
} from "./cricbuzz.js";
import { generateLine, sanitize, stripPreamble } from "./llm.js";

const DB_ID = process.env.APPWRITE_AI_CACHE_DB_ID ?? "bouncer-db";
const COMMENTARY_COL = process.env.MATCH_COMMENTARY_COL_ID ?? "match-commentary";
const DETAIL_COL = process.env.MATCH_LIVE_DETAIL_COL_ID ?? "match-live-detail";
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const LIVE_FILE = "live-matches";

// Per-match per-run bounds (control LLM cost + stay within function timeout).
const SCAN_NEWEST = 30; // how many newest commentary items to look at
const MAX_NEW_PER_MATCH = 10; // max LLM-backed rows to write per match per run
const MAX_MATCHES = 12; // cap live matches processed per run

const BALL_RE = /(\d{1,3}\.\d)/;
const READ_ANY = ['read("any")'];

const docId = (s) => createHash("sha256").update(s).digest("hex").slice(0, 36);
const sanitizeMatchId = (id) => String(id).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 36);

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }
  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  const storage = new Storage(client);
  const db = new Databases(client);

  // 1. Read the live-matches list written by poll-live-matches.
  // node-appwrite auto-parses JSON files (returns the object); older/plain files return bytes.
  let matches = [];
  try {
    const raw = await storage.getFileDownload(BUCKET_ID, LIVE_FILE);
    const parsed = Buffer.isBuffer(raw) || raw instanceof ArrayBuffer
      ? JSON.parse(Buffer.from(raw).toString("utf8"))
      : raw;
    matches = parsed?.data?.matches ?? [];
  } catch (e) {
    log(`No live-matches file — nothing to sync`);
    return res.json({ skipped: true, reason: "no_live_matches" }, 200);
  }

  const live = matches.filter((m) => m && m.id).slice(0, MAX_MATCHES);
  const summary = {};

  // Process matches in parallel so per-ball LLM latency doesn't serialize across matches.
  await Promise.allSettled(
    live.map(async (m) => {
      const matchId = String(m.id);
      try {
        summary[matchId] = await syncMatch({ db, matchId, log, error });
      } catch (e) {
        error(`Match ${matchId} failed: ${e?.message}`);
        summary[matchId] = "error";
      }
    })
  );

  return res.json({ processed: live.length, summary });
};

async function syncMatch({ db, matchId, log, error }) {
  // 2. Fetch live page + scorecard in parallel.
  const [liveHtml, scoreHtml] = await Promise.all([
    fetchHtml(liveScoresUrl(matchId)),
    fetchHtml(scorecardUrl(matchId)).catch((e) => { error(`Scorecard fetch failed for ${matchId}: ${e?.message}`); return null; }),
  ]);

  // 3. Build + upsert the live-detail snapshot.
  const detail = parseLiveDetail(liveHtml);
  if (scoreHtml) {
    try {
      detail.scorecard = parseScorecard(scoreHtml);
    } catch (e) {
      error(`Scorecard parse failed for ${matchId}: ${e?.message}`);
    }
  }
  await upsertDetail({ db, matchId, detail, error });

  // 4. Commentary: newest-first, generate AI only for new ball lines.
  const commentary = parseCommentary(liveHtml).slice(0, SCAN_NEWEST);

  // Single read per match for existence: fetch the newest rows once and dedupe in memory,
  // instead of a getDocument per candidate (~30x fewer reads — keeps us under the org read budget).
  const existingIds = await existingCommentaryIds({ db, matchId, error });

  let written = 0;
  for (const item of commentary) {
    if (written >= MAX_NEW_PER_MATCH) break;
    const rowId = docId(`${matchId}:${item.timestamp}`);
    if (existingIds.has(rowId)) continue;

    const clean = sanitize(item.commText);
    const ballMatch = clean.match(BALL_RE);
    const ball = ballMatch ? ballMatch[1] : "";

    let line = clean;
    if (ball) {
      // Ball-by-ball line → cinematic AI rewrite (mirror generateBallCommentary).
      const bowler = detail.bowler?.name ?? "";
      const batsman = detail.batsmen?.[0]?.name ?? "";
      const score = detail.score ?? "";
      const contextLine = [
        bowler && `Bowler: ${bowler}`,
        batsman && `Batsman: ${batsman}`,
        score && `Score: ${score}`,
      ].filter(Boolean).join(" | ");
      const prompt =
        `Ball ${ball}: ${clean}\n` +
        (contextLine ? `${contextLine}\n` : "") +
        `Write one vivid broadcast commentary line. Start with ${ball}: at the beginning.`;
      const generated = await generateLine(prompt, { maxTokens: 100, temperature: 0.7, log, error });
      if (generated) {
        line = sanitize(stripPreamble(generated));
        if (line && !line.startsWith(ball)) line = `${ball}: ${line}`;
      }
    }
    if (!line) continue;

    try {
      await db.createDocument(
        DB_ID,
        COMMENTARY_COL,
        rowId,
        {
          match_id: matchId,
          innings: 1,
          ball,
          sort_key: item.timestamp,
          commentary: line,
          created_at: new Date().toISOString(),
        },
        READ_ANY
      );
      written++;
    } catch (e) {
      if (e?.code !== 409) error(`Commentary write failed (${matchId}/${ball}): ${e?.message}`);
    }
  }
  log(`Match ${matchId}: ${written} new commentary rows`);
  return { commentaryWritten: written };
}

async function upsertDetail({ db, matchId, detail, error }) {
  const rowId = sanitizeMatchId(matchId);
  const data = {
    match_id: matchId,
    payload: JSON.stringify(detail),
    updated_at: new Date().toISOString(),
  };
  try {
    await db.updateDocument(DB_ID, DETAIL_COL, rowId, data);
  } catch (e) {
    if (e?.code === 404) {
      try {
        await db.createDocument(DB_ID, DETAIL_COL, rowId, data, READ_ANY);
      } catch (e2) {
        if (e2?.code !== 409) error(`Detail create failed (${matchId}): ${e2?.message}`);
      }
    } else {
      error(`Detail update failed (${matchId}): ${e?.message}`);
    }
  }
}

// One read per match: the newest commentary row IDs for this match. The create-on-409
// path stays as the race backstop, so a missed/blank result only risks a redundant write.
async function existingCommentaryIds({ db, matchId, error }) {
  try {
    const result = await db.listRows(DB_ID, COMMENTARY_COL, [
      Query.equal("match_id", matchId),
      Query.orderDesc("sort_key"),
      Query.limit(SCAN_NEWEST),
    ]);
    return new Set((result.rows ?? []).map((r) => r.$id));
  } catch (e) {
    error(`Commentary existence read failed (${matchId}): ${e?.message}`);
    return new Set();
  }
}
