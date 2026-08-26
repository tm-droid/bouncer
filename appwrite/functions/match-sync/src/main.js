// DEPLOYING THIS FUNCTION
//
//   node appwrite/tools/deploy-function.mjs bouncer-match-sync --with-node-modules
//
// That is the only path. Pushing to main does NOT deploy this function any more — the GitHub link
// was removed on 2026-08-05 (installationId 6a40a4cb5b3834b385d6, repository 1168989305, branch
// main, root appwrite/functions/match-sync, if it ever needs restoring from the console).
//
// It was removed because it caused a 22-minute production outage and then raced the fix. A push
// auto-built this function from the repo, and the repo had a PARTIAL node_modules committed, so the
// build produced an @sentry/node with a valid package.json and no build/ directory; npm install
// treated it as satisfied, the deployment reported `ready` at 4.8MB instead of 18MB, and every
// scheduled run died at module load with "Cannot find module @sentry/node/build/esm/index.js" —
// before any handler code, so nothing in observability.js could report it. Later, the trigger
// proved non-deterministic as well: three consecutive pushes touching this directory fired, did
// not fire, and fired again, twice superseding a manual deployment seconds after it went live.
//
// The `--with-node-modules` flag is not optional here: this function has repeatedly failed to build
// from a source-only archive (an Appwrite sidecar bug), which the VCS path could not do anyway.
import { Client, Storage, Databases } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { createHash } from "crypto";
import { parseJsonFile } from "./storageJson.js";
import {
  liveScoresUrl,
  scorecardUrl,
  fetchHtml,
  parseBallOutcome,
  parseCommentary,
  parseLiveDetail,
  parseScorecard,
  BLOCK_BACKOFF_MS,
} from "./cricbuzz.js";
import { classifyEvent, deadProviders, generateVerifiedLine, isHighStakesBall, sanitize, stripPreamble } from "./llm.js";
import {
  initObservability,
  report,
  reportError,
  flushObservability,
  probeSources,
  startCheckIn,
  finishCheckIn,
} from "./observability.js";
import { evaluate, evaluateLong, evaluatePrematch, formatFromText, ballsRemainingFor } from "./winprob.js";
import {
  resolveMatchTeams, teamStatsOf, genderFromTitle, teamsFromTitle, stripWomenSuffix, teamKey,
} from "./teamnames.js";

const DB_ID = process.env.APPWRITE_AI_CACHE_DB_ID ?? "bouncer-db";
const COMMENTARY_COL = process.env.MATCH_COMMENTARY_COL_ID ?? "match-commentary";
const DETAIL_COL = process.env.MATCH_LIVE_DETAIL_COL_ID ?? "match-live-detail";
const SCORECARD_COL = process.env.MATCH_SCORECARD_COL_ID ?? "match-scorecard";
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const LIVE_FILE = "live-matches";
const UPCOMING_FILE = "upcoming-matches";
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
// Pre-match costs no network call per fixture — the model is already in memory — so this is a cap
// on the written file rather than on work. Today's list is 20; a long tour week is not much more.
const MAX_PREMATCH = 60;

