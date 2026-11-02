#!/usr/bin/env node
//
// Synthetic burst-load harness for `bouncer-generate-commentary`.
//
// WHY THIS EXISTS
// ---------------
// Commentary generation is server-side only (26ce990). The device holds no provider keys, so
// `bouncer-generate-commentary` is the sole path an on-device request can take, and
// `bouncer-match-sync` — which does not call the function, it imports a byte-identical copy of
// llm.js — hits the SAME three provider accounts every minute. Nothing has ever measured what
// happens to those accounts on a busy day; the largest observed sample is 6 provider calls in one
// tick. With no Play Store listing there is no organic traffic that will produce a busy day, so
// the load is manufactured here.
//
// SHAPE — this is the part that matters
// -------------------------------------
// It mirrors match-sync, not a naive fan-out. match-sync processes matches in PARALLEL
// (`Promise.allSettled`, match-sync/src/main.js:308) and the balls within one match SEQUENTIALLY
// (`for (const item of fresh)`, :621), capped at MAX_MATCHES=12 and MAX_NEW_PER_MATCH=10. So the
// worst realistic tick is 12 concurrent chains of 10 serial rows — never 120 requests in flight.
// A 429 found by a flat fan-out would prove nothing about production.
//
// Each row is generate + verify, and on a failed fact-check regenerate + re-verify: up to 11
// provider HTTP calls per row (llm.js:217-244), each with a 15s timeout, inside a 30s function
// timeout.
//
// TODO(post-launch): presence gate. Before and during a run, poll `bouncer-match-presence` and
// abort if any real viewer is watching a live match — a quota burn here degrades their commentary
// to raw Cricbuzz text. Deliberately not built for the 2026-08-04 run: the app has no users yet
// and the operator confirmed they were not watching. Do not reuse this harness after launch
// without it.
//
// USAGE
//   node burst-load.mjs --dry-run          # print the plan + quota cost, call nothing
//   node burst-load.mjs --preflight        # verification gates only (see VERIFICATION below)
//   node burst-load.mjs --run              # preflight, then ramp 4 -> 8 -> 12, then clean up
//   node burst-load.mjs --cleanup-only <runId>
//
// VERIFICATION GATES (--preflight, and --run will not ramp unless they pass)
//   1. one generated row            -> proves auth, body shape, x-bouncer-device path
//   2. same cacheKey again          -> must return source:"cache". Without this a silently
//                                      all-cache-hit ramp would report zero 429s and mean nothing
//   3. execution.logs/errors present -> the ONLY reliable 429 detector (see detect429). If these
//                                      come back empty the run must report itself blind, not clean
//
// COST OF A FULL RAMP
//   240 function executions, 240 document reads, <=240 writes, <=240 deletes on cleanup,
//   and roughly 500-2600 provider HTTP calls against the shared Groq/Cerebras/SambaNova accounts.

import { createHash } from "node:crypto";
import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { Client, Functions, Databases } from "node-appwrite";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(HERE, "..", "..");

const FUNCTION_ID = "bouncer-generate-commentary";
// match-sync MAX_MATCHES = 12, so 12 is the ceiling of the realistic shape. `--levels` narrows it:
// the 2026-08-04 run stopped at the first level, which measured "somewhere at or below 4" and no
// more. Re-measuring after a fix wants the rungs below 4 that were never climbed.
const DEFAULT_RAMP_LEVELS = [4, 8, 12];
const ROWS_PER_CHAIN = 10; // match-sync MAX_NEW_PER_MATCH = 10

// generate-commentary/src/rateLimit.js allows 40 calls / 5min per x-bouncer-device. A breach costs
// that device a 1h block AND writes it to the shared `rate-limit-offenders` storage file, so the
// harness stays well under it and rotates the device id rather than ever finding out.
const DEVICE_CALL_CEILING = 35;

// ── config ──────────────────────────────────────────────────────────────────

/** Read the gitignored local.properties. Values are returned, never logged. */
function readLocalProperties() {
  const out = {};
  let raw;
  try {
    raw = readFileSync(join(REPO_ROOT, "local.properties"), "utf8");
  } catch {
    return out;
  }
  for (const line of raw.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq < 0) continue;
    out[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
  }
  return out;
}

