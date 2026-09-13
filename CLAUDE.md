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
│                       # AppwriteFavouritesSync, H2HRepository,
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

**Appwrite cloud functions** live in `appwrite/functions/` (Node.js, deployed via Appwrite CLI). `appwrite/appwrite.config.json` defines project-level config. Ten functions:
- `highlights-sync` — 15-min schedule; syncs cricket highlight clips to Appwrite. **Only one run per hour does anything** (`minute < 15`), and only every 4th hour sweeps the 3-day back-window — Highlightly's plan allows 100 requests per rolling window and `*/15` alone would spend 96 on today. Never make it fetch on every run without raising that plan. Highlightly publishes a clip hours-to-days after its match, so a same-day-only query permanently loses whatever the lag exceeds; that is what the back-window exists for. Requires `HIGHLIGHTLY_API_KEY`, optional `SENTRY_DSN`. Deploy: `node appwrite/tools/deploy-function.mjs` fails here — the ~13 MB `--with-node-modules` archive is rejected by the CDN edge (`503 backend write error` in ~55 ms), so this one needs a chunked (5 MB) upload.
- `generate-commentary` — on-demand; LLM commentary via OpenRouter (called by `CommentaryFusionService`)
- `match-sync` — every-minute schedule; fetches live match HTML + scorecard → upserts `match-live-detail` → AI commentary → `match-commentary` (see detailed section below)
- `news-sync` — on-demand; fetches cricket news from Cricbuzz via RapidAPI, caches to `cricket-stats` bucket (`news-cache` file, 4-hr TTL caps RapidAPI at ~6 calls/day). Requires `RAPIDAPI_KEY`.
- `poll-live-matches` — scheduled; fetches live match data server-side. Also owns the `active-series` bucket file, which now holds a **superset of active plus archived** series: it walks `cricket-scorecard-archives/{year}` two pages per run, resuming from an `archiveCursor` stored in the file. Active-only meant a query for a competition out of season ("World Cup" for most of a four-year cycle) matched nothing anywhere in the app. A `CricbuzzBlockedError` aborts the walk and keeps the previous file — a truncated corpus must never overwrite a good one — and does not advance the cursor, so the next run retries that page.
- `player-search` — on-demand; resolves a player name to a **Cricbuzz** profile id for `PlayerProfileScraper.searchPlayersDetailed`. Cricbuzz has no working player search left (`/api/search/results` and `/search` both 404; `/api/html/search` is a 200-empty stub), so this builds a name→id index from Cricbuzz's own squad pages (`/cricket-team` → `/cricket-team/{slug}/{id}/players`, 38 pages, 593 players as measured on the first live build) and caches it in `cricket-stats` as `player-index` with a 7-day TTL. **The cached index is read back with `parseJsonFile`, never `Buffer.from`** — node-appwrite 15 hands back an already-parsed object for `application/json`, and the unconditional `Buffer.from` this shipped with threw into the "no cached index" catch, so every single query rebuilt the whole corpus: 39 Cricbuzz fetches and ~14s per search, TTL never once hit. Cricinfo's search does work but answers with Cricinfo ids, and `buildProfileUrl` builds Cricbuzz `/profiles/{id}/` URLs — switching id namespace would trade a dead search for a dead profile screen. Returns **503, never an empty 200**, when it has no index and cannot build one: absence and unavailability must stay distinguishable on the device. `PROBE_SOURCES=1` turns one execution into a reachability probe. Needs `timeout: 90` for the cold index build.
- `match-presence` — on-demand; the server half of the viewer gate. `MatchPresenceReporter.ping` posts `{matchId}` every 90s from an open match screen; the function read-modify-writes an `active-viewers` map (`{matchId: epochMs}`) in `cricket-stats` and prunes entries older than `PRESENCE_TTL_MS` (4 min). That TTL must stay **≥ match-sync's `VIEWER_TTL_MS`**, or a viewer match-sync still counts as active gets dropped here first. Concurrent pings race on the read-modify-write (last write wins, losing another match's fresh timestamp) — accepted, because the next 90s heartbeat heals it. Env: `APPWRITE_API_KEY`, `MATCH_CACHE_BUCKET_ID`.
- `match-commentary-prune` — daily at 03:00; deletes `match-commentary` rows older than `COMMENTARY_RETENTION_DAYS` (14). Nothing else deletes them, and the table grows one row per ball of every match — it had reached ~500k rows before this existed. Pages oldest-first on the built-in `$createdAt` (always orderable, so no user index is needed) and stops at the first page whose oldest row is inside retention. Bounded to `BATCH` 100 × `COMMENTARY_PRUNE_MAX_PASSES` 100 per run so one run stays inside the 300s timeout; a large backlog is meant to drain over several days, and raising `COMMENTARY_PRUNE_MAX_PASSES` temporarily is the way to drain it faster. A read-budget 402 aborts and returns 402 with the partial `deleted` count rather than failing silently.
- `win-probability` — on-demand; calculates real-time win probability
- `train-win-probability` — scheduled; retrains win-probability model from match history

