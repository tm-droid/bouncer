import { Client, TablesDB, Query } from "node-appwrite-26";
import { cleanupDetailCache } from "../match-detail/cleanup.js";
import { Client as StorageClient, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { parseJsonFile } from "../presence/storageJson.js";
import { finalIdsOldestFirst, planRun } from "./budget.js";

// The end-of-cycle prune for match-commentary.
//
// It was a daily prune of rows older than 14 days, and its deletes were 28% of the project's writes
// (a bulk delete bills one write per row). Every row is now kept by the match archive
// (`.claude/rules/archive-data.md`) within three hours of being written, so deleting it is only
// about storage, and storage is not short. So the prune spends only writes that would otherwise go
// unused: once, in the last twelve hours of each billing cycle, 90% of the estimated remainder
// (`budget.js`).
//
// It deletes only rows the archive already holds: rows of a match the archive has finalised,
// created before the archive's watermark. Anything else stays, however old.
const DB_ID = process.env.APPWRITE_AI_CACHE_DB_ID ?? "bouncer-db";
const COMMENTARY_COL = process.env.MATCH_COMMENTARY_COL_ID ?? "match-commentary";
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const STATE_FILE = "prune-state";
const ARCHIVE = process.env.ARCHIVE_BASE_URL ?? "https://tm-droid.github.io/bouncer-archive/";

// The Free plan's bulk cap; asking for more fails the whole request.
const BATCH = 100;
// Appwrite's limit on values in one `equal`.
const IDS_PER_QUERY = 100;
// Inside the 300 s function timeout, with room to save the state afterwards.
const TIME_BUDGET_MS = 200_000;

const isWriteBudget402 = (e) => e?.code === 402 || /limit_databases_writes_exceeded/.test(e?.type ?? "");

async function fetchJson(path) {
  const res = await fetch(ARCHIVE + path, { headers: { "cache-control": "no-cache" } });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`archive ${path}: HTTP ${res.status}`);
  return res.json();
}

async function finalMatchIds() {
  const years = (await fetchJson("idx/years.json")) ?? {};
  const months = {};
  for (const [year, byMonth] of Object.entries(years)) {
    for (const month of Object.keys(byMonth)) months[`${year}-${month}`] = (await fetchJson(`idx/m/${year}-${month}.json`)) ?? [];
  }
  months.unknown = (await fetchJson("idx/m/unknown.json")) ?? [];
  return finalIdsOldestFirst(months);
}

export default async ({ res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  const endpoint = process.env.APPWRITE_FUNCTION_API_ENDPOINT;
  const project = process.env.APPWRITE_FUNCTION_PROJECT_ID;
  const key = process.env.APPWRITE_API_KEY;
  const db = new TablesDB(new Client().setEndpoint(endpoint).setProject(project).setKey(key));
  const storage = new Storage(new StorageClient().setEndpoint(endpoint).setProject(project).setKey(key));

  try { await cleanupDetailCache(storage, Query); }
  catch (e) { error(`Private detail cache cleanup skipped: ${e?.message}`); }

  const manifest = await fetchJson("manifest.json");
  let state = null;
  try {
    state = parseJsonFile(await storage.getFileDownload(BUCKET_ID, STATE_FILE));
  } catch {
    state = null; // the first run of the first cycle
  }
  const plan = planRun({ manifest, state, now: Date.now() });
  if (plan.action === "skip") return res.json({ skipped: plan.reason, state: plan.state ?? state });

  const started = Date.now();
  const ids = await finalMatchIds();
  let remaining = plan.remaining;
  let deleted = 0;
  let stopped = null;
  try {
    for (let i = 0; i < ids.length && remaining > 0 && !stopped; i += IDS_PER_QUERY) {
      const chunk = ids.slice(i, i + IDS_PER_QUERY);
      while (remaining > 0) {
        if (Date.now() - started > TIME_BUDGET_MS) {
          stopped = "time";
          break;
        }
        const limit = Math.min(BATCH, remaining);
        const result = await db.deleteRows({
          databaseId: DB_ID,
          tableId: COMMENTARY_COL,
          queries: [
            Query.equal("match_id", chunk),
            Query.lessThan("$createdAt", plan.cutoff),
            Query.orderAsc("$createdAt"),
            Query.limit(limit),
          ],
        });
        const n = result?.total ?? result?.rows?.length ?? 0;
        deleted += n;
        remaining -= n;
        if (n < limit) break;
      }
    }
  } catch (e) {
    stopped = isWriteBudget402(e) ? "write_budget" : "error";
    error(`Prune stopped after ${deleted} deletions: ${e?.message}`);
  }

  // Saved whatever happened: an uncounted delete is a write the next run would spend again.
  state = { ...plan.state, pruned: plan.state.pruned + deleted, updatedAt: new Date().toISOString() };
  try {
    await storage.deleteFile(BUCKET_ID, STATE_FILE).catch(() => {});
    await storage.createFile(BUCKET_ID, STATE_FILE, InputFile.fromBuffer(Buffer.from(JSON.stringify(state)), "prune-state.json"));
  } catch (e) {
    error(`Prune state not saved (pruned ${state.pruned} of ${state.budget}): ${e?.message}`);
  }

  log(`Pruned ${deleted} archived match-commentary rows; ${state.pruned} of ${state.budget} this cycle${stopped ? ` (stopped: ${stopped})` : ""}`);
  return res.json({ deleted, state, stopped }, stopped === "error" ? 500 : 200);
};
