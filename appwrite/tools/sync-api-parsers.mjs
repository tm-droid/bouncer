// Generated copies must live inside each independently deployed function archive.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
const names = ['cricbuzz.js', 'nextflight.js', 'cricbuzz-http.js', 'outcome.js'];
const root = new URL('../functions/', import.meta.url);
const target = new URL('api/src/match-detail/parser/', root);
const check = process.argv.includes('--check');
await mkdir(target, { recursive: true });
let drift = false;
for (const name of names) {
  const source = await readFile(new URL(`match-sync/src/${name}`, root), 'utf8');
  if (check) {
    const copy = await readFile(new URL(name, target), 'utf8').catch(() => null);
    if (source !== copy) { console.error(`API parser drift: ${name}`); drift = true; }
  } else await writeFile(new URL(name, target), source);
}
const httpSource = await readFile(new URL('match-sync/src/cricbuzz-http.js', root), 'utf8');
const searchHttp = new URL('api/src/player-search/cricbuzz-http.js', root);
if (check) {
  if (await readFile(searchHttp, 'utf8') !== httpSource) {
    console.error('API player-search HTTP parser drift');
    drift = true;
  }
} else await writeFile(searchHttp, httpSource);
if (drift) process.exitCode = 1;
