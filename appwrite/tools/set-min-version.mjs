#!/usr/bin/env node
//
// Publish the lowest versionCode the app may run, as `cricket-stats/app-config`.
//
// USAGE
//   node set-min-version.mjs 1150     # builds below 1150 show "Update Bouncer" and nothing else
//   node set-min-version.mjs 0        # no minimum
//
// Read by `AppUpdateGate` on every launch, through OkHttp with a one-hour max-age, so a change
// reaches every open install within the hour. Builds below 1000 (local ones) are never gated.
//
// Set it only once the build at that versionCode is installable from Play by every tester:
// closed testing (`alpha`) waits on review, and a minimum published before the build clears it
// locks testers out with nothing to update to. A Play-only retirement does not need this; push
// the release with a `Force-Update: yes` trailer instead (ci.yml).
//
// Storage files cannot be overwritten, so the old file is deleted and a new one created. A launch
// in that gap reads no file, which is no minimum.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const BUCKET_ID = "cricket-stats";
const FILE_ID = "app-config";

const raw = process.argv[2];
const minVersionCode = Number(raw);
if (raw === undefined || !Number.isInteger(minVersionCode) || minVersionCode < 0) {
  console.error("usage: node set-min-version.mjs <versionCode, or 0 for none>");
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
if (!endpoint || !projectId || !apiKey) {
  console.error("missing APPWRITE_ENDPOINT / APPWRITE_PROJECT_ID / APPWRITE_API_KEY");
  process.exit(1);
}
const headers = { "X-Appwrite-Project": projectId, "X-Appwrite-Key": apiKey };
const fileUrl = `${endpoint}/storage/buckets/${BUCKET_ID}/files`;

const removed = await fetch(`${fileUrl}/${FILE_ID}`, { method: "DELETE", headers });
if (!removed.ok && removed.status !== 404) {
  console.error(`delete failed: HTTP ${removed.status} ${(await removed.text()).slice(0, 200)}`);
  process.exit(1);
}

const body = new FormData();
body.append("fileId", FILE_ID);
body.append("permissions[]", 'read("any")');
body.append(
  "file",
  new Blob([JSON.stringify({ minVersionCode, setAt: new Date().toISOString() })], { type: "application/json" }),
  `${FILE_ID}.json`
);
const created = await fetch(fileUrl, { method: "POST", headers, body });
if (!created.ok) {
  console.error(`upload failed: HTTP ${created.status} ${(await created.text()).slice(0, 200)}`);
  process.exit(1);
}

// What a device will read: the public download, without the key.
const check = await fetch(`${fileUrl}/${FILE_ID}/download`, { headers: { "X-Appwrite-Project": projectId } });
const served = check.ok ? await check.json() : null;
if (served?.minVersionCode !== minVersionCode) {
  console.error(`published, but the public download answers HTTP ${check.status}: ${JSON.stringify(served)}`);
  process.exit(1);
}
console.log(`minVersionCode=${minVersionCode} live at ${BUCKET_ID}/${FILE_ID}`);