function loadConfig() {
  const props = readLocalProperties();
  const pick = (name, fallback) => process.env[name] ?? props[name] ?? fallback;
  const config = {
    endpoint: pick("APPWRITE_ENDPOINT", "https://sgp.cloud.appwrite.io/v1"),
    projectId: pick("APPWRITE_PROJECT_ID", ""),
    apiKey: pick("APPWRITE_API_KEY", ""),
    dbId: pick("APPWRITE_AI_CACHE_DB_ID", "bouncer-db"),
    colId: pick("APPWRITE_AI_CACHE_COL_ID", "ai-commentary-cache"),
  };
  const missing = ["projectId", "apiKey"].filter((k) => !config[k]);
  if (missing.length) {
    // Names only — never the values.
    console.error(
      `Missing credentials: ${missing.map((k) => (k === "apiKey" ? "APPWRITE_API_KEY" : "APPWRITE_PROJECT_ID")).join(", ")}. ` +
        `Set them in local.properties or the environment.`
    );
    process.exit(1);
  }
  return config;
}

// ── fixtures ────────────────────────────────────────────────────────────────

// Real-shaped Cricbuzz commentary. `verify:true` fact-checks the generated line against snippet1,
// so these have to carry actual facts — a lorem-ipsum fixture would fail verification for reasons
// that have nothing to do with load and would inflate the retry path.
const FIXTURE_BALLS = [
  { ball: "12.3", clean: "Bumrah to Root, no run, back of a length outside off, Root pushes it to cover", bowler: "Jasprit Bumrah", batsman: "Joe Root", score: "142/3 (12.3)" },
  { ball: "12.4", clean: "Bumrah to Root, 1 run, full on middle, worked away to deep square leg", bowler: "Jasprit Bumrah", batsman: "Joe Root", score: "143/3 (12.4)" },
  { ball: "12.5", clean: "Bumrah to Brook, FOUR, short and wide, Brook slashes it over point for a boundary", bowler: "Jasprit Bumrah", batsman: "Harry Brook", score: "147/3 (12.5)" },
  { ball: "12.6", clean: "Bumrah to Brook, SIX, on the pads, flicked over deep midwicket and into the crowd", bowler: "Jasprit Bumrah", batsman: "Harry Brook", score: "153/3 (12.6)" },
  { ball: "13.1", clean: "Siraj to Brook, out Caught by Jadeja!! Brook drives loosely and finds cover", bowler: "Mohammed Siraj", batsman: "Harry Brook", score: "153/4 (13.1)" },
  { ball: "13.2", clean: "Siraj to Stokes, wide, sprayed down the leg side, keeper dives to his left", bowler: "Mohammed Siraj", batsman: "Ben Stokes", score: "154/4 (13.1)" },
];

/** Byte-for-byte the prompt match-sync/src/main.js:645-665 sends. Drift here invalidates the test. */
function buildBody({ cacheKey, fixture }) {
  const { ball, clean, bowler, batsman, score } = fixture;
  const contextLine = [
    bowler && `Bowler: ${bowler}`,
    batsman && `Batsman: ${batsman}`,
    score && `Score: ${score}`,
  ]
    .filter(Boolean)
    .join(" | ");
  const prompt =
    `${ball}: ${clean}\n` +
    (contextLine ? `${contextLine}\n` : "") +
    `Write one vivid broadcast commentary line. Start with ${ball}: at the beginning.`;
  return {
    cacheKey,
    prompt,
    maxTokens: 100,
    temperature: 0.7,
    verify: true,
    snippet1: clean,
    snippet2: "",
    ball,
    bowler,
    batsman,
  };
}

// ── detection ───────────────────────────────────────────────────────────────

const HTTP_STATUS_RE = /HTTP (\d{3})/g;

/**
 * Pull provider HTTP failures out of one execution.
 *
 * The non-obvious part: a 200 hides them. `generate-commentary/src/main.js:182` returns only
 * {commentary, source, provider, verified} — `attempts` appears ONLY in the 503 body (:159). So a
 * groq 429 that cerebras recovers from is a clean 200 with no trace in the response. The function's
 * stderr is the ground truth: `llm.js:137` emits `error("groq HTTP 429")` on every provider
 * non-2xx, and Appwrite populates execution.logs/errors when the caller uses an API key.
 */
