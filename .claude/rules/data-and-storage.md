---
paths:
  - "app/src/main/kotlin/**/data/**"
  - "app/proguard-rules.pro"
  - "appwrite/functions/*/src/main.js"
---

# Data, Tables and Storage

## Tables written by `bouncer-match-sync`

Both device clients use `appwriteAiCacheDbId` as their DB ID.

### `match-commentary`

Columns: `match_id`, `innings`, `ball`, `sort_key` (timestamp, used for ordering), `commentary`, `ai_generated` (boolean, default true), `provider`, `created_at`. Realtime emits `CommentaryEvent` on create.

**Reading (`AppwriteMatchCommentaryClient`).** Newest-first by `sort_key` desc, **48 rows on the first page and 150 on every page behind it** (`FIRST_PAGE_LIMIT`/`FEED_LIMIT`): reads bill per row RETURNED and the common case is a glance at the last over or two. `getOlderThan(sortKey)` pages back on a reader's tap. Ordering on `sort_key` needs a user index; the fallback ladder is `orderDesc("$createdAt")` (built-in, still newest-first), then an unordered fetch, which returns the OLDEST rows of the match and is the last rung before a blank feed. Degraded rungs cannot page (the cursor filters on the attribute that just failed): they serve one `FEED_LIMIT` page and a request for older lines gets nothing.

