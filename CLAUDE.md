# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# Bouncer Android App

Cricket live-scoring, match analytics, and team management app built in Kotlin/Compose.

## Quick Start

**Setup (first time):**
```bash
cp local.properties.template local.properties  # Edit with real keys — see template for full key list
```

**Build & run:**
```bash
./gradlew build                  # Full build
./gradlew installDebug           # Install to emulator/device
./gradlew assemble               # Dev build (faster, skips Sentry transforms)
./gradlew test                   # Run all unit tests
./gradlew test --tests "*MatchUtilsTest"  # Run single test
./gradlew clean build            # Clean build (if cache corrupted)
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
├── services/           # FCM, CommentaryFusion, LiveCommentary, WebSocket, Workers
├── managers/           # Singleton managers (TeamLogo, Acronym, Favourites, Widget)
├── network/            # Retrofit + Jsoup scrapers (CricBuzz, CricInfo, Series, Squad)
├── data/               # Room DB, repositories, AppResult<T> monad
│   └── remote/         # Appwrite sync objects (AppwriteAICache, AppwriteFavouritesSync)
├── models/             # Data models and entities
├── analysis/           # Cricket analysis + ML models (score prediction, win probability)
├── auto/               # Android Auto (BouncerCarAppService)
├── mcp/                # Model Context Protocol handlers
├── utils/              # Utility functions
├── monitoring/         # Sentry + SentryAiSpans
└── logic/              # Match simulation + game logic
```

**No dependency injection framework.** ViewModels are constructed manually; managers use `object` singletons initialized in `BouncerApp.onCreate`.

**Error handling:** `AppResult<T>` monad (`data/AppResult.kt`) wraps success/failure. Use it for all repository return types.

**Navigation:** Single `BouncerNavHost` with Compose Navigation. Deep link entry via `bouncer://match/{matchId}`.

**Live data flow:** `LiveScoreWebSocketClient` → `LiveCommentaryManager` → `CommentaryFusionService` (OpenRouter, multi-model fallback) + `BallCommentaryService` (Groq → Cerebras → SambaNova per-ball) → UI via StateFlow.

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

## Common Tasks & Skills

| Task | Skill(s) |
|------|----------|
| Add feature | `superpowers:brainstorming` → `superpowers:writing-plans` → `superpowers:executing-plans` + `feature-dev:feature-dev` |
| Fix bug | `superpowers:systematic-debugging` + `sentry:seer` (if Sentry involved) |
| Code review | `code-review:code-review` |
| Refactor | `simplify` |
| Git operations | `commit-commands:commit` or `commit-commands:commit-push-pr` |
| Android testing | `android-dev MCP`: screenshots, UI dump, tap, swipe, logcat |
| Sentry issues | `sentry:sentry-workflow` → `sentry:seer` |
| Before shipping | `superpowers:verification-before-completion` → `superpowers:finishing-a-development-branch` |

## Code Style & Conventions

- **Kotlin**: when/is, sealed classes, extension functions
- **Compose**: Stateless components, modifiers for layout, remember/derivedStateOf for state
- **Naming**: camelCase for functions/variables, PascalCase for types
- **Comments**: Only for non-obvious logic (WHY, not WHAT)

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
