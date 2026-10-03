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
import { Client, Storage, Databases, Query } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { createHash } from "crypto";
import { parseJsonFile } from "./storageJson.js";
import {
  liveScoresUrl,
  scorecardUrl,
  fetchHtml,
  findCommentaryGaps,
  isTerminalStatus,
  parseBallOutcome,
  parseCommentary,
  parseLiveDetail,
  parseMatchMeta,
  metaPresence,
  META_FIELDS,
  parseScorecard,
  parseOverHistory,
  BLOCK_BACKOFF_MS,
} from "./cricbuzz.js";
import { fetchForecast, sideContextFor } from "./context.js";
import { sideContext } from "./contextmath.js";
import { createContextStore, newBudget } from "./contextstore.js";
import { parseSquads, squadsUrl } from "./squads.js";
import { BATCH_MAX_ITEMS, chainDepth, classifyEvent, deadProviders, drainUnknownQuotaIds, generateBatch, generateLine, nextPacificMidnight, outcomeClause, sanitize, stripPreamble, verifyBatch } from "./llm.js";
import {
  SITUATION_SYSTEM_INSTRUCTION,
  deliveryOf,
  detectTrigger,
  mustMentionOf,
  newSituationState,
  situationFacts,
  validSituationLine,
} from "./situation.js";
import {
  initObservability,
  report,
  reportError,
  reportThrottled,
  flushObservability,
  probeSources,
  startCheckIn,
  finishCheckIn,
} from "./observability.js";
import { deliveryAlert, milestoneAlerts, resultAlert } from "./alerts.js";
import { runPoll } from "./poll.js";
import { fcmConfigured, matchTopic, sendTopicMessage } from "./fcm.js";
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
// The pointer to the model the trainer last published, and how often it is looked at again. A
// container lives for hours and the trainer runs weekly, so without the re-check a new model
// reached a match only when its container happened to be recycled.
const MANIFEST_FILE = "winprob-manifest";
const MODEL_RECHECK_MS = 60 * 60 * 1000;
let cachedModel = null;        // warm-container cache
let cachedModelFile = null;    // the file id `cachedModel` was read from
let modelCheckedAt = 0;
// Viewer presence: bouncer-match-presence writes { [matchId]: epochMs } here whenever a
// client has a live match open. We only generate for matches with a fresh viewer, so idle
// matches (and idle nights) cost no LLM calls. TTL covers ~2 missed 90s client heartbeats.
const PRESENCE_FILE = "active-viewers";
const VIEWER_TTL_MS = 4 * 60 * 1000;
const PRESENCE_RETRY_DELAY_MS = 250;

// Per-match per-run bounds (control LLM cost + stay within function timeout).
const SCAN_NEWEST = 30; // how many newest commentary items to look at
const MAX_NEW_PER_MATCH = 10; // max LLM-REWRITTEN rows per match per run
// How many live matches get their commentary stored at all. Storing is cheap — a row per
// delivery, no LLM — but it is unbounded in the number of live matches, and Cricbuzz's live list
// runs to 30+ once domestic fixtures are counted. Viewed matches take the slots first, then the
// slow tier, then whatever is left; 16 covers every list observed (11 live at 2026-08-27T12:10Z).
const MAX_COMMENTARY_MATCHES = 16;
// Cap on live matches processed per run, filled VIEWERS FIRST (`liveMatchSlice`). 12 until
// 2026-09-25, when 13 live matches bound it on 2,219 runs in nine days (ANDROID-4P) — and because
// the slice ran in Cricbuzz's list order before the viewer set was built, a watched match at
// position 13 was invisible to the viewer gate. 16 is bounded by READ_BUDGET_PER_RUN (20): a cold
// container pays one watermark seed read per stored match, so the cap must stay under it.
const MAX_MATCHES = 16;
// How long a steady-state condition stays quiet in Sentry once reported. See reportThrottled.
const STEADY_STATE_REPORT_MS = 60 * 60 * 1000;
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
// The binding constraint on these two numbers is NOT the Appwrite read budget — it is the LLM
// chain's daily request budget, because every slow-tier row costs one call and the fast tier draws
// from the same pool. The 45 min / 1 row pair was sized against groq alone at 1000/day:
//
//   at 8 concurrent unwatched matches, one row a pass
//   15 min → 32 calls/hour → 768/day. One 2026-07-30-style hour (267 calls) on top = 1035. Over.
//   30 min → 16 calls/hour → 384/day. Leaves 616 — barely two such hours.
//   45 min → 11 calls/hour → 256/day. Leaves 744 — about three such hours. Chosen.
//
// Re-derived 2026-09-16, after three Gemini rungs were believed to take the ladder to ~2500
// calls/day. They did not: two of the three are 20 RPD, and the real supply is ~540 Gemini calls
// plus groq's daily token cap (measured 2026-09-24; see PROVIDERS in llm.js). What the old
// numbers actually bought, measured on the live table: 998 commentary rows stored in an hour and
// SIX of them rewritten — 0.6%. Eleven matches were live with no viewer, so `commentaryCapFor`
// returned 0 for all of them and the slow tier's single 45-minute pass was the entire AI output of
// the app. The tab was raw Cricbuzz text with a rounding error of commentary on top.
//
//   reserve 800/day for the fast tier — 07-30 is still not known to be a ceiling — leaving ~1700
//   at 11 concurrent unwatched matches (today's live count; p90 is 10), 15 min → 44 passes/hour
//   3 rows a pass → 132 calls/hour → 2112/day over ~16 hours with cricket on. Over.
//   2 rows a pass →  88 calls/hour → 1408/day. Chosen.
//
// That is ~6x the rewrite rate per match and still only ~9% of deliveries: 11 matches bowling for
// 16 hours is ~15,800 balls, so full coverage is not a budget away, it is an order of magnitude
// away. Do not read this pair as "enough" — read it as what fits.
//
// 2026-09-30: 15 → 60 minutes. Since rewriting became batched the tier no longer decides prose at
// all — only the DETAIL tier (scorecard fetch, detail row, scorecard row) for a match nobody has
// open — so what it buys is a row for a screen nobody is looking at. The one reader that did depend
// on it was the Live Update card of a followed-but-not-open match, which subscribed to the detail
// row without ever pinging presence and so updated every 15 minutes at best; `LiveUpdateService`
// now pings for every followed match, putting it on the per-minute fast tier. What is left for this
// tier is a safety net for older app builds and a not-too-old row for a first paint, where the
// match list already outranks a stale row (`listLeadsDetail`). ~1,050 fewer DB writes a day at 11
// unwatched matches.
const SLOW_TIER_INTERVAL_MS = 60 * 60 * 1000;
const SLOW_MAX_NEW_PER_MATCH = 2;
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
// ── Cross-match rewrite batching ─────────────────────────────────────────────
//
// Every delivery is now rewritten, which only fits the free ladder because the calls are batched:
// 6,672 deliveries a day is ~960 calls batched (measured 2026-09-16). The ~2,500-call ladder that
// was sized against was an assumption, not a measurement — see PROVIDERS in llm.js.
//
// It batches across MATCHES, not across time, and that distinction is the whole design. Filling a
// batch by waiting for an over would delay the ball by ~10 minutes against the 0-60s it takes to
// appear now. Breadth is free: `handleRun` already runs every match through `Promise.allSettled`,
// so their rewrite requests arrive within milliseconds of each other and a short debounce collects
// them with no added latency. Seven deliveries from seven unrelated matches measured 7/7 keys
// returned and zero players leaked between them.
//
// The debounce is the only thing that makes a batch form, so every queued item MUST eventually be
// resolved — a promise left hanging would stall `Promise.allSettled` until the 120s function
// timeout. `flushBatch` therefore resolves every item it takes, on every path including a throw.
const BATCH_DEBOUNCE_MS = 250;

let pendingRewrites = [];
let batchTimer = null;
// Indirection so the throw path below can be proved. `generateBatch` returns an empty Map for every
// provider failure rather than throwing, so no fetch stub can reach the catch — and an unreachable
// catch is exactly where a hang would hide if that ever changed.
let batchFn = generateBatch;
let verifyBatchFn = verifyBatch;

/** Queue one delivery for the next batch. Resolves to its rewritten line, or null. */
function queueRewrite(entry) {
  return new Promise((resolve) => {
    pendingRewrites.push({ ...entry, resolve });
    if (pendingRewrites.length >= BATCH_MAX_ITEMS) {
      flushBatch();
    } else if (!batchTimer) {
      batchTimer = setTimeout(flushBatch, BATCH_DEBOUNCE_MS);
    }
  });
}

function flushBatch() {
  if (batchTimer) { clearTimeout(batchTimer); batchTimer = null; }
  const taken = pendingRewrites.splice(0, BATCH_MAX_ITEMS);
  if (!taken.length) return;
  // Anything still queued forms the next batch immediately rather than waiting out another
  // debounce — it is already late by a full window.
  if (pendingRewrites.length) batchTimer = setTimeout(flushBatch, 0);

  // A batch always goes out full. Every Gemini rung is limited by REQUESTS a day, not tokens, and a
  // typical batch carried 6.95 of its 12 deliveries (measured 2026-09-16) — so the spare slots were
  // the cheapest capacity in the pipeline: a held delivery riding in one costs no call at all.
  // The size stays BATCH_MAX_ITEMS, the size the writer and the checker were probed at.
  const fill = takeHeld(BATCH_MAX_ITEMS - taken.length).map(heldItem);
  const all = [...taken, ...fill];
  if (fill.length) taken[0].log?.(`Rewrite batch: ${taken.length} fresh + ${fill.length} held`);

  rewriteWithRetry(all)
    .then((results) => {
      // Held first: their writes are collected for the run to await, and a fresh item's resolve is
      // what lets its match sync — and so the run — move on.
      for (const t of fill) t.resolve(results.get(t.key));
      for (const t of taken) t.resolve(results.get(t.key));
    })
    .catch((e) => {
      taken[0].error?.(`Rewrite batch threw: ${e?.message}`);
      for (const t of all) t.resolve({ line: null, provider: null, reason: "providers_exhausted", attempts: [] });
    });
}

/**
 * Rewrite and fact-check a batch, then give every delivery that came back rejected or missing ONE
 * more attempt before the run gives up on it.
 *
 * There was no second attempt until 2026-09-27, and that was most of the raw text left in the tab:
 * of 133 raw rows written over 09-26/27, ~90% were `verification_failed`, and the rejections were
 * right — groq had written "Mahipal Lomror sends a blistering drive… for a FOUR" when Lomror was
 * bowling. A rejected line cannot be stored, but a rejected DELIVERY can be written again.
 *
 * The retry goes to a different writer first (`exclude` the one that just failed), because the
 * rung that got it wrong once is the likeliest to get it wrong again. When no other writer answers
 * — the evening, when both flash-lite buckets are spent and groq writes alone — the same rung is
 * re-sampled once; the checker still gates whatever comes back. A batch nobody answered at all is
 * not retried: every rung already failed at the transport level, and asking again this second
 * repeats the same 429s. Those deliveries are held by the caller and retried next run.
 */
async function rewriteWithRetry(taken) {
  const first = await rewriteAndCheck(taken, []);
  const failed = taken.filter((t) => !first.results.get(t.key).line);
  if (!failed.length || !first.provider) return first.results;

  let retry = await rewriteAndCheck(failed, [first.provider]);
  if (!retry.provider) retry = await rewriteAndCheck(failed, []);
  for (const t of failed) {
    const again = retry.results.get(t.key);
    const before = first.results.get(t.key);
    first.results.set(t.key, again.line ? again : { ...again, attempts: [...before.attempts, ...again.attempts] });
  }
  return first.results;
}