Endpoint/projectId are auto-injected by the runtime — only `APPWRITE_API_KEY` and DB/collection IDs need manual env vars.

**No dependency injection framework.** ViewModels are constructed manually; managers use `object` singletons initialized in `BouncerApp.onCreate`.

**Error handling:** `AppResult<T>` monad (`data/AppResult.kt`) wraps success/failure. Use `.fold(onSuccess, onFailure)` in ViewModels; propagate via `return AppResult.Error(e)` in repositories. Don't unwrap with `!!`.

**Navigation:** Single `BouncerNavHost` with Compose Navigation. Deep link entry via `app://bouncer/match/{matchId}` — scheme `app`, host `bouncer`, as registered in `AndroidManifest.xml` and parsed by `handleExternalDeepLink` in `ui/MainAppContainer.kt`. A `bouncer://` URI does not resolve.

**Device-side commentary gate:** `LiveActivityViewModel.holdCommentary`/`releaseCommentary` subscribe to the `match-commentary` rows for a held match (and send the 90s presence ping); with no holder nothing is subscribed. `IntelligenceFeedScreenV2` is the only holder, via `probabilityEvents`. It used to run a whole commentary pipeline on the device instead — `LiveCommentaryManager` re-scraped Cricbuzz twice and Cricinfo once per held match on every 6s poll and called `bouncer-generate-commentary` for a line the server had already stored; before the hold existed it did that for every live match (~150 executions/minute, starving player search). Events are taken monotonically on `sort_key`, because the table channel also delivers the server's rewrites of older rows.

**Live data flow:** score polling (`ScorePollingWorker` + FCM, `LiveActivityViewModel` on the Live tab) → UI via StateFlow. Commentary is written server-side by `bouncer-match-sync` to `match-commentary` and read through `AppwriteMatchCommentaryClient` (match detail and the Activity feed); no commentary is generated on the device. `AICacheService` warms cache from Appwrite Realtime events.

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
the row `bouncer-match-sync` already writes every minute — so a tracked match costs one billed read
at first paint and none after. A promoted notification does **not** need a foreground service; the
FGS only keeps the process alive for the websocket frames, which is why `onTimeout` (the API 34+
six-hour cumulative `dataSync` cap, which a Test day exceeds) can `STOP_FOREGROUND_DETACH` and
leave every card posted. Subscribing to a match is the only control — there is deliberately no
second toggle, and the settings row exists only to surface the one state the user cannot fix from
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

**`probabilityEvents` classifies from prose, and always has.** `classifyProbabilityEvent` used to
prefer a structural read of the scraped recent-balls array — sound reasoning, since a "W" there is
the scorer's own record rather than an interpretation of prose. That branch was never once
reachable: the array's only writer sat inside the dead live-notification path, behind a
`notificationManager ?: return` whose setter had no caller anywhere in the app. Restoring it needs
a ball source `LiveActivityViewModel` does not have — the match-list scrape sets `recentBalls` to
null and only the per-match detail scrape carries it, which on the six-second Live poll would be
one scrape per live match per tick.

**AI cache (3-tier):** `MemoryFifoCache` (256 entries; FIFO eviction, not LRU — `utils/BoundedLruCache` is the real LRU) → Room `ai_commentary_cache` → Appwrite `ai-commentary-cache` table. All four commentary functions (`generateBallCommentary`, `fuse`, `streamCommentary`, `summarizeOversWithAI`) check cache before calling any AI provider. Completed matches use **permanent TTL** (`Long.MAX_VALUE` in Room, `expires_at = "2099-01-01T00:00:00.000Z"` in Appwrite) — `isMatchComplete` flag threads from ViewModels → `LiveCommentaryManager` → `CommentaryFusionService` → `AICacheService`. Never set permanent=true for live matches.

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
  magenta against lime. Dark-mode text greys are neutral, not cream/tan. Event colours
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
so it is on screen for every tab — Overview used to open with the status, CRR/RRR, recent balls and
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

**There is no win-probability history on the server.** `live-winprob` and the detail payload are both
snapshots. `MatchDetailViewModel` accumulates the swing series on the device as the probability
moves, session-scoped. It used to come from `RetrofitClient.localApi` — `http://10.0.2.2:8000/`, an
emulator-only dev mock — so on any real device it was a guaranteed CLEARTEXT failure per match open
and the chart never rendered. That client is deleted; do not reintroduce a localhost API.

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

