#!/usr/bin/env node
//
// One-off recovery for highlight clips that `bouncer-highlights-sync` never captured.
//
// The function's rolling back-window is BACKFILL_DAYS (3) deep, which is sized for publication lag,
// not for repairing history. Anything older than that is unreachable by the cron no matter how long
// it runs — the clips for 2026-07-29 and 2026-08-01 were still available upstream on 2026-08-06 and
// the function would never have asked for them again. This asks.
//
// USAGE
//   node backfill-highlights.mjs 2026-07-26 2026-08-02          # write the gap
//   node backfill-highlights.mjs 2026-07-26 2026-08-02 --dry-run # list what would be written
//
// Costs one Highlightly request per date, against the same 100-per-window plan the cron uses —
// check the printed `remaining` before running a wide range. Writes are idempotent: the row id is
// `hl-<highlight id>`, so a re-run updates rather than duplicates.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const argv = process.argv.slice(2);
const dryRun = argv.includes("--dry-run");
const [from, to] = argv.filter((a) => !a.startsWith("--"));
if (!from || !to) {
  console.error("usage: node backfill-highlights.mjs <from YYYY-MM-DD> <to YYYY-MM-DD> [--dry-run]");
  process.exit(1);
}

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const props = Object.fromEntries(
  fs
    .readFileSync(path.join(repoRoot, "local.properties"), "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("=") && !l.trim().startsWith("#"))
    .map((l) => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()])
);

const endpoint = (process.env.APPWRITE_ENDPOINT || props.APPWRITE_ENDPOINT || "").replace(/\/$/, "");
const projectId = process.env.APPWRITE_PROJECT_ID || props.APPWRITE_PROJECT_ID;
const apiKey = process.env.APPWRITE_API_KEY || props.APPWRITE_API_KEY;
// The cron reads this from the function's own env; locally it is whichever RapidAPI key is
// subscribed to cricket-highlights-api.
const rapidKey = process.env.HIGHLIGHTLY_API_KEY || props.HIGHLIGHTLY_API_KEY || props.RAPIDAPI_KEY;
if (!endpoint || !projectId || !apiKey || !rapidKey) {
  console.error("missing APPWRITE_ENDPOINT / APPWRITE_PROJECT_ID / APPWRITE_API_KEY / RAPIDAPI_KEY");
  process.exit(1);
}

const DB = "bouncer-db";
const TABLE = "highlights";
const HOST = "cricket-highlights-api.p.rapidapi.com";
const aw = { "X-Appwrite-Project": projectId, "X-Appwrite-Key": apiKey, "Content-Type": "application/json" };

const DAY = 86_400_000;
const dates = [];
for (let t = Date.parse(`${from}T00:00:00Z`); t <= Date.parse(`${to}T00:00:00Z`); t += DAY) {
  dates.push(new Date(t).toISOString().slice(0, 10));
}
console.log(`${dates.length} date(s): ${dates[0]} .. ${dates[dates.length - 1]}${dryRun ? "  (dry run)" : ""}`);

// Mirrors buildPayload in src/main.js. Kept in step by hand: this is a recovery tool run by a human
// against a specific gap, not a second production writer, so importing the function's module (and
// its node-appwrite dependency) would cost more than it saves.
const buildPayload = (h, now) => ({
  highlight_id: String(h.id),
  type: h.type ?? "UNVERIFIED",
  img_url: h.imgUrl ?? "",
  title: h.title ?? "",
  url: h.url ?? "",
  embed_url: h.embedUrl?.trim() || null,
  channel: h.channel ?? "",
  source: h.source ?? "",
  match_home_team: h.match?.homeTeam?.name ?? "",
  match_away_team: h.match?.awayTeam?.name ?? "",
  match_home_abbr: h.match?.homeTeam?.abbreviation ?? "",
  match_away_abbr: h.match?.awayTeam?.abbreviation ?? "",
  match_league_name: h.match?.league?.name ?? "",
  match_league_season: h.match?.league?.season ?? 0,
  match_start_date: h.match?.startDate ?? "",
  match_end_date: h.match?.endDate ?? "",
  raw_match_id: h.match?.id ?? "",
  fetched_at: now,
});

const byId = new Map();
for (const date of dates) {
  const res = await fetch(`https://${HOST}/highlights?date=${date}&limit=40`, {
    headers: { "x-rapidapi-key": rapidKey, "x-rapidapi-host": HOST },
  });
  const remaining = res.headers.get("x-ratelimit-requests-remaining") ?? "?";
  if (!res.ok) {
    console.error(`  ${date}: HTTP ${res.status} (remaining=${remaining}) — stopping`);
    break;
  }
  const body = await res.json();
  const items = Array.isArray(body?.data) ? body.data : [];
  for (const h of items) if (!byId.has(h.id)) byId.set(h.id, h);
  console.log(`  ${date}: ${items.length} item(s), remaining=${remaining}`);
}

console.log(`${byId.size} unique clip(s) upstream across the range`);

const now = Date.now();
let created = 0;
let updated = 0;
let failed = 0;
for (const h of byId.values()) {
  const docId = `hl-${h.id}`;
  const existing = await fetch(`${endpoint}/databases/${DB}/collections/${TABLE}/documents/${docId}`, { headers: aw });
  const isNew = existing.status === 404;
  if (dryRun) {
    console.log(`  ${isNew ? "WOULD CREATE" : "exists     "} ${docId}  ${(h.title ?? "").slice(0, 60)}`);
    continue;
  }
  if (!isNew) {
    updated++;
    continue; // clip metadata is effectively immutable once published; do not churn existing rows
  }
  const res = await fetch(`${endpoint}/databases/${DB}/collections/${TABLE}/documents`, {
    method: "POST",
    headers: aw,
    body: JSON.stringify({ documentId: docId, data: buildPayload(h, now), permissions: ['read("any")'] }),
  });
  if (res.ok) {
    created++;
    console.log(`  created ${docId}  ${(h.title ?? "").slice(0, 60)}`);
  } else {
    failed++;
    console.error(`  FAILED ${docId}: HTTP ${res.status} ${(await res.text()).slice(0, 200)}`);
  }
}
console.log(dryRun ? "dry run — nothing written" : `created ${created}, already present ${updated}, failed ${failed}`);
process.exitCode = failed ? 1 : 0;
