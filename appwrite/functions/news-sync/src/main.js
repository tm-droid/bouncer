import { Client, Query, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { parseJsonFile } from "./storageJson.js";
import { CACHE_TTL_MS, cacheAgeMs, isFresh, staleFallback } from "./newsResult.js";
import {
  BODY_ID_PREFIX,
  BODY_PRUNE_MAX,
  bodyFileId,
  extractParagraphs,
  listedStoryIds,
  needsRefetch,
  selectBodiesToPrune,
  withoutTokens,
  shapeHint,
} from "./articleBody.js";

const NEWS_CACHE_FILE_ID = "news-cache";
let cacheMemo = null; // warm-container copy of the headline cache

// Upstream body fetches this container has made, by story id and time.
//
// A story is asked for at most once per FETCH_HOLD_MS, whatever came of the request, and at most
// MAX_FETCHES_PER_HOLD stories are asked for in that window. The claim is taken BEFORE the request
// is sent and with no `await` between the check and the mark, so requests arriving together cannot
// all pass the check. It records attempts, not failures: a fetch that succeeds but whose body
// could not be stored would otherwise be repeated by every request after it.
//
// Per container. Several containers each have their own, so this bounds the spend rather than
// fixing it at one call.
const FETCH_HOLD_MS = 10 * 60 * 1000;
const MAX_FETCHES_PER_HOLD = 40;
const fetchAttempts = new Map();

/**
 * Takes the right to fetch [storyId] upstream now. False when it was asked for inside the hold, or
 * the container has used its allowance for the window.
 */
export function claimFetch(storyId, now, attempts = fetchAttempts) {
  for (const [id, at] of attempts) if (now - at >= FETCH_HOLD_MS) attempts.delete(id);
  if (attempts.has(storyId) || attempts.size >= MAX_FETCHES_PER_HOLD) return false;
  attempts.set(storyId, now);
  return true;
}

// Bodies fetched by this container, so one that could not be written to Storage is still served
// for the rest of its hold instead of answering 503. Bounded like the claims.
const fetchedBodies = new Map();

function rememberBody(storyId, paragraphs) {
  fetchedBodies.delete(storyId);
  fetchedBodies.set(storyId, paragraphs);
  while (fetchedBodies.size > MAX_FETCHES_PER_HOLD) fetchedBodies.delete(fetchedBodies.keys().next().value);
}

// Enough of the upstream body to identify the failure, short enough not to flood the log.
const UPSTREAM_ERROR_LOG_CHARS = 200;

const RAPIDAPI_HOST = "cricbuzz-cricket.p.rapidapi.com";
const UPSTREAM_TIMEOUT_MS = 15_000;

const ageLabel = (ageMs) => (ageMs === null ? "age unknown" : `${Math.round(ageMs / 60000)}m`);

const rapidApiHeaders = (key) => ({
  "x-rapidapi-key": key,
  "x-rapidapi-host": RAPIDAPI_HOST,
});

/**
 * The story id this request is asking for, or null for the headline index.
 *
 * The device sends `{"storyId": 140155}` for a body and an EMPTY STRING for headlines
 * (`CricbuzzNewsRepository.fetchHeadlines` → `executeFunctionDetailed(FUNCTION_ID, "")`).
 *
 * **`req.bodyJson` is a getter that throws on an empty body** — it is `JSON.parse(req.bodyRaw)`
 * inside the Appwrite runtime, so reading it at all on the headline path raised
 * `SyntaxError: Unexpected end of JSON input` and the whole function answered 500. `??` cannot help:
 * the throw happens on property access, before any operator sees a value. Every read of it is
 * therefore wrapped, and the raw body is parsed by hand as the fallback. Anything unparseable means
 * "no story id", which is the headline path — never an error, because that is what every caller of
 * it has always sent.
 */
function requestedStoryId(req) {
  const raw = bodyObject(req);
  const id = raw?.storyId;
  if (typeof id === "number" && Number.isFinite(id)) return String(Math.trunc(id));
  if (typeof id === "string" && /^\d+$/.test(id)) return id;
  return null;
}

/** The request body as an object, or null. Never throws, whatever the runtime hands us. */
export function bodyObject(req) {
  try {
    const parsed = req?.bodyJson;
    if (parsed && typeof parsed === "object") return parsed;
  } catch {
    // Empty or malformed body — fall through to the raw forms below.
  }
  for (const raw of [req?.bodyRaw, req?.body]) {
    if (!raw || typeof raw !== "string") continue;
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === "object") return parsed;
    } catch {
      // Not JSON. Headline path.
    }
  }
  return null;
}

