import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { parseJsonFile } from "./storageJson.js";

const CACHE_TTL_MS = 4 * 60 * 60 * 1000; // 4 hours — caps RapidAPI at 6 calls/day
const NEWS_CACHE_FILE_ID = "news-cache";

export default async ({ req, res, log, error }) => {
  const RAPIDAPI_KEY = process.env.RAPIDAPI_KEY ?? "";
  const STATS_BUCKET_ID = process.env.STATS_BUCKET_ID ?? "cricket-stats";

  if (!RAPIDAPI_KEY) {
    error("RAPIDAPI_KEY not set");
    return res.json({ error: "News service not configured" }, 503);
  }

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY ?? "");
  const storage = new Storage(client);

  // ── Try cached response ──────────────────────────────────────────────────
  try {
    const cached = parseJsonFile(await storage.getFileDownload(STATS_BUCKET_ID, NEWS_CACHE_FILE_ID));
    const age = Date.now() - new Date(cached.fetchedAt).getTime();
    if (age < CACHE_TTL_MS) {
      log(`Returning cached news (age: ${Math.round(age / 60000)}m)`);
      return res.json(cached.response);
    }
    log(`Cache stale (${Math.round(age / 60000)}m old) — fetching fresh`);
  } catch (_) {
    log("No cache found — fetching fresh");
  }

  // ── Fetch from RapidAPI ──────────────────────────────────────────────────
  let newsJson;
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
      error(`RapidAPI returned HTTP ${response.status}`);
      return res.json({ error: `Upstream error: ${response.status}` }, response.status);
    }
    newsJson = await response.json();
  } catch (e) {
    error(`RapidAPI fetch failed: ${e?.message}`);
    return res.json({ error: "Failed to fetch news" }, 503);
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
