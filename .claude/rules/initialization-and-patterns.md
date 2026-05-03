# Initialization & Non-Obvious Patterns

## App Startup Sequence (BouncerApp.kt)

Order matters for dependency + error isolation:

1. **Global exception handler** — Installed before anything else. Captures all uncaught exceptions to Sentry, then delegates to default handler
2. **TeamLogoManager.initialize()** — Team branding assets
3. **TeamAcronymManager.initialize()** — Team shorthand text
4. **FavouriteTeamsManager.initialize()** — Restore persisted user favorites
5. **MatchCacheService.initDiskCache()** — Initialize Room database + disk cache
6. **CoilImageLoader** — Configure HTTP client + image loader (depends on cache service)
7. **ScorePollingWorker.enqueuePeriodicWork()** — Schedule background score updates via WorkManager

Each step wrapped in `initializeSafely()` block (try-catch + Sentry breadcrumb). If one manager fails, others still initialize.

## Core Patterns

**TabTemplate UI:** Live/Upcoming/Recent tabs for match data (HomeScreenV2)

**WidgetManager:** Syncs app widget state with FCM score_update messages + WorkManager polling

**CommentaryFusionService:** Processes live commentary text → streams to OpenRouter API → returns LLM verdict on key moments

**Deep linking:** Match details accessible via `bouncer://match/{matchId}` intent URLs

**MCP integration:** Model Context Protocol handlers in `/mcp` folder for external tool integration

**Global exception handler:** Prevents silent crashes. All exceptions → Sentry breadcrumb + normal exception flow

## Why This Order

- Exception handler first: catches initialization failures
- Logo/Acronym managers early: lightweight, no I/O
- MatchCacheService before CoilImageLoader: HTTP client config depends on cache directory setup
- ScorePollingWorker last: depends on all services ready

## Failure Isolation

`initializeSafely()` wraps each step so a failed manager doesn't cascade. Sentry breadcrumb logged for each init attempt, making startup issues debuggable.

```kotlin
initializeSafely("ManagerName") { ManagerName.initialize(this) }
// Logs breadcrumb on success or failure. Exceptions don't propagate up.
```
