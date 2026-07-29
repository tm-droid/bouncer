import { Client, Databases } from "node-appwrite";

// Appwrite auto-injects APPWRITE_FUNCTION_API_ENDPOINT and APPWRITE_FUNCTION_PROJECT_ID.
// APPWRITE_API_KEY must be set manually in the function's env vars.

// Hard ceiling on writes per run. The loop below previously iterated whatever the Highlightly API
// returned, unbounded — a large response meant a write burst with nothing to stop it.
const MAX_WRITES_PER_RUN = 50;

// Ids already written, kept in module scope so it survives warm invocations of the */15 cron.
// Highlightly re-returns the same clips every run, so without this every run rewrote all of them.
// Bounded so a long-lived container cannot grow it without limit.
const MAX_SEEN = 2000;
const seenIds = new Set();

function rememberSeen(id) {
  if (seenIds.size >= MAX_SEEN) seenIds.clear(); // cheap bound; a reset only costs one re-write pass
  seenIds.add(id);
}

export default async ({ req, res, log, error }) => {
  const RAPIDAPI_KEY = process.env.RAPIDAPI_KEY ?? "";
  const HIGHLIGHTLY_API_KEY = process.env.HIGHLIGHTLY_API_KEY ?? "";
  const HIGHLIGHTS_DB_ID = process.env.APPWRITE_HIGHLIGHTS_DB_ID ?? "";
  const HIGHLIGHTS_COL_ID = process.env.APPWRITE_HIGHLIGHTS_COL_ID ?? "";
  const META_COL_ID = process.env.APPWRITE_HIGHLIGHTS_META_COL_ID ?? "";

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY ?? "");
  const db = new Databases(client);

  // ── Midnight reset ──────────────────────────────────────────────────────
  const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD

  // ── Cricbuzz gate (fail-OPEN on 429/error) ────────────────────────────
  // Only skip when Cricbuzz positively confirms no live/recent matches.
  // On 429 or network error, proceed — Cricbuzz 429 ≠ "no matches live".
  const gate = await checkForLiveOrRecentMatches(RAPIDAPI_KEY, log, error);
  if (gate.confirmed && !gate.hasRelevantMatches) {
    log("No live/recent matches confirmed — skipping Highlightly fetch");
    return res.json({ skipped: true, reason: "no_live_matches" });
  }
  if (!gate.confirmed) {
    log(`Cricbuzz gate unconfirmed (${gate.reason}) — proceeding with Highlightly anyway`);
  }

  // ── Fetch highlights from Highlightly API ───────────────────────────────
  const highlights = await fetchHighlights(today, HIGHLIGHTLY_API_KEY, log, error);
  if (highlights.length === 0) {
    log("Highlightly returned 0 highlights");
    return res.json({ upserted: 0 });
  }

  // ── Upsert each highlight into Appwrite DB ──────────────────────────────
  const now = Date.now();
  let upserted = 0;
  let skipped = 0;
  const failed = [];

  // Newest first, so if the cap truncates the run it sheds the oldest clips rather than the ones
  // users are most likely to want. startDate is an ISO string, so parse it — Number() yields NaN
  // and an NaN comparator leaves the order undefined.
  const startedAt = (h) => Date.parse(h.match?.startDate ?? "") || 0;
  // Skipping already-written ids means a later metadata change to an existing clip (say an
  // embed_url appearing) is not repushed until the container recycles. Accepted: Highlightly
  // clip metadata is effectively immutable once published, and the alternative was rewriting
  // every clip every 15 minutes forever.
  const pending = highlights
    .filter((h) => !seenIds.has(h.id))
    .sort((a, b) => startedAt(b) - startedAt(a));
  skipped = highlights.length - pending.length;

  const batch = pending.slice(0, MAX_WRITES_PER_RUN);
  if (pending.length > batch.length) {
    // Never let a cap truncate silently — a shorter list would otherwise read as "all synced".
    log(`Write cap hit: ${pending.length} new highlights, writing newest ${batch.length}`);
  }

  for (const h of batch) {
    const docId = `hl-${h.id}`;
    const payload = buildPayload(h, now);
    try {
      // Create first: most rows in a run are new, so this is one write instead of a guaranteed
      // failed update followed by a create.
      await db.createDocument(HIGHLIGHTS_DB_ID, HIGHLIGHTS_COL_ID, docId, payload, ["read(\"any\")"]);
      upserted++;
      rememberSeen(h.id);
    } catch (e) {
      if (e?.code !== 409) {
        failed.push({ id: h.id, message: e?.message });
        error(`Failed to create highlight ${h.id}: ${e?.message}`);
        continue;
      }
      try {
        await db.updateDocument(HIGHLIGHTS_DB_ID, HIGHLIGHTS_COL_ID, docId, payload);
        upserted++;
        rememberSeen(h.id);
      } catch (e2) {
        failed.push({ id: h.id, message: e2?.message });
        error(`Failed to upsert highlight ${h.id}: ${e2?.message}`);
      }
    }
  }

  // ── Persist fetch state for midnight reset tracking ─────────────────────
  if (META_COL_ID) {
    const metaPayload = { fetch_date: today, last_fetch_at: now };
    try {
      await db.updateDocument(HIGHLIGHTS_DB_ID, META_COL_ID, "fetch-state", metaPayload);
    } catch (_) {
      try {
        await db.createDocument(HIGHLIGHTS_DB_ID, META_COL_ID, "fetch-state", metaPayload);
      } catch (e3) {
        error(`Failed to persist fetch-state meta: ${e3?.message}`);
      }
    }
  }

  log(`Upserted ${upserted} highlights for ${today}; ${skipped} already known, ${failed.length} failures`);
  return res.json({ upserted, skipped, failed: failed.length, date: today });
};

