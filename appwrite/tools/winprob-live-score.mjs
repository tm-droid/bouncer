#!/usr/bin/env node
//
// Scores the win probabilities match-sync actually published against how the matches ended.
//
// The lab (`winprob-lab.mjs`) scores the model on Cricsheet. This scores what was SERVED: the
// per-over figures stamped during live matches, from live Cricbuzz inputs, read back from the
// `wpe-{matchId}` files match-sync writes when a match ends (see `match-sync/src/evallog.js`).
//
// HOW TO READ IT. Accuracy — "the favourite won" — is printed because it is the question people
// ask, but it is not the measure: a 55% call that loses was not wrong. Log-loss and calibration
// are. Rows within one match are near-duplicates, so every interval resamples MATCHES, never rows.
// A comparison with a rival is made only on points where both published a figure, and means
// nothing unless its interval excludes zero. Cricbuzz's figures come from the log itself; Cricinfo's
// from `wci-*` files and a laptop poller's JSONL, joined by team names and start time.
//
// Tests are not covered: match-sync stamps two-outcome formats only.
//
// USAGE
//   node appwrite/tools/winprob-live-score.mjs [--dir path] [--since 2026-10-05] [--boot 2000]
//
// `--dir` reads `wpe-*.json` from a directory instead of Storage.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const EPS = 1e-6;
const clamp = (p) => Math.min(1 - EPS, Math.max(EPS, p));
const logLoss = (p, y) => -(y ? Math.log(clamp(p)) : Math.log(1 - clamp(p)));

/** Team1's pre-match probability, or null when the stored figure names neither side. */
export function prematchForTeam1(log) {
  const pre = log.prematch;
  if (!pre || !Number.isFinite(pre.p)) return null;
  if (pre.teamId === log.team1Id) return pre.p;
  if (pre.teamId === log.team2Id) return 1 - pre.p;
  return null;
}

/**
 * Team1's chance from one raw Cricbuzz sample — `{team1: {id, percent}, team2: {id, percent}}` —
 * or null when neither side in it is the log's team1. By id: Cricbuzz's own team1 is its listing
 * order, not the side that batted first.
 */
export function cricbuzzForTeam1(raw, team1Id) {
  let sample;
  try {
    sample = typeof raw === "string" ? JSON.parse(raw) : raw;
  } catch {
    return null;
  }
  const side = [sample?.team1, sample?.team2].find((t) => t?.id != null && String(t.id) === String(team1Id));
  return Number.isFinite(side?.percent) && side.percent >= 0 && side.percent <= 100 ? side.percent / 100 : null;
}

const fold = (v) => String(v ?? "").toLowerCase().replace(/[^a-z0-9]/g, "");
const JOIN_WINDOW_MS = 36 * 60 * 60 * 1000;

/** A log's two side names: its own `teams`, or the title for a log written before it had them. */
function sidesOf(log) {
  const named = (log.teams ?? []).map((t) => t?.name).filter(Boolean);
  return (named.length === 2 ? named : String(log.title ?? "").split(/\s+vs\s+/i)).map(fold).filter(Boolean).sort();
}

/**
 * The Cricinfo samples that belong to a log. Cricinfo has its own match ids, so the join is both
 * team names and a start time within a day and a half; a log with no start time is matched on the
 * time it ended. Anything short of both names matching is no join.
 */
export function cricinfoFor(log, samples) {
  const sides = sidesOf(log);
  if (sides.length !== 2) return [];
  const anchor = log.startMs ?? log.endedAt;
  return samples.filter((r) => {
    const theirs = (r.teams ?? []).map((t) => fold(t?.name)).filter(Boolean).sort();
    const start = Date.parse(r.startTime);
    return theirs.length === 2 && theirs[0] === sides[0] && theirs[1] === sides[1]
      && Number.isFinite(start) && Number.isFinite(anchor) && Math.abs(start - anchor) <= JOIN_WINDOW_MS;
  });
}

/** `oversUnique` is overs.balls with the ball as two digits: 6.04 is four balls into the seventh over. */
const oversOf = (unique) => Math.floor(unique) + (Math.round((unique % 1) * 100)) / 6;