function extractProviderStatuses(execution, parsedBody) {
  const haystacks = [execution?.logs ?? "", execution?.errors ?? ""];
  if (Array.isArray(parsedBody?.attempts)) haystacks.push(parsedBody.attempts.join("; "));
  const counts = {};
  for (const text of haystacks) {
    for (const m of String(text).matchAll(HTTP_STATUS_RE)) {
      counts[m[1]] = (counts[m[1]] ?? 0) + 1;
    }
  }
  return counts;
}

// ── one row ─────────────────────────────────────────────────────────────────

async function fireRow({ functions, cacheKey, fixture, deviceId }) {
  const body = JSON.stringify(buildBody({ cacheKey, fixture }));
  const startedAt = Date.now();
  try {
    const execution = await functions.createExecution(
      FUNCTION_ID,
      body,
      false, // synchronous — we need responseBody, logs and errors back
      undefined,
      undefined,
      { "x-bouncer-device": deviceId }
    );
    const wallMs = Date.now() - startedAt;
    let parsed = null;
    try {
      parsed = JSON.parse(execution.responseBody || "{}");
    } catch {
      parsed = null;
    }
    return {
      cacheKey,
      deviceId,
      wallMs,
      serverMs: Math.round((execution.duration ?? 0) * 1000),
      status: execution.status,
      httpStatus: execution.responseStatusCode,
      source: parsed?.source ?? null,
      provider: parsed?.provider ?? null,
      verified: parsed?.verified ?? null,
      error: parsed?.error ?? null,
      attempts: parsed?.attempts ?? null,
      providerStatuses: extractProviderStatuses(execution, parsed),
      logsAvailable: Boolean((execution.logs ?? "").length || (execution.errors ?? "").length),
      wroteCacheRow: execution.responseStatusCode === 200 && parsed?.source === "generated",
    };
  } catch (e) {
    // A transport failure is a result, not a crash — it is exactly what a saturated function looks
    // like from the device, and swallowing it would understate the damage.
    return {
      cacheKey,
      deviceId,
      wallMs: Date.now() - startedAt,
      serverMs: null,
      status: "transport_error",
      httpStatus: e?.code ?? null,
      source: null,
      provider: null,
      verified: null,
      error: e?.message ?? String(e),
      attempts: null,
      providerStatuses: {},
      logsAvailable: false,
      wroteCacheRow: false,
    };
  }
}

// ── device id allocation ────────────────────────────────────────────────────

/**
 * Hand out a device id per chain, rotating before the per-device window can latch. Chains 0-3 fire
 * at every ramp level (30 calls) and would otherwise creep toward the 40/5min limit; a breach costs
 * an hour-long block and pollutes the shared offender file, which is not a result anybody wants.
 */
function makeDeviceAllocator(runId) {
  const used = new Map(); // chain -> { bucket, calls }
  return {
    take(chain, calls) {
      const state = used.get(chain) ?? { bucket: 0, calls: 0 };
      if (state.calls + calls > DEVICE_CALL_CEILING) {
        state.bucket += 1;
        state.calls = 0;
      }
      state.calls += calls;
      used.set(chain, state);
      return `loadtest-${runId}-m${chain}${state.bucket ? `b${state.bucket}` : ""}`;
    },
    counts() {
      return Object.fromEntries([...used].map(([chain, s]) => [`m${chain}`, s.calls]));
    },
  };
}

// ── stats ───────────────────────────────────────────────────────────────────

function percentile(sorted, p) {
  if (!sorted.length) return null;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[idx];
}

