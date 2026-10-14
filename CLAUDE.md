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
- Always run a clean build and tests before pushing; do not push a broken build. CI gates the Play upload on unit tests, lint, detekt and every function's `node --test` (`ci.yml`, `needs:` on the build job), so run `./gradlew lintDebug` as well as detekt before pushing — lint was red on main for weeks while it gated nothing.
- A push that changes no app path (`app/`, `gradle/`, `distribution/`, the Gradle files) builds but does not upload to Play. A push to main never cancels an earlier run.
- Every push to main uploads to Play with `distribution/whatsnew/whatsnew-en-US` as "What's new". A commit touching `app/src/main/` must update that file (user-facing wording, ≤500 chars — see `distribution/whatsnew/README.md`). `.claude/hooks/release-notes-check.sh` blocks the commit otherwise; prefix `SKIP_RELEASE_NOTES=1` only when nothing user-visible changed.
- **Retiring old builds.** A push with a `Force-Update: yes` commit trailer uploads at in-app update priority 5, and every Play install then shows Play's immediate update (`AppUpdateGate`). For installs Play cannot reach (sideloaded APKs, a stale Play cache), `node appwrite/tools/set-min-version.mjs <versionCode>` publishes a minimum that blocks older builds behind an "Update Bouncer" screen. Publish it only once that build has cleared closed-testing review. Builds below 1000 (local) are never gated.
- The Play bundle's Appwrite ids and Sentry DSN come from `local.properties.template`, not from GitHub secrets; `verifyReleaseConfig` fails a release artifact built without them. A green CI run does not mean the bundle can reach the backend — see "The Play build takes its ids from the template" in `.claude/rules/api-keys-and-setup.md`.

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

**Single kotlin source tree.** `app/src/main/kotlin/` (~302 files) is the only main source tree — the former `app/src/main/java/` was merged into it in June 2026. `BouncerActivity`, `LiveActivityService`, `ui/components/glass/`, and `ui/components/premium/` all live in the kotlin tree. (`app/src/androidTest/java` and `app/src/test/java` remain for test code only.)

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
├── services/           # FCM, match alert gate, presence, match cache, Workers, AICacheService (dormant)
│                       # RetryPolicy.kt: WORKER_MAX_RETRIES — shared retry cap for all Workers
├── managers/           # Singleton managers (TeamLogo, TeamAcronym, FavouriteTeams, MatchSubscription, NotificationInbox)
├── network/            # OkHttp + Jsoup scrapers (CricBuzz, CricInfo, Series, PointsTable, PlayerProfile)
├── data/               # Room DB, repositories, AppResult<T> monad
│   ├── CacheConfig.kt  # All cache TTLs + realtime reconnect backoff + permanent-cache sentinel — no magic numbers
│   └── remote/         # AppwriteClient (singleton) + table clients extending AppwriteTableClient
│                       # AppwriteAICache, AppwriteHighlightsClient, AppwritePlayerProfilesClient,
│                       # H2HRepository,
│                       # AppwriteMatchCommentaryClient (match-commentary table),
│                       # AppwriteMatchDetailClient (match-live-detail table),
│                       # AppwriteMatchScorecardClient (match-scorecard table, never subscribed)
│                       # AppwriteReadGuard (per-quota breaker), AppwriteUsageTracker (Sentry gauges),
│                       # RealtimeLifecycleController (background pause) — see appwrite-patterns.md
├── models/             # Data models and entities (@Serializable; see dead-code caution below)
├── analysis/           # Pure on-device match math only: CricketConstants (format ball counts,
│                       # magic numbers) + MatchProjections (run rate, projected score, required
│                       # rate). The ML models moved server-side — score prediction and win
│                       # probability are computed by `bouncer-match-sync`, on the model the weekly
│                       # `train-win-probability` workflow fits. Do not add a model here.
├── liveactivity/       # Android Live Updates: LiveUpdateContent/Progress/Score (pure, tested),
│                       # LiveUpdateNotifier (the promoted notification), LiveUpdateSources
├── auto/               # Android Auto (BouncerCarAppService)
└── utils/              # WorkerLogger.enqueued/failed (Workers) + sentryBreadcrumb() (SentryBreadcrumbs.kt)
                        # — use these instead of raw Breadcrumb().apply boilerplate
