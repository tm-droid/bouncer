#!/usr/bin/env node
//
// Build the historical-match site from Cricsheet's archive.
//
// USAGE
//   node build.mjs <all_json.zip> <out dir>
//
// Writes the files GitHub Pages serves from `tm-droid/bouncer-history` (see
// `.claude/rules/history-data.md`): one compact file per match, month and search indexes, and
// records per side, pair of sides, ground and player. The out dir is emptied first.
//
// Cricsheet is ODC-BY: re-hosting is allowed with attribution, which the app shows. Nothing from
// Cricbuzz may go into this output: the site is public.
//
// Exits non-zero when the output passes SIZE_LIMIT_BYTES. GitHub Pages refuses a site over 1 GB,
// and finding that out from a failed deploy would leave the published copy stale with no error.

import fs from "node:fs";
import path from "node:path";
import AdmZip from "adm-zip";
import { compactMatch, SCHEMA_VERSION } from "./src/match.js";
import { addMatch, createStats, formatKey, playerNameFiles, statsFiles } from "./src/stats.js";
import { monthOf, teamKey } from "./src/keys.js";

export const SIZE_LIMIT_BYTES = 900 * 1024 * 1024;

const [zipPath, outDir] = process.argv.slice(2);
if (!zipPath || !outDir) {
  console.error("usage: node build.mjs <all_json.zip> <out dir>");
  process.exit(1);
}

const mb = (n) => (n / 1024 / 1024).toFixed(1);
let bytes = 0;
let files = 0;
const root = path.resolve(outDir);
const write = (rel, obj) => {
  const file = path.resolve(root, rel);
  // Every path is built from keys the builder slugs or validates; this is the backstop that keeps
  // a key derived from Cricsheet's data from ever writing outside the site.
  if (!file.startsWith(root + path.sep)) throw new Error(`refusing to write outside ${root}: ${rel}`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const text = JSON.stringify(obj);
  fs.writeFileSync(file, text);
  bytes += Buffer.byteLength(text);
  files += 1;
};

fs.rmSync(outDir, { recursive: true, force: true });
fs.mkdirSync(outDir, { recursive: true });

let zip = new AdmZip(zipPath);
const entries = zip.getEntries().filter((e) => !e.isDirectory && /^\d+\.json$/.test(path.basename(e.entryName)));
const stats = createStats();
const months = new Map();
const teams = new Map();
let parsed = 0;
let skipped = 0;

// Each entry slot is cleared once read: adm-zip keeps an entry's inflated bytes for as long as
// the entry is referenced (the trainer died at 512 MB on exactly this, build.js).
for (let i = 0; i < entries.length; i++) {
  const entry = entries[i];
  entries[i] = null;
  const id = path.basename(entry.entryName, ".json");
  let match;
  try {
    match = compactMatch(id, JSON.parse(entry.getData().toString("utf8")));
  } catch (e) {
    if (skipped < 5) console.error(`skipped ${entry.entryName}: ${e?.message}`);
    skipped++;
    continue;
  }
  if (!match || !match.d) {
    skipped++;
    continue;
  }
  parsed++;
  write(`m/${id}.json`, match);
  addMatch(stats, match);

  const fk = formatKey(match);
  const row = [match.id, match.d, fk, match.g, match.t[0], match.t[1], match.o.w, match.o.r, match.e?.n ?? null, match.ven];
  const month = monthOf(match.d);
  if (!months.has(month)) months.set(month, []);
  months.get(month).push(row);
  for (const team of match.t) {
    const key = teamKey(team, match.g);
    const t = teams.get(key) ?? { n: team, g: match.g, c: 0 };
    t.c += 1;
    teams.set(key, t);
  }
}
entries.length = 0;
zip = null;
console.log(`parsed ${parsed}, skipped ${skipped}, RSS ${mb(process.memoryUsage().rss)} MB`);

const byDate = (a, b) => String(a[1]).localeCompare(String(b[1])) || String(a[0]).localeCompare(String(b[0]));
const years = {};
const search = [];
for (const [month, rows] of [...months].sort()) {
  rows.sort(byDate);
  write(`idx/m/${month}.json`, rows);
  const [y, mo] = month.split("-");
  (years[y] ??= {})[mo] = rows.length;
  // The search index drops the ground: it is ~10% of the bytes and nobody searches by it.
  for (const r of rows) search.push(r.slice(0, 9));
}
write("idx/years.json", years);
write("idx/search.json", search);
write(
  "idx/teams.json",
  [...teams].map(([key, t]) => [t.n, t.g, key, t.c]).sort((a, b) => b[3] - a[3])
);

for (const [rel, obj] of statsFiles(stats)) write(rel, obj);
for (const [rel, obj] of playerNameFiles(stats)) write(rel, obj);

// Last, so a reader that sees this build's manifest sees every file it describes.
const manifest = { v: SCHEMA_VERSION, built: new Date().toISOString(), matches: parsed, files: files + 1, bytes };
write("manifest.json", manifest);
// Pages would run Jekyll over the tree otherwise, and Jekyll skips nothing we need but costs a
// build minute per deploy.
fs.writeFileSync(path.join(outDir, ".nojekyll"), "");
fs.writeFileSync(
  path.join(outDir, "README.md"),
  [
    "# Bouncer history",
    "",
    "Match data for the Bouncer cricket app, rebuilt weekly. Not hand-edited: every push replaces the branch.",
    "",
    "Ball-by-ball data from [Cricsheet](https://cricsheet.org/), made available under the",
    "[Open Data Commons Attribution License (ODC-BY 1.0)](https://opendatacommons.org/licenses/by/1-0/).",
    "",
    `Built ${manifest.built} from ${parsed} matches.`,
    "",
  ].join("\n")
);

console.log(`wrote ${files} files, ${mb(bytes)} MB, RSS ${mb(process.memoryUsage().rss)} MB`);
if (bytes > SIZE_LIMIT_BYTES) {
  console.error(`output ${mb(bytes)} MB is over the ${mb(SIZE_LIMIT_BYTES)} MB limit (GitHub Pages refuses 1 GB)`);
  process.exit(1);
}
