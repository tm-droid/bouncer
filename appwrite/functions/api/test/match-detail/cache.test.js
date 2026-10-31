import test from 'node:test';
import assert from 'node:assert/strict';
import { detailCache, MAX_CACHE_BYTES } from '../../src/match-detail/cache.js';

test('private table round-trip preserves large payloads and uses one atomic upsert', async () => {
  let row, writes = 0;
  const target = { databaseId: 'bouncer-db', tableId: 'match-detail-cache', rowId: 'md-123' };
  const cache = detailCache({
    async upsertRow({ data, permissions, ...args }) {
      assert.deepEqual(args, target); assert.deepEqual(permissions, []);
      row = data; writes++;
    },
    async getRow(args) { assert.deepEqual(args, target); return row; },
  });
  const value = { detail: { status: 'é'.repeat(40_000) }, times: { detail: 1234 }, retryAt: 42 };
  await cache.write('123', value);
  assert.deepEqual(await cache.read('123'), value);
  await cache.write('123', { ...value, retryAt: 55 });
  assert.equal((await cache.read('123')).retryAt, 55); assert.equal(writes, 2);
});

test('oversized UTF-8 payload never replaces the last good private row', async () => {
  let writes = 0;
  const cache = detailCache({ upsertRow: async () => { writes++; } });
  await assert.rejects(cache.write('123', { status: 'é'.repeat(MAX_CACHE_BYTES / 2) }), /exceeds 1 MiB/);
  assert.equal(writes, 0);
});
