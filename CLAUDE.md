# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Bug Fixing Philosophy
- NEVER suppress, filter, or hide errors (e.g., Sentry filters, try/catch swallowing) without first identifying and fixing the root cause.
- When a bug is reported across multiple items (chapters, volumes, files), search for ALL occurrences before claiming a fix is complete.

## Environment & Secrets
- Before assuming an API/env var is configured, verify it explicitly (check `.env`, deployment config, or ask). Do not assume RAPIDAPI_KEY, Appwrite keys, etc. are set.
- For Appwrite changes, prefer the MCP server over CLI push (CLI has caused domain rule conflicts and column schema deletion from config drift).

---

# Bouncer Android App

Cricket live-scoring, match analytics, and team management app built in Kotlin/Compose.

## Quick Start

**Setup (first time):**
```bash
cp local.properties.template local.properties  # Fill in secret keys (RAPIDAPI_KEY, GROQ_API_KEY, etc.)
# Most non-secret Appwrite IDs are pre-filled — only secret API keys need manual entry.
# APPWRITE_PLAYER_FORMS_COL_ID is intentionally blank: that collection doesn't exist yet.
```

**Build & run:**
```bash
./gradlew build                  # Full build
./gradlew installDebug           # Install to emulator/device
./gradlew assemble               # Dev build (faster, skips Sentry transforms)
./gradlew test                   # Run all unit tests
./gradlew test --tests "*MatchUtilsTest"  # Run single test
./gradlew clean build            # Clean build (if cache corrupted)
./gradlew detekt                  # Static analysis
./gradlew assembleDebug -PenableComposeCompilerReports  # Compose metrics
```

## Architecture

```
app/src/main/kotlin/com/tanush/bouncer/
├── BouncerApp.kt       # App entry + startup sequence
├── ApiKeys.kt          # Single source for all API key access — use this, not BuildConfig directly
├── ui/
│   ├── screens/        # Full-page Compose screens
│   ├── components/     # Reusable composables
│   ├── widgets/        # Glance home-screen widgets
│   ├── navigation/     # BouncerNavHost.kt — single NavHost, all routes
│   ├── theme/          # Material3 theme
│   └── adaptive/       # Window-size-class layouts
├── viewmodels/         # MVVM ViewModels (no Hilt — manual construction)
├── services/           # FCM, CommentaryFusion, LiveCommentary, WebSocket, Workers, AICacheService
├── managers/           # Singleton managers (TeamLogo, Acronym, Favourites, MatchSubscription, NotificationInbox)
├── network/            # Retrofit + Jsoup scrapers (CricBuzz, CricInfo, Series, Squad)
├── data/               # Room DB, repositories, AppResult<T> monad
│   └── remote/         # Appwrite clients: AppwriteClient (singleton), AppwriteAICache,
│                       # AppwriteHighlightsClient, AppwriteFavouritesSync,
│                       # AppwritePlayerFormsClient, AppwritePlayerProfilesClient
├── models/             # Data models and entities
├── analysis/           # Cricket analysis + ML models (score prediction, win probability)
├── auto/               # Android Auto (BouncerCarAppService)
├── mcp/                # Model Context Protocol handlers
├── utils/              # Utility functions
├── monitoring/         # Sentry + SentryAiSpans
└── logic/              # Match simulation + game logic
```

**Appwrite cloud functions** live in `appwrite/functions/` (Node.js, deployed via Appwrite CLI). `appwrite/appwrite.config.json` defines project-level config. The `highlights-sync` function runs on a 15-min schedule; endpoint/projectId are auto-injected by the runtime — only `APPWRITE_API_KEY` and DB/collection IDs need manual env vars.

**No dependency injection framework.** ViewModels are constructed manually; managers use `object` singletons initialized in `BouncerApp.onCreate`.

**Error handling:** `AppResult<T>` monad (`data/AppResult.kt`) wraps success/failure. Use `.fold(onSuccess, onFailure)` in ViewModels; propagate via `return AppResult.Error(e)` in repositories. Don't unwrap with `!!`.

**Navigation:** Single `BouncerNavHost` with Compose Navigation. Deep link entry via `bouncer://match/{matchId}`.

**Live data flow:** `LiveScoreWebSocketClient` → `LiveCommentaryManager` → `CommentaryFusionService` (OpenRouter, multi-model fallback) + `BallCommentaryService` (Groq → Cerebras → SambaNova per-ball) → UI via StateFlow. `AICacheService` warms cache from Appwrite Realtime events.

