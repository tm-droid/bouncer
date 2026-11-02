# Search Lazy-Loading & Infinite Scroll — Design Spec

**Date:** 2026-05-04  
**Status:** Approved  
**Scope:** SearchRepository · SearchViewModel · SearchScreen · DAOs (Series, Historical, Player)

---

## Problem

`SearchRepository.searchFromDb()` and `searchPlayersFromDb()` load full result sets into memory. On large DB caches (1K+ series, 1K+ historical matches, 1K+ players) this causes OOM. Hard-coded `LIMIT 5/10` in DAO queries silently discards results past the cap.

---

## Goals

- Infinite scroll for series, historical matches, players (all DB-backed tabs)
- Memory-bounded: only one page (20 rows) in heap at a time
- No regression on live data tabs (ALL, MATCHES live, TEAMS, NEWS)
- Paging metadata in Sentry breadcrumbs
- Tests verify PagingSource load logic

---

## Non-Goals

- Paging live (in-memory) results — those are bounded by the match list size
- Paging NEWS or TEAMS tabs — not DB-backed
- Changing the ALL tab — stays as current `SearchResults` StateFlow

---

## Architecture

```
SearchScreen
  ├── ALL tab          → SearchResults (StateFlow, unchanged)
  ├── MATCHES tab      → live header (StateFlow) + historicalPagingData (PagingData)
  ├── PLAYERS tab      → live header (StateFlow) + playerDbPagingData (PagingData)
  ├── TOURNAMENTS tab  → seriesPagingData (PagingData, no live header)
  ├── TEAMS tab        → SearchResults.teams (StateFlow, unchanged)
  └── NEWS tab         → SearchResults.news (StateFlow, unchanged)

SearchViewModel
  ├── _results: StateFlow<SearchResults>         (unchanged — live data)
  ├── seriesPagingData: Flow<PagingData<SeriesResult>>
  ├── historicalPagingData: Flow<PagingData<HistoricalMatchResult>>
  └── playerDbPagingData: Flow<PagingData<PlayerResult>>

PagingSources (data/paging/)
  ├── SeriesSearchPagingSource(dao, ftsQuery)
  ├── HistoricalSearchPagingSource(dao, ftsQuery)
  └── PlayerSearchPagingSource(dao, ftsQuery)

DAOs (new paged methods, existing kept)
  ├── SeriesCacheDao.searchSeriesPage(query, limit, offset): List<SeriesCacheEntity>
  ├── HistoricalMatchCacheDao.searchHistoricalPage(query, limit, offset): List<HistoricalMatchCacheEntity>
  └── PlayerCacheDao.searchPlayersPage(query, limit, offset): List<PlayerCacheEntity>
```

---

## Layer 1 — DAOs

**Approach:** Add explicit `LIMIT :limit OFFSET :offset` paged variants. Keep existing `searchSeries()` / `searchHistoricalMatches()` / `searchPlayers()` methods (used by legacy code paths in `SearchRepository.searchFromDb()` / `searchPlayersFromDb()`).

**Why custom SQL over Room PagingSource return type:** Room's `LimitOffsetPagingSource` wraps the query as `SELECT * FROM (<original>) LIMIT ? OFFSET ?`. FTS4 virtual-table subqueries inside correlated wrapping have known SQLite version edge cases. Explicit LIMIT/OFFSET in the DAO query avoids the wrapping entirely.

```kotlin
// SeriesCacheDao — new method
@Query("""
    SELECT * FROM series_cache
    WHERE seriesId IN (SELECT seriesId FROM series_fts WHERE series_fts MATCH :query)
    ORDER BY name ASC
    LIMIT :limit OFFSET :offset
""")
suspend fun searchSeriesPage(query: String, limit: Int, offset: Int): List<SeriesCacheEntity>

// HistoricalMatchCacheDao — new method
@Query("""
    SELECT * FROM historical_match_cache
    WHERE matchId IN (SELECT matchId FROM historical_match_fts WHERE historical_match_fts MATCH :query)
    ORDER BY date DESC
    LIMIT :limit OFFSET :offset
""")
suspend fun searchHistoricalPage(query: String, limit: Int, offset: Int): List<HistoricalMatchCacheEntity>

// PlayerCacheDao — new method
@Query("""
    SELECT * FROM player_cache
    WHERE playerId IN (SELECT playerId FROM player_fts WHERE player_fts MATCH :query)
    ORDER BY name ASC
    LIMIT :limit OFFSET :offset
""")
suspend fun searchPlayersPage(query: String, limit: Int, offset: Int): List<PlayerCacheEntity>
```

---

## Layer 2 — PagingSources

Three classes in `data/paging/`. Shared contract:

