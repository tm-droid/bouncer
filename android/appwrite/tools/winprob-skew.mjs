#!/usr/bin/env node
// Train/serve skew: joins archived live matches (m/*.json with `context` and `evalLog`) to Cricsheet
// by date (+-1 day), gender, format and team, then diffs the context terms serving read against the
// terms the trainer would have built for the same match (as of the match date, and as of the
// published model's `asOf` to isolate the model-date lag).
//
//   node --max-old-space-size=3072 appwrite/tools/winprob-skew.mjs [--archive <repo dir>] [--zip <all_json.zip>] [--dump <out.json>]
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const trainerDir = path.join(here, "..", "functions", "train-win-probability");
const require = createRequire(path.join(trainerDir, "package.json"));
const AdmZip = require("adm-zip");
const { AsOfState, sideContextOf } = await import(path.join(trainerDir, "src", "asOfState.js"));
const { richSkeletonOf } = await import(path.join(trainerDir, "src", "corpus.js"));
const { unpackDelivery } = await import(path.join(trainerDir, "src", "training.js"));
const { squash, venueName } = await import(path.join(trainerDir, "src", "contextmath.js"));
const { sideContextFor, findGround } = await import(path.join(here, "..", "functions", "match-sync", "src", "context.js"));
const { venueAliases } = await import(path.join(here, "..", "functions", "match-sync", "src", "venuealias.js"));

const arg = (n, d) => { const i = process.argv.indexOf(`--${n}`); return i > 0 ? process.argv[i + 1] : d; };
const venuesFlag = process.argv.includes("--venues");
const venueTables = new Map();
const venueFixed = new Set();
const archive = arg("archive", process.env.WINPROB_ARCHIVE);
const zipPath = arg("zip", path.join(os.homedir(), "Library/Caches/bouncer-winprob-lab/all_json.zip"));
if (!archive) { console.error("pass --archive <dir holding m/>"); process.exit(2); }

const DAY = 86400000;
const dayOf = (s) => Date.parse(`${s}T00:00:00Z`);
const fmtOf = (f) => (/test/i.test(f) ? "TEST" : /odi/i.test(f) ? "ODI" : /t20/i.test(f) ? "T20" : String(f).toUpperCase());

// 1. archive entries
const entries = [];
for (const f of fs.readdirSync(path.join(archive, "m"))) {
  if (!f.endsWith(".json")) continue;
  let j; try { j = JSON.parse(fs.readFileSync(path.join(archive, "m", f), "utf8")); } catch { continue; }
  if (!j.context?.teams || !j.evalLog) continue;
  entries.push({ file: f, ...j, startMs: Number(j.evalLog.startMs ?? j.context.startMs) });
}
console.log(`archive entries with context+evalLog: ${entries.length}`);

// 2. Cricsheet skeletons, date order
const zip = new AdmZip(zipPath);
const skel = [];
for (const e of zip.getEntries()) {
  if (e.isDirectory || !e.entryName.endsWith(".json")) continue;
  const s = richSkeletonOf(JSON.parse(e.getData().toString("utf8")));
  if (s) { s.file = e.entryName; skel.push(s); }
}
skel.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.file < b.file ? -1 : 1));
console.log(`cricsheet matches: ${skel.length}`);

// 3. join
const used = new Set();
const joins = new Map(); // skeleton -> entry
for (const e of entries) {
  const gender = e.evalLog.gender ?? e.context.gender;
  const fmt = fmtOf(e.evalLog.format ?? e.context.format);
  const keys = e.context.teams.map((t) => squash(t.key ?? t.name)).sort().join("|");
  const cand = skel.filter((s) =>
    s.gender === gender && s.formatClass === fmt && Math.abs(dayOf(s.date) - e.startMs) <= 1.5 * DAY &&
    s.teams.map(squash).sort().join("|") === keys && !joins.has(s));
  if (cand.length) { joins.set(cand[0], e); used.add(e.file); }
}
console.log(`joined: ${joins.size} of ${entries.length} (${((100 * joins.size) / entries.length).toFixed(0)}%)`);
const unjoined = entries.filter((e) => !used.has(e.file));
const byFmt = {};
for (const e of unjoined) { const k = `${e.evalLog.gender}/${e.evalLog.format}`; byFmt[k] = (byFmt[k] ?? 0) + 1; }
console.log("unjoined by gender/format:", JSON.stringify(byFmt));