/**
 * Team1's chance from Cricinfo at the end of [over], or null with no sample within half an over.
 * Cricinfo's `winProbability` is the BATTING side's: on India v West Indies it tracked Cricbuzz's
 * batting-side figure through both innings. So innings 1 is team1's and a later one is the rest.
 */
export function cricinfoForTeam1(samples, innings, over) {
  let best = null;
  for (const r of samples) {
    const pr = r?.predictions;
    if (pr?.inningNumber !== innings || !Number.isFinite(pr.oversUnique) || !Number.isFinite(pr.winProbability)) continue;
    const gap = Math.abs(oversOf(pr.oversUnique) - over);
    if (gap <= 0.5 && (!best || gap < best.gap)) best = { gap, p: pr.winProbability / 100 };
  }
  if (!best) return null;
  return innings === 1 ? best.p : 1 - best.p;
}

/** [log] with `cb` and `ci` set on each point that a rival also published a figure for. */
export function withRivals(log, cricinfoSamples = []) {
  const theirs = cricinfoFor(log, cricinfoSamples);
  const points = (log.points ?? []).map((pt) => {
    const sample = (log.cricbuzz ?? []).find((c) => c.innings === pt.innings && c.over === pt.over);
    const cb = sample ? cricbuzzForTeam1(sample.raw, log.team1Id) : null;
    const ci = cricinfoForTeam1(theirs, pt.innings, pt.over);
    return { ...pt, ...(cb === null ? {} : { cb }), ...(ci === null ? {} : { ci }) };
  });
  return { ...log, points };
}

/** One row per scored figure. A match that was tied, abandoned or unresolved yields none. */
export function rowsOf(log) {
  if (log.outcome !== "team1" && log.outcome !== "team2") return [];
  const y = log.outcome === "team1" ? 1 : 0;
  const base = { matchId: log.matchId, format: log.format ?? "UNKNOWN", y, hadContext: !!log.hadContext, hadElevens: !!log.hadElevens };
  const rows = [];
  const pre = prematchForTeam1(log);
  if (pre !== null) rows.push({ ...base, phase: "pre-match", p: pre });
  for (const pt of log.points ?? []) {
    rows.push({
      ...base,
      phase: pt.innings === 1 ? "first innings" : "chase",
      p: pt.p,
      cb: Number.isFinite(pt.cb) ? pt.cb : null,
      ci: Number.isFinite(pt.ci) ? pt.ci : null,
    });
  }
  return rows;
}

export function metrics(rows, key = "p") {
  const n = rows.length;
  if (!n) return null;
  let ll = 0, brier = 0, right = 0;
  const bins = Array.from({ length: 10 }, () => ({ n: 0, p: 0, y: 0 }));
  for (const r of rows) {
    const p = r[key];
    ll += logLoss(p, r.y);
    brier += (p - r.y) ** 2;
    right += p === 0.5 ? 0.5 : (p > 0.5) === (r.y === 1) ? 1 : 0;
    const b = bins[Math.min(9, Math.floor(p * 10))];
    b.n++; b.p += p; b.y += r.y;
  }
  const ece = bins.reduce((sum, b) => sum + (b.n ? Math.abs(b.p / b.n - b.y / b.n) * (b.n / n) : 0), 0);
  return { n, matches: new Set(rows.map((r) => r.matchId)).size, accuracy: right / n, logLoss: ll / n, brier: brier / n, ece, bins };
}

/** A seeded generator, so a rerun on the same files prints the same interval. */
function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 2 ** 32);
}

/** 95% interval for [stat] over rows, resampling whole matches. */
export function matchBootstrap(rows, stat, { rounds = 2000, seed = 7 } = {}) {
  const byMatch = new Map();
  for (const r of rows) (byMatch.get(r.matchId) ?? byMatch.set(r.matchId, []).get(r.matchId)).push(r);
  const groups = [...byMatch.values()];
  if (groups.length < 2) return null;
  const next = rng(seed);
  const values = [];
  for (let i = 0; i < rounds; i++) {
    const sample = [];
    for (let g = 0; g < groups.length; g++) sample.push(...groups[Math.floor(next() * groups.length)]);
    values.push(stat(sample));
  }
  values.sort((a, b) => a - b);
  return [values[Math.floor(rounds * 0.025)], values[Math.floor(rounds * 0.975)]];
}