**AI cache (3-tier):** Memory LRU (256 entries) → Room `ai_commentary_cache` → Appwrite `ai-commentary-cache` table. All four commentary functions (`generateBallCommentary`, `fuse`, `streamCommentary`, `summarizeOversWithAI`) check cache before calling any AI provider. Completed matches use **permanent TTL** (`Long.MAX_VALUE` in Room, `expires_at = "2099-01-01T00:00:00.000Z"` in Appwrite) — `isMatchComplete` flag threads from ViewModels → `LiveCommentaryManager` → `CommentaryFusionService` → `AICacheService`. Never set permanent=true for live matches.

**Player profile cache (global):** `PlayerCacheWorker` checks `AppwritePlayerProfilesClient` before scraping Cricbuzz. First device that scrapes a player pushes to Appwrite (`player-profiles` table, `bouncer-db`); all subsequent devices get a cache hit. Row ID = `entity.playerId` (Room format: `virat_kohli`).

**Appwrite project:** single database `bouncer-db`. Known tables: `ai-commentary-cache`, `highlights`, `highlights-meta`, `user-favourites`, `player-profiles`.

**Appwrite MCP workflow:** `appwrite_search_tools` → `appwrite_call_tool` → `ReadMcpResourceTool` (server: `plugin:appwrite:appwrite-api`, URI from truncated result) for full output. Column creation returns `status: "processing"` — call `tables_db_list_columns` to confirm `"available"` before using. Table-level permissions at creation: `["read(\"any\")", "create(\"any\")", "update(\"any\")"]`.

**New Appwrite client pattern:** `object` singleton, `initialize()` sets `dbId`/`tableId`/`isReady`, `tablesDB` computed property returns fresh `TablesDB(AppwriteClient.client ?: return null)`, all ops `runCatching` + silent log on failure. Register in `BouncerApp.kt` via `initializeSafely("Name") { Client.initialize() }`.

**API keys:** Always access via `ApiKeys` object (`ApiKeys.kt`), never `BuildConfig` directly. Add new keys to `ApiKeys.kt` + `build.gradle.kts` + `local.properties.template`.

**Background workers:** `ScorePollingWorker`, `SeriesSyncWorker`, `PlayerCacheWorker` (WorkManager), `WidgetUpdateWorker` (Glance).

## Key Dependencies

- **Jetpack Compose + Navigation**: UI framework + in-app routing
- **Glance**: Home-screen app widgets
- **Sentry**: Error monitoring + performance (AI spans via `SentryAiSpans`)
- **Firebase Cloud Messaging (FCM)**: Push notifications
- **Room + Paging 3**: Offline cache + paginated queries
- **Retrofit + OkHttp**: REST APIs; **Jsoup**: HTML scraping for Cricbuzz
- **Ktor CIO**: WebSocket client for live score stream
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
- **V2 screens**: Most screens have `V1` (legacy) and `V2` (active) variants. `V2` is the current implementation; `V1` files are kept but unused.

---

## Detailed Specs

Detailed configuration documented in `.claude/rules/`:

- **[build-configuration.md](./.claude/rules/build-configuration.md)** — iCloud workarounds, Gradle tuning, NDK config
- **[sentry-configuration.md](./.claude/rules/sentry-configuration.md)** — Plugin settings, runtime init, breadcrumbs
- **[api-keys-and-setup.md](./.claude/rules/api-keys-and-setup.md)** — Key loading mechanism, setup options
- **[testing-patterns.md](./.claude/rules/testing-patterns.md)** — Test commands, organization by category
- **[initialization-and-patterns.md](./.claude/rules/initialization-and-patterns.md)** — Startup sequence, core patterns, failure isolation

## Context-Mode Tools

**Gradle/build commands must use `ctx_execute(language: "shell", ...)` — a session hook blocks Bash for commands producing >20 lines output (including all `./gradlew` invocations).**

For large data processing, log analysis, or API exploration:
- `ctx_execute`: Run code or shell commands, output stays in sandbox
- `ctx_execute_file`: Analyze large logs/data files
- `ctx_batch_execute`: Multiple commands in one call (faster)
- `ctx_search`: Query indexed content

## Git & Deploy
- When git commands hang in this sandbox, fall back to the GitHub API rather than retrying indefinitely.
- Before force-pushing, double-check the commit SHA you're pushing matches the one containing the intended fix.
- After deploys, verify the change is actually live (hardcoded dates, cache, hydration) before declaring done.
