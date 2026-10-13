---
paths:
  - "app/src/main/kotlin/**/data/remote/**"
  - "appwrite/**"
---

# Appwrite Access Patterns

Written after the July 2026 optimization pass that followed a production
**402 `limit_databases_reads_exceeded`**. Everything here exists because something already cost us
a quota. Read it before adding any Appwrite call.

## The one rule

**Every billed Appwrite operation goes through a funnel. Never construct a raw `TablesDB(client)`.**

| Operation | Funnel | Quota |
|---|---|---|
| Table read | `AppwriteTableClient.guardedRead(name) { db -> … }` | `DB_READ` |
| Table write (create/update/delete) | `AppwriteTableClient.guardedWrite(name) { db -> … }` | `DB_WRITE` |
| Storage download | `AppwriteClient.downloadFile(bucket, file)` | `BANDWIDTH` |
| Function execution | `AppwriteClient.executeFunction(id, body)` | `EXECUTIONS` |

The funnels are what make the circuit breaker, the rate windows and `AppwriteUsageTracker` work. A
call that bypasses them is invisible to all three — which is exactly how the (since-deleted)
favourites sync spent a year outside the guard.

All four return **null** when the guard is blocking. Null is already every caller's not-ready path,
so degradation costs no new branches: `CricketRepository` falls through to the Vercel API, the AI
cache falls through to Room, a table read falls through to whatever cache the caller has.

## 402 vs 429 — they are not the same error

- **402** = plan-level budget for one quota is spent, org-wide. Retrying does nothing until it
  resets. Latches that quota's breaker; one probe is allowed every 6h.
- **429** = short-term per-endpoint rate limit. Transient by definition. Sets a short pause and
  **never** latches a breaker; it is retryable (see `AppwriteAICache.isTransient`).

`Appwrite402Classifier` maps `e.type` (`limit_databases_reads_exceeded`, `limit_bandwidth_exceeded`,
…) onto a `Quota`, falling back to keyword matching on the message. **Quotas latch independently** —
a bandwidth 402 must not switch off database reads, which is what the pre-July guard did.

A 402 that cannot be attributed latches **the calling funnel's quota plus `BANDWIDTH`** (the shared
API+Storage+Realtime pool, breachable from any path) and nothing else, with a 1h probe instead of
6h because the attribution is partly inference. Freezing every quota on one ambiguous message is
more damage than the guess is worth.

Latches persist in SharedPreferences: a cold start after a 402 must not re-probe a dead budget.

**`EXECUTIONS` has local rate caps too**, as of the player-search work. It used to be breaker-only
alongside `BANDWIDTH` and `STORAGE`, on the reasoning that every function call was driven by a match
event or a fixed heartbeat and so was structurally rate-bounded. A search box is the first caller
driven by user typing, and with no cap a debounce regression sits directly against the plan budget.
`BANDWIDTH` and `STORAGE` stay breaker-only — they bill in bytes, which is server-side accounting we
cannot count locally.

## Reads: Appwrite bills per DOCUMENT RETURNED

Not per query. `Query.limit(60)` is 60 billed reads, and it was one of the two biggest drivers of
the 402.

- **Prefer `getRow($id)` over a filtered `listRows`.** Where a deterministic row id exists
  (`sanitizeMatchId(matchId)`, `cacheKeyToDocId(key)`), it is the same single billed read with no
  index dependency. `sanitizeMatchId` mirrors `match-sync/src/main.js` exactly and is pinned by
  `MatchRowIdTest` — a drift there is a silently dead subscription, not a crash.
- **Fetch incrementally, never rescan.** `AppwriteHighlightsClient` keeps a `fetched_at` watermark
  and asks only for rows newer than it. The full-table cursor loop it replaced cost ~19,000 billed
  reads per device per day.
- **A table needs an index only for queries you actually run.** `ai-commentary-cache.idx_cache_key`
  is load-bearing (`AppwriteAICache.get` filters on `cache_key`).
  `match-live-detail.idx_match_id` is redundant with the row id but stays: Android has no forced
  update, and an old build issuing `Query.equal("match_id", …)` after a drop reintroduces the
  read-amplification this work exists to remove.
- **`Query.select()` is bandwidth only** — it does not reduce the read count. Still worth adding on
  hot paths; see `AppwriteHighlightsClient.SELECTED_COLUMNS`.

## Repository reads: cache first, then single-flight

The order matters and it used to be inverted:

```kotlin
suspend fun getMatches(type: String): AppResult<SanwebMatchesListResponse> {
    matchesCache.get(type)?.let { return AppResult.Success(it) }   // 1. cache
    return matchesSingleFlight.run(type) { fetchMatches(type) }    // 2. one fetch, shared
}
```

`CricketRepository` used to download the whole matches blob and *then* check whether it was fresh —
so a 6-second poll loop downloaded a file the cron rewrote once a minute. `SingleFlight` collapses
the Auto screen, the main ViewModel and `ScorePollingWorker` all asking for `live` at once into one
request; it never hands out a completed `Deferred`, so a late caller cannot receive a stale result.

Storage downloads are conditional GETs through the shared OkHttp client, so an unchanged file costs
a 304 with an empty body. That only works because `bouncer-poll-live-matches` skips writes when the
content hash is unchanged — client TTL and server refresh floor are **one contract**
(`CacheConfig.matchStorageMaxAgeMs` vs `REFRESH_FLOOR_*_MS`). Change one, change the other.