| Property | Value |
|---|---|
| Key type | `Int` (byte offset into result set) |
| Page size | 20 |
| Prefetch distance | 5 |
| `prevKey` | `null` if `offset == 0`, else `offset - loadSize` |
| `nextKey` | `null` if `result.size < loadSize`, else `offset + loadSize` |
| `getRefreshKey()` | `null` — restart from page 0 on invalidation |
| Error handling | `LoadResult.Error(e)` on exception |

Each PagingSource maps entity → `SearchResult.*` subclass inside `load()`. No separate mapping step.

**Sentry breadcrumb per page load:**

```kotlin
Sentry.addBreadcrumb(Breadcrumb().apply {
    category = "search.paging"
    message = "page loaded"
    data["tab"] = "<series|historical|players>"
    data["query"] = query
    data["offset"] = offset
    data["count"] = result.size
})
```

---

## Layer 3 — SearchViewModel

Existing `_results: StateFlow<SearchResults>` unchanged. Three new paging flows added:

```kotlin
val seriesPagingData: Flow<PagingData<SearchResult.SeriesResult>> =
    _query
        .debounce(300)
        .filter { it.trim().length >= 2 }
        .flatMapLatest { q ->
            val ftsQ = buildFtsQuery(q)
            Pager(PagingConfig(pageSize = 20, prefetchDistance = 5)) {
                SeriesSearchPagingSource(db.seriesCacheDao(), ftsQ)
            }.flow
        }
        .cachedIn(viewModelScope)

// Same pattern for historicalPagingData and playerDbPagingData
```

`buildFtsQuery(q)`: `"\"${q.trim().replace("\"", "\"\"")}\""` — extracted to a shared private function, reused across all three flows and the existing `searchFromDb`/`searchPlayersFromDb` methods.

**Query change → new PagingSource:** `flatMapLatest` cancels the previous `Pager.flow` collection and starts a new one automatically. `cachedIn` holds one page in the ViewModel scope across recompositions.

---

## Layer 4 — SearchScreen

**Paging imports:** `collectAsLazyPagingItems()`, `items(lazyPagingItems)` from `androidx.paging.compose`.

### MATCHES tab

```
LazyColumn {
    // Live header section
    item { SectionHeader("Live Matches") }
    items(results.matches) { MatchCard(it) }

    // Paged DB historical section
    item { SectionHeader("Historical") }
    items(historicalItems) { HistoricalMatchRow(it) }

    // Append state footer
    item { AppendStateFooter(historicalItems.loadState.append) }
}
```

### PLAYERS tab

Same structure: live players header (from `results.players`) + paged DB players (`playerDbPagingItems`).

### TOURNAMENTS tab

Pure paged list — no live header:

```
LazyColumn {
    items(seriesItems) { SeriesRow(it) }
    item { AppendStateFooter(seriesItems.loadState.append) }
}
```

### AppendStateFooter composable

Shared composable handling three append states:
- `Loading` → centered `CircularProgressIndicator`
- `Error` → error text + "Retry" button calling `lazyItems.retry()`
- `NotLoading(endOfPaginationReached=true)` → nothing (or subtle "End" text)

---

## Layer 5 — Tests

Location: `app/src/test/java/com/tanush/bouncer/data/paging/`

Three test classes: `SeriesSearchPagingSourceTest`, `HistoricalSearchPagingSourceTest`, `PlayerSearchPagingSourceTest`.

Each class tests:

| Test | Description |
|---|---|
| `firstPageLoadsCorrectly` | Refresh with `offset=0`, 20 results returned → `prevKey=null`, `nextKey=20` |
| `secondPageLoadsCorrectly` | Append with `offset=20` → `prevKey=0`, `nextKey=40` |
| `lastPageSetsNullNextKey` | Load returns `< pageSize` rows → `nextKey=null` |
| `emptyResultSetsNullNextKey` | Load returns 0 rows → `nextKey=null`, `prevKey=null` |
| `daoExceptionReturnsError` | DAO throws → `LoadResult.Error` |

Mock DAO via a hand-written fake (no Mockito) returning a fixed list sliced by `limit`/`offset`.

---

## Sentry Integration

Breadcrumbs emitted in `PagingSource.load()` (see Layer 2). No new custom metrics — breadcrumb trail in Sentry is sufficient to reconstruct paging behavior for a session.

---

## Migration / Backward Compat

- Existing `searchFromDb()` and `searchPlayersFromDb()` in `SearchRepository` unchanged
- Existing DAO methods unchanged
- New DAO methods added alongside — no DB schema change, no migration needed
- `SearchResults.series` and `SearchResults.historicalMatches` fields kept on the data class (ALL tab still uses them)

---

## Open Questions

None — all resolved during design review.
