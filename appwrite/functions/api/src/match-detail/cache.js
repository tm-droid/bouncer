export const CACHE_TABLE = process.env.MATCH_DETAIL_CACHE_TABLE_ID ?? 'match-detail-cache';
export const CACHE_DATABASE = process.env.APPWRITE_AI_CACHE_DB_ID ?? 'bouncer-db';
export const MAX_CACHE_BYTES = 1_048_576;

// A separate server-only table; never write the canonical producer's rows.
export function detailCache(db, { databaseId = CACHE_DATABASE, tableId = CACHE_TABLE } = {}) {
  return {
    async read(id) {
      const row = await db.getRow({ databaseId, tableId, rowId: `md-${id}` });
      return JSON.parse(row.payload);
    },
    async write(id, value) {
      const payload = JSON.stringify(value);
      if (Buffer.byteLength(payload, 'utf8') > MAX_CACHE_BYTES) throw new Error('detail cache payload exceeds 1 MiB');
      await db.upsertRow({ databaseId, tableId, rowId: `md-${id}`, data: { payload }, permissions: [] });
    },
  };
}