// ── Slow tier ──────────────────────────────────────────────────────────────
//
// The viewer gate above is the FAST tier and is unchanged: a match someone has open gets a full
// pass every minute. The problem it leaves is that a match nobody watched while it was live gets
// nothing AT ALL, permanently — it drops out of live-matches the moment it ends, and no scheduled
// path ever revisits a finished match. Both Hundred games of 2026-07-30 ended that way: no detail
// row, no scorecard row, not one commentary line.
//
// So a live match with no viewer still gets covered, just far more cheaply: its own 45-minute
// clock, one row a pass instead of 10, and only whatever read budget the fast tier has not
// reserved. Its clock cannot live in module scope — a warm container does not survive a 45-minute
// gap reliably — so the last pass per match is persisted in Storage, which bills on a different
// meter from the DB reads the 402 breaker protects.
//
// The binding constraint on these two numbers is NOT the Appwrite read budget — it is Groq's
// 1000 requests/day, because every slow-tier row costs one LLM call and the fast tier draws from
// the same daily pool. Measured 2026-07-30: the fast tier alone spent 267 calls in a single hour.
// At 8 concurrent unwatched matches, one row a pass:
//
//   15 min → 32 calls/hour → 768/day. One 07-30-style hour on top = 1035. Over cap.
//   30 min → 16 calls/hour → 384/day. Leaves 616 — barely two such hours.
//   45 min → 11 calls/hour → 256/day. Leaves 744 — about three such hours. Chosen.
//
// 07-30 is not known to be a ceiling, so the margin has to cover several of those hours, not one.
const SLOW_TIER_INTERVAL_MS = 45 * 60 * 1000;
const SLOW_MAX_NEW_PER_MATCH = 1;
const SLOW_MAX_MATCHES_PER_RUN = 3;
const SLOW_STATE_FILE = "slow-sync-state";
// Reads out of READ_BUDGET_PER_RUN kept for the fast tier. The slow tier is admitted only if this
// much would still be free after the fast tier's worst case, so a busy night full of viewers
// starves the slow tier rather than the other way round.
const READ_HEADROOM_RESERVE = 5;
// Worst-case billed reads a single match costs: one watermark seed, on a cold container.
const READ_COST_PER_MATCH = 1;

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
/**
 * Which live matches the slow tier takes this run, as a pure function of the run's inputs.
 *
 * Pure, and exported, because every rule here is a state that is invisible in production until it
 * is wrong: "no entry yet" must mean due NOW (a match that just went live would otherwise wait a
 * quarter of an hour for its first line), the interval must actually hold matches back, and the
 * headroom gate must shut the tier off rather than quietly eating the fast tier's budget.
 *
 * The fast tier's cost is *reserved* up front rather than measured after it runs. Measuring would
 * mean a second pass over the same matches — the live page fetched twice for every slow match —
 * to learn something the viewer count already tells us: its worst case is one seed read each.
 *
 * @param live      live matches, already capped at MAX_MATCHES
 * @param viewed    matchIds with a fresh viewer (the fast tier's claim)
 * @param state     { [matchId]: lastSlowPassEpochMs } from SLOW_STATE_FILE; missing entry = due
 * @param now       epoch ms
 * @param cooldownUntil  budget402UntilMs — a latched read-budget 402 stops the tier entirely
 * @returns matchIds for the slow tier, in due order
 */
export function selectSlowTier({ live, viewed, state, now, cooldownUntil = 0 }) {
  if (now < cooldownUntil) return [];
  const fastReserve = viewed.size * READ_COST_PER_MATCH;
  const headroom = READ_BUDGET_PER_RUN - READ_HEADROOM_RESERVE - fastReserve;
  if (headroom < READ_COST_PER_MATCH) return [];

  const due = live
    .map((m) => String(m.id))
    .filter((id) => !viewed.has(id))
    // `?? 0` is load-bearing: a match with no entry has never had a slow pass, so it is due now.
    .filter((id) => now - (Number(state?.[id]) || 0) >= SLOW_TIER_INTERVAL_MS);

  return due.slice(0, Math.min(SLOW_MAX_MATCHES_PER_RUN, Math.floor(headroom / READ_COST_PER_MATCH)));
}

/** The slow-tier clock, stamped for the matches that just ran and pruned to those still live. */
export function nextSlowState({ state, processed, liveIds, now }) {
  const next = {};
  for (const [id, ts] of Object.entries(state ?? {})) {
    if (liveIds.has(id) && typeof ts === "number") next[id] = ts;
  }
  for (const id of processed) next[id] = now;
  return next;
}

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

/**
 * The run's outcome for the cron monitor, module-scope for the same reason `readsThisRun` is: an
 * Appwrite container serves one execution at a time.
 *
 * Defaults to "error" and is upgraded to "ok" only by a return path that reached a healthy end. An
 * unhandled throw therefore reports "error" by omission rather than needing to be caught, which is
 * the correct default for a check-in — the failure modes here are exactly the ones that skip code.
 */
let runStatus = "error";

/**
 * Wrapper that owns the check-in and the flush, so neither can be forgotten by a `return` added
 * later. `handleRun` is the function that used to be the default export.
 */
export default async (context) => {
  initObservability({ log: context.log });
  runStatus = "error";
  // Opened AFTER init so the DSN is loaded, and deliberately not for a PROBE_SOURCES run — that is
  // a manual diagnostic which returns before doing any of the scheduled work, and letting it report
  // "ok" would tell the monitor a sync happened when none did.
  const checkIn = process.env.PROBE_SOURCES ? null : startCheckIn();
  try {
    return await handleRun(context);
  } catch (e) {
    reportError(e, { operation: "unhandled" });
    throw e;
  } finally {
    finishCheckIn(checkIn, runStatus);
    await flushObservability();
  }
};

