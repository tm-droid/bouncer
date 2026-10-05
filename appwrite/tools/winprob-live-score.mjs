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
// nothing unless its interval excludes zero.
//
// Tests are not covered: match-sync stamps two-outcome formats only.
//
// USAGE
//   node appwrite/tools/winprob-live-score.mjs [--dir path] [--since 2026-10-05] [--boot 2000]
//
// `--dir` reads `wpe-*.json` from a directory instead of Storage.

import fs from "node:fs";
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

export function report(logs, { rounds = 2000 } = {}) {
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
  }
  const raw = logs.filter((l) => l.cricbuzz?.length);
  if (raw.length) {
    const samples = raw.reduce((n, l) => n + l.cricbuzz.length, 0);
    out.push("", `${samples} raw Cricbuzz samples in ${raw.length} match(es) are not yet read into the comparison. First one:`, `  ${raw[0].cricbuzz[0].raw}`);
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
      logs.push(JSON.parse(Buffer.from(raw).toString("utf8")));
    }
    if (page.files.length < 100) break;
    cursor = page.files[page.files.length - 1].$id;
  }
  return logs;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const argv = process.argv.slice(2);
  const flag = (name, fallback) => {
    const i = argv.indexOf(name);
    return i === -1 ? fallback : argv[i + 1];
  };
  const dir = flag("--dir", null);
  const logs = dir
    ? fs.readdirSync(dir).filter((f) => f.startsWith("wpe-")).map((f) => JSON.parse(fs.readFileSync(path.join(dir, f), "utf8")))
    : await fromStorage(flag("--since", null));
  console.log(report(logs, { rounds: Number(flag("--boot", "2000")) }));
}