export default async ({ req, res, log, error }) => {
  const RAPIDAPI_KEY = process.env.RAPIDAPI_KEY ?? "";
  const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY ?? "");
  const storage = new Storage(client);

  const storyId = requestedStoryId(req);
  if (storyId) {
    return serveArticleBody({ storyId, storage, STATS_BUCKET_ID, RAPIDAPI_KEY, res, log, error });
  }

  // ── Read the cache once ──────────────────────────────────────────────────
  // It has two jobs: a fresh hit returns immediately, and a stale one is the fallback for every
  // failure below. It is read before the key check for that second reason — a function that is
  // misconfigured can still serve what it already has.
  // A warm container that still holds a fresh copy answers without downloading it again. Every
  // headline call used to download the file, fresh or not. This function is the file's only
  // writer, so the memo cannot be older than the file while it is fresh.
  const now = Date.now();
  if (isFresh(cacheMemo, now)) {
    log(`Returning cached news from memory (age: ${ageLabel(cacheAgeMs(cacheMemo, now))})`);
    return res.json(cacheMemo.response);
  }
  let cached = null;
  try {
    cached = parseJsonFile(await storage.getFileDownload(STATS_BUCKET_ID, NEWS_CACHE_FILE_ID));
    cacheMemo = cached;
  } catch (_) {
    log("No cache found — fetching fresh");
  }

  if (isFresh(cached, now)) {
    log(`Returning cached news (age: ${ageLabel(cacheAgeMs(cached, now))})`);
    return res.json(cached.response);
  }
  if (cached) {
    log(`Cache stale (${ageLabel(cacheAgeMs(cached, now))} old, TTL ${CACHE_TTL_MS / 60000}m) — fetching fresh`);
  }

  // ── Fetch from RapidAPI ──────────────────────────────────────────────────
  let newsJson = null;
  let failure = null; // { body, status } — what to return if there is nothing cached to serve

  if (!RAPIDAPI_KEY) {
    error("RAPIDAPI_KEY not set");
    failure = { body: { error: "News service not configured" }, status: 503 };
  } else {
    try {
      const response = await fetch(
        `https://${RAPIDAPI_HOST}/news/v1/index`,
        {
          headers: rapidApiHeaders(RAPIDAPI_KEY),
          signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
        }
      );
      if (!response.ok) {
        // The status alone does not say whether the plan is exhausted, throttled, or cancelled —
        // RapidAPI answers 429 to more than one of those and puts the distinction in the body.
        // Logging only the status is what left a five-day outage unattributable.
        const detail = await response.text().catch(() => "");
        error(`RapidAPI returned HTTP ${response.status}: ${detail.slice(0, UPSTREAM_ERROR_LOG_CHARS)}`);
        failure = { body: { error: `Upstream error: ${response.status}` }, status: response.status };
      } else {
        newsJson = await response.json();
      }
    } catch (e) {
      error(`RapidAPI fetch failed: ${e?.message}`);
      failure = { body: { error: "Failed to fetch news" }, status: 503 };
    }
  }

  // ── Upstream gave us nothing: serve the stale cache rather than a blank tab ───────────────
  if (failure) {
    const fallback = staleFallback(cached, now);
    if (fallback) {
      log(`Upstream failed (${failure.status}) — serving stale cache (${ageLabel(cacheAgeMs(cached, now))} old)`);
      return res.json(fallback);
    }
    return res.json(failure.body, failure.status);
  }

  // ── Write cache to Storage (non-fatal if it fails) ───────────────────────
  try {
    const payload = { fetchedAt: new Date().toISOString(), response: newsJson };
    cacheMemo = payload;
    const buffer = Buffer.from(JSON.stringify(payload), "utf8");
    await storage.deleteFile(STATS_BUCKET_ID, NEWS_CACHE_FILE_ID).catch(() => {});
    await storage.createFile(
      STATS_BUCKET_ID,
      NEWS_CACHE_FILE_ID,
      InputFile.fromBuffer(buffer, "news-cache.json"),
      ["read(\"any\")"]
    );
    log("News cache updated in Storage");
  } catch (e) {
    error(`Failed to write news cache: ${e?.message}`);
  }

  // Retention sweep rides the headline refresh rather than a schedule of its own. The 4-hour TTL
  // above already caps this path at ~6 runs a day, which is the right cadence for a sweep and costs
  // no extra function and no extra executions. It is deliberately NOT on the article-body path:
  // that one is a user waiting for an article to open.
  await pruneArticleBodies({ storage, STATS_BUCKET_ID, log, error });

  return res.json(newsJson);
};

