# API Keys & Local Setup

## Required Keys

Four keys needed for full functionality:

1. **OPENROUTER_API_KEY** — OpenRouter LLM API (for CommentaryFusionService AI insights)
2. **RAPIDAPI_KEY** — RapidAPI for Cricbuzz scraper + cricket data APIs
3. **SENTRY_USER_AUTH_TOKEN** — Sentry CLI auth (source context uploads in release builds)
4. **SENTRY_DSN** — Sentry ingest endpoint (error/performance tracking)

## Loading Mechanism

Build-time configuration (app/build.gradle.kts):

```kotlin
buildConfigField("String", "OPENROUTER_API_KEY", 
    "\"${localProperties.getProperty("OPENROUTER_API_KEY") 
       ?: System.getenv("OPENROUTER_API_KEY") ?: ""}\"")
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
export OPENROUTER_API_KEY=sk-or-v1-...
export RAPIDAPI_KEY=...
export SENTRY_USER_AUTH_TOKEN=...
export SENTRY_DSN=...
```

**Option 3: Both (env vars override local.properties)**

## Build Behavior

- Build succeeds with empty keys (graceful degradation)
- AI features (AiInsightsDrsScreen, CommentaryFusionService) fail at runtime if keys missing
- Firebase/FCM requires google-services.json from Firebase Console (separate from keys above)

## Git Safety

`local.properties` is gitignored. Never commit real keys.
