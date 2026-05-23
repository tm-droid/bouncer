import { Client, Databases } from "node-appwrite";

// Appwrite auto-injects APPWRITE_FUNCTION_API_ENDPOINT and APPWRITE_FUNCTION_PROJECT_ID.
// APPWRITE_API_KEY must be set manually in the function's env vars.

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

  // ── Fail-CLOSED Cricbuzz gate ──────────────────────────────────────────
  // Old behavior assumed live matches when the API errored; that re-upserted
  // yesterday's highlights every run. Now we skip the fetch unless we
  // positively confirm relevant matches exist.
  const gate = await checkForLiveOrRecentMatches(RAPIDAPI_KEY, log, error);
  if (!gate.confirmed) {
    log(`Cricbuzz gate not confirmed (${gate.reason}) — skipping fetch`);
    return res.json({ skipped: true, reason: gate.reason });
  }
  if (!gate.hasRelevantMatches) {
    log("No live/recent matches — skipping Highlightly fetch (counter not consumed)");
    return res.json({ skipped: true, reason: "no_live_matches" });
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
  const failed = [];
  for (const h of highlights) {
    const docId = `hl-${h.id}`;
    const payload = buildPayload(h, now);
    try {
      // Try update first (cheaper on conflict); fall back to create
      await db.updateDocument(HIGHLIGHTS_DB_ID, HIGHLIGHTS_COL_ID, docId, payload);
      upserted++;
    } catch (_) {
      try {
        await db.createDocument(HIGHLIGHTS_DB_ID, HIGHLIGHTS_COL_ID, docId, payload, ["read(\"any\")"]);
        upserted++;
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

  log(`Upserted ${upserted} highlights for ${today}; ${failed.length} failures`);
  return res.json({ upserted, failed: failed.length, date: today });
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
