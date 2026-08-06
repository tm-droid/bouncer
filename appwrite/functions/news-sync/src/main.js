import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { parseJsonFile } from "./storageJson.js";
import { CACHE_TTL_MS, cacheAgeMs, isFresh, staleFallback } from "./newsResult.js";

const NEWS_CACHE_FILE_ID = "news-cache";

// Enough of the upstream body to identify the failure, short enough not to flood the log.
const UPSTREAM_ERROR_LOG_CHARS = 200;

const ageLabel = (ageMs) => (ageMs === null ? "age unknown" : `${Math.round(ageMs / 60000)}m`);

export default async ({ req, res, log, error }) => {
  const RAPIDAPI_KEY = process.env.RAPIDAPI_KEY ?? "";
  const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY ?? "");
  const storage = new Storage(client);

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
        "https://cricbuzz-cricket.p.rapidapi.com/news/v1/index",
        {
          headers: {
            "x-rapidapi-key": RAPIDAPI_KEY,
            "x-rapidapi-host": "cricbuzz-cricket.p.rapidapi.com",
          },
          signal: AbortSignal.timeout(15_000),
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