function summarize(level, rows) {
  const wall = rows.map((r) => r.wallMs).sort((a, b) => a - b);
  const server = rows.map((r) => r.serverMs).filter((n) => typeof n === "number" && n > 0).sort((a, b) => a - b);
  const tally = (key) =>
    rows.reduce((acc, r) => {
      const k = String(r[key] ?? "none");
      acc[k] = (acc[k] ?? 0) + 1;
      return acc;
    }, {});
  const providerStatuses = {};
  for (const r of rows) {
    for (const [code, n] of Object.entries(r.providerStatuses)) {
      providerStatuses[code] = (providerStatuses[code] ?? 0) + n;
    }
  }
  return {
    level,
    rows: rows.length,
    httpStatus: tally("httpStatus"),
    executionStatus: tally("status"),
    provider: tally("provider"),
    verified: tally("verified"),
    errorReason: tally("error"),
    providerHttpStatuses: providerStatuses,
    logsBlind: rows.filter((r) => !r.logsAvailable).length,
    wallMs: { p50: percentile(wall, 50), p95: percentile(wall, 95), max: wall.at(-1) ?? null },
    serverMs: { p50: percentile(server, 50), p95: percentile(server, 95), max: server.at(-1) ?? null },
  };
}

function printSummary(s) {
  console.log(`\n── level ${s.level} (${s.level} concurrent chains x ${ROWS_PER_CHAIN} serial rows) ──`);
  console.log(`  rows            ${s.rows}`);
  console.log(`  http status     ${JSON.stringify(s.httpStatus)}`);
  console.log(`  exec status     ${JSON.stringify(s.executionStatus)}`);
  console.log(`  provider used   ${JSON.stringify(s.provider)}`);
  console.log(`  verified        ${JSON.stringify(s.verified)}`);
  console.log(`  provider HTTP   ${JSON.stringify(s.providerHttpStatuses)}`);
  console.log(`  error reasons   ${JSON.stringify(s.errorReason)}`);
  console.log(`  wall ms         p50=${s.wallMs.p50} p95=${s.wallMs.p95} max=${s.wallMs.max}`);
  console.log(`  server ms       p50=${s.serverMs.p50} p95=${s.serverMs.p95} max=${s.serverMs.max}`);
  if (s.logsBlind) console.log(`  !! ${s.logsBlind} rows returned no logs/errors — 429 detection degraded for those`);
}

// ── cache row ids ───────────────────────────────────────────────────────────

/** Mirrors generate-commentary/src/main.js:120. A drift here leaves rows behind on cleanup. */
const docId = (s) => createHash("sha256").update(s).digest("hex").slice(0, 36);

const cacheKeyFor = (runId, level, chain, row) => `loadtest:${runId}:L${level}:m${chain}:r${row}`;

// ── phases ──────────────────────────────────────────────────────────────────

async function preflight({ functions, databases, config, runId }) {
  console.log("preflight:");
  const key = `loadtest:${runId}:preflight`;
  const fixture = FIXTURE_BALLS[0];
  const device = `loadtest-${runId}-preflight`;

  // Gate 1 — a real generation.
  const first = await fireRow({ functions, cacheKey: key, fixture, deviceId: device });
  console.log(`  1. generate      http=${first.httpStatus} source=${first.source} provider=${first.provider} verified=${first.verified} wall=${first.wallMs}ms`);
  // The baseline. A non-groq provider or a non-empty status map at concurrency 1 means the chain is
  // already falling through before any load has been applied — read the ramp against this, not
  // against an assumption that groq answers.
  console.log(`     provider HTTP  ${JSON.stringify(first.providerStatuses)}`);
  if (first.httpStatus !== 200 || first.source !== "generated") {
    console.error(`     FAIL — expected 200/generated. error=${first.error} attempts=${JSON.stringify(first.attempts)}`);
    return { ok: false, wrote: [] };
  }

  // Gate 2 — the cache is live, therefore unique keys in the ramp really do reach a provider.
  const second = await fireRow({ functions, cacheKey: key, fixture, deviceId: device });
  console.log(`  2. cache replay  http=${second.httpStatus} source=${second.source} wall=${second.wallMs}ms`);
  if (second.source !== "cache") {
    console.error("     FAIL — repeat of the same cacheKey did not hit the cache. A ramp reporting");
    console.error("     zero 429s would be unfalsifiable, because it might never reach a provider.");
    return { ok: false, wrote: [key] };
  }

  // Gate 3 — logs/errors are readable, so detect429 is not blind.
  const logsSeen = first.logsAvailable || second.logsAvailable;
  console.log(`  3. logs readable ${logsSeen ? "yes" : "NO"}`);
  if (!logsSeen) {
    console.warn("     WARNING — execution.logs/errors came back empty. The primary 429 detector is");
    console.warn("     blind; only 503 `attempts` and a non-groq `provider` remain. Any clean result");
    console.warn("     from this run must be reported with that caveat, not as a clean bill of health.");
  }

  await cleanup({ databases, config, keys: [key] });
  return { ok: true, logsAvailable: logsSeen, wrote: [] };
}

