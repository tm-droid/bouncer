# 6 Improvement Prompts for Sonnet 4.6

---

## 1. CommentaryFusionService Test Suite

**Goal:** 100% test coverage of LLM pipeline. Unit tests for happy path, fallback chains, rate-limit behavior, verification failures.

**Context:**
- File: `app/src/main/kotlin/com/tanush/bouncer/services/CommentaryFusionService.kt`
- Three model routes: `fusionRoute`, `verificationRoute`, `summaryRoute` with fallback chains
- `apiRateLimitedUntil: AtomicLong` tracks 429 state; `apiPermanentlyFailed: AtomicBoolean` tracks 401/402
- `fuse()` calls OpenRouter, then `verifyCommentary()` (second LLM call), stores to `AICacheService`
- `summarizeOversWithAI()` similar pattern for over summaries
- **No mocking library available** — use fake HTTP client or inline test doubles

**Deliverables:**
1. Test file: `app/src/test/java/com/tanush/bouncer/services/CommentaryFusionServiceTest.kt`
2. Test cases (minimum):
   - `fuse happy path` — calls OpenRouter, verifies, stores result
   - `fuse verification fails` — verify call rejects, falls back to raw snippet
   - `fuse cache hit` — skips API, returns cached
   - `fuse rate limited` — 429 blocks calls for 30min
   - `fuse permanently failed` — 401/402 blocks calls permanently
   - `summarizeOversWithAI happy path`
   - Model fallback chain works (primary fails, secondary succeeds)
3. Test doubles: fake HTTP client that returns canned JSON responses, fake `AICacheService` (or real with fake DAO)

**Acceptance:** All tests pass, ≥90% line coverage, CI green.

---

## 2. Live Data Resilience — Retry + Backoff

**Goal:** WebSocket + CommentaryFusionService pipeline survives transient failures. 3-retry exponential backoff (1s, 2s, 4s) for API timeouts/500s.

**Context:**
- `LiveScoreWebSocketClient` connects and streams score updates
- `CommentaryFusionService.fuse()` called on each ball; if OpenRouter times out, commentary stops
- Current: failures are logged but not retried
- Workers: `ScorePollingWorker` polls periodically (fallback); can feed retry buffer

**Deliverables:**
1. `services/RetryPolicy.kt` — configurable retry logic (max attempts, base delay, exponential multiplier)
2. Update `CommentaryFusionService.callOpenRouter()` — wrap in retry logic, log attempts
3. Update `LiveCommentaryManager` — catch timeouts, log, continue to next ball instead of crashing
4. **No new dependencies** — use `kotlinx.coroutines.delay()` for backoff
5. Test: verify 3 retries on 500, verify exponential delays, verify final failure is captured to Sentry

**Acceptance:** 500/timeout on OpenRouter → 3 retries with backoff → falls back to raw snippet. Sentry breadcrumb logged for each retry. Tests pass, CI green.

---

## 3. Search Pagination — Lazy Load + Memory Bounds

**Goal:** SearchRepository result sets lazy-load. Infinite scroll for series/historical/players. Memory-bounded cache per tab.

**Context:**
- `SearchRepository.search(query)` returns full list (series, historical, players)
- Used by `SearchViewModel` + `SearchScreen` (three tabs)
- Current: all results loaded into memory at once → OOM on huge result sets
- Room entities: `SeriesCacheEntity`, `HistoricalMatchCacheEntity`, `PlayerCacheEntity` all queryable

**Deliverables:**
1. Update `SearchRepository.search()` → return `PagingSource<Int, SearchResult>` (Paging 3 library, already in deps)
2. Separate DAO queries for each tab:
   - `SeriesCacheDao.searchSeries(query): Flow<PagingSource<Int, SeriesCacheEntity>>`
   - `HistoricalMatchCacheDao.searchHistorical(query): Flow<PagingSource<Int, HistoricalMatchCacheEntity>>`
   - `PlayerCacheDao.searchPlayers(query): Flow<PagingSource<Int, PlayerCacheEntity>>`
3. `SearchViewModel` — use Paging3 `Flow<PagingData>` per tab
4. `SearchScreen` — use `LazyPagingItems` to render + infinite scroll
5. Page size: 20 per page; prefetch distance: 5

**Acceptance:** Scroll through 1K results smoothly. No OOM. Paging metadata (position, size) visible in Sentry breadcrumbs. Tests verify PagingSource LOAD logic. CI green.

---

## 4. Widget Debouncing — Coalesce Updates

