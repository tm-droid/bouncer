import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { parseJsonFile } from "./storageJson.js";

// Records a "viewer is watching match X" ping so bouncer-match-sync can gate LLM
// generation to matches someone actually has open (see match-sync/src/main.js).
// Presence is a small { [matchId]: epochMs } map in the cricket-stats bucket.
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const PRESENCE_FILE = "active-viewers";
// Prune entries older than this so the file stays tiny. Must stay >= match-sync's
// VIEWER_TTL_MS so we never drop a viewer match-sync still counts as active.
const PRESENCE_TTL_MS = 4 * 60 * 1000;
// A ping finding its match stamped more recently than this skips the write.
const FRESH_SKIP_MS = 60 * 1000;

const sanitizeMatchId = (id) => String(id).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 64);

// Bounds one ping's reach: a device follows a handful of matches, never dozens.
const MAX_IDS_PER_PING = 10;

/** The sanitised, de-duplicated ids a ping names — `matchIds` (array) and/or `matchId`. */
export function presenceIds(body) {
  const raw = [...(Array.isArray(body?.matchIds) ? body.matchIds : []), body?.matchId];
  const ids = raw.filter((v) => v !== undefined && v !== null).map(sanitizeMatchId).filter(Boolean);
  return [...new Set(ids)].slice(0, MAX_IDS_PER_PING);
}

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
  // One id from an open match screen, or several from the Live Update service, which follows
  // every match the user has a live card for and pings them all in one execution.
  const matchIds = presenceIds(body);
  if (matchIds.length === 0) {
    return res.json({ error: "Missing matchId" }, 400);
  }
  const matchId = matchIds.join(",");

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
    map = parseJsonFile(await storage.getFileDownload(BUCKET_ID, PRESENCE_FILE));
    if (!map || typeof map !== "object" || Array.isArray(map)) map = {};
  } catch {
    map = {}; // first ping ever, or file missing/unparseable
  }

  // Already fresh: some viewer of this match pinged within the last minute. Writing again changes
  // nothing match-sync can see (it runs once a minute) and cost a delete+create per ping — so N
  // people watching one match were N writes every 90s. Capped at ~1 write/min/match now. Staleness
  // is unchanged: any ping that finds the stamp a minute old still writes, and each viewer pings
  // every 90s.
  const stale = matchIds.filter((id) => !(typeof map[id] === "number" && now - map[id] < FRESH_SKIP_MS));
  if (stale.length === 0) {
    return res.json({ ok: true, matchId, skipped: true });
  }

  for (const id of matchIds) map[id] = now;
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