const SCORER_NOTE_RE = / \[Scorer's note:[^\]]*\]$/;
const comparable = (text) =>
  String(text ?? "").replace(SCORER_NOTE_RE, "").replace(/^(?:\d{1,3}\.\d|Ball\s+\d{1,3}):?\s*/i, "").replace(/[^a-z0-9]+/gi, " ").trim().toLowerCase();

/** True when [line] is [source] again: same words once the ball label, note and punctuation go. */
export function isEcho(line, source) {
  const a = comparable(line);
  return a.length > 0 && a === comparable(source);
}

const BALL_LABEL_RE = /^(?:\d{1,3}\.\d|Ball\s+\d{1,3}):?\s*/i;
const SCORER_HEADER_RE = /^[^,]+ to [^,]+$/;
const SCORER_OUTCOME_RE = /^(?:no run|\d{1,3} runs?|four|six|out\b|that'?s out\b|wides?|no.?balls?|leg ?byes?|byes?)/i;
const SCORECARD_TAIL_RE = /\b\d{1,3}\(\d{1,3}\)|\[4s|!!/;

/**
 * True when [line] is still the scorer's text rather than a written line, whatever the source was.
 *
 * `isEcho` is exact equality and two shapes got past it, both stored as `ai_generated: true`
 * (measured 2026-10-03, 24 of 1,500 rows): the scorer's header kept with prose bolted on ("Sahil
 * Lotra to Rishabh Pant, no run: Lotra keeps probing…"), and a paraphrase that kept Cricbuzz's
 * scorecard tail ("…c John Campbell b Jayden Seales, 57(69) [4s-1 6s-4]"). The checker cannot fail
 * either — every fact in them is the source's.
 */
export function keepsScorerText(line) {
  const body = String(line ?? "").replace(BALL_LABEL_RE, "");
  if (SCORECARD_TAIL_RE.test(body)) return true;
  const head = body.split(",")[0];
  return SCORER_HEADER_RE.test(head) && SCORER_OUTCOME_RE.test(outcomeClause(body));
}

/** One writer call and one checker call. Returns a result for EVERY item, plus the writer rung. */
async function rewriteAndCheck(items, exclude) {
  const { log, error } = items[0];
  const attempts = [];
  // Mutated by generateLine with the rung that answered, so each row records its real provider
  // rather than a placeholder. Every delivery in a batch shares one rung by construction.
  const used = {};
  const lines = await batchFn(items.map((t) => ({ key: t.key, text: t.text })), { log, error, attempts, used, exclude });
  // A writer that hands the source back unchanged has not written a line, and the checker cannot
  // catch it — a copy of the source agrees with the source on every fact. That is how raw Cricbuzz
  // text was stored as `ai_generated: true, provider: groq` on 2026-10-03 (match 151554, 15.5),
  // after an hour of rejected rewrites. Dropped here, the delivery gets its retry on another rung
  // like any other the model skipped.
  for (const t of items) {
    if (lines.has(t.key) && (isEcho(lines.get(t.key), t.text) || keepsScorerText(lines.get(t.key)))) {
      error?.(`[ECHO] ${t.key}: ${used.provider ?? "unknown"} returned the source text unchanged`);
      attempts.push(`${used.provider ?? "unknown"}: echoed the source`);
      lines.delete(t.key);
    }
  }
  // EVERY rewritten line is fact-checked, each against its own raw Cricbuzz text, in one call
  // per batch. It used to be wickets and boundaries only, one call each — which left the
  // dot balls and singles, most of the tab, unchecked, and cost a call per ball it did check.
  // The checker gets the ball number the writer saw (it is in the writer's key). Without it,
  // "closes the over" on x.6 was unverifiable and failed — the first and last balls of overs
  // were most of the rejections on 2026-09-25.
  const toCheck = items.filter((t) => lines.get(t.key)).map((t) => ({
    key: t.key,
    source: t.ball ? `Ball ${t.ball}: ${t.text}` : t.text,
    line: lines.get(t.key),
  }));
  const checkAttempts = [];
  const { verdicts, available } = toCheck.length
    ? await verifyBatchFn(toCheck, { log, error, attempts: checkAttempts })
    : { verdicts: new Map(), available: false };
  const results = new Map();
  for (const t of items) {
    // Per item: one array per delivery, never shared, so a report names only its own ball.
    const own = [...attempts, ...checkAttempts];
    const line = lines.get(t.key) ?? null;
    if (!line) {
      results.set(t.key, { line: null, provider: null, reason: "providers_exhausted", attempts: own });
    } else if (!available || !verdicts.has(t.key)) {
      // Fail closed: a line nobody vouched for is never stored.
      results.set(t.key, { line: null, provider: null, reason: "verifier_unavailable", attempts: own });
    } else if (!verdicts.get(t.key)) {
      // The rejected line is not stored anywhere else, so this log is the only way to audit
      // whether the checker is catching errors or being over-strict.
      error?.(`[VERIFY-FAIL] ${t.key}: ${line.slice(0, 240)} || source: ${String(t.text).slice(0, 240)}`);
      results.set(t.key, { line: null, provider: null, reason: "verification_failed", attempts: [...own, `${used.provider ?? "unknown"}: verification FAIL`] });
    } else {
      results.set(t.key, { line, provider: used.provider ?? null, reason: null, attempts: own });
    }
  }
  return { results, provider: used.provider ?? null };
}

/** Test seam: swap the batch generator, e.g. for one that throws. Returns the previous one. */
export function __setBatchFn(fn) {
  const prev = batchFn;
  batchFn = fn ?? generateBatch;
  return prev;
}

/** Test seam: swap the batch fact-checker. Returns the previous one. */
export function __setVerifyBatchFn(fn) {
  const prev = verifyBatchFn;
  verifyBatchFn = fn ?? verifyBatch;
  return prev;
}

/** Exported for the coalescing tests; production calls it only from syncMatch. */
export const __queueRewrite = queueRewrite;

/** Test seam, and a safety net: nothing may survive a run boundary. */
export function resetRewriteBatcher() {
  if (batchTimer) { clearTimeout(batchTimer); batchTimer = null; }
  for (const t of pendingRewrites) t.resolve({ line: null, attempts: [] });
  pendingRewrites = [];
}

// ── Rewrite backlog ──────────────────────────────────────────────────────────────────────────────
//
// A delivery with no verified line is HELD, never written. Until 2026-09-27 it was stored as raw
// Cricbuzz text (`ai_generated: false`, `provider: cricbuzz:<reason>`), 2-3% of every day's rows.
// Now it waits here and is offered to the writer again on every run until a line passes the
// checker; the row then lands with its original `sort_key`, and the device inserts a row by
// `sortKey` position, so a late line sits where the ball was bowled.
//
// Held entries carry everything the row needs, because the live page is a ~20-item window: after a
// few minutes the ball is no longer on it, and the watermark has already moved past it.
//
// The cost is lateness, never a wrong line: when every writer is spent (groq's tokens-per-day latch
// before the Pacific reset), the tab shows no new lines for that stretch and then catches up in
// order. Alerts do not wait — `syncMatch` sends a held delivery's alert when it first sees it.
const BACKLOG_FILE = "rewrite-backlog";
// Report thresholds. A backlog that clears within a run or two is the retry working; one that is
// still there after 15 minutes means nothing is writing.
const BACKLOG_REPORT_AGE_MS = 15 * 60 * 1000;
const BACKLOG_REPORT_SIZE = 200;
// ~100 holds a day were measured when this was written. Past this something is broken, not slow.
const BACKLOG_ALARM_SIZE = 2000;
// Dedicated backlog batches per run: two. A backlog built while every writer was spent would
// otherwise go out as a dozen calls in one second the moment a rung reopens, straight into
// flash-lite's 15 RPM — and when the checker has fallen onto the same rung, each batch is two of
// those fifteen. Held deliveries also ride free in the spare slots of every fresh batch
// (`flushBatch`), which is the cheaper path and is not counted here.
const BACKLOG_DRAIN_PER_RUN = 2 * BATCH_MAX_ITEMS;
// A FINISHED match gets dedicated batches only while its backlog is this small — the ordinary
// trickle of twice-rejected lines, ~100 a day — or inside the sweep window below. Every call
// carries BATCH_MAX_ITEMS deliveries either way, so the only choice is who is served: a morning
// call spent on yesterday's match is a call the afternoon's live balls then do not have. A large
// finished-match backlog fills through the spare slots instead. Nothing is ever dropped.
const BACKLOG_FINISHED_TRICKLE = 2 * BATCH_MAX_ITEMS;
// Gemini's per-day buckets reset at midnight Pacific and do not carry over, so in the hours before
// it whatever is left is spent on finished matches rather than expiring.
const BACKLOG_SWEEP_MS = 3 * 60 * 60 * 1000;
// Fact-check FAILs after which a held delivery gets no more dedicated batches. Counted per offer,
// and an offer is a writer plus the retry, so three is six rejected lines from at least two rungs.
const HELD_STUCK_REJECTIONS = 3;

// rowId -> held delivery. Null until this container has read the file; the file is written only by
// this function, so a warm container already holds what it says.
let backlog = null;
// What a top-up needs and a batch does not carry: somewhere to write, and who is watching what.
// Set by `handleRun` for the length of one run; null means no top-ups (the unit tests of the
// batcher, and any caller that has not loaded the backlog).
let backlogCtx = null;
// Row ids already in a batch this run, so one held delivery is never rewritten twice in a run.
const inFlight = new Set();
// Row writes started by top-ups. The run awaits them before it saves the backlog and returns —
// the container freezes on return, and a write still in the air would be lost.
let heldWork = [];
// Whether `backlog` reflects the file. False after an unreadable (non-404) read: the file must not
// be overwritten with only this run's holds, or every older one would be lost.
let backlogLoaded = false;
let backlogSaved = null;

function heldMap() {
  if (!backlog) backlog = new Map();
  return backlog;
}

/** Take ownership of a delivery that has no verified line yet. */
export function holdDelivery(entry) {
  heldMap().set(entry.rowId, entry);
}

/** Whether a delivery is already owned by the backlog, so a match sync must not queue it again. */
export function isHeld(rowId) {
  return heldMap().has(rowId);
}

/** Test seam. */
export function __resetBacklog(entries = []) {
  backlog = new Map(entries.map((e) => [e.rowId, e]));
  backlogLoaded = true;
  backlogSaved = null;
  backlogCtx = null;
  inFlight.clear();
  heldWork = [];
}

/** Test seam: what `handleRun` sets for the length of a run. */
export function __setBacklogContext(ctx) {
  backlogCtx = ctx;
}

/** Test seam: wait for the row writes top-ups started. */
export async function __settleHeldWork() {
  await settleHeldWork();
}

async function settleHeldWork() {
  // A write can only start once its batch resolves, so loop until nothing new was added.
  while (heldWork.length) {
    const work = heldWork;
    heldWork = [];
    await Promise.allSettled(work);
  }
  inFlight.clear();
}

/**
 * The order held deliveries are served in: matches someone has open, then the other live matches,
 * then finished ones — and newest ball first inside each.
 *
 * It was oldest-first across everything, which after a dry evening spent the morning's fresh quota
 * on yesterday's finished match while the match being watched waited behind it. The tab is read
 * from the newest ball down and the device loads only the newest page first, so the newest held
 * ball is the one a reader is looking at; a late row still lands where the ball was bowled, because
 * the device inserts by `sort_key`. The cost is holes further down the feed until they fill, and
 * no figure is stated over a hole — over totals and maidens need all six balls present.
 */
export function backlogOrder(entries, { viewers = new Set(), liveIds = new Set() } = {}) {
  const tier = (e) => (viewers.has(String(e.matchId)) ? 0 : liveIds.has(String(e.matchId)) ? 1 : 2);
  return [...entries].sort((a, b) => tier(a) - tier(b) || b.ts - a.ts);
}

/** Held deliveries no batch has taken yet this run, in serving order. */
function waitingHeld(order = backlogCtx ?? {}) {
  // A delivery held during THIS run has just failed every attempt it gets in a run; offering it
  // again a second later repeats the same answer.
  const startedMs = backlogCtx?.startedMs ?? Infinity;
  return backlogOrder(
    [...heldMap().values()].filter((e) => !inFlight.has(e.rowId) && (e.firstSeenMs ?? 0) < startedMs),
    order
  );
}

/** Up to [n] held deliveries for the spare slots of a fresh batch. None outside a run. */
function takeHeld(n) {
  if (!backlogCtx || n <= 0) return [];
  const taken = waitingHeld().slice(0, n);
  for (const e of taken) inFlight.add(e.rowId);
  return taken;
}

/** A held delivery as a batch item. Its result is written, not handed back to a match sync. */
function heldItem(e) {
  const { db, log, error } = backlogCtx;
  return {
    key: batchKey(e.matchId, e.ball, e.ts),
    ball: e.ball,
    text: e.clean + (e.note ?? ""),
    log,
    error,
    resolve: (out) => { heldWork.push(writeHeld(e, out, { db, error })); },
  };
}

/**
 * Write a held delivery's row if [out] carries a verified line. Returns true when the delivery has
 * left the backlog. Never throws: a failed write leaves the delivery held for the next run.
 */
async function writeHeld(e, out, { db, error }) {
  const line = out?.line ? storedLine(out.line, e.ball) : null;
  if (!line) {
    if (out?.reason === "verification_failed") noteRejection(e, out);
    return false;
  }
  try {
    const data = commentaryRowData({
      matchId: e.matchId, innings: e.innings, ball: e.ball, sortKey: e.sortKey, line, clean: e.clean, provider: out.provider,
    });
    await db.createDocument(DB_ID, COMMENTARY_COL, e.rowId, data, READ_ANY);
    heldMap().delete(e.rowId);
    await supersedeOlder({ db, error, matchId: e.matchId, rowId: e.rowId, ts: e.ts, data });
    return true;
  } catch (err) {
    // 409: the row exists — another execution wrote it. Either way the delivery is stored.
    if (err?.code === 409) {
      heldMap().delete(e.rowId);
      return true;
    }
    error?.(`Backlog write failed (${e.matchId}/${e.ball}): ${err?.message}`);
    return false;
  }
}

/**
 * One more fact-check FAIL for a held delivery, and — once — the report that it is stuck.
 *
 * A ball the checker rejects every time is not a quota problem and waiting does not fix it: either
 * its source contradicts itself (see `sourceConflictNote`) or the checker is wrong about it. Until
 * 2026-10-03 nothing counted, so such a ball took two writer calls and two checks on every run for
 * as long as it was held, and the only report was the generic "commentary is being held" warning —
 * one event an hour in an issue that already existed, saying nothing about which ball or why.
 */
function noteRejection(e, out) {
  e.rejections = (e.rejections ?? 0) + 1;
  if (e.rejections !== HELD_STUCK_REJECTIONS) return;
  report("A held delivery keeps failing the fact-check — it now waits for a spare batch slot", {
    matchId: e.matchId,
    ball: e.ball,
    rejections: e.rejections,
    source: String(e.clean).slice(0, 300),
    note: e.note ?? "",
    heldMinutes: Math.round((Date.now() - (e.firstSeenMs ?? Date.now())) / 60000),
    attempts: (out?.attempts ?? []).join("; "),
  }, "error");
}

/** The held deliveries this run's dedicated batches may take — see BACKLOG_FINISHED_TRICKLE. */
export function dedicatedEntries({ viewers = new Set(), liveIds = new Set(), now = Date.now() } = {}) {
  const waiting = waitingHeld({ viewers, liveIds });
  const isActive = (e) => viewers.has(String(e.matchId)) || liveIds.has(String(e.matchId));
  const finished = waiting.filter((e) => !isActive(e)).length;
  const sweep = nextPacificMidnight(now) - now <= BACKLOG_SWEEP_MS;
  const finishedToo = sweep || finished <= BACKLOG_FINISHED_TRICKLE;
  // A stuck delivery is never dropped, but it stops costing calls of its own: it rides in the spare
  // slots of fresh batches, which are free, and in the sweep.
  const stuck = (e) => (e.rejections ?? 0) >= HELD_STUCK_REJECTIONS;
  return waiting
    .filter((e) => (finishedToo || isActive(e)) && (sweep || !stuck(e)))
    .slice(0, BACKLOG_DRAIN_PER_RUN);
}

export function __backlogEntries() {
  return [...heldMap().values()];
}

async function loadBacklog({ storage, error }) {
  if (backlogLoaded) return;
  try {
    const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, BACKLOG_FILE));
    // Memory wins over the file: an entry held since the last unreadable read is the newer word.
    const held = heldMap();
    for (const e of Array.isArray(parsed) ? parsed : []) if (e?.rowId && !held.has(e.rowId)) held.set(e.rowId, e);
    backlogSaved = JSON.stringify(Array.isArray(parsed) ? parsed : []);
    backlogLoaded = true;
  } catch (e) {
    if (e?.code === 404) {
      heldMap();
      backlogSaved = "[]";
      backlogLoaded = true;
    } else {
      error(`Could not read ${BACKLOG_FILE} (${e?.message}) — holding in memory until it can be read`);
    }
  }
}

/** Write the backlog only when it changed, and never over a file this container could not read. */
async function saveBacklog({ storage, log, error }) {
  if (!backlogLoaded) return;
  const payload = JSON.stringify([...heldMap().values()]);
  if (payload === backlogSaved) return;
  try {
    await storage.deleteFile(BUCKET_ID, BACKLOG_FILE).catch(() => {});
    await storage.createFile(
      BUCKET_ID,
      BACKLOG_FILE,
      InputFile.fromBuffer(Buffer.from(payload, "utf8"), "rewrite-backlog.json"),
      READ_ANY
    );
    backlogSaved = payload;
    log(`Wrote ${BACKLOG_FILE} (${heldMap().size} held)`);
  } catch (e) {
    // The entries are still in memory and the next run retries the write.
    error(`Failed to write ${BACKLOG_FILE}: ${e?.message}`);
  }
}

/**
 * The text a verified line is stored as, or null when nothing usable is left after cleaning.
 * Prefixed with the ball when the model left it off, so every row reads the same.
 */
function storedLine(raw, ball) {
  let line = sanitize(stripPreamble(raw ?? ""));
  if (line && !line.startsWith(ball)) line = `${ball}: ${line}`;
  return line || null;
}

/** The `match-commentary` row for one delivery. One builder, so a late row and an on-time row match. */
function commentaryRowData({ matchId, innings, ball, sortKey, line, clean, provider }) {
  // Classified from `clean` — the RAW Cricbuzz text — NOT from `line`. By this point `line` is
  // broadcast prose in which a four reads "races away to the rope", so the event is no longer
  // recoverable from it; that is precisely why the commentary tab's Boundaries filter could only
  // ever match the passthrough rows. Always written, "NONE" included: an absent column means
  // "no verdict" to the device and sends it back to guessing from the prose.
  const event = classifyEvent(clean);
  // Same source and the same reason as `event`: both the number and the KIND of delivery live
  // only in Cricbuzz's outcome clause, and `line` is broadcast prose by now — a wide reads
  // "strays down leg", so the device's `text.contains("wide")` test was the same guess-from-prose
  // failure in a different field. Omitted rather than written as 0/"" when the clause is not
  // recognised: the device draws no pill for an absent value and a fabricated dot ball is exactly
  // the bug these columns exist to end.
  const { runs, kind: extra } = parseBallOutcome(clean);
  const origin = commentaryOrigin({ line, provider });
  return {
    match_id: matchId,
    innings,
    ball,
    sort_key: sortKey,
    commentary: line,
    ai_generated: origin.aiGenerated,
    provider: origin.provider,
    created_at: new Date().toISOString(),
    event,
    ...(runs === null ? {} : { runs }),
    ...(extra === null ? {} : { extra }),
  };
}

// ── A corrected delivery replaces the one Cricbuzz withdrew ──────────────────
//
// Cricbuzz republishes a corrected delivery under a NEW timestamp, and the row id is the
// timestamp's hash, so the correction became a second row and the withdrawn one stayed. Measured
// 2026-10-03: match 171061's 10.3 was stored as a single, then as a catch that never happened, then
// as the single again — three rows, and a wicket in the Wickets filter for a batter who was not
// out; match 174881 kept "bowls Norah Salima" beside the catch that actually dismissed Mia Hedge.
//
// matchId -> "innings|ball" -> the newest LEGAL row this container wrote. A wide or a no-ball
// shares its label with the ball that follows and is never tracked. In memory only: a cold
// container forgets, and the device then shows the newest row per delivery by itself.
const legalRowByBall = new Map();
const REBOWLED = new Set(["WIDE", "NOBALL"]);

/** Test seam: forget every tracked delivery. */
export function __resetSuperseded() {
  legalRowByBall.clear();
}

/**
 * Record a legal delivery's row, deleting the older row it corrects. Returns the superseded row's
 * event, or null when this is the first version seen. Never throws.
 */
async function supersedeOlder({ db, error, matchId, rowId, ts, data }) {
  if (REBOWLED.has(data.extra)) return null;
  const key = `${data.innings}|${data.ball}`;
  const byBall = legalRowByBall.get(matchId) ?? new Map();
  legalRowByBall.set(matchId, byBall);
  const prior = byBall.get(key);
  if (prior && prior.ts >= ts) return null;
  byBall.set(key, { rowId, ts, event: data.event });
  // A withdrawn version still waiting for a line is the same delivery, not another one to keep.
  for (const h of [...heldMap().values()]) {
    const sameBall = h.matchId === matchId && h.innings === data.innings && h.ball === data.ball && h.ts < ts;
    if (sameBall && !REBOWLED.has(parseBallOutcome(h.clean).kind)) heldMap().delete(h.rowId);
  }
  if (!prior || prior.rowId === rowId) return null;
  try {
    await db.deleteDocument(DB_ID, COMMENTARY_COL, prior.rowId);
  } catch (e) {
    if (e?.code !== 404) error?.(`Superseded row not deleted (${matchId}/${data.ball}): ${e?.message}`);
  }
  return prior.event;
}

// ── Match-situation lines (see situation.js) ─────────────────────────────────
//
// matchId -> trigger state. In memory only: a cold container starts with no history, which can
// miss a streak that straddles the boot and cannot invent one.
const situationByMatch = new Map();
export const SITUATION_PROVIDER_PREFIX = "situation:";

let situationWriter = async (facts, { log, error }) => {
  const used = {};
  const raw = await generateLine(`${facts}\n\nWrite the sentence.`, {
    maxTokens: 80, log, error, used, systemInstruction: SITUATION_SYSTEM_INSTRUCTION,
  });
  return raw ? { line: sanitize(stripPreamble(raw)), provider: used.provider ?? "unknown" } : null;
};

/** Test seam: replace the writer; returns the previous one. Also forgets every match's state. */
export function __setSituationWriter(fn) {
  const previous = situationWriter;
  situationWriter = fn;
  situationByMatch.clear();
  return previous;
}

/**
 * Write a pending situation line once the over it belongs to is finished, so it sits between two
 * overs. A line stored mid-over would split that over into two blocks on the device.
 */
async function flushSituation({ db, matchId, state, deliveries, terminal, error }) {
  const p = state.pending;
  if (!p) return false;
  const next = deliveries.find((d) => d.ts > p.ts && (d.over !== p.over || d.innings !== p.innings));
  const lastTs = deliveries.at(-1)?.ts ?? p.ts;
  if (!next && !terminal) return false;
  const sortKey = next ? next.ts - 1 : Math.max(lastTs, p.ts) + 1;
  state.pending = null;
  try {
    await db.createDocument(DB_ID, COMMENTARY_COL, docId(`${matchId}:situation:${p.type}:${p.innings}:${p.ts}`), {
      match_id: matchId,
      innings: p.innings,
      ball: "",
      sort_key: sortKey,
      commentary: p.line,
      ai_generated: true,
      provider: `${SITUATION_PROVIDER_PREFIX}${p.provider}`.slice(0, 48),
      created_at: new Date().toISOString(),
      event: "NONE",
    }, READ_ANY);
    return true;
  } catch (e) {
    if (e?.code !== 409) error(`Situation line write failed (${matchId}): ${e?.message}`);
    return false;
  }
}

/** Detect a trigger in this run's deliveries, write its line, and store any line whose over is done. */
async function situationStep({ db, matchId, detail, winProb, deliveries, terminal, log, error }) {
  const state = situationByMatch.get(matchId) ?? newSituationState();
  situationByMatch.set(matchId, state);
  await flushSituation({ db, matchId, state, deliveries, terminal, error });
  const isTest = winProb?.drawProbability != null;
  const trigger = detectTrigger(state, deliveries, {
    prob: winProb && !isTest ? winProb.team1WinProbability : null,
    team1: detail.team1Name,
    team2: detail.team2Name,
  });
  if (!trigger || state.pending) return;
  const facts = situationFacts(trigger, detail);
  const written = await situationWriter(facts, { log, error }).catch((e) => {
    error(`Situation writer threw (${matchId}): ${e?.message}`);
    return null;
  });
  const line = written ? validSituationLine(written.line, facts, mustMentionOf(trigger)) : null;
  if (!line) {
    if (written) error(`[SITUATION-REJECT] ${matchId} ${trigger.type}: ${String(written.line).slice(0, 200)} || facts: ${facts.replace(/\n/g, " ")}`);
    return;
  }
  state.ballsSinceLine = 0;
  state.pending = { line, provider: written.provider, type: trigger.type, innings: trigger.innings, over: trigger.over, ts: trigger.ts };
  log(`Situation line queued for ${matchId} (${trigger.type})`);
  await flushSituation({ db, matchId, state, deliveries, terminal, error });
}

/**
 * Offer every held delivery to the writer again, and write the ones that come back verified.
 *
 * Runs at the top of every run, whether or not anything is live: a match that ends with deliveries
 * still held has no more match syncs to carry them. Its calls go through the same batcher, so a
 * backlog costs a batch of its own only while it is non-empty.
 */
export async function drainBacklog({ db, log, error, viewers, liveIds, now }) {
  let written = 0;
  // A held delivery whose source is the scorer's header alone needs no writer (see `plainLine`).
  // These were held before that rule existed; bounded so one run cannot spend its time on them.
  const plain = [...heldMap().values()]
    .filter((e) => !inFlight.has(e.rowId))
    .map((e) => ({ e, line: plainLine(e.clean, e.ts) }))
    .filter((p) => p.line)
    .slice(0, PLAIN_BACKLOG_PER_RUN);
  for (const { e, line } of plain) {
    if (await writeHeld(e, { line, provider: TEMPLATE_PROVIDER }, { db, error })) written++;
  }
  const entries = dedicatedEntries({ viewers, liveIds, now });
  for (const e of entries) inFlight.add(e.rowId);
  const reasons = new Set();
  let lastAttempts = [];
  // Its own batches, not the shared queue: these are already full or are all there is, and a held
  // delivery queued as if it were fresh would be topped up with other held deliveries.
  const chunks = [];
  for (let i = 0; i < entries.length; i += BATCH_MAX_ITEMS) chunks.push(entries.slice(i, i + BATCH_MAX_ITEMS));
  await Promise.all(chunks.map(async (chunk) => {
    const items = chunk.map((e) => ({ key: batchKey(e.matchId, e.ball, e.ts), ball: e.ball, text: e.clean + (e.note ?? ""), log, error }));
    const results = await rewriteWithRetry(items).catch((e) => {
      error(`Backlog batch threw: ${e?.message}`);
      return new Map();
    });
    for (let i = 0; i < chunk.length; i++) {
      const out = results.get(items[i].key);
      if (await writeHeld(chunk[i], out, { db, error })) {
        written++;
      } else if (!out?.line) {
        reasons.add(out?.reason ?? "unknown");
        lastAttempts = out?.attempts ?? lastAttempts;
      }
    }
  }));
  const held = heldMap().size;
  log(`Backlog: wrote ${written} late line(s), ${held} still held`);
  reportBacklog({ reasons, attempts: lastAttempts });
  return { written, held };
}

function reportBacklog({ reasons, attempts }) {
  const held = [...heldMap().values()];
  if (!held.length) return;
  const oldestMs = Math.min(...held.map((e) => e.firstSeenMs ?? Date.now()));
  const ageMs = Date.now() - oldestMs;
  if (ageMs < BACKLOG_REPORT_AGE_MS && held.length < BACKLOG_REPORT_SIZE) return;
  reportThrottled("rewrite-backlog", STEADY_STATE_REPORT_MS, "Commentary is being held — no writer has produced a verified line for these deliveries", {
    held: held.length,
    oldestMinutes: Math.round(ageMs / 60000),
    matches: [...new Set(held.map((e) => e.matchId))].join(","),
    reasons: [...reasons].sort().join(","),
    attempts: attempts.join("; "),
  }, held.length >= BACKLOG_ALARM_SIZE ? "error" : "warning");
}

// Dedupe watermark: matchId -> highest commentary sort_key already written. Survives warm 1-min
// cron invocations (module scope), so the seed read is paid only on a cold container / a match's
// first appearance. Pruned each run to the live set so ended matches don't retain memory.
//
// A high-water mark replaces the old id-set because the two carry the same information for this
// purpose — commentary only ever arrives newest-after-oldest — while costing 1 read instead of 30.
// Correctness never depended on either: writes use a deterministic rowId plus a 409 backstop.
const watermarkByMatch = new Map();
// name → runs for each live match's current batters, the last time this container saw them. Kept
// in memory only: a cold container seeds rather than alerting, which can miss a milestone crossed
// in the one minute it took to boot and cannot invent one.
const batterRunsByMatch = new Map();

// Last body written per row, per warm container, so an unchanged snapshot is not rewritten. Between
// overs, at drinks, at lunch and in a rain delay the live page does not move, yet every viewed
// match paid a detail write and a scorecard write (4-9 KB, plus the full document echoed back)
// every minute regardless. A cold container writes once, which is the safe direction. Pruned with
// the watermark.
const lastDetailBody = new Map();
const lastScorecardBody = new Map();
// When each match's scorecard row was last written, and what it looked like in outline.
const lastScorecardWrite = new Map();

// ── Over history (the worm and the manhattan) ─────────────────────────────────────────────────
//
// Every over of every innings, accumulated from the ~2 overs the live page shows each run. The
// live page is fetched for every live match every minute, so a match's history builds whether or
// not anyone is watching; it is published inside the scorecard row (`scorecard.overs`), which is
// written only for the detail tier and at finalisation. A cold container starts empty and seeds
// from that row the first time it would write it — one billed read per match per cold container,
// counted against READ_BUDGET_PER_RUN and skipped (retried next run) if the budget is spent. Overs
// bowled while no container held the history are simply absent; the device plots the points it
// has and never interpolates a figure it was not given.
const overHistoryByMatch = new Map();
const overHistorySeeded = new Set();

// How often each `parseMatchMeta` field is actually on the live page, per run and per match state.
// A model feature built on one of these fields is trained with that field masked at the rate
// measured here, so the figure has to come from the pages this container fetches, not from a
// captured fixture.
let metaTally = {};

/** The state a presence count is filed under: a field's absence means different things in each. */
export function metaStateOf(status) {
  const text = String(status ?? "").toLowerCase();
  if (/rain|wet outfield|bad light|delay|stopped|abandon/.test(text)) return "interrupted";
  if (/innings break|\b(?:lunch|tea|dinner|stumps|drinks)\b|strategic/.test(text)) return "break";
  return "play";
}

export function tallyMeta(tally, meta, status) {
  const state = metaStateOf(status);
  const row = (tally[state] ??= { matches: 0 });
  row.matches += 1;
  const present = metaPresence(meta);
  for (const field of META_FIELDS) row[field] = (row[field] ?? 0) + (present[field] ? 1 : 0);
  return tally;
}

export function metaTallyLines(tally) {
  return Object.entries(tally).map(
    ([state, row]) =>
      `[META] ${state} n=${row.matches}: ` + META_FIELDS.map((f) => `${f} ${row[f] ?? 0}`).join(", ")
  );
}

/** [stored] ∪ [fresh] by (innings, over), the fresh reading winning, sorted. Malformed entries drop. */
export function mergeOverHistory(stored, fresh) {
  const byKey = new Map();
  for (const e of [...(Array.isArray(stored) ? stored : []), ...(fresh ?? [])]) {
    if (!e || typeof e !== "object" || !Number.isInteger(e.innings) || !Number.isInteger(e.over)) continue;
    byKey.set(`${e.innings}:${e.over}`, e);
  }
  return [...byKey.values()].sort((a, b) => a.innings - b.innings || a.over - b.over);
}

/** Seeds a cold container's history for [matchId] from its scorecard row, once. Never throws. */
async function seedOverHistory({ db, matchId, error }) {
  if (overHistorySeeded.has(matchId)) return;
  if (readsThisRun + 1 > READ_BUDGET_PER_RUN) return; // next run
  readsThisRun++;
  try {
    const row = await db.getDocument(DB_ID, SCORECARD_COL, sanitizeMatchId(matchId));
    const stored = JSON.parse(row?.scorecard ?? "{}")?.overs;
    overHistoryByMatch.set(matchId, mergeOverHistory(stored, overHistoryByMatch.get(matchId)));
    overHistorySeeded.add(matchId);
  } catch (e) {
    if (e?.code === 404) overHistorySeeded.add(matchId); // no row yet: nothing to seed from
    else if (isBudget402(e)) budget402UntilMs = Date.now() + READ_402_BACKOFF_MS;
    else error(`Over history seed failed (${matchId}): ${e?.message}`);
  }
}
let lastWinProbBody = null;

// The scorecard changes with every ball (batter and bowler figures), so the unchanged-body skip
// above never fired during play: a viewed match cost one scorecard write a minute. The live card —
// score, batters, bowler, recent balls — travels in the detail row every minute regardless, so the
// scorecard now moves on a WICKET or a new INNINGS at once, and otherwise at most this often.
export const SCORECARD_MIN_INTERVAL_MS = 3 * 60 * 1000;

/** Innings count and wickets fallen — the two changes a scorecard reader must see at once. */
export function scorecardOutline(scorecard) {
  const innings = scorecard?.innings ?? [];
  const wickets = innings.reduce((n, i) => n + (i?.fallOfWickets?.length ?? 0), 0);
  return `${innings.length}:${wickets}`;
}

/**
 * Whether the scorecard row is due a write. [force] is the finalising run: the last write of a
 * match's life must never be throttled away, because nothing writes the row again.
 */
export function scorecardWriteDue({ previous, body, outline, now, force = false }) {
  if (!previous) return true;
  if (previous.body === body) return false;
  if (force || previous.outline !== outline) return true;
  return now - previous.at >= SCORECARD_MIN_INTERVAL_MS;
}

// `upcoming-matches` feeds pre-match priors only, and a fixture list does not change minute to
// minute; one download per 10 minutes instead of one per run.
const UPCOMING_TTL_MS = 10 * 60 * 1000;
let upcomingCache = null; // { fixtures, fetchedAt }

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

/**
 * The suffix that marks a match whose final rows have been written.
 *
 * It rides in the same flat `{ key: epochMs }` map as the slow-tier clock rather than a nested
 * object, so `selectSlowTier` — which only ever reads `state[<bare id>]` — cannot see it and needs
 * no change. The prune below compares the id BEFORE the suffix, so the marker lives exactly as long
 * as the match stays in `live-matches.json`: long enough to stop the finalisation repeating on
 * every run and on every cold container, and gone once the match leaves and can never sync again.
 */
const FINAL_SUFFIX = ":final";

export const finalKey = (matchId) => `${matchId}${FINAL_SUFFIX}`;

/** True when this run already wrote [matchId]'s final rows, per the persisted state. */
export function isFinalised(state, matchId) {
  return typeof state?.[finalKey(matchId)] === "number";
}

/** The slow-tier clock, stamped for the matches that just ran and pruned to those still live. */
export function nextSlowState({ state, processed, liveIds, now, finalised = [] }) {
  const next = {};
  for (const [key, ts] of Object.entries(state ?? {})) {
    const id = key.endsWith(FINAL_SUFFIX) ? key.slice(0, -FINAL_SUFFIX.length) : key;
    if (liveIds.has(id) && typeof ts === "number") next[key] = ts;
  }
  for (const id of processed) next[id] = now;
  for (const id of finalised) next[finalKey(id)] = now;
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

// ── Plain lines for deliveries the source does not describe ──────────────────
//
// Much of associate and women's cricket reaches Cricbuzz as the scorer's header alone — "Chinelle
// Henry to Mupachikwa, no run". A writer asked for a broadcast line from that has nothing to write
// about, and wrote "beating Mupachikwa once more as the ball whisks past the outside edge" and
// "digs it in short … pull it down to deep square" (measured 2026-10-03 against the live pages:
// every stored line of matches 173107 and 174881 carried a length, a shot or a field position the
// source never stated). The checker passes all of it by design, as colour. So these deliveries are
// not sent to a model at all: the line says who bowled, who faced and what it was worth.
//
// A dismissal is not templated even when bare — its source carries the method, the fielder and the
// score, which is something to write from.
export const TEMPLATE_PROVIDER = "template";
const PLAIN_BACKLOG_PER_RUN = 50;
const BARE_SEGMENT_RE = /^(?:no run|\d{1,3} runs?|four|six|(?:\d{1,3}\s*)?(?:wd|wides?|nb|no.?balls?|lb|leg ?byes?|byes?|b))$/i;
const SCORER_PARTS_RE = /^(.+?) to ([^,]+),\s*(.+)$/;

const PLAIN = {
  dot: ["{batter} does not score off {bowler}.", "Dot ball from {bowler} to {batter}.", "No run for {batter} off {bowler}."],
  single: ["{batter} takes a single off {bowler}.", "A single to {batter} off {bowler}."],
  runs: ["{batter} takes {n} off {bowler}.", "{n} runs to {batter} off {bowler}."],
  four: ["Four to {batter} off {bowler}.", "{batter} finds the boundary off {bowler} for four."],
  six: ["Six to {batter} off {bowler}.", "{batter} clears the boundary off {bowler} for six."],
  WIDE: ["{bowler} bowls a wide to {batter}.", "Wide from {bowler} to {batter}."],
  NOBALL: ["No-ball from {bowler} to {batter}."],
  LEGBYE: ["Leg byes off {bowler}'s delivery to {batter}."],
  BYE: ["Byes off {bowler}'s delivery to {batter}."],
  legbye1: ["A leg bye off {bowler}'s delivery to {batter}."],
  bye1: ["A bye off {bowler}'s delivery to {batter}."],
};
const singleBye = (kind, runs) => (runs !== 1 ? null : kind === "LEGBYE" ? "legbye1" : kind === "BYE" ? "bye1" : null);

/**
 * The plain line for a delivery whose source is the scorer's header and nothing else, or null when
 * the source describes the ball (or is a dismissal) and a writer has something to work from.
 * `seed` picks the wording, so a re-run of the same delivery writes the same line.
 */
export function plainLine(clean, seed = 0) {
  const parts = String(clean ?? "").match(SCORER_PARTS_RE);
  if (!parts) return null;
  const segments = parts[3].split(",").map((s) => s.trim().replace(/[.!]+$/, "")).filter(Boolean);
  if (segments.length === 0 || segments.length > 2 || !segments.every((s) => BARE_SEGMENT_RE.test(s))) return null;
  const { runs, kind } = parseBallOutcome(clean);
  if (runs === null) return null;
  const form = singleBye(kind, runs) ?? kind ?? (runs === 0 ? "dot" : runs === 1 ? "single" : runs === 4 ? "four" : runs === 6 ? "six" : "runs");
  const choices = PLAIN[form];
  let line = choices[Math.abs(Math.trunc(seed)) % choices.length]
    .replace("{bowler}", parts[1].trim())
    .replace("{batter}", parts[2].trim())
    .replace("{n}", String(runs));
  // An extra states its total only when it is more than the bare extra is worth.
  if (kind && runs > 1) {
    const total = runs === 1 ? "1 run" : `${runs} runs`;
    line = `${line.slice(0, -1)}, ${total} in all.`;
  }
  return line;
}

const surnameOf = (name) => String(name ?? "").trim().split(/\s+/).pop() ?? "";
const mentions = (text, name) => {
  const escape = (v) => v.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const full = String(name ?? "").trim();
  const last = surnameOf(full);
  if (!last) return false;
  return new RegExp(`\\b(?:${escape(full)}|${escape(last)})\\b`, "i").test(text);
};

/**
 * A note for the writer and the checker when a Cricbuzz line contradicts itself about who faced
 * the ball, or "" when it does not.
 *
 * The header ("Motie to Gaikwad, 1 run, …") comes from the scorer; the description after it is
 * typed by a commentator, and on 2026-10-03 match 151554's 15.5 read "Gudakesh Motie to Ruturaj
 * Gaikwad, 1 run, tad short outside off, Rohit Sharma carves it through the off-side" — Sharma was
 * the non-striker. No line can be right about that source: name Sharma and it contradicts the
 * header, name Gaikwad and it contradicts the description. The checker failed every attempt, two
 * writers a run, for an hour (~240 calls on one ball), and what finally passed was a writer echoing
 * the raw text back verbatim.
 *
 * Narrow on purpose. It fires only when the description names the OTHER current batter and never
 * the one in the header, and only while the header's batter is still at the crease — the pair is
 * the live miniscore's, which says nothing about a ball from an earlier partnership. A fielder is
 * never in [batsmen], so "Sharma dives at point" in the other innings cannot trip it. A line that
 * names both ("Sharma sends Gaikwad back") is running between the wickets, not a slip.
 *
 * The note tells the writer to leave the batter unnamed — what the two halves agree on is the
 * bowler, the runs and the shot. Probed 3×: the writer obeyed every time, the checker passed it
 * given the same note (and failed it 3/3 without), and still failed a planted line naming Sharma
 * and a planted four.
 *
 * @param batsmen the live pair, `[{ name }]`
 */
export function sourceConflictNote(clean, batsmen) {
  const m = String(clean ?? "").match(/^(.+?) to (.+?),\s*(.*)$/s);
  if (!m) return "";
  const [, , batter, rest] = m;
  const pair = (batsmen ?? []).map((b) => String(b?.name ?? "").trim()).filter(Boolean);
  const facing = pair.find((n) => n.toLowerCase() === batter.trim().toLowerCase());
  if (!facing) return "";
  const partner = pair.find((n) => n !== facing && surnameOf(n).toLowerCase() !== surnameOf(facing).toLowerCase());
  if (!partner) return "";
  if (!mentions(rest, partner) || mentions(rest, facing)) return "";
  return ` [Scorer's note: this ball was faced by ${facing}, but the description names ${partner}. The two disagree, so do not name who played the shot.]`;
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

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  const storage = new Storage(client);

  // 0. The match lists. Polled here, in this run, rather than by a sibling function a few seconds
  // earlier: the live list then arrives in memory instead of as a download of the file that sibling
  // had just uploaded. First, and ahead of every early return below — the lists, the widgets and
  // the start alerts must not stop because commentary is paused on a read budget or a broken
  // classifier; the poll spends no database reads. A poll that fails, or a live fetch that failed
  // inside it, falls back to the file, which is what this run read before the merge.
  const polled = await runPoll({ storage, log, error }).catch((e) => {
    error(`Match-list poll failed: ${e?.message}`);
    reportError(e, { operation: "poll_lists" });
    return null;
  });

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
  const db = new Databases(client);

  // Held deliveries are offered to the writer first thing, and the backlog is saved on every path
  // out of this run — including the early returns with nothing live, because a match that ended
  // with balls still held has no more syncs of its own to carry them.
  //
  // The dedicated batches start once the run knows who is watching what — that decides the order —
  // or at `settleBacklog` on a path that returns before then. Until `startDrain` runs the sets are
  // empty, which reads every held delivery as a finished match's: the right answer with nothing live.
  const backlogLoad = loadBacklog({ storage, error });
  backlogCtx = { db, log, error, viewers: new Set(), liveIds: new Set(), startedMs: Date.now() };
  let backlogRun = null;
  const startDrain = () => {
    backlogRun ??= backlogLoad.then(() => drainBacklog({ db, log, error, viewers: backlogCtx.viewers, liveIds: backlogCtx.liveIds }));
  };
  const settleBacklog = async () => {
    startDrain();
    await backlogRun.catch((e) => error(`Backlog drain failed: ${e?.message}`));
    await settleHeldWork();
    backlogCtx = null;
    await saveBacklog({ storage, log, error });
  };

  // 1. The live list: from this run's poll above, or the `live-matches` file when the poll failed.
  // node-appwrite auto-parses JSON files (returns the object); older/plain files return bytes.
  // The model is loaded before anything is gated on, because the pre-match pass below needs it even
  // on a run with nothing live — which is most of the night, and exactly when the app is showing
  // only upcoming fixtures.
  const model = await loadModel({ storage, log, error });
  const contexts = contextStoreFor({ storage, log, error });
  const contextBudget = newBudget();
  // Once a day, on the 04:00 UTC run.
  const clock = new Date();
  if (clock.getUTCHours() === 4 && clock.getUTCMinutes() === 0) await pruneContexts({ storage, log, error });
  const prematch = await prematchEntries({ storage, model, contexts, budget: contextBudget, log, error });

  let matches = [];
  try {
    const live = polled?.lists?.live;
    if (live && Array.isArray(live.matches)) {
      matches = live.matches;
    } else {
      const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, LIVE_FILE));
      matches = parsed?.data?.matches ?? [];
    }
  } catch (e) {
    log(`No live-matches file — writing ${Object.keys(prematch).length} pre-match entries only`);
    // A healthy run. poll-live-matches simply has nothing for us, which is the normal state
    // overnight — reporting it as a failed check-in would page every night. The file is still
    // written: an upcoming fixture has a probability whether or not anything is in play, and
    // returning early used to leave yesterday's live numbers as the newest thing on the device.
    await writeWinProbFile({ storage, winProb: prematch, log, error });
    await settleBacklog();
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
  const eligible = matches.filter((m) => m && m.id);
  // Only `allLive.length` used to be logged, and that number can never exceed MAX_MATCHES — so the
  // one figure that would reveal the cap binding was the one the cap had already censored.
  const { allLive, dropped } = liveMatchSlice(eligible, activeViewers);
  const live = allLive.filter((m) => activeViewers.has(String(m.id)));
  if (allLive.length === 0) {
    log(`No live matches — writing ${Object.keys(prematch).length} pre-match entries only`);
    await writeWinProbFile({ storage, winProb: prematch, log, error });
    await settleBacklog();
    runStatus = "ok";
    return res.json({ skipped: true, reason: "no_live_matches", prematch: Object.keys(prematch).length }, 200);
  }
  // Prune the warm dedupe cache to matches still live so ended matches don't retain memory.
  //
  // Against ALL live matches, not just viewed ones: pruning to the viewed set discarded the
  // watermark of every unviewed match on every run, so the slow tier below would have paid a fresh
  // seed read on each of its passes for a value it already had.
  const liveIds = new Set(allLive.map((m) => String(m.id)));
  // Loaded before any match sync asks `isHeld`, and before a fresh batch looks for held deliveries
  // to fill its spare slots.
  await backlogLoad;
  backlogCtx.viewers = activeViewers;
  backlogCtx.liveIds = liveIds;
  startDrain();
  for (const id of watermarkByMatch.keys()) {
    if (!liveIds.has(id)) watermarkByMatch.delete(id);
  }
  for (const cache of [legalRowByBall, situationByMatch]) {
    for (const id of cache.keys()) if (!liveIds.has(id)) cache.delete(id);
  }
  for (const cache of [lastDetailBody, lastScorecardBody, lastScorecardWrite, overHistoryByMatch]) {
    for (const id of cache.keys()) if (!liveIds.has(id)) cache.delete(id);
  }
  for (const id of overHistorySeeded) if (!liveIds.has(id)) overHistorySeeded.delete(id);
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
  const commentaryMatches = commentaryMatchSet(allLive, viewed, slowTier);
  log(
    // `eligible.length` is the PRE-slice count, not the cap — the old wording read as
    // "MAX_MATCHES of 11" and so reported a cap of 11 when MAX_MATCHES is 12. Misnaming this
    // number is the same failure the cap logging was added to fix.
    `${allLive.length} of ${eligible.length} live synced (${dropped.length} dropped by MAX_MATCHES=${MAX_MATCHES}), ` +
      `${live.length} with viewers, ${slowTier.size} on the slow tier, ` +
      `${commentaryMatches.size} storing commentary`
  );
  // Only when it actually binds. A run that fits under the cap stays silent.
  if (dropped.length > 0) {
    // The slice is viewers-first, so a watched match is dropped only when more than MAX_MATCHES
    // are watched at once. That is the case worth waking up for; a bare count would not
    // distinguish it. Keyed on the dropped ids: the same overflow stays quiet for an hour, a new
    // one reports at once.
    const droppedIds = dropped.map((m) => String(m.id));
    const droppedWithViewers = droppedIds.filter((id) => activeViewers.has(id));
    reportThrottled(`max-matches:${droppedIds.join(",")}`, STEADY_STATE_REPORT_MS, "MAX_MATCHES dropped live matches — they get no commentary, detail row or win probability", {
      cap: MAX_MATCHES,
      eligible: eligible.length,
      dropped: dropped.length,
      droppedIds: droppedIds.join(","),
      droppedWithViewers: droppedWithViewers.join(","),
    }, droppedWithViewers.length > 0 ? "error" : "warning");
  }

  // Matches whose final rows were written this run — see FINAL_SUFFIX.
  const finalisedNow = new Set();
  // Push sends queued by syncMatch, awaited before the run returns (the container freezes on
  // return and an unsent request is lost). Null when the Firebase secrets are unset.
  const alertsOn = fcmConfigured();
  const alerts = alertsOn ? [] : null;
  if (!alertsOn) log("FCM not configured (FIREBASE_SERVICE_ACCOUNT_JSON / FIREBASE_PROJECT_ID) — no match alerts sent");

  metaTally = {};
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
          storeCommentary: commentaryMatches.has(matchId),
          alreadyFinalised: isFinalised(slowState, matchId),
          model,
          log,
          error,
          alerts,
          contextFor: ({ meta, format }) => {
            const resolved = formatFromText(format, m.title ?? "");
            // T10 and an unreadable format have no slice in the archive, so no context to build.
            if (!["T20", "ODI", "HUNDRED", "TEST"].includes(resolved)) return null;
            return contexts.ensure({
              matchId, meta, model, format: resolved, gender: genderFromTitle(m.title ?? ""), budget: contextBudget,
            });
          },
        });
        summary[matchId] = result.status;
        if (result.finalised) finalisedNow.add(matchId);
        if (result.winProb) winProb[matchId] = result.winProb;
      } catch (e) {
        error(`Match ${matchId} failed: ${e?.message}`);
        // A block is the host telling us to stand down, not a defect on our side — it is reported
        // once per run with its own fingerprint instead of one exception per match, so a throttled
        // minute does not read as twelve independent failures.
        if (e?.blocked) {
          summary[matchId] = "blocked";
        } else if (e?.appwriteNetwork) {
          // Our own backend, not Cricbuzz. It used to fall into the branch below and read as
          // "Cricbuzz is unreachable", which sent the diagnosis to the wrong host.
          summary[matchId] = "appwrite_unreachable";
        } else if (isUpstreamNetworkError(e)) {
          // Cricbuzz timing out from this container is one upstream condition, not sixteen
          // independent defects. Counted per run below, the same shape `blocked` already uses.
          summary[matchId] = "unreachable";
        } else {
          reportError(e, { matchId, operation: "sync_match" });
          summary[matchId] = "error";
        }
      }
    })
  );

  for (const line of metaTallyLines(metaTally)) log(line);

  if (alerts?.length) {
    const sent = (await Promise.all(alerts)).filter(Boolean).length;
    log(`Sent ${sent}/${alerts.length} match alert(s)`);
    if (sent < alerts.length) {
      // Logged, not reported: every failed send is already its own event carrying the topic and
    // FCM's error code, and a second event per run with only the tally would double-count them.
    error(`[ALERTS] ${alerts.length - sent} of ${alerts.length} match alert(s) failed this run`);
    }
  }

  if (finalisedNow.size > 0) {
    log(`${finalisedNow.size} match(es) finalised: ${[...finalisedNow].join(",")}`);
  }

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
  const unreachableIds = Object.keys(summary).filter((id) => summary[id] === "unreachable");
  if (unreachableIds.length > 0) {
    error(`[UPSTREAM-UNREACHABLE] ${unreachableIds.length} match(es) failed to fetch this run`);
    reportThrottled("cricbuzz-unreachable", STEADY_STATE_REPORT_MS, "Cricbuzz is unreachable from this container — matches skipped", {
      matches: unreachableIds.length,
      matchIds: unreachableIds.join(","),
    });
  }

  const appwriteDownIds = Object.keys(summary).filter((id) => summary[id] === "appwrite_unreachable");
  if (appwriteDownIds.length > 0) {
    error(`[APPWRITE-UNREACHABLE] ${appwriteDownIds.length} match(es) could not read their watermark this run`);
    reportThrottled("appwrite-unreachable", STEADY_STATE_REPORT_MS, "Appwrite is unreachable from match-sync — matches skipped", {
      matches: appwriteDownIds.length,
      matchIds: appwriteDownIds.join(","),
    });
  }

  const blockedIds = Object.keys(summary).filter((id) => summary[id] === "blocked");
  if (blockedIds.length > 0) {
    error(`[CRICBUZZ-BLOCKED] ${blockedIds.length} match(es) skipped this run`);
    reportThrottled("cricbuzz-blocked", STEADY_STATE_REPORT_MS, "Cricbuzz is throttling this container — matches skipped", {
      matches: blockedIds.length,
      matchIds: blockedIds.join(","),
      backoffMs: BLOCK_BACKOFF_MS,
    });
  }

  await writeWinProbFile({ storage, winProb, log, error });
  await writeSlowState({
    storage,
    state: nextSlowState({
      state: slowState,
      processed: slowTier,
      liveIds,
      now: Date.now(),
      finalised: finalisedNow,
    }),
    previous: slowState,
    log,
    error,
  });

  await settleBacklog();
  reportDeadProviders(error);
  reportChainHealth(error);

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
    finalised: finalisedNow.size,
    winProbabilities: Object.keys(winProb).length,
    summary,
  });
}

