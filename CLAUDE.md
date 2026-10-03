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
- Every push to main uploads to Play with `distribution/whatsnew/whatsnew-en-US` as "What's new". A commit touching `app/src/main/` must update that file (user-facing wording, ≤500 chars — see `distribution/whatsnew/README.md`). `.claude/hooks/release-notes-check.sh` blocks the commit otherwise; prefix `SKIP_RELEASE_NOTES=1` only when nothing user-visible changed.
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

**Baseline profile:** `:baselineprofile` (Macrobenchmark) generates
`app/src/release/generated/baselineProfiles/`, which ships in the release bundle and is installed by
`profileinstaller`. Regenerate with a device or emulator attached (API 28+):
`./gradlew :app:generateReleaseBaselineProfile`; `connectedBenchmarkReleaseAndroidTest` runs
`StartupBenchmark` (no-compilation vs profile). The committed profile covers **cold start and
onboarding only**: on the `bouncer_test` emulator every UiAutomator tap on onboarding's Skip is
dropped (measured in the generator's logcat — clicked, never gone, three retries), so the Home and
match-detail journeys never ran. A physical phone is the way to extend it. The generator must also
keep the machine awake (`caffeinate -i`): a laptop sleeping mid-run leaves the test stuck at `0/3`
indefinitely. `nonMinifiedRelease`/`benchmarkRelease` are in Sentry's `ignoredVariants`.

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
├── services/           # FCM, CommentaryFusion, LiveCommentary, Workers, AICacheService
│                       # RetryPolicy.kt: WORKER_MAX_RETRIES — shared retry cap for all Workers
├── managers/           # Singleton managers (TeamLogo, TeamAcronym, FavouriteTeams, MatchSubscription, NotificationInbox)
├── network/            # Retrofit + Jsoup scrapers (CricBuzz, CricInfo, Series, PointsTable, PlayerProfile)
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
│                       # probability are the `bouncer-win-probability` /
│                       # `bouncer-train-win-probability` functions. Do not add a model here.
├── liveactivity/       # Android Live Updates: LiveUpdateContent/Progress/Score (pure, tested),
│                       # LiveUpdateNotifier (the promoted notification), LiveUpdateSources
├── auto/               # Android Auto (BouncerCarAppService)
└── utils/              # WorkerLogger.enqueued/failed (Workers) + sentryBreadcrumb() (SentryBreadcrumbs.kt)
                        # — use these instead of raw Breadcrumb().apply boilerplate
```

**Appwrite cloud functions** live in `appwrite/functions/` (Node.js, deployed via Appwrite CLI). `appwrite/appwrite.config.json` defines project-level config. Nine functions (the match-list poller, once a tenth, now runs inside `match-sync`):
- `highlights-sync` — 15-min schedule; syncs cricket highlight clips to Appwrite. **Only one run per hour does anything** (`minute < 15`), and only every 4th hour sweeps the 3-day back-window — Highlightly's plan allows 100 requests per rolling window and `*/15` alone would spend 96 on today. Never make it fetch on every run without raising that plan. Highlightly publishes a clip hours-to-days after its match, so a same-day-only query permanently loses whatever the lag exceeds; that is what the back-window exists for. Requires `HIGHLIGHTLY_API_KEY`, optional `SENTRY_DSN`. Deploy: the ~13 MB `--with-node-modules` archive is rejected whole by the CDN edge (`503 backend write error` in ~55 ms), so it must go up in 5 MB chunks — `node appwrite/tools/deploy-function.mjs` now does that itself and is the deploy path.
- `generate-commentary` — on-demand; LLM commentary via the shared groq/Gemini provider ladder in the vendored `llm.js`. It has never used OpenRouter. **It has no caller.** Its one device caller was `CommentaryFusionService`, a complete second commentary pipeline that no production code path ever constructed; that class was deleted, and the function stays deployed only because its `llm.js` is the vendored copy `LlmJsVendorParityTest` pins and because its provider probe is the one signal that reaches the providers on a day with no live match. Do not describe it as something the device calls.
- `match-sync` — every-minute schedule; fetches live match HTML + scorecard → upserts `match-live-detail` → AI commentary → `match-commentary` (see detailed section below)
- `news-sync` — on-demand; fetches cricket news from Cricbuzz via RapidAPI, caches to `cricket-stats` bucket (`news-cache` file, 4-hr TTL caps RapidAPI at ~6 calls/day). Requires `RAPIDAPI_KEY`.
- **match-list poll** (`match-sync/src/poll.js`, `runPoll`) — was the `bouncer-poll-live-matches` function, scheduled every minute on its own; merged into `match-sync` on 2026-09-30 and run at the **top of every match-sync run, ahead of every early return** (a latched read-budget 402 or a broken smoke probe must not stop the lists — the poll spends no DB reads). The live list is handed to match-sync in memory; a failed poll, or an empty live parse that kept the previous file, falls back to downloading `live-matches` as before. The old function is **disabled with its schedule cleared** on Appwrite, not deleted, and is gone from `appwrite.config.json`. It still **parses the Cricbuzz list pages itself** (`src/matchlist.js` over the flight payload, `src/matchstate.js` for which tab a match belongs on) and writes `live-matches`, `recent-matches` and `upcoming-matches`. It used to read `${VERCEL_API_BASE}/matches?type=…`, a separately-hosted scraper in another repository that answered `{id, title, status}` — the third independent parser of one fact, and the thinnest of the three fed everything server-side. That shape is why the widget split the title on " vs " to name the sides, why Android Auto showed no score, and why nothing downstream carried Cricbuzz's team ids. The summary now carries `state`, `matchFormat`, `seriesId`, `startDate` and a `teams` array (id, name, score, overs, isBatting); every added field has a default on the device so a file written by an older deployment still parses. **An empty parse never overwrites a good file** — same rule as the series walk. Also owns the `active-series` bucket file, which now holds a **superset of active plus archived** series: it walks `cricket-scorecard-archives/{year}` two pages per run, resuming from an `archiveCursor` stored in the file. Active-only meant a query for a competition out of season ("World Cup" for most of a four-year cycle) matched nothing anywhere in the app. A `CricbuzzBlockedError` aborts the walk and keeps the previous file — a truncated corpus must never overwrite a good one — and does not advance the cursor, so the next run retries that page.
- `player-search` — on-demand; resolves a player name to a **Cricbuzz** profile id for `PlayerProfileScraper.searchPlayersDetailed`. Cricbuzz has no working player search left (`/api/search/results` and `/search` both 404; `/api/html/search` is a 200-empty stub), so this builds a name→id index from Cricbuzz's own squad pages (`/cricket-team` → `/cricket-team/{slug}/{id}/players`, 38 pages, 593 players as measured on the first live build) and caches it in `cricket-stats` as `player-index` with a 7-day TTL. **The cached index is read back with `parseJsonFile`, never `Buffer.from`** — node-appwrite 15 hands back an already-parsed object for `application/json`, and the unconditional `Buffer.from` this shipped with threw into the "no cached index" catch, so every single query rebuilt the whole corpus: 39 Cricbuzz fetches and ~14s per search, TTL never once hit. Cricinfo's search does work but answers with Cricinfo ids, and `buildProfileUrl` builds Cricbuzz `/profiles/{id}/` URLs — switching id namespace would trade a dead search for a dead profile screen. Returns **503, never an empty 200**, when it has no index and cannot build one: absence and unavailability must stay distinguishable on the device. `PROBE_SOURCES=1` turns one execution into a reachability probe. Needs `timeout: 90` for the index build. **A search never rebuilds an index it can answer from** (`indexPlan`): the rebuild runs on the function's own schedule (`0 3 */3 * *`, detected by `x-appwrite-trigger: schedule`, a header Appwrite refuses from callers) and a search serves whatever index exists, however old. It used to rebuild inline in whichever search found the index past its 7-day TTL, and a build is ~15 s against the **10 s read timeout of the Android SDK's OkHttp client, which sets none of its own** — so that search drew "Online player search is unavailable" and reported nothing, because `SocketTimeoutException` is filtered as connectivity. That 10 s bounds every synchronous function call the app makes. Only a search that finds no index at all still builds inline. **A rebuild merges, it does not replace** (`mergePlayers`, the "merge, never shrink" rule `tools/update_logos.py` follows for teams): a squad page that failed used to take its whole team out of search until the next run, and a player dropped from a squad became unfindable although his profile page still exists. The walk itself stays a full one — Cricbuzz has no change feed, so finding what changed means reading every squad page.
- `match-presence` — on-demand; the server half of the viewer gate. `MatchPresenceReporter.ping` posts `{matchId}` every 90s from an open match screen, and `pingAll` posts `{matchIds: [...]}` (≤10) every 90s from `LiveUpdateService` for every followed match; the function read-modify-writes an `active-viewers` map (`{matchId: epochMs}`) in `cricket-stats` and prunes entries older than `PRESENCE_TTL_MS` (4 min). That TTL must stay **≥ match-sync's `VIEWER_TTL_MS`**, or a viewer match-sync still counts as active gets dropped here first. Concurrent pings race on the read-modify-write (last write wins, losing another match's fresh timestamp) — accepted, because the next 90s heartbeat heals it. A ping that finds its match stamped under 60s ago returns without writing (`FRESH_SKIP_MS`), so N viewers of one match cost ~1 write a minute, not N every 90s; staleness is unchanged because any ping finding a minute-old stamp still writes. Env: `APPWRITE_API_KEY`, `MATCH_CACHE_BUCKET_ID`.
- `match-commentary-prune` — daily at 03:00; deletes `match-commentary` rows older than `COMMENTARY_RETENTION_DAYS` (14). Nothing else deletes them, and the table grows one row per ball of every match — it had reached ~500k rows before this existed. Each pass is one **bulk** `deleteRows` filtered server-side by `lessThan("$createdAt", cutoff)` (built-in, no user index needed); it used to list 100 rows and delete them one call each, so every stale row cost a billed read first — ~6,700 reads a day, the project's largest read cost. A short batch ends the run. Bounded to `BATCH` 100 (the Free-plan bulk cap; asking for more fails the request) × `COMMENTARY_PRUNE_MAX_PASSES` 100 per run so one run stays inside the 300s timeout; raising `COMMENTARY_PRUNE_MAX_PASSES` temporarily drains a backlog faster. Needs node-appwrite ≥ 26 (bulk ops). A 402 aborts and returns 402 with the partial `deleted` count rather than failing silently.
- `win-probability` — on-demand; scores a position with the same `winprob.js` match-sync uses. No device caller.
- `train-win-probability` — Mondays 02:00; refits the win-probability model (v6) from Cricsheet and publishes the tables match-sync joins a live match against. See "Win probability is v6" below. ~280 s and ~760 MB of its 900 s / 1 GB.

Endpoint/projectId are auto-injected by the runtime — only `APPWRITE_API_KEY` and DB/collection IDs need manual env vars.

**No dependency injection framework.** ViewModels are constructed manually; managers use `object` singletons initialized in `BouncerApp.onCreate`.

**Error handling:** `AppResult<T>` monad (`data/AppResult.kt`) wraps success/failure. Use `.fold(onSuccess, onFailure)` in ViewModels; propagate via `return AppResult.Error(e)` in repositories. Don't unwrap with `!!`.

**Navigation:** Single `BouncerNavHost` with Compose Navigation. Deep link entry via `app://bouncer/match/{matchId}` — scheme `app`, host `bouncer`, as registered in `AndroidManifest.xml` and parsed by `handleExternalDeepLink` in `ui/MainAppContainer.kt`. A `bouncer://` URI does not resolve.

**The More tab is Settings, Appearance, About (and DevTools in debug), nothing else.** Its "Explore" grid was removed in September 2026 because none of its four tiles was a feature: Stats opened a hub whose Analytics page was entirely hardcoded (a fixed "V Kohli 9.4" leaderboard and an India-vs-Australia bar); Trophies and Achievements both opened one badge gallery where 3 of 24 badges could ever unlock; and Activity (`IntelligenceFeedScreenV2`) was navigated to with no match and no ViewModel, so it was always empty. That feed was the only caller of `LiveActivityViewModel.holdCommentary`, so the device-side commentary hold, `probabilityEvents`, `aiCommentary` and `keyEvent` went with it — all had been unreachable. The match-detail screen's own commentary subscription and presence ping (`MatchDetailViewModel`) are unaffected. Do not re-add a tile without a real data source behind it.

**Live data flow:** score polling (`ScorePollingWorker` + FCM, `LiveActivityViewModel` on the Live tab) → UI via StateFlow. Commentary is written server-side by `bouncer-match-sync` to `match-commentary` and read through `AppwriteMatchCommentaryClient` (match detail); no commentary is generated on the device. `AICacheService` warms cache from Appwrite Realtime events.

**Every `match-sync` row carries an `updated_at`, and the device reads it.** `getForMatch` and
`observeMatch` return `LiveDetailSnapshot`/`ScorecardSnapshot` — the payload plus the row's own
write time — because a row that froze when its match left `live-matches.json` and one written this
minute are otherwise identical field for field, which is what made the staleness in `eb91dcd`
invisible. `updatedAtMs` is null when the column is missing or unparseable, and **null means
unknown, never old**: a caller falls back to the rule it had before rather than treating the row as
stale, or one malformed timestamp blanks a live score. `ROW_STALE_MS`/`DETAIL_STALE_MS` are both
3 minutes — an open match-detail screen pings presence every 90s, which earns a detail-tier pass on
every one-minute run, so a row still being written is about a minute old at worst.

**The matches list outranks a row that stopped being written.** `MatchDetailViewModel.onMatchUpdated`
is called from composition on every list emission; `listLeadsDetail`
(`viewmodels/MatchDetailListSeed.kt`) decides, and it says yes in exactly three cases: before any
payload has arrived, when the list entry has gone terminal, and when the row is older than
`DETAIL_STALE_MS`. This is `eb91dcd` pointed the other way — there a frozen row overwrote the list
on a finished match; here a **live** match that `match-sync` is not writing at all (position 13+
under `MAX_MATCHES`, or one nobody is viewing) sat on its open-time score while the list
advanced behind it, because the state is seeded from the list at construction and so is never
blank, and `MatchDetailViewV2`'s `ifBlank` fallback could therefore never fire. `seededFrom` touches
only the fields the list actually carries: the batting pair, the bowler, the recent balls and the
scorecard exist only in the payload, and blanking them because the list is silent about them would
be the same bug again.

**The widgets have one snapshot and one writer.** `ui/widgets/WidgetSnapshot.kt` +
`WidgetSnapshotSource` build it from the scraped `CricketMatch` (both sides, acronyms, team ids) and
merge `match-live-detail` on top **by team id via `alignedTo`**, skipping a finished match and a row
that has gone quiet. `WidgetSnapshotStore` is the only thing that writes `widget_cache`,
`bouncer_glance_widget` or `bouncer_lock_widget`, projecting the snapshot onto the legacy keys each
renderer already reads. What this replaced: `ScorePollingWorker` wrote the payload's `score` — the
innings *being batted* — into `team1_score` whichever side was batting, **never wrote
`team2_score` at all**, named the sides by splitting a free-text title, and hardcoded
`is_live = true`; `LiveMatchWidgetProvider.updateCache` wrote both sides properly but its only
caller was `WidgetManager.updateAllWidgets`, whose only call site passed `match = null` — the
clear-everything branch. So every `score_update` FCM push **blanked all three stores** and threw
away the score it was carrying. That push now goes through `applyPushTo`, which updates the side it
names and returns null (change nothing) when it names neither: a number against the wrong team is
worse than one a minute old. `WidgetManager` is deleted. Pinned by `WidgetSnapshotTest`.

**A Live Update is a promoted notification, and `RemoteViews` disqualify it.** The home-screen live
score is an Android Live Update — an ongoing notification the system pins to the top of the shade,
to the lock screen, and to a status-bar chip. The contract is narrow and every clause is
load-bearing: the style must be Standard / `BigTextStyle` / `CallStyle` / `ProgressStyle` /
`MetricStyle`; the manifest must declare `POST_PROMOTED_NOTIFICATIONS`; the notification must be
`ongoing` and call `setRequestPromotedOngoing(true)`; and it must have **no** `setCustomContentView`
and **not** be a group summary. The stack this replaced broke all three ways at once and reported
none of them — it was built with `DecoratedCustomViewStyle` plus two `RemoteViews` (so promotion
could never be granted), its `setRequestPromotedOngoing` was a reflective call into
`NotificationCompat.Builder` with a silent catch, and its service was constructed with a
`NoopMatchUpdateDataSource` that nothing ever started. `LiveUpdateNotifier` uses
`NotificationCompat.BigTextStyle`; core-ktx 1.19 carries every promotion API, so there is **no
reflection and no `@RequiresApi`** — below API 36 the same builder degrades to an ordinary ongoing
notification. Two API traps: `Notification.isRequestPromotedOngoing()` does **not** exist on stock
API 36 even though the setter does (read `EXTRA_REQUEST_PROMOTED_ONGOING` from `extras` instead),
and the settings intent is `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`, not the
`ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` the developer guide names — **and that action resolves
to nothing on a stock API 36 build** (the Android 16 emulator has neither the screen nor the
feature: `canPostPromotedNotifications()` returns false there), so it must be `resolveActivity`-
checked before `startActivity` or it is an `ActivityNotFoundException`. Promotion being unavailable
is a normal device state, not an error: the app still posts an ordinary ongoing notification and
the settings row says so. Pinned by
`LiveUpdatePromotabilityTest` (**androidTest**, needs an API 36+ device), which asserts
`Notification.hasPromotableCharacteristics()` — the platform's own verdict, and the only check that
would have caught any of the above. It cannot be a unit test: there is no Robolectric here and the
promotion rules live in the framework, not in `NotificationCompat`.

**The Live Update feed is Realtime, and the foreground service exists only to receive it.**
`LiveUpdateService` subscribes `AppwriteMatchDetailClient.observeMatch` per followed live match —
the row `bouncer-match-sync` writes every minute **because the service pings presence for every
followed match** (`presenceHeartbeat`, one batched execution per 90s). Before 2026-09-30 it did not,
so a followed match nobody had open was on the slow tier and its card updated every 15 minutes at
best. So a tracked match costs one billed read
at first paint and none after. A promoted notification does **not** need a foreground service; the
FGS only keeps the process alive for the websocket frames, which is why `onTimeout` (the API 34+
six-hour cumulative `dataSync` cap, which a Test day exceeds) can `STOP_FOREGROUND_DETACH` and
leave every card posted. The card is one half of following a match (the "Live score card" switch in
`MatchFollowSheet`); the settings row exists only to surface the one state the user cannot fix from
inside the app. The channel is `live_update_scores` at `IMPORTANCE_DEFAULT`: the retired
`live_activity_updates` was `IMPORTANCE_LOW`, channel importance is immutable after creation, and a
LOW channel has no status-bar presence for the chip to attach to. `startForeground` binds the
service to exactly one notification id, so one match's card is the anchor and `anchorTo` moves it
when that match ends.

**A cricket card is a scoreboard, not a position, so the style is `BigTextStyle`.** `ProgressStyle`
allows exactly **one** line of text below the title, which is why the card it replaced showed a
batting pair and nothing else — no opposition total, no bowler, no target — while a bar counting
balls bowled took a third of the height. None of the iOS Live Activities this is modelled on has a
bar. `LiveUpdateProgress` and its test were deleted with it; do not reintroduce a bar without
re-reading what it would displace. This constrains the **promoted** card only — the custom-layout
card below has real views.

**`setColorized(true)` is a promotion requirement, and it is the only flag that moves the verdict.**
Measured on a stock API 36 emulator across every combination of style, `shortCriticalText`, large
icon and actions: `hasPromotableCharacteristics()` is true with colorized and false without, and a
`ProgressStyle` card carrying a chip is refused exactly like a `BigTextStyle` one. The lever on a
promoted card is the colour, not whether there is one.

**There are two cards, and the device picks.** `RemoteViews` disqualify promotion absolutely —
measured the same way: `setCustomContentView`, `setCustomBigContentView`, either alongside a style,
and `DecoratedCustomViewStyle` all return false. So a designed layout and a promoted card cannot
coexist, and the promoted one is a single TextView of left-aligned text: no column for the figures,
no second crest, no large numeral. But promotion is **unavailable to most of the install base** —
it is Android 16, `minSdk` is 26, and even an API 36 device can report
`canPostPromotedNotifications() == false` (the stock emulator does). Serving everyone the template
therefore charged every pre-16 user the design and gave them nothing. `LiveUpdateNotifier.build`
now branches on `canPromote()`: promotable devices get the `BigTextStyle` card and the chip;
everyone else gets `LiveUpdateScoreCard`, the real layout
(`res/layout/notification_live_score{,_collapsed}.xml`). Both render one
`LiveUpdateScoreboard` — fields, not a paragraph — so a change to what the card says lands on both,
and `LiveUpdateScoreCardTest` (androidTest) asserts the non-promotable branch actually takes the
layout. Do not collapse this to one path without re-measuring; the docs were wrong about the
colorized rule too.

**A notification is drawn on the platform's surface, not on ours, so it uses the platform's greys.**
`text_secondary` is `#C1B08D` and `card_border` `#33F3DB93` in `values-night` — correct against the
app's gold-on-black surfaces, and inside the neutral system shade they render as tan body text and
an olive divider, the same cast the Fall of Wickets card had. The notification layouts use
`notif_text_primary` / `notif_text_secondary` / `notif_divider` instead, which are neutral in both
files. The flat mustard every card used to be was
`getTeamNotificationColor` (now deleted) missing on **every** match: it matched full country names
with `contains` against `payload.team1Name`, which is a Cricbuzz short code ("RSA", "KENT"), so the
gold was a fallback rather than a team colour. `TeamColorRegistry` replaces it — a plain object,
safe from a Service, with a short-code alias table and a deterministic hash fallback — and the
platform picks its own text colour against the fill. This is the one place the visual grammar's
"team colour is never ink" rule is overridden by the platform, and `LiveUpdatePromotabilityTest`
pins it.

**`LiveUpdateContent` builds five surfaces and each has a different budget.** `subText` is the
header (series · format), `title` the batting side's **whole** contribution plus its over figure
(`"AUS 266 & 234/1 (56.4)"` — the side aggregate, never `score`, which is one innings of a Test),
`text` the collapsed second line under a 48-character budget, `bigText` the expanded scoreboard
capped at six lines, and `shortCriticalText` the status-bar chip, dropped entirely past 7
characters. `bigText` is built in descending order of value — opposition total, striker,
non-striker, bowler, recent balls, status — and truncated from the bottom, because the template
ellipsises overflow rather than scrolling it.

**The live payload carries a `situation` object, and `ignoreUnknownKeys` hid it for its whole
life.** `liveSituation` (`match-sync/src/cricbuzz.js`) publishes the match state as numbers —
`matchFormat`, `battingTeamIsTeam1` (compared by **team id** server-side), `runsScored`,
`wicketsLost`, `ballsBowled`, `target`, `runsNeeded`, `oversRemaining`, and both run rates — and
`MatchLiveDetailPayload` never declared it, so `AppJson.ignoreUnknownKeys = true` dropped it on
every read. Meanwhile the device inferred the same facts from the cached list entry and from exact
string equality between `score` and a side aggregate. `LiveSituation` now models it and is the
authority where present; the string paths stay as the fallback for older rows. Every field is
nullable and `liveSituation` can return null outright — absence must degrade, never read as zero.

**The Live Update read the match cache under the wrong key for its whole life.**
`LiveUpdateSources` used `"live"`; `MatchCacheService` is keyed `"Live"`. `cachedLiveMatch()`
therefore returned null on every device for every match, and that single mismatch cost the card its
series name, its **team crest** (`logoTeamName` joins through the cached match by team id) and its
format at once — three unrelated-looking symptoms, one cause. The keys are now
`MatchCacheService.LIVE` / `UPCOMING` / `RECENT` and every caller reads them; never write the
literal.

**The payload names the first-innings side, not the batting side.** `MatchLiveDetailPayload`'s
`team1*` slots belong to whoever batted first, which is the wrong team for the whole of a second
innings, and the payload's *strings* never say who is batting. `battingSlot` (`LiveUpdateScore.kt`)
reads `situation.battingTeamIsTeam1` first and falls back to matching the live `score` line against
each side's latest innings — both are built from the same Cricbuzz totals, so that comparison is
exact, but it is string equality and a formatting drift takes it out silently. Do not compare the names: `team1Name` is a short code
("EZONE", "RSAA") that matches no full team name, which is why every join onto the app's own teams
goes by `teamId`.

**Match alerts are server-sent FCM data messages to a per-match topic, and until September 2026 nothing
sent them.** `BouncerFirebaseMessagingService` always handled `wicket`/`boundary`/`milestone`/
`match_start`/`match_end`, but the only server push was `poll-live-matches`' `cricket-live-updates`
live-count broadcast, which the device turns into a widget refresh and nothing else — so a user with
notifications enabled received none. Now: `match-sync` sends wicket/boundary (from `classifyEvent` of
the raw line, on a row's **first** write only — a 409 means another execution already alerted),
milestones (batter runs crossing 50/100/150/200 between runs, kept in memory; a cold container seeds
rather than alerts) and the result (on the once-per-match finalising run); `poll-live-matches` sends
`match_start` when an id first appears live (`startedIds` in `poll-state`, 6-day memory; the first run
after deploy records without sending). Deliveries older than `ALERT_MAX_AGE_MS` (5 min) are not
alerted, so a Cricbuzz backlog republish does not burst. Topic = `match_{id}` (`matchTopic`, in the
vendored `fcm.js` and mirrored by the Kotlin `matchTopic`, both pinned by `FcmJsVendorParityTest`).
The server sends every event whether or not anyone listens; the device decides in
`shouldShowMatchAlert` (per-match prefs × the Settings switches, which were cosmetic until then ×
quiet hours). Both functions need the **secret** `FIREBASE_SERVICE_ACCOUNT_JSON` + `FIREBASE_PROJECT_ID`
— secret values read back empty from the API, so check the run log: each run logs `FCM not
configured` when they are missing. **The bell opens `MatchFollowSheet`**, which sets two independent
things in `MatchSubscriptionManager`: `subscriptions` (the Live Update card) and `alertPrefs` (which
alerts; any on ⇒ subscribed to the topic). `ScorePollingWorker` no longer posts notifications.

**AI cache: dormant on the device.** `AICacheService` (`MemoryFifoCache` → Room `ai_commentary_cache` → Appwrite `ai-commentary-cache`) has no caller: the device generates no commentary, so `getCommentary`/`setCommentary` are unreachable. Its Realtime table subscription was removed in September 2026 — it fed `ingestFromRealtime`, whose rows nothing reads, so every device paid Realtime bandwidth for a dead cache. The class and the startup replay stay until an R8 `usage.txt` pass removes them; do not re-add the subscription.

**The visual grammar is restraint, and it is load-bearing.** The August 2026 pass exists because
the app read as generated rather than designed. Four rules, each written into the file that owns it:

- **Surface.** A list of same-kind things is **rows on one background split by `HorizontalDivider`**,
  never a card each — `MatchCardV2` is the reference. `BouncerCard` means "these belong together",
  not "this is important": 12dp radius, `surface` fill, 1dp `divider` border, **0 elevation**. Use
  `BouncerRowGroup(items) { row(it) }` when several rows share one container. Never nest cards.
- **Colour, one job each.** Every number, name, over and status line is neutral
  (`onSurface`/`textSecondary`) — a result is not win-green, or the Recent tab is one green column. Red = live only. **Gold = interactive affordance only** — links,
  selected chip/tab, action text — plus the wordmark; not section headers, not borders, not fills, not
  the top bar (which is the page colour with a hairline). Team colour only
  where it identifies a team as a *shape* (the Home hero's win-probability bar), **never as ink**, and only
  when `TeamColorRegistry.isKnown` is true for both sides: its hash fallback painted associate fixtures
  magenta against lime. That gate is **`rememberSegmentColors`** (`ui/theme/TeamSegmentColors.kt`) and
  every proportional two-sided bar calls it — it lived inline in `HeroMatchCard` alone, so Home obeyed
  the rule while the match-detail win-probability card painted Amsterdam Flames magenta against Dublin
  Guardians blue. Do not re-inline it; a mirrored rule is not a pinned rule. Dark-mode text greys are neutral, not cream/tan. Event colours
  only on ball chips and commentary badges. `glassBorderHighlight`/`glassBorder` are **border**
  tokens — using them as a fill is what gave Fall of Wickets its olive cast.
- **Numerals are tabular.** Scores repoll ~every 6s; proportional digits reflow the row on each
  tick. `scoreLarge`/`scoreMedium`/`numeric`/`numericSmall` set `fontFeatureSettings = "tnum"` —
  use them for anything that re-renders with new digits in place.
- **Motion reports state changes only.** No ambient loops. A score tick and the event flash are
  motion; a breathing glow, a sweeping shimmer, a pulsing border, a bouncing bell and a pulsing LIVE
  dot are decoration — `StatusBadgeV2` is a static dot and a word, with no tinted pill. `SkeletonLoaders` are static blocks for the same reason.

- **Type and labels.** The face is IBM Plex Sans, bundled in `res/font` (`BouncerFontFamily`) —
  not Inter, and not a downloadable font, which fell back to Roboto whenever the Play provider was
  slow. Plex digits are tabular by construction. `SectionHeaderV2` is sentence case in `onSurface`;
  a tiny tracked all-caps overline is the most recognisable generated-layout tell. A list row marks
  only a **live** match; upcoming and decided rows say so in their status line. Home's filters are
  text tabs plus one competition menu, not chip rows with counts, and its hero appears only for a
  live match.

Two related traps. **Don't restate the fixed header**: `ScoreHeaderV2` is a sibling of the pager,
so it is on screen for every tab (a "need X off Y" chase card was considered and dropped for this
reason — the header's status line is Cricbuzz's own, which already says it) — Overview used to open with the status, CRR/RRR, recent balls and
a "momentum stream" that were all already above it. And **never present a figure the app does not
have**: `PremiumAiStatisticsTab` shipped a hardcoded bar chart as "ANALYTIC CONTRIBUTION",
hardcoded sparklines as run-rate trends, a constant string as a "predictive engine" insight, and
the win probability relabelled as both an impact score and a model confidence. The only server-side
model is win probability (`bouncer-win-probability`).

Density target: Home shows **4 matches per screen** (hero plus three rows, or four rows), measured
against `other-apps/cricinfo|cricbuzz`. Five fit at 10dp `rowVertical` and read as cramped; the row
is 14dp with 8dp between its lines. Those three reference apps and `design/stitch-mocks/` are the
design spec; check a change against them before adding chrome.

**Light mode is a first-class palette, and the legacy layer is bridged into it.** `BouncerThemeV2`
resolves Light/Dark/System and there is a user picker, but for a long time light mode was unusable
for three independent reasons. (1) `LocalBouncerColors` — read by ~46 files via
`BouncerDesignSystem.colors` — had **no provider** outside the legacy `BouncerTheme`, which the app
never calls, so every one of those reads returned the static dark default. `BouncerThemeV2` now
provides `legacyColorsFor(isDark)` from `ui/theme/LegacyColorBridge.kt`; `LegacyDarkColors` is
`BouncerColors()` so dark is byte-identical. (2) The dark-only top-level constants (`TextPrimary`,
`GlassSurface`, `GoldPrimary`, …) were `val`s, constant by construction. The raw literals are now
suffixed `*Dark` and the original names are **composable getters** reading the bridge, so ~260 call
sites follow the theme unedited; anything used outside composition points at the `*Dark` constant.
(3) Light `primary` (#E0B74F) is 1.74:1 on white. Gold as *fill* and gold as *ink* are now separate
roles — `primary` vs **`primaryText`** (#8A6516, 5.34:1). In dark they are the same value, so every
`primary` → `primaryText` swap is a light-only change. `BouncerThemeV2` also maps the **full** M3
scheme: unmapped roles fall through to the M3 baseline, which is purple, and `secondaryContainer` /
`surfaceContainer*` paint the nav indicator and every Card. Pinned by `LightModeContrastTest`
(WCAG floors for both palettes) — add a token, add its pair there.

**Contrast helpers are direction-aware, and team colours have a ground.** `Color.ensureContrast`
compared luminance the wrong way round and walked colours *toward* the background;
`liftForContrast` only ever raised HSV value, which is correct on a dark surface and bleaches on a
light one. Both now move away from the background. `TeamColorRegistry` baked every brand colour
against `BlackBackground` regardless of theme — it now takes a `Ground` (background + surface + ink);
use `rememberTeamGround()` / `rememberMatchTeamPalette` rather than calling the registry with the
default.

**RemoteViews colour is resolved per Context, not per constant.** `values/colors.xml` is the light
set and `values-night/colors.xml` the dark one — keep them key-for-key identical. Widgets and the
seven notification layouts read through them, and `WidgetRemoteViewsColors.of(context)` replaces the
old object of `toArgb()` constants (a singleton would cache the wrong mode). Glance styles pair
day/night via `cp(light, dark)`. The **splash stays dark in both modes on purpose**:
`ic_launcher_foreground.png` is a mostly-opaque black mark, so a light plate renders a black square
— hence `@color/splash_background` has no night variant.

**An empty Upcoming list is Cricbuzz's state, not a broken page.** `live-scores/upcoming-matches`
sometimes shows "Nothing to show" while fixtures exist, and the ~90 matches the scraper still
parses there are the site-wide live ticker, which the PRE_MATCH filter correctly rejects. Do not
switch the source: `/cricket-schedule/upcoming-series/…` lists series schedules, and switching to it
was reverted. When the scrape is empty, `LiveActivityViewModel.updateMatches` serves
`MatchCacheService.lastKnownUpcoming()` — the last non-empty Upcoming list this device saw, minus
fixtures whose `startDateMs` has passed. The `upcoming-matches` Storage file cannot stand in: it is
overwritten with the same empty list and carries no start times. Pinned by `UpcomingFallbackTest`.

**There is no Matches tab.** The bottom bar is Home / Series / News / More. Home lists every match;
its "All matches" link pushes `AllMatchesScreenV2`. `SeriesListScreen` is the Series tab and is no
longer a row under More; `series/…` and `points-table/…` highlight Series, `match/…` highlights Home.

**Series detail reads the series' own pages, and every series tab needs the slug.**
`/cricket-series/{id}/{tab}` answers 200 with no data for every tab; only
`/cricket-series/{id}/{slug}/{tab}` carries it, so `seriesSlugOf` reads the slug off the unslugged
page (`SeriesDetailScraper`, `PointsTableScraper`). Schedule walks `matchDetailsMap` groups — never
bare `matchInfo`, which is also the site-wide ticker on the same page. Stats is
`/stats?statsType=X`, server-rendered per type, and one page carries every format's table
(`initialStats.{odi|t20|test}StatsList`); a series that has not started has no stats section at
all, which is the normal answer, not drift. The Series tab's Past view is
`/cricket-scorecard-archives/{year}` (sections `internationalData`/`leagueData`/`domesticData`/
`womenData`); `/cricket-schedule/series/all` is current and upcoming only. Pinned by
`SeriesDetailFlightTest` and `SeriesListMergeTest`. The schedule opens on the next match
(`scheduleAnchorIndex`, pinned by `SeriesScheduleAnchorTest`).

**A tapped match is an overlay above the NavHost, and the NavHost stays composed under it.**
`BouncerAppContent` used to *swap* `MainAppContainer` for `MatchDetailViewV2`, which disposed the
NavHost and its back stack, so closing a match opened from a series, a search or a team landed on
Home. The navController is now hoisted there, the list poll pauses while the overlay is up
(`isCovered`), and the match screen reaches navigation through `LocalOpenSeries` rather than a
parameter.

**Team sheets come from `/cricket-match-squads/{id}`, fetched only when the Info tab opens.** The page is server-rendered (unlike `/live-cricket-over-by-over/{id}`, which is client-rendered and carries no per-over data server-side — the same dead end as the full-commentary page): its flight stream holds `{team1: {team, players}, team2: …}` where `players` is keyed `"playing XI"` + `"bench"` after the toss and `"Squad"` before it. `MatchSquadsRsc` parses it; `MatchSquadsSource` caches an XI for the process and a pre-toss squad for 10 minutes. No Appwrite cost. Aligned to `match.teams` by team id. Pinned by `MatchSquadsRscTest` against both captured shapes.

**Match reminders are listed at Notifications › Reminders.** `ReminderBottomSheet` used to schedule an alarm and record nothing (`RemindersManager.saveReminder` had no caller), and `RemindersActivity` was never launched, so a reminder could be set but never seen or cancelled. The sheet now upserts into `RemindersManager`; `upcoming()` drops fired ones.

**Cricbuzz profile pages are App Router.** `/profiles/{id}/{slug}` carries no `__NEXT_DATA__` block — only `self.__next_f.push(...)` chunks — and the legacy Jsoup selectors (`cb-font-40`, `cb-plyr-inf`, `cb-plyr-thead`, …) match **zero** elements on it. Both of `PlayerProfileScraper`'s original strategies were therefore dead, and the fallback produced a profile with a name and every other field empty rather than an error. `PlayerProfileRsc` reads the flight stream via `forEachNextFlightPayload` — the same parser the match pages use — and runs first; the other two stay as fallbacks. Pinned by `PlayerProfileRscTest` against captured markup.

**Scorecard player names are in a span, not the row's link.** A `div.grid.scorecard-bat-grid` /
`scorecard-bowl-grid` row's only `<a>` elements are a hidden per-player menu — "View match
performance", then "View profile" pointing at `/profiles/{id}/{slug}` — while the name sits in a
`span` whose class carries `hover:underline`. `row.select("a").first()` therefore named **every**
batter and **every** bowler "View match performance"; those names were written to `match-scorecard`
by `bouncer-match-sync` and rendered by the Scorecard tab, where `items(..., key = { it.name })`
turned the collision into `IllegalArgumentException: Key … was already used` (Sentry ANDROID-40).
`playerNameOf` (both `CricBuzzParser.kt` and `match-sync/src/cricbuzz.js`) reads the span first and
falls back to a non-menu link. Pinned by `CricBuzzScorecardRowFilterTest` against captured markup,
with `cricbuzz.js` declared as a Gradle test input so a one-sided edit fails the build.
**Never key a lazy list on scraped text** — keys must include the index.

**That menu is in the fall-of-wickets cell too, and partnerships are a different grid.** The
ANDROID-40 fix reached only the batting and bowling grids. A `div.grid.scorecard-fow-grid` row has
three cells and the first holds the name span *and* the same hidden menu, so both parsers named
every dismissed batter `KL RahulView match performanceView profile` — rendered as "KL RahulView
match", and, because `mapFallOfWickets` looks the batter up in `innings.batsmen` by name, followed
by a `0 (0)` where their score should be. `score` is the **runs half alone** (`"37"`, not `"37-1"`)
in both parsers: `OverviewDerivedData` reads it with `toIntOrNull`, for which the joined form is
silently 0. Separately, partnerships render as `div.scorecard-pship-grid` and never as fow-grid
rows, so the header walk that looked for a "Partnerships" section among them returned nothing on
every match — `innings.partnerships` was always empty and the Partnerships card never appeared.
Kotlin also had no partnerships parser at all, and left `extras`/`total` blank. Pinned by
`CricBuzzFowPartnershipTest`. The Kotlin FOW parser had been dead since the App Router rewrite and
its unit test kept passing, because that test asserted a hand-written `45-1 (D Warner, 8.2)` text
blob rather than captured markup — the same trap the paragraph above records.

**R8 renames the Appwrite SDK's own request fields.** `io.appwrite.Query.toString()` is
`JsonExtensionsKt.toJson(this)` — reflective Gson over the private `method` / `attribute` / `values`
fields — and that string is what goes on the wire. Minified, it sent `{"a":"orderDesc",…}` and
Appwrite answered `Invalid query method:` with an empty name, failing **every** query in the app at
once while each caller swallowed it into its own fallback. `Query` and `Operator` sit outside
`io.appwrite.models`, so the existing keep did not reach them; `proguard-rules.pro` now keeps both.
Verify a change here in the minified dex, not the source: `dexdump -d <classes.dex>` must still show
`name : 'method'` inside `Lio/appwrite/Query;`.

**Win probability is v6: every team, ground and player term is computed as of the match.** v5 knew
a team by its win rate over the whole training window — which includes the match being trained on,
so the term read its own result, the model leaned on it, and the calibration temperature had to
undo it (1.87 pre-match). `asOfState.js` walks the archive once in date order and reads each
match's context BEFORE folding the match in: Elo, form and head-to-head per side; a ground's
scoring, chase record and how often each side has played there (the only "home" signal Cricsheet
has); and each eleven's batting and bowling against the format's mean. Holdout (2024-12-27 on),
log-loss as served: **chase 0.3949 → 0.3729, first innings 0.6110 → 0.5654, pre-match
0.6775 → 0.6274**. The Test head is v5's, unchanged.

What was measured and left out, so nobody re-adds it on intuition (all on top of team + ground +
elevens, chase head, same holdout): the toss 0.0004, the current partnership 0.0008, the last five
overs 0.0006 — both against the innings' own rate and against the rate the chase still requires —
and the crease pair plus the remaining order 0.0012, which needs a scorecard a non-viewed match
never fetches. Boosted trees on the same columns: 0.3672 against the logistic model's 0.3667, with
worse calibration, and worse outright on first innings and Tests. For Tests, over four rolling
folds no group cleared its own noise (ground came closest: −0.025, interval [−0.053, +0.005]), and
**weather did nothing even with hindsight** — the weather that actually happened, with rain crossed
against time left, scored 0.7330 against 0.7208 without it. Limited-overs weather was then tested the way
it would be served — trained on the weather that happened (7,853 matches covered, a shuffled spread
across every season), holdout scored on what was FORECAST (`--weather-holdout`) — and it made both
heads worse: chase 0.3873 against 0.3830, first innings 0.5902 against 0.5806 with calibration
error up from 0.012 to 0.040. **Weather is not a model feature in any format.** The forecast is
still fetched for the Info tab and the preview, where it is shown as a forecast. Do not finish the
backfill (`appwrite/tools/weather-backfill.mjs`) expecting a different answer; the remaining
matches are more of the same seasons. `appwrite/tools/winprob-lab.mjs`
is the harness; every comparison is a column subset of one wide matrix, so two configurations
never differ by their sample. **The gate is ≥0.001 log-loss with a match-bootstrap interval that
excludes zero** — rows within a match are near-duplicates, so a row-level interval is meaningless.

**Each head ships two models, `core` and `full`, and the reader picks.** `full` also reads both
elevens and is used only when the context carries one for each side. A single model taught to
tolerate a missing eleven measured worse in BOTH cases than one fitted for each (0.3911 masked
against 0.3845 for a model that never had the group). Elevens are thinned in training at the miss
rates measured on 637 players of live team sheets (`JOIN_FAILURE` in `build.js`), because **a
Cricbuzz player joins to a Cricsheet person by name only** — the register's `key_cricbuzz` column
fills 50 of 18,554 rows. `playerjoin.js` joins inside one side's recent roster by surname and
initials (reading initials off the name as written, so "MS Dhoni" is not a man called "Ms"), then
among everyone of that gender with that surname's first letter, and returns null when two people
fit. Seven of eleven joined is the floor for an eleven; below it the side has none.

**`contextmath.js` is the one implementation of those terms, vendored three times.** The trainer
computes them for history, match-sync and win-probability recompute them live. A shrinkage
constant or a vector order that differed would not break anything — the model would be handed a
number that means something else. `ContextMathVendorParityTest` byte-compares the copies, all
three declared as Gradle inputs. `feature-parity.test.js` still pins the BASE vectors;
`contextFeatures` removes the retired strength term from each and appends the context.

**The model is published by pointer, and a bad refit is not published.** The trainer writes the
tables, then `winprob-v6-<stamp>`, reads it back, and only then rewrites `winprob-manifest`; the
previous model stays in the bucket, so rolling back is rewriting one small file. `publishProblems`
refuses a refit whose as-served log-loss is 0.02 worse than the manifest's, whose ECE passes 0.04,
or that scores a side needing two runs off thirty balls below 0.9. match-sync follows the manifest
and re-checks hourly; it used to hold one load — or one FAILED load — for the life of its
container, so a trainer mid-rewrite could leave a container on the heuristic for hours.

**A live match's context is `mc-{matchId}`, built at most two a run, and it costs no database
read.** `context.js` is the arithmetic and is pure; `contextstore.js` does the fetching — the
team-sheet page (`squads.js`, a port of `MatchSquadsRsc`), up to four table shards
(`wpx-<hh>`: rosters, grounds, head-to-head, keyed by FNV hash so a match never downloads the
megabyte the whole table would be; `wps-<letter>` for a player new to his side), and an Open-Meteo
forecast refreshed hourly. Upcoming fixtures build on a separate budget of one a run: they are
scored before live matches, and on a shared budget they took both builds. A ground the archive
does not know is not a special case — it reads exactly as a ground's first matches did in
training. One known side against one unknown is NOT an edge: the team terms are used only when
both are known. The file carries the forecast **as a forecast**, with its fetch time; nothing may
say it is raining because of it, and the interruption that matters is Cricbuzz's own status line.

**The detail payload says what moved the number.** `winProbabilityFactors` is up to three of
`teams` / `ground` / `elevens`, each the probability as scored minus the probability with that
group neutral, oriented to team1 like the number itself (two outcomes, so the sign simply flips;
a Test has none). It is exact for the question "what would the model have said without this",
which is the only question the prediction card asks. Kept out of `live-winprob`: every list
downloads that file and no list shows factors.

**The head-to-head card never rendered, and four cards showed figures nothing produced.**
`H2HRepository` lowercased the pair it looked up and compared it with keys the trainer writes in
original case; none of the 1,732 keys in the live file is lowercase, so the lookup never matched.
It now indexes the file case-insensitively on load. The file is the **T20 record only**, and it used
to pool a country's men's and women's sides under one key: a women's pair now has its own
(`A|||B|female`), and the card is shown on T20 matches only. Every other format reads its own
record — right format, right gender — from the match context, on the Info tab. Removed in the same pass, because each was a
constant: the toss card's "venue win %", "H2H toss 3-2" and "Dew factor HIGH"; the prediction
card's 85% confidence badge and "Updated: Just now"; and the Info tab's weather panel (wind, dew
point and visibility hardcoded, temperature and humidity defaulted) with its pace/bounce/spin bars
scored from keywords in the preview text. The pitch report is shown as written; the forecast and
the ground's record now come from `mc-{matchId}`.

**`backlogCtx` is module state and two executions can be alive in one container.** A run that
finished nulled the shared variable under the next run, which threw `Cannot set properties of null
(setting 'viewers')` and failed outright — seen once, at 14:54:38 on 2026-10-03, when two
executions started a second apart on a fresh deployment. Each run now holds its own `runCtx` and
clears the shared variable only if it is still its own. Anything else at module scope that one
run sets and another reads has the same hazard; `contextIo` (the context store's `storage`/`log`)
is deliberately re-pointed at the start of every run for the same reason.

**The win-probability history is the over history, stamped.** `live-winprob` and the detail
payload are snapshots, and for a long time nothing kept a series: `MatchDetailViewModel`
accumulated one on the device, so the swing chart began when the screen was opened and Key Moments
(which reads `keyEvent`, never set on that path) was always empty. `stampProbability`
(`match-sync/src/main.js`) now writes team1's probability as `p` on the over that has just been
completed, in the same `scorecard.overs` points the worm is drawn from — no extra write or read —
and `scorecard.probabilityTeamId` says whose probability it is. Only the newest over is stamped,
once: an over first seen late gets no figure rather than a later moment's. `mergeOverHistory` keeps
a stamp when a fresh page reading of the same over arrives without one. Tests are not stamped
(three outcomes). `probabilityHistoryFrom` (`viewmodels/ProbabilityHistory.kt`) reads the stamps in
the screen's team order, names an over's wicket only when the over before it is present, and
returns nothing when the team id is absent — an unknown orientation is not a guess between two.
The session series remains the fallback and supplies the live end point. Before any of this the
series came from `RetrofitClient.localApi` — `http://10.0.2.2:8000/`, an emulator-only dev mock —
so on a real device it was a guaranteed CLEARTEXT failure per match open; that client is deleted,
do not reintroduce a localhost API.

**A Test side's score is two innings, and the two data paths both used to drop one.** "266 & 83/2"
is one side's score, and both producers collapsed it: the match list's `latestInnings` returned
`inngs2` if present else `inngs1`, and `match-sync`'s `parseLiveDetail` read `inningsScoreList[0]`
and `[1]` — innings by index, which is per-team only in a limited-overs match, so a Test's third and
fourth entries went nowhere. `teamInningsScore` (`network/TeamInningsScore.kt`) and
`teamInningsSides` (`match-sync/src/cricbuzz.js`) now aggregate per side and join with `" & "`,
dropping the wicket count from a completed innings **in TEST/FC only** — limited-overs output is
unchanged. Pinned on both sides by `MultiInningsScoreParityTest`. `parseScore` used to delete `&`
outright, which left the joined string parsing as neither innings and every numeric consumer at
zero; it reads the last segment (the innings being batted) and keeps the whole string for display.

**The live payload is oriented to the first-innings side, and it is aligned by team id.** Everything
`match-sync` publishes — both score slots and the win probability — belongs to the side that batted
first, while `match.teams` is Cricbuzz's listing order; when the side batting second was listed
first, every score and the probability bar sat against the wrong team. Do **not** align on the
names: `inningsScoreList.batTeamName` is a short code ("EZONE" for Central Zone, "RSAA" for South
Africa A) that matches no full name under `normalizeTeamName` or `codeMatchesName`, so a name-based
swap silently never fires. The payload and the `live-winprob` entries carry `team1Id`,
`CricketMatch.Team` carries `teamId`, and `viewmodels/MatchLiveDetailAlignment.kt` compares those.
`battingTeamIsTeam1` was not a substitute either: it compared `current.inningsId` with
`isl[0].inningsId`, so it was false for a side batting its *second* innings even though that side is
team1 — and `winProbFor` flips the probability on it, so for half of every Test the number was
published against the opposition. It is now `current.batTeamId === isl[0].batTeamId`, the same field
`firstInningsTeamId` already reads, with the innings-id form kept only as a fallback for a miniscore
carrying no `batTeamId`.

**A Test is three outcomes, so the complement of a win is not the other side's win.** `evaluateLong`
returns a normalised (loss, draw, win) triple, and re-orienting it to team1 with `1 - batting` — as
`winProbFor` did — hands the whole draw to the bowling side. Match 170667 published
`winProbabilityTeam1 = 0.80` against a `drawProbability` of 0.41: a sum of 1.21, leaving the loss
negative, which the bar clamped away rather than surfaced. The rule now lives in one place,
`orientToTeam1` in `match-sync/src/main.js`, and subtracts the draw; the draw itself belongs to
neither side and is never re-oriented. The device half
(`MatchLiveDetailAlignment.alignedTo`) already did this correctly. The integration test carried its
own copy of the orientation rule, which is why the suite stayed green through the bug — it now
imports the exported one. **A mirrored rule is not a pinned rule.**

**The commentary feed is chronological, and chronological is not cricket order.** Rows are ordered
by `sort_key` (the Cricbuzz timestamp), so a Test's third-innings over 60 is genuinely newer than
its second-innings over 80. `CommentaryTabV2` used to reconcile that by regrouping the whole feed
with `groupBy { over }.toSortedMap(reverseOrder())`, which discards chronology — rendering over 89
directly above over 83 as if overs 84–88 had been skipped, and a wicket at 60.2 above one at 80.5
with nothing to explain it. `buildCommentaryBlocks` walks the feed **in order**, opens a block per
over, and draws an innings divider where the innings changes: from the `innings` column, or — for
rows written before that column could be trusted — from an over number that *rises* while walking
back in time, which is the shape an innings boundary has. Never re-sort this feed.

**An overturned review is not a wicket, and the outcome clause is what says so.** Cricbuzz writes
the on-field decision and the review in one item ("…, no run, … given OUT LBW, but he reviews …
survives"), so every dismissal pattern fires on it. `classifyEvent` (`llm.js`) and
`TimelineEventExtractor.isWicketLine` both read `outcomeClause` — the field after
"<Bowler> to <Batter>," — before any marker in the prose: a clause stating runs is a positive
statement that nobody was dismissed. `outcomeClause` lives in `llm.js`, which `cricbuzz.js` imports,
because llm.js is vendored byte-identically into `generate-commentary`, which has no `cricbuzz.js`.

**The verification call has its own token budget, and it is not the answer's length.** Every
provider in the chain is a reasoning model — groq runs `openai/gpt-oss-20b` with
`reasoning_effort: low` — and reasoning tokens come out of the same `max_tokens` as the reply. So
`verifyLine`'s budget cannot be sized for the word "PASS": at `maxTokens: 16` the budget was spent
before a verdict was ever emitted, groq returned an *empty completion* on the verify prompt while
answering the generate prompt (100) perfectly, and with cerebras and sambanova both on HTTP 402 the
fact-check failed closed on every delivery. That is the whole of ANDROID-38 — 1,858 balls stored as
raw Cricbuzz text — and generation was never the broken half. It is 256 now, pinned by
`verification-scope.test.js`. 64 was tried first and measured insufficient in production: one ball
verified and the next returned `empty completion (length)` again. The verdict is four characters;
the budget is sized by how long the model thinks before emitting them. An `empty completion` note carries its `finish_reason` for the same
reason: `length` (a spent budget) and `stop` (a model that answered nothing) need opposite fixes.
**256 was still not enough for groq** — production logged `groq: empty completion (length)` on
every verify on 2026-09-25, so each high-stakes ball fell through to the two 2048-floor Gemini rungs
(~800/day), which spent both 20/day buckets and left groq alone (ANDROID-4T, and ANDROID-38 again).
groq now carries `tokenFloor: 1024` in `PROVIDERS`; the floor lives on the rung, not in
`verifyLine`. A line that was generated but could not be verified by anyone is
`verifier_unavailable`, not `verification_failed` — the first is a spent chain, the second a
hallucination.

**A 402 is account state, and it was being paid for on every ball while raising no alarm.** Measured
2026-09-15 against the live keys: `GET /v1/models` returns **200** on all three providers and lists
both configured model ids, while `POST /chat/completions` returns **402** on *every* model both
accounts offer — Cerebras `{"type":"payment_required_error","param":"quota"}` (free trial spent),
SambaNova `{"balance_units":0,"code":"PAYMENT_METHOD_REQUIRED"}` (no card). A model a key cannot
reach answers **404 `model_not_found`**, which is what `c324d5b`, `82b467b` and `3bcf060` actually
fixed; this is not that, and no model swap escapes it. Two consequences were being absorbed
silently. (1) `unavailable` is scoped to one line on purpose — a 429 clears by the next ball — so a
402 provider was re-asked on **every delivery**, two doomed round trips at a 15s timeout each, for
six weeks. `paymentBlocked` (`llm.js`) is module-scoped for the opposite reason and holds for the
container's life; the skip is still written to `attempts`, because a three-rung chain that quietly
becomes one rung is the defect this file keeps repeating. (2) `deadProviders` counted **429 only**,
so a provider failing 100% of calls with 402 landed in `calls`, never in the failure column, and
could never reach `rateLimited === calls` — the aggregate alert built for exactly this outage was
structurally blind to it. The tally now counts any non-200 and carries the dominant status, and a
402 **skips the `DEAD_PROVIDER_MIN_CALLS` threshold entirely**: one 402 is conclusive where one 429
is not, and once latched the provider can never be called enough times to clear a ratio. Pinned by
the 402 cases in `verification-scope.test.js`.

**The chain needs a second reachable provider, and credits alone do not give it one.** With cerebras
and sambanova on 402 the chain was groq-only, and `generateVerifiedLine` retries a failed
verification with `exclude: [used.provider]` — deliberately a *different* provider. Groq-only means
that retry has nothing to call, returns null, and every verification FAIL becomes
`reason: "verification_failed"` and a raw-text row. Paying for groq does not change that; it is
structural. Groq's free tier is also 1,000 requests **per day** (`x-ratelimit-limit-requests`) and
8,000 tokens per minute, against two groq calls per rewritten ball and four on a retry.

**Gemini's free quota is per project PER MODEL, so one key buys three rungs — of very different
sizes.** The 429 body names the quota and carries `quotaDimensions: {model}`. Measured limits:
`gemini-3.5-flash-lite` 15 RPM (429 `quotaValue`, 2026-09-16) and 500 RPD / 250k TPM (read from AI
Studio, the only place Google publishes them now); `gemini-3.6-flash` and `gemini-3-flash-preview`
**20 RPD** (`GenerateRequestsPerDayPerProjectPerModel-FreeTier`, `quotaValue: "20"`, production
2026-09-24). The ladder was sized on 2026-09-16 at "≈ 2,500 calls/day" by assuming flash-lite's 500
applied to all three ids — it did not, and the real Gemini supply is **~540/day**, plus groq until
its tokens-per-day cap (hit ~04:30 UTC on 2026-09-25). **Never infer a rung's limit from a sibling
model**: read it at https://ai.dev/rate-limit or from its first per-day 429. All four rungs were
probed against the real `SYSTEM_INSTRUCTION` on wicket/dot/six fixture balls before being added —
the model list is not the set of models a key can call, and `gemini-2.5-flash-lite` proves it by
appearing in `/v1/models` and answering 404 on a completion.

**Any number of Gemini projects.** `PROVIDERS` lists each Gemini rung once; `ladder()` adds a `-<n>`
twin behind it for `GEMINI_API_KEY_2` (always, skipped with its usual note when unset — the names
are stored in `match-commentary.provider` and key the per-day latch, so they must not move) and for
`GEMINI_API_KEY_3`…`_20` when set. One line asks at most twelve rungs over the network and walks
for at most 45 s: with ten projects there are forty Gemini rungs, and `generate-commentary` is
callable by any device, so an unbounded walk was one request reaching every project's quota.

**The ladder since 2026-09-25**: writers `gemini-flash-lite` → `gemini-3.1-flash-lite-preview` →
`groq` (gpt-oss-20b) → `gemini-3.6-flash` → `gemini-3-flash-preview`; plus `groq-qwen`
(`qwen/qwen3.8-27b`), **`verifyOnly`** — asked first for every verdict, never asked to write
(`rungsFor(purpose)`). Probe verdicts that decided it: the preview model had no factual error in 3×11
hard deliveries; qwen invents names when writing ("Babar Zaman") but was perfect as a checker in 2
tokens; `gpt-oss-120b` said "bowled LBW" 3/3 and is rejected; `gemma-4-*` time out or emit raw
`<thought>`; `gemini-omni-*` and `gemini-3.1-pro-preview` have no free quota; `antigravity-*` is
Interactions-API only. groq's per-day 429 is prose ("tokens per day (TPD)"), not a QuotaFailure, so it
latches for 30 minutes (`GROQ_DAY_LATCH_MS`) — without it an exhausted groq rung was re-asked 399
times in one night.

**Limits read from the consoles on 2026-10-03, and what they add up to.** Both flash-lite models:
15 RPM / 250K TPM / 500 RPD. `gpt-oss-20b` and qwen: 30 RPM / 1K RPD / 8K TPM / **200K TPD** — the
token cap is what binds on both. One project therefore supplies ~1,330 calls a day (qwen ~160
checks, flash-lite 500 + 500, gpt-oss ~130, the two reasoning rungs 40), against ~2,400 for the
measured busy day (6,672 deliveries) and ~2,930 for 16 matches bowling for 8 hours. **The checker is
the ceiling**: qwen is token-bound at ~117 tokens a delivery, so it covers ~1,500 deliveries a day
however the writers batch, and every check past that lands on a Gemini writer rung — the weaker
checker (it passed "a maiden over" 3/3) and double the burn on that rung.

**A second Gemini PROJECT is four more buckets; a second key on the same project is none.** The
`-2` rungs (`GEMINI_API_KEY_2`, `account: "gemini-2"`) sit directly behind their twins: same model
ids, prompts and floors, so the probe verdicts carry over. `dailyExhausted` is keyed by rung name
and `paymentBlocked` by account, so the projects latch independently (pinned in
`provider-ladder.test.js`). Unset, each is skipped as `no GEMINI_API_KEY_2`.

Three traps, each of which looks like free capacity and is not. **An alias is not a bucket**:
bursting `gemini-flash-lite-latest` returns `quotaDimensions: {"model":"gemini-3.5-flash-lite"}` —
it bills what it resolves to. **A reasoning rung needs its own `tokenFloor`**: `gemini-3.6-flash` at
`max_tokens: 512` returns `completion_tokens: 18` against `total_tokens: 717` and
`finish_reason: length`, the gpt-oss trap one rung down, so those two rungs carry `tokenFloor: 2048`
while flash-lite keeps the caller's 100. And **a per-day 429 is not a per-minute one** — `unavailable`
is per-line because a rate limit clears by the next ball, which is true of the 15 RPM bucket and
false of the per-day one, so `dailyExhausted` latches until the Pacific reset. Both quotaIds are now
observed verbatim (per-day on 2026-09-24), and `drainUnknownQuotaIds` still reports any 429 matching
neither.

Rejected rungs, so nobody re-adds them: `gemini-3.1-flash-lite` (fast and a separate bucket, but
dropped "ramp" and invented "sharp bouncer" — a hallucination ships, where a timeout merely falls
through), `gemini-3.7-flash` and `gemini-flash-latest` (16s timeout / 503 against a 15s
`AbortSignal`), the Pro ids (429 on the first call), every `gemini-2.5-*` (404, "no longer available
to new users"), and **OpenRouter's `:free` models**, which answered "temporarily rate-limited
upstream" 429 on every call without even consuming the key's 50/day. Cerebras and sambanova left
`PROVIDERS` for the same reason and stay in `probeProviders()` alone, which is how a funded account
would still be noticed. Pinned by `provider-ladder.test.js` in both function dirs.

**On 2026-09-16 the chain was not capacity-starved** — it became so once every ball was rewritten
(see "Rewriting is batched" and the Gemini limits above). Measured 2026-09-16 over 13 days of
`match-commentary`: **94 `ai_generated = true` rows/day** on average and 367 on the busiest day —
~11% of groq's 1,000/day on its own. The number that matters is the other one: **0.7-8.3% of stored
commentary is AI-written**, so the tab is 91-99% raw Cricbuzz text. That is not an RPD problem. It
is `commentaryCapFor` returning 0 for unviewed matches, the slow tier's interval × per-pass cap, and
verification failing closed on a one-rung chain. The extra rungs fix the third and pay for raising
the first two — `SLOW_TIER_INTERVAL_MS` is 45 min *only* because 1,000/day was the whole budget
(`main.js` records the 15-minute option being rejected at 768/day). Concurrency for that sizing,
measured the same day over 312 hours: **mean 2.87 matches bowling at once, 4.28 while any cricket is
on, p90 10, peak 13**, with 33% of hours having none. Count a match live only if it wrote a
commentary row that hour — an open `match-live-detail` window says 7.06/19, because a Test holds its
row open overnight.

**Never state a figure for a partially-stored over.** `overTotalRuns`/`isMaidenOver` require six
legal deliveries present. One stored dot ball rendered as "Over 89 · MAIDEN · 0 runs".

**The commentary `ball` column is the over-and-ball label, and it is the only trustworthy one.**
`CommentaryEntry.ball` comes straight off the `match-commentary` row, where `match-sync` writes
`ballMetric.label` — `"19.2"`, or The Hundred's `"Ball 72"` (pinned by the smoke probes at
`match-sync/src/main.js`). Anything that wants the ball number must read this column. Regexing a
leading `40.4` out of the prose does not work: that prefix exists only on lines the AI chain
rewrote, and the passthrough branch (`ai_generated = false`) writes raw Cricbuzz text unprefixed,
which is the dominant path in production. No over-shaped regex can match The Hundred either.

**Deduplication that reads the ball out of the prose deletes most of the innings.**
`TimelineEventExtractor.deduplicateByBall` used to key both of its passes on
`ballNumberOf(text)`, so on raw passthrough rows every delivery in an over looked like one
unnumbered line. Pass 1 then keyed on the text itself — collapsing the three separate balls that
are all `"Tymal Mills to Matthew Hollard, no run"` into one — and pass 2 compared lines *across*
ball numbers with a prefix floor of `DEDUP_MIN_LENGTH` = 15 characters, which is the width of
`"Tymal Mills to "`. Every short delivery was therefore judged a duplicate of the one long line in
its over, and the long line is always the wicket or the boundary. Over 18 of ETPL match 169380
rendered as two balls of six while the "This Over" chips above it (which come from the live-detail
over summary, not this feed) showed all six. Three unit tests asserted that collapse as though it
were the contract, describing it as "pre-existing behaviour this perf change must not alter". It
was the bug. `deduplicateByBall` now takes a `ballOf` accessor — `CommentaryParsing` passes the
`ball` column — pass 2 compares only within one ball, and `DEDUP_PREFIX_RATIO` is `1.0`, a strict
prefix, which is the actual shape of a progressive republish. **Check a dedup change against an
over of raw passthrough rows, never against distinctly-worded AI prose**, which is what the old
fixtures used and why they never caught this.

**The Series tab and the player profile screen read their caches.** `SeriesListViewModel` paints
from the Room `series_cache` first and then merges the scrape, keeping the cached rows when the
scrape fails; it used to live-scrape on every open, which meant it could not show a series search
could find (that table holds `active-series`, which is active **plus archived**), showed nothing
offline, and replaced the list with an error screen on any failure. `PlayerDetailScreenV2` and
`PlayerComparisonViewModel` go Room → `AppwritePlayerProfilesClient.pullProfile` → scrape via
`PlayerCacheWorker.cachedProfile`; both used to scrape unconditionally, so the profile screen
disagreed with search about the same player and the global cache bought the one screen that shows a
profile nothing at all. `statsJson` had no reader until `deserializeStats` existed.

**Search ranks in memory over what the app already holds, and it carries the Cricbuzz id.**
Every comparison goes through `normalizeForSearch` + `SearchScore.of` (`utils/SearchText.kt`):
accent-folded, punctuation-free, and word by word for multi-word queries, with
optimal-string-alignment distance so a swapped pair ("kolhi") is one edit. The corpora are
`LiveActivityViewModel.allMatches` (every cached status, **not** `currentMatches`, which Home's
filters had already narrowed), `TeamAcronymManager.allTeams()` (~1,000 curated sides and their codes;
a query equal to a curated code scores `ACRONYM` and counts as an exact entity, so "mi" is not
fuzzy-corrected), all of `series_cache` and the name columns of `player_cache` — loaded once per
screen by `SearchCorpusLoader` and ranked in Kotlin. The DAOs' `LIKE … LIMIT 5/10` searches had no
`ORDER BY`, dropped rows before ranking, and matched a player on `country`, so "india" listed every
cached Indian player; do not reintroduce SQL-side search. `expandedSearchQueries` expands competition
acronyms per word ("ipl 2026"). Network players are re-scored by the device (`mergePlayers`) instead
of a flat 0.9, keep their `cricbuzzId`, and the player route carries it (`player/{name}?cbId=`):
`scrapeProfile` then skips the name search whose `first()` opened the wrong profile for two players
who share a name, and the cache row is `cb_{id}` so a name-keyed row cannot mask the right player.
The server's `rankPlayers` mirrors the same normalisation and typo tier; both are pinned to
`player-search/test/fixtures/search-normalization.json` by `SearchNormalizationParityTest` and the
function's own test. A score tick re-runs only the local leg (`onSourcesChanged`) — never the billed
player lookup.

**Cross-language rules are pinned, not commented.** `LlmJsVendorParityTest` (llm.js), `ContextMathVendorParityTest` (contextmath.js, three copies),
`NextFlightVendorParityTest` (nextflight.js), `WinProbJsVendorParityTest` (winprob.js),
`SeriesClassificationParityTest` (`SeriesClassification.kt` ↔ `series.js`),
`SearchNormalizationParityTest` (`normalizeForSearch` ↔ `player-search`'s, via a shared fixture) and
`MatchStatusParityTest` (all three classifier lists, Kotlin ↔ `matchstate.js`, plus `cricbuzz.js`'s
copy of the terminal one). Every one of those files is declared via `inputs.files(...)` in
`app/build.gradle.kts`; undeclared, an edit outside the module leaves the test task UP-TO-DATE and
the check never runs. Prove a new guard bites by mutating one rule and watching the build fail.

**Player profile cache (global):** `PlayerCacheWorker` checks `AppwritePlayerProfilesClient` before scraping Cricbuzz. First device that scrapes a player pushes to Appwrite (`player-profiles` table, `bouncer-db`); all subsequent devices get a cache hit. Row ID = `entity.playerId` (Room format: `virat_kohli`).

**Appwrite project:** single database `bouncer-db`. Known tables: `ai-commentary-cache`, `highlights`, `highlights-meta`, `user-favourites`, `player-profiles`, `match-commentary`, `match-live-detail`, `match-scorecard`.

**Rewriting is batched across MATCHES, and that is what makes every ball affordable.** Measured
2026-09-16: 6,672 deliveries a day is ~960 calls batched, against the ~6,700 one call per ball
would need. The "~2,500-call ladder" this was sized against was never real (Gemini is ~540/day), and
~800 high-stakes verify calls come on top, so on 2026-09-24/25 the free ladder ran dry mid-evening
UTC and rewrites fell to ~25% until the Pacific reset, then ran at ~94%. The batch is filled by **breadth, not by waiting**: `handleRun` already runs the matches
concurrently, ~7 new deliveries arrive per run across them, and `queueRewrite` coalesces those on a
250ms debounce into one `generateBatch` call. Filling a batch by over instead would need ~10 minutes
of accumulation against the 0-60s a ball currently takes to appear — the cron stays the only thing
gating that. Cross-match contamination was the risk and was measured: seven deliveries from seven
unrelated matches returned 7/7 keys with zero players leaked.

Three rules hold it up. **Lines are matched to deliveries by `[matchId|ball]` key, never by
position** — a model that drops or reorders a line must leave that delivery absent so it stores raw
text, because a confidently wrong line is far worse than a raw one. **Every queued promise must
resolve**, including when the batch throws: `handleRun` awaits every match and every match awaits
its deliveries, so one unresolved promise costs the run its 120s timeout (pinned by a seam,
`__setBatchFn`, because `generateBatch` returns an empty Map rather than throwing and no fetch stub
can reach that catch). And **every rewritten line is fact-checked, in one batched call** —
`verifyBatch`, run inside `flushBatch` right after `generateBatch`, each line judged against its own
raw Cricbuzz text, verdicts matched by key. Until 2026-09-25 only wickets and boundaries were
checked, one call each; checking every ball per-line would be ~6,500 calls a day. It fails closed:
a FAIL is `verification_failed`, and an unanswered key or no checker at all is
`verifier_unavailable` — never an unchecked line. Measured before shipping on real lines plus six
planted errors (four→six, wrong fielder, lbw→bowled, wrong thrower, bowler batting, invented
milestone): all six caught in 3/3 runs via qwen (~700ms for 14 lines) and 2/2 via the Gemini
fallback. The checker is sent the ball number with each source ("Ball 58.6: …") — without it
"closes the over" was unverifiable, and first/last balls of overs were most of the rejections — and
its rules separate a FACT (who, runs, outcome, dismissal, fielder, anything about the over, innings
or match — a "maiden", a "fifty partnership") from COLOUR (line, length, a defensive push), tuned on
2026-09-25 against its own `[VERIFY-FAIL]` log lines, which carry the rejected text because nothing
else stores it. qwen scored 14/14 × 3 on real rejections plus planted errors; the Gemini fallback
misses over/innings claims (passed "a maiden over" 3/3), so qwen staying reachable matters. The rules follow
`SYSTEM_INSTRUCTION`'s own licence ("the atmosphere is yours — the facts are not"), because a checker
stricter than the writer's brief rejects most good lines. The batch prompt carries the raw Cricbuzz line alone: it already names bowler and
batter, and per-match score context cannot travel in a cross-match batch without becoming a
fabricated fact.

**Raw Cricbuzz text is never stored — a delivery with no verified line is retried, then held.**
Measured 2026-09-26/27: 133 of ~5,400 rows were raw, ~90% `verification_failed`, and the checker
was right — groq wrote "Mahipal Lomror sends a blistering drive… for a FOUR" when Lomror was
bowling, a whole batch of six that way. The quota was not the leak; one rejection with no second
attempt was. Two mechanisms close it. (1) `rewriteWithRetry` (`main.js`) gives every rejected or
dropped delivery **one** more writer call in the same run, `exclude`-ing the rung that failed, and
re-samples the same rung only when no other writer answers; a batch nobody answered is not retried
(the rungs are 429/latched and would fail again this second). (2) Anything still without a verified
line goes to the **rewrite backlog** instead of being written: `holdDelivery` stores everything the
row needs (the live page is a ~20-item window, so the ball will not be on it later), the watermark
still advances, and the held delivery is offered to the writer again on later runs — including runs
with nothing live — and the
late row carries its original `sort_key`, which the device's `sortKey`-positioned insert puts where
the ball was bowled. **Since 2026-10-03 a held delivery mostly rides in the spare slots of a fresh
batch** (`flushBatch` → `takeHeld`): every Gemini rung is limited by requests a day, not tokens, and
a typical batch carried 6.95 of its 12 deliveries, so a top-up costs no call. The size stays
`BATCH_MAX_ITEMS` (12), the size the writer and checker were probed at. The serving order is
`backlogOrder`: matches someone has open, then other live matches, then finished ones, **newest ball
first** in each — it was oldest-first across everything, which after a dry evening spent the
morning's quota on yesterday's match while the one being watched waited. `drainBacklog` still sends
dedicated batches, two a run (`BACKLOG_DRAIN_PER_RUN`, 24), but a FINISHED match gets them only
while its backlog is ≤ 24 (the ordinary trickle) or in the 3 hours before the Pacific reset, when
unused quota would expire (`dedicatedEntries`). **Nothing is ever dropped and there is no age limit**
— an old match must always end up complete; that was an explicit decision, do not add an expiry.
A delivery held during a run is not offered again in that run, and `inFlight` keeps one held
delivery out of two batches.

**A source can contradict itself, and then no line passes.** Match 151554's 15.5 read "Gudakesh
Motie to Ruturaj Gaikwad, 1 run, …, Rohit Sharma carves it" — the scorer's header and the
commentator's description naming different batters. The checker failed every rewrite for an hour,
two writers and two checks a run (~240 calls, on a ~1,330-call day), and what finally passed was
groq **echoing the raw text back**, stored as `ai_generated: true`. Three guards since 2026-10-03:
`sourceConflictNote` appends a scorer's note to the text both the writer and the checker read
(fires only when the description names the other live batter and not the header's; probed 3× —
without the note in the checker's copy the correct unnamed line failed 3/3); `isEcho` drops a line
that is the source again, so it gets its retry instead of being stored; and a held delivery's
`rejections` count stops dedicated batches at `HELD_STUCK_REJECTIONS` (3) — it still rides in spare
slots and the pre-reset sweep, and is never dropped — and raises one **error**-level Sentry event
naming the ball. Before that the only report was the generic hourly "Commentary is being held"
warning inside an existing issue (ANDROID-4Z), which named neither the ball nor the cause. Persisted as `rewrite-backlog` in `cricket-stats`, written only when it
changes, never over a file this container failed to read. **A held delivery's alert is sent when it
is first seen**, not when its row lands — a wicket alert minutes late is past `ALERT_MAX_AGE_MS` —
and the drain sends none. Held balls count as present for `findCommentaryGaps`. The cost is
lateness, never a wrong line: when every writer is spent the tab stops gaining lines and then
catches up in order. `reportThrottled("rewrite-backlog")` fires past 15 minutes or 200 held.
Pinned by `rewrite-backlog.test.js`. A "who bowls, who bats" rule in the writer prompt, with the
roles tagged on each input line, was probed the same day on groq over 36 real rejected deliveries
and cut role swaps without reaching zero ("Sofia Mardones takes a single off Maria Miranda"), so it
was not shipped; the checker catches those swaps and the retry replaces them.

**A delivery is not one row, and the ball label is not its identity.** Audited 2026-10-03 over
1,500 rows against the scorecards and the live pages. (1) Cricbuzz republishes a corrected delivery
under a new timestamp and the row id is the timestamp's hash, so the withdrawn version stayed beside
the correction: match 171061's 10.3 was a single, then a catch that never happened, then the single
again. `supersedeOlder` (`main.js`) now deletes the older **legal** row for the same innings and
label when a newer one is written — in memory per container, so a cold start forgets and the device
rule covers it. (2) A wide or no-ball carries the label of the legal ball that follows, and every
label repeats in the next innings. The device's dedup keyed on the label alone and kept the
*longest* text, so `88.4` wide hid `88.4` FOUR and a withdrawn row could outlast its correction.
`deliveryKey` (`CommentaryParsing.kt`) is innings + label, plus the row's `sortKey` for a
WIDE/NOBALL, and the **newest** row wins. Pinned by `CommentaryFilterTest` and
`commentary-supersede.test.js`.

**A source with no description is written in code, not by a model.** "Chinelle Henry to Mupachikwa,
no run" has nothing to rewrite, and the writer filled it with a length, a shot and a field position
on every ball of matches 173107 and 174881 — which the checker passes by design, as colour.
`plainLine` (`main.js`) writes those from bowler, batter and outcome with `provider: "template"`,
`ai_generated: false`; a dismissal still goes to the writer because its source carries facts. This
is about a quarter of deliveries on an associate-heavy day, and none of them cost a call.
`keepsScorerText` rejects a model line that kept the scorer's header or scorecard tail, which exact-
match `isEcho` let through 24 times in those 1,500 rows.

**The voice went to match-situation lines, and they are event-driven** (`situation.js`). A row with
a blank `ball`, `provider: "situation:<rung>"`, written only on a trigger: the same batter's last 2
balls faced both sixes or last 3 all boundaries, a bowler's 2 wickets in an over (run-outs
excluded), 3 wickets in 18 balls, a 0.15 win-probability move inside 15 minutes, or a side first
crossing 0.90. There is **no fixed interval** — a user decision; do not add one. The writer sees
structured facts only and `validSituationLine` checks the result in code: every number, counted
quantity and capitalised word must be in the facts. A failed line is dropped, never retried. It is
stored when its over ends (`sort_key` = the next over's first ball − 1), because a non-delivery row
mid-over splits that over into two blocks on the device. Win-probability triggers are skipped for
Tests and for matches outside the detail tier.

**Three more triggers read the match's context, and a milestone word needs the trigger's own
facts.** `stateTrigger` (`situation.js`): a batter crossing 50/100/150/200, with their record in
the archive's matches of that format; a partnership passing the same marks; and a first innings
ending at a ground whose usual first-innings score the archive knows (`typicalFirstInnings` on the
context). Each remembers what the last run saw, so a cold container that first sees a batter on 73
says nothing — crossing a mark is the event, being past it is not. `UNBACKED_RE` is no longer an
outright ban: a line may say "half-century" only when its facts do, and only a milestone trigger's
facts ever do. **"career" is never in the facts** — Cricsheet is the matches it recorded, so a
record is "5210 runs in 131 recorded one-day innings". A spelled-out number ("sixty-four balls")
fails the line, correctly: it cannot be checked against a digit.

**A match has a preview and a summary, and neither is ever unchecked prose** (`surfaces.js`,
stored on `mc-{matchId}`). The facts are built in code — head-to-head record, the ground's usual
score, toss, forecast, the model's pre-match chance; or the result, each innings, its top scorer
and best bowling. One writer call; `validSurface` then requires every sentence to be grounded on
its own, both sides to be named, and weather to be called a forecast; then the batch fact-checker
runs with the facts as its source, because the code check proves a number is IN the facts and
cannot see it given to the wrong side. Any failure stores the same facts as plain sentences
(`provider: "template"`). Probed on real contexts on 2026-10-03: 8 of 8 written versions passed
both. A preview needs two things beyond the fixture or it is not written; a summary is written on
the finalising run, the one run every match gets a scorecard on.

**The checker rules were not the reason contradictions were stored.** Four stored lines that
contradict their source (163077 89.1 "left alone" → "defends it back down the pitch") all FAIL
under the existing `VERIFY_BATCH_RULES`, 3/3 on qwen and on `gemini-flash-lite-2`, in isolation. A
reworded rule scored the same, so nothing was changed. Why they passed in production is not
established.

**`match-commentary` table:** written by `bouncer-match-sync` (every minute). Columns: `match_id`, `innings`, `ball`, `sort_key` (timestamp, used for ordering), `commentary` (AI-rewritten line), `ai_generated` (boolean, default true), `provider`, `created_at`. Read by `AppwriteMatchCommentaryClient` — newest-first, ordered by `sort_key` desc, **48 rows on the first page and 150 on every page behind it** (`FIRST_PAGE_LIMIT`/`FEED_LIMIT`): reads bill per row RETURNED, and the common case is a glance at the last over or two, so `getOlderThan(sortKey)` pages further back on a reader's tap and only that reader pays for it. Ordering on `sort_key` needs a user index; the fallback ladder is `orderDesc("$createdAt")` — a built-in, always orderable, still newest-first — and only then an unordered fetch, which returns Appwrite's own order (the OLDEST rows of the match) and is the last rung before a blank feed. The degraded rungs cannot page (the cursor filters on the attribute that just failed), so they serve one `FEED_LIMIT` page and a caller asking for older lines gets nothing. **The Wickets and Boundaries chips ask the table for the whole match** (`getEvents`: `equal("event", …)` over the same `idx_match_sort`, 60 rows a page, fetched once when the chip is first selected and held by `CommentaryEventPager` apart from the feed — merged into it, an old wicket would move the load-earlier cursor past every ball between). They used to filter the loaded feed only, so a reader saw the last eight overs' events and no way to the rest; that was misread as a rewrite-backlog ordering problem when the backlog was empty. The fetched rows are unioned with the loaded feed by `sort_key` (`mergeEventRows`), because a wicket that falls while the chip is selected arrives over Realtime into the feed. A failed fetch leaves the chip on the loaded feed and is not "no wickets". Milestones still draws from the loaded feed only — match-sync writes no milestone event — which is why its empty state carries its own load-earlier action;  Realtime subscription emits `CommentaryEvent` on create. **`ai_generated = false` means the row holds raw Cricbuzz text**, and **no new row has been written that way since 2026-09-27** — a delivery without a verified line is held in the rewrite backlog instead (see above); the false rows in the table are older ones, pruned after 14 days. Before that it meant either the provider chain returned nothing, or the delivery was past the run's LLM budget. The device does not read the column — it exists so "is the stored commentary actually AI?" is answerable from the storage layer, which it was not when a dead chain and a working one produced identical-looking rows. **`provider` is the same question one level finer**, because `ai_generated: false` conflates a chain that FAILED with a delivery that had no rewrite budget — a defect and a design decision, indistinguishable in storage and needing opposite responses. A rung name (`groq`, `gemini-flash-lite`, …) means rewritten; `cricbuzz` means nobody tried; `cricbuzz:providers_exhausted` / `cricbuzz:verification_failed` / `cricbuzz:verifier_unavailable` mean somebody tried and failed, and which. So the ladder's real mix is a GROUP BY rather than a guess. Both columns come from `commentaryOrigin` in `main.js` — one function, because two fields describing one fact drift apart, and the invariant that `ai_generated` is true exactly when `provider` is not a `cricbuzz*` value is pinned in `commentary-storage.test.js`.

**`match-live-detail` table:** written by `bouncer-match-sync` (every minute). One row per match, upserted. Columns: `match_id`, `payload` (JSON `MatchLiveDetailPayload`), `updated_at`. Row ID = sanitized `matchId`. Read by `AppwriteMatchDetailClient`; Realtime emits `DetailEvent` on create/update. Both clients use `appwriteAiCacheDbId` as their DB ID.

**`match-scorecard` table:** written by `bouncer-match-sync` alongside the detail row. One row per match, row ID = sanitized `matchId`, columns `match_id`, `scorecard` (JSON `Scorecard`), `updated_at`. **No index** — read by `AppwriteMatchScorecardClient.getForMatch` (one `getRow` for first paint) then kept fresh by `observeMatch`, subscribed for the life of the detail screen. Exists because the scorecard measured 85–93% of the detail payload (4–9 KB of 4–9 KB) and was pushed to every subscriber of that row every minute; on its own row the live card's ~650-byte updates travel alone. **Written on a wicket or a new innings at once, otherwise at most every `SCORECARD_MIN_INTERVAL_MS` (3 min)** (`scorecardWriteDue`, pinned by `scorecard-throttle.test.js`): batter and bowler figures change every ball, so the unchanged-body skip never fired during play and a viewed match cost a write a minute. The finalising write is never throttled. **It also carries `overs`**: one point per completed over (`innings`, `over`, `runs`, `score`, `wickets`, `team`) accumulated by match-sync from each run's live-page `overSeparator`s (`parseOverHistory`), since no server-rendered Cricbuzz page carries the history — the over-by-over page is client-rendered. A cold container seeds from this row once per match (one budgeted read) and overs bowled while no container held the history are simply absent. The device builds the worm/manhattan for both innings from it (`OverWorm`); a gap ends the worm line rather than being filled.

**`bouncer-match-sync` Appwrite function:** scheduled `* * * * *`. Polls the match lists itself first (`runPoll`, see the function list), using the live list in memory and falling back to `live-matches.json` in `cricket-stats`; if neither has anything, returns 200 `{skipped:true}` (not an error). For each live match: fetches Cricbuzz live HTML + scorecard → upserts `match-live-detail` → writes EVERY new delivery to `match-commentary` → rewrites the oldest `commentaryCapFor` of them with the LLM. **Storage is not gated on a viewer; rewriting is.** It used to be the other way round, and a match nobody had open stored one ball per 45 minutes — so the commentary tab showed a sample of the match (overs missing outright, one boundary in an innings, a "recent" line hours old) rather than its commentary. `commentaryMatchSet` caps storage at 16 matches per run, viewers first. **Every stored delivery is now rewritten**, because the calls are batched: `commentaryCapFor` no longer rations rewriting and gates only the DETAIL tier (scorecard fetch, detail row, scorecard row — a write-budget decision), while the slow tier's pass — 60 minutes since 2026-09-30, when followed matches started pinging presence — decides detail freshness for a match nobody has open or follows, not prose. Those last two were 1 and 45 min while groq's 1,000/day was the whole budget; re-derived 2026-09-16 against a ~2,500/day Gemini ladder that was assumed rather than measured (it is ~540), from the measured 11 concurrent unwatched matches. The derivation is written above the constants in `main.js` and mirrored **deliberately** in `slow-tier.test.js` — a test that imports the constant it is checking cannot catch a wrong constant, so that mirror is the one place in this repo where duplication is the point. The watermark seeds from the newest `match-commentary` row (`limit(1)`, one billed read per cold container) and is no longer persisted to the detail row, because most live matches have no detail row. `innings` is stamped **per commentary item** from Cricbuzz's `inningsId`, falling back to the miniscore and then to the scorecard length — the old write-time stamp mislabelled an innings' tail as the next innings. Env vars: `APPWRITE_AI_CACHE_DB_ID`, `MATCH_COMMENTARY_COL_ID`, `MATCH_LIVE_DETAIL_COL_ID`, `MATCH_CACHE_BUCKET_ID`, `APPWRITE_API_KEY`, `GROQ_API_KEY` (+ fallbacks), `SENTRY_DSN` (optional — unset disables server-side reporting), `PROBE_SOURCES` (optional).

**Server-side observability (`match-sync/src/observability.js`):** `@sentry/node`, initialized per container from `SENTRY_DSN` and flushed before every return (the container freezes on return, so an unflushed queue is lost). Reports: raw-text passthrough with the per-provider reasons from `generateLine`'s `attempts`, commentary write failures, a viewer-gated match that produced nothing, an unreadable `active-viewers` file (a 404 is normal and stays quiet), read-budget 402s, **a provider chain with fewer than two reachable rungs** (error level — generation still works, so every other signal looks healthy while `generateVerifiedLine`'s retry has nobody to fall back to; this is the state that went six weeks unnoticed), and **a 429 naming a quota id the per-day latch cannot classify**. The last two are also reported by `generate-commentary`, which on a day with no live match is the only caller talking to the providers at all. Setting `PROBE_SOURCES=1` turns one execution into an upstream reachability probe (status + bytes + markup markers for Cricinfo and Cricbuzz, from inside the Appwrite container) and returns without syncing — that is the only way to answer whether Cricinfo is reachable server-side, which is a different question from whether it is reachable from a laptop or a phone.

**The slow tier is an LLM budget, not a fetch cadence — and the real ball-loss mechanism is the
upstream window.** `handleRun` does `Promise.allSettled(allLive.map(...))`: **every** live match has
its Cricbuzz live page fetched and every new delivery stored, on every one-minute run.
`commentaryMatchSet` caps storage at 16 and `MAX_MATCHES` is also 16, so that slice never drops anything;
`slowTier` feeds only `commentaryCapFor`, the 10/1/0 **rewrite** budget. `slow-sync-state` looks
stale (entries 30+ minutes old while those matches sync every minute) because it is refreshed only
for matches that were on the tier — that is not evidence of a 45-minute fetch cadence, and it was
misread as one.

What actually loses deliveries is that **the live page is a 20-item window**, about two overs —
measured on three live pages, `commText=20` across 55 `self.__next_f.push` chunks. Normally a 20×
margin at a one-minute cadence. But on 2026-09-12 Cricbuzz published nothing for match 161777 for
**thirteen minutes** (match-sync ran every minute throughout, logging `0 new commentary rows`), then
republished a backlog that jumped `12.5` straight to `13.4`. 13.1–13.3 never appeared on the page and
are absent from the table. The watermark absorbed the hole and the tab rendered a three-ball over
indistinguishable from a real one. `findCommentaryGaps` (`cricbuzz.js`) now reports these to Sentry;
it deliberately does not count the source over's tail, because whether that ball was captured by an
earlier run is unknowable from one batch. **There is no backfill**: `/live-cricket-full-commentary/{id}`
is client-rendered and yields zero deliveries server-side, `/cricket-full-commentary/{id}` 404s and
`/cricket-commentary/{id}` 307s back to the live page. Do not re-investigate recovery without new
evidence.

**`MAX_MATCHES` drops live matches past the cap, so it is filled viewers first.** Anything past
the cap gets no commentary, no detail row and no win probability. It was 12 and sliced in Cricbuzz's
list order *before* the viewer set was built, so a viewer watching match 13 was invisible to the
viewer gate entirely; 13 live matches bound it on 2,219 runs in nine days (ANDROID-4P). Now
`liveMatchSlice` puts watched matches first and the cap is 16 — bounded by `READ_BUDGET_PER_RUN` (20),
because a cold container pays one watermark seed read per stored match. The run log prints the
pre-slice count, and Sentry hears about a binding cap at most hourly per dropped-id set (`error` if a
dropped match had a viewer). Steady-state server conditions — chain depth, Cricbuzz unreachable or
blocked, this cap — go through `reportThrottled`; they were one event per one-minute run.

**App-lifetime Realtime starts with the first activity, not with the process.** `RealtimeLifecycleController.attach` begins paused: `Application.onCreate` also runs for WorkManager and FCM wakes, which never start an activity and so never reach `onActivityStopped`, and the highlights channel used to stay open for the whole life of such a process. The match-detail streams (`MatchDetailViewModel`) go through `whileForeground` (`viewmodels/ForegroundGate.kt`) for the same reason — the ViewModel outlives a backgrounded activity — and catch up on return: one `getRow` for detail, `getNewerThan(sort_key)` for commentary (bills only the rows missed). A finished match subscribes to no stream at all, and its first commentary page is served from `ProductionLiveDetailSource`'s small LRU on re-open. `WidgetUpdateWorker` does nothing — and does not re-arm — when no Bouncer widget is placed; the receivers' `onEnabled` restart it.

**Viewer gate:** `MatchDetailViewModel` pings `bouncer-match-presence` every 90s while a match is open and **not over** — gated on `isMatchOver(match, liveStatus)`, never on `match.isLive`, which is a frozen list snapshot (same staleness class as `5b86664`). A dead heartbeat means every sync logs "0 with viewers" and the tab falls back to raw Cricbuzz text — but it does **not** stop deliveries being stored: the slow tier gates the LLM rewrite budget alone (see below). `MatchPresenceReporter.ping` leaves a breadcrumb, not an event: every cause of a failed ping is already reported once by `AppwriteClient.executeFunctionDetailed` (uninitialised SDK, guard, FAILED, HTTP status), and the one it deliberately skips — the device being offline — was most of what the old capture raised (ANDROID-3M).

**The Sentry exception filter never sees a `captureMessage`.** `shouldDropEvent` reads `event.exceptions`, and every `sentryCaptureThrottled` / `logSilentFailure` report is a message event, so an `UnknownHostException` inside one sailed through. Offline and already-reported causes are now skipped at each reporting site with `HttpClientProvider.isConnectivityException` — do not add a new message-level report without the same check. A `SentryHttpClientException` from a host other than `*.appwrite.io` is dropped: the Gradle-injected OkHttp integration captures every 5xx on every host and its targets cannot be set from options.

**Appwrite Storage bucket `cricket-stats`:** read-only from Android via `AppwriteClient.downloadFile(bucketId, fileId)`; written only by Appwrite cloud functions. It is **not** four files — measured 2026-09-12 it holds fifteen, and most are inter-function state rather than app data:

| File | Owner | What it is |
|---|---|---|
| `team-stats`, `h2h-stats` | `train-win-probability` | the last **v5** model, no longer rewritten (the fallback a reader uses when there is no manifest); H2H for 1,700+ team pairs, T20 only, read by the device |
| `active-series` | `poll-live-matches` | active **and archived** series, read by `SeriesSyncWorker` |
| `live-matches`, `recent-matches`, `upcoming-matches`, `live-count`, `poll-state` | `poll-live-matches` | the match lists `match-sync` runs off, plus its own cursor//hash state |
| `live-winprob`, `slow-sync-state`, `rewrite-backlog` | `match-sync` | published probabilities; the per-match slow-tier clock; deliveries held until a line passes the checker |
| `mc-{matchId}` | `match-sync` | one match's context: sides, ground, record, elevens, forecast, preview, summary. Read by the device (Info and Overview tabs). Pruned after 14 days by `pruneContexts`, selected by `startsWith("$id", "mc-")` |
| `winprob-manifest`, `winprob-v6-<stamp>` | `train-win-probability` | the pointer to the serving model, and the model (the current one and the one before it) |
| `wpx-00`…`wpx-3f`, `wps-a`…`wps-z` | `train-win-probability` | the tables a context is joined against: rosters with ratings and plain records, grounds, head-to-head; and everyone by surname letter |
| `active-viewers` | `match-presence` | `{matchId: epochMs}`, the viewer gate |
| `player-index` | `player-search` | name → Cricbuzz profile id |
| `news-cache`, `news-body-{storyId}` | `news-sync` | headline index (4h TTL); one article body per opened story |
| `rate-limit-offenders` | — | legacy, unread |

**Deleting from this bucket is how you break a different feature silently, so select by `startsWith("$id", …)` and never by `search`.** Storage search is fuzzy: measured against the live bucket, `search=news-body` also returns **`news-cache`**, while `startsWith($id, "news-body-")` returns exactly the bodies. `bouncer-news-sync` prunes `news-body-*` past `BODY_RETENTION_MS` (14 days) on its **headline-refresh path only** — that path is already capped at ~6 runs/day by the 4h TTL, so the sweep needs no schedule of its own — and re-checks the prefix on every candidate before deleting, because a query-semantics surprise must not be able to become a deletion.

**Room DB:** currently at schema version 15. v14→v15 dropped the `commentary` table, which nothing ever inserted into (live commentary is the `match-commentary` Appwrite table). The `matches` table exists only for highlight fuzzy-matching; `PlayedMatchIndex` writes it from the Live and Recent lists, and each Recent write re-scores highlights still stored unmatched. It was written by nothing until September 2026, which hid how loose `HighlightFuzzyMatcher` was: the first time it had rows, 154 of 400 highlights linked on a test device and every link was wrong (one shared team plus the league cleared the averaged threshold weeks apart). A contradicting date (>1 day) or a side below 0.92 similarity, or with a different qualifier ("Women", "A", "XI"), now vetoes outright — pinned by `HighlightFuzzyMatcherTest` with those real pairs. **Check a matcher change against rows from the device, not only fixtures.** New migrations: add `MIGRATION_N_(N+1)` in `BouncerDatabase.kt` + bump `version` in `@Database`.

**No FTS.** `player_fts`, `series_fts` and `historical_match_fts` were dropped in v12→v13, along with `historical_match_cache` (nothing ever wrote to it). The FTS entities were `@Fts4` with **no `contentEntity`**, so Room created them with no sync triggers; the triggers that populated them existed only inside `MIGRATION_1_2`, which a fresh install never runs. Every install created after schema v1 was searching a permanently empty index, and only v1-upgraded devices worked — which made it look device-specific. `SeriesCacheDao`/`PlayerCacheDao` now use `LIKE` over the backing tables (a few hundred rows; FTS bought nothing) and that is also what makes acronym expansion work. Do not reintroduce an FTS index without `contentEntity`.

**Appwrite MCP workflow:** `appwrite_search_tools` → `appwrite_call_tool` → `ReadMcpResourceTool` (server: `plugin:appwrite:appwrite-api`, URI from truncated result) for full output. Column creation returns `status: "processing"` — call `tables_db_list_columns` to confirm `"available"` before using. Table-level permissions at creation: `["read(\"any\")", "create(\"any\")", "update(\"any\")"]`.

**New Appwrite client pattern:** `object` singleton extending `AppwriteTableClient(tag)`. The base provides `initTable()`, `isReady`, the billed-operation funnels `guardedRead()`/`guardedWrite()`, the refcounted Realtime `acquireChannel(channel) { … }` + reconnect backoff, `stop()`/`stopAll()`, and `logSilentFailure()` — don't reimplement these, and never construct a raw `TablesDB(client)`. Subclass adds `initialize()` (calls `initTable(dbId, tableId)`) and its table ops as `runCatching` + `logSilentFailure` on failure. Register in `BouncerApp.kt` via `initializeSafely("Name") { Client.initialize() }`.

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
- **Dead-code caution**: manifest-registered classes (`AndroidManifest.xml` `android:name`) and WorkManager `CoroutineWorker` subclasses are reflection roots — never delete these on grep evidence alone. `models/**` and `network/*Dto` used to be proguard-kept for Gson reflection; that keep is **gone** (serialization is kotlinx, whose serializers are generated at compile time), along with the blanket `-keep class com.tanush.bouncer.** { *; }` that made R8 a no-op app-wide. Do not reintroduce either. The sound evidence for what is unreachable is now R8's `usage.txt` from a release build (`~/Library/Caches/bouncer-android/app/outputs/mapping/release/usage.txt`), not grep.

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