// ── Article bodies ──────────────────────────────────────────────────────────
//
// One story's body, cached permanently. A published article's text does not change, so each story
// costs at most one RapidAPI call for the life of the bucket — which is the only reason this can be
// driven by a user tap at all. The plan's monthly quota was exhausted once already (2026-07-30) and
// an uncached per-tap fetch would sit directly against it.

/**
 * Serve the body for one story: cache, then upstream, then 503.
 *
 * It returns **503 and never an empty 200** when it has no body and cannot fetch one. The device
 * renders an empty paragraph list as "this article has no text" and a failure as a retry prompt;
 * collapsing the two is what made the broken scrape look like an article that was simply short.
 */
async function serveArticleBody({ storyId, storage, STATS_BUCKET_ID, RAPIDAPI_KEY, res, log, error }) {
  const fileId = bodyFileId(storyId);

  // A body cached before placeholders were resolved. It is fetched again at most while upstream
  // answers; whenever it cannot be, this is what is served, with the placeholders taken out. It
  // must never become a reason to call upstream on every request.
  let unresolved = null;
  const serveUnresolved = (why) => {
    if (!unresolved) return null;
    log(`Story ${storyId}: ${why} — serving the cached body without its placeholders`);
    return res.json({ storyId, paragraphs: unresolved });
  };

  // parseJsonFile, never Buffer.from — see storageJson.js. The unconditional Buffer.from is what
  // made bouncer-player-search rebuild its whole index on every single query.
  try {
    const cached = parseJsonFile(await storage.getFileDownload(STATS_BUCKET_ID, fileId));
    if (Array.isArray(cached?.paragraphs) && cached.paragraphs.length > 0) {
      if (!needsRefetch(cached)) {
        log(`Story ${storyId}: cache hit (${cached.paragraphs.length} paragraphs)`);
        return res.json({ storyId, paragraphs: cached.paragraphs });
      }
      unresolved = withoutTokens(cached.paragraphs);
    }
  } catch (_) {
    log(`Story ${storyId}: no cached body — fetching`);
  }

  // Upstream is asked only about a story this function has listed. See listedStoryIds.
  if (!(await isListedStory({ storyId, storage, STATS_BUCKET_ID }))) {
    const served = serveUnresolved("not in the headline index");
    if (served) return served;
    log(`Story ${storyId}: not in the headline index — not fetched`);
    return res.json({ error: "Unknown story" }, 404);
  }

  const fetched = fetchedBodies.get(storyId);
  if (fetched) {
    log(`Story ${storyId}: served from this container's memory (${fetched.length} paragraphs)`);
    return res.json({ storyId, paragraphs: fetched });
  }

  if (!RAPIDAPI_KEY) {
    error("RAPIDAPI_KEY not set");
    return serveUnresolved("no RAPIDAPI_KEY") ?? res.json({ error: "News service not configured" }, 503);
  }

  // Nothing is awaited between this claim and the request below.
  if (!claimFetch(storyId, Date.now())) {
    const served = serveUnresolved("already asked upstream minutes ago");
    if (served) return served;
    log(`Story ${storyId}: already asked upstream minutes ago, or this container's allowance is spent`);
    return res.json({ error: "Article body unavailable" }, 503);
  }

  let detail;
  try {
    const response = await fetch(
      `https://${RAPIDAPI_HOST}/news/v1/detail/${storyId}`,
      {
        headers: rapidApiHeaders(RAPIDAPI_KEY),
        signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
      }
    );
    if (!response.ok) {
      const detailText = await response.text().catch(() => "");
      error(
        `RapidAPI detail ${storyId} returned HTTP ${response.status}: ` +
        detailText.slice(0, UPSTREAM_ERROR_LOG_CHARS)
      );
      return serveUnresolved(`upstream HTTP ${response.status}`)
        ?? res.json({ error: `Upstream error: ${response.status}` }, response.status);
    }
    detail = await response.json();
  } catch (e) {
    error(`RapidAPI detail ${storyId} fetch failed: ${e?.message}`);
    return serveUnresolved("upstream unreachable") ?? res.json({ error: "Failed to fetch article" }, 503);
  }

  const paragraphs = extractParagraphs(detail);
  if (paragraphs.length === 0) {
    // An upstream shape change reads exactly like an empty article, so say which one it is. Keys
    // and block types only; the payload itself is several KB.
    error(`Story ${storyId}: no text blocks in detail payload — ${shapeHint(detail)}`);
    return serveUnresolved("no text blocks upstream") ?? res.json({ error: "Article body unavailable" }, 503);
  }

  rememberBody(storyId, paragraphs);

  // Non-fatal: a body we could not cache is still a body we can serve.
  try {
    // formatResolved: stored by code that resolves placeholders, so never fetched again for
    // looking unresolved. See needsRefetch.
    const payload = { fetchedAt: new Date().toISOString(), storyId, paragraphs, formatResolved: true };
    const buffer = Buffer.from(JSON.stringify(payload), "utf8");
    await storage.deleteFile(STATS_BUCKET_ID, fileId).catch(() => {});
    await storage.createFile(
      STATS_BUCKET_ID,
      fileId,
      InputFile.fromBuffer(buffer, `${fileId}.json`),
      ["read(\"any\")"]
    );
    log(`Story ${storyId}: cached ${paragraphs.length} paragraphs`);
  } catch (e) {
    error(`Failed to cache story ${storyId}: ${e?.message}`);
  }

  return res.json({ storyId, paragraphs });
}

