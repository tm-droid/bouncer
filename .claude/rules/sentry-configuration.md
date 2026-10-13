---
paths:
  - "app/build.gradle.kts"
  - "app/src/main/kotlin/**/Sentry*.kt"
  - "app/src/main/kotlin/**/Diagnostics.kt"
  - "sentry.properties"
---

# Sentry Configuration

## Plugin Settings (app/build.gradle.kts)

**Telemetry disabled:** Prevents sentry-cli invocation at Gradle config time (resource extraction failure on Windows crashed every build)

**Debug variant ignored:** Skips source context collection + ProGuard uploads
- Why: Running these on iCloud-synced folders causes 30+ minute hangs

**Release variant:** Uploads source context + ProGuard mappings for better error resolution

**Performance instrumentation:** Enabled for:
- DATABASE (Room query tracing)
- FILE_IO (slow I/O detection)
- OKHTTP (network timing)
- COMPOSE disabled: causes duplicate-class DEX errors with androidx.activity.compose.R (AGP 9 + activity-compose 1.12.x conflict)

**Sentry version:** Auto-installed at 8.43.2 (kept current for Session Replay / Logs / Metrics features)

## Runtime Initialization (BouncerApp.kt)

**Configured via SentryOptionsConfig.kt**

**Manifest placeholders:**
- `sentryDsn`: From local.properties or default
- `sentryEnvironment`: "debug" in debug build, "production" in release
- `sentryDebug`: "true" in debug (verbose logging), "false" in release

**App startup:**
1. Global uncaught-exception handler installed (captures to Sentry before delegate)
2. Breadcrumb added: "BouncerApp.onCreate started"
3. Managers initialized with safety wrappers
4. Final breadcrumb: "BouncerApp.onCreate completed"

**Metrics tracked:**
- `app_start`: Count of app initializations
- `app_init_components`: Gauge of initialized components

## Breadcrumb & Logging

Sentry captures breadcrumbs at lifecycle points (onCreate, initialization blocks). Exceptions from manager init wrapped in `initializeSafely()` to prevent cascading startup failures.

**The Sentry exception filter never sees a `captureMessage`.** `shouldDropEvent` reads `event.exceptions`, and every `sentryCaptureThrottled` / `logSilentFailure` report is a message event, so an `UnknownHostException` inside one sailed through. Offline and already-reported causes are now skipped at each reporting site with `HttpClientProvider.isConnectivityException` — do not add a new message-level report without the same check. A `SentryHttpClientException` from a host other than `*.appwrite.io` is dropped: the Gradle-injected OkHttp integration captures every 5xx on every host and its targets cannot be set from options.
