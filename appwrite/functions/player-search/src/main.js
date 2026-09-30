// bouncer-player-search — resolve a player name to a Cricbuzz profile id.
//
// On-demand, called by `PlayerProfileScraper.searchPlayersDetailed` on the device.
//
// This exists because the device-side lookup it replaces had been returning nothing for every query
// for an unknown length of time: it scraped two Cricbuzz URLs that both answer 404, and the caller
// turned that into an empty list, which the UI drew as a clean "no results". Everything downstream
// of the lookup — the player detail screen, the comparison screen, `PlayerCacheWorker`, and so the
// shared `player-profiles` cache that could have covered for all of them — was dead with it.
//
// Server-side so the next time the upstream moves it is a function deploy, not an app release
// gated on users updating.

import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { CRICBUZZ_BASE, CricbuzzBlockedError, fetchHtml, resetThrottleState } from "./cricbuzz-http.js";
import { INDEX_FILE_ID, INDEX_TTL_MS, buildIndex, rankPlayers } from "./playerIndex.js";
import { parseJsonFile } from "./storageJson.js";

const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID || "cricket-stats";
const MAX_RESULTS = 10;
const MIN_QUERY_LENGTH = 2;

// Per-device rate limit. The device already debounces and caches, so this is a backstop against a
// client bug, not the primary control — see AppwriteReadGuard's EXECUTIONS caps for the other side.
const DEVICE_WINDOW_MS = 60_000;
const MAX_PER_DEVICE_PER_WINDOW = 20;
/** Container-scoped and therefore approximate: a device spread across containers gets more. */
const deviceHits = new Map();

function rateLimited(deviceId, now) {
  if (!deviceId) return false;
  const hits = (deviceHits.get(deviceId) ?? []).filter((t) => now - t < DEVICE_WINDOW_MS);
  hits.push(now);
  deviceHits.set(deviceId, hits);
  // Bound the map: it is keyed by client-supplied ids and lives for the container's life.
  if (deviceHits.size > 500) {
    for (const [k, v] of deviceHits) {
      if (v.every((t) => now - t >= DEVICE_WINDOW_MS)) deviceHits.delete(k);
    }
  }
  return hits.length > MAX_PER_DEVICE_PER_WINDOW;
}

export default async ({ req, res, log, error }) => {
  // Upstream reachability probe, run from inside the container. Cricbuzz and Cricinfo block
  // differently per network, so a curl from a laptop answers a different question than this does;
  // this is the only way to tell whether the *function* can reach them. Returns without searching.
  if (process.env.PROBE_SOURCES === "1") {
    return res.json(await probeSources(log));
  }

  const query = String(parseBody(req)?.q ?? "").trim();
  if (query.length < MIN_QUERY_LENGTH) {
    return res.json({ players: [], reason: "query_too_short" });
  }

  const deviceId = req.headers?.["x-bouncer-device"] ?? "";
  if (rateLimited(deviceId, Date.now())) {
    log(`rate limited device ${deviceId}`);
    return res.json({ players: [], reason: "rate_limited" }, 429);
  }

  const storage = new Storage(
    new Client()
      .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
      .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
      .setKey(process.env.APPWRITE_API_KEY)
  );

  // A warm container answers from memory: a search box fires several queries per search, and each
  // used to download the whole index file. The TTL still governs, so a stale memo falls through to
  // the file (which another container may have rebuilt) and then to a rebuild.
  const fresh = (ix) => ix && Date.now() - (ix.fetchedAt ?? 0) <= INDEX_TTL_MS;
  let index = fresh(indexMemo) ? indexMemo : await readIndex(storage, log);
  const stale = !fresh(index);

  if (stale) {
    try {
      const built = await buildIndex(log);
      if (built.players.length > 0) {
        index = { players: built.players, fetchedAt: Date.now() };
        await writeIndex(storage, index, error);
      }
    } catch (e) {
      // A block or a failed rebuild must not lose a usable cached index. Answering from a stale
      // index is strictly better than answering "no such player".
      if (e instanceof CricbuzzBlockedError || e?.blocked) {
        error(`player-index rebuild throttled: ${e?.message}${e?.bodySnippet ? ` — body: ${e.bodySnippet}` : ""}`);
      } else {
        error(`player-index rebuild failed: ${e?.message}`);
      }
      if (!index) {
        // No index at all and no way to build one. This is unavailability, not absence, and the
        // device must be able to tell the difference — hence a 503, never an empty 200.
        return res.json({ players: [], reason: "index_unavailable" }, 503);
      }
    }
  }

  indexMemo = index;
  const players = rankPlayers(index.players ?? [], query, MAX_RESULTS);
  log(`"${query}" → ${players.length} of ${index.players?.length ?? 0} indexed`);
  return res.json({ players, indexedAt: index.fetchedAt ?? 0 });
};

let indexMemo = null; // warm-container copy of the last index read or built

function parseBody(req) {
  if (req?.bodyJson && typeof req.bodyJson === "object") return req.bodyJson;
  try {
    return JSON.parse(req?.body ?? req?.bodyRaw ?? "{}");
  } catch {
    return {};
  }
}

async function readIndex(storage, log) {
  try {
    // Via parseJsonFile, not Buffer.from: node-appwrite 15 returns an already-parsed object for an
    // `application/json` file, and the unconditional Buffer.from this replaced threw on every
    // read. The throw landed in the catch below as "no cached index", so each search rebuilt the
    // whole corpus — 39 Cricbuzz fetches and ~14s per query, with the 7-day TTL never once hit.
    return parseJsonFile(await storage.getFileDownload(BUCKET_ID, INDEX_FILE_ID));
  } catch (e) {
    // A 404 on first run is normal and must stay quiet; anything else is worth a line.
    if (e?.code !== 404) log(`player-index read failed: ${e?.message}`);
    return null;
  }
}

async function writeIndex(storage, index, error) {
  try {
    await storage.deleteFile(BUCKET_ID, INDEX_FILE_ID).catch(() => {});
    await storage.createFile(
      BUCKET_ID,
      INDEX_FILE_ID,
      InputFile.fromBuffer(Buffer.from(JSON.stringify(index), "utf8"), "player-index.json"),
      ['read("any")']
    );
  } catch (e) {
    // The search still answers from the in-memory index; only the cache write was lost.
    error(`player-index write failed: ${e?.message}`);
  }
}

/**
 * What this container can actually reach, with enough detail to tell "blocked" from "moved".
 * Mirrors match-sync's PROBE_SOURCES.
 */
async function probeSources(log) {
  resetThrottleState();
  const targets = [
    ["cricbuzz_team_list", `${CRICBUZZ_BASE}/cricket-team`, "/cricket-team/"],
    ["cricbuzz_squad", `${CRICBUZZ_BASE}/cricket-team/india/2/players`, "/profiles/"],
    ["cricbuzz_profile", `${CRICBUZZ_BASE}/profiles/1413/virat-kohli`, "__NEXT_DATA__"],
  ];
  const results = {};
  for (const [name, url, marker] of targets) {
    try {
      const html = await fetchHtml(url, 12_000);
      results[name] = { ok: true, bytes: html.length, hasMarker: html.includes(marker) };
    } catch (e) {
      results[name] = { ok: false, status: e?.status ?? null, blocked: !!e?.blocked, message: e?.message };
    }
    log(`probe ${name}: ${JSON.stringify(results[name])}`);
  }
  return { probe: true, results };
}