async function handleRun({ req, res, log, error }) {
  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }

  // Source reachability probe. Off unless PROBE_SOURCES is set, so the per-minute schedule never
  // pays for it. Answers "can this container reach Cricinfo, and is its markup still what
  // CricInfoScraper expects" — the question that decides whether Cricinfo can be restored as a
  // server-side second commentary source, which no amount of testing from a laptop can settle.
  if (process.env.PROBE_SOURCES) {
    const probe = await probeSources({ log, error });
    await flushObservability();
    return res.json({ probe }, 200);
  }

  if (Date.now() < budget402UntilMs) {
    error(`[READ-BUDGET-402] skipping run — read budget exhausted, cooldown until ${new Date(budget402UntilMs).toISOString()}`);
    report("match-sync skipped a run — read budget 402 latched", {
      cooldownUntil: new Date(budget402UntilMs).toISOString(),
    }, "error");
    await flushObservability();
    return res.json({ error: "read_budget_exceeded" }, 402);
  }
  try {
    runSmokeProbes();
  } catch (e) {
    error(`[SMOKE] commentary classifier is broken: ${e.message}`);
    reportError(e, { operation: "smoke_probe" });
    await flushObservability();
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
  // The model is loaded before anything is gated on, because the pre-match pass below needs it even
  // on a run with nothing live — which is most of the night, and exactly when the app is showing
  // only upcoming fixtures.
  const model = await loadModel({ storage, log, error });
  const prematch = await prematchEntries({ storage, model, log, error });

  let matches = [];
  try {
    const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, LIVE_FILE));
    matches = parsed?.data?.matches ?? [];
  } catch (e) {
    log(`No live-matches file — writing ${Object.keys(prematch).length} pre-match entries only`);
    // A healthy run. poll-live-matches simply has nothing for us, which is the normal state
    // overnight — reporting it as a failed check-in would page every night. The file is still
    // written: an upcoming fixture has a probability whether or not anything is in play, and
    // returning early used to leave yesterday's live numbers as the newest thing on the device.
    await writeWinProbFile({ storage, winProb: prematch, log, error });
    runStatus = "ok";
    return res.json({ skipped: true, reason: "no_live_matches", prematch: Object.keys(prematch).length }, 200);
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
    log(`No live matches — writing ${Object.keys(prematch).length} pre-match entries only`);
    await writeWinProbFile({ storage, winProb: prematch, log, error });
    runStatus = "ok";
    return res.json({ skipped: true, reason: "no_live_matches", prematch: Object.keys(prematch).length }, 200);
  }
  // Prune the warm dedupe cache to matches still live so ended matches don't retain memory.
  //
  // Against ALL live matches, not just viewed ones: pruning to the viewed set discarded the
  // watermark of every unviewed match on every run, so the slow tier below would have paid a fresh
  // seed read on each of its passes for a value it already had.
  const liveIds = new Set(allLive.map((m) => String(m.id)));
  for (const id of watermarkByMatch.keys()) {
    if (!liveIds.has(id)) watermarkByMatch.delete(id);
  }
  const summary = {};
  // Seeded with the pre-match entries so a fixture that has not started still has a number.
  // A live entry for the same id overwrites it, which is the right precedence: a position beats a
  // prior the moment there is one.
  const winProb = { ...prematch };
  const viewed = new Set(live.map((m) => String(m.id)));

  // 1c. Slow tier: live, unviewed, and due. Chosen before any match runs, so the fast tier's
  // worst-case read cost is reserved rather than raced for.
  const slowState = await readSlowState({ storage, error });
  const slowTier = new Set(
    selectSlowTier({
      live: allLive,
      viewed,
      state: slowState,
      now: Date.now(),
      cooldownUntil: budget402UntilMs,
    })
  );
  log(`${allLive.length} live, ${live.length} with viewers, ${slowTier.size} on the slow tier`);

  // Process matches in parallel so per-ball LLM latency doesn't serialize across matches.
  await Promise.allSettled(
    allLive.map(async (m) => {
      const matchId = String(m.id);
      try {
        const result = await syncMatch({
          db,
          matchId,
          title: m.title ?? "",
          commentaryCap: commentaryCapFor(matchId, viewed, slowTier),
          model,
          log,
          error,
        });
        summary[matchId] = result.status;
        if (result.winProb) winProb[matchId] = result.winProb;
      } catch (e) {
        error(`Match ${matchId} failed: ${e?.message}`);
        // A block is the host telling us to stand down, not a defect on our side — it is reported
        // once per run with its own fingerprint instead of one exception per match, so a throttled
        // minute does not read as twelve independent failures.
        if (e?.blocked) {
          summary[matchId] = "blocked";
        } else {
          reportError(e, { matchId, operation: "sync_match" });
          summary[matchId] = "error";
        }
      }
    })
  );

  // A viewed match that produced nothing is the failure mode this whole change exists to expose:
  // someone has the screen open, the fast tier ran, and the device still has no rows to render, so
  // it falls back to raw Cricbuzz text. Reported per run rather than per match — the count is the
  // signal, and the per-match reason has already been captured above.
  const viewedWithoutRows = [...viewed].filter((id) => !summary[id] || summary[id] === "error");
  if (viewedWithoutRows.length > 0) {
    report("match-sync produced nothing for a match with an active viewer", {
      matchIds: viewedWithoutRows.join(","),
      viewers: viewed.size,
    });
  }

  // One event per throttled run, carrying what the host actually said — the body snippet is the
  // thing that was missing when ANDROID-35 had to be diagnosed from status codes alone.
  const blockedIds = Object.keys(summary).filter((id) => summary[id] === "blocked");
  if (blockedIds.length > 0) {
    error(`[CRICBUZZ-BLOCKED] ${blockedIds.length} match(es) skipped this run`);
    report("Cricbuzz is throttling this container — matches skipped", {
      matches: blockedIds.length,
      matchIds: blockedIds.join(","),
      backoffMs: BLOCK_BACKOFF_MS,
    });
  }

  await writeWinProbFile({ storage, winProb, log, error });
  await writeSlowState({
    storage,
    state: nextSlowState({ state: slowState, processed: slowTier, liveIds, now: Date.now() }),
    previous: slowState,
    log,
    error,
  });

  reportDeadProviders(error);

  if (Date.now() < budget402UntilMs) {
    // A 402 surfaced mid-run: mark the whole execution failed so it's visible in the console.
    report("match-sync hit a read-budget 402 mid-run", { summary: JSON.stringify(summary) }, "error");
    await flushObservability();
    return res.json({ error: "read_budget_exceeded", summary }, 402);
  }
  runStatus = "ok";
  return res.json({
    live: allLive.length,
    processed: live.length,
    slowTier: slowTier.size,
    winProbabilities: Object.keys(winProb).length,
    summary,
  });
}

