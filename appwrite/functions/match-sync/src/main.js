import { Client, Storage, Databases } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { createHash } from "crypto";
import { parseJsonFile } from "./storageJson.js";
import {
  liveScoresUrl,
  scorecardUrl,
  fetchHtml,
  parseCommentary,
  parseLiveDetail,
  parseScorecard,
} from "./cricbuzz.js";
import { generateLine, sanitize, stripPreamble } from "./llm.js";
import { evaluate, formatFromText, ballsRemainingFor } from "./winprob.js";

const DB_ID = process.env.APPWRITE_AI_CACHE_DB_ID ?? "bouncer-db";
const COMMENTARY_COL = process.env.MATCH_COMMENTARY_COL_ID ?? "match-commentary";
const DETAIL_COL = process.env.MATCH_LIVE_DETAIL_COL_ID ?? "match-live-detail";
const SCORECARD_COL = process.env.MATCH_SCORECARD_COL_ID ?? "match-scorecard";
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const LIVE_FILE = "live-matches";
// Aggregate win probabilities for every live match, keyed by matchId. The device reads this one
// file for its match-list cards: live-matches.json carries only { id, title, status } with no
// scores, so nothing upstream of here can compute a probability, and reading one detail row per
// visible card would cost one billed DB read per card per refresh.
const WINPROB_FILE = "live-winprob";
// The trained model written by bouncer-train-win-probability. Without it every match falls back to
// the heuristic, which is deliberately crude — the calibrated numbers come from the model.
const TEAM_STATS_FILE = "team-stats";
let cachedModel = null;        // warm-container cache; one storage read per cold start
let modelLoadFailed = false;   // retry on the next cold start, not every minute
// Viewer presence: bouncer-match-presence writes { [matchId]: epochMs } here whenever a
// client has a live match open. We only generate for matches with a fresh viewer, so idle
// matches (and idle nights) cost no LLM calls. TTL covers ~2 missed 90s client heartbeats.
const PRESENCE_FILE = "active-viewers";
const VIEWER_TTL_MS = 4 * 60 * 1000;

// Per-match per-run bounds (control LLM cost + stay within function timeout).
const SCAN_NEWEST = 30; // how many newest commentary items to look at
const MAX_NEW_PER_MATCH = 10; // max LLM-backed rows to write per match per run
const MAX_MATCHES = 12; // cap live matches processed per run

// Bare section headers Cricbuzz emits as commentary items with no body — render as
// pointless one-word lines (e.g. "Teams:", "Squads:"). "Drinks!" is a real break marker, kept.
const JUNK_COMMENTARY_RE = /^(teams|squads|playing xi)\s*:?\s*$/i;
const READ_ANY = ['read("any")'];

// Read-budget protection: Appwrite bills DB reads at the org level (monthly budget) and
// bills ONE read PER DOCUMENT RETURNED, not per query. The seed read used to be a
// listDocuments(limit(30)) costing up to 30 reads per match per cold container; it is now a
// single getDocument on the match's own detail row (1 read). Exhausting the budget 402s every
// read project-wide, so anything above READ_BUDGET_PER_RUN reads can only be a future bug.
const READ_BUDGET_PER_RUN = 20;
const READ_402_BACKOFF_MS = 6 * 60 * 60 * 1000; // budget resets monthly; retrying sooner is pointless
let budget402UntilMs = 0; // survives warm invocations of the 1-min cron
let readsThisRun = 0;
// Dedupe watermark: matchId -> highest commentary sort_key already written. Survives warm 1-min
// cron invocations (module scope), so the seed read is paid only on a cold container / a match's
// first appearance. Pruned each run to the live set so ended matches don't retain memory.
//
// A high-water mark replaces the old id-set because the two carry the same information for this
// purpose — commentary only ever arrives newest-after-oldest — while costing 1 read instead of 30.
// Correctness never depended on either: writes use a deterministic rowId plus a 409 backstop.
const watermarkByMatch = new Map();
const isBudget402 = (e) => e?.code === 402 || e?.type === "limit_databases_reads_exceeded";

const docId = (s) => createHash("sha256").update(s).digest("hex").slice(0, 36);
const sanitizeMatchId = (id) => String(id).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 36);

