---
paths:
  - "app/src/main/kotlin/**/BouncerApp.kt"
---

# Initialization & Non-Obvious Patterns

## App Startup Sequence (BouncerApp.kt)

Order matters for dependency + error isolation:

0. **Diagnostics.start()** — starts Sentry unless the user has switched diagnostics off
1. **Global exception handler** — Installed before anything else. Captures all uncaught exceptions to Sentry, then delegates to default handler
2. **TeamLogoManager.initialize()** — Team branding assets
3. **TeamAcronymManager.initialize()** — Team shorthand text
4. **FavouriteTeamsManager.initialize()** — Restore persisted user favorites
5. **MatchSubscriptionManager.initialize()** — FCM match subscription state (lightweight, no I/O)
6. **MatchCacheService.initDiskCache()** — Initialize Room database + disk cache, then **NewsDiskCache.initialize(cacheDir)**
7. **AppwriteClient.initialize()** — Creates SDK singleton; sets `isReady = false` if keys blank
8. **AppwriteClient.client?.ping()** — Fired on `Dispatchers.IO`; verifies connectivity
9. **AppwriteAICache.initialize()** — Reads DB/table IDs, validates readiness
10. **AppwriteHighlightsClient.initialize()** — Same pattern
11. **NotificationInboxManager.initialize()** — Local notification inbox state
12. **AppwriteRealtime subscriptions** — Registers highlights → Room upsert with `RealtimeLifecycleController`, which starts it on the first activity start (never in a background-only process). No AI-cache subscription.
13. **CoilImageLoader** — Configure HTTP client + image loader (depends on cache directory from step 6)
14. **ScorePollingWorker.enqueuePeriodicWork()** — Schedule background score updates via WorkManager

Each step wrapped in `initializeSafely()` block (try-catch + Sentry breadcrumb). If one manager fails, others still initialize.

## Core Patterns

**TabTemplate UI:** Live/Upcoming/Recent tabs for match data (HomeScreenV2)

**Widgets:** one snapshot and one writer (`WidgetSnapshotSource` → `WidgetSnapshotStore`), refreshed by `WidgetUpdateWorker`, which does nothing when no widget is placed. `WidgetManager` was deleted.

**Commentary generation is server-side only.** `bouncer-match-sync` writes every line into `match-commentary` and the device reads it. `CommentaryFusionService` — a second, complete pipeline that called `bouncer-generate-commentary` per ball — was deleted: nothing in the app ever constructed it, only its own tests referenced it, and it was still wired to the live function and its per-device rate limiter. Do not reintroduce a device-side generator.

**Deep linking:** Match details accessible via `app://bouncer/match/{matchId}` intent URLs (scheme `app`, host `bouncer`)

**MCP integration:** none. `mcp/` held a 13-line stub returning `"Echo: $message"` against `http://localhost:8080/mcp`, fronted by a DevTools screen that shipped in release; it was deleted rather than implemented. Do not recreate it as a placeholder.

**Global exception handler:** Prevents silent crashes. All exceptions → Sentry breadcrumb + normal exception flow

## Why This Order

- Exception handler first: catches initialization failures
- Logo/Acronym/MatchSubscription managers early: lightweight, no I/O
- MatchCacheService before Appwrite: `AppwriteHighlightsClient` Realtime callback writes to Room DAO, needs DB initialized first
- Appwrite inits before Realtime subscriptions: subscriptions reference already-initialized clients
- CoilImageLoader after MatchCacheService: HTTP client config depends on cache directory setup
- ScorePollingWorker last: depends on all services ready

## Failure Isolation

`initializeSafely()` wraps each step so a failed manager doesn't cascade. Sentry breadcrumb logged for each init attempt, making startup issues debuggable.

```kotlin
initializeSafely("ManagerName") { ManagerName.initialize(this) }
// Logs breadcrumb on success or failure. Exceptions don't propagate up.
```
