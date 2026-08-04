# appwrite/tools

Local operator scripts for the Bouncer Appwrite functions. **Not deployed** — `appwrite.config.json`
maps every function to its own directory and none of them is this one.

```bash
cd appwrite/tools && npm install
```

Credentials come from the gitignored `local.properties` at the repo root (`APPWRITE_ENDPOINT`,
`APPWRITE_PROJECT_ID`, `APPWRITE_API_KEY`), or from environment variables of the same names. Neither
script prints a secret.

---

## `burst-load.mjs` — synthetic burst load for `bouncer-generate-commentary`

```bash
node burst-load.mjs --dry-run              # print the plan and quota cost, call nothing
node burst-load.mjs --preflight            # verification gates only
node burst-load.mjs --run                  # preflight, ramp 4 -> 8 -> 12, clean up
node burst-load.mjs --cleanup-only <runId> # delete a previous run's cache rows
```

**Shape.** Mirrors `bouncer-match-sync`, which is where production burst load actually comes from:
matches in parallel (`Promise.allSettled`, `match-sync/src/main.js:308`), balls within a match
sequentially (`:621`), capped at `MAX_MATCHES = 12` and `MAX_NEW_PER_MATCH = 10`. So the harness runs
N concurrent chains of 10 serial rows, never a flat fan-out — a 429 found by 120 simultaneous
requests would say nothing about a system that can only ever issue 12.

**Ramp stops at the first provider 429.** Deliberately: the question is where headroom ends, not how
hard the providers can be pushed past a limit that production could never reach.

**Detecting a 429 is the non-obvious part.** A 200 hides it — `generate-commentary/src/main.js:182`
returns only `{commentary, source, provider, verified}`, and `attempts` appears only in the 503 body
(`:159`), so a groq failure that cerebras recovers from leaves no trace in the response. The primary
detector is `execution.errors`, where `llm.js:137` writes `"<provider> HTTP <status>"` for every
provider non-2xx; Appwrite populates it because the harness authenticates with an API key. The
preflight asserts those fields are non-empty, and the run reports itself blind if they are not.

**Preflight gates** (a failure blocks the ramp):

1. one generated row — auth, body shape, `x-bouncer-device` path
2. the same `cacheKey` again must return `source:"cache"` — proves the cache is live, and therefore
   that the ramp's unique keys really do reach a provider. Without it, an all-cache-hit run would
   report zero 429s and mean nothing
3. `execution.logs`/`errors` readable

**Rate limiter.** `generate-commentary/src/rateLimit.js` allows 40 calls / 5 min per
`x-bouncer-device`; a breach blocks that device for an hour and appends it to the shared
`rate-limit-offenders` file in the `cricket-stats` bucket. The harness uses one synthetic device per
chain and rotates the id before 35 calls, so it never adds an entry.

**Cleanup** deletes every cache row it wrote, by recomputing `sha256(cacheKey).slice(0,36)` — the
same id `generate-commentary/src/main.js:120` uses. No `listRows`, so cleanup costs no billed reads.
It runs in a `finally`, and `--cleanup-only <runId>` recovers from a hard kill.

**Cost of a full ramp:** 240 executions, 240 document reads, ≤240 writes, ≤240 deletes, and roughly
500–2600 provider HTTP calls against the shared Groq/Cerebras/SambaNova accounts.

### TODO before reusing this after launch: presence gate

There is none. Poll `bouncer-match-presence` before and during a run and abort if a real viewer has a
live match open — provider quota burned here degrades their commentary to raw Cricbuzz text. It was
skipped for the 2026-08-04 run because the app has no users and the operator confirmed they were not
watching. That will not be true twice.

---

## `sentry-fallback-proof.mjs` — proves match-sync's passthrough reports and flushes

```bash
SENTRY_DSN='<dsn>' SENTRY_ENVIRONMENT=loadtest node sentry-fallback-proof.mjs
```

`burst-load.mjs` cannot answer this: `bouncer-generate-commentary` has no Sentry wiring at all, and
the `ai_generated: false` raw-text fallback lives only in match-sync (`match-sync/src/main.js:668-676`).

This imports match-sync's **real** `llm.js` and `observability.js`, sets `FORCE_LLM_FAILURE=1` (the
hook at `llm.js:108-112`), asserts the chain returns `{line: null, reason: "providers_exhausted"}`,
makes the same `report()` call the passthrough branch makes with a synthetic
`matchId: loadtest-<runId>`, and flushes. **The flush is the thing under test** — an Appwrite
container freezes the moment the handler returns, so an unflushed queue is simply lost, which is
why `observability.js:61` exists. Confirm delivery by searching Sentry for that `matchId`.

Touches no deployed function, no environment variable, and no table.
