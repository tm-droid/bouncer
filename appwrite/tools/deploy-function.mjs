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

// Captured before anything is uploaded: it is both the "did activate take?" baseline and the
// rollback target printed if the new deployment turns out not to serve.
const activeBefore = (
  await (
    await fetch(`${endpoint}/functions/${functionId}`, {
      headers: { "X-Appwrite-Project": projectId, "X-Appwrite-Key": apiKey },
    })
  ).json()
).deploymentId;
console.log(`  currently serving ${activeBefore ?? "(none)"}`);

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

  if (status === "ready") {
    // `activate=true` is a request, not a guarantee, and "built" is not "serving". Both halves have
    // already failed in production: a VCS deployment superseded a manual one 97 seconds after it
    // went live, and a deployment that reported `ready` then failed every execution for 22 minutes
    // on a module it could not load. Neither was caught by the deploy step that produced it.
    process.exitCode = (await verifyLive(body.$id, activeBefore)) ? 0 : 1;
  }
} finally {
  fs.rmSync(tarball, { force: true });
}

/**
 * Assert the new deployment is the one actually serving, then that it can actually serve.
 *
 * Two checks, because they fail independently:
 *  1. The active `deploymentId` moved off whatever was there before and onto this build. Catches a
 *     silently-ignored `activate`, and catches a concurrent VCS build winning the race.
 *  2. The first real execution on it did not fail. A build can be `ready` and still be dead — a
 *     missing module throws before any handler code runs, so nothing in the function's own
 *     observability can report it. This is the only place that check can live.
 *
 * Returns false rather than throwing so the caller's `finally` still deletes the tarball.
 */
async function verifyLive(deploymentId, before) {
  const get = async (p) =>
    (await fetch(`${endpoint}${p}`, { headers: { "X-Appwrite-Project": projectId, "X-Appwrite-Key": apiKey } })).json();

  const fnNow = await get(`/functions/${functionId}`);
  if (fnNow.deploymentId !== deploymentId) {
    console.error(
      `\n  FAILED: ${functionId} is serving ${fnNow.deploymentId}, not the deployment just built (${deploymentId}).` +
        (fnNow.deploymentId === before
          ? "\n  The active deployment never moved — `activate` did not take."
          : "\n  Something else activated in between — most likely a VCS build from a push.")
    );
    return false;
  }
  console.log(`  active deployment moved ${before ?? "(none)"} -> ${deploymentId}`);

  // Scheduled functions produce their own traffic; on-demand ones need a nudge, and a real
  // invocation is the point — a synthetic one that skips the handler would prove nothing.
  const scheduled = Boolean(fn.schedule);
  if (!scheduled) {
    await fetch(`${endpoint}/functions/${functionId}/executions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Appwrite-Project": projectId, "X-Appwrite-Key": apiKey },
      body: JSON.stringify({ body: "{}", async: false, method: "POST" }),
    }).catch(() => {});
  }

  const waitMs = scheduled ? 150_000 : 20_000;
  const until = Date.now() + waitMs;
  console.log(`  waiting up to ${waitMs / 1000}s for an execution on it${scheduled ? " (scheduled every minute)" : ""}…`);
  while (Date.now() < until) {
    const q = [{ method: "limit", values: [10] }, { method: "orderDesc", values: ["$createdAt"] }]
      .map((o) => "queries[]=" + encodeURIComponent(JSON.stringify(o)))
      .join("&");
    const { executions = [] } = await get(`/functions/${functionId}/executions?${q}`);
    const mine = executions.filter((e) => e.deploymentId === deploymentId);
    if (mine.length) {
      const e = mine[0];
      // A 4xx is the function answering — a bad body, a rate limit. Only a 5xx or a FAILED status
      // means the deployment itself cannot serve, which is what this check is for.
      const broken = e.status === "failed" || e.responseStatusCode >= 500;
      console.log(`  first execution: http=${e.responseStatusCode} status=${e.status} dur=${Number(e.duration).toFixed(1)}s`);
      if (broken) {
        console.error(`  FAILED: the new deployment is built but not serving.\n  stderr: ${String(e.errors || "(empty)").slice(0, 500)}`);
        console.error(`  Roll back with: PATCH /functions/${functionId}/deployments/${before}`);
        return false;
      }
      return true;
    }
    await new Promise((r) => setTimeout(r, 5000));
  }
  console.error(`  FAILED: no execution ran on ${deploymentId} within ${waitMs / 1000}s — cannot confirm it serves.`);
  return false;
}
