---
paths:
  - "appwrite/functions/**"
  - "appwrite/appwrite.config.json"
  - "appwrite/tools/**"
---

# Appwrite Functions

Functions live in `appwrite/functions/` (Node.js). `appwrite/appwrite.config.json` defines project-level config. `match-sync` itself is in `match-sync-commentary.md`; the trainer is in `win-probability.md`.

## Two functions, because the Free plan allows two

Appwrite's Education plan ends for this org in November 2026 and Free allows **two functions per project**, 512 MB each. So:

- `bouncer-match-sync` — the per-minute job, unchanged.
- `bouncer-api` (`functions/api`) — five former functions, each still a self-contained handler in its own directory under `src/` with its tests under `test/<name>/`. `src/main.js` only routes: an on-demand execution by its **path** (`/news`, `/player-search`, `/presence`; anything else is a 404, never a default handler), a scheduled one by the clock (`scheduledJobs`: highlights on the first tick of each hour, the prune on the third tick of every hour, the player-index rebuild at 04:15 every third day — **one job per `*/15` slot**, pinned by `router.test.js`, because together they do not fit the 300 s timeout).
- **Each public route carries its own timeout** (`ROUTES[...].timeoutMs`: news 30 s, player-search 90 s, presence 15 s — what each had as a function). The function's timeout is 300 s for the prune and the routes are `execute("any")`; a route added without a cap inherits 300 s for any caller.
- **Two node-appwrite majors are installed in `api`**: `node-appwrite` (15) for news, player-search and presence, `node-appwrite-26` (an npm alias) for highlights and the prune, which needs bulk deletes. Moving a handler across is a migration of that handler.
- The device calls a route as `bouncer-api/<route>`; `AppwriteClient` splits it into function id and path and keeps the whole string as the guard, usage and Sentry key.
- **Secret variables cannot be read back** from the API, so a new function's keys come from `local.properties` or the Console. `HIGHLIGHTLY_API_KEY` is not a separate credential: Highlightly is a RapidAPI listing, and `RAPIDAPI_KEY` answers it (HTTP 200, measured 2026-10-14). It is set on `bouncer-api` to that value.
- **The old functions are deleted** (news-sync, player-search, match-presence, match-commentary-prune and highlights-sync on 2026-10-14; the project is at two). Their executions had dropped to zero once the build calling `bouncer-api/<route>` reached Play; an older install loses news, player search and the viewer gate.
- **Variable ids are project-global**, not per function: a second function's `SENTRY_DSN` needs a different `variableId`.
- The trainer runs in GitHub Actions (`appwrite/tools/run-function.mjs` imports the handler unchanged).
- Deployments and builds count toward the 2 GB storage cap (since 2026-09-01); `deploy-function.mjs` prunes to the serving deployment and the one before it after a verified deploy. `match-sync` alone had reached 190 deployments and 4.8 GB.

The sections below describe the handlers; the names are their directories under `functions/api/src/`.

## `highlights-sync`

15-min schedule; syncs highlight clips to Appwrite. Reads `HIGHLIGHTLY_API_KEY`, falling back to `RAPIDAPI_KEY` (the same value; reading the override alone lost every sync 2026-10-13..14, ANDROID-5N); optional `SENTRY_DSN`.

- **Only one run per hour does anything** (`minute < 15`), and only every 4th hour sweeps the 3-day back-window. Highlightly allows 100 requests per rolling window and `*/15` alone would spend 96 on today. Never make it fetch on every run without raising that plan.
- The back-window exists because Highlightly publishes a clip hours-to-days after its match; a same-day-only query permanently loses whatever the lag exceeds.
- Deploy: the ~13 MB `--with-node-modules` archive is rejected whole by the CDN edge (`503 backend write error` in ~55 ms), so it goes up in 5 MB chunks. `node appwrite/tools/deploy-function.mjs` does that and is the deploy path.

## Removed in October 2026