**Goal:** FCM + polling widget updates coalesced into single refresh per 5min. Reduce battery drain.

**Context:**
- `WidgetUpdateWorker` triggered by FCM score_update messages (1-3/min during live match)
- `WidgetUpdateWorker.enqueuePeriodicWork()` runs every 15min (backup polling)
- Current: each FCM push triggers immediate widget update (Glance `updateData()`)
- Goal: batch updates, refresh max once every 5min

**Deliverables:**
1. `managers/WidgetUpdateManager.kt` — singleton that:
   - Tracks last update time
   - Queues FCM updates in a buffer
   - Enqueues one-shot work for 5min from first update
   - On work trigger: refresh widget, clear buffer, reset timer
2. Update `LiveCommentaryManager` — instead of direct widget update, call `WidgetUpdateManager.queueUpdate(data)`
3. Update FCM receiver — call `WidgetUpdateManager.queueUpdate()` instead of direct worker enqueue
4. Metrics: Sentry gauge `widget_updates_coalesced` (count of suppressed updates per interval)

**Acceptance:** Live match with 1-3 FCM/min → ≤1 widget refresh per 5min. Gauge shows >50% coalescing. Battery profiler shows reduced WorkManager wakeups. Tests verify buffering + dedup. CI green.

---

## 5. Compose Recomposition Audit — Optimize Hotspots

**Goal:** Run Compose compiler metrics. Identify excessive recompositions in HomeScreenV2 + MatchCardV2. Optimize state + modifiers.

**Context:**
- CLAUDE.md mentions: `./gradlew assembleDebug -PenableComposeCompilerReports`
- Metrics written to `build/compose_metrics/report-*.txt`
- HomeScreenV2 has TabTemplate (Live/Upcoming/Recent)
- MatchCardV2 renders match data
- Goal: reduce unnecessary recompositions (unstable properties, lambda captures, derived state)

**Deliverables:**
1. Run metrics: `./gradlew assembleDebug -PenableComposeCompilerReports`
2. Analyze output for HomeScreenV2 + MatchCardV2:
   - Identify unstable classes (mutable captures, non-stable model types)
   - Count recompositions per composable
   - Flag "skippable" functions that recompose unnecessarily
3. Refactoring (pick 2-3 hotspots):
   - Wrap expensive state in `remember { ... }`
   - Use `derivedStateOf` for computed values
   - Extract lambda captures to stable callbacks
   - Add `@Stable` annotations to data classes passed as params
4. Before/after metrics: show reduction in recomposition count

**Acceptance:** Metrics generated. ≥2 refactorings applied. Before/after metrics show <50% recomposition reduction in target composables. No visual regressions. Tests pass. CI green.

---

## 6. Sentry Instrumentation — Cache + DB + API Spans

**Goal:** Add custom performance spans for AI cache hits/misses, Room DB queries, OpenRouter API latency breakdown.

**Context:**
- `SentryAiSpans` in `monitoring/` wraps OpenRouter calls
- `AICacheService` now has DB layer (just implemented)
- Goal: visibility into cache behavior + DB perf + API latency
- Custom spans: create parent span for `CommentaryFusionService.fuse()`, child spans for cache, verify, API calls

**Deliverables:**
1. Update `AICacheService`:
   - Span for `getCommentary()` with tag `cache_layer: "memory" | "db" | "miss"`
   - Tag `hit_latency_ms` for sub-ms memory hits
2. Update `CommentaryFusionService.fuse()`:
   - Parent span wrapping entire flow
   - Child spans: cache_check, verify_call, api_call, store
   - Tags: `cache_hit: true/false`, `verification_passed: true/false`, `fallback_used: true/false`
3. Add Room span wrapper:
   - Query span for DB reads (in `AICacheDao`)
   - Tag with table name + query duration
4. Sentry dashboard: new metric `ai_commentary_pipeline_latency` breakdown by stage

**Acceptance:** Sentry Performance tab shows cache/DB/API breakdown for typical fuse() call. ≥10 live matches instrumented. Metrics persisted to Sentry. Tests verify span creation. CI green.

---

## How to Use These Prompts

Each prompt is self-contained. To execute:

```
I want to implement improvement #[1-6]. Here's the full spec:

[paste prompt text]

Context: Working in Bouncer Android at /Users/tanushmalhotra/Documents/Tanush\ Coding/Bouncer/android

Use superpowers:brainstorming + feature-dev skills. Accept my approval before implementing.
```

Sonnet 4.6 will explore, design, ask clarifying Qs, then implement + test.