**Player profile cache (global):** `PlayerCacheWorker` checks `AppwritePlayerProfilesClient` before scraping Cricbuzz. First device that scrapes a player pushes to Appwrite (`player-profiles` table, `bouncer-db`); all subsequent devices get a cache hit. Row ID = `entity.playerId` (Room format: `virat_kohli`).

**Appwrite project:** single database `bouncer-db`. Known tables: `ai-commentary-cache`, `highlights`, `highlights-meta`, `user-favourites`, `player-profiles`, `match-commentary`, `match-live-detail`, `match-scorecard`.

**`match-commentary` table:** written by `bouncer-match-sync` (every minute). Columns: `match_id`, `innings`, `ball`, `sort_key` (timestamp, used for ordering), `commentary` (AI-rewritten line), `ai_generated` (boolean, default true), `created_at`. Read by `AppwriteMatchCommentaryClient` — newest-first 150 rows ordered by `sort_key` desc, with `getOlderThan(sortKey)` paging further back on a reader's tap; Realtime subscription emits `CommentaryEvent` on create. **`ai_generated = false` means the row holds raw Cricbuzz text**: either the provider chain returned nothing, or the delivery was past the run's LLM budget — which since 2026-08-27 is the common case, because storage is unconditional and rewriting is not. The device does not read the column — it exists so "is the stored commentary actually AI?" is answerable from the storage layer, which it was not when a dead chain and a working one produced identical-looking rows.

**`match-live-detail` table:** written by `bouncer-match-sync` (every minute). One row per match, upserted. Columns: `match_id`, `payload` (JSON `MatchLiveDetailPayload`), `updated_at`. Row ID = sanitized `matchId`. Read by `AppwriteMatchDetailClient`; Realtime emits `DetailEvent` on create/update. Both clients use `appwriteAiCacheDbId` as their DB ID.

**`match-scorecard` table:** written by `bouncer-match-sync` alongside the detail row. One row per match, row ID = sanitized `matchId`, columns `match_id`, `scorecard` (JSON `Scorecard`), `updated_at`. **No index** — read by `AppwriteMatchScorecardClient.getForMatch` (one `getRow` for first paint) then kept fresh by `observeMatch`, subscribed for the life of the detail screen. Exists because the scorecard measured 85–93% of the detail payload (4–9 KB of 4–9 KB) and was pushed to every subscriber of that row every minute; on its own row the live card's ~650-byte updates travel alone.

**`bouncer-match-sync` Appwrite function:** scheduled `* * * * *`. Reads `live-matches.json` from `cricket-stats` bucket (written by `bouncer-poll-live-matches`); if file absent, returns 200 `{skipped:true}` (not an error). For each live match: fetches Cricbuzz live HTML + scorecard → upserts `match-live-detail` → writes EVERY new delivery to `match-commentary` → rewrites the oldest `commentaryCapFor` of them with the LLM. **Storage is not gated on a viewer; rewriting is.** It used to be the other way round, and a match nobody had open stored one ball per 45 minutes — so the commentary tab showed a sample of the match (overs missing outright, one boundary in an innings, a "recent" line hours old) rather than its commentary. `commentaryMatchSet` caps storage at 16 matches per run, viewers first; `commentaryCapFor` is 10 / 1 / 0 rewrites for viewed / slow-tier / idle. The watermark seeds from the newest `match-commentary` row (`limit(1)`, one billed read per cold container) and is no longer persisted to the detail row, because most live matches have no detail row. `innings` is stamped **per commentary item** from Cricbuzz's `inningsId`, falling back to the miniscore and then to the scorecard length — the old write-time stamp mislabelled an innings' tail as the next innings. Env vars: `APPWRITE_AI_CACHE_DB_ID`, `MATCH_COMMENTARY_COL_ID`, `MATCH_LIVE_DETAIL_COL_ID`, `MATCH_CACHE_BUCKET_ID`, `APPWRITE_API_KEY`, `GROQ_API_KEY` (+ fallbacks), `SENTRY_DSN` (optional — unset disables server-side reporting), `PROBE_SOURCES` (optional).

**Server-side observability (`match-sync/src/observability.js`):** `@sentry/node`, initialized per container from `SENTRY_DSN` and flushed before every return (the container freezes on return, so an unflushed queue is lost). Reports: raw-text passthrough with the per-provider reasons from `generateLine`'s `attempts`, commentary write failures, a viewer-gated match that produced nothing, an unreadable `active-viewers` file (a 404 is normal and stays quiet), and read-budget 402s. Setting `PROBE_SOURCES=1` turns one execution into an upstream reachability probe (status + bytes + markup markers for Cricinfo and Cricbuzz, from inside the Appwrite container) and returns without syncing — that is the only way to answer whether Cricinfo is reachable server-side, which is a different question from whether it is reachable from a laptop or a phone.

