package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.history.CRICSHEET_PREFIX
import com.tanush.bouncer.data.history.HistoryCommentary
import com.tanush.bouncer.data.history.HistoryMatch
import com.tanush.bouncer.data.history.PastFeedSource
import com.tanush.bouncer.data.history.PastMatchSource
import com.tanush.bouncer.data.history.resultText
import com.tanush.bouncer.data.history.scorecardOf
import com.tanush.bouncer.data.history.sideScoreOf
import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The screen of a match that exists only on the Cricsheet site (`cs-` id), filled from its one
 * file: the card, the ball-by-ball, each side's score and the result line. Nothing else is asked
 * for, because no live source has a row for it.
 */
internal fun MatchDetailViewModel.UiState.withCricsheet(match: CricketMatch, history: HistoryMatch): MatchDetailViewModel.UiState {
    // The row's sides are Cricsheet's own names; position is the fallback for a spelling that differs.
    val sideOf = { slot: Int ->
        history.t.indexOfFirst { it.equals(match.teams.getOrNull(slot)?.name, ignoreCase = true) }.takeIf { it >= 0 } ?: slot
    }
    val (score1, overs1) = sideScoreOf(history, sideOf(0))
    val (score2, overs2) = sideScoreOf(history, sideOf(1))
    return copy(
        scorecard = scorecardOf(history).takeIf { it.innings.isNotEmpty() },
        commentary = HistoryCommentary.entries(history),
        commentaryError = null,
        pastFeedSource = PastFeedSource.CRICSHEET,
        hasOlderCommentary = false,
        liveTeam1Score = score1,
        liveTeam1Overs = overs1,
        liveTeam2Score = score2,
        liveTeam2Overs = overs2,
        liveStatus = resultText(history),
        loadError = null,
    )
}

/** Reads the match's one Cricsheet file and fills the screen from it, or says it could not be read. */
internal suspend fun MutableStateFlow<MatchDetailViewModel.UiState>.loadCricsheet(match: CricketMatch, source: PastMatchSource) {
    val history = source.cricsheetMatch(match.id.removePrefix(CRICSHEET_PREFIX))
    update { state ->
        val filled = history?.let { state.withCricsheet(match, it) }
            ?: state.copy(loadError = "Couldn't load this match. Check your connection and retry.")
        filled.copy(commentaryLoading = false, scorecardLoading = false)
    }
}

/**
 * Fills a finished match's per-over points from the archive or Cricsheet when its card has none, so
 * the worm and the win-probability arc draw. Needs a card already: a points-only one would close
 * the "no scorecard" gates while the tab still read empty.
 */
internal suspend fun MutableStateFlow<MatchDetailViewModel.UiState>.loadPastOvers(match: CricketMatch, source: PastMatchSource) {
    val card = value.scorecard ?: return
    if (!match.isCompleted || card.overs.isNotEmpty()) return
    val past = source.overs(match) ?: return
    update { st ->
        val current = st.scorecard
        if (current == null || current.overs.isNotEmpty()) st
        else st.copy(scorecard = current.copy(overs = past.overs, probabilityTeamId = past.probabilityTeamId ?: current.probabilityTeamId))
    }
}
