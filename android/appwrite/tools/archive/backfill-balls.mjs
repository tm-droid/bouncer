#!/usr/bin/env node
//
// Ball-by-ball backfill: Cricbuzz completed-match commentary -> STRUCTURED deliveries in the archive
// site's `m/<id>.json` (`balls`). No commentary text is stored anywhere (see src/balls.js).
//
// MAC OVERNIGHT (site = a gh-pages checkout of tm-droid/bouncer-archive; run from appwrite/tools/archive):
//   git -C ~/bouncer-archive-site pull --rebase -q origin gh-pages
//   caffeinate -i node backfill-balls.mjs --site ~/bouncer-archive-site --max-requests 3000 --delay-ms 3000
//   git -C ~/bouncer-archive-site add -A && git -C ~/bouncer-archive-site commit -m "Balls $(date -u +%FT%H%MZ)" \
//     && git -C ~/bouncer-archive-site pull --rebase -q origin gh-pages && git -C ~/bouncer-archive-site push origin gh-pages
// (Exit 2 = Cricbuzz said 403/429: state is saved, commit what exists, stop and wait hours.)
// Never run it from two places at once, and never while the U5b crawler is running: one IP.
//
// FLAGS  --site <dir>  --max-requests N (400)  --delay-ms N (3000)  --cache <dir>  --probe
//   --probe     3 requests against one known match (21646, innings 2); prints statuses; exit 0 on 200s, 2 on 403/429
//   --cache     where in-progress records are kept (default: inside <site>/balls-state.json)
// State: <site>/balls-state.json { v, matches: { id: { done?, errs?, inn: { "1": {cursor, done, recs?} } } } }

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { CricbuzzBlockedError, ballsBlock, commentaryUrl, crawlInnings } from "./src/balls.js";

