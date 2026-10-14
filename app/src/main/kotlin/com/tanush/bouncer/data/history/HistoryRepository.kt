package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.CricketMatch

/** Cricsheet's record of a Cricbuzz match, when there is one. */
object HistoryRepository {
    /**
     * The match's ball-by-ball from Cricsheet, newest first, or null when the match is not over,
     * Cricsheet has no match that is certainly this one (`HistoryMatchLinker`), or the site cannot
     * be reached. A live match is never looked up: Cricsheet publishes days after the last ball.
     */
    suspend fun ballByBall(match: CricketMatch): List<CommentaryEntry>? {
        if (!match.isCompleted) return null
        val rows = HistoryMatchLinker.monthsFor(match).flatMap { HistoryClient.month(it).orEmpty() }
        val id = HistoryMatchLinker.link(match, rows) ?: return null
        val history = HistoryClient.match(id) ?: return null
        return HistoryCommentary.entries(history).takeIf { it.isNotEmpty() }
    }
}