/** Ours against a rival on the points both published. Negative difference = ours is better. */
export function paired(rows, rivalKey, opts) {
  const both = rows.filter((r) => r[rivalKey] !== null && r[rivalKey] !== undefined);
  if (!both.length) return null;
  const diff = (sample) => metrics(sample, "p").logLoss - metrics(sample, rivalKey).logLoss;
  return { ours: metrics(both, "p"), theirs: metrics(both, rivalKey), difference: diff(both), interval: matchBootstrap(both, diff, opts) };
}

const pct = (v) => `${(v * 100).toFixed(1)}%`;
const f4 = (v) => v.toFixed(4);

function line(label, m, interval) {
  if (!m) return `${label.padEnd(26)} —`;
  const ci = interval ? `  [${f4(interval[0])}, ${f4(interval[1])}]` : "";
  return `${label.padEnd(26)} ${String(m.matches).padStart(4)} matches ${String(m.n).padStart(6)} points   right ${pct(m.accuracy).padStart(6)}   log-loss ${f4(m.logLoss)}${ci}   Brier ${f4(m.brier)}   ECE ${f4(m.ece)}`;
}

export function report(rawLogs, { rounds = 2000, cricinfo = [] } = {}) {
  const logs = rawLogs.map((l) => withRivals(l, cricinfo));
  const out = [];
  const outcomes = {};
  for (const l of logs) outcomes[l.outcome] = (outcomes[l.outcome] ?? 0) + 1;
  out.push(`${logs.length} finished matches logged — ${Object.entries(outcomes).map(([k, v]) => `${k} ${v}`).join(", ") || "none"}`);
  const rows = logs.flatMap(rowsOf);
  if (!rows.length) {
    out.push("Nothing to score yet.");
    return out.join("\n");
  }
  const ll = (sample) => metrics(sample).logLoss;
  const section = (title, groups) => {
    out.push("", title);
    for (const [label, subset] of groups) out.push(line(label, metrics(subset), matchBootstrap(subset, ll, { rounds })));
  };
  const by = (fn) => [...new Set(rows.map(fn))].sort().map((v) => [String(v), rows.filter((r) => fn(r) === v)]);
  section("By phase", [["all", rows], ...by((r) => r.phase)]);
  section("By format", by((r) => r.format));
  section("By what the model had", [
    ["no context", rows.filter((r) => !r.hadContext)],
    ["context, no elevens", rows.filter((r) => r.hadContext && !r.hadElevens)],
    ["context and elevens", rows.filter((r) => r.hadElevens)],
  ]);

  const all = metrics(rows);
  out.push("", "Calibration (all points): said → happened");
  all.bins.forEach((b, i) => {
    if (b.n) out.push(`  ${String(i * 10).padStart(2)}–${String(i * 10 + 10).padEnd(3)}%  ${String(b.n).padStart(6)} points   said ${pct(b.p / b.n).padStart(6)}   team won ${pct(b.y / b.n).padStart(6)}`);
  });

  out.push("", "Against the rivals (same points only; negative difference = ours is better)");
  for (const [name, key] of [["Cricbuzz", "cb"], ["Cricinfo", "ci"]]) {
    const cmp = paired(rows, key, { rounds });
    if (!cmp) {
      out.push(`  ${name}: no paired points`);
      continue;
    }
    out.push(line(`  ours (vs ${name})`, cmp.ours), line(`  ${name}`, cmp.theirs));
    out.push(`  log-loss difference ${f4(cmp.difference)}${cmp.interval ? `  [${f4(cmp.interval[0])}, ${f4(cmp.interval[1])}]` : "  (one match: no interval)"}`);
    if (cmp.ours.matches === 1) out.push("  One match shows who was most confident about the side that won, not who is better.");
  }
  return out.join("\n");
}

