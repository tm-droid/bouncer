# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Bug Fixing Philosophy
- NEVER suppress, filter, or hide errors (e.g., Sentry filters, try/catch swallowing) without first identifying and fixing the root cause.
- When a bug is reported across multiple items (chapters, volumes, files), search for ALL occurrences before claiming a fix is complete.

## Environment & Secrets
- Before assuming an API/env var is configured, verify it explicitly (check `.env`, deployment config, or ask). Do not assume RAPIDAPI_KEY, Appwrite keys, etc. are set.
- For Appwrite changes, prefer the MCP server over CLI push (CLI has caused domain rule conflicts and column schema deletion from config drift).

## Git Workflow
- After completing requested changes, commit and push directly to main (do not create a PR unless explicitly asked).
- Always run a clean build and tests before pushing; do not push a broken build.

## Code Tasks
- When asked to 'look at the app' or audit features, INSPECT THE CODEBASE (Read/Grep) rather than launching, screenshotting, or building mocked/fake screens.
- Never substitute mocked UI for real screens driven by the app's actual ViewModels.

## Audits & Fixes
- For audit-then-implement requests, diagnose the true root cause before applying fixes (e.g. trace transitive/build-classpath deps, not just app deps) and verify each fix actually resolves the issue.

---

# Bouncer Android App

Cricket live-scoring, match analytics, and team management app built in Kotlin/Compose.

## Quick Start

**Setup (first time):**
```bash
cp local.properties.template local.properties  # Fill in secret keys (RAPIDAPI_KEY, GROQ_API_KEY, etc.)
# Most non-secret Appwrite IDs are pre-filled — only secret API keys need manual entry.
```

**Build & run:**
```bash
./gradlew build                  # Full build
./gradlew installDebug           # Install to emulator/device
./gradlew assemble               # Dev build (faster, skips Sentry transforms)
./gradlew test                   # Run all unit tests
./gradlew testDebugUnitTest --tests "*MatchUtilsTest"  # Run single test (--tests is invalid on the aggregate `test` task)
./gradlew clean build            # Clean build (if cache corrupted)
./gradlew detekt "-Dorg.gradle.java.home=$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home"  # Static analysis (see below)
./gradlew assembleDebug -PenableComposeCompilerReports  # Compose metrics
```

**Detekt:** `maxIssues: 0` is enforced. Pre-existing debt lives in `app/config/detekt-baseline.xml` (~533 entries) — new/modified code must be detekt-clean; never re-baseline files you touch. detekt 1.23.x cannot run on JDK 25 (`EnvironmentFacade` crash), so detekt tasks must run under JDK ≤21 via `-Dorg.gradle.java.home` (JBR 21 is installed at the path above). Regular builds use the default JDK.

**Release builds** require `sentry-cli` at `/opt/homebrew/bin/sentry-cli` (pinned in `sentry.properties`): `brew install getsentry/tools/sentry-cli`. Without it, `uploadSentryProguardMappingsRelease`/`sentryBundleSourcesRelease` fail.

## Architecture

**Single kotlin source tree.** `app/src/main/kotlin/` (~325 files) is the only main source tree — the former `app/src/main/java/` was merged into it in June 2026. `BouncerActivity`, `LiveActivityService`, `ui/components/glass/`, and `ui/components/premium/` all live in the kotlin tree. (`app/src/androidTest/java` and `app/src/test/java` remain for test code only.)