// 4. walk, reading contextFor before update; also snapshot at the model's asOf
const FEATS = ["eloDiff", "formDiff", "h2hAdvantage", "winRateDiff", "venueScoring", "venueChaseAdvantage", "venueDrawAdvantage", "familiarityDiff", "hasVenue"];
const XI = ["batAvg", "batStrike", "batRecent", "bowl", "coverage"];
const state = new AsOfState();
const pending = [...joins.entries()]
  .map(([s, e]) => ({ s, e, asOf: e.context.modelAsOf ?? null }))
  .filter((p) => p.asOf)
  .sort((a, b) => (a.asOf < b.asOf ? -1 : 1));
let pi = 0;
const rows = []; // {entry, side, serving, exact, lagged}
const ctxOf = (match) => state.contextFor(match);
const lagged = new Map();
const exact = new Map();
for (const m of skel) {
  while (pi < pending.length && pending[pi].asOf <= m.date) {
    lagged.set(pending[pi].s, ctxOf(pending[pi].s));
    if (venuesFlag && !pending[pi].e.context.venue?.known) {
      const nm = venueName(pending[pi].s.venue);
      const keys = new Set();
      for (const k of state.venues.keys()) { const g = k.split("|")[0]; if (g.split("@")[0] === nm) keys.add(g); }
      venueTables.set(pending[pi].s, [...keys]);
      // the table serving would have read: every ground filed under a name this lookup tries
      const want = new Set([venueName(pending[pi].e.context.venue?.name), ...venueAliases(pending[pi].e.context.venue?.name)]);
      const table = {};
      for (const k of state.venues.keys()) { const [n, city = ""] = k.split("|")[0].split("@"); if (want.has(n)) { const l = (table[n] ??= []); if (!l.some((x) => x.city === city)) l.push({ city }); } }
      if (findGround(table, pending[pi].e.context.venue)) venueFixed.add(pending[pi].s);
    }
    pi++;
  }
  if (joins.has(m)) exact.set(m, ctxOf(m));
  state.update(m);
}
// ASOF snapshots for matches after the last walked date are not reachable; none expected.

const stats = {};
const add = (group, feat, d) => {
  if (d == null || Number.isNaN(d)) return;
  const k = `${group}:${feat}`;
  (stats[k] ??= { n: 0, abs: 0, signed: 0, big: 0 });
  const t = stats[k]; t.n++; t.abs += Math.abs(d); t.signed += d; if (Math.abs(d) > 0.05) t.big++;
};
const worst = {};
const venueMiss = { n: 0, byClass: {}, rows: [] };
for (const [m, e] of joins) {
  for (const sideName of m.teams) {
    const tm = e.context.teams.find((t) => squash(t.key ?? t.name) === squash(sideName));
    if (!tm) continue;
    const serving = sideContextFor(e.context, tm.id);
    if (!serving) continue;
    const sets = { exact: exact.get(m), lagged: lagged.get(m) };
    for (const [g, ctx] of Object.entries(sets)) {
      if (!ctx) continue;
      const tr = sideContextOf(ctx, m, sideName, (name) => ctx.xi[name]);
      for (const f of FEATS) {
        const d = Number(serving[f]) - Number(tr[f]);
        add(g, f, d);
        if (g === "lagged" && venuesFlag) add("sim", f, f === "hasVenue" && venueFixed.has(m) ? 0 : d);
        if (g === "exact" && Math.abs(d) > 0.05) (worst[f] ??= []).push({ f: e.file, side: sideName, serving: serving[f], trainer: tr[f] });
      }
      add(g, "xiPresent(serving-vs-trainer)", (serving.xi ? 1 : 0) - (tr.xi ? 1 : 0));
      if (serving.xi && tr.xi) for (const x of XI) add(g, `xi.${x}`, serving.xi[x] - tr.xi[x]);
    }
    if (venuesFlag && sets.lagged && !e.context.venue?.known && sideContextOf(sets.lagged, m, sideName).hasVenue) {
      const cities = venueTables.get(m) ?? [];
      const cls = cities.length === 0 ? "name-miss" : cities.length === 1 ? "single-ground-not-picked" : "several-grounds-city-mismatch";
      venueMiss.n++; venueMiss.byClass[cls] = (venueMiss.byClass[cls] ?? 0) + 1;
      venueMiss.rows.push({ servingName: e.context.venue?.name, servingCity: e.context.venue?.city, trainerKey: m.venueKey, trainerVenue: m.venue, sameNameKeys: cities.slice(0, 4), cls });
    }
    // gating: serving neutralises team terms unless both known; venue unless known
    add("gating", "teamKnown(serving)", tm.known ? 1 : 0);
    add("gating", "venueKnown(serving)", e.context.venue?.known ? 1 : 0);
    add("gating", "trainerHasVenue(exact)", sets.exact ? (sideContextOf(sets.exact, m, sideName).hasVenue ? 1 : 0) : null);
    add("gating", "trainerTeamGames>0", sets.exact ? (sets.exact.teams[sideName].games > 0 ? 1 : 0) : null);
  }
}

