package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard

/** Where a finished match's commentary came from, once `match-commentary` no longer has it. */
enum class PastFeedSource {
    /** Bouncer's own commentary, kept by the match archive. */
    ARCHIVE,

    /** Cricsheet's deliveries, written out as plain lines. */
    CRICSHEET,
}

/** A finished match's whole feed, newest first. */
data class PastFeed(val entries: List<CommentaryEntry>, val source: PastFeedSource)

/**
 * What a finished match's screen reads once Appwrite has pruned it. An interface so a ViewModel
 * test never reaches GitHub Pages: [None] answers nothing.
 */
interface PastMatchSource {
    suspend fun feed(match: CricketMatch): PastFeed?

    suspend fun scorecard(matchId: String): Scorecard?

    object None : PastMatchSource {
        override suspend fun feed(match: CricketMatch): PastFeed? = null

        override suspend fun scorecard(matchId: String): Scorecard? = null
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
        ArchiveClient.match(match.id)?.commentary()?.takeIf { it.isNotEmpty() }?.let {
            return PastFeed(it, PastFeedSource.ARCHIVE)
        }
        return ballByBall(match)?.let { PastFeed(it, PastFeedSource.CRICSHEET) }
    }

    /** The archive's final card. */
    override suspend fun scorecard(matchId: String): Scorecard? = ArchiveClient.match(matchId)?.scorecard()

    /**
     * Cricsheet's record of the match, when it has one that is certainly this match
     * (`HistoryMatchLinker`). Cricsheet publishes days after the last ball.
     */
    private suspend fun ballByBall(match: CricketMatch): List<CommentaryEntry>? {
        val rows = HistoryMatchLinker.monthsFor(match).flatMap { HistoryClient.month(it).orEmpty() }
        val id = HistoryMatchLinker.link(match, rows) ?: return null
        val history = HistoryClient.match(id) ?: return null
        return HistoryCommentary.entries(history).takeIf { it.isNotEmpty() }
    }
}
