#!/usr/bin/env node
//
// Records ESPNcricinfo's live win prediction, so the model's figures can be compared with it.
//
// Cricinfo's live-scores page carries every live match in its `__NEXT_DATA__` block, each with a
// `liveInningPredictions` field. It was null on all 23 matches on 2026-10-05 — Cricinfo appears to
// publish it only for some matches — so its populated shape is unknown, and this stores it RAW with
// enough of the match (teams, start, score, overs) to join it to a Cricbuzz match afterwards.
// One page per poll covers every match; no match page is fetched.
//
// Runs from a laptop on purpose: `hs-consumer-api` answers 403, and match-sync's every-minute run
// should not take on a second upstream. Gaps while the laptop sleeps are just fewer points.
//
// USAGE
//   caffeinate -i node appwrite/tools/rival-winprob-watch.mjs [--out path] [--every 300] [--once]
//
// Output: one JSON line per match per poll in which a prediction was present.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const argv = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = argv.indexOf(name);
  return i === -1 ? fallback : argv[i + 1];
};
const cacheDir = path.join(os.homedir(), "Library/Caches/bouncer-winprob-lab");
const out = flag("--out", path.join(cacheDir, "cricinfo-predictions.jsonl"));
const everyMs = Math.max(60, Number(flag("--every", "300"))) * 1000;
const once = argv.includes("--once");

const PAGE = "https://www.espncricinfo.com/live-cricket-score";
const HEADERS = {
  "user-agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
  "accept-language": "en",
};

/** Every distinct match object on the page: any node that owns `liveInningPredictions`. */
export function matchesIn(nextData) {
  const byId = new Map();
  const walk = (node) => {
    if (!node || typeof node !== "object") return;
    if (Array.isArray(node)) return node.forEach(walk);
    if (Object.hasOwn(node, "liveInningPredictions") && node.objectId != null) byId.set(node.objectId, node);
    for (const k of Object.keys(node)) walk(node[k]);
  };
  walk(nextData);
  return [...byId.values()];
}

export function rowFor(match, at) {
  return {
    at,
    cricinfoId: match.objectId,
    slug: match.slug ?? null,
    format: match.format ?? null,
    state: match.state ?? null,
    startTime: match.startTime ?? null,
    teams: (match.teams ?? []).map((t) => ({
      id: t.team?.objectId ?? null, name: t.team?.longName ?? null, score: t.score ?? null, scoreInfo: t.scoreInfo ?? null,
    })),
    liveInning: match.liveInning ?? null,
    liveOvers: match.liveOvers ?? null,
    statusText: match.statusText ?? null,
    winnerTeamId: match.winnerTeamId ?? null,
    predictions: match.liveInningPredictions,
  };
}

async function poll() {
  const res = await fetch(PAGE, { headers: HEADERS });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const html = await res.text();
  const block = html.match(/<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
  if (!block) throw new Error("no __NEXT_DATA__ block — the page changed shape");
  const matches = matchesIn(JSON.parse(block[1]));
  const at = Date.now();
  const rows = matches.filter((m) => m.liveInningPredictions != null).map((m) => rowFor(m, at));
  if (rows.length) fs.appendFileSync(out, rows.map((r) => JSON.stringify(r)).join("\n") + "\n");
  const live = matches.filter((m) => m.state === "LIVE").length;
  console.log(`${new Date(at).toISOString()}  ${matches.length} matches, ${live} live, ${rows.length} with a prediction`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  fs.mkdirSync(path.dirname(out), { recursive: true });
  console.log(`writing to ${out}`);
  for (;;) {
    await poll().catch((e) => console.error(`${new Date().toISOString()}  poll failed: ${e.message}`));
    if (once) break;
    await new Promise((r) => setTimeout(r, everyMs));
  }
}