/**
 * Returns:
 *   { confirmed: false, reason }                       — we can't trust the answer; caller should skip
 *   { confirmed: true, hasRelevantMatches: boolean }   — authoritative answer
 */
async function checkForLiveOrRecentMatches(rapidApiKey, log, error) {
  if (!rapidApiKey) {
    return { confirmed: false, reason: "missing_rapidapi_key" };
  }

  try {
    const response = await fetch(
      "https://cricbuzz-cricket.p.rapidapi.com/matches/v1/live",
      {
        headers: {
          "X-RapidAPI-Key": rapidApiKey,
          "X-RapidAPI-Host": "cricbuzz-cricket.p.rapidapi.com",
        },
      }
    );

    if (!response.ok) {
      return { confirmed: false, reason: `cricbuzz_http_${response.status}` };
    }

    const data = await response.json();
    const now = Date.now();
    const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

    const matches = extractMatches(data);
    const hasRelevantMatches = matches.some(m => {
      const state = (m.state ?? m.status ?? "").toLowerCase();
      if (state === "in progress" || state === "live") return true;
      if (state === "complete" || state === "completed") {
        const endTime = Number(m.endDate ?? m.endTime ?? 0);
        return (now - endTime) < TWO_HOURS_MS;
      }
      return false;
    });
    return { confirmed: true, hasRelevantMatches };
  } catch (e) {
    error(`Cricbuzz check failed: ${e?.message}`);
    return { confirmed: false, reason: "cricbuzz_exception" };
  }
}

function extractMatches(data) {
  if (Array.isArray(data)) return data;
  const results = [];
  for (const type of (data.typeMatches ?? [])) {
    for (const series of (type.seriesMatches ?? [])) {
      for (const match of (series.seriesAdWrapper?.matches ?? [])) {
        const info = match.matchInfo ?? match;
        results.push(info);
      }
    }
  }
  return results;
}

/**
 * Pull highlights for a date. Validates the response shape and explicitly
 * logs which shape matched (or "unknown") so silent format drift is visible.
 */
async function fetchHighlights(today, highlightlyApiKey, log, error) {
  if (!highlightlyApiKey) {
    error("HIGHLIGHTLY_API_KEY not set — cannot fetch highlights");
    return [];
  }

  try {
    const url = `https://cricket-highlights-api.p.rapidapi.com/highlights?date=${today}`;
    log(`Fetching: ${url}`);
    const response = await fetch(url, {
      headers: {
        "x-rapidapi-key": highlightlyApiKey,
        "x-rapidapi-host": "cricket-highlights-api.p.rapidapi.com",
        "Content-Type": "application/json",
      },
    });

    if (!response.ok) {
      error(`Highlightly API returned ${response.status}`);
      return [];
    }

    const data = await response.json();
    const extracted = extractHighlights(data);
    if (!extracted.matched) {
      error(`Highlightly response shape unknown — top-level keys: ${
        Array.isArray(data) ? "[array]" : Object.keys(data).join(", ")
      }`);
      return [];
    }
    if (extracted.items.length === 0) {
      log(`Highlightly 0 results (shape=${extracted.shape})`);
    } else {
      log(`Highlightly returned ${extracted.items.length} items (shape=${extracted.shape})`);
    }
    return extracted.items;
  } catch (e) {
    error(`Highlightly fetch failed: ${e?.message}`);
    return [];
  }
}

function extractHighlights(data) {
  if (Array.isArray(data)) return { matched: true, shape: "array", items: data };
  if (Array.isArray(data?.highlights)) return { matched: true, shape: "highlights", items: data.highlights };
  if (Array.isArray(data?.data)) return { matched: true, shape: "data", items: data.data };
  if (Array.isArray(data?.results)) return { matched: true, shape: "results", items: data.results };
  return { matched: false, shape: "unknown", items: [] };
}

function buildPayload(h, now) {
  return {
    highlight_id: String(h.id),
    type: h.type ?? "UNVERIFIED",
    img_url: h.imgUrl ?? "",
    title: h.title ?? "",
    url: h.url ?? "",
    embed_url: h.embedUrl?.trim() || null,
    channel: h.channel ?? "",
    source: h.source ?? "",
    match_home_team: h.match?.homeTeam?.name ?? "",
    match_away_team: h.match?.awayTeam?.name ?? "",
    match_home_abbr: h.match?.homeTeam?.abbreviation ?? "",
    match_away_abbr: h.match?.awayTeam?.abbreviation ?? "",
    match_league_name: h.match?.league?.name ?? "",
    match_league_season: h.match?.league?.season ?? 0,
    match_start_date: h.match?.startDate ?? "",
    match_end_date: h.match?.endDate ?? "",
    raw_match_id: h.match?.id ?? "",
    fetched_at: now,
  };
}
