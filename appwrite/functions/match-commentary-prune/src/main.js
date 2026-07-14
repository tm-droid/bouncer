import { Client, Databases, Query } from "node-appwrite";

// Daily retention prune for the match-commentary table. Nothing else deletes these rows, so
// without this the table grows one row per ball of every match forever (it had ~500k+ rows).
const DB_ID = process.env.APPWRITE_AI_CACHE_DB_ID ?? "bouncer-db";
const COMMENTARY_COL = process.env.MATCH_COMMENTARY_COL_ID ?? "match-commentary";
const RETENTION_DAYS = Number(process.env.COMMENTARY_RETENTION_DAYS ?? "14");

// Safety caps: keep a single run inside the function timeout and prevent runaway deletion.
// Steady-state a day adds only a few thousand rows; MAX_PASSES lets a large initial backlog
// drain over several days (raise COMMENTARY_PRUNE_MAX_PASSES temporarily to drain faster).
const BATCH = 100;
const MAX_PASSES = Number(process.env.COMMENTARY_PRUNE_MAX_PASSES ?? "100");

const isBudget402 = (e) => e?.code === 402 || e?.type === "limit_databases_reads_exceeded";

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }
  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  const db = new Databases(client);

  const cutoff = new Date(Date.now() - RETENTION_DAYS * 24 * 60 * 60 * 1000).toISOString();
  let deleted = 0;
  let passes = 0;

  try {
    for (; passes < MAX_PASSES; passes++) {
      // Order by the built-in $createdAt (always orderable — no user index needed) so the
      // oldest rows come first. Filter the cutoff in memory: because the page is ascending,
      // the first row newer than the cutoff means every remaining row is newer too → stop.
      const result = await db.listDocuments(DB_ID, COMMENTARY_COL, [
        Query.orderAsc("$createdAt"),
        Query.limit(BATCH),
      ]);
      const rows = result.documents ?? [];
      if (rows.length === 0) break;

      const stale = rows.filter((r) => r.$createdAt < cutoff);
      if (stale.length === 0) break; // oldest row is within retention → nothing to prune

      await Promise.all(
        stale.map((r) =>
          db.deleteDocument(DB_ID, COMMENTARY_COL, r.$id).catch((e) => {
            if (e?.code !== 404) error(`Delete failed for ${r.$id}: ${e?.message}`);
          })
        )
      );
      deleted += stale.length;

      // A page that was only partly stale, or shorter than a full batch, means we've reached
      // the retention boundary (or the end of the table) — no more full stale pages to fetch.
      if (stale.length < rows.length || rows.length < BATCH) break;
    }
  } catch (e) {
    if (isBudget402(e)) {
      error(`[READ-BUDGET-402] read budget exhausted — pruned ${deleted} before aborting`);
      return res.json({ error: "read_budget_exceeded", deleted }, 402);
    }
    error(`Prune failed after ${deleted} deletions: ${e?.message}`);
    return res.json({ error: e?.message, deleted }, 500);
  }

  log(`Pruned ${deleted} match-commentary rows older than ${RETENTION_DAYS}d (${passes} passes)`);
  return res.json({ deleted, retentionDays: RETENTION_DAYS, passes });
};
