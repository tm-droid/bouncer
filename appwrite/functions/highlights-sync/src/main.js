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
  // Tracks fetch_date. When date changes, last_fetch_at is treated as 0 so
  // the cycle restarts fresh. The highlight cache is never cleared here.
  const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD

  // ── Check Cricbuzz for live / recently completed matches ────────────────
  // Fail-open: if API call errors, proceed with highlights fetch.
  const hasRelevantMatches = await checkForLiveOrRecentMatches(RAPIDAPI_KEY, log, error);

  if (!hasRelevantMatches) {
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
  for (const h of highlights) {
    const docId = `hl-${h.id}`;
    const payload = buildPayload(h, now);
    try {
      // Try update first (cheaper on conflict); fall back to create
      await db.updateDocument(HIGHLIGHTS_DB_ID, HIGHLIGHTS_COL_ID, docId, payload);
    } catch (_) {
      try {
        await db.createDocument(HIGHLIGHTS_DB_ID, HIGHLIGHTS_COL_ID, docId, payload, ["read(\"any\")"]);
      } catch (e2) {
        error(`Failed to upsert highlight ${h.id}: ${e2?.message}`);
      }
    }
    upserted++;
  }

  // ── Persist fetch state for midnight reset tracking ─────────────────────
  if (META_COL_ID) {
    const metaPayload = { fetch_date: today, last_fetch_at: now };
    try {
      await db.updateDocument(HIGHLIGHTS_DB_ID, META_COL_ID, "fetch-state", metaPayload);
    } catch (_) {
      await db.createDocument(HIGHLIGHTS_DB_ID, META_COL_ID, "fetch-state", metaPayload).catch(() => {});
    }
  }

  log(`Upserted ${upserted} highlights for ${today}`);
  return res.json({ upserted, date: today });
};

async function checkForLiveOrRecentMatches(rapidApiKey, log, error) {
  if (!rapidApiKey) {
    log("RAPIDAPI_KEY not set — assuming live matches exist (fail-open)");
    return true;
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
      log(`Cricbuzz API returned ${response.status} — proceeding (fail-open)`);
      return true;
    }

    const data = await response.json();
    const now = Date.now();
    const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

    // Parse both flat arrays and Cricbuzz's nested typeMatches structure
    const matches = extractMatches(data);
    return matches.some(m => {
      const state = (m.state ?? m.status ?? "").toLowerCase();
      if (state === "in progress" || state === "live") return true;
      if (state === "complete" || state === "completed") {
        const endTime = Number(m.endDate ?? m.endTime ?? 0);
        return (now - endTime) < TWO_HOURS_MS;
      }
      return false;
    });
  } catch (e) {
    error(`Cricbuzz check failed: ${e?.message} — proceeding (fail-open)`);
    return true;
  }
}

function extractMatches(data) {
  // Handle flat array
  if (Array.isArray(data)) return data;
  // Handle Cricbuzz nested: { typeMatches: [{ seriesMatches: [{ seriesAdWrapper: { matches: [] } }] }] }
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

async function fetchHighlights(today, highlightlyApiKey, log, error) {
  if (!highlightlyApiKey) {
    error("HIGHLIGHTLY_API_KEY not set — cannot fetch highlights");
    return [];
  }

  try {
    const response = await fetch(
      `https://cricket-highlights-api.p.rapidapi.com/highlights?date=${today}`,
      {
        headers: {
          "X-RapidAPI-Key": highlightlyApiKey,
          "X-RapidAPI-Host": "cricket-highlights-api.p.rapidapi.com",
        },
      }
    );

    if (!response.ok) {
      error(`Highlightly API returned ${response.status}`);
      return [];
    }

    const data = await response.json();
    const raw = Array.isArray(data) ? data : (data.highlights ?? data.data ?? data.results ?? []);
    if (raw.length === 0) {
      log(`Highlightly 0 results — response keys: ${Array.isArray(data) ? "[array]" : Object.keys(data).join(", ")}`);
    }
    return raw;
  } catch (e) {
    error(`Highlightly fetch failed: ${e?.message}`);
    return [];
  }
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