```

**Appwrite cloud functions** live in `appwrite/functions/` (Node.js; deploy with `node appwrite/tools/deploy-function.mjs`). `appwrite/appwrite.config.json` defines project-level config. Details: `.claude/rules/appwrite-functions.md`.
- `match-sync` — every minute; polls the match lists (`src/poll.js`), then live detail, scorecard, commentary, win probability and alerts
- `api` (`bouncer-api`) — everything else that must stay on Appwrite, because the Free plan allows **two functions**. On-demand routes by execution path: `/news` (Cricbuzz news via RapidAPI into `cricket-stats`), `/player-search` (player name → Cricbuzz profile id), `/presence` (the server half of the viewer gate). Its `*/15` schedule runs one job per slot: highlights sync (first tick of each hour; Highlightly quota), the commentary prune (03:15 daily, rows older than 14 days) and the player-index rebuild (04:15 every third day). The device names a route as `bouncer-api/<route>` (`AppwriteClient.executeFunctionDetailed`)
- `train-win-probability` — the v6 refit. Runs weekly in GitHub Actions (`.github/workflows/train-win-probability.yml`), not on Appwrite: it needs ~760 MB and Free fixes functions at 512 MB

Endpoint/projectId are auto-injected by the runtime — only `APPWRITE_API_KEY` and DB/collection IDs need manual env vars.

**No dependency injection framework.** ViewModels are constructed manually; managers use `object` singletons initialized in `BouncerApp.onCreate`.

**Error handling:** `AppResult<T>` monad (`data/AppResult.kt`) wraps success/failure. Use `.fold(onSuccess, onFailure)` in ViewModels; propagate via `return AppResult.Error(e)` in repositories. Don't unwrap with `!!`.

**Navigation:** Single `BouncerNavHost` with Compose Navigation. Deep link entry via `app://bouncer/match/{matchId}` — scheme `app`, host `bouncer`, as registered in `AndroidManifest.xml` and parsed by `handleExternalDeepLink` in `ui/MainAppContainer.kt`. A `bouncer://` URI does not resolve.

**Live data flow:** score polling (`ScorePollingWorker` + FCM, `LiveActivityViewModel` on the Live tab) → UI via StateFlow. Commentary is written server-side by `bouncer-match-sync` to `match-commentary` and read through `AppwriteMatchCommentaryClient` (match detail); no commentary is generated on the device. `AICacheService` warms cache from Appwrite Realtime events.

