# Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all architectural, security, concurrency, and performance defects identified in the 2026-06-04 adversarial codebase audit.

**Architecture:** Fixes are ordered P0→P3; each task is independent and produces a working, testable build. Tasks that share a dependency (AppwriteTableClient before singletons) are ordered accordingly.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, Appwrite Android SDK, kotlinx.coroutines, Sentry

---

## File Map

| File | Change |
|------|--------|
| `services/RetryPolicy.kt` | Replace `!!` with safe fallback |
| `ui/screens/matchdetail/v2/InfoTabV2.kt` | Replace `tossWinner!!` with safe access |
| `ui/screens/SeriesListScreen.kt` | Replace empty catch with logging |
| `data/remote/AppwriteClient.kt` | Atomic `@Synchronized` initialization |
| `data/remote/AppwriteTableClient.kt` | **New** — abstract base class for Appwrite singletons |
| `data/remote/AppwriteAICache.kt` | Extend `AppwriteTableClient` |
| `data/remote/AppwriteHighlightsClient.kt` | Extend `AppwriteTableClient` |
| `data/remote/AppwriteFavouritesSync.kt` | Extend `AppwriteTableClient` |
| `data/remote/AppwritePlayerFormsClient.kt` | Extend `AppwriteTableClient` |
| `data/remote/AppwritePlayerProfilesClient.kt` | Extend `AppwriteTableClient` |
| `viewmodels/SearchViewModel.kt` | Remove Context params, use `getApplication()` |
| `viewmodels/HighlightsViewModel.kt` | Extend `AndroidViewModel`, remove raw `Context` field |
| `services/CommentaryFusionService.kt` | Replace `verifyCacheMutex` with proper LRU |
| `services/AICacheService.kt` | Check Room before overwriting in `ingestFromRealtime` |
| `services/ScorePollingWorker.kt` | Early-exit when no subscriptions |
| 44 UI files (see Task 10) | Add `key =` to `items()` / `itemsIndexed()` calls |
| `data/local/dao/HighlightDao.kt` | Wire `deleteOlderThan` call |
| `services/SeriesSyncWorker.kt` | Call highlight cleanup during sync |
| `managers/FavouriteTeamsManager.kt` | Accept `applicationScope` instead of owning scope |
| `managers/NotificationInboxManager.kt` | Accept `applicationScope` instead of owning scope |
| `BouncerApp.kt` | Pass `applicationScope` to managers |
| `ui/MatchDetailView.kt` | Delete V1 dead file |
| `utils/CommentaryParser.kt` | **New** — extract `parseCommentaryType` from UI |
| `ui/components/LiveCommentaryBubbles.kt` | Import from `CommentaryParser` |
| `utils/PrefsRegistry.kt` | **New** — centralize all SharedPreferences file names and keys |
| `ui/components/v2/ReminderBottomSheet.kt` | Use `PrefsRegistry` |
| `ui/components/v2/MatchNotificationPrefsSheet.kt` | Use `PrefsRegistry` |
| `services/ScorePollingWorker.kt` | Use `PrefsRegistry` |
| `managers/MatchSubscriptionManager.kt` | Use `PrefsRegistry` |

---

