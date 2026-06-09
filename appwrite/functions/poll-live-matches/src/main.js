import { Client, Storage, InputFile } from "node-appwrite";

const VERCEL_BASE = process.env.VERCEL_API_BASE ?? "https://api-iota-jade.vercel.app";
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const FILE_IDS = { live: "live-matches", recent: "recent-matches", upcoming: "upcoming-matches" };

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }
  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  const storage = new Storage(client);

  const types = ["live", "recent", "upcoming"];
  const results = {};
  for (const type of types) {
    try {
      const resp = await fetch(`${VERCEL_BASE}/matches?type=${type}`, { signal: AbortSignal.timeout(10_000) });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const data = await resp.json();
      const payload = JSON.stringify({ data, fetchedAt: Date.now() });
      const buffer = Buffer.from(payload, "utf8");
      await storage.deleteFile(BUCKET_ID, FILE_IDS[type]).catch(() => {});
      await storage.createFile(BUCKET_ID, FILE_IDS[type], InputFile.fromBuffer(buffer, `${type}-matches.json`), ["read(\"any\")"]);
      results[type] = "ok";
      log(`Updated ${type}`);
    } catch (e) {
      error(`Failed ${type}: ${e?.message}`);
      results[type] = "error";
    }
  }
  return res.json(results);
};
