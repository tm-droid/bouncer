# QoL Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Build commands:** All `./gradlew` invocations must run via `mcp__plugin_context-mode_context-mode__ctx_execute(language: "shell", code: "...")` — a session hook blocks raw Bash for commands producing >20 lines output.

**Goal:** Fix 4 concrete daily-use friction points: format filter resets on tab switch, commentary spinner vs empty-state ambiguity, LiveCommentaryManager coroutine leaks, and dead 12h skip logic in SeriesSyncWorker.

**Architecture:** All changes are isolated to existing files. No new abstractions. Format filter moves from composable `remember` to an existing ViewModel StateFlow; commentary adds one `Boolean` param; lifecycle adds `onCleared` + `DisposableEffect`; worker deletes dead lines.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, WorkManager, Room

---

## Files Modified

| File | What changes |
|------|-------------|
| `app/src/main/kotlin/com/tanush/bouncer/viewmodels/LiveActivityViewModel.kt` | Add `_selectedFormat` StateFlow + `setFormat()` + `onCleared()` |
| `app/src/main/kotlin/com/tanush/bouncer/ui/screens/AllMatchesScreenV2.kt` | Collect `selectedFormat` from ViewModel instead of local `remember` |
| `app/src/main/kotlin/com/tanush/bouncer/ui/screens/matchdetail/v2/CommentaryTabV2.kt` | Add `isLoading: Boolean` param; show `CircularProgressIndicator` when loading |
| `app/src/main/kotlin/com/tanush/bouncer/ui/MatchDetailViewV2.kt` | Add `commentaryLoading` state + `DisposableEffect` for cleanup |
| `app/src/main/kotlin/com/tanush/bouncer/services/SeriesSyncWorker.kt` | Remove dead 12h skip block |
| `app/src/test/java/com/tanush/bouncer/viewmodels/LiveActivityViewModelTest.kt` | New: unit test for `setFormat` |

---

## Task 1: Format Filter — ViewModel StateFlow

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/viewmodels/LiveActivityViewModel.kt`
- Create: `app/src/test/java/com/tanush/bouncer/viewmodels/LiveActivityViewModelTest.kt`

- [ ] **Step 1.1: Write the failing test**

Create `app/src/test/java/com/tanush/bouncer/viewmodels/LiveActivityViewModelTest.kt`:

```kotlin
package com.tanush.bouncer.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveActivityViewModelTest {

    @Test
    fun `selectedFormat starts as All`() {
        val vm = LiveActivityViewModel()
        assertEquals("All", vm.selectedFormat.value)
    }

    @Test
    fun `setFormat updates selectedFormat`() {
        val vm = LiveActivityViewModel()
        vm.setFormat("T20")
        assertEquals("T20", vm.selectedFormat.value)
    }

    @Test
    fun `setFormat to All resets filter`() {
        val vm = LiveActivityViewModel()
        vm.setFormat("ODI")
        vm.setFormat("All")
        assertEquals("All", vm.selectedFormat.value)
    }
}
```

- [ ] **Step 1.2: Run test to verify it fails**

```shell
./gradlew test --tests "*LiveActivityViewModelTest"
```

Expected: FAIL — `selectedFormat` property not found on `LiveActivityViewModel`.

- [ ] **Step 1.3: Add StateFlow and setter to LiveActivityViewModel**

In `LiveActivityViewModel.kt`, after the existing `_selectedCompetitionType` block (around line 78), add:

```kotlin
private val _selectedFormat = MutableStateFlow("All")
val selectedFormat: StateFlow<String> = _selectedFormat.asStateFlow()

fun setFormat(format: String) {
    _selectedFormat.value = format
}
```

No other changes needed in this file for this task.

- [ ] **Step 1.4: Run test to verify it passes**

```shell
./gradlew test --tests "*LiveActivityViewModelTest"
```

Expected: 3 tests PASS.

- [ ] **Step 1.5: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/viewmodels/LiveActivityViewModel.kt \
        app/src/test/java/com/tanush/bouncer/viewmodels/LiveActivityViewModelTest.kt
git commit -m "feat(matches): add selectedFormat StateFlow to LiveActivityViewModel"
```