/**
 * The cleaned text and ball number for a commentary item, or null when it must be skipped.
 *
 * Pure and exported so SMOKE_PROBE can exercise it without a database. `ffe9656` deleted the
 * `const clean = sanitize(item.commText)` binding from the middle of the write loop and left all
 * four of its uses, so every run threw `ReferenceError: clean is not defined` on the first fresh
 * item of every match. Nothing failed loudly — the per-match catch logged and moved on — and no
 * commentary row was written for a month.
 *
 * The ball number comes from Cricbuzz's own `ballMetric` field. Matching a ball-prefix regex
 * against `commText` (which is what this did) finds nothing, because the number is a sibling field
 * on the same node and never appears in the text. That alone would have kept this path dead even
 * with `clean` restored.
 */
export function classifyCommentaryItem(item) {
  const clean = sanitize(item.commText);
  if (!clean) return null;
  if (JUNK_COMMENTARY_RE.test(clean)) return null;
  const ball = item.ballMetric?.label ?? "";
  if (!ball) return null;
  return { clean, ball };
}

// Exercised on every invocation. An unbound identifier inside classifyCommentaryItem only throws
// once a live match has a fresh item, so it can hide for weeks; this makes it a hard failure on
// the very next run instead.
//
// Both ball-label forms are probed. Every defect this pipeline has had was format-specific and
// invisible to an over-based-only check: the Hundred label reached `stripPreamble` with an
// over-based-only regex and kept its refusal preamble, and the Kotlin side rendered it
// "Ball Ball 72:". An over-based probe passes cleanly through both.
const SMOKE_PROBES = [
  { item: { commText: "Bumrah to Root, no run", ballMetric: { label: "18.5" } }, ball: "18.5" },
  { item: { commText: "Jansen to Banton, FOUR", ballMetric: { label: "Ball 72" } }, ball: "Ball 72" },
];

// The preamble stripper is the other half of the same format sensitivity, and it fails silently:
// a missed cut writes the model's refusal to `match-commentary` as if it were commentary.
const SMOKE_PREAMBLE = [
  ["Since there is no ball number provided, Ball 72: Banton clears the ropes.", "Ball 72: Banton clears the ropes."],
  ["Since there is no ball number provided, 18.5: Kohli drives through covers.", "18.5: Kohli drives through covers."],
];

function runSmokeProbes() {
  for (const { item, ball } of SMOKE_PROBES) {
    const probe = classifyCommentaryItem(item);
    if (probe?.ball !== ball || !probe.clean) {
      throw new Error(`classifier: ${ball} → ${JSON.stringify(probe)}`);
    }
  }
  for (const [input, want] of SMOKE_PREAMBLE) {
    const got = stripPreamble(input);
    if (got !== want) throw new Error(`stripPreamble: got ${JSON.stringify(got)}, want ${JSON.stringify(want)}`);
  }
}

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }
  if (Date.now() < budget402UntilMs) {
    error(`[READ-BUDGET-402] skipping run — read budget exhausted, cooldown until ${new Date(budget402UntilMs).toISOString()}`);
    return res.json({ error: "read_budget_exceeded" }, 402);
  }
  try {
    runSmokeProbes();
  } catch (e) {
    error(`[SMOKE] commentary classifier is broken: ${e.message}`);
    return res.json({ error: "commentary_classifier_broken", detail: e.message }, 500);
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
    const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, LIVE_FILE));
    matches = parsed?.data?.matches ?? [];
  } catch (e) {
    log(`No live-matches file — nothing to sync`);
    return res.json({ skipped: true, reason: "no_live_matches" }, 200);
  }

  // 1b. Viewer presence gates the EXPENSIVE work only.
  //
  // Commentary generation costs an LLM call per ball, so it stays restricted to matches someone
  // currently has open. Win probability costs one page fetch and some arithmetic, and the match
  // list needs it for every live match — nobody has a match "open" while they are looking at the
  // list. So the two are now separated: every live match gets a probability, only viewed matches
  // get commentary and a detail row.
  const activeViewers = await activeViewerIds({ storage, log, error });
  const allLive = matches.filter((m) => m && m.id).slice(0, MAX_MATCHES);
  const live = allLive.filter((m) => activeViewers.has(String(m.id)));
  if (allLive.length === 0) {
    log(`No live matches — nothing to sync`);
    return res.json({ skipped: true, reason: "no_live_matches" }, 200);
  }
  log(`${allLive.length} live, ${live.length} with viewers`);
  // Prune the warm dedupe cache to matches still live so ended matches don't retain memory.
  const liveIds = new Set(live.map((m) => String(m.id)));
  for (const id of watermarkByMatch.keys()) {
    if (!liveIds.has(id)) watermarkByMatch.delete(id);
  }
  const summary = {};
  const winProb = {};
  const viewed = new Set(live.map((m) => String(m.id)));
  const model = await loadModel({ storage, log, error });

  // Process matches in parallel so per-ball LLM latency doesn't serialize across matches.
  await Promise.allSettled(
    allLive.map(async (m) => {
      const matchId = String(m.id);
      try {
        const result = await syncMatch({
          db,
          matchId,
          title: m.title ?? "",
          withCommentary: viewed.has(matchId),
          model,
          log,
          error,
        });
        summary[matchId] = result.status;
        if (result.winProb) winProb[matchId] = result.winProb;
      } catch (e) {
        error(`Match ${matchId} failed: ${e?.message}`);
        summary[matchId] = "error";
      }
    })
  );

  await writeWinProbFile({ storage, winProb, log, error });

  if (Date.now() < budget402UntilMs) {
    // A 402 surfaced mid-run: mark the whole execution failed so it's visible in the console.
    return res.json({ error: "read_budget_exceeded", summary }, 402);
  }
  return res.json({
    live: allLive.length,
    processed: live.length,
    winProbabilities: Object.keys(winProb).length,
    summary,
  });
};

