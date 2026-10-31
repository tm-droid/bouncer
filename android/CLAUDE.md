# CLAUDE.md

# Bouncer Android App

Cricket live-scoring, match analytics, and team management app built in Kotlin/Compose (Appwrite backend, Sentry).

## Bug Fixing Philosophy
- NEVER suppress, filter, or hide errors (e.g., Sentry filters, try/catch swallowing) without first identifying and fixing the root cause.
- When a bug is reported across multiple items (chapters, volumes, files), search for ALL occurrences before claiming a fix is complete.

## Code Tasks
- When asked to 'look at the app' or audit features, INSPECT THE CODEBASE (Read/Grep) rather than launching, screenshotting, or building mocked/fake screens.
- Never substitute mocked UI for real screens driven by the app's actual ViewModels.

## Audits & Fixes
- For audit-then-implement requests, diagnose the true root cause before applying fixes (e.g. trace transitive/build-classpath deps, not just app deps) and verify each fix actually resolves the issue.

## Other rules
- V2 naming: active screens carry `V2` suffix; do not recreate deleted V1 files (see docs/claude/code-style.md).
- Never delete manifest-registered classes or WorkManager workers on grep evidence alone; do not reintroduce proguard keeps for models/DTOs or the blanket `-keep class com.tanush.bouncer.** { *; }`.
- Appwrite: prefer the MCP server over CLI push; verify env vars (RAPIDAPI_KEY etc.) are set, don't assume.
- detekt `maxIssues: 0`: new/modified code must be clean; never re-baseline touched files.

## Architecture & specs

Full architecture, dependencies, and spec index: `docs/architecture.md`. Read it when touching cross-module structure, adding dependencies, or before large features. Feature-level rules auto-load from `.claude/rules/` by path.

## Quick Start

```bash
cp local.properties.template local.properties  # Fill in secret keys (RAPIDAPI_KEY, GROQ_API_KEY, etc.)
# Most non-secret Appwrite IDs are pre-filled — only secret API keys need manual entry.
./gradlew build                  # Full build
./gradlew installDebug           # Install to emulator/device
./gradlew assemble               # Dev build (faster, skips Sentry transforms)
./gradlew test                   # Run all unit tests
./gradlew testDebugUnitTest --tests "*MatchUtilsTest"  # Single test (--tests invalid on aggregate `test`)
./gradlew clean build            # Clean build (if cache corrupted)
./gradlew detekt "-Dorg.gradle.java.home=$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home"  # needs JDK <=21
./gradlew assembleDebug -PenableComposeCompilerReports  # Compose metrics
./gradlew lintDebug              # run with detekt before every push
```

## Git & Deploy
- "push" = `git push` only. Never open a PR unless explicitly asked.
- Run clean build, tests, `lintDebug` and detekt before pushing; never push a broken build.
- Commit touching `app/src/main/` must update `distribution/whatsnew/whatsnew-en-US` (<=500 chars, user-facing).
- When git commands hang in this sandbox, fall back to the GitHub API.
- Before force-pushing, check the SHA matches the intended fix.
- After deploys, verify the change is actually live (hardcoded dates, cache, hydration).

## Directory map
`app/` (Kotlin/Compose app), repo-root `../appwrite/` (shared functions + tools), `distribution/` (Play whatsnew), `docs/`, `.claude/rules/` (feature rules, auto-load by path).

## Reference docs
- docs/architecture.md — read when touching cross-module structure, adding dependencies, or before large features.
- docs/claude/git-ci.md — read when pushing, changing CI (`ci.yml`), release notes, force-update, or Play upload.
- docs/claude/build-tooling.md — read when running detekt or release builds (JDK, sentry-cli).
- docs/claude/env-secrets.md — read when touching Appwrite queries/config or env vars.
- docs/claude/code-style.md — read when writing code or deleting apparently dead code.
- docs/claude/skills.md — read when choosing a skill/workflow for a task.

## Shared repository

Backend functions and GitHub workflows live at the monorepo root (`../appwrite/`, `../.github/` when working here). Public Appwrite defaults live in `../shared/backend.json`; local.properties contains machine/signing settings and optional local public overrides. Read root AGENTS.md and docs/shared-backend.md for cross-platform changes.
