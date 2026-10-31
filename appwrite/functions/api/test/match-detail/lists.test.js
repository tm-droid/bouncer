import test from 'node:test';
import assert from 'node:assert/strict';
import { storedMatchEntries } from '../../src/match-detail/main.js';
import { createDetailService } from '../../src/match-detail/service.js';

// Production poll.js writes {data: {matches: [...]}, fetchedAt}, not {data: [...]}.
test('producer list envelopes reach detail membership for live, upcoming and recent', async () => {
  const rows = ['live', 'upcoming', 'recent'].flatMap((state, i) => {
    const summary = { id: String(170761 + i), state: state === 'recent' ? 'In Progress' : state };
    const entries = storedMatchEntries({ data: { matches: [summary] }, fetchedAt: 1234 }, state);
    assert.deepEqual(entries, [{ summary, state: state === 'recent' ? 'Complete' : state, fetchedAt: 1234 }]);
    return entries;
  });
  const service = createDetailService({ readCache: async () => null, readLists: async () => rows,
    readSeeds: async () => ({}), fetchSections: async () => ({}), isFinalized: async () => false, writeCache: async () => {} });
  for (const row of rows) {
    const r = await service(row.summary.id);
    assert.equal(r.status, 200); assert.deepEqual(r.body.summary, row.summary);
    assert.equal(r.body.state, row.state); assert.equal(r.body.availability.summary.fetchedAt, 1234);
  }
  assert.deepEqual(storedMatchEntries({ data: { matches: [] } }, 'live'), []);
});

test('malformed producer envelopes fail instead of proving an ID absent', () => {
  for (const blob of [null, { data: [] }, { data: {} }]) assert.throws(() => storedMatchEntries(blob, 'live'), /Invalid match list/);
});
