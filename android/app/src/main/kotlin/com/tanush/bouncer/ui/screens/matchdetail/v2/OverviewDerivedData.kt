package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.HighlightHighlightMomentType
import com.tanush.bouncer.ui.components.HighlightMoment
import com.tanush.bouncer.ui.components.MatchPhaseData
import com.tanush.bouncer.ui.components.OverRunRate
import com.tanush.bouncer.utils.TimelineEventExtractor
import kotlin.math.abs

/** Derived collections for the Live, Summary and Graphs tabs, memoized so per-ball recompositions don't re-derive them. */
internal class OverviewDerivedData(
    val cumulativeScores: List<Int>,
    val fallbackBatsmen: List<BatsmanStats>,
    val fallbackHasNotOut: Boolean,
    val matchPhases: List<MatchPhaseData>,
    val perOverRuns: List<OverRunRate>,
    val keyMoments: List<HighlightMoment>,
    val meaningfulCommentary: List<String>,
    /** Both innings from the server's over history; null falls back to the Cricinfo milestones. */
    val worm: OverWorm? = null,
)

@Composable
internal fun rememberOverviewDerivedData(
    match: CricketMatch,
    scorecard: Scorecard?,
    matchFlow: MatchFlowData?,
    isMatchActive: Boolean,
    probabilityHistory: List<WinProbabilityPoint>,
    commentary: List<String>,
): OverviewDerivedData = remember(match, scorecard, matchFlow, isMatchActive, probabilityHistory, commentary) {
    val overMilestones = matchFlow?.overMilestones.orEmpty()
    val latestInnings = scorecard?.innings?.lastOrNull()
    val notOutBatsmen = latestInnings?.batsmen?.filter {
        val d = it.dismissal.lowercase()
        d.contains("not out") || d.contains("batting")
    }?.take(2).orEmpty()
    val (cumulativeScores, perOverRuns) = buildOverAggregates(overMilestones)

    OverviewDerivedData(
        cumulativeScores = cumulativeScores,
        fallbackBatsmen = notOutBatsmen.ifEmpty { latestInnings?.batsmen?.takeLast(2).orEmpty() },
        fallbackHasNotOut = notOutBatsmen.isNotEmpty(),
        matchPhases = buildMatchPhases(
            overMilestones = overMilestones,
            powerplays = matchFlow?.powerplays ?: emptyList(),
            scorecard = scorecard,
            isLive = isMatchActive,
            format = CricketConstants.formatFromMatchType(match.matchType, match.matchTitle),
        ),
        perOverRuns = perOverRuns,
        keyMoments = buildKeyMoments(probabilityHistory),
        worm = scorecard?.overs?.let(::overWormOf),
        meaningfulCommentary = TimelineEventExtractor.deduplicateByBall(
            commentary
                .map { it.trimEnd('/').trim() }
                .filter { it.isNotBlank() && it.length > 2 && it != "Loading commentary..." }
        ),
    )
}

/** Per-over cumulative scores and run deltas derived from milestone points in one pass. */
private fun buildOverAggregates(
    overMilestones: List<Pair<Int, Int>>,
): Pair<List<Int>, List<OverRunRate>> {
    if (overMilestones.isEmpty()) return emptyList<Int>() to emptyList()
    val maxOver = overMilestones.maxOf { it.first }
    val cumulative = buildList {
        var prev = 0
        for (over in 1..maxOver) {
            val cum = overMilestones.lastOrNull { it.first <= over }?.second ?: prev
            add(cum)
            prev = cum
        }
    }
    val perOver = buildList {
        var prev = 0
        overMilestones.sortedBy { it.first }.forEach { (over, cum) ->
            add(OverRunRate(over = over, team1Runs = cum - prev, team2Runs = null))
            prev = cum
        }
    }
    return cumulative to perOver
}

private fun buildKeyMoments(probabilityHistory: List<WinProbabilityPoint>): List<HighlightMoment> =
    probabilityHistory.mapIndexedNotNull { idx, point ->
        val event = point.keyEvent ?: return@mapIndexedNotNull null
        val prevProb = probabilityHistory.getOrNull(idx - 1)
        val impact = if (prevProb != null) {
            (abs(point.team1Probability - prevProb.team1Probability) * 2f).coerceIn(0f, 1f)
        } else 0.5f
        HighlightMoment(
            overNumber = point.over,
            title = event,
            description = "${(point.team1Probability * 100).toInt()}% win probability",
            type = momentTypeFor(event),
            impactScore = impact,
            timestamp = "Over ${point.over.toInt()}",
        )
    }

private val momentKeywordTypes = listOf(
    listOf("wicket") to HighlightHighlightMomentType.WICKET,
    listOf("six") to HighlightHighlightMomentType.SIX,
    listOf("four", "boundary") to HighlightHighlightMomentType.FOUR,
    listOf("50", "half century") to HighlightHighlightMomentType.FIFTY,
    listOf("100", "century") to HighlightHighlightMomentType.CENTURY,
)

private fun momentTypeFor(event: String): HighlightHighlightMomentType =
    momentKeywordTypes.firstOrNull { (keywords, _) ->
        keywords.any { event.contains(it, ignoreCase = true) }
    }?.second ?: HighlightHighlightMomentType.MILESTONE
