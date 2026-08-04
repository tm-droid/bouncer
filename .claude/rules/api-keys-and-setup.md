# API Keys & Local Setup

## The rule

**No credential may go through `buildConfigField`.** Every one becomes a `BuildConfig` string
constant compiled into the APK, readable with:

```bash
unzip -oq app-release.apk 'classes*.dex' && strings classes*.dex | grep -E 'gsk_|csk-'
```

R8 does not obfuscate string literals, so minification never hid them. Until 2026-08-04 the Groq,
Cerebras, SambaNova, RapidAPI and Highlightly keys shipped this way in every build, and the AI
provider keys were being used directly from the device as the live commentary path.

If a feature needs a credential, it calls an Appwrite function that holds one. It does not get a key.

## Where secrets live now

| Secret | Home | Used by |
|---|---|---|
| `GROQ_API_KEY`, `CEREBRAS_API_KEY`, `SAMBANOVA_API_KEY` | Appwrite function env | `bouncer-generate-commentary` (device requests), `bouncer-match-sync` (cron) |
| `RAPIDAPI_KEY` | Appwrite function env | `bouncer-news-sync`, `bouncer-highlights-sync` |
| `HIGHLIGHTLY_API_KEY` | Appwrite function env | `bouncer-highlights-sync` |
| `APPWRITE_API_KEY` | Appwrite function env | every function; server-side only |
| `SENTRY_USER_AUTH_TOKEN` | `local.properties`, build-time only | Gradle release upload; never reaches BuildConfig |
| `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | `local.properties`, build-time only | release signing |

`SENTRY_DSN` is a manifest placeholder, not a secret — a DSN can submit events, not read them.

## What is still in `local.properties`

Appwrite endpoint/project/database/table ids. These name public `read("any")` resources and are
already visible to anyone with the APK; they are configuration, not credentials. The template
(`local.properties.template`) is pre-filled with them, so setup is:

```bash
cp local.properties.template local.properties   # then set sdk.dir and, for release builds, the signing values
```

The app builds and runs with no secrets at all. Commentary comes from the server.

## Adding a new key

1. Put it in the relevant Appwrite function's environment (console, or MCP `functions_create_variable`).
   **A function env-var change needs a deployment rebuild to take effect** — see
   `appwrite-patterns.md`.
2. Call the function from the app via `AppwriteClient.executeFunction`. The device sends
   `x-bouncer-device` (see `DeviceIdentity`) so the function can rate-limit per device.
3. Do **not** add a `buildConfigField`, and do not add it to `ApiKeys.kt` — that object is now
   documented as non-secret build configuration only.

## Verifying

The acceptance check for any change in this area, on both debug and release artifacts:

```bash
unzip -oq <apk> 'classes*.dex'
strings classes*.dex | grep -E 'gsk_|csk-|rapidapi' && echo "LEAK" || echo "clean"
```

## Runtime access

Non-secret ids are read via the `ApiKeys` object, never `BuildConfig` directly.
