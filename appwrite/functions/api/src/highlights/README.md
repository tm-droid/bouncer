# Highlights Sync Cloud Function

Appwrite cloud function that syncs cricket highlights from the Highlightly API every 15 minutes.

## Prerequisites

- Appwrite project with TablesDB enabled
- RapidAPI key for Cricbuzz + Highlightly API access
- Appwrite API key with `databases.write` permission

## Deployment

1. Set env vars in `appwrite.json` or Appwrite console:
   - `APPWRITE_ENDPOINT` — Appwrite console URL (e.g., https://cloud.appwrite.io/v1)
   - `APPWRITE_PROJECT_ID` — Project ID from Appwrite console
   - `APPWRITE_API_KEY` — Server API key with write permission
   - `APPWRITE_HIGHLIGHTS_DB_ID` — Database ID for highlights cache
   - `APPWRITE_HIGHLIGHTS_COL_ID` — Table ID for highlights
   - `RAPIDAPI_KEY` — RapidAPI key (Cricbuzz + Highlightly hosts)
   - `HIGHLIGHTLY_API_KEY` — Highlightly API key (if separate)

2. Deploy:
   ```bash
   appwrite deploy function
   ```

3. Verify cron schedule:
   - Function runs every 15 minutes (`*/15 * * * *`)
   - Check Appwrite console → Functions → highlights-sync → Executions
   - Logs show "skipped" if no live/recent matches, or "upsertedCount: N"

## Behavior

- Queries Cricbuzz (RapidAPI) for live or recently completed matches (≤2 hours finished)
- If no such matches exist, exits immediately (no Highlightly API call)
- If live/recent matches exist, fetches highlights for today from Highlightly API
- Upserts highlights into `highlights_cache` table
- Returns `{ skipped: true }` or `{ upsertedCount: N }`
- Errors are logged to Appwrite console and returned as `{ error: "..." }`

## Database Schema

Highlights table (`highlights_cache`) fields:
- `highlight_id` (string, PK)
- `type` (string: "VERIFIED" | "UNVERIFIED")
- `img_url`, `title`, `url`, `embed_url`, `channel`, `source` (string)
- `match_home_team`, `match_away_team` (string)
- `match_home_abbr`, `match_away_abbr` (string)
- `match_league_name` (string)
- `match_league_season` (int)
- `match_start_date`, `match_end_date` (string)
- `raw_match_id` (string)
- `fetched_at` (integer, epoch ms)

Permissions: `read("any")` (public read, no client write).
