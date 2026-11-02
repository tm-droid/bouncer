#!/usr/bin/env node
//
// Team and head-to-head records for Afghanistan (and the Afghanistan Premier League sides), which
// Cricsheet withholds, built from the archive's scorecards (`.claude/rules/archive-data.md`).
//
// USAGE   node afg-records.mjs <siteDir>
// Reads every `m/*.json` of a gh-pages checkout and writes `s/team/<key>.json` and
// `s/h2h/<a>__<b>.json` in the Cricsheet site's schemas. Rerun after any backfill. Fails (writes
// nothing) when a result names a winner that is neither side.

import fs from "node:fs";
import path from "node:path";
import { buildRecords } from "./src/afg-records.js";

const site = process.argv[2];
if (!site) {
  console.error("usage: node afg-records.mjs <siteDir>");
  process.exit(1);
}
const dir = path.resolve(site);
const files = fs.readdirSync(path.join(dir, "m")).filter((f) => f.endsWith(".json"))
  .map((f) => JSON.parse(fs.readFileSync(path.join(dir, "m", f), "utf8")));
const { files: out, matches, unresolved } = buildRecords(files);
if (unresolved.length) {
  console.error("unresolved results:", JSON.stringify(unresolved));
  process.exit(1);
}
for (const [rel, obj] of out) {
  const file = path.join(dir, rel);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(obj));
}
const paths = [...out.keys()];
console.log(`${matches} matches -> ${paths.filter((p) => p.startsWith("s/team/")).length} team files, ${paths.filter((p) => p.startsWith("s/h2h/")).length} h2h files`);
