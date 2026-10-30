# Shared client contract

`backend.json` is the sole checked-in default for public Appwrite client configuration. It contains no credentials. Android Gradle reads it from `../shared/backend.json`; iOS bundles this same file directly, without a copied configuration file. Android `local.properties` and environment values may override the public defaults for local development (nonblank local property, then nonblank environment, then JSON).

| JSON field | Meaning |
| --- | --- |
| `endpoint` | Appwrite `/v1` endpoint |
| `projectId` | Public Appwrite project ID |
| `databaseId` | Shared database ID |
| `tables.aiCommentaryCache` | Legacy AI commentary cache |
| `tables.favourites` | Existing favourites resource; iOS preferences remain local |
| `tables.highlights` | Highlights rows |
| `tables.highlightsMeta` | Highlights synchronization metadata |
| `tables.playerProfiles` | Legacy player profiles resource, not present in the CLI export |
| `tables.matchCommentary` | Scheduled writer's commentary rows |
| `tables.matchLiveDetail` | Scheduled writer's live detail rows |
| `tables.matchScorecard` | Scheduled writer's scorecard rows |
| `matchesBucketId` | Public Storage bucket holding match lists and existing cricket data |
| `apiFunctionId` | Public API function, with routes such as `/match-detail` |

All fields and table values are required nonempty strings. `tables` is the only nested object. The private match detail response cache is server-only and must never be added here. Signing material, Sentry upload tokens, Appwrite server API keys and provider credentials stay outside client configuration.

Android keeps its existing public BuildConfig names. All historical `*_DB_ID` aliases map to `databaseId`; the cache, favourites, highlights and player profile `*_COL_ID` aliases map to their named table. New route consumers use `APPWRITE_DATABASE_ID`, `APPWRITE_MATCH_COMMENTARY_COL_ID`, `APPWRITE_MATCH_DETAIL_COL_ID`, `APPWRITE_MATCH_SCORECARD_COL_ID`, `APPWRITE_MATCHES_BUCKET_ID`, `APPWRITE_API_FUNCTION_ID`.

Run `node shared/check-backend-config.mjs` from the repository root to check agreement with `appwrite/appwrite.config.json`. This is an offline guard, not proof of the currently deployed cloud state.

Match list blobs use `{data, fetchedAt}` in Storage: `live-matches.json`, `upcoming-matches.json`, `recent-matches.json`. Realtime detail and scorecard IDs use the backend's sanitized match ID; commentary is ordered by `sort_key`. The shared `/match-detail` response is versioned with `schemaVersion: 1`; its route implementation and fixtures define availability, stale data and retries. Only the existing scheduled backend writes live detail, scorecard and AI commentary. Client requests never invoke AI.
