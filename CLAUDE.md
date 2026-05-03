# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# Bouncer Android App

Cricket live-scoring, match analytics, and team management app built in Kotlin/Compose.

## Quick Start

**Setup (first time):**
```bash
cp local.properties.template local.properties  # Edit with real keys
# OR set env vars: OPENROUTER_API_KEY, RAPIDAPI_KEY, SENTRY_AUTH_TOKEN
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
├── ui/                 # Compose screens and components
├── viewmodels/         # MVVM ViewModels
├── services/           # Business logic (FCM, analytics, CommentaryFusion)
├── managers/           # Feature managers (Widget, TeamLogo, Analytics)
├── network/            # API clients + Cricbuzz scraper
├── data/               # Room DB, repositories, caching
├── models/             # Data models and entities
├── analysis/           # Cricket analysis + ML models
├── auto/               # Automation features
├── mcp/                # Model Context Protocol handlers
├── utils/              # Utility functions
├── monitoring/         # Sentry integration
└── logic/              # Core game logic
```

## Key Dependencies

- **Jetpack Compose**: UI framework
- **Sentry**: Error monitoring + performance tracking
- **Firebase Cloud Messaging (FCM)**: Push notifications
- **Room**: Offline cache + local DB
- **Retrofit + OkHttp**: REST APIs for Cricbuzz data
- **Kotlin Symbol Processing (KSP)**: Code generation

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

For large data processing, log analysis, or API exploration:
- `ctx_execute`: Run code against indexed data
- `ctx_execute_file`: Analyze large logs/data files
- `ctx_batch_execute`: Multiple commands in one call (faster)
- `ctx_search`: Query indexed content
