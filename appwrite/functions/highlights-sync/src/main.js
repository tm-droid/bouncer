import { Client, Databases, Query } from "node-appwrite";

export default async function(req, res) {
  const client = new Client()
    .setEndpoint(process.env.APPWRITE_ENDPOINT)
    .setProject(process.env.APPWRITE_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);

  const databases = new Databases(client);
  const dbId = process.env.APPWRITE_HIGHLIGHTS_DB_ID;
  const colId = process.env.APPWRITE_HIGHLIGHTS_COL_ID;

  try {
    // 1. Check Cricbuzz for live or recently completed matches
    const today = new Date().toISOString().split('T')[0];
    const liveMatches = await checkCricbuzzForLiveMatches(today);

    if (!liveMatches || liveMatches.length === 0) {
      console.log("No live or recent matches found; skipping highlights fetch");
      return res.json({ skipped: true });
    }

    console.log(`Found ${liveMatches.length} live/recent matches; fetching highlights`);

    // 2. Fetch highlights from Highlightly API
    const highlights = await fetchHighlightlyHighlights(today);
    if (!highlights || highlights.length === 0) {
      console.log("No highlights found for today");
      return res.json({ highlightsCount: 0 });
    }

    // 3. Upsert highlights into Appwrite
    let upsertedCount = 0;
    for (const highlight of highlights) {
      const doc = highlightToDocument(highlight);
      try {
        await databases.createDocument(
          dbId,
          colId,
          doc.highlight_id,
          doc.data,
          ["read(\"any\")"]
        );
        upsertedCount++;
      } catch (e) {
        // Document may already exist; try to update
        if (e.code === 409) {
          await databases.updateDocument(dbId, colId, doc.highlight_id, doc.data);
          upsertedCount++;
        } else {
          console.error(`Failed to upsert highlight ${doc.highlight_id}:`, e.message);
        }
      }
    }

    console.log(`Upserted ${upsertedCount} highlights`);
    return res.json({ upsertedCount });
  } catch (error) {
    console.error("Highlights sync failed:", error.message);
    return res.json({ error: error.message }, 500);
  }
}

async function checkCricbuzzForLiveMatches(today) {
  const rapidApiKey = process.env.RAPIDAPI_KEY;
  if (!rapidApiKey) {
    console.warn("RAPIDAPI_KEY not set; cannot check Cricbuzz");
    return [];
  }

  try {
    // Determine appropriate Cricbuzz RapidAPI endpoint for live matches
    // For now, assume endpoint returns matches with { id, status, endTime } fields
    const url = "https://unofficial-cricbuzz.p.rapidapi.com/matches/live";
    const options = {
      method: "GET",
      headers: {
        "X-RapidAPI-Key": rapidApiKey,
        "X-RapidAPI-Host": "unofficial-cricbuzz.p.rapidapi.com"
      }
    };

    const response = await fetch(url, options);
    if (!response.ok) {
      console.warn(`Cricbuzz API returned ${response.status}`);
      return [];
    }

    const data = await response.json();
    if (!Array.isArray(data)) return [];

    const now = Date.now();
    const twoHoursAgo = now - 2 * 60 * 60 * 1000;

    // Filter live matches or recently completed (within 2 hours)
    return data.filter(m => {
      if (m.status === "live" || m.status === "Live") return true;
      if (m.status === "completed" || m.status === "Completed") {
        const endTime = m.endTime ? new Date(m.endTime).getTime() : 0;
        return endTime > twoHoursAgo;
      }
      return false;
    });
  } catch (error) {
    console.error("Cricbuzz check failed:", error.message);
    return [];
  }
}

async function fetchHighlightlyHighlights(today) {
  const rapidApiKey = process.env.RAPIDAPI_KEY;
  const highlightlyApiKey = process.env.HIGHLIGHTLY_API_KEY;
  if (!rapidApiKey || !highlightlyApiKey) {
    console.warn("API keys not set");
    return [];
  }

  try {
    const url = `https://cricket-highlights-api.p.rapidapi.com/highlights?date=${today}`;
    const options = {
      method: "GET",
      headers: {
        "X-RapidAPI-Key": rapidApiKey,
        "X-RapidAPI-Host": "cricket-highlights-api.p.rapidapi.com"
      }
    };

    const response = await fetch(url, options);
    if (!response.ok) {
      console.warn(`Highlightly API returned ${response.status}`);
      return [];
    }

    const data = await response.json();
    return Array.isArray(data) ? data : data.highlights || [];
  } catch (error) {
    console.error("Highlightly fetch failed:", error.message);
    return [];
  }
}

function highlightToDocument(highlight) {
  return {
    highlight_id: String(highlight.id),
    data: {
      highlight_id: String(highlight.id),
      type: highlight.type || "UNVERIFIED",
      img_url: highlight.imgUrl || "",
      title: highlight.title || "",
      url: highlight.url || "",
      embed_url: highlight.embedUrl || null,
      channel: highlight.channel || "",
      source: highlight.source || "",
      match_home_team: highlight.match?.homeTeam?.name || "",
      match_away_team: highlight.match?.awayTeam?.name || "",
      match_home_abbr: highlight.match?.homeTeam?.abbreviation || "",
      match_away_abbr: highlight.match?.awayTeam?.abbreviation || "",
      match_league_name: highlight.match?.league?.name || "",
      match_league_season: highlight.match?.league?.season || 0,
      match_start_date: highlight.match?.startDate || "",
      match_end_date: highlight.match?.endDate || "",
      raw_match_id: highlight.match?.id || "",
      fetched_at: Date.now()
    }
  };
}
