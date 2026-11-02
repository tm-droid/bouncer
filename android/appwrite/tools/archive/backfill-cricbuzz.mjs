#!/usr/bin/env node
//
// One-off backfill: Afghanistan men's internationals (Test/ODI/T20I) and the Afghanistan Premier
// League as STRUCTURED SCORECARDS in the match archive (`.claude/rules/archive-data.md`), because
// Cricsheet withholds every Afghanistan match. Scorecards only: `rows` stays empty, and Cricbuzz's
// raw text (commentary, reports, HTML) is never written anywhere that reaches the site.
//
// USAGE (run from this repo; it imports match-sync's parsers, so it is not published with the archiver)
//   node backfill-cricbuzz.mjs <siteDir> [--apply-only]
//
// Phase 1 (network): enumerate series -> matches, fetch each match's live page and scorecard page
// into a resumable cache (never inside the site), parse them, keep the built match file next to it.
// Phase 2 (offline): add the built files to `siteDir` (a gh-pages checkout): never overwriting a match
// file that exists or one Bouncer is following, then index, years and manifest.
//
// Politeness: one request per 2.5 s, a hard request budget, and the first 403/429 stops the run.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { MATCH_ID, SCHEMA_VERSION, mergeIndex, monthOf, yearsOf } from "./src/archive.js";
import {
  LEAGUE_SERIES,
  backfillIndexRow,
  cardProblems,
  isAfghanistanMatch,
  isFinished,
  isLeagueMatch,
  matchFile,
  matchHeaderOf,
  matchesOfSeries,
  metaOf,
  officialsOf,
  seriesOfArchive,
} from "./src/backfill.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const sync = path.resolve(here, "../../functions/match-sync/src");
const { fetchHtml, liveScoresUrl, scorecardUrl, parseScorecard, parseMatchMeta, parseLiveDetail } = await import(path.join(sync, "cricbuzz.js"));
const { forEachNextFlightPayload } = await import(path.join(sync, "nextflight.js"));

const siteDir = process.argv[2];
const applyOnly = process.argv.includes("--apply-only");
if (!siteDir) {
  console.error("usage: node backfill-cricbuzz.mjs <siteDir> [--apply-only]");
  process.exit(1);
}
const site = path.resolve(siteDir);
const CACHE = process.env.BACKFILL_CACHE ?? "/private/tmp/claude-503/u5b-cache";
const GAP_MS = 2500;
const BUDGET = Number(process.env.BACKFILL_BUDGET ?? 900);
const FIRST_YEAR = 2007;
const LAST_YEAR = new Date().getUTCFullYear();

// Series that carry Afghanistan matches without having "Afghanistan" in the name: multi-team events
// found by hand in the probe (U5a), plus ones the probe skipped. A series page costs one request and
// the team-96 filter below drops everything else on it.
const KNOWN_SERIES = (process.env.BACKFILL_KNOWN_SERIES ?? "").split(/\s+/).filter(Boolean);
const EXTRA_SERIES = ["2388", "10642", "10906", "8511", "13047", "12563", "2327", "2073", "2132", "2072", "2431", "13197"];
// Matches whose series page lists no matches (a different layout): their ids come from the team page.
const EXTRA_MATCHES = ["172680", "172681", "172691", "171030", "170993", "170973"];
const TEAM_RESULTS = "https://www.cricbuzz.com/cricket-team/afghanistan/96/results";

fs.mkdirSync(path.join(CACHE, "pages"), { recursive: true });
fs.mkdirSync(path.join(CACHE, "built"), { recursive: true });
const pageFile = (key) => path.join(CACHE, "pages", `${key}.html`);
const readJson = (file, fallback) => {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (e) {
    if (e.code === "ENOENT") return fallback;
    throw e;
  }
};
const siteRel = (rel) => {
  const f = path.resolve(site, rel);
  if (!f.startsWith(site + path.sep)) throw new Error(`refusing to write outside ${site}: ${rel}`);
  return f;
};
const payloadsOf = (html) => {
  const out = [];
  forEachNextFlightPayload(html, (p) => out.push(p));
  return out;
};

// ---------------------------------------------------------------- phase 1: network
const counter = path.join(CACHE, "requests.json");
const stats = readJson(counter, { total: 0 });
let sessionRequests = 0;
let lastAt = 0;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class Stop extends Error {}

