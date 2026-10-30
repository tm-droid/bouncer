#!/usr/bin/env node
// Offline guard: public client defaults must agree with the deployment's resource names.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => JSON.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));
const client = read('./backend.json');
const deployment = read('../appwrite/appwrite.config.json');
assert.deepEqual(Object.keys(client).sort(),
  ['apiFunctionId', 'databaseId', 'endpoint', 'matchesBucketId', 'projectId', 'tables']);
for (const [key, value] of Object.entries(client)) {
  if (key === 'tables') continue;
  assert.equal(typeof value, 'string', key);
  assert(value.length > 0, `${key} must not be blank`);
}
assert.equal(client.endpoint, deployment.endpoint);
assert.equal(client.projectId, deployment.projectId);
assert(deployment.functions.some((fn) => fn.$id === client.apiFunctionId));
assert(deployment.buckets.some((bucket) => bucket.$id === client.matchesBucketId));
const expectedTables = ['aiCommentaryCache', 'favourites', 'highlights', 'highlightsMeta',
  'matchCommentary', 'matchLiveDetail', 'matchScorecard', 'playerProfiles'];
assert.deepEqual(Object.keys(client.tables).sort(), expectedTables.sort());
for (const [name, id] of Object.entries(client.tables)) {
  assert.equal(typeof id, 'string');
  assert(id.length > 0, `${name} must not be blank`);
  // player-profiles is a legacy provisioned resource absent from the CLI export. Keep its public
  // id for existing Android builds; this guard must not add or mutate cloud resources.
  if (name === 'playerProfiles') continue;
  assert(deployment.tables.some((table) => table.$id === id && table.databaseId === client.databaseId),
    `${name} does not match a deployed database/table`);
}
for (const fn of deployment.functions) {
  const vars = Object.fromEntries((fn.vars ?? []).map(({ key, value }) => [key, value]));
  for (const key of ['APPWRITE_AI_CACHE_DB_ID', 'APPWRITE_HIGHLIGHTS_DB_ID']) {
    if (vars[key]) assert.equal(vars[key], client.databaseId, `${fn.$id}.${key}`);
  }
  for (const key of ['MATCH_CACHE_BUCKET_ID', 'STATS_BUCKET_ID']) {
    if (vars[key]) assert.equal(vars[key], client.matchesBucketId, `${fn.$id}.${key}`);
  }
}
console.log('Shared public client config agrees with Appwrite deployment config.');