// 5. over-state check on a sample
let ov = { n: 0, scoreAbs: 0, wktAbs: 0, exact: 0, shifted: 0 };
for (const [m, e] of joins) {
  if (m.formatClass === "TEST") continue;
  for (const p of e.scorecard?.overs ?? []) {
    const inn = m.innings[(p.innings ?? 1) - 1];
    if (!inn || p.score == null) continue;
    const at = (balls) => { let r = 0, w = 0, l = 0; for (const c of inn.balls) { if (l >= balls) break; const d = unpackDelivery(c); r += d.runs; w += d.wickets; if (d.legal) l++; } return { r, w }; };
    const a = at(p.over * 6), b = at((p.over + 1) * 6);
    const sc = Number(p.score), wk = Number(p.wickets);
    const hitA = a.r === sc && a.w === wk, hitB = b.r === sc && b.w === wk;
    ov.n++; if (hitA) ov.exact++; else if (hitB) ov.shifted++;
    ov.scoreAbs += Math.abs(sc - (hitB && !hitA ? b.r : a.r)); ov.wktAbs += Math.abs(wk - (hitB && !hitA ? b.w : a.w));
  }
}

for (const g of ["exact", "lagged", ...(venuesFlag ? ["sim"] : []), "gating"]) {
  console.log(`-- ${g}${g === "sim" ? " (lagged, hasVenue zeroed where the new serving lookup finds the ground)" : ""}${g === "exact" ? " (trainer as of match date)" : g === "lagged" ? " (trainer as of model asOf)" : " (mean = share true)"}`);
  for (const [k, t] of Object.entries(stats)) if (k.startsWith(`${g}:`))
    console.log(`${k.slice(g.length + 1).padEnd(30)} n=${String(t.n).padStart(4)} mean|d|=${(t.abs / t.n).toFixed(4)} mean d=${(t.signed / t.n).toFixed(4)} >0.05: ${t.big}`);
}
console.log(`over-state: n=${ov.n} exactAtOver=${ov.exact} matchesNextOver=${ov.shifted} mean|score d|=${(ov.scoreAbs / Math.max(1, ov.n)).toFixed(2)} mean|wkt d|=${(ov.wktAbs / Math.max(1, ov.n)).toFixed(3)}`);
if (venuesFlag) {
  console.log(`new serving lookup finds the ground for ${venueFixed.size} matches of the ${venueTables.size} archived with an unknown ground`);
  console.log(`-- venue misses (serving unknown, trainer hasVenue, lagged): ${venueMiss.n} sides`, JSON.stringify(venueMiss.byClass));
  const uniq = new Map();
  for (const r of venueMiss.rows) { const k = `${r.servingName} | ${r.servingCity} | ${r.trainerKey}`; uniq.set(k, (uniq.get(k) ?? 0) + 1); }
  for (const [k, n] of [...uniq].slice(0, 15)) console.log(`${n}x ${k}`);
}
const dump = arg("dump");
if (dump) fs.writeFileSync(dump, JSON.stringify({ stats, worst, ov }));
for (const [f, list] of Object.entries(worst)) console.log(`worst ${f}:`, JSON.stringify(list.slice(0, 2)));
