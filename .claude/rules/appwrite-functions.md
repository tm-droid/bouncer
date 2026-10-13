---
paths:
  - "appwrite/functions/**"
  - "appwrite/appwrite.config.json"
  - "appwrite/tools/**"
---

# Appwrite Functions

Functions live in `appwrite/functions/` (Node.js). `appwrite/appwrite.config.json` defines project-level config. Nine functions; the match-list poller, once a tenth, runs inside `match-sync`. `match-sync` itself is in `match-sync-commentary.md`; `win-probability` (on-demand, same `winprob.js` as match-sync, no device caller) and `train-win-probability` (Mondays 02:00, ~280 s and ~760 MB of its 900 s / 1 GB) are in `win-probability.md`.

## `highlights-sync`

15-min schedule; syncs highlight clips to Appwrite. Requires `HIGHLIGHTLY_API_KEY`, optional `SENTRY_DSN`.

- **Only one run per hour does anything** (`minute < 15`), and only every 4th hour sweeps the 3-day back-window. Highlightly allows 100 requests per rolling window and `*/15` alone would spend 96 on today. Never make it fetch on every run without raising that plan.
- The back-window exists because Highlightly publishes a clip hours-to-days after its match; a same-day-only query permanently loses whatever the lag exceeds.
- Deploy: the ~13 MB `--with-node-modules` archive is rejected whole by the CDN edge (`503 backend write error` in ~55 ms), so it goes up in 5 MB chunks. `node appwrite/tools/deploy-function.mjs` does that and is the deploy path.

## `generate-commentary`

On-demand; LLM commentary via the shared groq/Gemini ladder in the vendored `llm.js`. It has never used OpenRouter. **It has no caller.** Its one device caller, `CommentaryFusionService`, was a second pipeline no production path constructed, and was deleted. The function stays deployed because its `llm.js` is the vendored copy `LlmJsVendorParityTest` pins and its provider probe is the one signal reaching the providers on a day with no live match. Do not describe it as something the device calls. Its execute permission is **empty** since 2026-10-08 (server key only): it was `execute("any")` with a caller-supplied prompt, system prompt and `maxTokens`, so anyone with the project id could spend the provider quota. `appwrite.config.json` says the same; do not reopen it.

## `news-sync`

On-demand; fetches Cricbuzz news via RapidAPI and caches to `cricket-stats` (`news-cache`, 4-hr TTL caps RapidAPI at ~6 calls/day). Requires `RAPIDAPI_KEY`.

- Cricbuzz body text carries placeholders (`@B0$`, `@L0$`) whose words are in the payload's top-level `format` array; the function resolves them (`articleBody.js`, pinned against `test/fixtures/detail-140424.json`).
- It fetches a body upstream only for a story in its own headline index, and claims the fetch before making it (`claimFetch`: once per story per ten minutes, 40 stories per container per window).
- It decides a refetch by the stored body's `formatResolved` mark, never by what the text looks like.
- `news-body-*` pruning is in `data-and-storage.md`.

## Match-list poll (`match-sync/src/poll.js`, `runPoll`)

Was the `bouncer-poll-live-matches` function; merged into `match-sync` on 2026-09-30. The old function is **disabled with its schedule cleared**, not deleted, and is gone from `appwrite.config.json`.

- Runs at the **top of every match-sync run, ahead of every early return**: a latched read-budget 402 or a broken smoke probe must not stop the lists (the poll spends no DB reads). The live list is handed over in memory; a failed poll, or an empty live parse that kept the previous file, falls back to downloading `live-matches`.
- **Parses the Cricbuzz list pages itself** (`src/matchlist.js` over the flight payload, `src/matchstate.js` for which tab a match belongs on) and writes `live-matches`, `recent-matches`, `upcoming-matches`. It used to read `${VERCEL_API_BASE}/matches?type=…`, a separately-hosted scraper answering `{id, title, status}` — which is why the widget split the title on " vs ", Android Auto showed no score, and nothing downstream carried team ids.
- The summary carries `state`, `matchFormat`, `seriesId`, `startDate` and a `teams` array (id, name, score, overs, isBatting); every added field has a device default so an older file still parses.
- **An empty parse never overwrites a good file.**
- Owns `active-series`, a **superset of active plus archived** series: it walks `cricket-scorecard-archives/{year}` two pages per run, resuming from an `archiveCursor` stored in the file. Active-only meant an out-of-season competition ("World Cup") matched nothing in the app. A `CricbuzzBlockedError` aborts the walk, keeps the previous file and does not advance the cursor.

