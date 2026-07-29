import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { createSign } from "crypto";
import { seriesCategory, isWomensSeries } from "./series.js";
import { parseJsonFile, fileText } from "./storageJson.js";

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
    return { type, data };
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
      // Use in-memory data directly — no self-read needed
      const liveData = liveResult.value?.data;
      const newLiveCount = liveData?.matches?.length ?? liveData?.count ?? 0;

      let prevCount = 0;
      try {
        prevCount = parseInt(fileText(await storage.getFileDownload(BUCKET_ID, "live-count"))) || 0;
      } catch { /* first run */ }

      if (newLiveCount !== prevCount) {
        log(`Live match count changed: ${prevCount} → ${newLiveCount}`);

        // Send FCM BEFORE writing live-count so a timeout can't update count without notifying
        const saJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
        const projectId = process.env.FIREBASE_PROJECT_ID;
        if (saJson && projectId) {
          await sendFcmTopicMessage(
            "cricket-live-updates",
            { type: newLiveCount > prevCount ? "MATCH_STARTED" : "MATCH_ENDED", liveCount: String(newLiveCount) },
            saJson, projectId, error
          );
        }

        await storage.deleteFile(BUCKET_ID, "live-count").catch(() => {});
        await storage.createFile(BUCKET_ID, "live-count",
          InputFile.fromBuffer(Buffer.from(String(newLiveCount)), "live-count.txt"),
          ["read(\"any\")"]
        );
      }
    } catch (e) {
      error(`FCM state-change check failed: ${e?.message}`);
    }
  }

  // Series sync: scrape Cricbuzz at most once per 24h server-side (replaces N per-device daily scrapes)
  try {
    let seriesFresh = false;
    try {
      const prevJson = parseJsonFile(await storage.getFileDownload(BUCKET_ID, "active-series"));
      seriesFresh = (Date.now() - (prevJson.fetchedAt ?? 0)) < 24 * 3600 * 1000;
    } catch { /* missing or parse error — treat as stale */ }

    if (!seriesFresh) {
      const series = await fetchActiveSeries();
      if (series.length > 0) {
        const payload = JSON.stringify({ data: series, fetchedAt: Date.now() });
        await storage.deleteFile(BUCKET_ID, "active-series").catch(() => {});
        await storage.createFile(
          BUCKET_ID, "active-series",
          InputFile.fromBuffer(Buffer.from(payload, "utf8"), "active-series.json"),
          ["read(\"any\")"]
        );
        log(`Updated active-series (${series.length} series)`);
      }
    }
  } catch (e) {
    error(`Series sync failed: ${e?.message}`);
  }

  return res.json(results);
};

async function fetchActiveSeries() {
  const resp = await fetch("https://www.cricbuzz.com/cricket-schedule/series", {
    headers: { "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" },
    signal: AbortSignal.timeout(10_000),
  });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  const html = await resp.text();

  const nextDataMatch = html.match(/<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/);
  if (nextDataMatch) {
    try {
      const pageProps = JSON.parse(nextDataMatch[1])?.props?.pageProps;
      const seriesData = pageProps?.seriesMapProto ?? pageProps?.content;
      if (seriesData) {
        const result = [];
        for (const group of seriesData) {
          for (const s of (group.series ?? [])) {
            const id = String(s.id ?? s.seriesId ?? "");
            const name = s.name ?? s.seriesName ?? "";
            if (!id || !name || name.length < 3) continue;
            result.push({ id, name, category: seriesCategory(name), isWomens: isWomensSeries(name), startDate: s.startDt ?? "", endDate: s.endDt ?? "" });
          }
        }
        if (result.length > 0) return result;
      }
    } catch {}
  }

  // Regex fallback if __NEXT_DATA__ missing or parse failed
  const result = [];
  const seen = new Set();
  const re = /href="\/cricket-series\/(\d+)\/[^"]*">([^<]+)<\/a>/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    const id = m[1], name = m[2].trim();
    if (!name || name.length < 3 || seen.has(id)) continue;
    seen.add(id);
    result.push({ id, name, category: seriesCategory(name), isWomens: isWomensSeries(name), startDate: "", endDate: "" });
  }
  return result;
}

async function getFirebaseAccessToken(serviceAccountJson) {
  const sa = JSON.parse(serviceAccountJson);
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })).toString("base64url");
  const sigInput = `${header}.${payload}`;
  const sign = createSign("RSA-SHA256");
  sign.update(sigInput);
  const sig = sign.sign(sa.private_key, "base64url");
  const jwt = `${sigInput}.${sig}`;

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
    signal: AbortSignal.timeout(8_000),
  });
  if (!tokenResp.ok) throw new Error(`Token exchange HTTP ${tokenResp.status}`);
  const tokenData = await tokenResp.json();
  return tokenData.access_token;
}

async function sendFcmTopicMessage(topic, data, serviceAccountJson, projectId, errorFn) {
  try {
    const accessToken = await getFirebaseAccessToken(serviceAccountJson);
    const resp = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": `Bearer ${accessToken}` },
        body: JSON.stringify({ message: { topic, data } }),
        signal: AbortSignal.timeout(8_000),
      }
    );
    if (!resp.ok) errorFn?.(`FCM v1 send failed: HTTP ${resp.status}`);
  } catch (e) {
    errorFn?.(`FCM v1 send error: ${e?.message}`);
  }
}