/**
 * Publish the aggregate win-probability file the match list reads.
 *
 * One small storage object rather than a DB row per match: storage is billed on a separate meter
 * from the org DB-read budget that the 402 breaker protects, and the device pays a single
 * download regardless of how many cards are on screen.
 */
async function writeWinProbFile({ storage, winProb, log, error }) {
  try {
    const payload = JSON.stringify({ version: 1, updatedAt: Date.now(), matches: winProb });
    await storage.deleteFile(BUCKET_ID, WINPROB_FILE).catch(() => {});
    await storage.createFile(
      BUCKET_ID,
      WINPROB_FILE,
      InputFile.fromBuffer(Buffer.from(payload, "utf8"), "live-winprob.json"),
      READ_ANY
    );
    log(`Wrote ${WINPROB_FILE} (${Object.keys(winProb).length} matches)`);
  } catch (e) {
    // Non-fatal: commentary and detail rows are already written, and a stale probability file is
    // better than failing the run.
    error(`Failed to write ${WINPROB_FILE}: ${e?.message}`);
  }
}

/**
 * Win probability for the FIRST-INNINGS batting side, matching the team1Score/team2Score
 * convention the detail payload already uses, so the device needs no second team mapping.
 */
async function loadModel({ storage, log, error }) {
  if (cachedModel || modelLoadFailed) return cachedModel;
  try {
    const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, TEAM_STATS_FILE));
    cachedModel = parsed;
    log(`Loaded win-probability model v${parsed?.version ?? "?"} (${Object.keys(parsed?.teamStats ?? {}).length} teams)`);
  } catch (e) {
    modelLoadFailed = true;
    error(`Could not load ${TEAM_STATS_FILE} (${e?.message}) — falling back to the heuristic`);
  }
  return cachedModel;
}

function winProbFor(detail, title, model) {
  const s = detail?.situation;
  if (!s) return null;

  const format = formatFromText(s.matchFormat, title);
  const ballsRemaining = ballsRemainingFor({
    oversRemaining: s.oversRemaining,
    ballsBowled: s.ballsBowled,
    format,
  });
  const result = evaluate({
    runsNeeded: s.runsNeeded ?? 0,
    ballsRemaining: ballsRemaining ?? 0,
    wicketsLost: s.wicketsLost ?? 0,
    currentRunRate: s.currentRunRate ?? 0,
    requiredRunRate: s.requiredRunRate ?? 0,
    battingTeam: s.battingTeamName ?? "",
    bowlingTeam: s.bowlingTeamName ?? "",
    format,
    model,
  });
  if (result.undetermined) return null;

  const batting = result.battingTeamWinProbability;
  return {
    team1WinProbability: s.battingTeamIsTeam1 ? batting : 1 - batting,
    confidence: result.confidence,
    battingTeamName: s.battingTeamName,
    battingTeamIsTeam1: s.battingTeamIsTeam1,
    format,
  };
}

