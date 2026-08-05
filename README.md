# Bouncer — Cricket Intelligence App

A live cricket Android app with AI-powered ball commentary, machine learning win probability, real-time scores, smart notifications, and home screen widgets.

---

## Features

**Live Match Coverage**
- Live scores, ball-by-ball commentary, and scorecards sourced from CricBuzz and ESPNCricinfo
- Match flow: DRS reviews, powerplays, and impact substitutions
- Pre-match pitch reports and team news
- Toss result, umpire details, batting partnerships

**AI Ball Commentary**
- Per-ball AI commentary with automatic failover across Groq → Cerebras → SambaNova (one model each, not one model everywhere)
- Two-source commentary fusion with a hallucination verification pass (temp=0.0)
- Commentary shared across all users via Appwrite cache (near-zero latency after first user)

**Win Probability Engine**
- Custom multi-layer neural network (pure Kotlin, no TensorFlow Lite) trained on Cricsheet historical data
- 35 input features: wickets lost, run rates, recent momentum, team strength ratios, pressure metrics
- Updates after every ball; falls back to Monte Carlo simulation while model is warming up

**Smart Notifications** (5 channels)
- Wicket falls (dismissal type, bowler, runs scored)
- Boundaries (fours and sixes with configurable vibration patterns)
- Milestones: centuries, half-centuries, 5-wicket hauls
- Match status changes (start, end, toss)
- Per-match mute and global quiet hours

**Home Screen Widgets**
- Live match score widget (2×1, 3×2, 4×2 sizes) via Jetpack Glance — score, run rate, team names
- Countdown widget: time remaining to next scheduled match, updated every minute
- Lock screen widget (Android 14+)

**Player Intelligence**
- Player profiles with career stats and recent form from Cricbuzz
- Head-to-head player comparison
- Squad rosters for every team

**Series & Standings**
- IPL, international, and bilateral series support
- Real-time points tables and fixture lists

**Match Highlights**
- Video highlights sourced via Highlightly API, fuzzy-matched to matches by team name

**Android Auto**
- Match info on car displays via Android CarApp library

**Offline-Capable**
- Room SQLite with 7 entity types: matches, commentary, highlights, AI cache, series, player profiles, historical data
- WorkManager background sync: series sync (24 h), score polling, player cache, ML model training

---

## Architecture

```
CricBuzz scraper     ──┐
ESPNCricinfo scraper ──┤
Points Table scraper ──┤──► Background Workers ──► Room DB ──► ViewModels ──► Compose UI
Series scraper       ──┤         (WorkManager)        ↕
Squad scraper        ──┘                           Appwrite
                                                  (AI cache,
PlayerProfile scraper ─────────────────────────►  favourites,
Highlightly API      ─────────────────────────►   highlights)
AI providers
  Groq / Cerebras / SambaNova ─────────────────► AppwriteAICache
```

**Key packages** (all under `app/src/main/kotlin/com/tanush/bouncer/`):

| Package | Responsibility |
|---------|---------------|
| `data/remote/` | Appwrite clients (`AppwriteAICache`, `AppwriteFavouritesSync`, `AppwriteHighlightsClient`), scraper HTTP clients |
| `data/local/` | Room database, 7 DAOs, 7 entity classes |
| `analysis/ml/` | Neural network (`ScorePredictionModel`), `WinProbabilityCalculator` |
| `services/` | `CommentaryFusionService`, `LiveNotificationManager`, `LiveCommentaryManager` |
| `workers/` | `SeriesSyncWorker`, `ScorePollingWorker`, `PlayerCacheWorker`, `CricsheetTrainingWorker` |
| `ui/screens/` | ~26 Jetpack Compose screens |
| `ui/widgets/` | Glance widget implementations |

---

## Build

### Prerequisites

- JDK 17
- Android Studio Iguana or newer
- Android SDK with `compileSdk 36`

### 1. Clone

```bash
git clone <repo-url>
cd android
```

### 2. Create local.properties

```bash
cp local.properties.template local.properties
```

Fill in all values — see the table below.

