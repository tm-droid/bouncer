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
// Deploying is not the only moment the active deployment can change. A `git push` may or may not
// trigger a VCS build — observed firing for one commit, not firing for the next, and firing again
// for the one after — and when it does fire it supersedes whatever was deployed manually. So the
// health check has to be runnable on its own, after a push, against whatever is serving right now.
const verifyOnly = argv.includes("--verify-only");
const functionId = argv.find((a) => !a.startsWith("--"));
if (!functionId) {
  console.error("usage: node deploy-function.mjs <functionId> [--with-node-modules] [--verify-only]");
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

if (verifyOnly) {
  // Same execution-health check the deploy path runs, pointed at whatever is live. Passing
  // activeBefore as both arguments makes the "did activation move?" half a no-op, which is correct
  // here — nothing was just built, so there is nothing for it to have moved to.
  process.exitCode = (await verifyLive(activeBefore, activeBefore)) ? 0 : 1;
} else {
  await deploy();
}

async function deploy() {

const excludes = ["--exclude=.appwrite", "--exclude=test"];
if (!withNodeModules) excludes.push("--exclude=node_modules");
execFileSync("tar", [...excludes, "-czf", tarball, "-C", srcDir, "."], { stdio: "inherit" });
const bytes = fs.statSync(tarball).size;
console.log(`  archive ${bytes} bytes`);

try {
  const body = await upload(fs.readFileSync(tarball), path.basename(tarball));
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
    const live = await verifyLive(body.$id, activeBefore);
    process.exitCode = live ? 0 : 1;
    // Only once the new build is proven to serve: a failed verify leaves every older deployment
    // in place, because one of them is the rollback.
    if (live) await pruneDeployments(new Set([body.$id, activeBefore]));
  }
} finally {
  fs.rmSync(tarball, { force: true });
}
}

/**
 * Upload the archive, chunked when it is large enough that the edge will refuse it whole.
 *
 * A single-shot 13MB multipart POST is rejected by the CDN in front of Appwrite with
 * `503 backend write error` (Varnish) in ~55ms — no bytes reach Appwrite, and retrying does
 * nothing because it is a size limit, not congestion. `bouncer-highlights-sync` hit this the first
 * time it shipped `node_modules`. Appwrite's own SDKs chunk at 5MB; this does the same.
 *
 * The deployment id has to be minted here rather than sent as `unique()`: Appwrite echoes back
 * whatever `x-appwrite-id` it was given, so the literal string `unique()` comes back as the id and
 * every chunk after the first is rejected for a malformed job id.
 */
async function upload(buf, filename) {
  const CHUNK = 5 * 1024 * 1024;
  const headers = {
    "X-Appwrite-Project": projectId,
    "X-Appwrite-Key": apiKey,
    "X-Appwrite-Response-Format": "1.8.0",
  };
  const chunked = buf.length > CHUNK;
  const deploymentId = chunked ? `${functionId.replace(/^bouncer-/, "")}-${Date.now().toString(36)}`.slice(0, 36) : null;

  let body;
  for (let start = 0; start < buf.length; start += CHUNK) {
    const end = Math.min(start + CHUNK, buf.length) - 1;
    const form = new FormData();
    form.append("entrypoint", fn.entrypoint);
    if (fn.commands) form.append("commands", fn.commands);
    form.append("activate", "true");
    form.append("code", new Blob([buf.subarray(start, end + 1)]), filename);

    const res = await fetch(`${endpoint}/functions/${functionId}/deployments`, {
      method: "POST",
      headers: chunked
        ? { ...headers, "content-range": `bytes ${start}-${end}/${buf.length}`, "x-appwrite-id": deploymentId }
        : headers,
      body: form,
    });
    const text = await res.text();
    if (!res.ok) {
      // Thrown, not process.exit'd, so the caller's finally still removes the tarball. The raw text
      // is kept rather than JSON.parse'd: the interesting failures here answer HTML, not JSON.
      throw new Error(`create deployment HTTP ${res.status}: ${text.slice(0, 300)}`);
    }
    body = JSON.parse(text);
    if (chunked) console.log(`  chunk ${start}-${end}/${buf.length} ok`);
  }
  return body;
}