// Read the presence map written by bouncer-match-presence and return the set of matchIds
// with a viewer seen within VIEWER_TTL_MS. Any failure (missing file, parse error) is
// treated as "no active viewers" — the gate fails closed so we never generate blind.
async function activeViewerIds({ storage, log, error }) {
  const now = Date.now();
  try {
    const map = parseJsonFile(await storage.getFileDownload(BUCKET_ID, PRESENCE_FILE));
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

async function syncMatch({ db, matchId, title, withCommentary, model, log, error }) {
  // 2. Fetch live page, plus the scorecard only when we are going to use it. A match with no
  // viewer needs the live page alone to produce a win probability.
  const [liveHtml, scoreHtml] = await Promise.all([
    fetchHtml(liveScoresUrl(matchId)),
    withCommentary
      ? fetchHtml(scorecardUrl(matchId)).catch((e) => { error(`Scorecard fetch failed for ${matchId}: ${e?.message}`); return null; })
      : Promise.resolve(null),
  ]);

  // 3. Build + upsert the live-detail snapshot.
  const detail = parseLiveDetail(liveHtml);
  const winProb = winProbFor(detail, title, model);
  // Carried in the detail payload as well as the aggregate file, so the detail screen updates on
  // the existing realtime channel instead of waiting for a file download. `payload` is an opaque
  // JSON string column and Gson ignores unknown keys, so this needs no Appwrite schema change and
  // is safe for older app builds.
  if (winProb) {
    detail.winProbabilityTeam1 = winProb.team1WinProbability;
    detail.winProbabilityConfidence = winProb.confidence;
  }

  if (!withCommentary) {
    // No viewer: the probability is the only product. No scorecard, no LLM, no DB write.
    return { status: winProb ? "winprob_only" : "winprob_undetermined", winProb };
  }

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
    // The scorecard now lives ONLY here. upsertDetail strips it from the detail payload, so this
    // write is the sole source for the Scorecard tab and for the Overview's scorecard-derived
    // widgets. The app subscribes to this row's own channel for the life of the detail screen.
    if ((detail.scorecard?.innings?.length || 0) > 0) {
      await upsertScorecard({ db, matchId, scorecard: detail.scorecard, error });
    }
  } else {
    log(`Match ${matchId}: hollow live-detail, skipping upsert`);
  }

  // 4. Commentary: newest-first, generate AI only for new ball lines.
  const commentary = parseCommentary(liveHtml).slice(0, SCAN_NEWEST);

  // Dedupe against everything already written, via a high-water mark seeded from Appwrite exactly
  // once — on a cold container or this match's first appearance — then maintained in memory across
  // warm 1-min invocations, so steady-state runs cost 0 DB reads.
  let watermark = watermarkByMatch.get(matchId);
  if (watermark === undefined) {
    watermark = await commentaryWatermark({ db, matchId, error });
    watermarkByMatch.set(matchId, watermark);
    log(`Match ${matchId}: seeded commentary watermark ${watermark} (cold)`);
  }

  // Newly-written commentary belongs to the innings currently being batted. The scorecard
  // carries one entry per innings played so far, so its length is the current innings number
  // (1 during the 1st innings, 2 during the 2nd, etc). Falls back to 1 when the scorecard
  // is unavailable. Replaces the previous hardcoded `innings: 1`.
  const currentInnings = detail.scorecard?.innings?.length || 1;

  // Oldest-first among items past the watermark. Order matters: the watermark can only advance
  // contiguously from the bottom, so processing newest-first would strand every older unwritten
  // item behind it permanently once MAX_NEW_PER_MATCH truncated the run.
  const fresh = commentary
    .filter((i) => Number(i.timestamp) > watermark)
    .sort((a, b) => Number(a.timestamp) - Number(b.timestamp));

  let written = 0;
  let newWatermark = watermark;
  for (const item of fresh) {
    // Checked before any work so the watermark stops at the last FULLY processed item.
    if (written >= MAX_NEW_PER_MATCH) break;
    const rowId = docId(`${matchId}:${item.timestamp}`);
    const itemTs = Number(item.timestamp);

    // Skipped items are still "processed" — advance past them or they are re-examined forever.
    const classified = classifyCommentaryItem(item);
    if (!classified) { newWatermark = itemTs; continue; }
    const { clean, ball } = classified;

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
        `${ball}: ${clean}\n` +
        (contextLine ? `${contextLine}\n` : "") +
        `Write one vivid broadcast commentary line. Start with ${ball}: at the beginning.`;
      const generated = await generateLine(prompt, { maxTokens: 100, temperature: 0.7, log, error });
      if (generated) {
        line = sanitize(stripPreamble(generated));
        if (line && !line.startsWith(ball)) line = `${ball}: ${line}`;
      }
    }
    if (!line) { newWatermark = itemTs; continue; }

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
      newWatermark = itemTs;
      written++;
    } catch (e) {
      // 409 = row already exists (race / watermark behind): it is durably written, so advance.
      if (e?.code === 409) newWatermark = itemTs;
      else error(`Commentary write failed (${matchId}/${ball}): ${e?.message}`);
    }
  }

  if (newWatermark > watermark) {
    watermarkByMatch.set(matchId, newWatermark);
    await persistWatermark({ db, matchId, watermark: newWatermark, error });
  }
  log(`Match ${matchId}: ${written} new commentary rows`);
  return { status: { commentaryWritten: written }, winProb };
}

