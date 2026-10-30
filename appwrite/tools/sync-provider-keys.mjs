#!/usr/bin/env node
//
// Copy the provider keys from the gitignored `local.properties` into a function's Appwrite
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
//   node sync-provider-keys.mjs --function bouncer-generate-commentary --keys SENTRY_DSN
//
// `--keys` defaults to the provider keys in `DEFAULT_KEYS`. Any name is accepted as long as `local.properties`
// carries it, which is what makes this reusable for SENTRY_DSN — not a credential (a DSN can submit
// events, not read them), but the same copy-from-local-properties problem.
//
// A variable change does NOT take effect until the function is redeployed.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_KEYS = ["GROQ_API_KEY", "GEMINI_API_KEY", "GEMINI_API_KEY_2", "CEREBRAS_API_KEY", "SAMBANOVA_API_KEY"];

const argv = process.argv.slice(2);
const dryRun = argv.includes("--dry-run");
const flag = (name) => {
  const i = argv.indexOf(name);
  const v = i === -1 ? undefined : argv[i + 1];
  return v && !v.startsWith("--") ? v : undefined;
};
const functionId = flag("--function");
if (!functionId) {
  console.error("usage: node sync-provider-keys.mjs --function <functionId> [--keys A,B] [--dry-run]");
  process.exit(1);
}

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const props = Object.fromEntries(
  fs
    .readFileSync(path.join(repoRoot, "android", "local.properties"), "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("=") && !l.trim().startsWith("#"))
    .map((l) => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()])
);

// Every further Gemini project's key (`GEMINI_API_KEY_3` and up) is copied when `local.properties`
// carries it. llm.js adds a project's rungs only when its key is set, so a key left out here is a
// project that silently contributes nothing.
const extraGeminiKeys = Object.keys(props)
  .filter((k) => /^GEMINI_API_KEY_\d+$/.test(k) && !DEFAULT_KEYS.includes(k))
  .sort((a, b) => Number(a.split("_").pop()) - Number(b.split("_").pop()));
const KEYS = (flag("--keys") ?? [...DEFAULT_KEYS, ...extraGeminiKeys].join(","))
  .split(",")
  .map((k) => k.trim())
  .filter(Boolean);

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
    // Appwrite 1.9 requires an explicit variableId on create, and variable ids are unique
    // PROJECT-wide, not per function — `sentry-dsn` was already taken by bouncer-match-sync. So the
    // id carries the function too, which also keeps it readable in the console.
    const slug = (s) => s.toLowerCase().replace(/^bouncer-/, "").replace(/[^a-z0-9]+/g, "-");
    const variableId = `${slug(functionId)}-${slug(key)}`.slice(0, 36).replace(/-$/, "");
    const created = await api(`/functions/${functionId}/variables`, {
      method: "POST",
      body: JSON.stringify({ variableId, key, value, secret: true }),
    });
    console.log(`${" ".repeat(label.length)}  create HTTP ${created.status}`);
    if (created.ok) changed += 1;
    else console.error(`${" ".repeat(label.length)}  ${(await created.text()).slice(0, 300)}`);
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