---

## Task 2: Format Filter — Wire UI to ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/ui/screens/AllMatchesScreenV2.kt`

- [ ] **Step 2.1: Replace local `remember` with ViewModel collection**

Open `AllMatchesScreenV2.kt`. Find line 53:

```kotlin
// REMOVE this line:
var selectedFormat by remember { mutableStateOf("All") }
```

Replace with:

```kotlin
val selectedFormat by viewModel.selectedFormat.collectAsState()
```

The `remember` import is no longer needed for this variable; leave it if used elsewhere in the file.

- [ ] **Step 2.2: Update the format chip row's onItemSelected**

Find the format `FilterChipRowV2` call (around line 101–107). Change `onItemSelected`:

```kotlin
// Before:
onItemSelected = { selectedFormat = it }

// After:
onItemSelected = { viewModel.setFormat(it) }
```

The `filteredMatches` derivation (lines 61–67) reads `selectedFormat` — no change needed there; it now reads from the StateFlow-backed delegate instead of local state.

- [ ] **Step 2.3: Build to verify no compile errors**

```shell
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.4: Manual verification on device/emulator**

```shell
./gradlew installDebug
```

1. Open app → Matches tab
2. Select "T20" from format chips
3. Navigate to Home tab
4. Return to Matches tab
5. Confirm filter still shows "T20" (was "All" before this fix)

- [ ] **Step 2.5: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/ui/screens/AllMatchesScreenV2.kt
git commit -m "fix(matches): persist format filter across tab switches via ViewModel"
```

---

## Task 3: Commentary Loading State — CommentaryTabV2

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/ui/screens/matchdetail/v2/CommentaryTabV2.kt`

- [ ] **Step 3.1: Add `isLoading` param and `CircularProgressIndicator` import**

Open `CommentaryTabV2.kt`. Add import near the top with other `material3` imports:

```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
```

(Check if `Box` is already imported — it likely is given the existing layout code. Only add what's missing.)

- [ ] **Step 3.2: Update function signature**

Find `fun CommentaryTabV2(` (line 104). Change to:

```kotlin
fun CommentaryTabV2(
    commentary: List<String>,
    recentBalls: List<String>,
    matchStatus: String = "",
    isLoading: Boolean = false,
) {
```

- [ ] **Step 3.3: Replace the existing empty-state check with loading + empty states**

Find lines 113–124 (the existing empty/loading check):

```kotlin
// REMOVE this block:
if (commentary.isEmpty() || (commentary.size == 1 && commentary.first().contains("Loading"))) {
    val statusLower = matchStatus.lowercase()
    val (title, subtitle) = when {
        statusLower.contains("stumps") -> "Stumps" to "Commentary will resume at start of play"
        statusLower.contains("break") -> "Session Break" to "Commentary will resume after the break"
        statusLower.contains("rain") || statusLower.contains("delay") -> "Rain Delay" to "Commentary will resume when play restarts"
        statusLower.contains("won") || statusLower.contains("drawn") -> "Match Complete" to "No more commentary available"
        else -> "No commentary yet" to "Commentary will appear once the match starts"
    }
    EmptyStateV2(title = title, subtitle = subtitle)
    return
}
```

Replace with:

```kotlin
if (isLoading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = colors.primary)
    }
    return
}

if (commentary.isEmpty()) {
    val statusLower = matchStatus.lowercase()
    val (title, subtitle) = when {
        statusLower.contains("stumps") -> "Stumps" to "Commentary will resume at start of play"
        statusLower.contains("break") -> "Session Break" to "Commentary will resume after the break"
        statusLower.contains("rain") || statusLower.contains("delay") -> "Rain Delay" to "Commentary will resume when play restarts"
        statusLower.contains("won") || statusLower.contains("drawn") -> "Match Complete" to "No more commentary available"
        else -> "No commentary yet" to "Commentary will appear once the match starts"
    }
    EmptyStateV2(title = title, subtitle = subtitle)
    return
}
```

- [ ] **Step 3.4: Build to verify no compile errors**

```shell
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. (The existing call site in `MatchDetailViewV2.kt` still compiles because `isLoading` defaults to `false`.)

