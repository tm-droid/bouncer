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
// Viewer presence: bouncer-match-presence writes { [matchId]: epochMs } here whenever a
// client has a live match open. We only generate for matches with a fresh viewer, so idle
// matches (and idle nights) cost no LLM calls. TTL covers ~2 missed 90s client heartbeats.
const PRESENCE_FILE = "active-viewers";
const VIEWER_TTL_MS = 4 * 60 * 1000;

// Per-match per-run bounds (control LLM cost + stay within function timeout).
const SCAN_NEWEST = 30; // how many newest commentary items to look at
const MAX_NEW_PER_MATCH = 10; // max LLM-backed rows to write per match per run
const MAX_MATCHES = 12; // cap live matches processed per run

const BALL_RE = /(\d{1,3}\.\d)/;
// Bare section headers Cricbuzz emits as commentary items with no body — render as
// pointless one-word lines (e.g. "Teams:", "Squads:"). "Drinks!" is a real break marker, kept.
const JUNK_COMMENTARY_RE = /^(teams|squads|playing xi)\s*:?\s*$/i;
const READ_ANY = ['read("any")'];

// Read-budget protection: Appwrite bills DB reads at the org level (monthly budget) and
// bills ONE read PER DOCUMENT RETURNED, not per query — a listDocuments(limit(30)) costs
// up to 30 reads. Exhausting the budget 402s every read project-wide. The dedupe read below
// is seeded once per warm container (see seenByMatch) so steady-state runs cost 0 reads;
// anything above READ_BUDGET_PER_RUN listDocuments calls can only be a future bug.
const READ_BUDGET_PER_RUN = 20;
const READ_402_BACKOFF_MS = 6 * 60 * 60 * 1000; // budget resets monthly; retrying sooner is pointless
let budget402UntilMs = 0; // survives warm invocations of the 1-min cron
let readsThisRun = 0;
// Dedupe cache: matchId -> Set<rowId> of commentary rows we know exist. Survives warm 1-min
// cron invocations (module scope), so we pay the seed read only on a cold container / a
// match's first appearance — never re-reading the same 30 rows every minute. Pruned each run
// to the live set so ended matches don't retain memory.
const seenByMatch = new Map();
const isBudget402 = (e) => e?.code === 402 || e?.type === "limit_databases_reads_exceeded";

const docId = (s) => createHash("sha256").update(s).digest("hex").slice(0, 36);
const sanitizeMatchId = (id) => String(id).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 36);

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }
  if (Date.now() < budget402UntilMs) {
    error(`[READ-BUDGET-402] skipping run — read budget exhausted, cooldown until ${new Date(budget402UntilMs).toISOString()}`);
    return res.json({ error: "read_budget_exceeded" }, 402);
  }
  readsThisRun = 0;
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

  // 1b. Gate on viewer presence: only sync matches someone currently has open.
  const activeViewers = await activeViewerIds({ storage, log, error });
  const live = matches
    .filter((m) => m && m.id && activeViewers.has(String(m.id)))
    .slice(0, MAX_MATCHES);
  if (live.length === 0) {
    log(`No matches with active viewers — nothing to sync`);
    return res.json({ skipped: true, reason: "no_viewers" }, 200);
  }
  // Prune the warm dedupe cache to matches still live so ended matches don't retain memory.
  const liveIds = new Set(live.map((m) => String(m.id)));
  for (const id of seenByMatch.keys()) {
    if (!liveIds.has(id)) seenByMatch.delete(id);
  }
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

  if (Date.now() < budget402UntilMs) {
    // A 402 surfaced mid-run: mark the whole execution failed so it's visible in the console.
    return res.json({ error: "read_budget_exceeded", summary }, 402);
  }
  return res.json({ processed: live.length, summary });
};