/**
 * How many of this match's new deliveries get an LLM rewrite this run.
 *
 * This used to be the number of rows the match could WRITE, which made the viewer gate a storage
 * gate: a match nobody had open stored one ball per 45 minutes, so its commentary feed was a
 * sample and not a commentary. Overs 84-88 of match 163017 were never captured at all, its whole
 * innings held one FOUR, and the Overview's newest line was hours old. Storage is now
 * unconditional (see `commentaryMatchSet`) and costs no LLM call; this budget only decides which
 * of those rows get broadcast prose instead of Cricbuzz's own words.
 *
 * Every delivery is rewritten now, and one without a verified line is held rather than stored raw
 * (see "Rewrite backlog"), so this no longer decides whether a row carries Cricbuzz's words.
 */
/**
 * Where a stored row's TEXT came from, as the two columns that record it.
 *
 * One function because `ai_generated` and `provider` describe the same fact, and two fields
 * describing one fact drift — this file has a whole section on that. Derived from one input so
 * they cannot disagree.
 *
 * `ai_generated` alone stops a question short. A false row is either a chain that failed (a defect)
 * or a delivery this run had no rewrite budget for (a design decision, and by far the common case:
 * measured 2026-09-16, ~97% of rows). Those need opposite responses and were indistinguishable in
 * storage. So: a rung name means rewritten, `cricbuzz` means nobody tried, and
 * `cricbuzz:<reason>` means somebody tried and failed — `providers_exhausted` if nobody answered,
 * `verification_failed` if somebody did and was rejected, `verifier_unavailable` if the line was
 * generated but no rung could fact-check it. The ladder's real mix becomes a GROUP BY
 * rather than a guess, which is the thing nobody could do while cerebras and sambanova were dead.
 *
 * Since 2026-09-27 no new row takes a `cricbuzz*` value: a delivery with no verified line is held
 * in the backlog instead of written. The branches stay because the table still holds older rows
 * and the invariant between the two columns is pinned against them.
 *
 * @param generated `generateVerifiedLine`'s result, or null when it was never called.
 */