### 3. Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew bundleRelease        # signed release AAB (requires keystore keys)
```

> **macOS note:** Build outputs are redirected to `~/Library/Caches/bouncer-android/app` to prevent iCloud sync from invalidating Gradle's incremental build cache.

---

## local.properties Keys

| Key | Description | Where to get it |
|-----|-------------|----------------|
| `RAPIDAPI_KEY` | Cricket data API (TeamDirectory, news) | RapidAPI → cricket-api-free-data |
| `GROQ_API_KEY` | `llama-3.3-70b-versatile` (primary AI provider) | console.groq.com |
| `CEREBRAS_API_KEY` | `gemma-4-31b` (failover #1) | cloud.cerebras.ai |
| `SAMBANOVA_API_KEY` | `DeepSeek-V3.1` (failover #2) | cloud.sambanova.ai |
| `APPWRITE_ENDPOINT` | Appwrite server base URL | Appwrite Console → Project settings |
| `APPWRITE_PROJECT_ID` | Appwrite project ID | Appwrite Console → Project settings |
| `APPWRITE_AI_CACHE_DB_ID` | Database for AI commentary cache | Appwrite Console → Databases |
| `APPWRITE_AI_CACHE_COL_ID` | Collection for AI commentary cache | Appwrite Console → Collections |
| `APPWRITE_FAVOURITES_COL_ID` | Collection for user favourite teams (same DB as AI cache) | Appwrite Console |
| `APPWRITE_HIGHLIGHTS_DB_ID` | Database for match highlights | Appwrite Console |
| `APPWRITE_HIGHLIGHTS_COL_ID` | Collection for match highlights | Appwrite Console |
| `APPWRITE_PLAYER_FORMS_DB_ID` | Database for player form data | Appwrite Console |
| `APPWRITE_PLAYER_FORMS_COL_ID` | Collection for player form data | Appwrite Console |
| `APPWRITE_PLAYER_PROFILES_COL_ID` | Collection for player profiles | Appwrite Console |
| `HIGHLIGHTLY_API_KEY` | Highlights matching API | highlightly.com |
| `SENTRY_DSN` | Sentry project DSN | sentry.io → Project settings |
| `SENTRY_USER_AUTH_TOKEN` | Sentry auth token (release build ProGuard upload) | sentry.io → User settings → Auth Tokens |
| `KEYSTORE_PASSWORD` | Release keystore password | Your password manager |
| `KEY_ALIAS` | Release key alias | `bouncer` |
| `KEY_PASSWORD` | Release key password | Your password manager |

---

## Generating the Release Keystore

Run once from the repo root. Store both passwords in a password manager — they cannot be recovered.

```bash
keytool -genkey -v \
  -keystore app/bouncer-release.keystore \
  -alias bouncer \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Bouncer, OU=Android, O=Tanush Malhotra, L=Singapore, C=SG" \
  -storepass YOUR_KEYSTORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD
```

> `app/bouncer-release.keystore` is gitignored (`*.keystore` in `.gitignore`) — it will never be committed.

Encode for the `KEYSTORE_BASE64` GitHub secret:

```bash
base64 -i app/bouncer-release.keystore | pbcopy   # macOS — copies to clipboard
```

---

## GitHub Secrets Setup

Go to repo **Settings → Secrets and variables → Actions** and add:

### Signing

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | Output of `base64 -i app/bouncer-release.keystore` |
| `KEYSTORE_PASSWORD` | Keystore store password |
| `KEY_ALIAS` | `bouncer` |
| `KEY_PASSWORD` | Key password (same as `KEYSTORE_PASSWORD` for PKCS12) |

### Play Store

| Secret | Value |
|--------|-------|
| `PLAY_SERVICE_ACCOUNT_JSON` | Full JSON key file content for `bouncer-ci@golden-object-498303-q9.iam.gserviceaccount.com` — the service account must have **Release Manager** role in Play Console |

### API Keys

| Secret | Matches local.properties key |
|--------|------------------------------|
| `RAPIDAPI_KEY` | `RAPIDAPI_KEY` |
| `GROQ_API_KEY` | `GROQ_API_KEY` |
| `CEREBRAS_API_KEY` | `CEREBRAS_API_KEY` |
| `SAMBANOVA_API_KEY` | `SAMBANOVA_API_KEY` |
| `APPWRITE_ENDPOINT` | `APPWRITE_ENDPOINT` |
| `APPWRITE_PROJECT_ID` | `APPWRITE_PROJECT_ID` |
| `APPWRITE_AI_CACHE_DB_ID` | `APPWRITE_AI_CACHE_DB_ID` |
| `APPWRITE_AI_CACHE_COL_ID` | `APPWRITE_AI_CACHE_COL_ID` |
| `APPWRITE_FAVOURITES_COL_ID` | `APPWRITE_FAVOURITES_COL_ID` |
| `HIGHLIGHTLY_API_KEY` | `HIGHLIGHTLY_API_KEY` |
| `APPWRITE_HIGHLIGHTS_DB_ID` | `APPWRITE_HIGHLIGHTS_DB_ID` |
| `APPWRITE_HIGHLIGHTS_COL_ID` | `APPWRITE_HIGHLIGHTS_COL_ID` |
| `APPWRITE_PLAYER_FORMS_DB_ID` | `APPWRITE_PLAYER_FORMS_DB_ID` |
| `APPWRITE_PLAYER_FORMS_COL_ID` | `APPWRITE_PLAYER_FORMS_COL_ID` |
| `APPWRITE_PLAYER_PROFILES_COL_ID` | `APPWRITE_PLAYER_PROFILES_COL_ID` |

### Error Tracking

| Secret | Value |
|--------|-------|
| `SENTRY_AUTH_TOKEN` | Same value as `SENTRY_USER_AUTH_TOKEN` in local.properties |

---

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on every push to `main` and every pull request:

| Job | Trigger | What it does |
|-----|---------|-------------|
| `test` | all pushes / PRs | `./gradlew test` — unit tests; uploads results on failure |
| `lint` | all pushes / PRs | `./gradlew lint` — always uploads HTML report |
| `build` | all pushes / PRs (after `test` passes) | `./gradlew bundleRelease` — uploads signed AAB artifact |
| `publish` | push to `main` only | Uploads AAB to Play Store internal track via service account |

> **First publish:** `versionCode` is `1`. Play Store requires it to increment on each upload. Bump it in `app/build.gradle.kts` before pushing again.