/** The page for `key` from the cache, or fetched (politely) and cached. Null for a 404. */
async function page(key, url) {
  const file = pageFile(key);
  if (fs.existsSync(file)) return fs.readFileSync(file, "utf8");
  if (fs.existsSync(`${file}.404`)) return null;
  if (stats.total >= BUDGET) throw new Stop(`request budget ${BUDGET} spent`);
  const wait = lastAt + GAP_MS - Date.now();
  if (wait > 0) await sleep(wait);
  lastAt = Date.now();
  stats.total++;
  sessionRequests++;
  fs.writeFileSync(counter, JSON.stringify(stats));
  try {
    const html = await fetchHtml(url, 25_000);
    fs.writeFileSync(file, html);
    return html;
  } catch (e) {
    if (e.blocked || e.status === 403 || e.status === 429) throw new Stop(`BLOCKED: HTTP ${e.status} on ${url}`);
    if (e.status === 404) {
      fs.writeFileSync(`${file}.404`, "");
      return null;
    }
    throw e;
  }
}

async function enumerate() {
  const series = new Map(); // id -> name
  for (const id of [...KNOWN_SERIES, ...EXTRA_SERIES]) series.set(id, "");
  for (let y = FIRST_YEAR; y <= LAST_YEAR; y++) {
    const html = await page(`archive-${y}`, `https://www.cricbuzz.com/cricket-scorecard-archives/${y}`);
    if (!html) continue;
    for (const s of seriesOfArchive(payloadsOf(html))) {
      if (/afghan/i.test(s.name) || LEAGUE_SERIES.test(s.name)) series.set(s.id, s.name);
    }
  }
  const infos = new Map(); // matchId -> { mi, seriesName }
  for (const [sid, name] of series) {
    const html = await page(`series-${sid}`, `https://www.cricbuzz.com/cricket-series/${sid}/x/matches`);
    if (!html) continue;
    for (const mi of matchesOfSeries(payloadsOf(html))) {
      const seriesName = mi.seriesName ?? name;
      if (isAfghanistanMatch(mi) || isLeagueMatch(mi, seriesName)) infos.set(String(mi.matchId), { mi, seriesName });
    }
  }
  // The team page holds the latest ten, which covers matches played since the series pages were cached.
  const results = await page("team-96-results", TEAM_RESULTS);
  const latest = new Set(EXTRA_MATCHES);
  if (results) {
    const walk = (n) => {
      if (!n || typeof n !== "object") return;
      if (Array.isArray(n)) return n.forEach(walk);
      if (n.matchInfo && MATCH_ID.test(String(n.matchInfo.matchId)) && isAfghanistanMatch(n.matchInfo)) latest.add(String(n.matchInfo.matchId));
      Object.values(n).forEach(walk);
    };
    payloadsOf(results).forEach(walk);
  }
  for (const id of latest) if (!infos.has(id)) infos.set(id, { mi: null, seriesName: null });
  return { series, infos };
}

function build(id, info, liveHtml, cardHtml, nowMs) {
  const livePayloads = payloadsOf(liveHtml);
  const header = matchHeaderOf(livePayloads);
  const detail = parseLiveDetail(liveHtml);
  const meta = metaOf({ id, header, info: info.mi, detail });
  if (!meta.seriesName && info.seriesName) meta.seriesName = info.seriesName;
  if (!isFinished(meta)) return { skip: `not finished (${meta.state}: ${meta.status})` };
  const scorecard = parseScorecard(cardHtml);
  const mm = parseMatchMeta(liveHtml);
  const file = matchFile({
    id,
    nowMs,
    meta,
    detail,
    scorecard,
    officials: officialsOf(header),
    venue: mm.venue,
    toss: mm.toss,
    dayNight: mm.dayNight,
  });
  return { file, problems: cardProblems(scorecard), venue: mm.venue };
}