/** Rows this match may write this run: full for a viewer, a trickle for the slow tier, none else. */
export function commentaryCapFor(matchId, viewed, slowTier) {
  if (viewed.has(matchId)) return MAX_NEW_PER_MATCH;
  if (slowTier.has(matchId)) return SLOW_MAX_NEW_PER_MATCH;
  return 0;
}

/**
 * The slow tier's clock. A missing or unreadable file reads as `{}` — every live match is then
 * due, which is the safe direction: the tier is capped per run anyway, and the alternative
 * (treating unreadable as "nothing is due") would silently disable it exactly like the
 * already-parsed-JSON bug disabled the viewer gate.
 */
async function readSlowState({ storage, error }) {
  try {
    const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, SLOW_STATE_FILE));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch (e) {
    if (e?.code !== 404) error(`Could not read ${SLOW_STATE_FILE} (${e?.message}) — treating every match as due`);
    return {};
  }
}

/** Write the clock only when it changed — an unchanged rewrite is bandwidth for nothing. */
async function writeSlowState({ storage, state, previous, log, error }) {
  const payload = JSON.stringify(state);
  if (payload === JSON.stringify(previous ?? {})) return;
  try {
    await storage.deleteFile(BUCKET_ID, SLOW_STATE_FILE).catch(() => {});
    await storage.createFile(
      BUCKET_ID,
      SLOW_STATE_FILE,
      InputFile.fromBuffer(Buffer.from(payload, "utf8"), "slow-sync-state.json"),
      READ_ANY
    );
    log(`Wrote ${SLOW_STATE_FILE} (${Object.keys(state).length} matches)`);
  } catch (e) {
    // Non-fatal: losing the clock costs one extra slow pass, never a wrong one.
    error(`Failed to write ${SLOW_STATE_FILE}: ${e?.message}`);
  }
}

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

