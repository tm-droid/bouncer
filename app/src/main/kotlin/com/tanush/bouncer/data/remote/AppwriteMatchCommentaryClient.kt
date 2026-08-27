package com.tanush.bouncer.data.remote

import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.models.TimelineEventType
import io.appwrite.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One commentary line plus what actually happened on the delivery.
 *
 * [event] is `bouncer-match-sync`'s verdict, classified from the RAW Cricbuzz text *before* the
 * line was rewritten into broadcast prose. It has to travel as data because it is not recoverable
 * from [text]: the rewrite turns a four into "races away to the rope", so the `\bFOUR\b` regex the
 * commentary filters used to rely on matched only the rows where generation failed and the raw text
 * was written through.
 *
 * Three distinct states, and the difference between the last two is the point:
 *  - `WICKET` / `SIX` / `FOUR` — the server saw it.
 *  - [TimelineEventType.OTHER] — the server saw an ordinary delivery. Not a boundary, full stop.
 *  - `null` — the server never had an opinion: a scraped fallback line, or a row written before
 *    the `event` column existed. Only here do callers fall back to text classification.
 *
 * Collapsing the last two would undo the fix: a dot ball with no verdict sends the filter back to
 * searching broadcast prose for the word "six", which is a word commentators write about the
 * target score. See `CommentaryEntry.eventType`.
 */
/**
 * @param ball the server's own ball label for this delivery — `"18.5"`, or The Hundred's
 *   `"Ball 72"` — straight off the row's `ball` column, and null for a row that has none (an
 *   on-device scrape fallback, or a row written before the column existed).
 *
 *   The UI used to re-derive this by regexing the prose for a leading `18.5`, which only works
 *   when the line carries the prefix. `bouncer-match-sync` adds it on the AI path but writes raw
 *   Cricbuzz text unprefixed when the provider chain produces nothing, and that passthrough is the
 *   dominant path in production — so the over and ball number simply vanished from those rows.
 *   Reading the column the server already writes fixes every row, past and future, and is the only
 *   thing that can work for `Ball 72`, which no over-shaped regex will ever match.
 */
data class CommentaryEntry(
    val text: String,
    val event: TimelineEventType?,
    val ball: String? = null,
    /**
     * The row's `sort_key`, which is what the feed is ordered by. Null for a line that did not come
     * from the table (the on-device scrape fallback), which sorts as newest.
     */
    val sortKey: Double? = null,
    /**
     * Total runs off the delivery, extras included — `bouncer-match-sync`'s reading of Cricbuzz's
     * own outcome clause, taken before the line was rewritten.
     *
     * Null is **not** zero. It means no verdict: a row written before the column existed, a scrape
     * fallback line, or a clause the server did not recognise. The commentary tab draws no result
     * pill for a null, because the alternative is what this column was added to stop — the number
     * does not survive the rewrite ("1 run" becomes "scampers through for a single"), `event` is
     * four-valued and cannot carry it, and the device's prose fallback defaulted to 0, so every
     * single rendered as a dot ball and an over of six of them rendered as a MAIDEN.
     */
    val runs: Int? = null,
    /**
     * The kind of delivery, where it is not an ordinary one: `WIDE`, `NOBALL`, `BYE`, `LEGBYE` —
     * `bouncer-match-sync`'s reading of Cricbuzz's outcome clause, taken before the rewrite.
     *
     * Null is "no verdict", not "ordinary delivery", exactly as for [runs] and [event]. It travels
     * as data because the rewrite destroys it: a wide reads "strays down leg", so the commentary
     * tab's `text.contains("wide")` test was the same guess-from-prose failure that put a dot ball
     * on every single. Only where this is null does the tab fall back to reading the text.
     */
    val extra: String? = null,
    /**
     * Which innings this delivery belongs to, from the row's `innings` column, or null for a line
     * that did not come from the table.
     *
     * The feed is ordered by wall clock, which is correct and is not the same as cricket order: a
     * Test's third innings over 60 is genuinely NEWER than its second innings over 80. Without this
     * the commentary tab had nothing to say so with, and re-sorted the whole feed by over number to
     * compensate — which is what put over 89 immediately above over 83 and a wicket at 60.2 above
     * one at 80.5.
     *
     * Historical rows carry `bouncer-match-sync`'s write-time innings, which is the innings being
     * batted when the row was WRITTEN, so an innings tail written after the change is mislabelled.
     * Treat a change in this value as a divider, never as a sort key.
     */
    val innings: Int? = null,
)

data class CommentaryEvent(
    val matchId: String,
    val commentary: String,
    val sortKey: Double,
    val event: TimelineEventType?,
    val ball: String? = null,
    val runs: Int? = null,
    val extra: String? = null,
    val innings: Int? = null,
)

/**
 * Parse the `event` column. Unknown or absent values are null, never a throw: the column is a
 * string rather than an enum precisely so a server-side vocabulary change degrades to "the client
 * will classify this one from text" instead of failing the write and losing the commentary.
 */
internal fun parseCommentaryEvent(raw: String?): TimelineEventType? = when (raw) {
    "WICKET" -> TimelineEventType.WICKET
    "SIX" -> TimelineEventType.SIX
    "FOUR" -> TimelineEventType.FOUR
    "NONE" -> TimelineEventType.OTHER
    else -> null
}

