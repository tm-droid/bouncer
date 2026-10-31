import { CACHE_DATABASE, CACHE_TABLE } from './cache.js';
// Run every maintenance tick, independently of the commentary billing-window gate.
export async function cleanupDetailCache(db, Query, { now = Date.now(), databaseId = CACHE_DATABASE, tableId = CACHE_TABLE } = {}) {
  const deadline = Date.now() + 3000;
  let timer;
  const work = async () => {
    const result = await db.listRows({ databaseId, tableId, queries: [Query.lessThan('$updatedAt', new Date(now - 14 * 86_400_000).toISOString()), Query.orderAsc('$updatedAt'), Query.limit(20), Query.select(['$id'])], total: false });
    let deleted = 0;
    for (const row of result.rows ?? []) {
      if (Date.now() >= deadline) break;
      if (!/^md-\d{1,12}$/.test(row.$id)) continue;
      await db.deleteRow({ databaseId, tableId, rowId: row.$id }); deleted++;
    }
    return deleted;
  };
  try { return await Promise.race([work(), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('detail cleanup timeout')), 3000); })]); }
  finally { clearTimeout(timer); }
}