// Read the presence map written by bouncer-match-presence and return the set of matchIds
// with a viewer seen within VIEWER_TTL_MS. Any failure (missing file, parse error) is
// treated as "no active viewers" — the gate fails closed so we never generate blind.
async function activeViewerIds({ storage, log, error }) {
  const now = Date.now();
  try {
    const raw = await storage.getFileDownload(BUCKET_ID, PRESENCE_FILE);
    const map = JSON.parse(Buffer.from(raw).toString("utf8"));
    if (!map || typeof map !== "object" || Array.isArray(map)) return new Set();
    const fresh = Object.entries(map)
      .filter(([, ts]) => typeof ts === "number" && now - ts <= VIEWER_TTL_MS)
      .map(([id]) => id);
    return new Set(fresh);
  } catch (e) {
    log(`No active-viewers file (${e?.message ?? "missing"}) — treating as no viewers`);
    return new Set();
  }
}

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
  // Skip writing a hollow snapshot: parseLiveDetail returns an all-null skeleton when the
  // live page has no miniscore yet (pre-toss, parse miss, or error page). Writing it renders
  // a blank detail screen and — because upsertDetail uses updateDocument — would overwrite
  // previously-good data. Legit pre-match states still carry a status or a 0/0 score.
  const detailHasContent =
    !!detail.score ||
    !!detail.status ||
    (detail.batsmen?.length || 0) > 0 ||
    (detail.scorecard?.innings?.length || 0) > 0;
  if (detailHasContent) {
    await upsertDetail({ db, matchId, detail, error });
  } else {
    log(`Match ${matchId}: hollow live-detail, skipping upsert`);
  }

  // 4. Commentary: newest-first, generate AI only for new ball lines.
  const commentary = parseCommentary(liveHtml).slice(0, SCAN_NEWEST);

  // Dedupe against known-existing rows. The set is seeded from Appwrite exactly once — on a
  // cold container or this match's first appearance — then maintained in memory across warm
  // 1-min invocations, so steady-state runs cost 0 DB reads. Correctness never depends on it:
  // writes use a deterministic rowId + a 409 backstop, so a cold/empty set only risks a
  // redundant (409'd) write, never a duplicate row.
  let existingIds = seenByMatch.get(matchId);
  if (!existingIds) {
    existingIds = await existingCommentaryIds({ db, matchId, error });
    seenByMatch.set(matchId, existingIds);
    log(`Match ${matchId}: seeded dedupe from Appwrite (${existingIds.size} rows, cold)`);
  }

  // Newly-written commentary belongs to the innings currently being batted. The scorecard
  // carries one entry per innings played so far, so its length is the current innings number
  // (1 during the 1st innings, 2 during the 2nd, etc). Falls back to 1 when the scorecard
  // is unavailable. Replaces the previous hardcoded `innings: 1`.
  const currentInnings = detail.scorecard?.innings?.length || 1;

  let written = 0;
  for (const item of commentary) {
    if (written >= MAX_NEW_PER_MATCH) break;
    const rowId = docId(`${matchId}:${item.timestamp}`);
    if (existingIds.has(rowId)) continue;

    const clean = sanitize(item.commText);
    if (JUNK_COMMENTARY_RE.test(clean)) continue;
    const ballMatch = clean.match(BALL_RE);
    const ball = ballMatch ? ballMatch[1] : "";
    // Only persist ball-by-ball lines. Items with no ball number are session/meta chatter
    // (breaks, notes) that previously landed as malformed, ball-less rows — skip them.
    if (!ball) continue;

    let line = clean;
    {
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
          innings: currentInnings,
          ball,
          sort_key: item.timestamp,
          commentary: line,
          created_at: new Date().toISOString(),
        },
        READ_ANY
      );
      existingIds.add(rowId);
      written++;
    } catch (e) {
      // 409 = row already exists (race / seed missed it): record it so we don't retry it.
      if (e?.code === 409) existingIds.add(rowId);
      else error(`Commentary write failed (${matchId}/${ball}): ${e?.message}`);
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

// Seed read: the newest commentary row IDs for this match. Called at most once per match per
// warm container (see seenByMatch), NOT every run. Bills up to SCAN_NEWEST reads (one per row
// returned). The create-on-409 path stays as the race backstop, so a missed/blank result only
// risks a redundant write.
async function existingCommentaryIds({ db, matchId, error }) {
  if (++readsThisRun > READ_BUDGET_PER_RUN) {
    // Hard stop: with warm-container dedupe, seed reads/run should stay well under this.
    // Exceeding it means the cache isn't sticking (cold-loop) or a bug — fail loudly.
    throw new Error(`[READ-BUDGET-402] per-run read cap exceeded (${readsThisRun} > ${READ_BUDGET_PER_RUN}) — aborting`);
  }
  try {
    // node-appwrite 15 Databases API: listDocuments (listRows only exists on the
    // TablesDB service of newer SDKs — calling it here threw on every run, silently
    // disabling dedupe and pushing every write through the 409 backstop).
    const result = await db.listDocuments(DB_ID, COMMENTARY_COL, [
      Query.equal("match_id", matchId),
      Query.orderDesc("sort_key"),
      Query.limit(SCAN_NEWEST),
    ]);
    return new Set((result.documents ?? []).map((r) => r.$id));
  } catch (e) {
    if (isBudget402(e)) {
      budget402UntilMs = Date.now() + READ_402_BACKOFF_MS;
      error(`[READ-BUDGET-402] limit_databases_reads_exceeded on ${matchId} — aborting run, cooldown ${READ_402_BACKOFF_MS / 60000} min`);
      throw e; // fail the match (and run) loudly instead of silently emptying the dedupe set
    }
    error(`Commentary existence read failed (${matchId}): ${e?.message}`);
    return new Set();
  }
}
