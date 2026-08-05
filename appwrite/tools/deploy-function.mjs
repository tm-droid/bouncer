#!/usr/bin/env node
//
// Deploy one Appwrite function from `appwrite.config.json`, over the REST API.
//
// Not `appwrite push`: the CLI reconciles the whole config file, which has previously deleted table
// columns and rewritten domain rules from config drift (see .claude/rules). This uploads a single
// function's source and nothing else — no schema, no bucket, no other function.
//
// USAGE
//   node deploy-function.mjs bouncer-generate-commentary
//   node deploy-function.mjs bouncer-match-sync --with-node-modules
//
// `node_modules` is excluded by default and rebuilt server-side by the function's own `commands`.
// `--with-node-modules` ships it instead: `bouncer-match-sync` has repeatedly failed to build from a
// small archive (an Appwrite sidecar bug), and every deployment of it that has succeeded has been a
// ~12MB one with the modules already in the tarball. Its `commands` still runs, and re-installing
// over an existing tree is a cache hit.
//
// A variable change is not live until a deployment, so this is also how an env-var edit takes effect.

import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const argv = process.argv.slice(2);
const withNodeModules = argv.includes("--with-node-modules");
const functionId = argv.find((a) => !a.startsWith("--"));
if (!functionId) {
  console.error("usage: node deploy-function.mjs <functionId> [--with-node-modules]");
  process.exit(1);
}

const toolsDir = path.dirname(fileURLToPath(import.meta.url));
const appwriteDir = path.resolve(toolsDir, "..");
const repoRoot = path.resolve(appwriteDir, "..");

const config = JSON.parse(fs.readFileSync(path.join(appwriteDir, "appwrite.config.json"), "utf8"));
const fn = config.functions.find((f) => f.$id === functionId);
if (!fn) {
  console.error(`${functionId} is not in appwrite.config.json`);
  process.exit(1);
}

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

const srcDir = path.join(appwriteDir, fn.path);
const tarball = path.join(toolsDir, `.deploy-${functionId}.tar.gz`);

console.log(`${functionId}  ${fn.path}  entrypoint=${fn.entrypoint}  commands=${fn.commands ?? "(none)"}`);

const excludes = ["--exclude=.appwrite", "--exclude=test"];
if (!withNodeModules) excludes.push("--exclude=node_modules");
execFileSync("tar", [...excludes, "-czf", tarball, "-C", srcDir, "."], { stdio: "inherit" });
const bytes = fs.statSync(tarball).size;
console.log(`  archive ${bytes} bytes`);

try {
  const form = new FormData();
  form.append("entrypoint", fn.entrypoint);
  if (fn.commands) form.append("commands", fn.commands);
  form.append("activate", "true");
  form.append("code", new Blob([fs.readFileSync(tarball)]), path.basename(tarball));

  const created = await fetch(`${endpoint}/functions/${functionId}/deployments`, {
    method: "POST",
    headers: {
      "X-Appwrite-Project": projectId,
      "X-Appwrite-Key": apiKey,
      "X-Appwrite-Response-Format": "1.8.0",
    },
    body: form,
  });
  const body = await created.json();
  if (!created.ok) {
    // Thrown, not process.exit'd, so the finally below still removes the tarball.
    throw new Error(`create deployment HTTP ${created.status}: ${JSON.stringify(body).slice(0, 400)}`);
  }
  console.log(`  deployment ${body.$id} status=${body.status}`);

  // Poll to "ready". A deployment that fails to build leaves the previous one active, so a script
  // that returned at "processing" would report success over an unchanged function.
  const deadline = Date.now() + 240_000;
  let status = body.status;
  while (status !== "ready" && status !== "failed" && Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 5000));
    const polled = await fetch(`${endpoint}/functions/${functionId}/deployments/${body.$id}`, {
      headers: { "X-Appwrite-Project": projectId, "X-Appwrite-Key": apiKey },
    });
    const d = await polled.json();
    status = d.status;
    process.stdout.write(`  ${status}\r`);
    if (status === "failed") {
      console.log(`\n  build logs (tail):\n${String(d.buildLogs || "").slice(-1500)}`);
    }
  }
  console.log(`\n  final status=${status}`);
  // Set the code rather than calling process.exit here — process.exit skips the finally below and
  // leaves a 12MB tarball in the working tree.
  process.exitCode = status === "ready" ? 0 : 1;
} finally {
  fs.rmSync(tarball, { force: true });
}