async function runChain({ functions, runId, level, chain, deviceId }) {
  const out = [];
  for (let row = 0; row < ROWS_PER_CHAIN; row += 1) {
    const fixture = FIXTURE_BALLS[row % FIXTURE_BALLS.length];
    out.push(await fireRow({ functions, cacheKey: cacheKeyFor(runId, level, chain, row), fixture, deviceId }));
  }
  return out;
}

async function ramp({ functions, runId }) {
  const allocator = makeDeviceAllocator(runId);
  const levels = [];
  const written = [];
  let stoppedAt = null;

  let firstLevel = true;
  for (const level of RAMP_LEVELS) {
    // Every provider limit that binds here is per MINUTE (groq 30 RPM, sambanova ~13, cerebras 5),
    // so back-to-back levels share one window and the ramp measures the wrong thing: on 2026-08-05
    // level 1 spent 20 of groq's 30 requests and level 2 started seconds later with 10 left, then
    // 429'd. That read as "the ceiling is 1 concurrent chain" when it was "the minute was already
    // two-thirds gone". Each level must start against a fresh window or the number is meaningless.
    if (!firstLevel && COOLDOWN_S > 0) {
      console.log(`\ncooling down ${COOLDOWN_S}s so the per-minute provider windows reset`);
      await new Promise((r) => setTimeout(r, COOLDOWN_S * 1000));
    }
    firstLevel = false;
    console.log(`\nfiring level ${level}: ${level} chains x ${ROWS_PER_CHAIN} rows = ${level * ROWS_PER_CHAIN} invocations`);
    const chains = Array.from({ length: level }, (_, chain) =>
      runChain({ functions, runId, level, chain, deviceId: allocator.take(chain, ROWS_PER_CHAIN) })
    );
    const rows = (await Promise.all(chains)).flat();
    written.push(...rows.filter((r) => r.wroteCacheRow).map((r) => r.cacheKey));

    const summary = summarize(level, rows);
    levels.push({ summary, rows });
    printSummary(summary);

    const rateLimited = summary.providerHttpStatuses["429"] ?? 0;
    if (rateLimited > 0) {
      stoppedAt = level;
      console.log(`\nSTOP — ${rateLimited} provider 429 response(s) at ${level} concurrent chains. Ramp halted.`);
      break;
    }
  }

  return { levels, written, stoppedAt, deviceCalls: allocator.counts() };
}

async function cleanup({ databases, config, keys }) {
  if (!keys.length) return { deleted: 0, failed: 0 };
  let deleted = 0;
  let failed = 0;
  for (const key of keys) {
    try {
      await databases.deleteDocument(config.dbId, config.colId, docId(key));
      deleted += 1;
    } catch (e) {
      // 404 means the row was never written (503 path) — not a failure.
      if (e?.code !== 404) failed += 1;
    }
  }
  return { deleted, failed };
}

function dryRun(runId) {
  const total = RAMP_LEVELS.reduce((n, l) => n + l * ROWS_PER_CHAIN, 0);
  console.log(`run id            ${runId}`);
  console.log(`function          ${FUNCTION_ID}`);
  console.log(`levels            ${RAMP_LEVELS.join(" -> ")} concurrent chains`);
  console.log(`rows per chain    ${ROWS_PER_CHAIN} (sequential, mirroring match-sync)`);
  console.log(`invocations       ${RAMP_LEVELS.map((l) => `${l}x${ROWS_PER_CHAIN}=${l * ROWS_PER_CHAIN}`).join(" + ")} = ${total}`);
  console.log(`device ids        loadtest-${runId}-m0 .. m${Math.max(...RAMP_LEVELS) - 1} (rotated above ${DEVICE_CALL_CEILING} calls)`);
  console.log(`quota            ${total} executions, ${total} document reads, <=${total} writes, <=${total} deletes`);
  console.log(`provider calls    ~${total * 2} typical, up to ${total * 11} worst case`);
  console.log(`stop condition    first provider HTTP 429 at the end of any level`);
}