- [ ] **Step 3.5: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/ui/screens/matchdetail/v2/CommentaryTabV2.kt
git commit -m "feat(commentary): add isLoading param to distinguish loading from empty state"
```

---

## Task 4: Commentary Loading State — Wire MatchDetailViewV2

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/ui/MatchDetailViewV2.kt`

- [ ] **Step 4.1: Add `commentaryLoading` state variable**

Open `MatchDetailViewV2.kt`. After the `var commentary by remember { mutableStateOf(listOf("Loading commentary...")) }` line (line 100), add:

```kotlin
var commentaryLoading by remember { mutableStateOf(true) }
```

- [ ] **Step 4.2: Wrap the seed block in try/finally to clear loading state**

Find the initial seed block inside `LaunchedEffect(match.id)` (lines 128–135):

```kotlin
// BEFORE:
try {
    val url = "https://www.cricbuzz.com/live-cricket-scores/${match.id}"
    val allLines = scraper.fetchLiveCommentary(url)
    if (allLines.isNotEmpty()) {
        commentaryManager.seedHistory(allLines)
        commentary = commentaryManager.getSeededHistory()
    }
} catch (_: Exception) { }
```

Replace with:

```kotlin
try {
    val url = "https://www.cricbuzz.com/live-cricket-scores/${match.id}"
    val allLines = scraper.fetchLiveCommentary(url)
    if (allLines.isNotEmpty()) {
        commentaryManager.seedHistory(allLines)
        commentary = commentaryManager.getSeededHistory()
    }
} catch (_: Exception) {
} finally {
    commentaryLoading = false
}
```

- [ ] **Step 4.3: Pass `isLoading` to CommentaryTabV2**

Find the `CommentaryTabV2` call site (around line 322):

```kotlin
// BEFORE:
2 -> CommentaryTabV2(
    commentary = commentary,
    recentBalls = recentBalls,
    matchStatus = match.currentStatus,
)
```

Replace with:

```kotlin
2 -> CommentaryTabV2(
    commentary = commentary,
    recentBalls = recentBalls,
    matchStatus = match.currentStatus,
    isLoading = commentaryLoading,
)
```

- [ ] **Step 4.4: Build to verify no compile errors**

```shell
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.5: Manual verification on device/emulator**

```shell
./gradlew installDebug
```

1. Open any live or recent match
2. Tap the "Commentary" tab immediately after opening
3. Should see `CircularProgressIndicator` for 1–3 seconds while seed fetch runs
4. Spinner should disappear and commentary appear (or show empty state for finished matches)
5. Previously: "No commentary yet" flashed immediately even while loading

- [ ] **Step 4.6: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/ui/MatchDetailViewV2.kt
git commit -m "fix(commentary): show spinner while initial commentary loads"
```

---

## Task 5: LiveCommentaryManager Lifecycle — ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/viewmodels/LiveActivityViewModel.kt`

- [ ] **Step 5.1: Add `onCleared` override**

Open `LiveActivityViewModel.kt`. Find the end of the class (after the last function, before the closing `}`). Add:

```kotlin
override fun onCleared() {
    super.onCleared()
    liveCommentaryManager.close()
}
```

`liveCommentaryManager` is the field defined at line 61: `private val liveCommentaryManager = LiveCommentaryManager()`. Its `close()` cancels the `supervisorJob` coroutine scope.

- [ ] **Step 5.2: Build to verify no compile errors**

```shell
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.3: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/viewmodels/LiveActivityViewModel.kt
git commit -m "fix(lifecycle): cancel LiveCommentaryManager scope in LiveActivityViewModel.onCleared"
```

---