async function fromStorage(since) {
  const toolsDir = path.dirname(fileURLToPath(import.meta.url));
  const props = Object.fromEntries(
    fs.readFileSync(path.resolve(toolsDir, "../../local.properties"), "utf8")
      .split(/\r?\n/)
      .filter((l) => l.includes("=") && !l.trim().startsWith("#"))
      .map((l) => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()])
  );
  const endpoint = (process.env.APPWRITE_ENDPOINT || props.APPWRITE_ENDPOINT || "").replace(/\/$/, "");
  const projectId = process.env.APPWRITE_PROJECT_ID || props.APPWRITE_PROJECT_ID;
  const apiKey = process.env.APPWRITE_API_KEY || props.APPWRITE_API_KEY;
  if (!endpoint || !projectId || !apiKey) throw new Error("missing APPWRITE_ENDPOINT / APPWRITE_PROJECT_ID / APPWRITE_API_KEY");
  const { Client, Storage, Query } = await import("node-appwrite");
  const storage = new Storage(new Client().setEndpoint(endpoint).setProject(projectId).setKey(apiKey));
  const bucket = process.env.MATCH_CACHE_BUCKET_ID || "cricket-stats";
  const logs = [];
  let cursor = null;
  for (;;) {
    const queries = [Query.startsWith("$id", "wpe-"), Query.limit(100)];
    if (since) queries.push(Query.greaterThanEqual("$updatedAt", new Date(since).toISOString()));
    if (cursor) queries.push(Query.cursorAfter(cursor));
    const page = await storage.listFiles(bucket, queries);
    for (const file of page.files) {
      if (!String(file.$id).startsWith("wpe-")) continue;
      const raw = await storage.getFileDownload(bucket, file.$id);
      // node-appwrite hands back an already-parsed object for a JSON file, and bytes otherwise.
      const bytes = raw instanceof ArrayBuffer || ArrayBuffer.isView(raw);
      logs.push(bytes ? JSON.parse(Buffer.from(raw).toString("utf8")) : raw);
    }
    if (page.files.length < 100) break;
    cursor = page.files[page.files.length - 1].$id;
  }
  // Cricinfo's samples, one file per Cricinfo match.
  const cricinfo = [];
  const theirs = await storage.listFiles(bucket, [Query.startsWith("$id", "wci-"), Query.limit(100)]);
  for (const file of theirs.files) {
    if (!String(file.$id).startsWith("wci-")) continue;
    const body = await storage.getFileDownload(bucket, file.$id).catch(() => null);
    if (Array.isArray(body?.samples)) cricinfo.push(...body.samples);
  }
  return { logs, cricinfo };
}

/** Rows a laptop poller captured, if any; they cover polls the container missed and the reverse. */
function laptopCricinfo() {
  const file = path.join(os.homedir(), "Library/Caches/bouncer-winprob-lab/cricinfo-predictions.jsonl");
  if (!fs.existsSync(file)) return [];
  return fs.readFileSync(file, "utf8").split("\n").filter(Boolean).flatMap((l) => {
    try {
      return [JSON.parse(l)];
    } catch {
      return [];
    }
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const argv = process.argv.slice(2);
  const flag = (name, fallback) => {
    const i = argv.indexOf(name);
    return i === -1 ? fallback : argv[i + 1];
  };
  const dir = flag("--dir", null);
  const read = (f) => JSON.parse(fs.readFileSync(path.join(dir, f), "utf8"));
  const { logs, cricinfo } = dir
    ? {
      logs: fs.readdirSync(dir).filter((f) => f.startsWith("wpe-")).map(read),
      cricinfo: fs.readdirSync(dir).filter((f) => f.startsWith("wci-")).flatMap((f) => read(f).samples ?? []),
    }
    : await fromStorage(flag("--since", null));
  console.log(report(logs, { rounds: Number(flag("--boot", "2000")), cricinfo: [...cricinfo, ...laptopCricinfo()] }));
}
