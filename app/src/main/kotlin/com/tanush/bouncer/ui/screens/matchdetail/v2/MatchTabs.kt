package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.annotation.StringRes
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchStatus
import com.tanush.bouncer.ui.components.v2.MatchStatusType
import com.tanush.bouncer.ui.components.v2.determineMatchStatus

/**
 * The match screen's tabs. Each is one kind of information, with one home:
 *
 * - [LIVE] / [SUMMARY]: the state of play while it is on, or what happened once it is over.
 *   Never both — a finished match has no one at the crease, and a live one has no result.
 * - [SCORECARD]: the innings tables, fall of wickets and partnerships.
 * - [COMMENTARY]: the ball-by-ball feed.
 * - [GRAPHS]: every chart, the win-probability arc included.
 * - [SQUADS]: who is playing.
 * - [INFO]: the facts around the match — preview, venue, toss, officials, weather, head to head.
 * - [HIGHLIGHTS]: clips.
 *
 * Dispatching on this enum rather than on a string resource id is deliberate: resource ids are
 * non-final under nonTransitiveRClass, so a `when` over them was never checked for exhaustiveness.
 */
enum class MatchTab { LIVE, SUMMARY, SCORECARD, COMMENTARY, GRAPHS, SQUADS, INFO, HIGHLIGHTS }

/** The tab's label. Squads says "Playing XI" once both elevens are named. */
@StringRes
internal fun MatchTab.titleRes(playingXiNamed: Boolean): Int = when (this) {
    MatchTab.LIVE -> R.string.tab_live
    MatchTab.SUMMARY -> R.string.tab_summary
    MatchTab.SCORECARD -> R.string.tab_scorecard
    MatchTab.COMMENTARY -> R.string.tab_commentary
    MatchTab.GRAPHS -> R.string.tab_graphs
    MatchTab.SQUADS -> squadsTitle(playingXiNamed)
    MatchTab.INFO -> R.string.tab_info
    MatchTab.HIGHLIGHTS -> R.string.tab_highlights
}

@StringRes
private fun squadsTitle(playingXiNamed: Boolean): Int = if (playingXiNamed) R.string.squads_playing_xi else R.string.squads_squads

/** Where a match is, as far as the tab set is concerned. Stumps, breaks and rain are [IN_PLAY]. */
enum class MatchPhase { UPCOMING, IN_PLAY, FINISHED }

/** Which optional tabs have something to show. A tab with nothing in it is not offered. */
data class MatchTabContent(
    val scorecard: Boolean,
    val commentary: Boolean,
    val graphs: Boolean,
    val squads: Boolean,
    val highlights: Boolean,
)

/**
 * The tabs for a match in [phase], in order. **The first is the one the screen opens on**: Info
 * for a fixture, Live while it is on, Summary once it is over.
 *
 * In play, Commentary is offered with nothing in it yet: its empty state says the feed is coming,
 * which is true. Before the toss and after the end, an empty feed is all there will ever be.
 */
internal fun matchTabsFor(phase: MatchPhase, content: MatchTabContent): List<MatchTab> = when (phase) {
    MatchPhase.UPCOMING -> listOfNotNull(
        MatchTab.INFO,
        MatchTab.SQUADS.takeIf { content.squads },
        MatchTab.COMMENTARY.takeIf { content.commentary },
    )
    MatchPhase.IN_PLAY -> listOfNotNull(
        MatchTab.LIVE,
        MatchTab.SCORECARD.takeIf { content.scorecard },
        MatchTab.COMMENTARY,
        MatchTab.GRAPHS.takeIf { content.graphs },
        MatchTab.SQUADS.takeIf { content.squads },
        MatchTab.INFO,
        MatchTab.HIGHLIGHTS.takeIf { content.highlights },
    )
    MatchPhase.FINISHED -> listOfNotNull(
        MatchTab.SUMMARY,
        MatchTab.SCORECARD.takeIf { content.scorecard },
        MatchTab.COMMENTARY.takeIf { content.commentary },
        MatchTab.GRAPHS.takeIf { content.graphs },
        MatchTab.HIGHLIGHTS.takeIf { content.highlights },
        MatchTab.SQUADS.takeIf { content.squads },
        MatchTab.INFO,
    )
}

/**
 * The page to show for [selected] in [tabs].
 *
 * The selection is a tab, not an index, because the set changes while the screen is open: Graphs
 * appears with the first plotted over, Squads when the team sheet loads, and the whole set when a
 * match starts or ends. An index would silently move the reader onto a different tab. A reader on
 * Live when the match ends lands on Summary, its successor; one whose tab went away lands on the
 * default.
 */
internal fun pageFor(tabs: List<MatchTab>, selected: MatchTab?): Int {
    val target = if (selected == MatchTab.LIVE && MatchTab.LIVE !in tabs) MatchTab.SUMMARY else selected
    return tabs.indexOf(target).takeIf { it >= 0 } ?: 0
}

internal fun matchPhaseOf(match: CricketMatch): MatchPhase {
    val active = matchIsActive(match)
    val completed = match.matchStatus == MatchStatus.Completed
    return when {
        completed -> MatchPhase.FINISHED
        matchIsUpcoming(match, active, completed) -> MatchPhase.UPCOMING
        else -> MatchPhase.IN_PLAY
    }
}

/**
 * Live, or between sessions: the state of play is still the thing to show. Whatever the header's
 * badge calls live counts too (after the toss, Cricbuzz's state word is "Toss" and the status line
 * reads "India opt to bowl"), so the screen never says LIVE above a fixture's tabs.
 */
internal fun matchIsActive(match: CricketMatch): Boolean =
    match.matchStatus == MatchStatus.Live || determineMatchStatus(match) == MatchStatusType.Live ||
        match.currentStatus.lowercase().let {
            it.contains("stumps") || it.contains("break") || it.contains("rain") || it.contains("delay")
        }

internal fun matchIsUpcoming(match: CricketMatch, isMatchActive: Boolean, isCompleted: Boolean): Boolean =
    match.matchStatus == MatchStatus.Upcoming ||
        (!isMatchActive && !isCompleted && match.teams.all { it.score.isBlank() || it.score == "0/0" })