export function commentaryOrigin(generated) {
  if (!generated) return { aiGenerated: false, provider: "cricbuzz" };
  // A plain line built in code from the scorer's header: not raw text, and not a model's either.
  if (generated.provider === TEMPLATE_PROVIDER) return { aiGenerated: false, provider: TEMPLATE_PROVIDER };
  if (generated.line) return { aiGenerated: true, provider: generated.provider ?? "unknown" };
  return { aiGenerated: false, provider: `cricbuzz:${generated.reason ?? "unknown"}` };
}

/**
 * The live matches processed this run: every match with a viewer first, then Cricbuzz's order,
 * capped at [MAX_MATCHES]. Stable, so within each group the list order still decides.
 */
export function liveMatchSlice(eligible, activeViewers, cap = MAX_MATCHES) {
  const watched = eligible.filter((m) => activeViewers.has(String(m.id)));
  const rest = eligible.filter((m) => !activeViewers.has(String(m.id)));
  const ordered = [...watched, ...rest];
  return { allLive: ordered.slice(0, cap), dropped: ordered.slice(cap) };
}

/** A delivery's key in a cross-match batch. Unique per delivery, not per ball label. */
export function batchKey(matchId, ball, timestamp) {
  return `${matchId}|${ball}|${timestamp}`;
}