**The slow tier is an LLM budget, not a fetch cadence — and the real ball-loss mechanism is the
upstream window.** `handleRun` does `Promise.allSettled(allLive.map(...))`: **every** live match has
its Cricbuzz live page fetched and every new delivery stored, on every one-minute run.
`commentaryMatchSet` caps storage at 16 but `MAX_MATCHES` is 12, so that slice never drops anything;
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

**`MAX_MATCHES = 12` silently drops live matches, and hides them from the viewer gate.** `allLive` is
`matches.slice(0, MAX_MATCHES)` in Cricbuzz's list order, and anything past position 12 gets no
commentary, no detail row and no win probability. Worse, `live` (the viewer set) is derived **after**
the slice, so a viewer watching match 13 is invisible to the viewer gate entirely. The run log used
to print only `allLive.length`, which can never exceed the cap — the cap censored the one number that
would reveal it. It now logs the pre-slice count too and reports to Sentry when it binds, at `error`
level if any dropped match had a viewer. The cap itself is unchanged; raising it is read-budget
arithmetic, not a one-line edit.

**Viewer gate:** `MatchDetailViewModel` pings `bouncer-match-presence` every 90s while a match is open and **not over** — gated on `isMatchOver(match, liveStatus)`, never on `match.isLive`, which is a frozen list snapshot (same staleness class as `5b86664`). A dead heartbeat means every sync logs "0 with viewers" and the tab falls back to raw Cricbuzz text — but it does **not** stop deliveries being stored: the slow tier gates the LLM rewrite budget alone (see below); `MatchPresenceReporter.ping` therefore reports failures to Sentry instead of swallowing them.

**Appwrite Storage bucket `cricket-stats`:** read-only from Android via `AppwriteClient.downloadFile(bucketId, fileId)`; written only by Appwrite cloud functions. It is **not** four files — measured 2026-09-12 it holds fifteen, and most are inter-function state rather than app data:

| File | Owner | What it is |
|---|---|---|
| `team-stats`, `h2h-stats` | `train-win-probability` | win-rate model; H2H for 650+ team pairs |
| `active-series` | `poll-live-matches` | active **and archived** series, read by `SeriesSyncWorker` |
| `live-matches`, `recent-matches`, `upcoming-matches`, `live-count`, `poll-state` | `poll-live-matches` | the match lists `match-sync` runs off, plus its own cursor//hash state |
| `live-winprob`, `slow-sync-state` | `match-sync` | published probabilities; the per-match slow-tier clock |
| `active-viewers` | `match-presence` | `{matchId: epochMs}`, the viewer gate |
| `player-index` | `player-search` | name → Cricbuzz profile id |
| `news-cache`, `news-body-{storyId}` | `news-sync` | headline index (4h TTL); one article body per opened story |
| `rate-limit-offenders` | — | legacy, unread |

**Deleting from this bucket is how you break a different feature silently, so select by `startsWith("$id", …)` and never by `search`.** Storage search is fuzzy: measured against the live bucket, `search=news-body` also returns **`news-cache`**, while `startsWith($id, "news-body-")` returns exactly the bodies. `bouncer-news-sync` prunes `news-body-*` past `BODY_RETENTION_MS` (14 days) on its **headline-refresh path only** — that path is already capped at ~6 runs/day by the 4h TTL, so the sweep needs no schedule of its own — and re-checks the prefix on every candidate before deleting, because a query-semantics surprise must not be able to become a deletion.

**Room DB:** currently at schema version 15. v14→v15 dropped the `commentary` table, which nothing ever inserted into (live commentary is the `match-commentary` Appwrite table). The `matches` table is in the same state — read by highlight fuzzy-matching, written by nothing — so highlights are always stored unmatched; that is a missing write, not dead code. New migrations: add `MIGRATION_N_(N+1)` in `BouncerDatabase.kt` + bump `version` in `@Database`.

**No FTS.** `player_fts`, `series_fts` and `historical_match_fts` were dropped in v12→v13, along with `historical_match_cache` (nothing ever wrote to it). The FTS entities were `@Fts4` with **no `contentEntity`**, so Room created them with no sync triggers; the triggers that populated them existed only inside `MIGRATION_1_2`, which a fresh install never runs. Every install created after schema v1 was searching a permanently empty index, and only v1-upgraded devices worked — which made it look device-specific. `SeriesCacheDao`/`PlayerCacheDao` now use `LIKE` over the backing tables (a few hundred rows; FTS bought nothing) and that is also what makes acronym expansion work. Do not reintroduce an FTS index without `contentEntity`.

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