```
app/src/main/kotlin/com/tanush/bouncer/
├── BouncerApp.kt       # App entry + startup sequence
├── ApiKeys.kt          # Single source for all API key access — use this, not BuildConfig directly
├── SentryOptionsConfig.kt  # Sentry runtime init options
├── ui/
│   ├── screens/        # Full-page Compose screens
│   ├── components/     # Reusable composables (+ components/v2/, components/premium/)
│   ├── widgets/        # Glance home-screen widgets
│   ├── navigation/     # BouncerNavHost.kt — single NavHost, all routes
│   └── theme/          # Material3 theme
├── viewmodels/         # MVVM ViewModels (no Hilt — manual construction)
├── services/           # FCM, CommentaryFusion, LiveCommentary, Workers, AICacheService
│                       # RetryPolicy.kt: WORKER_MAX_RETRIES — shared retry cap for all Workers
├── managers/           # Singleton managers (TeamLogo, TeamAcronym, FavouriteTeams, MatchSubscription, NotificationInbox)
├── network/            # Retrofit + Jsoup scrapers (CricBuzz, CricInfo, Series, PointsTable, PlayerProfile)
├── data/               # Room DB, repositories, AppResult<T> monad
│   ├── CacheConfig.kt  # All cache TTLs + realtime reconnect backoff + permanent-cache sentinel — no magic numbers
│   └── remote/         # AppwriteClient (singleton) + table clients extending AppwriteTableClient
│                       # AppwriteAICache, AppwriteHighlightsClient, AppwritePlayerProfilesClient,
│                       # AppwriteFavouritesSync, H2HRepository,
│                       # AppwriteMatchCommentaryClient (match-commentary table),
│                       # AppwriteMatchDetailClient (match-live-detail table),
│                       # AppwriteMatchScorecardClient (match-scorecard table, never subscribed)
│                       # AppwriteReadGuard (per-quota breaker), AppwriteUsageTracker (Sentry gauges),
│                       # RealtimeLifecycleController (background pause) — see appwrite-patterns.md
├── models/             # Data models and entities (proguard-kept for Gson — never delete on grep evidence)
├── analysis/           # Cricket analysis + ML models (score prediction, win probability)
├── auto/               # Android Auto (BouncerCarAppService)
├── mcp/                # Model Context Protocol handlers
└── utils/              # WorkerLogger.enqueued/failed (Workers) + sentryBreadcrumb() (SentryBreadcrumbs.kt)
                        # — use these instead of raw Breadcrumb().apply boilerplate
```

**Appwrite cloud functions** live in `appwrite/functions/` (Node.js, deployed via Appwrite CLI). `appwrite/appwrite.config.json` defines project-level config. Seven functions:
- `highlights-sync` — 15-min schedule; syncs cricket highlight clips to Appwrite
- `generate-commentary` — on-demand; LLM commentary via OpenRouter (called by `CommentaryFusionService`)
- `match-sync` — every-minute schedule; fetches live match HTML + scorecard → upserts `match-live-detail` → AI commentary → `match-commentary` (see detailed section below)
- `news-sync` — on-demand; fetches cricket news from Cricbuzz via RapidAPI, caches to `cricket-stats` bucket (`news-cache` file, 4-hr TTL caps RapidAPI at ~6 calls/day). Requires `RAPIDAPI_KEY`.
- `poll-live-matches` — scheduled; fetches live match data server-side
- `win-probability` — on-demand; calculates real-time win probability
- `train-win-probability` — scheduled; retrains win-probability model from match history

Endpoint/projectId are auto-injected by the runtime — only `APPWRITE_API_KEY` and DB/collection IDs need manual env vars.

**No dependency injection framework.** ViewModels are constructed manually; managers use `object` singletons initialized in `BouncerApp.onCreate`.

**Error handling:** `AppResult<T>` monad (`data/AppResult.kt`) wraps success/failure. Use `.fold(onSuccess, onFailure)` in ViewModels; propagate via `return AppResult.Error(e)` in repositories. Don't unwrap with `!!`.

**Navigation:** Single `BouncerNavHost` with Compose Navigation. Deep link entry via `app://bouncer/match/{matchId}` — scheme `app`, host `bouncer`, as registered in `AndroidManifest.xml` and parsed by `handleExternalDeepLink` in `ui/MainAppContainer.kt`. A `bouncer://` URI does not resolve.

