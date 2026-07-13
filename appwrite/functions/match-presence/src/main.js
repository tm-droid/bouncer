import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";

// Records a "viewer is watching match X" ping so bouncer-match-sync can gate LLM
// generation to matches someone actually has open (see match-sync/src/main.js).
// Presence is a small { [matchId]: epochMs } map in the cricket-stats bucket.
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const PRESENCE_FILE = "active-viewers";
// Prune entries older than this so the file stays tiny. Must stay >= match-sync's
// VIEWER_TTL_MS so we never drop a viewer match-sync still counts as active.
const PRESENCE_TTL_MS = 4 * 60 * 1000;

const sanitizeMatchId = (id) => String(id).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 64);

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }

  let body;
  try {
    body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
    if (!body || typeof body !== "object" || Array.isArray(body)) throw new Error();
  } catch {
    return res.json({ error: "Body must be a JSON object" }, 400);
  }
  const matchId = sanitizeMatchId(body.matchId ?? "");
  if (!matchId) {
    return res.json({ error: "Missing matchId" }, 400);
  }

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  const storage = new Storage(client);

  const now = Date.now();

  // Read-modify-write the presence map. Concurrent pings can race (last write wins,
  // dropping another match's fresh timestamp) — accepted; the next ~90s heartbeat heals it.
  let map = {};
  try {
    const raw = await storage.getFileDownload(BUCKET_ID, PRESENCE_FILE);
    map = JSON.parse(Buffer.from(raw).toString("utf8"));
    if (!map || typeof map !== "object" || Array.isArray(map)) map = {};
  } catch {
    map = {}; // first ping ever, or file missing/unparseable
  }

  map[matchId] = now;
  // Prune stale viewers so the file can't grow unbounded.
  for (const [id, ts] of Object.entries(map)) {
    if (typeof ts !== "number" || now - ts > PRESENCE_TTL_MS) delete map[id];
  }

  const payload = Buffer.from(JSON.stringify(map), "utf8");
  try {
    await storage.deleteFile(BUCKET_ID, PRESENCE_FILE).catch(() => {});
    await storage.createFile(
      BUCKET_ID, PRESENCE_FILE,
      InputFile.fromBuffer(payload, "active-viewers.json"),
      ["read(\"any\")"]
    );
  } catch (e) {
    // A concurrent ping may have already recreated the file (409) — its map is valid,
    // so just log; this match self-heals on the next heartbeat.
    error(`Presence write failed (${matchId}): ${e?.message}`);
    return res.json({ ok: false, matchId }, 200);
  }

  log(`Presence ping: ${matchId} (${Object.keys(map).length} active)`);
  return res.json({ ok: true, matchId, active: Object.keys(map).length });
};