/**
 * Pre-match probabilities for the fixtures that have not started.
 *
 * Reads the same `upcoming-matches` file the app's Upcoming tab renders from, so the set is exactly
 * what a user can see. Only fixtures where BOTH sides resolve to corpus teams get an entry — the
 * rest are omitted rather than published as an even 50/50, which would be indistinguishable from a
 * considered call on a match we know nothing about.
 *
 * Non-fatal throughout: this runs before the live sync and must never be the reason a live match
 * goes unscored.
 */
async function prematchEntries({ storage, model, log, error }) {
  const entries = {};
  if (!model) return entries;
  let fixtures = [];
  try {
    const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, UPCOMING_FILE));
    fixtures = parsed?.data?.matches ?? [];
  } catch (e) {
    if (e?.code !== 404) error(`Could not read ${UPCOMING_FILE} (${e?.message}) — no pre-match this run`);
    return entries;
  }

  const teamStats = teamStatsOf(model);
  let resolved = 0;
  for (const fixture of fixtures.slice(0, MAX_PREMATCH)) {
    if (!fixture?.id || !fixture.title) continue;
    const title = String(fixture.title);
    // `matchFormat` is Cricbuzz's own field, carried through by bouncer-poll-live-matches. The
    // title is the fallback for a fixture Cricbuzz's schedule page did not list.
    const format = formatFromText(fixture.matchFormat, title);
    const gender = genderFromTitle(title);
    const sides = teamsFromTitle(title);
    if (!sides) continue;

    // Unlike a live match there is no miniscore and so no short code to attribute a batting side
    // from — but none is needed, because the answer is oriented to the first named side. What is
    // needed is that both sides are corpus teams in this slice, which is what a null here means.
    //
    // The probe below runs only when the format is genuinely unknown, and it is a guess: it finds
    // the first slice in which both teams have a record, which answers "where have these teams
    // played" rather than "what is this match". On 2026-08-07 that scored eight One-Day Cup
    // fixtures and an ODI World Cup League game on T20 form, and on Scotland vs Canada it named the
    // wrong favourite (0.496 for Scotland on T20 form against 0.556 on their actual ODI record).
    // With the real format present there is one candidate and no guessing.
    const candidates = format === "UNKNOWN" ? ["T20", "ODI", "HUNDRED", "TEST"] : [format];
    const slice = candidates.find((f) => resolveMatchTeams({
      title, battingTeamName: sides[0], teamStats, format: f, gender,
    }) || bothSidesKnown({ sides, teamStats, format: f, gender }));
    if (!slice) continue;

    const result = evaluatePrematch({
      firstTeam: stripWomenSuffix(sides[0]),
      secondTeam: stripWomenSuffix(sides[1]),
      gender,
      format: slice,
      resolved: true,
      model,
    });
    if (result.undetermined) continue;
    resolved++;
    entries[String(fixture.id)] = {
      team1WinProbability: result.battingTeamWinProbability,
      drawProbability: null,
      confidence: result.confidence,
      // The device renders this differently and labels it "PRE-MATCH". A prior shown as a live
      // reading claims far more than team strength alone can support.
      prematch: true,
      format: slice,
    };
  }
  log(`Pre-match: ${resolved} of ${fixtures.length} upcoming fixtures scored`);
  return entries;
}

/** Both named sides have a record in this (gender, format) slice, regardless of who bats first. */
function bothSidesKnown({ sides, teamStats, format, gender }) {
  return sides.every((side) => {
    const key = teamKey(stripWomenSuffix(side), gender, format);
    return Object.prototype.hasOwnProperty.call(teamStats, key);
  });
}