async function upsertDetail({ db, matchId, detail, error }) {
  const rowId = sanitizeMatchId(matchId);
  const data = {
    match_id: matchId,
    // The scorecard is stripped here, not from `detail` itself: the caller still reads
    // detail.scorecard.innings.length to number the current innings. Measured on live rows, the
    // scorecard is 85-93% of this payload (4-9 KB of 4-9 KB), and the payload is pushed to every
    // subscriber of this row every minute. It lives in match-scorecard now, on its own channel.
    payload: JSON.stringify({ ...detail, scorecard: undefined }),
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

// Same deterministic rowId as the detail row, in a table with no index and no realtime subscriber.
// Costs one extra write per viewed match per minute and saves ~60 KB on every realtime message
// that match sends to every device watching it.
async function upsertScorecard({ db, matchId, scorecard, error }) {
  const rowId = sanitizeMatchId(matchId);
  const data = {
    match_id: matchId,
    scorecard: JSON.stringify(scorecard),
    updated_at: new Date().toISOString(),
  };
  try {
    await db.updateDocument(DB_ID, SCORECARD_COL, rowId, data);
  } catch (e) {
    if (e?.code === 404) {
      try {
        await db.createDocument(DB_ID, SCORECARD_COL, rowId, data, READ_ANY);
      } catch (e2) {
        if (e2?.code !== 409) error(`Scorecard create failed (${matchId}): ${e2?.message}`);
      }
    } else {
      error(`Scorecard update failed (${matchId}): ${e?.message}`);
    }
  }
}

// Seed read: the highest commentary sort_key already written for this match, stored alongside the
// match's own detail row. Called at most once per match per warm container (see watermarkByMatch),
// NOT every run.
//
// This replaced a listDocuments(limit(SCAN_NEWEST)) over the commentary table. Appwrite bills one
// read per document RETURNED, so that query cost up to 30 reads per match per cold container; a
// getDocument on a row we are already writing every run costs exactly 1. The create-on-409 path
// stays as the race backstop, so a missed or zero result only risks a redundant write.
async function commentaryWatermark({ db, matchId, error }) {
  if (++readsThisRun > READ_BUDGET_PER_RUN) {
    // Hard stop: with warm-container dedupe, seed reads/run should stay well under this.
    // Exceeding it means the cache isn't sticking (cold-loop) or a bug — fail loudly.
    throw new Error(`[READ-BUDGET-402] per-run read cap exceeded (${readsThisRun} > ${READ_BUDGET_PER_RUN}) — aborting`);
  }
  try {
    const row = await db.getDocument(DB_ID, DETAIL_COL, sanitizeMatchId(matchId));
    return Number(row?.last_commentary_sort_key) || 0;
  } catch (e) {
    // 404 = this match has no detail row yet, so nothing has been written for it either.
    if (e?.code === 404) return 0;
    if (isBudget402(e)) {
      budget402UntilMs = Date.now() + READ_402_BACKOFF_MS;
      error(`[READ-BUDGET-402] limit_databases_reads_exceeded on ${matchId} — aborting run, cooldown ${READ_402_BACKOFF_MS / 60000} min`);
      throw e; // fail the match (and run) loudly instead of silently resetting the watermark
    }
    error(`Commentary watermark read failed (${matchId}): ${e?.message}`);
    // Rethrow: returning 0 here would re-generate (and re-pay for) every line in the scan window.
    throw e;
  }
}

// Persist the advanced watermark onto the detail row. Deliberately a separate, tiny update rather
// than folding it into upsertDetail: that upsert runs BEFORE commentary is generated, and delaying
// it until after would hold back the live-score realtime push by the whole LLM round-trip.
// At most one extra write per viewed match per run, and only when new commentary was written.
async function persistWatermark({ db, matchId, watermark, error }) {
  try {
    await db.updateDocument(DB_ID, DETAIL_COL, sanitizeMatchId(matchId), {
      last_commentary_sort_key: watermark,
    });
  } catch (e) {
    // Losing it only costs a re-scan on the next cold container — never fail the match for this.
    error(`Watermark persist failed (${matchId}): ${e?.message}`);
  }
}
