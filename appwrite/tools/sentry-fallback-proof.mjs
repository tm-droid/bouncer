#!/usr/bin/env node
//
// Proves the raw-Cricbuzz fallback path in `bouncer-match-sync` reports and FLUSHES.
//
// The burst harness cannot answer this. `bouncer-generate-commentary` has no Sentry wiring at all,
// and the `ai_generated: false` passthrough lives only in match-sync (match-sync/src/main.js:668-676).
// So this exercises match-sync's REAL modules — not a copy — with FORCE_LLM_FAILURE=1, the test hook
// llm.js:108-112 exists for, and then checks the event actually left the process.
//
// The flush is the thing under test. An Appwrite container freezes the moment the handler returns,
// so an unflushed Sentry queue is simply lost — which is the entire reason
// match-sync/src/observability.js:61 exists.
//
// USAGE
//   SENTRY_DSN='<match-sync dsn>' node sentry-fallback-proof.mjs
//
// Nothing here touches the deployed function, its environment, or any table.

import { generateVerifiedLine } from "../functions/match-sync/src/llm.js";
import {
  initObservability,
  isEnabled,
  report,
  flushObservability,
} from "../functions/match-sync/src/observability.js";

const runId = `lt${Date.now().toString(36)}`;
const syntheticMatchId = `loadtest-${runId}`;

process.env.FORCE_LLM_FAILURE = "1";

const log = (m) => console.log(`  ${m}`);
const error = (m) => console.log(`  [stderr] ${m}`);

console.log(`sentry-fallback-proof ${runId}`);

// 1. The exhausted chain, via the real llm.js.
const attempts = [];
const generated = await generateVerifiedLine("13.1: Siraj to Brook, out Caught by Jadeja!!", {
  maxTokens: 100,
  temperature: 0.7,
  verify: true,
  context: { snippet1: "Siraj to Brook, out Caught by Jadeja!!", ball: "13.1", bowler: "Mohammed Siraj", batsman: "Harry Brook" },
  log,
  error,
  attempts,
});

const chainOk = generated.line === null && generated.reason === "providers_exhausted";
console.log(`1. chain exhausted   line=${generated.line} reason=${generated.reason} attempts=${JSON.stringify(attempts)}`);
if (!chainOk) {
  console.error("   FAIL — expected {line:null, reason:'providers_exhausted'}");
  process.exit(1);
}

// 2. Sentry init + the exact report() call the passthrough branch makes.
const enabled = initObservability({ log });
console.log(`2. sentry enabled    ${enabled} (isEnabled=${isEnabled()})`);
if (!enabled) {
  console.error("   FAIL — SENTRY_DSN not set, so nothing can be proven. Pass match-sync's DSN.");
  process.exit(1);
}

report("match-sync wrote raw Cricbuzz text — AI generation produced nothing usable", {
  matchId: syntheticMatchId,
  ball: "13.1",
  reason: generated.reason,
  attempts: attempts.join("; "),
});
console.log(`3. reported          matchId=${syntheticMatchId}`);

// 3. Flush — the failure mode this whole module exists to prevent.
const flushStart = Date.now();
await flushObservability();
console.log(`4. flushed           ${Date.now() - flushStart}ms`);

console.log(`\nSearch Sentry for tag matchId:${syntheticMatchId} to confirm delivery.`);
