import { Client, TablesDB, Query } from "node-appwrite-26";

// Daily retention prune for the match-commentary table. Nothing else deletes these rows, so
// without this the table grows one row per ball of every match forever (it had ~500k+ rows).
const DB_ID = process.env.APPWRITE_AI_CACHE_DB_ID ?? "bouncer-db";
const COMMENTARY_COL = process.env.MATCH_COMMENTARY_COL_ID ?? "match-commentary";
const RETENTION_DAYS = Number(process.env.COMMENTARY_RETENTION_DAYS ?? "14");

// Safety caps: keep a single run inside the function timeout and prevent runaway deletion.
// Steady-state a day adds only a few thousand rows; MAX_PASSES lets a large initial backlog
// drain over several days (raise COMMENTARY_PRUNE_MAX_PASSES temporarily to drain faster).
// BATCH is also the bulk-operation row cap on the Free plan (1,000 on Pro); asking for more than
// the plan allows fails the whole request, so it stays at the lower one.
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
  const db = new TablesDB(client);

  const cutoff = new Date(Date.now() - RETENTION_DAYS * 24 * 60 * 60 * 1000).toISOString();
  let deleted = 0;
  let passes = 0;

  try {
    for (; passes < MAX_PASSES; passes++) {
      // One bulk delete per pass, filtered server-side on the built-in $createdAt (always
      // queryable — no user index needed). This used to list 100 rows, filter the cutoff in memory
      // and delete them one call each: every stale row cost a billed READ before its write, ~6,700
      // reads a day, the largest read cost in the project. The cutoff now travels in the query, so
      // nothing is read back.
      const result = await db.deleteRows({
        databaseId: DB_ID,
        tableId: COMMENTARY_COL,
        queries: [
          Query.lessThan("$createdAt", cutoff),
          Query.orderAsc("$createdAt"),
          Query.limit(BATCH),
        ],
      });
      const n = result?.total ?? result?.rows?.length ?? 0;
      deleted += n;
      // A short batch means everything older than the cutoff is gone.
      if (n < BATCH) break;
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