export function commentaryCapFor(matchId, viewed, slowTier) {
  if (viewed.has(matchId)) return MAX_NEW_PER_MATCH;
  if (slowTier.has(matchId)) return SLOW_MAX_NEW_PER_MATCH;
  return 0;
}

/**
 * The live matches whose commentary is stored this run, viewed first, then slow tier, then the
 * rest, capped at [MAX_COMMENTARY_MATCHES].
 *
 * Everything in the set stores EVERY new delivery. The cap exists only so an unusually long live
 * list cannot make the per-run write count unbounded; in steady state the watermark bounds it to
 * the balls actually bowled since the last run.
 */
export function commentaryMatchSet(allLive, viewed, slowTier) {
  const rank = (id) => (viewed.has(id) ? 0 : slowTier.has(id) ? 1 : 2);
  const ids = allLive.map((m) => String(m.id));
  ids.sort((a, b) => rank(a) - rank(b));
  return new Set(ids.slice(0, MAX_COMMENTARY_MATCHES));
}

/**
 * The slow tier's clock. A missing or unreadable file reads as `{}` — every live match is then
 * due, which is the safe direction: the tier is capped per run anyway, and the alternative
 * (treating unreadable as "nothing is due") would silently disable it exactly like the
 * already-parsed-JSON bug disabled the viewer gate.
 */
