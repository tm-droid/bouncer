package com.tanush.bouncer.data.remote

import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.data.CacheConfig
import io.appwrite.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppwriteHighlightsClient : AppwriteTableClient("AppwriteHighlightsClient") {

    fun initialize() {
        initTable(ApiKeys.appwriteHighlightsDbId, ApiKeys.appwriteHighlightsColId)
    }

    /**
     * One bounded page-run of highlights newer than [sinceFetchedAt].
     *
     * [hasMore] is true when the run stopped on the page cap rather than on running out of rows,
     * which lets the caller keep draining instead of waiting out its freshness throttle.
     */
    data class HighlightsPage(
        val rows: List<HighlightRow>,
        val maxFetchedAt: Long,
        val hasMore: Boolean,
    )

    /**
     * Incremental fetch, ordered oldest-first so the watermark can only move forward.
     *
     * This used to be an unbounded rescan of the entire table on every call, and Appwrite bills one
     * read per row returned — so a device with a few hundred highlight rows spent tens of thousands
     * of org reads a day re-fetching rows it already had. Steady state here is a single query that
     * returns nothing.
     */
    suspend fun getHighlightsSince(sinceFetchedAt: Long): HighlightsPage? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                if (tablesDB == null) return@runCatching null
                fetchSince(sinceFetchedAt)
            }.onFailure { e ->
                logSilentFailure("Fetch highlights failed", e)
            }.getOrNull()
        }
    }

    private suspend fun fetchSince(sinceFetchedAt: Long): HighlightsPage {
        val rows = mutableListOf<HighlightRow>()
        var lastId: String? = null
        var hasMore = false
        for (page in 1..MAX_PAGES_PER_RUN) {
            val result = guardedRead("highlights.page") {
                it.listRows(
                    databaseId = dbId,
                    tableId = tableId,
                    queries = pageQueries(sinceFetchedAt, lastId),
                )
            } ?: break
            @Suppress("UNCHECKED_CAST")
            rows.addAll(result.rows.map { HighlightRow.fromMap(it.data as? Map<String, Any?> ?: emptyMap()) })
            if (result.rows.size < PAGE_SIZE) break
            lastId = result.rows.last().id
            // A full last page means we stopped on the cap, not on exhaustion.
            hasMore = page == MAX_PAGES_PER_RUN
        }
        // Rows arrive ordered by fetched_at, but take the max explicitly rather than trusting the
        // ordering — a watermark that moved too far would skip rows permanently.
        val highest = rows.maxOfOrNull { it.fetchedAt } ?: sinceFetchedAt
        return HighlightsPage(rows, maxOf(highest, sinceFetchedAt), hasMore)
    }

    private fun pageQueries(sinceFetchedAt: Long, cursorId: String?) = buildList {
        add(Query.greaterThan(FIELD_FETCHED_AT, sinceFetchedAt))
        add(Query.orderAsc(FIELD_FETCHED_AT))
        add(Query.select(SELECTED_COLUMNS))
        add(Query.limit(PAGE_SIZE))
        if (cursorId != null) add(Query.cursorAfter(cursorId))
    }

    private const val PAGE_SIZE = 100

    // Bounds one call. A first-run backfill drains across consecutive calls via hasMore rather
    // than fetching an unbounded table in one go.
    private const val MAX_PAGES_PER_RUN = 3

    private const val FIELD_FETCHED_AT = "fetched_at"

    // Only what HighlightRow.toEntity() and the watermark actually consume. The omitted columns
    // (url, raw_match_id, both abbreviations, league season, end date) have no reader anywhere —
    // url alone is a varchar(2048). Absent columns fall back to defaults in HighlightRow.fromMap,
    // and the Realtime path is unaffected since it parses the full row from the event payload.
    private val SELECTED_COLUMNS = listOf(
        "highlight_id", "type", "img_url", "title", "embed_url", "channel", "source",
        "match_home_team", "match_away_team", "match_league_name", "match_start_date",
        FIELD_FETCHED_AT,
    )

    fun startRealtimeSubscription(scope: CoroutineScope, onNewHighlight: (HighlightRow) -> Unit) {
        if (!isReady) return
        if (!subscriptionStarted.compareAndSet(false, true)) return
        startSubscriptionLoop { response ->
            val isCreate = response.events.any { it.contains(".create") }
            if (!isCreate) return@startSubscriptionLoop
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val row = HighlightRow.fromMap(response.payload as? Map<String, Any?> ?: return@startSubscriptionLoop)
                scope.launch { onNewHighlight(row) }
            }.onFailure {
                logSilentFailure("Realtime highlight payload parse failed", it)
            }
        }
    }
}

data class HighlightRow(
    val highlightId: String,
    val type: String,
    val imgUrl: String,
    val title: String,
    val url: String,
    val embedUrl: String?,
    val channel: String,
    val source: String,
    val matchHomeTeam: String,
    val matchAwayTeam: String,
    val matchHomeAbbr: String,
    val matchAwayAbbr: String,
    val matchLeagueName: String,
    val matchLeagueSeason: Int,
    val matchStartDate: String,
    val matchEndDate: String,
    val rawMatchId: String,
    val fetchedAt: Long
) {
    companion object {
        private fun Map<*, *>.str(key: String, default: String = "") = this[key] as? String ?: default

        fun fromMap(data: Map<String, Any?>): HighlightRow {
            // data may come from SDK row.data or from Realtime payload directly
            val inner = (data["data"] as? Map<*, *>) ?: data
            return HighlightRow(
                highlightId = inner.str("highlight_id"),
                type = inner.str("type", "UNVERIFIED"),
                imgUrl = inner.str("img_url"),
                title = inner.str("title"),
                url = inner.str("url"),
                embedUrl = (inner["embed_url"] as? String)?.takeIf { it.isNotBlank() },
                channel = inner.str("channel"),
                source = inner.str("source"),
                matchHomeTeam = inner.str("match_home_team"),
                matchAwayTeam = inner.str("match_away_team"),
                matchHomeAbbr = inner.str("match_home_abbr"),
                matchAwayAbbr = inner.str("match_away_abbr"),
                matchLeagueName = inner.str("match_league_name"),
                matchLeagueSeason = (inner["match_league_season"] as? Number)?.toInt() ?: 0,
                matchStartDate = inner.str("match_start_date"),
                matchEndDate = inner.str("match_end_date"),
                rawMatchId = inner.str("raw_match_id"),
                fetchedAt = (inner["fetched_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
