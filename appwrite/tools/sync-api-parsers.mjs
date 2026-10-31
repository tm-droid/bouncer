// Generated copies must live inside each independently deployed function archive.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
const names = ['cricbuzz.js', 'nextflight.js', 'cricbuzz-http.js', 'outcome.js'];
const root = new URL('../functions/', import.meta.url);
const target = new URL('api/src/match-detail/parser/', root);
await mkdir(target, { recursive: true });
let drift = false;
for (const name of names) {
  const source = await readFile(new URL(`match-sync/src/${name}`, root), 'utf8');
  if (process.argv.includes('--check')) {
    const copy = await readFile(new URL(name, target), 'utf8').catch(() => null);
    if (source !== copy) { console.error(`API parser drift: ${name}`); drift = true; }
  } else await writeFile(new URL(name, target), source);
}
if (drift) process.exitCode = 1;
