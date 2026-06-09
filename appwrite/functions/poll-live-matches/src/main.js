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

  // Fetch all three types in parallel to minimize total write-time spread
  const types = ["live", "recent", "upcoming"];
  const settled = await Promise.allSettled(types.map(async (type) => {
    const resp = await fetch(`${VERCEL_BASE}/matches?type=${type}`, { signal: AbortSignal.timeout(10_000) });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const data = await resp.json();
    const payload = JSON.stringify({ data, fetchedAt: Date.now() });
    const buffer = Buffer.from(payload, "utf8");
    await storage.deleteFile(BUCKET_ID, FILE_IDS[type]).catch(() => {});
    await storage.createFile(BUCKET_ID, FILE_IDS[type], InputFile.fromBuffer(buffer, `${type}-matches.json`), ["read(\"any\")"]);
    log(`Updated ${type}`);
    return type;
  }));

  const results = {};
  for (let i = 0; i < types.length; i++) {
    const s = settled[i];
    if (s.status === "fulfilled") {
      results[types[i]] = "ok";
    } else {
      error(`Failed ${types[i]}: ${s.reason?.message}`);
      results[types[i]] = "error";
    }
  }

  // Check if live match count changed and send FCM topic message if so
  const liveResult = settled[0]; // "live" is index 0
  if (liveResult.status === "fulfilled") {
    try {
      const liveData = JSON.parse(Buffer.from(
        await storage.getFileDownload(BUCKET_ID, FILE_IDS.live)
      ).toString("utf8"));
      const newLiveCount = liveData?.data?.matches?.length ?? liveData?.data?.count ?? 0;

      let prevCount = 0;
      try {
        const prevBytes = await storage.getFileDownload(BUCKET_ID, "live-count");
        prevCount = parseInt(Buffer.from(prevBytes).toString("utf8")) || 0;
      } catch { /* first run */ }

      if (newLiveCount !== prevCount) {
        log(`Live match count changed: ${prevCount} → ${newLiveCount}`);
        await storage.deleteFile(BUCKET_ID, "live-count").catch(() => {});
        await storage.createFile(BUCKET_ID, "live-count",
          InputFile.fromBuffer(Buffer.from(String(newLiveCount)), "live-count.txt"),
          ["read(\"any\")"]
        );
        const fcmKey = process.env.FCM_SERVER_KEY;
        if (fcmKey) {
          await sendFcmTopicMessage(
            "cricket-live-updates",
            { type: newLiveCount > prevCount ? "MATCH_STARTED" : "MATCH_ENDED", liveCount: String(newLiveCount) },
            fcmKey,
            error
          );
        }
      }
    } catch (e) {
      error(`FCM state-change check failed: ${e?.message}`);
    }
  }

  return res.json(results);
};

async function sendFcmTopicMessage(topic, data, serverKey, errorFn) {
  try {
    const resp = await fetch("https://fcm.googleapis.com/fcm/send", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `key=${serverKey}`,
      },
      body: JSON.stringify({ to: `/topics/${topic}`, data }),
      signal: AbortSignal.timeout(8_000),
    });
    if (!resp.ok) errorFn?.(`FCM send failed: HTTP ${resp.status}`);
  } catch (e) {
    errorFn?.(`FCM send error: ${e?.message}`);
  }
}