export const FROM_MS = Date.UTC(2016, 11, 1);
const UAS = [
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const readJson = (f, fallback) => {
  try {
    return JSON.parse(fs.readFileSync(f, "utf8"));
  } catch {
    return fallback;
  }
};
function writeAtomic(file, text) {
  fs.writeFileSync(`${file}.tmp`, text);
  fs.renameSync(`${file}.tmp`, file);
}

/** Whether a match file should get balls. `state` is its balls-state entry. */
export function isEligible(file, state) {
  if (!file || file.balls || state?.done || (state?.errs ?? 0) >= 3) return false;
  if (!(file.scorecard?.innings?.length > 0)) return false;
  if (!(Number(file.meta?.startDate) >= FROM_MS)) return false;
  if (file.source === "cricbuzz-backfill") return true;
  const afghan = (file.meta?.teams ?? []).some((t) => String(t.teamId) === "96" || /afghanistan/i.test(t.name ?? ""));
  return afghan && !(file.rows?.length > 0);
}

/** Eligible ids, oldest match first. */
export function pending(site, matches) {
  const dir = path.join(site, "m");
  const out = [];
  for (const f of fs.existsSync(dir) ? fs.readdirSync(dir) : []) {
    if (!f.endsWith(".json")) continue;
    const file = readJson(path.join(dir, f), null);
    if (isEligible(file, matches[file?.id])) out.push([String(file.id), Number(file.meta.startDate)]);
  }
  return out.sort((a, b) => a[1] - b[1]).map(([id]) => id);
}

export function makeFetchPage({ delayMs, fetchImpl = fetch, counter }) {
  let n = 0;
  return async function fetchUrl(url) {
    if (counter.sent > 0) await sleep(delayMs);
    counter.sent++;
    let resp;
    for (let attempt = 1; ; attempt++) {
      try {
        resp = await fetchImpl(url, {
          headers: { "User-Agent": UAS[n++ % UAS.length], Accept: "application/json, text/plain, */*", "Accept-Language": "en-US,en;q=0.9", Referer: "https://www.cricbuzz.com/" },
          signal: AbortSignal.timeout(15_000),
        });
        break;
      } catch (e) {
        if (attempt >= 2) throw e;
        await sleep(delayMs);
      }
    }
    if (resp.status === 403 || resp.status === 429) throw new CricbuzzBlockedError(resp.status, url);
    return resp;
  };
}

const itemsOf = async (resp) => {
  if (resp.status === 404) return [];
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  const text = await resp.text();
  if (!text.trim()) return [];
  const j = JSON.parse(text);
  return Array.isArray(j) ? j : [];
};

/** The run. Returns {requests, matchesDone, remaining, blocked}. State is saved on every exit path. */
export async function run({ site, maxRequests = 400, delayMs = 3000, cache = null, fetchImpl = fetch, nowMs = Date.now(), log = console.log }) {
  const stateFile = path.join(site, "balls-state.json");
  const state = readJson(stateFile, { v: 1, matches: {} });
  state.matches ??= {};
  const counter = { sent: 0 };
  const fetchUrl = makeFetchPage({ delayMs, fetchImpl, counter });
  const cacheFile = (id, n) => path.join(cache, `${id}-${n}.json`);
  const save = () => {
    if (cache) fs.mkdirSync(cache, { recursive: true });
    const slim = structuredClone(state);
    if (cache) {
      for (const [id, m] of Object.entries(state.matches))
        for (const [n, st] of Object.entries(m.inn ?? {})) {
          if (st.recs && !st.done) writeAtomic(cacheFile(id, n), JSON.stringify(st.recs));
          if (cache) delete slim.matches[id].inn[n].recs;
        }
    }
    writeAtomic(stateFile, JSON.stringify(slim));
  };
  let blocked = false;
  let finished = 0;
  const ids = pending(site, state.matches);
  try {
    for (const id of ids) {
      const file = readJson(path.join(site, "m", `${id}.json`), null);
      const cards = file.scorecard.innings;
      const m = (state.matches[id] ??= { inn: {} });
      let budgetHit = false;
      for (let n = 1; n <= cards.length; n++) {
        const st = (m.inn[n] ??= { cursor: nowMs + 3_600_000, done: 0 });
        if (cache && !st.recs && fs.existsSync(cacheFile(id, n))) st.recs = readJson(cacheFile(id, n), []);
        try {
          const why = await crawlInnings({
            st,
            innings: n,
            canRequest: () => counter.sent < maxRequests,
            fetchPage: async (cursor) => itemsOf(await fetchUrl(commentaryUrl(id, n, cursor))),
          });
          if (why === "budget") {
            budgetHit = true;
            break;
          }
        } catch (e) {
          if (e instanceof CricbuzzBlockedError) throw e;
          m.errs = (m.errs ?? 0) + 1;
          log(`match ${id} innings ${n}: ${e.message}`);
          budgetHit = true;
          break;
        }
      }
      if (budgetHit) {
        if (counter.sent >= maxRequests) break;
        continue;
      }
      const inns = cards.map((_, i) => ({ id: i + 1, recs: m.inn[i + 1].recs ?? [], cp: m.inn[i + 1].cp, os: m.inn[i + 1].os }));
      if (inns.every((x) => x.recs.length === 0)) {
        m.done = 1; // Cricbuzz holds no ball data for this match; do not ask again.
        log(`match ${id}: no ball data`);
      } else {
        const { balls, problems } = ballsBlock(inns, cards);
        const target = path.join(site, "m", `${id}.json`);
        const fresh = readJson(target, null); // read-modify-write: only `balls` changes
        if (fresh && !fresh.balls) writeAtomic(target, JSON.stringify({ ...fresh, balls }));
        m.done = 1;
        finished++;
        log(`match ${id}: ${balls.inn.reduce((s, i) => s + i.b.length, 0)} deliveries${problems.length ? ` PARTIAL ${problems.join(" | ")}` : ""}`);
      }
      delete m.inn;
      if (cache) for (let n = 1; n <= cards.length; n++) fs.rmSync(cacheFile(id, n), { force: true });
      save();
    }
  } catch (e) {
    if (!(e instanceof CricbuzzBlockedError)) {
      save();
      throw e;
    }
    blocked = true;
    log(`BLOCKED: ${e.message}`);
  }
  save();
  const remaining = pending(site, state.matches).length;
  return { requests: counter.sent, finished, remaining, blocked };
}

/** 3 requests against one known match. */
export async function probe({ delayMs = 3000, fetchImpl = fetch, log = console.log }) {
  const counter = { sent: 0 };
  const fetchUrl = makeFetchPage({ delayMs, fetchImpl, counter });
  let cursor = 1550766000000;
  try {
    for (let i = 0; i < 3; i++) {
      const resp = await fetchUrl(commentaryUrl(21646, 2, cursor));
      const items = await itemsOf(resp);
      log(`probe ${i + 1}: HTTP ${resp.status}, ${items.length} items`);
      const stamps = items.map((x) => Number(x.timestamp)).filter(Number.isFinite);
      if (!stamps.length) break;
      cursor = Math.min(...stamps);
    }
  } catch (e) {
    if (e instanceof CricbuzzBlockedError) {
      log(`probe BLOCKED: HTTP ${e.status}`);
      return 2;
    }
    log(`probe failed: ${e.message}`);
    return 1;
  }
  return 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const opt = (name, d) => (args.includes(name) ? args[args.indexOf(name) + 1] : d);
  const delayMs = Number(opt("--delay-ms", 3000));
  if (args.includes("--probe")) process.exit(await probe({ delayMs }));
  const site = opt("--site");
  if (!site) {
    console.error("usage: node backfill-balls.mjs --site <dir> [--max-requests N] [--delay-ms N] [--cache <dir>] | --probe");
    process.exit(1);
  }
  const r = await run({ site: path.resolve(site), maxRequests: Number(opt("--max-requests", 400)), delayMs, cache: opt("--cache") ? path.resolve(opt("--cache")) : null });
  console.log(`requests=${r.requests} finished=${r.finished} remaining=${r.remaining}${r.blocked ? " BLOCKED" : ""}`);
  if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT, `remaining=${r.remaining}\nblocked=${r.blocked ? 1 : 0}\n`);
  process.exit(r.blocked ? 2 : 0);
}