/**
 * Delete every deployment of this function except the ones in `keep` (the one now serving and the
 * one it replaced, which is the rollback target).
 *
 * Deployment source and build artifacts count toward the plan's storage, and nothing else removes
 * them: by October 2026 `bouncer-match-sync` alone held 190 deployments and 4.8 GB, against a
 * 2 GB Free-plan cap whose breach locks the Console. A failed delete is reported and never fails
 * the deploy — the function is already serving.
 */
async function pruneDeployments(keep) {
  const headers = { "X-Appwrite-Project": projectId, "X-Appwrite-Key": apiKey };
  const q = [{ method: "limit", values: [100] }, { method: "orderDesc", values: ["$createdAt"] }]
    .map((o) => "queries[]=" + encodeURIComponent(JSON.stringify(o)))
    .join("&");
  const res = await fetch(`${endpoint}/functions/${functionId}/deployments?${q}`, { headers });
  if (!res.ok) {
    console.error(`  could not list deployments to prune (HTTP ${res.status})`);
    return;
  }
  const { deployments = [] } = await res.json();
  // A deployment still building may be a concurrent VCS build; it is not ours to remove.
  const stale = deployments.filter((d) => !keep.has(d.$id) && (d.status === "ready" || d.status === "failed"));
  let removed = 0;
  for (const d of stale) {
    const del = await fetch(`${endpoint}/functions/${functionId}/deployments/${encodeURIComponent(d.$id)}`, {
      method: "DELETE",
      headers,
    });
    if (del.ok) removed++;
    else console.error(`  could not delete deployment ${d.$id} (HTTP ${del.status})`);
  }
  console.log(`  pruned ${removed} old deployment(s), kept ${[...keep].filter(Boolean).join(", ")}`);
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
  // In --verify-only these are the same id by construction; only a real deploy "moved" anything.
  if (deploymentId !== before) console.log(`  active deployment moved ${before ?? "(none)"} -> ${deploymentId}`);

  // Scheduled functions produce their own traffic; on-demand ones need a nudge, and a real
  // invocation is the point — a synthetic one that skips the handler would prove nothing.
  // A function clients may execute is on-demand even when it also has a schedule
  // (`bouncer-player-search` rebuilds its index every third day): its cron cannot fall inside
  // any wait worth having, and a `{}` body takes its ordinary request path. Schedule-only
  // functions are never nudged — a POST to the prune or the trainer would run the job.
  const onDemand = (fn.$permissions ?? []).some((p) => p.startsWith("execute("));
  const scheduled = Boolean(fn.schedule) && !onDemand;
  if (!scheduled) {
    await fetch(`${endpoint}/functions/${functionId}/executions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Appwrite-Project": projectId, "X-Appwrite-Key": apiKey },
      body: JSON.stringify({ body: "{}", async: false, method: "POST" }),
    }).catch(() => {});
  }

  // 150s was sized for `* * * * *`. `bouncer-highlights-sync` runs `*/15` and the check reported
  // FAILED on a deployment that was in fact serving — a verifier that cries wolf gets ignored,
  // which costs more than the wait. Derive the window from the schedule's minute field instead.
  const everyN = Number(/^\*\/(\d+)/.exec(fn.schedule ?? "")?.[1]);
  const waitMs = scheduled ? Math.min((Number.isFinite(everyN) ? everyN + 2 : 2) * 60_000, 17 * 60_000) : 20_000;
  const until = Date.now() + waitMs;
  console.log(`  waiting up to ${waitMs / 1000}s for an execution on it${scheduled ? ` (schedule ${fn.schedule})` : ""}…`);
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
