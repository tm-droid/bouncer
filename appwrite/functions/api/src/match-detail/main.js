import { Client, Databases, Storage } from 'node-appwrite';
import { Client as TableClient, TablesDB } from 'node-appwrite-26';
import { detailCache } from './cache.js';
import { parseJsonFile } from '../presence/storageJson.js';
import { createDetailService, validMatchId } from './service.js';
import { fetchHtml, liveScoresUrl, scorecardUrl, parseLiveDetail, parseScorecard, parseMatchMeta, parseCommentary } from './parser/cricbuzz.js';

const LISTS = process.env.MATCH_CACHE_BUCKET_ID ?? 'cricket-stats';
const DB = process.env.APPWRITE_AI_CACHE_DB_ID ?? 'bouncer-db';
// The SDK has no per-call cancellation in v15. Bound every await and check the absolute deadline
// before starting another operation; upstream HTTP itself is abortable and performs one attempt.
async function bounded(work, deadlineAt, maxMs = 1500) {
  const ms = Math.min(maxMs, deadlineAt - Date.now());
  if (ms <= 0) throw new Error('match-detail deadline exceeded');
  let timer;
  try { return await Promise.race([work(), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('match-detail dependency timeout')), ms); })]); }
  finally { clearTimeout(timer); }
}
function dependencies() {
  const client = new Client().setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID).setKey(process.env.APPWRITE_API_KEY);
  const storage = new Storage(client), db = new Databases(client);
  const cache = detailCache(new TablesDB(new TableClient().setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID).setKey(process.env.APPWRITE_API_KEY)));
  return {
    readCache: (id, end) => bounded(() => cache.read(id), end),
    writeCache: (id, value, end) => bounded(() => cache.write(id, value), end, 1000),
    async readLists(end) {
      const groups = await Promise.all(['live', 'upcoming', 'recent'].map(async state => {
        const blob = await bounded(async () => parseJsonFile(await storage.getFileDownload(LISTS, `${state}-matches`)), end);
        if (!Array.isArray(blob?.data)) throw new Error('Invalid match list');
        return blob.data.map(summary => ({ summary, state: state === 'recent' ? 'Complete' : state, fetchedAt: typeof blob.fetchedAt === 'number' ? blob.fetchedAt : null }));
      }));
      return groups.flat();
    },
    async readSeeds(id, end) {
      const rows = await Promise.all(['detail', 'scorecard'].map(async key => {
        try {
          const table = key === 'detail' ? process.env.MATCH_LIVE_DETAIL_COL_ID ?? 'match-live-detail' : process.env.MATCH_SCORECARD_COL_ID ?? 'match-scorecard';
          const row = await bounded(() => db.getDocument(DB, table, id), end);
          const value = JSON.parse(key === 'detail' ? row.payload : row.scorecard);
          // updated_at reflects the actual canonical producer, never this download's clock.
          const at = Date.parse(row.updated_at ?? row.$updatedAt);
          return [key, { value, at: Number.isFinite(at) ? at : null }];
        } catch { return [key, null]; }
      }));
      return Object.fromEntries(rows);
    },
    async fetchSections(id, end) {
      const [live, score] = await Promise.allSettled([
        bounded(() => fetchHtml(liveScoresUrl(id), 5000, { deadlineAt: end, maxAttempts: 1 }), end, 5500),
        bounded(() => fetchHtml(scorecardUrl(id), 5000, { deadlineAt: end, maxAttempts: 1 }), end, 5500),
      ]);
      const result = {};
      if (live.status === 'fulfilled') {
        result.detail = parseLiveDetail(live.value);
        result.meta = parseMatchMeta(live.value);
        result.commentary = parseCommentary(live.value).slice(0, 48).map(c => ({ ...c, source: 'cricbuzz', aiGenerated: false }));
      }
      if (score.status === 'fulfilled') result.scorecard = parseScorecard(score.value);
      return result;
    },
    async isFinalized(id, end) {
      return bounded(async () => {
        const url = new URL(`m/${id}.json`, process.env.ARCHIVE_BASE_URL ?? 'https://tm-droid.github.io/bouncer-archive/');
        const response = await fetch(url, { signal: AbortSignal.timeout(Math.max(1, Math.min(750, end - Date.now()))) });
        return response.ok && (await response.json()).final === 1;
      }, end, 800);
    },
  };
}
let service;
export default async ({ req, res }) => {
  if (req.method && req.method !== 'POST') return res.json({ error: 'method_not_allowed' }, 405);
  let body;
  try { body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body; }
  catch { return res.json({ error: 'invalid_json' }, 400); }
  if (!validMatchId(body?.matchId)) return res.json({ error: 'invalid_match_id' }, 400);
  if (!process.env.APPWRITE_API_KEY) return res.json({ error: 'service_unavailable' }, 503);
  service ??= createDetailService(dependencies());
  const result = await service(body?.matchId);
  return res.json(result.body, result.status);
};