**Live data flow:** score polling (`ScorePollingWorker` + FCM) → `LiveCommentaryManager` → `CommentaryFusionService` (delegates to `bouncer-generate-commentary` Appwrite function; OpenRouter used server-side) + `BallCommentaryService` (Groq → Cerebras → SambaNova per-ball) → UI via StateFlow. `AICacheService` warms cache from Appwrite Realtime events.

**AI cache (3-tier):** Memory LRU (256 entries) → Room `ai_commentary_cache` → Appwrite `ai-commentary-cache` table. All four commentary functions (`generateBallCommentary`, `fuse`, `streamCommentary`, `summarizeOversWithAI`) check cache before calling any AI provider. Completed matches use **permanent TTL** (`Long.MAX_VALUE` in Room, `expires_at = "2099-01-01T00:00:00.000Z"` in Appwrite) — `isMatchComplete` flag threads from ViewModels → `LiveCommentaryManager` → `CommentaryFusionService` → `AICacheService`. Never set permanent=true for live matches.

**Player profile cache (global):** `PlayerCacheWorker` checks `AppwritePlayerProfilesClient` before scraping Cricbuzz. First device that scrapes a player pushes to Appwrite (`player-profiles` table, `bouncer-db`); all subsequent devices get a cache hit. Row ID = `entity.playerId` (Room format: `virat_kohli`).

**Appwrite project:** single database `bouncer-db`. Known tables: `ai-commentary-cache`, `highlights`, `highlights-meta`, `user-favourites`, `player-profiles`, `match-commentary`, `match-live-detail`, `match-scorecard`.

**`match-commentary` table:** written by `bouncer-match-sync` (every minute). Columns: `match_id`, `innings`, `ball`, `sort_key` (timestamp, used for ordering), `commentary` (AI-rewritten line), `ai_generated` (boolean, default true), `created_at`. Read by `AppwriteMatchCommentaryClient` — newest-first 60 rows ordered by `sort_key` desc; Realtime subscription emits `CommentaryEvent` on create. **`ai_generated = false` means the row holds raw Cricbuzz text**: the provider chain returned nothing and match-sync wrote the scraped line rather than nothing. The device does not read the column — it exists so "is the stored commentary actually AI?" is answerable from the storage layer, which it was not when a dead chain and a working one produced identical-looking rows.

**`match-live-detail` table:** written by `bouncer-match-sync` (every minute). One row per match, upserted. Columns: `match_id`, `payload` (JSON `MatchLiveDetailPayload`), `updated_at`. Row ID = sanitized `matchId`. Read by `AppwriteMatchDetailClient`; Realtime emits `DetailEvent` on create/update. Both clients use `appwriteAiCacheDbId` as their DB ID.

**`match-scorecard` table:** written by `bouncer-match-sync` alongside the detail row. One row per match, row ID = sanitized `matchId`, columns `match_id`, `scorecard` (JSON `Scorecard`), `updated_at`. **No index** — read by `AppwriteMatchScorecardClient.getForMatch` (one `getRow` for first paint) then kept fresh by `observeMatch`, subscribed for the life of the detail screen. Exists because the scorecard measured 85–93% of the detail payload (4–9 KB of 4–9 KB) and was pushed to every subscriber of that row every minute; on its own row the live card's ~650-byte updates travel alone.

**`bouncer-match-sync` Appwrite function:** scheduled `* * * * *`. Reads `live-matches.json` from `cricket-stats` bucket (written by `bouncer-poll-live-matches`); if file absent, returns 200 `{skipped:true}` (not an error). For each live match: fetches Cricbuzz live HTML + scorecard → upserts `match-live-detail` → generates AI commentary lines → writes to `match-commentary`. Up to 10 new rows/match/run, 12 matches max. Env vars: `APPWRITE_AI_CACHE_DB_ID`, `MATCH_COMMENTARY_COL_ID`, `MATCH_LIVE_DETAIL_COL_ID`, `MATCH_CACHE_BUCKET_ID`, `APPWRITE_API_KEY`, `GROQ_API_KEY` (+ fallbacks), `SENTRY_DSN` (optional — unset disables server-side reporting), `PROBE_SOURCES` (optional).

