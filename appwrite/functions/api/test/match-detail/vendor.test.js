import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
for (const name of ['cricbuzz.js', 'nextflight.js', 'cricbuzz-http.js', 'outcome.js']) {
  test(`deployed API parser matches canonical ${name}`, async () => {
    const [canonical, copy] = await Promise.all([
      readFile(new URL(`../../../match-sync/src/${name}`, import.meta.url), 'utf8'),
      readFile(new URL(`../../src/match-detail/parser/${name}`, import.meta.url), 'utf8'),
    ]);
    assert.equal(copy, canonical);
    assert.doesNotMatch(copy, /from ["'].*llm\.js/);
  });
}