## Task 6: LiveCommentaryManager Lifecycle — Composable

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/ui/MatchDetailViewV2.kt`

- [ ] **Step 6.1: Check for DisposableEffect import**

Open `MatchDetailViewV2.kt`. The existing imports include several `androidx.compose.runtime.*` items. Verify `DisposableEffect` is not already imported (it likely isn't). Add to the import block:

```kotlin
import androidx.compose.runtime.DisposableEffect
```

- [ ] **Step 6.2: Add DisposableEffect after the commentaryManager remember**

Find line 112:

```kotlin
val commentaryManager = remember { LiveCommentaryManager() }
```

Immediately after that line, add:

```kotlin
DisposableEffect(match.id) {
    onDispose { commentaryManager.close() }
}
```

This hooks into Compose's composition lifecycle: when `MatchDetailViewV2` leaves the composition (user navigates back), `commentaryManager.close()` cancels its `supervisorJob`.

- [ ] **Step 6.3: Build to verify no compile errors**

```shell
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.4: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/ui/MatchDetailViewV2.kt
git commit -m "fix(lifecycle): cancel commentaryManager coroutine scope on composable dispose"
```

---

## Task 7: SeriesSyncWorker Dead Code Removal

**Files:**
- Modify: `app/src/main/kotlin/com/tanush/bouncer/services/SeriesSyncWorker.kt`

- [ ] **Step 7.1: Remove the 12h skip block**

Open `SeriesSyncWorker.kt`. Inside `doWork()`, find lines 36–46 (the skip check):

```kotlin
// REMOVE these lines entirely:
val lastSync = db.seriesCacheDao().getLastSyncTime()
val now = System.currentTimeMillis()
val twelveHoursMs = 12L * 60 * 60 * 1000

if (lastSync != null && (now - lastSync) < twelveHoursMs) {
    Log.d(TAG, "Skipping sync — last sync was ${(now - lastSync) / 1000 / 60} min ago")
    Sentry.addBreadcrumb(Breadcrumb().apply {
        category = "series_sync"
        message = "Skipped — synced recently"
        level = SentryLevel.INFO
    })
    return Result.success()
}
```

The worker is scheduled via `PeriodicWorkRequestBuilder(24, TimeUnit.HOURS)` with `ExistingPeriodicWorkPolicy.KEEP` — it can never run twice within 12h. The check is dead code.

After removal, the `doWork()` body should flow directly from the Sentry breadcrumb at the top to `val series = SeriesScraper().fetchActiveSeries()`.

- [ ] **Step 7.2: Check if `db` declaration needs to move**

The `val db = BouncerDatabase.getInstance(context)` line was just before the removed block. It's still needed for `db.seriesCacheDao().upsertAll(entities)` later in the function. Verify it's still present and in scope after removal. If the removal accidentally deleted it, re-add:

```kotlin
val db = BouncerDatabase.getInstance(context)
```

immediately before `val series = SeriesScraper().fetchActiveSeries()`.

- [ ] **Step 7.3: Build to verify no compile errors**

```shell
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.4: Verify no unused imports**

Check if `getLastSyncTime` being removed leaves any unused import. If the IDE or build flags any, remove the import.

- [ ] **Step 7.5: Commit**

```bash
git add app/src/main/kotlin/com/tanush/bouncer/services/SeriesSyncWorker.kt
git commit -m "refactor(workers): remove dead 12h skip check in SeriesSyncWorker"
```

---

## Final Verification

- [ ] **Full build passes**

```shell
./gradlew build
```

Expected: BUILD SUCCESSFUL, 0 errors.

- [ ] **All unit tests pass**

```shell
./gradlew test
```

Expected: All tests green including the new `LiveActivityViewModelTest`.

- [ ] **Install and smoke test on device**

```shell
./gradlew installDebug
```

Checklist:
1. Matches tab → select "ODI" → switch tabs → return → filter is "ODI" ✓
2. Open live match → Commentary tab → see spinner → spinner resolves ✓
3. Open live match → navigate back → re-open (repeat 5×) → no OOM or memory growth ✓
4. Logcat: no "Skipping sync" lines from SeriesSyncWorker ✓