// ── main ────────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const mode =
  args.find((a) => ["--dry-run", "--preflight", "--run", "--cleanup-only"].includes(a)) ?? "--dry-run";

// `--levels 1,2,4`. Ascending and de-duplicated: the whole point of a ramp is that the first level
// to fail is the ceiling, which is only true if every level below it already passed.
const levelsArg = args[args.indexOf("--levels") + 1];
const RAMP_LEVELS =
  args.includes("--levels") && levelsArg && !levelsArg.startsWith("--")
    ? [...new Set(levelsArg.split(",").map((n) => Number(n.trim())))].filter((n) => n > 0).sort((a, b) => a - b)
    : DEFAULT_RAMP_LEVELS;
if (!RAMP_LEVELS.length) {
  console.error("--levels needs at least one positive integer, e.g. --levels 1,2,4");
  process.exit(1);
}

// 75s clears every per-minute provider window with margin. `--cooldown 0` reproduces the old
// back-to-back behaviour, which is only useful for deliberately measuring a sustained burst.
const cooldownArg = args[args.indexOf("--cooldown") + 1];
const COOLDOWN_S =
  args.includes("--cooldown") && cooldownArg && !cooldownArg.startsWith("--") ? Number(cooldownArg) : 75;
const runId = args[args.indexOf("--cleanup-only") + 1] && mode === "--cleanup-only"
  ? args[args.indexOf("--cleanup-only") + 1]
  : `lt${Date.now().toString(36)}`;

if (mode === "--dry-run") {
  dryRun(runId);
  process.exit(0);
}

const config = loadConfig();
const client = new Client().setEndpoint(config.endpoint).setProject(config.projectId).setKey(config.apiKey);
const functions = new Functions(client);
const databases = new Databases(client);

if (mode === "--cleanup-only") {
  const keys = RAMP_LEVELS.flatMap((level) =>
    Array.from({ length: level }, (_, chain) =>
      Array.from({ length: ROWS_PER_CHAIN }, (_, row) => cacheKeyFor(runId, level, chain, row))
    ).flat()
  );
  const result = await cleanup({ databases, config, keys });
  console.log(`cleanup ${runId}: deleted ${result.deleted}, failed ${result.failed}`);
  process.exit(result.failed ? 1 : 0);
}

console.log(`run id ${runId}\n`);
const pre = await preflight({ functions, databases, config, runId });
if (!pre.ok) {
  console.error("\npreflight failed — not ramping.");
  process.exit(1);
}
if (mode === "--preflight") process.exit(0);

let result;
try {
  result = await ramp({ functions, runId });
} finally {
  const keys = result?.written ?? [];
  if (keys.length) {
    const c = await cleanup({ databases, config, keys });
    console.log(`\ncleanup: deleted ${c.deleted} of ${keys.length} cache rows (${c.failed} failed)`);
  }
}

console.log(`\ndevice call counts ${JSON.stringify(result.deviceCalls)}`);
console.log(
  result.stoppedAt
    ? `\nCEILING: provider 429 first seen at ${result.stoppedAt} concurrent chains.`
    : `\nNo ceiling found within realistic production shape (${Math.max(...RAMP_LEVELS)} concurrent x ${ROWS_PER_CHAIN} sequential rows).`
);

mkdirSync(join(HERE, "runs"), { recursive: true });
const outPath = join(HERE, "runs", `${runId}.json`);
writeFileSync(
  outPath,
  JSON.stringify(
    {
      runId,
      functionId: FUNCTION_ID,
      logsAvailable: pre.logsAvailable,
      stoppedAt: result.stoppedAt,
      deviceCalls: result.deviceCalls,
      levels: result.levels.map((l) => ({ summary: l.summary, rows: l.rows })),
    },
    null,
    2
  )
);
console.log(`\nfull record: ${outPath}`);
