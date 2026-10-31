// Run every maintenance tick, independently of the commentary billing-window gate.
export async function cleanupDetailCache(storage, Query, { now = Date.now(), bucket = process.env.MATCH_DETAIL_CACHE_BUCKET_ID ?? 'match-detail-cache' } = {}) {
  const deadline = Date.now() + 3000;
  let timer;
  const work = async () => {
    const result = await storage.listFiles(bucket, [Query.lessThan('$updatedAt', new Date(now - 14 * 86_400_000).toISOString()), Query.orderAsc('$updatedAt'), Query.limit(20)]);
    let deleted = 0;
    for (const file of result.files ?? []) {
      if (Date.now() >= deadline) break;
      if (!/^md-\d{1,12}$/.test(file.$id)) continue;
      await storage.deleteFile(bucket, file.$id); deleted++;
    }
    return deleted;
  };
  try { return await Promise.race([work(), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('detail cleanup timeout')), 3000); })]); }
  finally { clearTimeout(timer); }
}
