package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.utils.BoundedLruCache

/** Where a finished match's commentary came from, once `match-commentary` no longer has it. */
enum class PastFeedSource {
    /** Bouncer's own commentary, kept by the match archive. */
    ARCHIVE,

    /** Cricsheet's deliveries, written out as plain lines. */
    CRICSHEET,

    /** The archive's own deliveries of a backfilled match, written out as plain lines. */
    ARCHIVE_DELIVERIES,
}

/** A finished match's whole feed, newest first; [partialInnings] are the innings whose source lacks some deliveries. */
data class PastFeed(
    val entries: List<CommentaryEntry>,
    val source: PastFeedSource,
    val partialInnings: List<Int> = emptyList(),
)

/**
 * What a finished match's screen reads once Appwrite has pruned it. An interface so a ViewModel
 * test never reaches GitHub Pages: [None] answers nothing.
 */
interface PastMatchSource {
    suspend fun feed(match: CricketMatch): PastFeed?

    suspend fun scorecard(match: CricketMatch): Scorecard?

    /**
     * Per-over points of a finished match for the worm, manhattan and win-probability arc, in a
     * [Scorecard] that carries only [Scorecard.overs] and [Scorecard.probabilityTeamId].
     */
    suspend fun overs(match: CricketMatch): Scorecard? = null

    /** A Cricsheet match by its own id (no `cs-` prefix), for a match that exists nowhere else. */
    suspend fun cricsheetMatch(id: String): HistoryMatch? = null

    /** Toss, officials, player of the match and squads of a backfilled match; null for any other. */
    suspend fun info(match: CricketMatch): ArchivedInfo? = null

    object None : PastMatchSource {
        override suspend fun feed(match: CricketMatch): PastFeed? = null

        override suspend fun scorecard(match: CricketMatch): Scorecard? = null
    }
}

/** A finished match's commentary and card from the sites that keep them after Appwrite's 14-day prune. */
object HistoryRepository : PastMatchSource {
    /**
     * The archive's copy first, because it is Bouncer's own commentary and needs no linking; then
     * Cricsheet's ball-by-ball. Null when the match is not over, neither has it, or neither can be
     * reached. A live match is never looked up: its rows are still in Appwrite.
     */
    override suspend fun feed(match: CricketMatch): PastFeed? {
        if (!match.isCompleted) return null
        val archived = ArchiveClient.match(match.id)
        archived?.commentary()?.takeIf { it.isNotEmpty() }?.let {
            return PastFeed(it, PastFeedSource.ARCHIVE)
        }
        archived?.deliveries()?.let { d ->
            HistoryCommentary.entries(d.match).takeIf { it.isNotEmpty() }
                ?.let { return PastFeed(it, PastFeedSource.ARCHIVE_DELIVERIES, d.partial) }
        }
        return ballByBall(match)?.let { PastFeed(it, PastFeedSource.CRICSHEET) }
    }

    override suspend fun cricsheetMatch(id: String): HistoryMatch? = HistoryClient.match(id)

    override suspend fun info(match: CricketMatch): ArchivedInfo? =
        if (match.isCompleted) ArchiveClient.match(match.id)?.archivedInfo() else null

    /** The archive's final card, else the card of the Cricsheet match this is. */
    override suspend fun scorecard(match: CricketMatch): Scorecard? =
        ArchiveClient.match(match.id)?.scorecard()
            ?: linked(match)?.let(::scorecardOf)?.takeIf { it.innings.isNotEmpty() }

    /** The archive card's overs, then its win-probability log, then Cricsheet's `ov`. */
    override suspend fun overs(match: CricketMatch): Scorecard? {
        if (!match.isCompleted) return null
        val archived = ArchiveClient.match(match.id)
        archived?.scorecard()?.takeIf { it.overs.isNotEmpty() }
            ?.let { return Scorecard(overs = it.overs, probabilityTeamId = it.probabilityTeamId) }
        archived?.evalOvers()?.let { return it }
        // Complete innings only: `deliveries` leaves a partial innings' overs out.
        archived?.deliveries()?.let { scorecardOf(it.match).overs }?.takeIf { it.isNotEmpty() }
            ?.let { return Scorecard(overs = it) }
        return linked(match)?.let(::scorecardOf)?.overs?.takeIf { it.isNotEmpty() }?.let { Scorecard(overs = it) }
    }

    // The feed and the card both need the same file; an empty list remembers a miss.
    private val linkedCache = BoundedLruCache<String, List<HistoryMatch>>(LINKED_CACHE_SIZE)

    /**
     * Cricsheet's record of the match, when it has one that is certainly this match
     * (`HistoryMatchLinker`). Cricsheet publishes days after the last ball.
     */
    private suspend fun linked(match: CricketMatch): HistoryMatch? {
        if (!match.isCompleted || match.isCricsheetOnly) return null
        linkedCache[match.id]?.let { return it.firstOrNull() }
        val rows = HistoryMatchLinker.monthsFor(match).flatMap { HistoryClient.month(it).orEmpty() }
        val found = HistoryMatchLinker.link(match, rows)?.let { HistoryClient.match(it) }
        // A failed fetch is not a miss worth remembering: the next screen may reach the site.
        if (found != null || rows.isNotEmpty()) linkedCache[match.id] = listOfNotNull(found)
        return found
    }

    private suspend fun ballByBall(match: CricketMatch): List<CommentaryEntry>? =
        linked(match)?.let { HistoryCommentary.entries(it) }?.takeIf { it.isNotEmpty() }

    private const val LINKED_CACHE_SIZE = 3
}