## Realtime subscriptions

Appwrite has **no query-filtered channels**. There are two channel shapes:

- `rowChannel(rowId)` → `databases.{db}.tables.{table}.rows.{rowId}` — one row.
- `tableChannel()` → `databases.{db}.collections.{table}.documents` — every row anyone writes.

**Prefer the row channel.** `match-live-detail` on the table channel delivered every match anyone,
anywhere, had open to every device. Where the row id is unknowable client-side
(`match-commentary` ids are `sha256("$matchId:$timestamp")`) the table channel is unavoidable; the
lever is then **duration**, not scope.

**Then split the row by update shape.** A row is the unit of delivery, so anything in it is pushed
to every subscriber on every write. Measured on live `match-live-detail` rows, the scorecard was
85–93% of the payload (4–9 KB of 4–9 KB) while the live card — score, batsmen, bowler, recent balls,
win probability — was ~650 bytes. Same write cadence, wildly different value per byte, so the
scorecard moved to `match-scorecard` with its own row id and its own channel. Splitting is only a
win if the split-off part is genuinely subscribed less or read less; **re-subscribing to both rows
for the same lifetime saves nothing.** Here it buys a first-paint `getRow` and no billed reads
after, versus a poll, which would have spent reads — the quota that broke — to save bandwidth that
was never the constraint.

Two lifetimes, and nothing else:

1. **Demand-scoped.** `acquireChannel` is refcounted and returns a `Closeable`. Expose it as a
   `Flow` via `callbackFlow` + `awaitClose { handle.close() }`, and let `viewModelScope`'s
   cancellation in `onCleared()` be the release boundary — no `DisposableEffect`. Release lingers
   `REALTIME_LINGER_MS` so a rotation does not re-handshake. Ten screens on one channel = one socket.
2. **Always-on** (`AppwriteHighlightsClient`). Registered with
   `RealtimeLifecycleController`, which starts it on the first activity start, stops it ~60s after
   the app backgrounds and restarts it on foreground. Realtime bandwidth bills from the same pool
   as API and Storage, and a backgrounded app can render none of it.

`stop()` cancels the scope's **children**, never the scope — a cancelled `CoroutineScope` cannot
relaunch, so `cancel()` would kill realtime permanently on the first background cycle.

Realtime events are not billed reads and are deliberately not gated by the guard; anything they
*trigger* goes through `guardedRead` as usual.

## Writes that must not be silently lost

`AICacheService` writes memory + Room **before** mirroring to Appwrite, so the local
permanent-TTL guarantee for completed matches never depends on the network. The mirror can still be
dropped (breaker latched, no network), which used to lose the cross-device share with only a Sentry
capture.

The pattern: the row carries a nullable `syncedAt`, cleared only once Appwrite confirms.
`replayUnsyncedPermanent()` runs at startup and whenever the `DB_WRITE` breaker closes, bounded to
`AI_CACHE_REPLAY_BATCH` and spaced by `AI_CACHE_REPLAY_SPACING_MS` — the trigger is a breaker
*closing*, so an unbounded drain would spike the window it was just unblocked from. A pass that does
not finish is not a loss: the rows stay `syncedAt = null` and the next trigger resumes. Writes are
idempotent (deterministic row id, 409 handled), so a replayed row cannot duplicate.

## Server-side

Move work into a Function when every device would otherwise do it. Two rules learned the hard way:

- **Hash the data, not the payload.** `poll-live-matches` writes only when the content hash changed
  or the file is older than its refresh floor. Hashing the payload would compare a `fetchedAt:
  Date.now()` and differ every run.
- **A high-water mark beats an id set.** `match-sync` seeds `last_commentary_sort_key` from the
  detail row it is already writing (1 read, once per cold container) instead of listing 30
  commentary rows per match. Process oldest-first — a watermark can only advance contiguously, so
  newest-first would strand older items permanently once the per-run cap truncated a batch.

Apply schema and bucket changes via the **Appwrite MCP server**, not `appwrite push` (the CLI has
caused domain-rule conflicts and column deletion from config drift). Column creation returns
`status: "processing"` — confirm `"available"` with `tables_db_list_columns` before using it.

## Auth

There is none, deliberately. The app is unauthenticated, everything is `Role.any()`, and identity is
a device UUID in prefs. Appwrite's ~10 requests/hour account limit is therefore unreachable — **do
not add a session refresh loop** without revisiting that.

## Watching the numbers

`AppwriteUsageTracker` counts rows read, writes, executions, player searches, storage bytes,
realtime messages, 402s by quota and 429s, straight from the funnels, and flushes
`Sentry.metrics().gauge` every 5 minutes. Player searches are a labelled subset of executions
because they are the only ones driven by typing rather than by a match event or a fixed heartbeat —
aggregated together, a search box that regressed into firing per keystroke reads as ordinary growth.
402/429 also breadcrumb immediately. There is deliberately **no per-operation breadcrumb**: Sentry's
ring buffer holds ~100 entries and per-read crumbs would evict everything useful for crash triage.

The Appwrite console's read counter is the ground truth. The gauges exist to see a cap coming.