function winProbFor(detail, title, model) {
  const s = detail?.situation;
  if (!s) return null;

  const format = formatFromText(s.matchFormat, title);
  // The miniscore names teams by short code ("SA", "SUR"), the model keys them by Cricsheet full
  // name, so passing the raw code missed every lookup and left teamStrengthDiff at zero. Resolution
  // is off the title and returns null for anything outside the corpus, which is most domestic
  // cricket — those keep the neutral 0.5 they already had.
  const resolved = resolveMatchTeams({
    title,
    battingTeamName: s.battingTeamName,
    teamStats: teamStatsOf(model),
    format,
    gender: genderFromTitle(title),
  });
  const battingTeam = resolved?.battingTeam ?? s.battingTeamName ?? "";
  const bowlingTeam = resolved?.bowlingTeam ?? s.bowlingTeamName ?? "";
  const gender = resolved?.gender ?? genderFromTitle(title);

  const result = s.long
    ? evaluateLong({
      lead: s.long.lead ?? 0,
      wicketsLost: s.wicketsLost ?? 0,
      inningsIndex: s.long.inningsIndex ?? 0,
      ballsBowled: s.long.ballsBowled ?? 0,
      scheduledDays: s.long.scheduledDays ?? 5,
      inningsRunRate: s.currentRunRate ?? 0,
      ownInningsUsed: s.long.ownInningsUsed ?? 1,
      oppInningsUsed: s.long.oppInningsUsed ?? 0,
      followedOn: s.long.followedOn ?? false,
      battingTeam,
      bowlingTeam,
      gender,
      model,
    })
    : evaluate({
      runsNeeded: s.runsNeeded ?? 0,
      runsScored: s.runsScored ?? 0,
      ballsRemaining: ballsRemainingFor({
        oversRemaining: s.oversRemaining,
        ballsBowled: s.ballsBowled,
        format,
      }) ?? 0,
      wicketsLost: s.wicketsLost ?? 0,
      currentRunRate: s.currentRunRate ?? 0,
      requiredRunRate: s.requiredRunRate ?? 0,
      battingTeam,
      bowlingTeam,
      gender,
      format,
      model,
    });
  if (result.undetermined) return null;

  const batting = result.battingTeamWinProbability;
  return {
    team1WinProbability: s.battingTeamIsTeam1 ? batting : 1 - batting,
    // A draw belongs to neither side, so unlike the win probability it is not re-oriented.
    drawProbability: result.drawProbability,
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
    // 404 is the ordinary state before any client has ever pinged, and is not worth an event.
    // Anything else means the gate is failing closed for a reason we cannot see from the log —
    // and a permanently-closed gate is exactly the "0 with viewers on every run" symptom.
    if (e?.code !== 404) {
      report("match-sync could not read the active-viewers file — viewer gate is failing closed", {
        reason: e?.message ?? "unknown",
        code: e?.code ?? "none",
      });
    }
    return new Set();
  }
}

