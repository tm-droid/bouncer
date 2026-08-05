#!/usr/bin/env node
//
// Copy the three provider keys from the gitignored `local.properties` into a function's Appwrite
// environment, over the REST API.
//
// Why a script rather than the Appwrite MCP or console: a variable's `value` comes back masked
// (`secret: true`), so the two functions' keys cannot be compared by reading them — and writing one
// through a tool call would put the key itself in a transcript. This reads and writes without ever
// printing a value; the only output is a key name, a length and an HTTP status.
//
// The defect this exists for: on 2026-08-04 07:19 UTC all three provider keys were rotated on
// `bouncer-match-sync` and in `local.properties`, and not on `bouncer-generate-commentary`, whose
// variables still held their 2026-06-09 values. Groq had revoked its old key outright (HTTP 403 on
// every call, at zero load), so the device-facing commentary path was running a two-provider chain
// while believing it had three. Nothing reported it, because that function has no Sentry wiring.
//
// USAGE
//   node sync-provider-keys.mjs --function bouncer-generate-commentary [--dry-run]
//
// A variable change does NOT take effect until the function is redeployed.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const KEYS = ["GROQ_API_KEY", "CEREBRAS_API_KEY", "SAMBANOVA_API_KEY"];

const argv = process.argv.slice(2);
const dryRun = argv.includes("--dry-run");
const functionId = argv[argv.indexOf("--function") + 1];
if (!argv.includes("--function") || !functionId || functionId.startsWith("--")) {
  console.error("usage: node sync-provider-keys.mjs --function <functionId> [--dry-run]");
  process.exit(1);
}

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
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

const missing = [
  ["APPWRITE_ENDPOINT", endpoint],
  ["APPWRITE_PROJECT_ID", projectId],
  ["APPWRITE_API_KEY", apiKey],
  ...KEYS.map((k) => [k, props[k]]),
].filter(([, v]) => !v);
if (missing.length) {
  console.error(`missing from local.properties: ${missing.map(([k]) => k).join(", ")}`);
  process.exit(1);
}

const api = (p, init = {}) =>
  fetch(`${endpoint}${p}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-Appwrite-Project": projectId,
      "X-Appwrite-Key": apiKey,
      ...(init.headers || {}),
    },
  });

console.log(`${functionId}${dryRun ? "  (dry run)" : ""}`);

const listed = await api(`/functions/${functionId}/variables`);
if (!listed.ok) {
  console.error(`list variables failed: HTTP ${listed.status}`);
  process.exit(1);
}
const existing = new Map((await listed.json()).variables.map((v) => [v.key, v]));

let changed = 0;
for (const key of KEYS) {
  const value = props[key];
  const current = existing.get(key);
  const label = `  ${key.padEnd(18)} len=${value.length}`;

  if (!current) {
    console.log(`${label}  ABSENT on ${functionId} — creating`);
    if (dryRun) continue;
    const created = await api(`/functions/${functionId}/variables`, {
      method: "POST",
      body: JSON.stringify({ key, value, secret: true }),
    });
    console.log(`${" ".repeat(label.length)}  create HTTP ${created.status}`);
    if (created.ok) changed += 1;
    continue;
  }

  console.log(`${label}  updatedAt=${current.$updatedAt}`);
  if (dryRun) continue;
  const updated = await api(`/functions/${functionId}/variables/${current.$id}`, {
    method: "PUT",
    body: JSON.stringify({ key, value, secret: true }),
  });
  console.log(`${" ".repeat(label.length)}  update HTTP ${updated.status}`);
  if (!updated.ok) console.error(`${" ".repeat(label.length)}  ${(await updated.text()).slice(0, 200)}`);
  else changed += 1;
}

console.log(
  dryRun
    ? "\nnothing written."
    : `\n${changed}/${KEYS.length} written. Redeploy ${functionId} — a variable change is not live until the next deployment.`
);