//
// This function is the file's only writer, so a warm container already holds what the file says:
// it is downloaded once per cold container, not once a minute. A small download still costs ~2 MB
// a day in per-request overhead at this cadence.
let slowStateMemo = null;

async function readSlowState({ storage, error }) {
  if (slowStateMemo) return slowStateMemo;
  try {
    const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, SLOW_STATE_FILE));
    slowStateMemo = parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
    return slowStateMemo;
  } catch (e) {
    if (e?.code !== 404) error(`Could not read ${SLOW_STATE_FILE} (${e?.message}) — treating every match as due`);
    // A 404 is a real empty clock and can be remembered; any other failure retries next run.
    if (e?.code === 404) slowStateMemo = {};
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
    slowStateMemo = state;
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
/**
 * Fractional numbers rounded to three places — 0.1 of a percentage point, finer than any surface
 * renders. At full float precision nearly every ball moved the fifteenth decimal of some match, so
 * the unchanged-table skip below almost never fired and the file was rewritten every run.
 * Integers (team ids, timestamps) pass through untouched.
 */
export const roundFractions = (_key, v) =>
  typeof v === "number" && !Number.isInteger(v) ? Math.round(v * 1000) / 1000 : v;

async function writeWinProbFile({ storage, winProb, log, error }) {
  // The device's freshness is its own TTL, never `updatedAt`, so an unchanged table need not be
  // re-uploaded; a cold container always writes once.
  const body = JSON.stringify(winProb, roundFractions);
  if (body === lastWinProbBody) return;
  try {
    const payload = JSON.stringify({ version: 1, updatedAt: Date.now(), matches: JSON.parse(body) });
    await storage.deleteFile(BUCKET_ID, WINPROB_FILE).catch(() => {});
    await storage.createFile(
      BUCKET_ID,
      WINPROB_FILE,
      InputFile.fromBuffer(Buffer.from(payload, "utf8"), "live-winprob.json"),
      READ_ANY
    );
    lastWinProbBody = body;
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
  if (Date.now() - modelCheckedAt < MODEL_RECHECK_MS) return cachedModel;
  modelCheckedAt = Date.now();
  try {
    // The manifest names the model. Before the first v6 model is published there is none, and
    // the v5 file under its old fixed name is read instead.
    const manifest = await storage
      .getFileDownload(BUCKET_ID, MANIFEST_FILE)
      .then(parseJsonFile)
      .catch((e) => {
        if (e?.code !== 404) error(`Could not read ${MANIFEST_FILE} (${e?.message})`);
        return null;
      });
    const fileId = manifest?.fileId ?? TEAM_STATS_FILE;
    if (fileId === cachedModelFile && cachedModel) return cachedModel;
    const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, fileId));
    cachedModel = parsed;
    cachedModelFile = fileId;
    log(`Loaded win-probability model v${parsed?.version ?? "?"} from ${fileId} (${Object.keys(parsed?.teamStats ?? {}).length} teams)`);
  } catch (e) {
    // The model already in hand keeps serving. With none, the heuristic does, and the next hourly
    // check tries again — the old latch held a failed load for the life of the container.
    error(`Could not load the win-probability model (${e?.message}) — ${cachedModel ? "keeping the one loaded" : "falling back to the heuristic"}`);
  }
  return cachedModel;
}

// One context store per container, so a context built in one run is still there in the next. The
// store outlives the run that created it, but `storage`, `log` and `error` belong to one
// execution, so it reaches them through this object and each run points it at its own.
let contextStore = null;
const contextIo = { storage: null, log: () => {}, error: () => {} };

function contextStoreFor({ storage, log, error }) {
  Object.assign(contextIo, { storage, log, error });
  if (contextStore) return contextStore;
  contextStore = createContextStore({
    readJson: async (fileId) => parseJsonFile(await contextIo.storage.getFileDownload(BUCKET_ID, fileId)),
    writeJson: async (fileId, value) => {
      await contextIo.storage.deleteFile(BUCKET_ID, fileId).catch(() => {});
      await contextIo.storage.createFile(
        BUCKET_ID,
        fileId,
        InputFile.fromBuffer(Buffer.from(JSON.stringify(value), "utf8"), `${fileId}.json`),
        ['read("any")']
      );
    },
    fetchPage: fetchHtml,
    parseMatchMeta,
    parseSquads,
    squadsUrl,
    matchUrl: liveScoresUrl,
    fetchForecast,
    log: (m) => contextIo.log(m),
    error: (m) => contextIo.error(m),
  });
  return contextStore;
}

const CONTEXT_PREFIX = "mc-";
const CONTEXT_RETENTION_MS = 14 * 24 * 60 * 60 * 1000;

/**
 * Delete match contexts nobody will read again. One file is written per match and nothing else
 * removes them.
 *
 * Selected with `startsWith("$id", …)`, never `search`: storage search is fuzzy, and this bucket
 * holds every other function's state. The prefix is checked again on each candidate before it is
 * deleted, because a surprise in query semantics must not be able to become a deletion.
 */