**Server-side observability (`match-sync/src/observability.js`):** `@sentry/node`, initialized per container from `SENTRY_DSN` and flushed before every return (the container freezes on return, so an unflushed queue is lost). Reports: raw-text passthrough with the per-provider reasons from `generateLine`'s `attempts`, commentary write failures, a viewer-gated match that produced nothing, an unreadable `active-viewers` file (a 404 is normal and stays quiet), and read-budget 402s. Setting `PROBE_SOURCES=1` turns one execution into an upstream reachability probe (status + bytes + markup markers for Cricinfo and Cricbuzz, from inside the Appwrite container) and returns without syncing — that is the only way to answer whether Cricinfo is reachable server-side, which is a different question from whether it is reachable from a laptop or a phone.

**Viewer gate:** `MatchDetailViewModel` pings `bouncer-match-presence` every 90s while a match is open and **not over** — gated on `isMatchOver(match, liveStatus)`, never on `match.isLive`, which is a frozen list snapshot (same staleness class as `5b86664`). A dead heartbeat means every sync logs "0 with viewers", only the 45-minute slow tier writes, and the tab falls back to raw Cricbuzz text; `MatchPresenceReporter.ping` therefore reports failures to Sentry instead of swallowing them.

**Appwrite Storage bucket `cricket-stats`:** read-only from Android via `AppwriteClient.downloadFile(bucketId, fileId)`. Three files: `team-stats` (win-rate model written by `train-win-probability`), `h2h-stats` (H2H stats for 650+ team pairs, written by same function), `active-series` (series list, read by `SeriesSyncWorker`). Bucket is written only by Appwrite cloud functions.

**Room DB:** currently at schema version 11. New migrations: add `MIGRATION_N_(N+1)` in `BouncerDatabase.kt` + bump `version` in `@Database`.

**Appwrite MCP workflow:** `appwrite_search_tools` → `appwrite_call_tool` → `ReadMcpResourceTool` (server: `plugin:appwrite:appwrite-api`, URI from truncated result) for full output. Column creation returns `status: "processing"` — call `tables_db_list_columns` to confirm `"available"` before using. Table-level permissions at creation: `["read(\"any\")", "create(\"any\")", "update(\"any\")"]`.

**New Appwrite client pattern:** `object` singleton extending `AppwriteTableClient(tag)`. The base provides `initTable()`, `isReady`, the billed-operation funnels `guardedRead()`/`guardedWrite()`, the refcounted Realtime `acquireChannel(channel) { … }` + reconnect backoff, `stop()`/`stopAll()`, and `logSilentFailure()` — don't reimplement these, and never construct a raw `TablesDB(client)`. Subclass adds `initialize()` (calls `initTable(dbId, tableId)`) and its table ops as `runCatching` + `logSilentFailure` on failure. Register in `BouncerApp.kt` via `initializeSafely("Name") { Client.initialize() }`. Exception: `AppwriteFavouritesSync` is in `data/remote/` but is **not** registered in `BouncerApp` via `initializeSafely` — do not assume it follows the standard init pattern.

**API keys:** Always access via `ApiKeys` object (`ApiKeys.kt`), never `BuildConfig` directly. Add new keys to `ApiKeys.kt` + `build.gradle.kts` + `local.properties.template`.

**Background workers:** `ScorePollingWorker`, `SeriesSyncWorker`, `PlayerCacheWorker`, `DatabaseCleanupWorker` (weekly cleanup, WorkManager), `WidgetUpdateWorker` (Glance).

## Key Dependencies

