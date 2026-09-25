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
| `GROQ_API_KEY`, `GEMINI_API_KEY`, `CEREBRAS_API_KEY`, `SAMBANOVA_API_KEY` | Appwrite function env | `bouncer-generate-commentary` (device requests), `bouncer-match-sync` (cron). Declared in `appwrite.config.json` and copied by `sync-provider-keys.mjs`. `GEMINI_API_KEY` backs **three** `PROVIDERS` rungs — the free quota is per project per model, so each model id is its own allowance, and the allowances differ: flash-lite 500 RPD, 3.6-flash and 3-flash-preview 20 RPD (measured 2026-09-24). Do not multiply capacity with extra Google projects — Google APIs Terms §2(d) forbids circumventing limits. Cerebras and SambaNova answer 402 (unfunded accounts) and are no longer in the chain; their keys remain only so `probeProviders()` can still see them. |
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
   **Then rebuild the deployment** — see "Env vars are snapshotted per container" below.
2. Call the function from the app via `AppwriteClient.executeFunction`. The device sends
   `x-bouncer-device` (see `DeviceIdentity`) so the function can rate-limit per device.
3. Do **not** add a `buildConfigField`, and do not add it to `ApiKeys.kt` — that object is now
   documented as non-secret build configuration only.

## Env vars are snapshotted per container

An Appwrite function reads its environment **once, when a container boots**. A container is then
reused across executions. So a variable written or deleted through the console, the MCP or the REST
API applies to containers started *after* the change and to no others — which means a change lands
on an unpredictable subset of executions, indefinitely. This is not "it takes a moment to
propagate": there is no moment at which it becomes consistent on its own.

Measured on `bouncer-generate-commentary`, 2026-08-05, in both directions:

| Action | Next execution | Outcome |
|---|---|---|
| create `FORCE_LLM_FAILURE=1` | cold start, 4.39s | flag **live** — looks like the change "just worked" |
| delete `FORCE_LLM_FAILURE` | warm reuse, 0.14s | flag **still live** — the delete changed nothing |

The second row is the dangerous one. The variable was gone from `functions_list_variables`, the API
returned 204, and production kept returning `503 providers_exhausted` on a test flag that no longer
existed in the configuration. Nothing in the console shows this; the only symptom is behaviour that
disagrees with the variable list.

The only deterministic eviction:

```
appwrite_call_tool functions_create_duplicate_deployment {function_id, deployment_id}
```

Rebuilds the active deployment in place, `activate: true` by default, ~15s on a build-cache hit.
(The `deployment_id` is the function's current `deploymentId`.)

Two rules that follow:

- **Budget a rebuild in both directions.** Setting a flag for an experiment and removing it
  afterwards is two rebuilds, not one. Skipping the second leaves the flag live for the container's
  remaining life.
- **Never conclude "the code ignored the variable" without rebuilding first.** Equally, never
  conclude "I have cleaned up" — verify by calling the function and reading the response, not by
  reading the variable list.

## Verifying

The acceptance check for any change in this area, on both debug and release artifacts:

```bash
unzip -oq <apk> 'classes*.dex'
strings classes*.dex | grep -E 'gsk_|csk-|rapidapi' && echo "LEAK" || echo "clean"
```

## Runtime access

Non-secret ids are read via the `ApiKeys` object, never `BuildConfig` directly.