async function syncMatch({ db, matchId, title, commentaryCap, model, log, error }) {
  // 2. Fetch live page, plus the scorecard only when we are going to use it. A match with no
  // viewer needs the live page alone to produce a win probability.
  //
  // `commentaryCap` is how many rows this match may write this run: 0 for a live match nobody is
  // watching and that is not due a slow pass, SLOW_MAX_NEW_PER_MATCH for the slow tier,
  // MAX_NEW_PER_MATCH when someone has it open. It replaces a `withCommentary` boolean — the tier
  // and the cap are the same decision, and splitting them into two arguments is how they drift.
  const [liveHtml, scoreHtml] = await Promise.all([
    fetchHtml(liveScoresUrl(matchId)),
    commentaryCap > 0
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
    // Only a Test carries one. Null for every other format, and the device renders a two-segment
    // bar unless it is present rather than inferring a draw from the arithmetic.
    detail.winProbabilityDraw = winProb.drawProbability;
  }

  if (commentaryCap <= 0) {
    // No viewer and not due a slow pass: the probability is the only product. No scorecard, no
    // LLM, no DB write.
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
  // item behind it permanently once `commentaryCap` truncated the run.
  const fresh = commentary
    .filter((i) => Number(i.timestamp) > watermark)
    .sort((a, b) => Number(a.timestamp) - Number(b.timestamp));

  let written = 0;
  let newWatermark = watermark;
  for (const item of fresh) {
    // Checked before any work so the watermark stops at the last FULLY processed item.
    if (written >= commentaryCap) break;
    const rowId = docId(`${matchId}:${item.timestamp}`);
    const itemTs = Number(item.timestamp);

    // Skipped items are still "processed" — advance past them or they are re-examined forever.
    const classified = classifyCommentaryItem(item);
    if (!classified) { newWatermark = itemTs; continue; }
    const { clean, ball } = classified;

    let line = clean;
    // False when the AI chain produced nothing and the row below carries the scraper's own words.
    // The row is still written — a raw line beats an empty commentary tab — but "we wrote raw
    // Cricbuzz text" and "we wrote generated commentary" were previously the same event, in the
    // data and in the logs, so a dead provider chain was indistinguishable from a working one.
    let aiGenerated = true;
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
      const attempts = [];
      // Verified generation: the hallucination check that used to run on the device, where the
      // provider keys no longer live. A rejected line is a failed generation, not a usable one —
      // it falls through to raw text and is marked, rather than being written as if it were AI.
      const generated = await generateVerifiedLine(prompt, {
        // Covers reasoning tokens as well as the line itself. gpt-oss emits its reasoning first
        // and only then the `content`; at 100 the budget was spent before a single word of
        // commentary was written and every call came back `empty completion`.
        maxTokens: 320,
        temperature: 0.7,
        // Narrowed from unconditional `true` on 2026-08-05. See isHighStakesBall: verification is
        // the difference between one provider call a line and two, and the chain sustains ~48
        // requests/minute in total. Spending that on a dot ball is spending it on the deliveries
        // with nothing to invent.
        verify: isHighStakesBall(clean),
        context: { snippet1: clean, ball, bowler, batsman },
        log,
        error,
        attempts,
      });
      if (generated.line) {
        line = sanitize(stripPreamble(generated.line));
        if (line && !line.startsWith(ball)) line = `${ball}: ${line}`;
      } else {
        aiGenerated = false;
        error(`[PASSTHROUGH] ${matchId}/${ball} (${generated.reason}): writing raw Cricbuzz text — ${attempts.join("; ")}`);
        report("match-sync wrote raw Cricbuzz text — AI generation produced nothing usable", {
          matchId,
          ball,
          reason: generated.reason,
          attempts: attempts.join("; "),
        });
      }
    }
    if (!line) { newWatermark = itemTs; continue; }

    // Classified from `clean` — the RAW Cricbuzz text — NOT from `line`. By this point `line` is
    // broadcast prose in which a four reads "races away to the rope", so the event is no longer
    // recoverable from it; that is precisely why the commentary tab's Boundaries filter could only
    // ever match the passthrough rows. Always written, "NONE" included: an absent column means
    // "no verdict" to the device and sends it back to guessing from the prose.
    const event = classifyEvent(clean);
    // Same source and the same reason as `event`: the number lives only in Cricbuzz's outcome
    // clause and `line` is broadcast prose by now. Omitted rather than written as 0 when the clause
    // is not recognised — the device draws no pill for an absent value and a fabricated dot ball is
    // exactly the bug this column exists to end.
    // Same source and the same reason as `event`: both the number and the KIND of delivery live
    // only in Cricbuzz's outcome clause, and `line` is broadcast prose by now — a wide reads
    // "strays down leg", so the device's `text.contains("wide")` test was the same guess-from-prose
    // failure in a different field. Omitted rather than written as 0/"" when the clause is not
    // recognised: the device draws no pill for an absent value and a fabricated dot ball is exactly
    // the bug these columns exist to end.
    const { runs, kind: extra } = parseBallOutcome(clean);

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
          ai_generated: aiGenerated,
          created_at: new Date().toISOString(),
          event,
          ...(runs === null ? {} : { runs }),
          ...(extra === null ? {} : { extra }),
        },
        READ_ANY
      );
      newWatermark = itemTs;
      written++;
    } catch (e) {
      // 409 = row already exists (race / watermark behind): it is durably written, so advance.
      if (e?.code === 409) newWatermark = itemTs;
      else {
        error(`Commentary write failed (${matchId}/${ball}): ${e?.message}`);
        reportError(e, { matchId, ball, operation: "commentary_write" });
      }
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

/**
 * Report any provider that answered 429 to every request we made of it.
 *
 * `attempts` already carries each individual 429, but per line and only on a 503, so a provider
 * that is permanently dead reads exactly like one that is briefly busy. That is not hypothetical:
 * SambaNova's model id had its quota withdrawn and answered 429 to literally every call, at zero
 * concurrency, and the only thing that ever said so was a load test. A 100% rate-limited ratio over
 * a meaningful number of calls is the one signal that separates the two.
 *
 * Warning, not error: the chain is designed to survive a provider being gone, so this is a
 * degradation to investigate, not an outage. `deadProviders` self-throttles per container.
 */
function reportDeadProviders(error) {
  for (const d of deadProviders()) {
    error(`[PROVIDER-DEAD] ${d.provider} rate-limited on ${d.rateLimited}/${d.calls} calls — not load, check the model id and account`);
    report("A commentary provider is answering 429 to every request — likely dead, not busy", {
      provider: d.provider,
      calls: d.calls,
      rateLimited: d.rateLimited,
    });
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