// Reads the server-generated per-ball commentary feed (written by bouncer-match-sync)
// for a single match, and exposes an app-lifetime Realtime stream of new lines. The
// on-device scrape is gone — this is the sole source of match-detail commentary.
// The Realtime channel is table-wide (started once); ViewModels filter [events] by matchId.
object AppwriteMatchCommentaryClient : AppwriteTableClient("AppwriteMatchCommentary") {
    private const val TABLE_ID = "match-commentary"
    // A Test day is ~90 overs and `match-commentary` now stores every delivery, so 60 rows could
    // not hold a session, let alone an innings — the tab showed a window narrower than the over
    // groups it was drawing. Reads bill per row returned, and at ~2% of the plan's monthly read
    // budget this is affordable; `olderThan` pages beyond it rather than raising it further.
    private const val FEED_LIMIT = 150

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, TABLE_ID)
    }

    // Newest-first list of commentary lines for the match.
    suspend fun getForMatch(matchId: String): List<CommentaryEntry> = fetchPage(matchId, olderThan = null)

    /**
     * The next page back, strictly older than [olderThan].
     *
     * Paged on `sort_key` rather than a cursor id: the id is `sha256("$matchId:$timestamp")` and
     * means nothing in sort order, whereas the key the feed is ordered by is already in hand from
     * the last row on screen. Reads bill per row, so a page is only fetched when a reader asks for
     * one.
     */
    suspend fun getOlderThan(matchId: String, olderThan: Double): List<CommentaryEntry> =
        fetchPage(matchId, olderThan)

    private suspend fun fetchPage(matchId: String, olderThan: Double?): List<CommentaryEntry> {
        if (!isReady) return emptyList()
        return withContext(Dispatchers.IO) {
            // Server-side order needs a sort_key index; if that's missing the ordered query 400s.
            // Fall back to an unordered fetch + client-side sort so a missing index never blanks the feed.
            runCatching {
                queryRows(matchId, ordered = true, olderThan = olderThan)
            }.recoverCatching { e ->
                logSilentFailure("Commentary ordered fetch failed; retrying unordered", e)
                // The unordered retry cannot page — a page is defined by the order — so it only
                // ever serves the first one. A caller asking for older lines gets nothing rather
                // than an arbitrary 150 rows presented as "older".
                if (olderThan != null) emptyList() else queryRows(matchId, ordered = false, olderThan = null)
            }.onFailure { e ->
                logSilentFailure("Commentary feed fetch failed", e)
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun queryRows(
        matchId: String,
        ordered: Boolean,
        olderThan: Double?,
    ): List<CommentaryEntry> {
        val queries = buildList {
            add(Query.equal("match_id", matchId))
            if (olderThan != null) add(Query.lessThan("sort_key", olderThan))
            if (ordered) add(Query.orderDesc("sort_key"))
            add(Query.limit(FEED_LIMIT))
        }
        val name = if (ordered) "commentary.ordered" else "commentary.unordered"
        val rows = guardedRead(name) {
            it.listRows(databaseId = dbId, tableId = tableId, queries = queries)
        }?.rows ?: return emptyList()
        val sorted = if (ordered) rows else rows.sortedByDescending { (it.data["sort_key"] as? Number)?.toDouble() ?: 0.0 }
        return sorted.mapNotNull { row ->
            val text = (row.data["commentary"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CommentaryEntry(
                text = text,
                event = parseCommentaryEvent(row.data["event"] as? String),
                ball = (row.data["ball"] as? String)?.takeIf { it.isNotBlank() },
                sortKey = (row.data["sort_key"] as? Number)?.toDouble(),
                runs = (row.data["runs"] as? Number)?.toInt(),
                extra = (row.data["extra"] as? String)?.takeIf { it.isNotBlank() },
                innings = (row.data["innings"] as? Number)?.toInt(),
            )
        }
    }

    /**
     * New commentary lines for one match, for as long as the flow is collected.
     *
     * Row ids here are `sha256("$matchId:$timestamp")`, unknowable client-side, and Appwrite has no
     * query-filtered channels — so unlike the detail client this must use the table channel and
     * filter locally. The lever is therefore DURATION, not selectivity: the socket is held only
     * while a match-detail screen is alive instead of for the whole process. Concurrent collectors
     * share one socket via the base class refcount.
     */
    fun observeCommentary(matchId: String): Flow<CommentaryEvent> = callbackFlow {
        if (!isReady) {
            close()
            return@callbackFlow
        }
        val handle = acquireChannel(tableChannel()) { response ->
            val isWrite = response.events.any { it.contains(".create") || it.contains(".update") }
            if (!isWrite) return@acquireChannel
            @Suppress("UNCHECKED_CAST")
            val payload = response.payload as? Map<String, Any> ?: return@acquireChannel
            if (payload["match_id"] as? String != matchId) return@acquireChannel
            val commentary = (payload["commentary"] as? String)?.takeIf { it.isNotBlank() }
                ?: return@acquireChannel
            val sortKey = (payload["sort_key"] as? Number)?.toDouble() ?: 0.0
            val event = parseCommentaryEvent(payload["event"] as? String)
            val ball = (payload["ball"] as? String)?.takeIf { it.isNotBlank() }
            val runs = (payload["runs"] as? Number)?.toInt()
            val extra = (payload["extra"] as? String)?.takeIf { it.isNotBlank() }
            val innings = (payload["innings"] as? Number)?.toInt()
            trySend(CommentaryEvent(matchId, commentary, sortKey, event, ball, runs, extra, innings))
        }
        awaitClose { handle.close() }
    }
}