## Task 1: Fix `RetryPolicy` NPE — `throw lastThrowable!!`

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/services/RetryPolicy.kt:34`
- Test: `app/src/test/java/com/tanush/bouncer/services/RetryPolicyTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
// app/src/test/java/com/tanush/bouncer/services/RetryPolicyTest.kt
package com.tanush.bouncer.services

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `withRetry throws descriptive exception when block never throws but loop exhausts`() = runTest {
        // This path is logically unreachable today, but the !! makes it an NPE bomb.
        // Verify the safe fallback message instead.
        val policy = RetryPolicy(maxAttempts = 1)
        var caughtMessage: String? = null
        try {
            policy.withRetry { throw RuntimeException("boom") }
        } catch (e: Throwable) {
            caughtMessage = e.message
        }
        assertNotNull(caughtMessage)
        assertTrue("boom" == caughtMessage || caughtMessage?.contains("exhausted") == true)
    }

    @Test
    fun `withRetry propagates original exception message`() = runTest {
        val policy = RetryPolicy(maxAttempts = 2, baseDelayMs = 0)
        var attempts = 0
        val caught = runCatching {
            policy.withRetry {
                attempts++
                throw IllegalStateException("original error")
            }
        }
        assertTrue(caught.isFailure)
        assertTrue(attempts == 2)
        assertTrue(caught.exceptionOrNull()?.message == "original error")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "*RetryPolicyTest" --no-daemon
```

Expected: FAIL on `throw lastThrowable!!` or test infrastructure complaint.

- [ ] **Step 3: Fix RetryPolicy.kt**

Open `app/src/main/kotlin/com/tanush/bouncer/services/RetryPolicy.kt`. Replace the final line:

```kotlin
// Before
throw lastThrowable!!

// After
throw lastThrowable ?: IllegalStateException("RetryPolicy exhausted ${maxAttempts} attempts with no recorded exception")
```

- [ ] **Step 4: Run tests to verify pass**

```bash
./gradlew test --tests "*RetryPolicyTest" --no-daemon
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/services/RetryPolicy.kt \
        app/src/test/java/com/tanush/bouncer/services/RetryPolicyTest.kt
git commit -m "fix(retry): replace lastThrowable!! with safe fallback to prevent NPE"
```

---

## Task 2: Fix `tossWinner!!` crash in InfoTabV2

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/ui/screens/matchdetail/v2/InfoTabV2.kt:78`

Run this to see the exact call site context:
```bash
grep -n 'tossWinner' app/src/main/kotlin/com/tanush/bouncer/ui/screens/matchdetail/v2/InfoTabV2.kt
```

- [ ] **Step 1: Find the exact !! and its enclosing composable call**

Expect output like:
```
78:    tossWinner = match.tossWinner!!,
```

Read lines 70–95 to understand the enclosing composable and its nullability contract.

- [ ] **Step 2: Apply null-safe guard**

`match.tossWinner` is nullable. Pattern: only render the toss section when data is present.

Find the block that calls the composable/component using `tossWinner`. Wrap it:

```kotlin
// Before (somewhere around line 70–80):
TossInfoRow(
    tossWinner = match.tossWinner!!,
    // ... other params
)

// After: skip the entire row when toss hasn't happened yet
match.tossWinner?.let { tossWinner ->
    TossInfoRow(
        tossWinner = tossWinner,
        // ... other params unchanged
    )
}
```

If `TossInfoRow` has other non-null params sourced from the same object, apply `?.let { }` over all of them together or use `?: return` / `?: "Toss pending"` as the display fallback per param.

- [ ] **Step 3: Build to verify no compile errors**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/ui/screens/matchdetail/v2/InfoTabV2.kt
git commit -m "fix(match-detail): null-guard tossWinner to prevent crash on pre-toss matches"
```

---

## Task 3: Fix silent exception swallow in SeriesListScreen

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/ui/screens/SeriesListScreen.kt:69`

- [ ] **Step 1: Read the context**

```bash
sed -n '55,85p' app/src/main/kotlin/com/tanush/bouncer/ui/screens/SeriesListScreen.kt
```

- [ ] **Step 2: Replace empty catch**

Find the `catch (_: Exception) {}` at line ~69. Replace with:

```kotlin
// Before
} catch (_: Exception) {}

// After
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    Sentry.captureException(e)
    Log.w("SeriesListScreen", "Series list operation failed", e)
}
```

Make sure `import io.sentry.Sentry` and `import android.util.Log` are present at the top of the file.

- [ ] **Step 3: Build to verify**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/ui/screens/SeriesListScreen.kt
git commit -m "fix(series): replace silent exception swallow with Sentry capture"
```

---

## Task 4: Fix `AppwriteClient` TOCTOU race on `isReady`/`client`

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwriteClient.kt`

**Problem:** `isReady = true` and `client = Client(context)` are two separate volatile writes. A thread reading `isReady == true` and then accessing `client` can observe `client == null` between the two writes.

**Fix:** Use `@Synchronized` on `initialize()` and derive `isReady` from `client != null` to make them one atomic invariant.

- [ ] **Step 1: Rewrite `initialize()` and the `isReady`/`client` declarations**

```kotlin
// In AppwriteClient object

// Before:
@Volatile
var client: Client? = null
    private set
@Volatile
var isReady = false
    private set

fun initialize(context: Context) {
    val endpoint = ApiKeys.appwriteEndpoint.trimEnd('/')
    val projectId = ApiKeys.appwriteProjectId
    isReady = endpoint.isNotBlank() && projectId.isNotBlank()
    if (isReady) {
        client = Client(context)
            .setEndpoint(endpoint)
            .setProject(projectId)
    } else {
        Log.w(TAG, "Appwrite not configured — SDK client disabled")
    }
}

// After:
@Volatile
private var _client: Client? = null

val client: Client? get() = _client
val isReady: Boolean get() = _client != null

@Synchronized
fun initialize(context: Context) {
    val endpoint = ApiKeys.appwriteEndpoint.trimEnd('/')
    val projectId = ApiKeys.appwriteProjectId
    if (endpoint.isBlank() || projectId.isBlank()) {
        Log.w(TAG, "Appwrite not configured — SDK client disabled")
        return
    }
    _client = Client(context.applicationContext)
        .setEndpoint(endpoint)
        .setProject(projectId)
}
```

- [ ] **Step 2: Build — confirm all Appwrite singletons still compile**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|warning:|BUILD'
```

The 5 singletons all read `AppwriteClient.isReady` and `AppwriteClient.client`. Both are now derived from `_client` so no call sites need changing.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwriteClient.kt
git commit -m "fix(appwrite): atomic client initialization prevents isReady/client TOCTOU race"
```

---

## Task 5: Extract `AppwriteTableClient` base class — DRY the 5 Appwrite singletons

**Files:**
- Create: `app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwriteTableClient.kt`
- Modify: `AppwriteAICache.kt`, `AppwriteFavouritesSync.kt`, `AppwritePlayerFormsClient.kt`, `AppwritePlayerProfilesClient.kt`
- Note: `AppwriteHighlightsClient.kt` has Realtime subscription + reconnect scope — only the table init boilerplate is extracted; leave its extra fields as-is.

- [ ] **Step 1: Create the base class**

```kotlin
// app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwriteTableClient.kt
package com.tanush.bouncer.data.remote

import android.util.Log
import io.appwrite.services.TablesDB

abstract class AppwriteTableClient(private val tag: String) {

    protected var dbId = ""
        private set
    protected var tableId = ""
        private set

    val isReady: Boolean
        get() = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()

    protected val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    protected fun initTable(dbIdValue: String, tableIdValue: String) {
        dbId = dbIdValue
        tableId = tableIdValue
        if (!isReady) {
            Log.w(tag, "Appwrite $tag not configured — remote tier disabled")
        }
    }
}
```

- [ ] **Step 2: Update AppwriteAICache**

Open `AppwriteAICache.kt`. Make it extend `AppwriteTableClient`:

```kotlin
// Before:
object AppwriteAICache {
    private const val TAG = "AppwriteAICache"
    private val TTL_MS get() = CacheConfig.AI_CACHE_TTL_MS
    private var realtimeInstance: Realtime? = null
    private var realtimeSubscription: Any? = null
    private var dbId = ""
    private var tableId = ""
    var isReady = false
        private set
    private val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    fun initialize() {
        dbId = ApiKeys.appwriteAiCacheDbId
        tableId = ApiKeys.appwriteAiCacheColId
        isReady = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()
        if (!isReady) {
            Log.w(TAG, "Appwrite AI cache not configured — remote cache tier disabled")
        }
    }

// After:
object AppwriteAICache : AppwriteTableClient("AppwriteAICache") {
    private const val TAG = "AppwriteAICache"
    private val TTL_MS get() = CacheConfig.AI_CACHE_TTL_MS
    private var realtimeInstance: Realtime? = null
    private var realtimeSubscription: Any? = null
    // dbId, tableId, isReady, tablesDB inherited — remove them

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, ApiKeys.appwriteAiCacheColId)
    }
```

Delete the now-redundant `dbId`, `tableId`, `isReady`, and `tablesDB` declarations from `AppwriteAICache`.

- [ ] **Step 3: Update AppwriteFavouritesSync**

Same pattern. The object inherits `dbId`, `tableId`, `isReady`, `tablesDB`. Replace the duplicated boilerplate:

```kotlin
// Before:
object AppwriteFavouritesSync {
    private const val TAG = "AppwriteFavouritesSync"
    private var dbId = ""
    private var tableId = ""
    var isReady = false
        private set
    private val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    fun initialize(prefs: SharedPreferences) {
        dbId = ApiKeys.appwriteAiCacheDbId   // shares same DB as AI cache
        tableId = ApiKeys.appwriteFavouritesColId
        isReady = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()
        ...
    }

// After:
object AppwriteFavouritesSync : AppwriteTableClient("AppwriteFavouritesSync") {
    private const val TAG = "AppwriteFavouritesSync"

    fun initialize(prefs: SharedPreferences) {
        initTable(ApiKeys.appwriteAiCacheDbId, ApiKeys.appwriteFavouritesColId)
        ...  // keep remaining init logic (prefs storage etc.)
    }
```

- [ ] **Step 4: Update AppwritePlayerFormsClient and AppwritePlayerProfilesClient**

Apply the same extraction:

```kotlin
// AppwritePlayerFormsClient
object AppwritePlayerFormsClient : AppwriteTableClient("AppwritePlayerFormsClient") {
    fun initialize() {
        initTable(ApiKeys.appwritePlayerFormsDbId, ApiKeys.appwritePlayerFormsColId)
    }
    // rest of implementation unchanged
}

// AppwritePlayerProfilesClient
object AppwritePlayerProfilesClient : AppwriteTableClient("AppwritePlayerProfilesClient") {
    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, ApiKeys.appwritePlayerProfilesColId)
    }
    // rest of implementation unchanged
}
```

- [ ] **Step 5: Update AppwriteHighlightsClient (partial)**

Only remove the `dbId`, `tableId`, `isReady`, and `tablesDB` duplicates. Keep `reconnectScope`, `subscriptionStarted`, `realtimeSubscription`:

```kotlin
object AppwriteHighlightsClient : AppwriteTableClient("AppwriteHighlightsClient") {
    private const val TAG = "AppwriteHighlightsClient"
    // Keep these — specific to Highlights:
    @Volatile private var realtimeSubscription: Any? = null
    private val subscriptionStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Remove: dbId, tableId, isReady, tablesDB — all inherited

    fun initialize() {
        initTable(ApiKeys.appwriteHighlightsDbId, ApiKeys.appwriteHighlightsColId)
    }
```

- [ ] **Step 6: Build to confirm no regressions**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E '^.*error:|BUILD'
```

Expected: BUILD SUCCESSFUL, 0 errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwriteTableClient.kt \
        app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwriteAICache.kt \
        app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwriteHighlightsClient.kt \
        app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwriteFavouritesSync.kt \
        app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwritePlayerFormsClient.kt \
        app/src/main/kotlin/com/tanush/bouncer/data/remote/AppwritePlayerProfilesClient.kt
git commit -m "refactor(appwrite): extract AppwriteTableClient base class, eliminate 5x duplicated boilerplate"
```

---

## Task 6: Fix `SearchViewModel` — remove Context params, use `getApplication()`

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/viewmodels/SearchViewModel.kt`
- Modify: All callers of `loadRecentQueries(ctx)`, `onSearchSubmitted(ctx, q)`, `clearRecentQueries(ctx)`

`SearchViewModel` already extends `AndroidViewModel` — it just exposes public methods that accept external `Context`, creating a latent Activity leak risk.

- [ ] **Step 1: Update SearchViewModel methods**

```kotlin
// Before:
fun loadRecentQueries(context: Context) {
    _recentQueries.value = SearchRepository.getRecentQueries(context)
}

fun onSearchSubmitted(context: Context, query: String) {
    SearchRepository.addRecentQuery(context, query)
    _recentQueries.value = SearchRepository.getRecentQueries(context)
}

fun clearRecentQueries(context: Context) {
    SearchRepository.clearRecentQueries(context)
    _recentQueries.value = emptyList()
}

// After:
fun loadRecentQueries() {
    _recentQueries.value = SearchRepository.getRecentQueries(getApplication())
}

fun onSearchSubmitted(query: String) {
    SearchRepository.addRecentQuery(getApplication(), query)
    _recentQueries.value = SearchRepository.getRecentQueries(getApplication())
}

fun clearRecentQueries() {
    SearchRepository.clearRecentQueries(getApplication())
    _recentQueries.value = emptyList()
}
```

Also remove `import android.content.Context` if it becomes unused.

- [ ] **Step 2: Find and fix all callers**

```bash
grep -rn 'loadRecentQueries\|onSearchSubmitted\|clearRecentQueries' \
  app/src/main/kotlin/com/tanush/bouncer/ui/
```

For each call site, remove the context argument:

```kotlin
// Before (in a Composable):
val context = LocalContext.current
LaunchedEffect(Unit) { viewModel.loadRecentQueries(context) }
viewModel.onSearchSubmitted(context, query)
viewModel.clearRecentQueries(context)

// After:
LaunchedEffect(Unit) { viewModel.loadRecentQueries() }
viewModel.onSearchSubmitted(query)
viewModel.clearRecentQueries()
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/viewmodels/SearchViewModel.kt
git add $(grep -rl 'loadRecentQueries\|onSearchSubmitted\|clearRecentQueries' app/src/main/kotlin/com/tanush/bouncer/ui/)
git commit -m "fix(viewmodel): remove Context params from SearchViewModel, use getApplication() internally"
```

---

## Task 7: Fix `HighlightsViewModel` — use `AndroidViewModel` not raw `Context`

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/viewmodels/HighlightsViewModel.kt`

- [ ] **Step 1: Update constructor and factory**

```kotlin
// Before:
class HighlightsViewModel(context: Context, val bouncerMatchId: String) : ViewModel() {
    private val db = BouncerDatabase.getInstance(context)

    companion object {
        fun factory(context: Context, matchId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HighlightsViewModel(context, matchId) as T
            }
    }
}

// After:
class HighlightsViewModel(
    application: Application,
    val bouncerMatchId: String,
) : AndroidViewModel(application) {
    private val db = BouncerDatabase.getInstance(application)
    // rest of implementation unchanged

    companion object {
        fun factory(context: Context, matchId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HighlightsViewModel(context.applicationContext as Application, matchId) as T
            }
    }
}
```

Add `import android.app.Application` and `import androidx.lifecycle.AndroidViewModel`. Remove `import android.content.Context` if unused elsewhere.

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/viewmodels/HighlightsViewModel.kt
git commit -m "fix(viewmodel): HighlightsViewModel extends AndroidViewModel to prevent Activity context leak"
```

---

## Task 8: Fix `verifyCacheMutex` arbitrary eviction in `CommentaryFusionService`

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/services/CommentaryFusionService.kt`

**Problem:** `verifyCache` is a `ConcurrentHashMap<Int, Boolean>` with a `verifyCacheMutex` for eviction. `ConcurrentHashMap.keys.firstOrNull()` has no ordering guarantee — eviction is arbitrary, not LRU/FIFO.

**Fix:** Replace with a `LinkedHashMap` in insertion order (FIFO), protected entirely by the existing `verifyCacheMutex`. Remove the now-redundant `ConcurrentHashMap`.

- [ ] **Step 1: Find the declarations and eviction block**

```bash
grep -n 'verifyCache\|verifyCacheMutex' \
  app/src/main/kotlin/com/tanush/bouncer/services/CommentaryFusionService.kt
```

- [ ] **Step 2: Replace declaration**

```kotlin
// Before:
// ConcurrentHashMap: point reads/writes never cross suspension points so no Mutex needed here.
private val verifyCache = ConcurrentHashMap<Int, Boolean>(512)
private val verifyCacheMutex = Mutex()

// After:
// LinkedHashMap in insertion order — FIFO eviction. All access guarded by verifyCacheMutex.
private val verifyCache = LinkedHashMap<Int, Boolean>(512)
private val verifyCacheMutex = Mutex()
```

Remove `import java.util.concurrent.ConcurrentHashMap` if it's only used by `verifyCache` (check `providerCooldowns`, `previousBallsBuffer`, `inFlightFuse` — those stay as `ConcurrentHashMap` since they are accessed without a mutex).

- [ ] **Step 3: Fix the read site**

The read at line ~755 does `verifyCache[cacheKey]?.let { return it }`. This is a point read that now crosses a suspension point boundary (it's inside a `suspend fun`). Protect it with the mutex:

```kotlin
// Before (around line 755):
verifyCache[cacheKey]?.let { return it }

// After:
verifyCacheMutex.withLock { verifyCache[cacheKey] }?.let { return it }
```

- [ ] **Step 4: Fix the write/eviction site**

```kotlin
// Before (around line 789):
verifyCacheMutex.withLock {
    if (verifyCache.size >= 512) {
        verifyCache.keys.firstOrNull()?.let { verifyCache.remove(it) }
    }
    verifyCache[cacheKey] = passed
}

// After: firstOrNull() on LinkedHashMap IS the oldest (insertion order) — comment corrected:
verifyCacheMutex.withLock {
    if (verifyCache.size >= 512) {
        // LinkedHashMap.keys.firstOrNull() = oldest inserted entry (FIFO eviction)
        verifyCache.keys.firstOrNull()?.let { verifyCache.remove(it) }
    }
    verifyCache[cacheKey] = passed
}
```

- [ ] **Step 5: Build and run commentary tests**

```bash
./gradlew test --tests "*CommentaryFusionServiceTest" --no-daemon 2>&1 | tail -30
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/services/CommentaryFusionService.kt
git commit -m "fix(commentary): replace ConcurrentHashMap with LinkedHashMap for deterministic FIFO eviction in verifyCache"
```

---

## Task 9: Fix `AICacheService.ingestFromRealtime` — check Room when memory is cold

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/services/AICacheService.kt`

**Problem:** `ingestFromRealtime` skips overwrite only if there's a valid *memory* entry. After app restart, memory is cold. A Realtime push (e.g., a network replay) for a key that Room already has fresh can overwrite the Room entry unnecessarily.

**Fix:** When `existing == null` (memory cold), also check Room before accepting the Realtime entry.

- [ ] **Step 1: Update `ingestFromRealtime`**

```kotlin
// Before:
suspend fun ingestFromRealtime(key: String, commentary: String) = mutex.withLock {
    try {
        val existing = cache[key]
        if (existing != null) {
            val ageMs = Date().time - existing.timestamp.time
            if (ageMs < CACHE_EXPIRATION_INTERVAL_MS) return@withLock
        }
        val now = Date()
        cache[key] = CommentaryEntry(commentary, now)
        dao?.insert(AICommentaryCacheEntity(key, commentary, now.time))
        Log.d(TAG, "Realtime: warmed cache for key $key")
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "Failed to ingest realtime cache entry for key $key", e)
    }
}

// After:
suspend fun ingestFromRealtime(key: String, commentary: String) = mutex.withLock {
    try {
        val existing = cache[key]
        if (existing != null) {
            val ageMs = Date().time - existing.timestamp.time
            if (ageMs < CACHE_EXPIRATION_INTERVAL_MS) return@withLock
        } else {
            // Memory cold — check Room before accepting the Realtime push.
            // This prevents a stale Realtime replay from overwriting a fresher Room entry
            // on first access after app restart.
            val cutoffMs = System.currentTimeMillis() - CACHE_EXPIRATION_INTERVAL_MS
            if (dao?.getCommentary(key, cutoffMs) != null) return@withLock
        }
        val now = Date()
        cache[key] = CommentaryEntry(commentary, now)
        dao?.insert(AICommentaryCacheEntity(key, commentary, now.time))
        Log.d(TAG, "Realtime: warmed cache for key $key")
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "Failed to ingest realtime cache entry for key $key", e)
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/services/AICacheService.kt
git commit -m "fix(cache): check Room when memory is cold in ingestFromRealtime to prevent stale overwrite"
```

---

## Task 10: Fix `ScorePollingWorker` — skip network calls when no subscriptions

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/services/ScorePollingWorker.kt`

**Problem:** Worker runs every 15 min unconditionally, making API calls even when the user has zero match subscriptions.

- [ ] **Step 1: Add early-exit guard at top of `doWork()`**

Find the start of `doWork()`. After the existing Sentry breadcrumb log and before the `CricketRepository.getMatches("live")` call, insert:

```kotlin
// Early-exit: no subscriptions → nothing to notify, skip network call entirely.
if (MatchSubscriptionManager.subscriptions.value.isEmpty()) {
    Log.d(TAG, "No subscriptions — skipping poll")
    WorkerLogger.enqueued("score_polling", "Skipped — no active subscriptions")
    return Result.success()
}
```

`MatchSubscriptionManager` is already imported (it's used in `notifySubscribedMatches`). If not, add `import com.tanush.bouncer.managers.MatchSubscriptionManager`.

- [ ] **Step 2: Build and verify**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/services/ScorePollingWorker.kt
git commit -m "perf(worker): skip score poll network call when user has no match subscriptions"
```

---

## Task 11: Add stable `key =` to all 44 `items()` / `itemsIndexed()` calls missing it

**Files:** 44 UI files listed below. Each change is mechanical: add `key = { ... }` using the most stable identifier available (entity ID, name, index as last resort).

**Full list of affected lines:**
```
adaptive/AdaptiveLayouts.kt:514         items(items) { item ->
adaptive/AdaptiveLayouts.kt:549         items(items) { item ->
components/AIPredictiveComponents.kt:116  items(predictions) { prediction ->
components/FallOfWicketsCard.kt:147       itemsIndexed(wickets) { index, wicket ->
components/FilterDropdown.kt:77           items(options.filter { ... }) { option ->
components/LiveCommentaryCard.kt:228      items(
components/LiveMatchDashboard.kt:419      items(wickets) { wicket ->
components/MatchExperienceComponents.kt:273 items(balls) { ball ->
components/MatchTimelineCard.kt:246       items(moments.sortedBy { it.over }) { moment ->
components/MatchTimelineComponents.kt:65  itemsIndexed(events) { index, event ->
components/PredictiveForecast.kt:74       items(predictions) { prediction ->
components/premium/EliteForecastCarousel.kt:70  items(displayPredictions) { prediction ->
components/premium/PremiumScorecardTab.kt:133   items(innings?.batsmen ?: emptyList()) { batsman ->
components/premium/PremiumScorecardTab.kt:177   items(innings?.bowlers ?: emptyList()) { bowler ->
components/premium/PremiumScorecardTab.kt:361   items(fow) { entry ->
components/PremiumMatchComponents.kt:288  items(matches) { match ->
components/PremiumQuickActions.kt:65      items(actions) { action ->
components/PremiumSquadComponents.kt:382  items(playersInRole) { player ->
components/PremiumTeamComponents.kt:621   items(teams) { team ->
components/PremiumTimelineComponents.kt:60 items(events) { event ->
components/PremiumUIElements.kt:199       items(chips) { chip ->
components/ProbabilityGraphView.kt:196    items(keyMoments) { event ->
components/TeamFormCard.kt:187            items(recentMatches) { match ->
devtools/DevToolsScreen.kt:97             items(recent) { l ->
performance/ListOptimizations.kt:82       itemsIndexed(
performance/ListOptimizations.kt:146      itemsIndexed(
performance/ListOptimizations.kt:387      itemsIndexed(
screens/AchievementsScreenV2.kt:136       items(filtered) { achievement ->
screens/AppearanceScreen.kt:98            items(ThemeVariant.entries) { variant ->
screens/AppearanceScreen.kt:115           items(BackgroundSkinType.entries) { skin ->
screens/FavoritesScreen.kt:265            itemsIndexed(reorderableFavorites) { index, teamName ->
screens/IntelligenceFeedScreen.kt:161     items(alerts) { alert ->
screens/IntelligenceFeedScreenV2.kt:126   items(alerts) { alert ->
screens/matchdetail/v2/HighlightsTabV2.kt:37 items(highlights) { highlight ->
screens/McpPredictiveForecastScreen.kt:172 items(predictions) { prediction ->
screens/RemindersActivity.kt:60           items(reminders) { r ->
screens/SettingsScreen.kt:394             items(sections) { section ->
screens/SquadScreen.kt:229               items(currentSquad) { player ->
screens/TrophyRoomScreenV2.kt:118         items(sampleTrophies) { trophy ->
theme/ThemeCustomization.kt:296           items(PresetAccentColors) { color ->
widgets/WidgetConfigurationActivity.kt:138 items(availableMatches) { match ->
```

- [ ] **Step 1: For each file, read the item type and choose a key**

Key selection rules (in priority order):
1. Use `.id` if the item has an `id: String` or `id: Int` field
2. Use `.name` or `.title` if unique within the list
3. Use `index` for `itemsIndexed` calls (already available as lambda param)
4. Use `it.hashCode()` only as last resort for string primitives

Examples:

```kotlin
// items(matches) { match -> }  →  match has .id
items(matches, key = { it.id }) { match -> ... }

// items(wickets) { wicket -> }  →  wicket likely has batsman name
items(wickets, key = { it.batsmanName }) { wicket -> ... }

// items(predictions) { prediction -> }  →  use index from position
items(predictions, key = { predictions.indexOf(it) }) { ... }
// Better if prediction has an over field:
items(predictions, key = { it.over }) { prediction -> ... }

// itemsIndexed(events) { index, event -> }  →  index is already stable
itemsIndexed(events, key = { index, _ -> index }) { index, event -> ... }

// items(ThemeVariant.entries) { variant -> }  →  enum name is stable
items(ThemeVariant.entries, key = { it.name }) { variant -> ... }

// items(chips) { chip -> }  →  if chip is String
items(chips, key = { it }) { chip -> ... }

// items(recent) { l -> }  →  if l is a log String, use index
items(recent, key = { recent.indexOf(it) }) { l -> ... }
```

- [ ] **Step 2: Apply all changes**

Work file-by-file. For each file:
1. Read the item data class or type to pick the right key.
2. Apply the `key = { ... }` argument.
3. Confirm build passes.

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/ui/
git commit -m "perf(compose): add stable key= to 44 LazyColumn/LazyRow items() calls to prevent O(N) recomposition"
```

---

## Task 12: Wire `HighlightDao.deleteOlderThan` — prevent unbounded table growth

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/services/SeriesSyncWorker.kt`

`HighlightDao` has `deleteOlderThan(:threshold)` but it is never called. Highlights accumulate indefinitely.

- [ ] **Step 1: Find SeriesSyncWorker's doWork() body**

```bash
grep -n 'doWork\|highlights\|cleanup' \
  app/src/main/kotlin/com/tanush/bouncer/services/SeriesSyncWorker.kt | head -20
```

- [ ] **Step 2: Add cleanup call at the start of doWork()**

After the worker acquires the database instance, add:

```kotlin
// Prune highlights older than 7× the freshness window (~1 week).
// The DAO query exists but was never wired. Without this, the table grows unbounded.
val highlightCutoff = System.currentTimeMillis() - (CacheConfig.HIGHLIGHTS_FRESHNESS_MS * 7)
db.highlightDao().deleteOlderThan(highlightCutoff)
Log.d(TAG, "Pruned highlights older than 7 days")
```

Add imports `com.tanush.bouncer.data.CacheConfig` and `android.util.Log` if missing.

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/services/SeriesSyncWorker.kt
git commit -m "fix(data): wire HighlightDao.deleteOlderThan in SeriesSyncWorker to prevent unbounded table growth"
```

---

## Task 13: Fix orphaned `CoroutineScope` in managers — use `applicationScope`

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/managers/FavouriteTeamsManager.kt`
- Modify: `app/src/main/kotlin/com/tanush/bouncer/managers/NotificationInboxManager.kt`
- Modify: `app/src/main/kotlin/com/tanush/bouncer/BouncerApp.kt`

**Problem:** Both managers create private `CoroutineScope(SupervisorJob() + Dispatchers.X)` that are never cancelled. They should share the application-level scope from `BouncerApp`.

- [ ] **Step 1: Update `FavouriteTeamsManager`**

```kotlin
// Before:
object FavouriteTeamsManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        ...
        scope.launch { ... }
    }

// After:
object FavouriteTeamsManager {
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // fallback

    fun initialize(context: Context, appScope: CoroutineScope = scope) {
        scope = appScope
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        ...
        scope.launch { ... }
    }
```

Default parameter keeps existing callers without `appScope` working unchanged.

- [ ] **Step 2: Update `NotificationInboxManager`**

```kotlin
// Before:
object NotificationInboxManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initialize(context: Context) { ... }

// After:
object NotificationInboxManager {
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initialize(context: Context, appScope: CoroutineScope = scope) {
        scope = appScope
        ...
    }
```

- [ ] **Step 3: Pass `applicationScope` in BouncerApp**

In `BouncerApp.kt`, the `applicationScope` is already defined. Find the `initializeSafely` calls for both managers and pass the scope:

```kotlin
// Before:
initializeSafely("FavouriteTeamsManager") { FavouriteTeamsManager.initialize(this) }
initializeSafely("NotificationInboxManager") { NotificationInboxManager.initialize(this) }

// After:
initializeSafely("FavouriteTeamsManager") {
    FavouriteTeamsManager.initialize(this, applicationScope)
}
initializeSafely("NotificationInboxManager") {
    NotificationInboxManager.initialize(this, applicationScope)
}
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/managers/FavouriteTeamsManager.kt \
        app/src/main/kotlin/com/tanush/bouncer/managers/NotificationInboxManager.kt \
        app/src/main/kotlin/com/tanush/bouncer/BouncerApp.kt
git commit -m "fix(managers): pass applicationScope to FavouriteTeamsManager and NotificationInboxManager, eliminating orphaned scopes"
```

---

## Task 14: Delete V1 dead screen code

**Files:**
- Delete: `app/src/main/kotlin/com/tanush/bouncer/ui/MatchDetailView.kt`

Confirmed by CLAUDE.md: "V1 files are kept but unused." No navigation routes point to `MatchDetailView` (V1). The V2 file `MatchDetailViewV2.kt` is the active implementation.

- [ ] **Step 1: Confirm no import or navigation reference to V1**

```bash
grep -rn 'MatchDetailView\b' \
  app/src/main/kotlin/com/tanush/bouncer/ui/navigation/ \
  app/src/main/kotlin/com/tanush/bouncer/ui/MainAppContainer.kt 2>/dev/null
```

Expected: 0 results (only V2 is wired in nav).

- [ ] **Step 2: Delete the file**

```bash
rm 'app/src/main/kotlin/com/tanush/bouncer/ui/MatchDetailView.kt'
```

- [ ] **Step 3: Build to confirm no regressions**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: delete unused V1 MatchDetailView — dead code compiled into APK"
```

---

## Task 15: Move `parseCommentaryType` out of UI into `utils/CommentaryParser.kt`

**Files:**
- Create: `app/src/main/kotlin/com/tanush/bouncer/utils/CommentaryParser.kt`
- Modify: `app/src/main/kotlin/com/tanush/bouncer/ui/components/LiveCommentaryBubbles.kt`

`parseCommentaryType(text, runs): CommentaryEventType` is business logic sitting in a Composable component file.

- [ ] **Step 1: Find the full function**

```bash
grep -n 'fun parseCommentaryType\|enum class CommentaryEventType' \
  app/src/main/kotlin/com/tanush/bouncer/ui/components/LiveCommentaryBubbles.kt
```

Read the full function body and the `CommentaryEventType` enum definition.

- [ ] **Step 2: Create `CommentaryParser.kt`**

```kotlin
// app/src/main/kotlin/com/tanush/bouncer/utils/CommentaryParser.kt
package com.tanush.bouncer.utils

// Moved from ui/components/LiveCommentaryBubbles.kt — domain logic must not live in UI layer
fun parseCommentaryType(text: String, runs: Int?): CommentaryEventType {
    // PASTE the full existing function body here exactly
}
```

If `CommentaryEventType` is defined inside `LiveCommentaryBubbles.kt`, move it too:

```kotlin
enum class CommentaryEventType {
    WICKET, SIX, FOUR, DOT, WIDE, NO_BALL, NORMAL
    // paste existing values
}
```

- [ ] **Step 3: Update `LiveCommentaryBubbles.kt`**

Remove the function and enum definitions. Add import:

```kotlin
import com.tanush.bouncer.utils.CommentaryEventType
import com.tanush.bouncer.utils.parseCommentaryType
```

- [ ] **Step 4: Check for other callers of `parseCommentaryType`**

```bash
grep -rn 'parseCommentaryType\|CommentaryEventType' \
  app/src/main/kotlin/com/tanush/bouncer/ | grep -v 'CommentaryParser.kt'
```

Update any callers to use the new package import.

- [ ] **Step 5: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/utils/CommentaryParser.kt \
        app/src/main/kotlin/com/tanush/bouncer/ui/components/LiveCommentaryBubbles.kt
git commit -m "refactor(commentary): move parseCommentaryType from UI component to utils/CommentaryParser.kt"
```

---

## Task 16: Centralize SharedPreferences keys — create `PrefsRegistry`

**Files:**
- Create: `app/src/main/kotlin/com/tanush/bouncer/utils/PrefsRegistry.kt`
- Modify: `ui/components/v2/ReminderBottomSheet.kt` (inline `"bouncer_reminders"`)
- Modify: `ui/components/v2/MatchNotificationPrefsSheet.kt` (inline `"bouncer_match_notif_prefs"`)
- Modify: `services/ScorePollingWorker.kt` (`PREFS_NAME = "score_polling_cache"`)
- Modify: `managers/MatchSubscriptionManager.kt` (`PREFS_NAME = "match_subscriptions"`)
- Modify: `ui/screens/NotificationPrefsManager.kt` (`PREFS_NAME` constant)

- [ ] **Step 1: Create `PrefsRegistry.kt`**

```kotlin
// app/src/main/kotlin/com/tanush/bouncer/utils/PrefsRegistry.kt
package com.tanush.bouncer.utils

object PrefsRegistry {
    // Prefs file names
    const val PREFS_APP = "bouncer_prefs"
    const val PREFS_REMINDERS = "bouncer_reminders"
    const val PREFS_MATCH_NOTIF = "bouncer_match_notif_prefs"
    const val PREFS_SCORE_POLLING = "score_polling_cache"
    const val PREFS_MATCH_SUBSCRIPTIONS = "match_subscriptions"
    const val PREFS_NOTIFICATION = "bouncer_notification_prefs"
    const val PREFS_FAVOURITES = "favourite_teams"

    // Keys within PREFS_SCORE_POLLING
    object ScorePolling {
        const val FOLLOWUP_COUNT = "live_followup_count"
    }

    // Keys within PREFS_MATCH_SUBSCRIPTIONS
    object MatchSubscriptions {
        const val SUBSCRIBED_IDS = "subscribed_match_ids"
    }

    // Keys within PREFS_REMINDERS
    object Reminders {
        const val REMINDER_PREFIX = "reminder_"
    }
}
```

- [ ] **Step 2: Update each file to reference `PrefsRegistry`**

`ReminderBottomSheet.kt`:
```kotlin
// Before:
val prefs = context.getSharedPreferences("bouncer_reminders", Context.MODE_PRIVATE)
// After:
val prefs = context.getSharedPreferences(PrefsRegistry.PREFS_REMINDERS, Context.MODE_PRIVATE)
```

`MatchNotificationPrefsSheet.kt`:
```kotlin
// Before:
val prefs = context.getSharedPreferences("bouncer_match_notif_prefs", Context.MODE_PRIVATE)
// After:
val prefs = context.getSharedPreferences(PrefsRegistry.PREFS_MATCH_NOTIF, Context.MODE_PRIVATE)
```

`ScorePollingWorker.kt`:
```kotlin
// Before:
private const val PREFS_NAME = "score_polling_cache"
private const val KEY_FOLLOWUP_COUNT = "live_followup_count"
// After:
private val PREFS_NAME = PrefsRegistry.PREFS_SCORE_POLLING
private val KEY_FOLLOWUP_COUNT = PrefsRegistry.ScorePolling.FOLLOWUP_COUNT
```

`MatchSubscriptionManager.kt`:
```kotlin
// Before:
private const val PREFS_NAME = "match_subscriptions"
private const val KEY_SUBSCRIBED = "subscribed_match_ids"
// After:
private val PREFS_NAME = PrefsRegistry.PREFS_MATCH_SUBSCRIPTIONS
private val KEY_SUBSCRIBED = PrefsRegistry.MatchSubscriptions.SUBSCRIBED_IDS
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/utils/PrefsRegistry.kt
git add app/src/main/kotlin/com/tanush/bouncer/ui/components/v2/ReminderBottomSheet.kt \
        app/src/main/kotlin/com/tanush/bouncer/ui/components/v2/MatchNotificationPrefsSheet.kt \
        app/src/main/kotlin/com/tanush/bouncer/services/ScorePollingWorker.kt \
        app/src/main/kotlin/com/tanush/bouncer/managers/MatchSubscriptionManager.kt
git commit -m "refactor(prefs): centralize all SharedPreferences file names and keys in PrefsRegistry"
```

---

## Task 17: Consolidate duplicate `MatchDetails` data classes

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/network/CricBuzzScraper.kt` (line 433)
- Read first: `app/src/main/kotlin/com/tanush/bouncer/network/models/ApiResponseModels.kt:41`

**Problem:** Two `MatchDetails` data classes exist independently. Codex defined both. The canonical one is in `ApiResponseModels.kt`; the nested one in `CricBuzzScraper` is an internal parsing artifact.

- [ ] **Step 1: Read both definitions**

```bash
grep -n 'data class MatchDetails' \
  app/src/main/kotlin/com/tanush/bouncer/network/models/ApiResponseModels.kt \
  app/src/main/kotlin/com/tanush/bouncer/network/CricBuzzScraper.kt
```

Read 20 lines after each to compare fields.

- [ ] **Step 2: Determine overlap**

If `CricBuzzScraper.MatchDetails` fields are a strict subset of `ApiResponseModels.MatchDetails`, replace all uses inside `CricBuzzScraper` of the nested type with the canonical one.

If `CricBuzzScraper.MatchDetails` has unique fields not in the canonical type, add them to `ApiResponseModels.MatchDetails` as nullable fields.

- [ ] **Step 3: Remove the nested class and update usages**

```kotlin
// In CricBuzzScraper.kt — remove the nested data class:
// data class MatchDetails(...) {  ← DELETE THIS

// Replace references to CricBuzzScraper.MatchDetails with the canonical import:
import com.tanush.bouncer.network.models.MatchDetails
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

Fix any field name mismatches revealed by compilation.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/network/CricBuzzScraper.kt \
        app/src/main/kotlin/com/tanush/bouncer/network/models/ApiResponseModels.kt
git commit -m "refactor(models): remove duplicate CricBuzzScraper.MatchDetails nested class, use canonical ApiResponseModels.MatchDetails"
```

---

## Task 18: Modernize `ViewModelProvider.Factory` with `viewModelFactory` lambda

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/viewmodels/HighlightsViewModel.kt`
- Modify: `app/src/main/kotlin/com/tanush/bouncer/viewmodels/MatchDetailViewModel.kt`
- (Other ViewModels with companion factory objects follow the same pattern)

Replace the `object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") ... }` boilerplate with the `viewModelFactory {}` lambda from `lifecycle-viewmodel-ktx`.

- [ ] **Step 1: Update HighlightsViewModel factory (already fixed to AndroidViewModel in Task 7)**

```kotlin
// Before (after Task 7):
companion object {
    fun factory(context: Context, matchId: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HighlightsViewModel(context.applicationContext as Application, matchId) as T
        }
}

// After:
companion object {
    fun factory(context: Context, matchId: String) = viewModelFactory {
        initializer {
            HighlightsViewModel(
                checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]),
                matchId
            )
        }
    }
}
```

Add `import androidx.lifecycle.viewModelFactory` and `import androidx.lifecycle.ViewModelProvider`.

- [ ] **Step 2: Update MatchDetailViewModel factory**

```kotlin
// Before:
companion object {
    fun factory(context: Context, match: CricketMatch): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MatchDetailViewModel(match, context) as T
        }
}

// After:
companion object {
    fun factory(context: Context, match: CricketMatch) = viewModelFactory {
        initializer { MatchDetailViewModel(match, context.applicationContext) }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | grep -E 'error:|BUILD'
```

- [ ] **Step 4: Run all unit tests**

```bash
./gradlew test --no-daemon 2>&1 | tail -20
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/viewmodels/HighlightsViewModel.kt \
        app/src/main/kotlin/com/tanush/bouncer/viewmodels/MatchDetailViewModel.kt
git commit -m "refactor(viewmodels): replace ViewModelProvider.Factory boilerplate with viewModelFactory lambda"
```

---

## Verification

After all tasks:

```bash
# Full build
./gradlew build --no-daemon 2>&1 | tail -20

# All unit tests
./gradlew test --no-daemon 2>&1 | tail -30

# Static analysis
./gradlew detekt --no-daemon 2>&1 | grep -E 'error|warning|BUILD'
```

Install on emulator/device and manually verify:
1. Open a match detail screen for an upcoming match (no toss yet) — should NOT crash
2. Open series list — any error should now appear in Sentry, not silently disappear
3. Open search — type a query, confirm recent queries work (no context errors in logcat)
4. Subscribe to 0 matches, wait for ScorePollingWorker to fire — confirm logcat shows "Skipped — no active subscriptions"
5. Open any screen with a list (commentary, highlights, squad) — confirm smooth scrolling under rapid data updates
