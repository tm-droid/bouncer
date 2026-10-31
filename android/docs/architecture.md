# Bouncer architecture & specs

Moved out of CLAUDE.md on 2026-10-23 to keep per-turn context small.

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

**Appwrite cloud functions** live at the monorepo root in `appwrite/functions/` (Node.js; from this Android directory run `node ../appwrite/tools/deploy-function.mjs`). `appwrite/appwrite.config.json` defines project-level config. Details: `.claude/rules/appwrite-functions.md`.
- `match-sync` — every minute; polls the match lists (`src/poll.js`), then live detail, scorecard, commentary, win probability and alerts
- `api` (`bouncer-api`) — everything else that must stay on Appwrite, because the Free plan allows **two functions**. On-demand routes by execution path: `/news` (Cricbuzz news via RapidAPI into `cricket-stats`), `/player-search` (player name → Cricbuzz profile id), `/presence` (the server half of the viewer gate). Its `*/15` schedule runs one job per slot: highlights sync (first tick of each hour; Highlightly quota), the commentary prune (offered every hour's third tick, but it acts only in a billing cycle's last 12 hours, spending 90% of the writes left on rows the archive already holds) and the player-index rebuild (04:15 every third day). The device names a route as `bouncer-api/<route>` (`AppwriteClient.executeFunctionDetailed`)
- `appwrite/tools/history` — not a function: builds the historical-match site (every Cricsheet match, ball-by-ball, plus records) that `workflow/build.yml` publishes weekly, running inside to the public repo `tm-droid/bouncer-history` on GitHub Pages. Off Appwrite because Free cannot hold or serve it
- `appwrite/tools/archive` — not a function: the forever match archive. Every match Bouncer follows (commentary rows, scorecard, detail, `mc-` context, `wpe-` log) is appended to the public repo `tm-droid/bouncer-archive` on GitHub Pages before Appwrite's 14-day prune. It runs every 3 hours in that repo (free Actions minutes); `publish-archiver.yml` copies it there
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

**Public client configuration:** Access through `ApiKeys` (`ApiKeys.kt`), never `BuildConfig` directly. Defaults come from repo-root `shared/backend.json`. Provider credentials and server API keys stay in the function environment; no credential is added to a native build constant.

**Appwrite Free plan (from November 2026).** 250k DB writes, 500k reads, 5 GB bandwidth, 2 GB storage *including function deployments and builds*, two functions, 512 MB per function, and a project is **paused after 7 days with no Console activity** (app traffic does not count; `appwrite/tools/archive/workflow/appwrite-alive.yml`, run in the public `bouncer-archive` repo, is the alarm). `deploy-function.mjs` deletes old deployments after a verified deploy for the storage cap.

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

## Detailed Specs

The detail lives in `.claude/rules/`. Each file loads by itself when a file under its paths is read. **When a task starts in one of these areas without reading such a file, read the rule file first** — these hold measured limits, rejected approaches and traps that are not derivable from the code.

| Working on | Read |
|---|---|
| Any Appwrite call, guard funnels, 402 vs 429, Realtime lifetimes | `appwrite-patterns.md` |
| An Appwrite function (schedules, deploy, per-function traps) | `appwrite-functions.md` |
| `match-sync` commentary: batching, checker, backlog, provider ladder and quotas, Cricinfo, slow tier | `match-sync-commentary.md` |
| Win probability: v6 model, context files, eval logs, orientation | `win-probability.md` |
| Tables, the `cricket-stats` bucket, Room, R8 keeps | `data-and-storage.md` |
| Historical matches and records (Cricsheet site on GitHub Pages, its builder and workflow) | `history-data.md` |
| The forever archive of Bouncer's own matches (`bouncer-archive`, its archiver and workflows) | `archive-data.md` |
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
