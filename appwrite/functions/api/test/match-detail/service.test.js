import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createDetailService, ttlFor, validMatchId } from '../../src/match-detail/service.js';
import { cleanupDetailCache } from '../../src/match-detail/cleanup.js';
let clock = 1_000_000;
function setup(overrides = {}, options = {}) {
  let calls = 0, writes = [];
  const deps = { readCache: async () => null, writeCache: async (id, value) => writes.push(value),
    readLists: async () => [{ summary: { id: '123', state: 'Live' }, state: 'live', fetchedAt: clock }],
    readSeeds: async () => ({}), fetchSections: async () => { calls++; return { detail: { score: '10/0' }, scorecard: { innings: [{}] } }; },
    isFinalized: async () => false, ...overrides };
  return { run: createDetailService(deps, { now: () => clock, ...options }), calls: () => calls, writes };
}
test('strict IDs and TTLs', () => {
  for (const id of [123, '', '123x', '1/2', '1234567890123']) assert.equal(validMatchId(id), false);
  assert.equal(validMatchId('123456789012'), true);
  assert.equal(ttlFor('Live'), 60000); assert.equal(ttlFor('Preview'), 600000);
  assert.equal(ttlFor('Complete'), 600000); assert.equal(ttlFor('Complete', true), 86400000);
});
test('cold membership and malformed IDs never reach upstream', async () => {
  const s = setup(); assert.equal((await s.run('no')).status, 400);
  assert.equal((await s.run('456')).status, 404); assert.equal(s.calls(), 0);
  const down = setup({ readLists: async () => { throw new Error('402'); } });
  assert.equal((await down.run('123')).status, 503); assert.equal(down.calls(), 0);
});
test('parallel duplicate requests single-flight and warm response avoids upstream', async () => {
  const s = setup(); const result = await Promise.all([s.run('123'), s.run('123'), s.run('123')]);
  assert.equal(s.calls(), 1); assert.equal(result[0].body.detail.score, '10/0');
  assert.equal((await s.run('123')).status, 200); assert.equal(s.calls(), 1);
});
test('partial refresh preserves genuine old scorecard time and backs off', async () => {
  const oldAt = clock - 120000;
  const s = setup({ readCache: async () => ({ state: 'Live', times: { scorecard: oldAt }, scorecard: { innings: [{ team: 'Old' }] } }),
    fetchSections: async () => ({ detail: { score: '20/1' } }) });
  const { body } = await s.run('123');
  assert.equal(body.scorecard.innings[0].team, 'Old'); assert.equal(body.availability.scorecard.fetchedAt, oldAt);
  assert.equal(body.availability.scorecard.stale, true); assert.equal(body.availability.detail.stale, false);
  assert.equal(body.retryAfterSeconds, 90); assert.equal(s.writes.length, 1);
});
test('seed freshness is producer time and empty parses preserve it', async () => {
  const oldAt = clock - 300000;
  const s = setup({ readSeeds: async () => ({ detail: { value: { score: '5/0' }, at: oldAt } }), fetchSections: async () => ({ detail: { batsmen: [] }, scorecard: { innings: [] } }) });
  const { body } = await s.run('123'); assert.equal(body.detail.score, '5/0');
  assert.equal(body.availability.detail.fetchedAt, oldAt); assert.equal(body.availability.scorecard.available, false);
});
test('cached IDs can survive list removal without arbitrary upstream', async () => {
  const s = setup({ readCache: async () => ({ detail: { score: '7/0' }, times: {} }), readLists: async () => [] });
  assert.equal((await s.run('123')).status, 200); assert.equal(s.calls(), 0);
});
test('attempt budget claims before upstream and returns summary fallback', async () => {
  const s = setup({ readLists: async () => ['123', '456'].map(id => ({ summary: { id }, state: 'live', fetchedAt: clock })) }, { attemptLimit: 1 });
  await s.run('123'); const result = await s.run('456');
  assert.equal(s.calls(), 1); assert.equal(result.status, 200); assert.equal(result.body.retryAfterSeconds, 600);
});
test('bounded memo evicts oldest ID', async () => {
  let reads = 0;
  const s = setup({ readCache: async () => { reads++; return null; }, readLists: async () => ['123', '456'].map(id => ({ summary: { id }, state: 'live', fetchedAt: clock })) }, { capacity: 1 });
  await s.run('123'); await s.run('456'); await s.run('123'); assert.equal(reads, 3);
});
test('completed cache extends only with archive-final evidence', async () => {
  const s = setup({ readLists: async () => [{ summary: { id: '123', state: 'Complete' }, state: 'Complete', fetchedAt: clock }], isFinalized: async () => true });
  await s.run('123'); assert.equal(s.writes[0].expiresAt - clock, 86400000);
});
test('fixture has exact envelope and honest per-section availability', async () => {
  const f = JSON.parse(await readFile(new URL('../fixtures/match-detail.json', import.meta.url)));
  assert.equal(f.schemaVersion, 1); assert.equal(f.commentary[0].aiGenerated, false);
  assert.equal(f.availability.scorecard.available, false); assert.equal(f.availability.detail.fetchedAt, f.fetchedAt);
});
test('private cleanup limits candidates and ignores foreign row IDs', async () => {
  let deleted = [];
  const q = { lessThan: (...args) => args, orderAsc: x => x, limit: x => x, select: x => x };
  const db = { listRows: async ({ databaseId, tableId, queries, total }) => {
    assert.equal(databaseId, 'bouncer-db'); assert.equal(tableId, 'match-detail-cache');
    assert.equal(queries[2], 20); assert.equal(total, false); assert.deepEqual(queries[3], ['$id']);
    return { rows: [{ $id: 'md-123' }, { $id: 'active-viewers' }] };
  }, deleteRow: async ({ databaseId, tableId, rowId }) => {
    assert.equal(databaseId, 'bouncer-db'); assert.equal(tableId, 'match-detail-cache'); deleted.push(rowId);
  } };
  assert.equal(await cleanupDetailCache(db, q), 1); assert.deepEqual(deleted, ['md-123']);
});