**Cross-language rules are pinned, not commented.** `ContextMathVendorParityTest` (contextmath.js, match-sync and the trainer),
`NextFlightVendorParityTest` (nextflight.js),
`SeriesClassificationParityTest` (`SeriesClassification.kt` ↔ `series.js`),
`SearchNormalizationParityTest` (`normalizeForSearch` ↔ `player-search`'s, via a shared fixture) and
`MatchStatusParityTest` (all three classifier lists, Kotlin ↔ `matchstate.js`, plus `cricbuzz.js`'s
copy of the terminal one). Every one of those files is declared via `inputs.files(...)` in
`app/build.gradle.kts`; undeclared, an edit outside the module leaves the test task UP-TO-DATE and
the check never runs. Prove a new guard bites by mutating one rule and watching the build fail.

**Appwrite project:** single database `bouncer-db`. Known tables: `ai-commentary-cache`, `highlights`, `highlights-meta`, `user-favourites`, `player-profiles`, `match-commentary`, `match-live-detail`, `match-scorecard`.

**Appwrite MCP workflow:** `appwrite_search_tools` → `appwrite_call_tool` → `ReadMcpResourceTool` (server: `plugin:appwrite:appwrite-api`, URI from truncated result) for full output. Column creation returns `status: "processing"` — call `tables_db_list_columns` to confirm `"available"` before using. Table-level permissions at creation: `["read(\"any\")", "create(\"any\")", "update(\"any\")"]`.

**New Appwrite client pattern:** `object` singleton extending `AppwriteTableClient(tag)`. The base provides `initTable()`, `isReady`, the billed-operation funnels `guardedRead()`/`guardedWrite()`, the refcounted Realtime `acquireChannel(channel) { … }` + reconnect backoff, `stop()`/`stopAll()`, and `logSilentFailure()` — don't reimplement these, and never construct a raw `TablesDB(client)`. Subclass adds `initialize()` (calls `initTable(dbId, tableId)`) and its table ops as `runCatching` + `logSilentFailure` on failure. Register in `BouncerApp.kt` via `initializeSafely("Name") { Client.initialize() }`.

**API keys:** Always access via `ApiKeys` object (`ApiKeys.kt`), never `BuildConfig` directly. Add new keys to `ApiKeys.kt` + `build.gradle.kts` + `local.properties.template`.

**Appwrite Free plan (from November 2026).** 250k DB writes, 500k reads, 5 GB bandwidth, 2 GB storage *including function deployments and builds*, two functions, 512 MB per function, and a project is **paused after 7 days with no Console activity** (app traffic does not count; `.github/workflows/appwrite-alive.yml` is the alarm). `deploy-function.mjs` deletes old deployments after a verified deploy for the storage cap.

**Background workers:** `ScorePollingWorker`, `SeriesSyncWorker`, `PlayerCacheWorker`, `DatabaseCleanupWorker` (weekly cleanup, WorkManager), `WidgetUpdateWorker` (Glance).

## Key Dependencies

- **Jetpack Compose + Navigation**: UI framework + in-app routing
- **Glance**: Home-screen app widgets
- **Sentry**: Error monitoring + performance (configured in `SentryOptionsConfig.kt`)
- **Firebase Cloud Messaging (FCM)**: Push notifications
- **Room + Paging 3**: Offline cache + paginated queries
- **OkHttp**: every HTTP call (there is no Retrofit); **Jsoup**: HTML scraping for Cricbuzz
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
- **Dead-code caution**: manifest-registered classes (`AndroidManifest.xml` `android:name`) and WorkManager `CoroutineWorker` subclasses are reflection roots — never delete these on grep evidence alone. `models/**` and `network/*Dto` used to be proguard-kept for Gson reflection; that keep is **gone** (serialization is kotlinx, whose serializers are generated at compile time), along with the blanket `-keep class com.tanush.bouncer.** { *; }` that made R8 a no-op app-wide. Do not reintroduce either. The sound evidence for what is unreachable is now R8's `usage.txt` from a release build (`~/Library/Caches/bouncer-android/app/outputs/mapping/release/usage.txt`), not grep.

---

## Detailed Specs

The detail lives in `.claude/rules/`. Each file loads by itself when a file under its paths is read. **When a task starts in one of these areas without reading such a file, read the rule file first** — these hold measured limits, rejected approaches and traps that are not derivable from the code.

| Working on | Read |
|---|---|
| Any Appwrite call, guard funnels, 402 vs 429, Realtime lifetimes | `appwrite-patterns.md` |
| An Appwrite function (schedules, deploy, per-function traps) | `appwrite-functions.md` |
| `match-sync` commentary: batching, checker, backlog, provider ladder and quotas, Cricinfo, slow tier | `match-sync-commentary.md` |
| Win probability: v6 model, context files, eval logs, orientation | `win-probability.md` |
| Tables, the `cricket-stats` bucket, Room, R8 keeps | `data-and-storage.md` |
| Cricbuzz parsers and page shapes | `cricbuzz-scraping.md` |
| Match detail screen, commentary tab, viewer gate, highlights player | `match-detail-and-commentary-ui.md` |
| Home, navigation, deep links, Series, News, Search | `home-nav-search.md` |
| Live Update notification cards, FCM match alerts, reminders | `live-updates-and-alerts.md` |
| Widgets | `widgets.md` |
| Any Compose UI, colour, type, light mode | `design-system.md` |
| Secrets, `local.properties`, function env vars | `api-keys-and-setup.md` |
| Gradle, NDK, baseline profile | `build-configuration.md` |
| Sentry plugin, filters | `sentry-configuration.md` |
| Tests | `testing-patterns.md` |
| Startup order | `initialization-and-patterns.md` |

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