- **Jetpack Compose + Navigation**: UI framework + in-app routing
- **Glance**: Home-screen app widgets
- **Sentry**: Error monitoring + performance (configured in `SentryOptionsConfig.kt`)
- **Firebase Cloud Messaging (FCM)**: Push notifications
- **Room + Paging 3**: Offline cache + paginated queries
- **Retrofit + OkHttp**: REST APIs; **Jsoup**: HTML scraping for Cricbuzz
- **Kotlin Symbol Processing (KSP)**: Code generation for Room
- **Android Auto**: `BouncerCarAppService` for car display
- **Appwrite Android SDK** (`io.appwrite:sdk-for-android`): Realtime subscriptions, TablesDB sync — endpoint `https://sgp.cloud.appwrite.io/v1`, project `6a02f7f3000e6e100b19`

## Common Tasks & Skills

| Task | Skill(s) |
|------|----------|
| Add feature | `superpowers:brainstorming` → `superpowers:writing-plans` → `superpowers:executing-plans` + `feature-dev:feature-dev` |
| Fix bug | `superpowers:systematic-debugging` + `sentry:seer` (if Sentry involved) |
| Code review | `code-review:code-review` |
| Refactor | `code-simplifier` |
| Git operations | `commit-commands:commit` or `commit-commands:commit-push-pr` |
| Android testing | `android-dev MCP`: screenshots, UI dump, tap, swipe, logcat |
| Sentry issues | `sentry:sentry-workflow` → `sentry:seer` |
| Before shipping | `superpowers:verification-before-completion` → `superpowers:finishing-a-development-branch` |

## Code Style & Conventions

- **Kotlin**: when/is, sealed classes, extension functions
- **Compose**: Stateless components, modifiers for layout, remember/derivedStateOf for state
- **Naming**: camelCase for functions/variables, PascalCase for types
- **Comments**: Only for non-obvious logic (WHY, not WHAT)
- **V2 naming**: Active screens/components carry a `V2` suffix (`HomeScreenV2`, `MatchDetailViewV2`, `components/v2/`). Legacy `V1` files were deleted in the June 2026 dead-code purge — do not recreate them.
- **Dead-code caution**: `models/**` and `network/*Dto` are proguard-kept for Gson reflection; manifest-registered classes (`AndroidManifest.xml` `android:name`) and WorkManager `CoroutineWorker` subclasses are reflection roots. Never delete any of these on grep evidence alone.

---

## Detailed Specs

Detailed configuration documented in `.claude/rules/`:

- **[build-configuration.md](./.claude/rules/build-configuration.md)** — iCloud workarounds, Gradle tuning, NDK config
- **[sentry-configuration.md](./.claude/rules/sentry-configuration.md)** — Plugin settings, runtime init, breadcrumbs
- **[api-keys-and-setup.md](./.claude/rules/api-keys-and-setup.md)** — Key loading mechanism, setup options
- **[testing-patterns.md](./.claude/rules/testing-patterns.md)** — Test commands, organization by category
- **[initialization-and-patterns.md](./.claude/rules/initialization-and-patterns.md)** — Startup sequence, core patterns, failure isolation
- **[appwrite-patterns.md](./.claude/rules/appwrite-patterns.md)** — **Read before adding any Appwrite call.** Guard funnels, 402-vs-429, per-document read billing, subscription lifecycles, single-flight caching

## Context-Mode Tools

**Gradle/build commands must use `ctx_execute(language: "shell", ...)` — a session hook blocks Bash for commands producing >20 lines output (including all `./gradlew` invocations).**

For large data processing, log analysis, or API exploration:
- `ctx_execute`: Run code or shell commands, output stays in sandbox
- `ctx_execute_file`: Analyze large logs/data files
- `ctx_batch_execute`: Multiple commands in one call (faster)
- `ctx_search`: Query indexed content

## Git & Deploy
- "push" = `git push` only. Never open a PR unless explicitly asked.
- When git commands hang in this sandbox, fall back to the GitHub API rather than retrying indefinitely.
- Before force-pushing, double-check the commit SHA you're pushing matches the one containing the intended fix.
- After deploys, verify the change is actually live (hardcoded dates, cache, hydration) before declaring done.