export async function pruneContexts({ storage, log, error, now = Date.now() }) {
  try {
    const cutoff = new Date(now - CONTEXT_RETENTION_MS).toISOString();
    const page = await storage.listFiles(BUCKET_ID, [
      Query.startsWith("$id", CONTEXT_PREFIX),
      Query.lessThan("$updatedAt", cutoff),
      Query.limit(100),
    ]);
    let deleted = 0;
    for (const file of page?.files ?? []) {
      if (!String(file.$id).startsWith(CONTEXT_PREFIX) || !(file.$updatedAt < cutoff)) continue;
      await storage.deleteFile(BUCKET_ID, file.$id);
      deleted++;
    }
    if (deleted) log(`Pruned ${deleted} match context file(s) older than 14 days`);
    return deleted;
  } catch (e) {
    error(`Context prune failed: ${e?.message}`);
    return 0;
  }
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
async function prematchEntries({ storage, model, contexts, budget, log, error }) {
  const entries = {};
  if (!model) return entries;
  let fixtures = [];
  try {
    if (upcomingCache && Date.now() - upcomingCache.fetchedAt < UPCOMING_TTL_MS) {
      fixtures = upcomingCache.fixtures;
    } else {
      const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, UPCOMING_FILE));
      fixtures = parsed?.data?.matches ?? [];
      upcomingCache = { fixtures, fetchedAt: Date.now() };
    }
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

    // The fixture's context — ground, record, forecast — built a couple a run until every
    // fixture has one. A fixture without one yet is scored on team terms alone.
    const context = await contexts
      .ensure({ matchId: fixture.id, model, format: slice, gender, budget, live: false })
      .catch(() => null);
    const firstNamed = context?.teams?.find((t) => t.key === stripWomenSuffix(sides[0]));
    const result = evaluatePrematch({
      firstTeam: stripWomenSuffix(sides[0]),
      secondTeam: stripWomenSuffix(sides[1]),
      gender,
      format: slice,
      resolved: true,
      context: firstNamed ? sideContextFor(context, firstNamed.id) : teamOnlyContext(teamStats, sides, gender, slice),
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

/**
 * The team terms alone, for a fixture whose context has not been built yet. The model file
 * carries every side's Elo and form, so this needs no fetch.
 */
function teamOnlyContext(teamStats, sides, gender, format) {
  const of = (side) => teamStats[teamKey(stripWomenSuffix(side), gender, format)];
  const own = of(sides[0]);
  const opp = of(sides[1]);
  return own && opp ? sideContext({ own, opp, venue: null }) : null;
}

/** Both named sides have a record in this (gender, format) slice, regardless of who bats first. */
function bothSidesKnown({ sides, teamStats, format, gender }) {
  return sides.every((side) => {
    const key = teamKey(stripWomenSuffix(side), gender, format);
    return Object.prototype.hasOwnProperty.call(teamStats, key);
  });
}

/**
 * The first-innings side's win probability, given the batting side's.
 *
 * A Test is three outcomes, so the complement of the batting side's win is `loss + draw`, not the
 * bowling side's win. Returning it handed the whole draw to the bowling side and published pairs
 * that sum past 1 — match 170667 read 0.80 against a 0.41 draw, which leaves the loss negative.
 * The draw belongs to neither side and so is never re-oriented; it is only subtracted out.
 *
 * Exported so the orientation rule has exactly one definition. The integration test used to carry
 * its own copy of it, which is why the suite stayed green through the bug above.
 */
export function orientToTeam1({ battingTeamWinProbability, drawProbability, battingTeamIsTeam1 }) {
  if (battingTeamIsTeam1) return battingTeamWinProbability;
  const other = 1 - (drawProbability ?? 0) - battingTeamWinProbability;
  return Math.min(1, Math.max(0, other));
}

function winProbFor(detail, title, model, matchContext = null) {
  const s = detail?.situation;
  if (!s) return null;
  // The payload's team1 is the first-innings side; the context knows sides by Cricbuzz team id.
  const battingId = s.battingTeamIsTeam1 ? detail.team1Id : detail.team2Id;
  const context = matchContext && battingId != null ? sideContextFor(matchContext, battingId) : null;

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
      context,
      model,
    });
  if (result.undetermined) return null;

  return {
    team1WinProbability: orientToTeam1({
      battingTeamWinProbability: result.battingTeamWinProbability,
      drawProbability: result.drawProbability,
      battingTeamIsTeam1: s.battingTeamIsTeam1,
    }),
    // A draw belongs to neither side, so unlike the win probability it is not re-oriented.
    drawProbability: result.drawProbability,
    confidence: result.confidence,
    battingTeamName: s.battingTeamName,
    battingTeamIsTeam1: s.battingTeamIsTeam1,
    // The side team1WinProbability is oriented to, as a Cricbuzz team id. Without it the device
    // cannot tell this "team1" from its own teams[0], which is Cricbuzz's listing order.
    team1Id: s.firstInningsTeamId ?? null,
    format,
  };
}

// One retry on a 5xx before the gate gives up. A storage 500 is transient, and failing closed on
// it costs the whole minute: every match drops to the 45-minute slow tier and the commentary tab
// falls back to raw Cricbuzz text. A 404 is the ordinary pre-first-ping state and is not retried.
async function readPresenceFile(storage, log) {
  try {
    return await storage.getFileDownload(BUCKET_ID, PRESENCE_FILE);
  } catch (e) {
    const code = Number(e?.code);
    if (!(code >= 500 && code < 600)) throw e;
    log(`active-viewers read failed with ${code} — retrying once`);
    await new Promise((resolve) => setTimeout(resolve, PRESENCE_RETRY_DELAY_MS));
    return storage.getFileDownload(BUCKET_ID, PRESENCE_FILE);
  }
}

// A transport failure reaching Cricbuzz: DNS, TCP timeout, unreachable route, or undici's
// `TypeError: fetch failed` wrapper around any of them. Deliberately narrow — an HTTP status or a
// parse failure is ours to fix and must keep its own event.
const NETWORK_ERROR_CODES = new Set([
  "ETIMEDOUT",
  "ENETUNREACH",
  "ECONNRESET",
  "ECONNREFUSED",
  "EAI_AGAIN",
  "ENOTFOUND",
  "UND_ERR_CONNECT_TIMEOUT",
  "UND_ERR_HEADERS_TIMEOUT",
]);

function isUpstreamNetworkError(e) {
  if (!e) return false;
  if (e.name === "TimeoutError" || e.name === "AbortError") return true;
  for (let cur = e, depth = 0; cur && depth < 5; cur = cur.cause, depth += 1) {
    if (NETWORK_ERROR_CODES.has(cur.code)) return true;
    if (Array.isArray(cur.errors) && cur.errors.some((inner) => NETWORK_ERROR_CODES.has(inner?.code))) return true;
  }
  return e instanceof TypeError && /fetch failed/i.test(e.message ?? "");
}

// Read the presence map written by bouncer-match-presence and return the set of matchIds
// with a viewer seen within VIEWER_TTL_MS. Any failure (missing file, parse error) is
// treated as "no active viewers" — the gate fails closed so we never generate blind.
async function activeViewerIds({ storage, log, error }) {
  const now = Date.now();
  try {
    const map = parseJsonFile(await readPresenceFile(storage, log));
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

// Exported for `finalise.test.js`: the finalisation wiring (which tier writes, and once) is
// invisible in production until a match ends, which is the class of defect this file keeps finding.
export async function syncMatch({ db, matchId, title, commentaryCap, storeCommentary, alreadyFinalised, model, log, error, alerts = null, contextFor = null }) {
  // Queues one push to this match's topic. [alerts] is null when FCM is unconfigured or in tests
  // that do not exercise alerts, and then nothing is sent.
  const alert = (data) => {
    if (!alerts || !data) return;
    alerts.push(sendTopicMessage(matchTopic(matchId), data, {
      onError: (message, detail = {}) => {
        error(message);
        // Per failed send, with what FCM said. The run's tally alone ("1 of 2") could never say
        // which alert, which topic or which FCM error it was.
        report("match-sync could not deliver a match alert", {
          matchId,
          type: data.type ?? "unknown",
          topic: detail.topic ?? matchTopic(matchId),
          status: detail.status ?? "none",
          fcmStatus: detail.fcmStatus ?? "none",
        }, "warning");
      },
    }));
  };
  // 2. Fetch live page, plus the scorecard only when we are going to use it. A match with no
  // viewer needs the live page alone to produce a win probability — and, now, to store its
  // commentary, which the live page alone carries.
  //
  // Two independent decisions, deliberately two arguments:
  //  - `commentaryCap` now gates the DETAIL tier ONLY — scorecard fetch, detail row, scorecard
  //    row — which is a write-budget decision. It stopped gating rewriting when rewriting became
  //    batched: a batched call covers every live match at once, so there is nothing to ration.
  //  - `storeCommentary` says whether every new delivery is written to `match-commentary`. It is
  //    true for essentially every live match, because a feed that skips balls is not a feed.
  const detailTier = commentaryCap > 0;
  const [liveHtml, tierScoreHtml] = await Promise.all([
    fetchHtml(liveScoresUrl(matchId)),
    detailTier
      ? fetchHtml(scorecardUrl(matchId)).catch((e) => { error(`Scorecard fetch failed for ${matchId}: ${e?.message}`); return null; })
      : Promise.resolve(null),
  ]);

  // 3. Build + upsert the live-detail snapshot.
  const detail = parseLiveDetail(liveHtml);
  const meta = parseMatchMeta(liveHtml);
  tallyMeta(metaTally, meta, detail.status);
  overHistoryByMatch.set(matchId, mergeOverHistory(overHistoryByMatch.get(matchId), parseOverHistory(liveHtml)));

  // 3a. Finalisation. A match leaves `live-matches.json` the moment it is over and never comes
  // back, and nothing else writes these two rows — so whatever the detail tier last left behind is
  // what the device reads forever. For an unviewed match that tier runs once per
  // SLOW_TIER_INTERVAL_MS, so the frozen row was routinely a quarter of an hour short of the
  // result: the detail screen showed a mid-innings score while the Recent list, re-scraped from
  // Cricbuzz every minute, showed the real one.
  //
  // The live page is fetched for every live match on every run regardless of tier, so the status
  // is already in hand here; the scorecard is not, and is fetched now if the tier skipped it.
  // Once per match lifetime — `alreadyFinalised` is the persisted marker, and lastDetailBody
  // suppresses an identical rewrite on top of that.
  const finalising = !alreadyFinalised && isTerminalStatus(detail.status);
  let scoreHtml = tierScoreHtml;
  if (finalising && !scoreHtml) {
    scoreHtml = await fetchHtml(scorecardUrl(matchId)).catch((e) => {
      error(`Final scorecard fetch failed for ${matchId}: ${e?.message}`);
      return null;
    });
  }
  const writeDetail = detailTier || finalising;

  // Milestones come off the live page's batters, which every live match has fetched every run.
  const milestones = milestoneAlerts({
    matchId, title, batsmen: detail.batsmen, previous: batterRunsByMatch.get(matchId), score: detail.score,
  });
  batterRunsByMatch.set(matchId, milestones.next);
  milestones.alerts.forEach(alert);
  const matchContext = contextFor ? await contextFor({ meta, format: detail.situation?.matchFormat }) : null;
  const winProb = winProbFor(detail, title, model, matchContext);
  // The device refetches `mc-{matchId}` only when this number moves.
  if (matchContext) detail.contextRev = matchContext.rev;
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

  if (!writeDetail && !storeCommentary) {
    // No viewer, not due a slow pass, beyond the commentary cap, and not the run that ends the
    // match: the probability is the only product. No scorecard, no LLM, no DB write.
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
  // Gated on the detail tier, not on storeCommentary: a match nobody has open stores its
  // commentary but must NOT pay a live-detail write every minute — that is one row per live match
  // per run, which is the write volume this whole change is budgeted against.
  if (detailHasContent && writeDetail) {
    await upsertDetail({ db, matchId, detail, error });
    // The scorecard now lives ONLY here. upsertDetail strips it from the detail payload, so this
    // write is the sole source for the Scorecard tab and for the Overview's scorecard-derived
    // widgets. The app subscribes to this row's own channel for the life of the detail screen.
    if ((detail.scorecard?.innings?.length || 0) > 0) {
      await seedOverHistory({ db, matchId, error });
      const overs = overHistoryByMatch.get(matchId) ?? [];
      if (overs.length > 0) detail.scorecard.overs = overs;
      await upsertScorecard({ db, matchId, scorecard: detail.scorecard, force: finalising, error });
    }
  } else if (writeDetail) {
    log(`Match ${matchId}: hollow live-detail, skipping upsert`);
  }
  // Reported to handleRun so the marker is persisted for the runs before this match leaves the
  // live list. Claimed only when a row was actually written: a hollow parse must be retried, not
  // recorded as the match's final word.
  const finalised = finalising && detailHasContent;
  if (finalised) {
    log(`Match ${matchId}: finalised on "${detail.status}"`);
    // Once per match: `alreadyFinalised` is persisted, so this is the one run that sends it.
    alert(resultAlert({ matchId, title, status: detail.status }));
    batterRunsByMatch.delete(matchId);
  }

  if (!storeCommentary) {
    return { status: { commentaryWritten: 0, skipped: "beyond commentary cap" }, winProb, finalised };
  }

  // 4. Commentary: store every new delivery, and rewrite every one of them (batched).
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

  // The innings each line belongs to. Preferred source is the commentary item itself, because the
  // two write-time sources below are both a snapshot of the innings being batted WHEN THE ROW IS
  // WRITTEN, not when the ball was bowled — which is how ball 89.3, the tail of match 163017's
  // second innings, came to be stored as innings 3 and sorted above the third innings' over 60.
  // The miniscore's inningsId comes off the live page, so it works for a match with no scorecard
  // fetch (the whole no-viewer tier); the scorecard length is the last resort it replaced.
  const fallbackInnings =
    detail.situation?.inningsId || detail.scorecard?.innings?.length || 1;

  // Oldest-first among items past the watermark. Order matters twice over: the watermark can only
  // advance contiguously from the bottom, and the LLM budget is spent in this order, so the oldest
  // unrewritten deliveries are the ones that get broadcast prose rather than an arbitrary slice.
  // A held delivery belongs to the backlog. It is still on the page after a cold start re-seeds the
  // watermark below it, and queueing it here as well would rewrite it twice in one run.
  const fresh = commentary
    .filter((i) => Number(i.timestamp) > watermark && !isHeld(docId(`${matchId}:${i.timestamp}`)))
    .sort((a, b) => Number(a.timestamp) - Number(b.timestamp));

  // Rewrite every fresh delivery in one batched call, BEFORE the write loop.
  //
  // Per delivery inside the loop this could not batch at all: the loop awaits, so a match with six
  // new balls would issue six calls of one. Queued up front, this match's deliveries batch with
  // each other AND with every other match's, because `handleRun` runs the matches concurrently.
  //
  // Every fresh delivery is queued, not the first BATCH_MAX_ITEMS. The batcher already splits a
  // queue into batches of BATCH_MAX_ITEMS, so the old per-match slice bought nothing but raw text:
  // in steady state a match produces about one delivery a run, and the slice bound only on a new
  // match's first sync or a Cricbuzz backlog republish — ~395 deliveries a day stored raw and never
  // retried, because the watermark moves past them (measured 2026-09-24/25). `fresh` is bounded by
  // the live page's ~20-item window, so this is at most two batches per match.
  //
  // Note this is NOT gated on `commentaryCap`. That still gates the detail tier (`detailTier`
  // above) and its scorecard writes, which are a write-budget decision; rewriting is now a
  // batched-call decision and every live match gets it.
  const rewrites = new Map();
  if (storeCommentary) {
    const queued = [];
    for (const item of fresh) {
      const classified = classifyCommentaryItem(item);
      if (!classified) continue;
      const plain = plainLine(classified.clean, Number(item.timestamp));
      if (plain) {
        rewrites.set(Number(item.timestamp), { line: plain, provider: TEMPLATE_PROVIDER, verified: true, reason: null, attempts: [] });
        continue;
      }
      queued.push({ ts: Number(item.timestamp), clean: classified.clean, ball: classified.ball, note: sourceConflictNote(classified.clean, detail.batsmen) });
    }
    const results = await Promise.all(
      queued.map(async (qi) => {
        // The batch prompt carries the raw Cricbuzz line and nothing else. That line already names
        // the bowler and the batter ("Hassan Khan to Chris Green, 1 run, ..."), which is what the
        // measured 7/7 run used; the per-match bowler/batsman/score context cannot travel in a
        // cross-match batch, and a score carried from the wrong match would be a fabricated fact.
        // The key is `match|ball|timestamp`, not `match|ball`: an extra repeats its ball label —
        // "2.2 wide" and then "2.2 no run" are two deliveries — so the shorter key collided. The
        // batch kept one line and the checker, seeing the key twice, rejected both, which is most
        // of what ANDROID-38's `verification_failed` rows were on 2026-09-25.
        // `text` is what BOTH the writer and the checker read, so a note reaches the two together.
        const out = await queueRewrite({ key: batchKey(matchId, qi.ball, qi.ts), ball: qi.ball, text: qi.clean + qi.note, log, error });
        return { qi, out };
      })
    );
    for (const { qi, out } of results) {
      // The fact-check already ran inside the batch (`flushBatch`), so a line arrives here either
      // verified or null with the reason it is not.
      if (!out.line) {
        rewrites.set(qi.ts, { line: null, provider: null, verified: false, reason: out.reason ?? "providers_exhausted", attempts: out.attempts, note: qi.note });
        continue;
      }
      rewrites.set(qi.ts, { line: out.line, provider: out.provider, verified: true, reason: null, attempts: out.attempts });
    }
  }

  let written = 0;
  let rewritten = 0;
  // Every delivery this run writes, oldest first, for the gap check after the loop. Only what we
  // write: the check must never query the table for what came before, because that is billed reads
  // on the quota that caused the July 402.
  const writtenBalls = [];
  let newWatermark = watermark;
  // The watermark may only advance over a CONTIGUOUS run of handled items. Once one ball fails to
  // write, nothing after it may raise the mark, or that ball falls permanently below it and is
  // never retried — the loop is ascending, so the next success would otherwise carry the mark
  // straight past the failure. The comment above has always claimed this; only now does the code do
  // it. `advance` is what enforces it.
  let contiguous = true;
  const advance = (itemTs) => { if (contiguous) newWatermark = itemTs; };
  // Network write failures, reported once per match per run after the loop — one Sentry event per
  // ball read a single upstream cause as hundreds of independent failures.
  let networkWriteFailures = 0;
  let heldNow = 0;
  for (const item of fresh) {
    const rowId = docId(`${matchId}:${item.timestamp}`);
    const itemTs = Number(item.timestamp);

    // Skipped items are still "processed" — advance past them or they are re-examined forever.
    const classified = classifyCommentaryItem(item);
    if (!classified) { advance(itemTs); continue; }
    const { clean, ball } = classified;
    const innings = item.innings ?? fallbackInnings;

    const pre = rewrites.get(itemTs);
    const line = pre?.line ? storedLine(pre.line, ball) : null;
    if (!line) {
      // No verified line: the backlog takes the delivery and the next runs retry it. Raw Cricbuzz
      // text is never stored — see "Rewrite backlog" above. The watermark still advances, because
      // the backlog now owns this ball; blocking the mark on it would stall every ball behind it.
      const attempts = pre?.attempts ?? [];
      error(`[HOLD] ${matchId}/${ball} (${pre?.reason ?? "not_attempted"}): no verified line yet — ${attempts.join("; ")}`);
      // `rejections` counts fact-check FAILs only: a spent ladder is not the ball's fault.
      holdDelivery({
        matchId, rowId, ts: itemTs, sortKey: item.timestamp, ball, innings, clean, firstSeenMs: Date.now(),
        ...(pre?.note ? { note: pre.note } : {}),
        rejections: pre?.reason === "verification_failed" ? 1 : 0,
      });
      heldNow++;
      advance(itemTs);
      // The alert does not wait for the line: a wicket alert that arrives with the prose, minutes
      // later, is past ALERT_MAX_AGE_MS and would never be sent. Classified from the raw text, the
      // same as a written row's.
      alert(deliveryAlert({
        matchId, title, event: classifyEvent(clean), ball, text: clean, timestampMs: itemTs, now: Date.now(), score: detail.score,
      }));
      // Present for the gap check: a held ball was captured, it just has no row yet.
      writtenBalls.push({ innings, ball });
      continue;
    }
    rewritten++;

    const data = commentaryRowData({ matchId, innings, ball, sortKey: item.timestamp, line, clean, provider: pre.provider });
    try {
      await db.createDocument(DB_ID, COMMENTARY_COL, rowId, data, READ_ANY);
      advance(itemTs);
      written++;
      const supersededEvent = await supersedeOlder({ db, error, matchId, rowId, ts: itemTs, data });
      // Only on a first write: a 409 means another execution stored this ball and alerted for it.
      // A correction that says the same thing the withdrawn row did has been alerted already.
      if (supersededEvent !== data.event) {
        alert(deliveryAlert({
          matchId, title, event: data.event, ball, text: clean, timestampMs: itemTs, now: Date.now(), score: detail.score,
        }));
      }
      writtenBalls.push({ innings, ball });
    } catch (e) {
      // 409 = row already exists (race / watermark behind): it is durably written, so advance.
      if (e?.code === 409) advance(itemTs);
      else {
        // A real write failure closes the watermark for the rest of this pass, so the next run
        // starts from this ball instead of skipping it forever.
        contiguous = false;
        error(`Commentary write failed (${matchId}/${ball}): ${e?.message}`);
        // A network failure to Appwrite fails every remaining ball the same way; it is one
        // condition, tallied below. Anything else is a defect worth its own exception.
        if (isUpstreamNetworkError(e)) networkWriteFailures++;
        else reportError(e, { matchId, ball, operation: "commentary_write" });
      }
    }
  }

  // The watermark is NOT persisted to the detail row any more. That was one extra write per match
  // per run, and it is redundant now that `commentaryWatermark` seeds from the commentary table
  // itself — which also works for a match that has no detail row, i.e. every match outside the
  // detail tier.
  if (newWatermark > watermark) watermarkByMatch.set(matchId, newWatermark);
  if (storeCommentary) {
    const deliveries = fresh
      .map((item) => {
        const c = classifyCommentaryItem(item);
        return c && deliveryOf(c.clean, { ball: c.ball, innings: item.innings ?? fallbackInnings, ts: Number(item.timestamp) });
      })
      .filter(Boolean);
    await situationStep({ db, matchId, detail, winProb, deliveries, terminal: finalising, log, error });
  }
  if (networkWriteFailures > 0) {
    reportThrottled(`commentary-write-network:${matchId}`, STEADY_STATE_REPORT_MS, "match-sync could not reach Appwrite to write commentary", {
      matchId,
      failures: networkWriteFailures,
    });
  }
  reportCommentaryGaps({ matchId, writtenBalls, error });
  log(`Match ${matchId}: ${written} new commentary rows (${rewritten} rewritten, ${heldNow} held)`);
  return { status: { commentaryWritten: written }, winProb, finalised };
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
  // `updated_at` is excluded from the comparison — it differs every run by construction, and no
  // reader uses it as a freshness signal.
  if (lastDetailBody.get(matchId) === data.payload) return;
  try {
    await db.updateDocument(DB_ID, DETAIL_COL, rowId, data);
    lastDetailBody.set(matchId, data.payload);
  } catch (e) {
    if (e?.code === 404) {
      try {
        await db.createDocument(DB_ID, DETAIL_COL, rowId, data, READ_ANY);
        lastDetailBody.set(matchId, data.payload);
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
async function upsertScorecard({ db, matchId, scorecard, force = false, error }) {
  const rowId = sanitizeMatchId(matchId);
  const data = {
    match_id: matchId,
    scorecard: JSON.stringify(scorecard),
    updated_at: new Date().toISOString(),
  };
  const now = Date.now();
  const outline = scorecardOutline(scorecard);
  if (!scorecardWriteDue({ previous: lastScorecardWrite.get(matchId), body: data.scorecard, outline, now, force })) return;
  const remember = () => {
    lastScorecardBody.set(matchId, data.scorecard);
    lastScorecardWrite.set(matchId, { body: data.scorecard, outline, at: now });
  };
  try {
    await db.updateDocument(DB_ID, SCORECARD_COL, rowId, data);
    remember();
  } catch (e) {
    if (e?.code === 404) {
      try {
        await db.createDocument(DB_ID, SCORECARD_COL, rowId, data, READ_ANY);
        remember();
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
 * concurrency, and the only thing that ever said so was a load test. Then Cerebras and SambaNova
 * both sat on 402 for six weeks and this alert could not see it, because the ratio was 429-only.
 * A 100% failure ratio over a meaningful number of calls is the one signal that separates the two.
 *
 * Warning, not error: the chain is designed to survive a provider being gone, so this is a
 * degradation to investigate, not an outage. `deadProviders` self-throttles per container.
 */
function reportDeadProviders(error) {
  for (const d of deadProviders()) {
    error(`[PROVIDER-DEAD] ${d.provider} failed ${d.failed}/${d.calls} calls, mostly HTTP ${d.status} — not load, check the model id and account`);
    report("A commentary provider is failing every request — likely dead, not busy", {
      provider: d.provider,
      calls: d.calls,
      failed: d.failed,
      status: String(d.status),
    });
  }
}

/**
 * Report the state of the chain itself, once per run.
 *
 * Two conditions, both of which were unobservable while they were doing damage.
 *
 * DEPTH. `generateVerifiedLine` retries a failed fact-check with `exclude: [used.provider]`, so a
 * chain with one reachable rung cannot retry and every verification FAIL becomes a raw-text row.
 * Each rung's own skip reason looks unremarkable in `attempts`; only the count is alarming, and
 * nothing counted. That is how cerebras and sambanova sat at 402 for six weeks while the alerts
 * stayed green. Error at depth 0 (nothing can be generated at all), error at 1 (generation works,
 * verification is structurally broken), quiet at 2 or more.
 *
 * UNKNOWN QUOTA IDS. The per-day latch matches `/PerDay/i`, inferred from the per-minute id that
 * WAS measured. If Google spells the daily one differently, the latch silently never fires and the
 * rung is re-asked all day — so an unrecognised id is reported rather than assumed harmless. This
 * is the one report here that exists to correct this code's own guess.
 */
function reportChainHealth(error) {
  const { depth, rungs } = chainDepth();
  if (depth < 2) {
    const blocked = rungs.filter((r) => !r.reachable).map((r) => `${r.provider}: ${r.reason}`).join("; ");
    error(`[CHAIN-DEPTH] only ${depth} reachable provider(s) — ${blocked}`);
    // Keyed on the blocked set, so a rung going down or coming back reports at once and a chain
    // that stays short (a day-latched rung holds until the Pacific reset) reports hourly, not on
    // every one-minute run.
    // The key is the blocked rung NAMES, not their reasons: a reason carries the provider's error
    // text, which varies call to call (groq's 429 names its limit and usage), so keying on it
    // re-reported the same outage dozens of times an hour.
    const blockedNames = rungs.filter((r) => !r.reachable).map((r) => r.provider).sort().join(",");
    reportThrottled(`chain-depth:${blockedNames}`, STEADY_STATE_REPORT_MS, "Commentary provider chain is too short to fact-check — a verification FAIL now means raw text", {
      depth: String(depth),
      reachable: rungs.filter((r) => r.reachable).map((r) => r.provider).join(",") || "none",
      blocked,
    }, "error");
  }
  for (const quotaId of drainUnknownQuotaIds()) {
    error(`[QUOTA-UNKNOWN] 429 named an unrecognised quota window: ${quotaId}`);
    report("A provider 429 named a quota id this code cannot classify — the per-day latch may never fire", {
      quotaId,
    });
  }
}

// Seed read: the highest commentary sort_key already written for this match, stored alongside the
// match's own detail row. Called at most once per match per warm container (see watermarkByMatch),
// NOT every run.
//
// One billed read per match per COLD container, and no more: Appwrite bills per document
// RETURNED, so `limit(1)` on the [match_id, sort_key DESC] index costs exactly one — the same as
// the `getDocument` on the detail row this replaced, and unlike the listDocuments(limit(30)) that
// preceded that. Reading the commentary table rather than the detail row is what lets a match
// outside the detail tier keep a watermark at all, now that every live match stores commentary and
// most of them have no detail row. The create-on-409 path stays as the race backstop, so a missed
// or zero result only risks a redundant write.
async function commentaryWatermark({ db, matchId, error }) {
  if (++readsThisRun > READ_BUDGET_PER_RUN) {
    // Hard stop: with warm-container dedupe, seed reads/run should stay well under this.
    // Exceeding it means the cache isn't sticking (cold-loop) or a bug — fail loudly.
    throw new Error(`[READ-BUDGET-402] per-run read cap exceeded (${readsThisRun} > ${READ_BUDGET_PER_RUN}) — aborting`);
  }
  try {
    const res = await db.listDocuments(DB_ID, COMMENTARY_COL, [
      Query.equal("match_id", matchId),
      Query.orderDesc("sort_key"),
      Query.limit(1),
    ]);
    return Number(res?.documents?.[0]?.sort_key) || 0;
  } catch (e) {
    // 404 = the table has nothing for this match, so nothing has been written for it either.
    if (e?.code === 404) return 0;
    if (isBudget402(e)) {
      budget402UntilMs = Date.now() + READ_402_BACKOFF_MS;
      error(`[READ-BUDGET-402] limit_databases_reads_exceeded on ${matchId} — aborting run, cooldown ${READ_402_BACKOFF_MS / 60000} min`);
      throw e; // fail the match (and run) loudly instead of silently resetting the watermark
    }
    error(`Commentary watermark read failed (${matchId}): ${e?.message}`);
    // Marked so the per-match catch does not file it under "Cricbuzz is unreachable" — a fetch
    // failure here is Appwrite's, and isUpstreamNetworkError cannot tell the two hosts apart.
    if (isUpstreamNetworkError(e)) e.appwriteNetwork = true;
    // Rethrow: returning 0 here would re-generate (and re-pay for) every line in the scan window.
    throw e;
  }
}


/**
 * Say out loud when the upstream feed skipped deliveries.
 *
 * Warning, not error, and no recovery attempted: this is Cricbuzz's behaviour, not ours, and the
 * deliveries are unreachable once they leave the live page's 20-item window. What was missing before
 * was any signal at all — the watermark absorbed the hole and the commentary tab showed a short over
 * that looked exactly like a real one. See `findCommentaryGaps` for the measured case.
 */
function reportCommentaryGaps({ matchId, writtenBalls, error }) {
  for (const gap of findCommentaryGaps(writtenBalls)) {
    error(`[GAP] ${matchId} innings ${gap.innings}: ${gap.after} -> ${gap.before}, ${gap.missing} missing`);
    report("Cricbuzz skipped deliveries — commentary has a hole that cannot be backfilled", {
      matchId,
      innings: gap.innings,
      after: gap.after,
      before: gap.before,
      missing: gap.missing,
    });
  }
}