async function phase1() {
  const { series, infos } = await enumerate();
  const ids = [...infos.keys()].sort((a, b) => Number(a) - Number(b));
  const report = readJson(path.join(CACHE, "report.json"), { built: {}, skipped: {}, failed: {} });
  let siteState = readJson(path.join(site, "state.json"), {});
  const followed = new Set([...Object.keys(siteState.pending ?? {}), ...Object.keys(siteState.seen ?? {})]);
  console.log(`enumerated ${ids.length} matches from ${series.size} series; requests so far ${stats.total}`);
  for (const id of ids) {
    if (fs.existsSync(path.join(CACHE, "built", `${id}.json`)) || report.skipped[id]) continue;
    if (fs.existsSync(siteRel(`m/${id}.json`)) || followed.has(id)) {
      report.skipped[id] = "already archived or followed by Bouncer";
      continue;
    }
    try {
      const live = await page(`live-${id}`, liveScoresUrl(id));
      const card = live && (await page(`card-${id}`, scorecardUrl(id)));
      if (!live || !card) throw new Error("page is 404");
      const out = build(id, infos.get(id), live, card, Date.now());
      if (out.skip) report.skipped[id] = out.skip;
      else {
        fs.writeFileSync(path.join(CACHE, "built", `${id}.json`), JSON.stringify({ file: out.file, venue: out.venue, problems: out.problems }));
        delete report.failed[id];
        if (out.problems.length) console.log(`  ${id}: ${out.problems.join("; ")}`);
      }
    } catch (e) {
      if (e instanceof Stop) {
        fs.writeFileSync(path.join(CACHE, "report.json"), JSON.stringify(report));
        throw e;
      }
      report.failed[id] = String(e.message).slice(0, 200);
    }
    fs.writeFileSync(path.join(CACHE, "report.json"), JSON.stringify(report));
  }
  console.log(`built ${fs.readdirSync(path.join(CACHE, "built")).length}, skipped ${Object.keys(report.skipped).length}, failed ${Object.keys(report.failed).length}; requests ${stats.total} (${sessionRequests} this run)`);
}

// ---------------------------------------------------------------- phase 2: the site
function phase2() {
  const state = readJson(path.join(site, "state.json"), {});
  const followed = new Set([...Object.keys(state.pending ?? {}), ...Object.keys(state.seen ?? {})]);
  const months = {};
  const monthFile = (m) => (months[m] ??= readJson(siteRel(`idx/m/${m}.json`), []));
  let written = 0;
  let kept = 0;
  const problems = [];
  for (const f of fs.readdirSync(path.join(CACHE, "built")).sort()) {
    const { file, venue, problems: p } = readJson(path.join(CACHE, "built", f), null);
    const id = file.id;
    if (!MATCH_ID.test(id) || file.v !== SCHEMA_VERSION) throw new Error(`bad built file ${f}`);
    // Bouncer's own copy, live-followed, is richer: never replace it.
    if (fs.existsSync(siteRel(`m/${id}.json`)) || followed.has(id)) {
      kept++;
      continue;
    }
    if (p.length) problems.push(`${id} ${p.join("; ")}`);
    fs.mkdirSync(path.dirname(siteRel(`m/${id}.json`)), { recursive: true });
    fs.writeFileSync(siteRel(`m/${id}.json`), JSON.stringify(file), { flag: "wx" });
    const row = backfillIndexRow(file.meta, venue);
    const month = monthOf(row);
    months[month] = mergeIndex(monthFile(month), [row]);
    written++;
  }
  for (const [month, rows] of Object.entries(months)) fs.writeFileSync(siteRel(`idx/m/${month}.json`), JSON.stringify(rows));

  // years.json and the manifest, as archive.mjs writes them. The watermark and the billing-cycle
  // count belong to the archiver and are carried over untouched.
  const all = {};
  const idxDir = siteRel("idx/m/x").replace(/x$/, "");
  for (const f of fs.readdirSync(idxDir)) if (f.endsWith(".json")) all[f.slice(0, -5)] = readJson(path.join(idxDir, f), []);
  fs.writeFileSync(siteRel("idx/years.json"), JSON.stringify(yearsOf(all)));
  let bytes = 0;
  let files = 0;
  (function walk(dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (e.name === ".git") continue;
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p);
      else {
        bytes += fs.statSync(p).size;
        files++;
      }
    }
  })(site);
  const old = readJson(siteRel("manifest.json"), {});
  fs.writeFileSync(
    siteRel("manifest.json"),
    JSON.stringify({
      ...old,
      v: SCHEMA_VERSION,
      updated: new Date().toISOString(),
      matches: Object.values(all).reduce((n, r) => n + r.length, 0),
      files,
      bytes,
    })
  );
  console.log(`site: ${written} written, ${kept} kept (already archived), ${problems.length} cards whose totals do not add up`);
  for (const p of problems) console.log("  " + p);
}

try {
  if (!applyOnly) await phase1();
} catch (e) {
  if (e instanceof Stop) {
    console.error(`STOPPED: ${e.message}`);
    process.exit(3);
  }
  throw e;
}
phase2();
