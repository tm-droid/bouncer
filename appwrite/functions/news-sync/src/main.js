import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { parseJsonFile } from "./storageJson.js";
import { CACHE_TTL_MS, cacheAgeMs, isFresh, staleFallback } from "./newsResult.js";
import { bodyFileId, extractParagraphs, shapeHint } from "./articleBody.js";

const NEWS_CACHE_FILE_ID = "news-cache";

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
 * The device sends `{"storyId": 140155}`; the scheduled/headline path sends an empty body. A body
 * that is not JSON at all is treated as "no story id" rather than as an error, because that is what
 * every caller of the headline path has always sent.
 */
function requestedStoryId(req) {
  const raw = req?.bodyJson ?? safeParse(req?.body);
  const id = raw?.storyId;
  if (typeof id === "number" && Number.isFinite(id)) return String(Math.trunc(id));
  if (typeof id === "string" && /^\d+$/.test(id)) return id;
  return null;
}

function safeParse(body) {
  if (!body || typeof body !== "string") return null;
  try {
    return JSON.parse(body);
  } catch {
    return null;
  }
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
  let cached = null;
  try {
    cached = parseJsonFile(await storage.getFileDownload(STATS_BUCKET_ID, NEWS_CACHE_FILE_ID));
  } catch (_) {
    log("No cache found — fetching fresh");
  }

  const now = Date.now();
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

  // parseJsonFile, never Buffer.from — see storageJson.js. The unconditional Buffer.from is what
  // made bouncer-player-search rebuild its whole index on every single query.
  try {
    const cached = parseJsonFile(await storage.getFileDownload(STATS_BUCKET_ID, fileId));
    if (Array.isArray(cached?.paragraphs) && cached.paragraphs.length > 0) {
      log(`Story ${storyId}: cache hit (${cached.paragraphs.length} paragraphs)`);
      return res.json({ storyId, paragraphs: cached.paragraphs });
    }
  } catch (_) {
    log(`Story ${storyId}: no cached body — fetching`);
  }

  if (!RAPIDAPI_KEY) {
    error("RAPIDAPI_KEY not set");
    return res.json({ error: "News service not configured" }, 503);
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
      return res.json({ error: `Upstream error: ${response.status}` }, response.status);
    }
    detail = await response.json();
  } catch (e) {
    error(`RapidAPI detail ${storyId} fetch failed: ${e?.message}`);
    return res.json({ error: "Failed to fetch article" }, 503);
  }

  const paragraphs = extractParagraphs(detail);
  if (paragraphs.length === 0) {
    // An upstream shape change reads exactly like an empty article, so say which one it is. Keys
    // and block types only; the payload itself is several KB.
    error(`Story ${storyId}: no text blocks in detail payload — ${shapeHint(detail)}`);
    return res.json({ error: "Article body unavailable" }, 503);
  }

  // Non-fatal: a body we could not cache is still a body we can serve.
  try {
    const payload = { fetchedAt: new Date().toISOString(), storyId, paragraphs };
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