`generate-commentary` (on-demand, no caller since `CommentaryFusionService` was deleted; `llm.js` now has one copy, in `match-sync`) and `win-probability` (on-demand, no device caller; `winprob.js` and `contextmath.js` live in `match-sync`, the latter also in the trainer). Both went so the project fits two functions. Their unique tests moved to `match-sync/test` (`winprob.test.js`, `storage-json.test.js`). Rule files that still name them describe measurements taken on them.

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
- **A search never rebuilds an index it can answer from** (`indexPlan`). The rebuild runs from `bouncer-api`'s schedule (04:15 every third day, detected by `x-appwrite-trigger: schedule`, a header Appwrite refuses from callers); a search serves whatever index exists, however old. Only a search that finds no index builds inline. An inline rebuild is ~15 s against the **10 s read timeout of the Android SDK's OkHttp client** (it sets none of its own), so that search drew "Online player search is unavailable" and reported nothing (`SocketTimeoutException` is filtered as connectivity). That 10 s bounds every synchronous function call the app makes.
- **A rebuild merges, it does not replace** (`mergePlayers`, the "merge, never shrink" rule `tools/update_logos.py` follows): a failed squad page used to remove its whole team, and a dropped player became unfindable though his profile exists. The walk stays a full one — Cricbuzz has no change feed.

## `match-presence`

On-demand; the server half of the viewer gate. Env: `APPWRITE_API_KEY`, `MATCH_CACHE_BUCKET_ID`.

- `MatchPresenceReporter.ping` posts `{matchId}` every 90s from an open match screen; `pingAll` posts `{matchIds: [...]}` (≤10) every 90s from `LiveUpdateService` for every followed match.
- It read-modify-writes an `active-viewers` map (`{matchId: epochMs}`) in `cricket-stats` and prunes entries older than `PRESENCE_TTL_MS` (4 min). That TTL must stay **≥ match-sync's `VIEWER_TTL_MS`**.
- Concurrent pings race on the read-modify-write (last write wins) — accepted; the next heartbeat heals it.
- A ping that finds its match stamped under 60s ago returns without writing (`FRESH_SKIP_MS`): N viewers cost ~1 write a minute, and staleness is unchanged.

## `prune` (match-commentary, end of each billing cycle)

Deletes `match-commentary` rows only with writes that would otherwise go unused. Until 2026-10-15 it deleted every row older than 14 days at 03:15 daily, and those deletes were 28% of the project's writes: a bulk delete bills **one write per row** (1,676 rows, 1,676 writes, measured 2026-10-14).

- **It deletes only what the archive holds.** That means rows of a match the archive has finalised, created before the archive's `watermark` (`archive-data.md`). Final ids come from the archive's month indexes, oldest first. Nothing else is deleted, however old.
- **When it acts.** Every hour's third tick runs it. It skips (one GitHub Pages request) unless the time is inside the last 12 hours of the billing cycle named in the archive's `manifest.json`, stopping 15 minutes before the end.
- **The budget.** No API key can read usage, so `budget.js` estimates writes used from the archive's count of this cycle's commentary rows (`cycle.commentaryRows`, which matched Appwrite's own total exactly: 4,775 of 4,775).
  - Estimated writes used = rows × 1.15. Creates were 66% of writes and PATCHes ~6%; the 15% covers those and unmeasured writes.
  - Budget = 90% × (250k − estimated − 5k reserved for the window).
  - The budget is fixed once per cycle in the bucket file `prune-state` (`{cycleStart, budget, pruned}`). Recomputing it every run would creep to the limit.
- **It skips** when the manifest is over 4 hours old or has no watermark.
- **Limits.** 100 rows per bulk call (the Free cap); 100 match ids per `equal`; 200 s per run, then the state is saved even after an error, because an uncounted delete would be spent twice. A write-budget 402 stops it.
- **The cycle** is a 30-day period anchored 2026-10-14T00:00Z (`cycleOf` in the archiver; the Console's `billingNextInvoiceDate` was 2026-11-13). **After each cycle, compare the Console's write total with the estimate** (`organizations_get_usage`) and the next invoice date with `cycleOf`, and correct `OTHER_WRITES_FACTOR` or the anchor.