**Wickets and Boundaries chips ask the table for the whole match.** `getEvents`: `equal("event", …)` over `idx_match_sort`, 60 rows a page, fetched once when the chip is first selected and held by `CommentaryEventPager` apart from the feed — merged into the feed, an old wicket would move the load-earlier cursor past every ball between. They used to filter the loaded feed only (the last eight overs' events), which was misread as a rewrite-backlog ordering problem. Fetched rows are unioned with the loaded feed by `sort_key` (`mergeEventRows`), because a wicket that falls while the chip is selected arrives over Realtime. A failed fetch leaves the chip on the loaded feed and is not "no wickets". Milestones still draws from the loaded feed only (match-sync writes no milestone event), which is why its empty state has its own load-earlier action.

**`ai_generated` and `provider`.** `ai_generated = false` means the row holds raw Cricbuzz text, and **no new row has been written that way since 2026-09-27** (an unverified delivery is held in the rewrite backlog); older false rows are pruned after 14 days. The device does not read the column. `provider` is finer: a rung name (`groq`, `gemini-flash-lite`, …) means rewritten; `cricbuzz` means nobody tried; `cricbuzz:providers_exhausted` / `cricbuzz:verification_failed` / `cricbuzz:verifier_unavailable` mean somebody tried and failed, and which — a defect and a design decision need opposite responses. Both come from `commentaryOrigin` in `main.js`; the invariant that `ai_generated` is true exactly when `provider` is not a `cricbuzz*` value is pinned in `commentary-storage.test.js`.

### `match-live-detail`

One row per match, upserted every minute. Columns: `match_id`, `payload` (JSON `MatchLiveDetailPayload`), `updated_at`. Row ID = sanitized `matchId`. Read by `AppwriteMatchDetailClient`; Realtime emits `DetailEvent` on create/update.

### `match-scorecard`

One row per match, row ID = sanitized `matchId`, columns `match_id`, `scorecard` (JSON `Scorecard`), `updated_at`. **No index.** `AppwriteMatchScorecardClient.getForMatch` is one `getRow` for first paint, then `observeMatch` for the life of the detail screen. It exists because the scorecard measured 85–93% of the detail payload (4–9 KB) and was pushed to every subscriber every minute; split off, the live card's ~650-byte updates travel alone.

- **Written on a wicket or a new innings at once, otherwise at most every `SCORECARD_MIN_INTERVAL_MS` (3 min)** (`scorecardWriteDue`, pinned by `scorecard-throttle.test.js`). Figures change every ball, so the unchanged-body skip never fired and a viewed match cost a write a minute. The finalising write is never throttled.
- **It carries `overs`**: one point per completed over (`innings`, `over`, `runs`, `score`, `wickets`, `team`) accumulated from each run's live-page `overSeparator`s (`parseOverHistory`); no server-rendered Cricbuzz page carries the history. A cold container seeds from this row once per match (one budgeted read); overs bowled while no container held the history are absent. The device builds the worm/manhattan from it (`OverWorm`); a gap ends the worm line rather than being filled.

## Storage bucket `cricket-stats`

Read-only from Android via `AppwriteClient.downloadFile(bucketId, fileId)`; written only by functions. Most files are inter-function state:

| File | Owner | What it is |
|---|---|---|
| `team-stats`, `h2h-stats` | `train-win-probability` | the last **v5** model, no longer rewritten (the fallback when there is no manifest); H2H for 1,700+ team pairs, T20 only, read by the device |
| `active-series` | match-list poll | active **and archived** series, read by `SeriesSyncWorker` |
| `live-matches`, `recent-matches`, `upcoming-matches`, `live-count`, `poll-state` | match-list poll | the lists `match-sync` runs off, plus its cursor/hash state |
| `live-winprob`, `slow-sync-state`, `rewrite-backlog` | `match-sync` | published probabilities; the per-match slow-tier clock; deliveries held until a line passes the checker |
| `mc-{matchId}` | `match-sync` | one match's context: sides, ground, record, elevens, forecast, preview, summary. Read by the device (Info and Summary tabs). Pruned after 14 days by `pruneContexts`, selected by `startsWith("$id", "mc-")` |
| `wpe-{matchId}` | `match-sync` | one finished match's evaluation log. Not read by the device. Pruned after 60 days by `pruneEvalLogs` |
| `prematch-log` | `match-sync` | each upcoming fixture's latest pre-match probability, `{matchId: {teamId, p, at}}`, kept a week |
| `wci-{cricinfoId}` | `match-sync` | Cricinfo's live win prediction for one match, keyed by CRICINFO's match id; pruned after 60 days |
| `wpr-{matchId}` | `match-sync` | a live match's raw Cricbuzz win-probability samples, one per over; folded into `wpe-{matchId}` and deleted when the match ends |
| `winprob-manifest`, `winprob-v6-<stamp>` | `train-win-probability` | the pointer to the serving model, and the model (current and previous) |
| `wpx-00`…`wpx-3f`, `wps-a`…`wps-z` | `train-win-probability` | context join tables: rosters with ratings and records, grounds, head-to-head; and everyone by surname letter |
| `active-viewers` | `match-presence` | `{matchId: epochMs}`, the viewer gate |
| `player-index` | `player-search` | name → Cricbuzz profile id |
| `news-cache`, `news-body-{storyId}` | `news-sync` | headline index (4h TTL); one article body per opened story |
| `rate-limit-offenders` | — | legacy, unread |

**Select deletions by `startsWith("$id", …)`, never by `search`.** Storage search is fuzzy: `search=news-body` also returns **`news-cache`**, while `startsWith($id, "news-body-")` returns exactly the bodies. `bouncer-news-sync` prunes `news-body-*` past `BODY_RETENTION_MS` (14 days) on its **headline-refresh path only** (already capped at ~6 runs/day by the 4h TTL) and re-checks the prefix on every candidate before deleting.

## Realtime lifetime

**App-lifetime Realtime starts with the first activity, not the process.** `RealtimeLifecycleController.attach` begins paused: `Application.onCreate` also runs for WorkManager and FCM wakes, which never reach `onActivityStopped`, and the highlights channel used to stay open for such a process's whole life. The match-detail streams (`MatchDetailViewModel`) go through `whileForeground` (`viewmodels/ForegroundGate.kt`) for the same reason and catch up on return: one `getRow` for detail, `getNewerThan(sort_key)` for commentary (bills only rows missed). A finished match subscribes to no stream, and its first commentary page is served from `ProductionLiveDetailSource`'s small LRU on re-open. `WidgetUpdateWorker` does nothing — and does not re-arm — when no Bouncer widget is placed; the receivers' `onEnabled` restart it.

## Room

**Schema version 15.** v14→v15 dropped the `commentary` table, which nothing ever inserted into. New migrations: add `MIGRATION_N_(N+1)` in `BouncerDatabase.kt` and bump `version` in `@Database`.

The `matches` table exists only for highlight fuzzy-matching; `PlayedMatchIndex` writes it from the Live and Recent lists, and each Recent write re-scores highlights still stored unmatched. Nothing wrote it until September 2026, which hid how loose `HighlightFuzzyMatcher` was: with rows, 154 of 400 highlights linked on a test device and every link was wrong (one shared team plus the league cleared the averaged threshold weeks apart). A contradicting date (>1 day), a side below 0.92 similarity, or a different qualifier ("Women", "A", "XI") now vetoes outright — pinned by `HighlightFuzzyMatcherTest` with those real pairs. **Check a matcher change against rows from the device, not only fixtures.**

**No FTS.** `player_fts`, `series_fts`, `historical_match_fts` and `historical_match_cache` (never written) were dropped in v12→v13. The FTS entities were `@Fts4` with **no `contentEntity`**, so Room created no sync triggers; the populating triggers existed only inside `MIGRATION_1_2`, which a fresh install never runs. Every install after schema v1 searched an empty index. `SeriesCacheDao`/`PlayerCacheDao` use `LIKE` over the backing tables, which is also what makes acronym expansion work. Do not reintroduce an FTS index without `contentEntity`.

**Player profile cache (global).** `PlayerCacheWorker` checks `AppwritePlayerProfilesClient` before scraping Cricbuzz. The first device that scrapes a player pushes to Appwrite (`player-profiles` table, `bouncer-db`); later devices get a cache hit. Row ID = `entity.playerId` (Room format: `virat_kohli`).

**AI cache: dormant on the device.** `BouncerApp` no longer calls `AICacheService.initialize`. `AICacheService` (`MemoryFifoCache` → Room `ai_commentary_cache` → Appwrite `ai-commentary-cache`) has no caller, so `getCommentary`/`setCommentary` are unreachable. Its Realtime table subscription was removed in September 2026: it fed `ingestFromRealtime`, whose rows nothing reads. The class and the startup replay stay until an R8 `usage.txt` pass removes them; do not re-add the subscription.

## R8

**R8 renames the Appwrite SDK's own request fields.** `io.appwrite.Query.toString()` is `JsonExtensionsKt.toJson(this)` — reflective Gson over the private `method` / `attribute` / `values` fields — and that string goes on the wire. Minified, it sent `{"a":"orderDesc",…}`; Appwrite answered `Invalid query method:` and **every** query failed while each caller swallowed it into its own fallback. `Query` and `Operator` sit outside `io.appwrite.models`, so `proguard-rules.pro` keeps both explicitly. Verify in the minified dex, not the source: `dexdump -d <classes.dex>` must still show `name : 'method'` inside `Lio/appwrite/Query;`.
