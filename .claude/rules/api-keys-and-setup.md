# API Keys & Local Setup

## Required Keys

**Secret keys** (must be provided locally — never commit):

1. **RAPIDAPI_KEY** — RapidAPI for Cricbuzz scraper + cricket data APIs
2. **GROQ_API_KEY** — Groq (primary per-ball commentary provider in BallCommentaryService)
3. **CEREBRAS_API_KEY** — Cerebras (fallback #1 per-ball commentary)
4. **SAMBANOVA_API_KEY** — SambaNova (fallback #2 per-ball commentary)
5. **HIGHLIGHTLY_API_KEY** — Highlightly API for cricket highlight clips
6. **SENTRY_USER_AUTH_TOKEN** — Sentry CLI auth (source context uploads in release builds)
7. **SENTRY_DSN** — Sentry ingest endpoint (error/performance tracking)

**Appwrite config** (pre-filled in template — values are non-secret):

8. **APPWRITE_ENDPOINT** — `https://sgp.cloud.appwrite.io/v1` (pre-filled)
9. **APPWRITE_PROJECT_ID** — `6a02f7f3000e6e100b19` (pre-filled)
10. **APPWRITE_AI_CACHE_DB_ID** — Appwrite database ID for AI commentary cache
11. **APPWRITE_AI_CACHE_COL_ID** — Table ID for AI cache rows
12. **APPWRITE_FAVOURITES_COL_ID** — Table ID for favourites sync (shares AI cache DB)
13. **APPWRITE_HIGHLIGHTS_DB_ID** — Appwrite database ID for highlights
14. **APPWRITE_HIGHLIGHTS_COL_ID** — Table ID for highlight rows

## Loading Mechanism

Build-time configuration (app/build.gradle.kts):

```kotlin
buildConfigField("String", "GROQ_API_KEY",
    "\"${localProperties.getProperty("GROQ_API_KEY") ?: System.getenv("GROQ_API_KEY") ?: ""}\"")
```

Precedence: `local.properties` > env var > empty string

## Setup Options

**Option 1: local.properties (recommended for local dev)**
```bash
cp local.properties.template local.properties
# Edit with real keys
```

**Option 2: Environment variables (CI/shared machines)**
```bash
export RAPIDAPI_KEY=...
export GROQ_API_KEY=...
export CEREBRAS_API_KEY=...
export SAMBANOVA_API_KEY=...
export SENTRY_USER_AUTH_TOKEN=...
export SENTRY_DSN=...
```

**Option 3: Both (env vars override local.properties)**

## Build Behavior

- Build succeeds with empty keys (graceful degradation)
- AI features (AiInsightsDrsScreen, CommentaryFusionService) fail at runtime if keys missing
- Firebase/FCM requires google-services.json from Firebase Console (separate from keys above)

## Runtime Key Access

All keys accessed at runtime via `ApiKeys` object (`ApiKeys.kt`), never `BuildConfig` directly.
Adding a new key: `buildConfigField` in `build.gradle.kts` → entry in `ApiKeys.kt` → placeholder in `local.properties.template`.

## Git Safety

`local.properties` is gitignored. Never commit real keys.
