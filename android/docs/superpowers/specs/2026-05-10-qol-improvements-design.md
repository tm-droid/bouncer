# QoL Improvements Design

**Date:** 2026-05-10  
**Scope:** 4 targeted fixes across state management, commentary UX, and lifecycle reliability.

---

## Context

Daily use surfaces two categories of friction:
1. **State resets** — format filter in Matches tab resets every tab switch because it lives in local `remember` state instead of the ViewModel.
2. **Silent failures** — commentary shows the same blank state whether it's loading or genuinely empty; LiveCommentaryManager coroutine scopes are never cancelled on cleanup; a SeriesSyncWorker skip-check is dead code that misleads readers.

None of these require new features or new patterns. All follow existing conventions exactly.

---

## Fix 1: Format Filter Persistence

**Problem:** `AllMatchesScreenV2.kt:53` — `var selectedFormat by remember { mutableStateOf("All") }` resets on every tab switch.

**Solution:** Move to `LiveActivityViewModel` alongside `_selectedStatus` and `_selectedCompetitionType`.

### Changes

**`LiveActivityViewModel.kt`** — add after `_selectedCompetitionType`:
```kotlin
private val _selectedFormat = MutableStateFlow("All")
val selectedFormat: StateFlow<String> = _selectedFormat.asStateFlow()

fun setFormat(format: String) {
    _selectedFormat.value = format
}
```

**`AllMatchesScreenV2.kt`** — replace lines 52–66:
```kotlin
val selectedFormat by viewModel.selectedFormat.collectAsState()

val filteredMatches = if (selectedFormat == "All") {
    currentMatches
} else {
    currentMatches.filter { it.matchType.contains(selectedFormat, ignoreCase = true) }
}
```

Update `onItemSelected` in the format chip row:
```kotlin
onItemSelected = { viewModel.setFormat(it) }
```

---

## Fix 2: Commentary Loading State Clarity

**Problem:** `MatchDetailViewV2.kt:100` initializes `commentary = listOf("Loading commentary...")`. `CommentaryTabV2.kt:113` checks for this and falls through to `EmptyStateV2` — so loading and genuinely-empty commentary look identical.

**Solution:** Add an explicit `isLoading` param to `CommentaryTabV2`. Show a centered `CircularProgressIndicator` when loading, `EmptyStateV2` when truly empty.

### Changes

**`CommentaryTabV2.kt`** — update signature:
```kotlin
fun CommentaryTabV2(
    commentary: List<String>,
    recentBalls: List<String>,
    matchStatus: String = "",
    isLoading: Boolean = false,   // new
)
```

Replace the check at lines 113–124:
```kotlin
if (isLoading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = colors.primary)
    }
    return
}
if (commentary.isEmpty()) {
    // existing status-aware EmptyStateV2 logic
}
```

**`MatchDetailViewV2.kt`** — add local loading state:
```kotlin
var commentaryLoading by remember { mutableStateOf(true) }
```

Set `commentaryLoading = false` in a `finally` block wrapping the seed try-catch (lines 128–135), so a failed seed doesn't leave the spinner stuck:
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

Pass to `CommentaryTabV2`:
```kotlin
2 -> CommentaryTabV2(
    commentary = commentary,
    recentBalls = recentBalls,
    matchStatus = match.currentStatus,
    isLoading = commentaryLoading,
)
```

---

## Fix 3: LiveCommentaryManager Lifecycle Leaks

**Problem:** Two `LiveCommentaryManager` instances hold a `SupervisorJob`-backed coroutine scope that is never cancelled:
1. `LiveActivityViewModel.kt:61` — ViewModel-owned instance, no `onCleared()` override
2. `MatchDetailViewV2.kt:112` — composable `remember { LiveCommentaryManager() }`, no `DisposableEffect`

**Solution:** Cancel the scope at the correct lifecycle boundary.

### Changes

**`LiveActivityViewModel.kt`** — add `onCleared()`:
```kotlin
override fun onCleared() {
    super.onCleared()
    liveCommentaryManager.close()
}
```

**`MatchDetailViewV2.kt`** — wrap the `remember` with `DisposableEffect`:
```kotlin
// replace:
val commentaryManager = remember { LiveCommentaryManager() }

// with:
val commentaryManager = remember { LiveCommentaryManager() }
DisposableEffect(match.id) {
    onDispose { commentaryManager.close() }
}
```

---

## Fix 4: SeriesSyncWorker Dead Skip Check

**Problem:** `SeriesSyncWorker.kt:36–46` checks `(now - lastSync) < 12h` before syncing. The worker is scheduled every 24h via `PeriodicWorkRequestBuilder`, so by the time it runs, last sync is always ≥ 24h ago. The 12h check never fires — dead code that contradicts its own comment.

**Solution:** Remove the skip block. The `ExistingPeriodicWorkPolicy.KEEP` guard on enqueue already prevents duplicate runs.

### Changes

**`SeriesSyncWorker.kt`** — delete lines 36–46:
```kotlin
// Remove this entire block:
val twelveHoursMs = 12L * 60 * 60 * 1000
if (lastSync != null && (now - lastSync) < twelveHoursMs) {
    Log.d(TAG, "Skipping sync — last sync was ${(now - lastSync) / 1000 / 60} min ago")
    Sentry.addBreadcrumb(...)
    return Result.success()
}
```

Keep the `val lastSync = db.seriesCacheDao().getLastSyncTime()` and `val now = System.currentTimeMillis()` lines only if they are used elsewhere; if not, remove them too.

---

## Files Modified

| File | Change |
|------|--------|
| `viewmodels/LiveActivityViewModel.kt` | Add `_selectedFormat` StateFlow + `setFormat()` + `onCleared()` |
| `ui/screens/AllMatchesScreenV2.kt` | Collect `selectedFormat` from ViewModel |
| `ui/screens/matchdetail/v2/CommentaryTabV2.kt` | Add `isLoading` param, show `CircularProgressIndicator` |
| `ui/MatchDetailViewV2.kt` | Add `commentaryLoading` state + `DisposableEffect` cleanup |
| `services/SeriesSyncWorker.kt` | Remove dead 12h skip block |

---

## Verification

1. **Format filter:** Switch to Matches tab, select "T20", navigate to Home, return — filter should still show "T20"
2. **Commentary loading:** Open a live match, switch to Commentary tab immediately — should show spinner until first data arrives, not empty state
3. **Lifecycle:** Use Android Studio's Memory Profiler or LeakCanary — no `LiveCommentaryManager` instances should survive ViewModel/composable destruction
4. **SeriesSyncWorker:** Confirm worker runs correctly on device; check Logcat for "Series sync succeeded" without any "Skipping sync" lines