/**
 * Whether [storyId] is in the headline index: the warm copy if there is one, else the stored file.
 * An index that cannot be read lists nothing — the device then falls back to its own scrape, which
 * costs no quota.
 */
async function isListedStory({ storyId, storage, STATS_BUCKET_ID }) {
  if (listedStoryIds(cacheMemo).has(storyId)) return true;
  try {
    cacheMemo = parseJsonFile(await storage.getFileDownload(STATS_BUCKET_ID, NEWS_CACHE_FILE_ID));
  } catch (_) {
    return false;
  }
  return listedStoryIds(cacheMemo).has(storyId);
}

/**
 * Delete cached bodies past their retention.
 *
 * Every failure is swallowed after logging. A sweep is a guest on a news response and must never be
 * able to fail one — the caller has the headlines in hand by the time this runs.
 */
async function pruneArticleBodies({ storage, STATS_BUCKET_ID, log, error }) {
  try {
    // startsWith on $id, never `search` — see BODY_ID_PREFIX. Oldest first, so a backlog drains in
    // the order that matters and the cap below always takes the most deserving candidates.
    const listed = await storage.listFiles(STATS_BUCKET_ID, [
      Query.startsWith("$id", BODY_ID_PREFIX),
      Query.orderAsc("$createdAt"),
      Query.limit(BODY_PRUNE_MAX),
    ]);

    const doomed = selectBodiesToPrune(listed?.files, Date.now());
    if (doomed.length === 0) return;

    let deleted = 0;
    for (const file of doomed) {
      try {
        await storage.deleteFile(STATS_BUCKET_ID, file.$id);
        deleted++;
      } catch (e) {
        error(`Failed to prune ${file.$id}: ${e?.message}`);
      }
    }
    log(`Pruned ${deleted} cached article bodies older than retention`);
  } catch (e) {
    error(`Article body prune failed: ${e?.message}`);
  }
}
