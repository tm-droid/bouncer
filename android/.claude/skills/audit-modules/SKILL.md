# Audit Modules

Dispatch parallel Explore agents — one per major module — to scan for deprecated or unsafe patterns, then implement fixes in the main thread.

## Modules to audit (in parallel)

- `ui/` — screens, components, widgets, navigation
- `network/` — Retrofit clients, Jsoup scrapers, DTOs
- `data/` — Room DB, repositories, Appwrite clients
- `services/` — Workers, FCM, commentary services
- `analysis/` — ML models, score prediction, win probability
- `managers/` — singleton managers (TeamLogo, FavouriteTeams, etc.)

## Patterns each agent checks for

- Raw `!!` (non-null assertion) — replace with safe call + fallback or early return
- Swallowed exceptions — empty `catch` blocks or `catch { /* ignored */ }`
- Direct `BuildConfig` key access — must go through `ApiKeys` object
- Hardcoded strings that belong in resources or constants
- Deprecated Android/Compose API calls
- `runCatching` without `logSilentFailure` on Appwrite client ops

## Protocol

1. Spawn one Explore agent per module with the pattern list above. Run all in parallel.
2. Collect findings: each agent returns `file:line — pattern — severity` table.
3. Triage: fix Critical (swallowed exceptions, unsafe `!!` on nullable returns) first; defer Informational.
4. Implement fixes in the main thread, one module at a time.
5. After all fixes: `./gradlew assembleDebug test` — must be green before committing.