## `player-search`

On-demand; resolves a player name to a **Cricbuzz** profile id for `PlayerProfileScraper.searchPlayersDetailed`. Needs `timeout: 90` for the index build. `PROBE_SOURCES=1` turns one execution into a reachability probe.

- Cricbuzz has no working player search (`/api/search/results` and `/search` 404; `/api/html/search` is a 200-empty stub), so it builds a name→id index from squad pages (`/cricket-team` → `/cricket-team/{slug}/{id}/players`, 38 pages, 593 players on the first live build), cached in `cricket-stats` as `player-index` with a 7-day TTL.
- Cricinfo's search works but answers with Cricinfo ids, and `buildProfileUrl` builds Cricbuzz `/profiles/{id}/` URLs; switching id namespace trades a dead search for a dead profile screen.
- **The cached index is read with `parseJsonFile`, never `Buffer.from`.** node-appwrite 15 returns an already-parsed object for `application/json`; the unconditional `Buffer.from` threw into the "no cached index" catch, so every query rebuilt the corpus (39 fetches, ~14s).
- Returns **503, never an empty 200**, when it has no index and cannot build one: absence and unavailability must stay distinguishable.
- **A search never rebuilds an index it can answer from** (`indexPlan`). The rebuild runs on the function's schedule (`0 3 */3 * *`, detected by `x-appwrite-trigger: schedule`, a header Appwrite refuses from callers); a search serves whatever index exists, however old. Only a search that finds no index builds inline. An inline rebuild is ~15 s against the **10 s read timeout of the Android SDK's OkHttp client** (it sets none of its own), so that search drew "Online player search is unavailable" and reported nothing (`SocketTimeoutException` is filtered as connectivity). That 10 s bounds every synchronous function call the app makes.
- **A rebuild merges, it does not replace** (`mergePlayers`, the "merge, never shrink" rule `tools/update_logos.py` follows): a failed squad page used to remove its whole team, and a dropped player became unfindable though his profile exists. The walk stays a full one — Cricbuzz has no change feed.

## `match-presence`

On-demand; the server half of the viewer gate. Env: `APPWRITE_API_KEY`, `MATCH_CACHE_BUCKET_ID`.

- `MatchPresenceReporter.ping` posts `{matchId}` every 90s from an open match screen; `pingAll` posts `{matchIds: [...]}` (≤10) every 90s from `LiveUpdateService` for every followed match.
- It read-modify-writes an `active-viewers` map (`{matchId: epochMs}`) in `cricket-stats` and prunes entries older than `PRESENCE_TTL_MS` (4 min). That TTL must stay **≥ match-sync's `VIEWER_TTL_MS`**.
- Concurrent pings race on the read-modify-write (last write wins) — accepted; the next heartbeat heals it.
- A ping that finds its match stamped under 60s ago returns without writing (`FRESH_SKIP_MS`): N viewers cost ~1 write a minute, and staleness is unchanged.

## `match-commentary-prune`

Daily at 03:00; deletes `match-commentary` rows older than `COMMENTARY_RETENTION_DAYS` (14). Nothing else deletes them and the table had reached ~500k rows.

- Each pass is one **bulk** `deleteRows` filtered server-side by `lessThan("$createdAt", cutoff)` (built-in, no user index). It used to list 100 rows and delete each: ~6,700 billed reads a day, the project's largest read cost.
- Bounded to `BATCH` 100 (the Free-plan bulk cap; more fails the request) × `COMMENTARY_PRUNE_MAX_PASSES` 100 per run, to stay inside the 300s timeout; raise the passes temporarily to drain a backlog. A short batch ends the run.
- Needs node-appwrite ≥ 26 (bulk ops). A 402 aborts and returns 402 with the partial `deleted` count.
