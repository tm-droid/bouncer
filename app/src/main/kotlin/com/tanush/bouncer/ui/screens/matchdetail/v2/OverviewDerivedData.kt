package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.FallOfWicket as ModelFallOfWicket
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.DismissalType
import com.tanush.bouncer.ui.components.FallOfWicket as UiFallOfWicket
import com.tanush.bouncer.ui.components.MatchPhaseData
import com.tanush.bouncer.utils.TimelineEventExtractor

/** Derived collections for OverviewTabV2, memoized so per-ball recompositions don't re-derive them. */
internal class OverviewDerivedData(
    val cumulativeScores: List<Int>,
    val fallbackBatsmen: List<BatsmanStats>,
    val fallbackHasNotOut: Boolean,
    val matchPhases: List<MatchPhaseData>,
    val wicketOvers: List<Int>,
    val uniqueInnings: List<Innings>,
    val fallOfWicketsByInnings: List<List<UiFallOfWicket>>,
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
    val cumulativeScores = cumulativeScoresByOver(overMilestones)
    val worm = scorecard?.overs?.let(::overWormOf)

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
        // On the worm these mark the line still batting, so they are kept only when that line is
        // the latest innings: not a Test's third or fourth, and not a second innings whose overs
        // have not reached the server yet (the dots would land on the first innings' line).
        wicketOvers = latestInnings?.fallOfWickets
            ?.takeIf { worm == null || scorecard?.innings?.size == if (worm.team2Scores.isEmpty()) 1 else 2 }
            ?.mapNotNull { it.overs.split(".").firstOrNull()?.toIntOrNull() }
            ?: emptyList(),
        uniqueInnings = scorecard?.innings?.distinctBy { "${it.teamName}_${it.score}_${it.overs}" }.orEmpty(),
        fallOfWicketsByInnings = scorecard?.innings.orEmpty().map { innings ->
            if (innings.fallOfWickets.isNotEmpty()) {
                mapFallOfWickets(innings.fallOfWickets, innings.batsmen)
            } else emptyList()
        },
        worm = worm,
        meaningfulCommentary = TimelineEventExtractor.deduplicateByBall(
            commentary
                .map { it.trimEnd('/').trim() }
                .filter { it.isNotBlank() && it.length > 2 && it != "Loading commentary..." }
        ),
    )
}

/** Cumulative score after each over, carried forward from the latest milestone reached. */
private fun cumulativeScoresByOver(overMilestones: List<Pair<Int, Int>>): List<Int> {
    if (overMilestones.isEmpty()) return emptyList()
    val maxOver = overMilestones.maxOf { it.first }
    return buildList {
        var prev = 0
        for (over in 1..maxOver) {
            val cum = overMilestones.lastOrNull { it.first <= over }?.second ?: prev
            add(cum)
            prev = cum
        }
    }
}

private fun mapFallOfWickets(
    fowList: List<ModelFallOfWicket>,
    batsmen: List<BatsmanStats>,
): List<UiFallOfWicket> {
    val batsmanByName = batsmen.associateBy { it.name.lowercase().trim() }
    var prevScore = 0
    return fowList
        .sortedBy { it.number }
        .mapIndexed { idx, fow ->
            val stats = batsmanByName[fow.batsman.lowercase().trim()]
            val (dismissalType, bowlerName) = parseDismissal(stats?.dismissal ?: "")
            val score = fow.score.toIntOrNull() ?: 0
            val partnershipRuns = (score - prevScore).coerceAtLeast(0)
            prevScore = score
            UiFallOfWicket(
                wicketNumber = fow.number.takeIf { it > 0 } ?: (idx + 1),
                score = score,
                overs = fow.overs.toFloatOrNull() ?: 0f,
                batsmanName = fow.batsman,
                batsmanRuns = stats?.runs?.toIntOrNull() ?: 0,
                batsmanBalls = stats?.balls?.toIntOrNull() ?: 0,
                bowlerName = bowlerName,
                dismissalType = dismissalType,
                partnershipRuns = partnershipRuns,
            )
        }
}

private class DismissalRule(
    val matches: (String) -> Boolean,
    val parse: (String) -> Pair<DismissalType, String>,
)

private fun bowlerAfterB(dismissal: String): String {
    val bIdx = dismissal.indexOf(" b ", ignoreCase = true)
    return if (bIdx >= 0) dismissal.substring(bIdx + 3).trim() else ""
}

private fun parseCaught(dismissal: String): Pair<DismissalType, String> {
    val bIdx = dismissal.indexOf(" b ", ignoreCase = true)
    if (bIdx < 0) return DismissalType.CAUGHT to ""
    // The fielder is whatever sits between the "c" and the " b ", found by cutting rather than by
    // a fixed offset. `substring(2, bIdx)` assumed "c " occupied exactly index 0..1, and a
    // scorecard that writes a caught-and-bowled as "c b Bumrah" puts " b " at index 1 — a
    // backwards range, which threw out of the Overview tab's layout pass (ANDROID-4S). The rules
    // also match a lowercased, TRIMMED copy while this gets the original, so index 0 was never
    // guaranteed to be the "c" in the first place.
    val fielder = dismissal.take(bIdx).trim().removePrefix("c").removePrefix("C").trim()
    val bowler = dismissal.substring(bIdx + 3).trim()
    // An empty fielder is a name the scorecard did not give us. It is NOT evidence of a caught and
    // bowled — that form is "c & b" / "c and b", which its own rule takes before this one.
    val type = if (fielder.startsWith("†") || fielder.lowercase() == "wk") {
        DismissalType.CAUGHT_BEHIND
    } else {
        DismissalType.CAUGHT
    }
    return type to bowler
}

// Matched against the lowercased dismissal string, in order; parse receives the original.
private val DISMISSAL_RULES = listOf(
    DismissalRule({ it.startsWith("c&b") || it.startsWith("c & b") }) {
        // Anchored on the "c & b" itself. A character class of [Cc][\s&bB]+ is greedy over B as
        // well, so it ate the first letter of every bowler whose name begins with one: "c & b
        // Bumrah" came out as "umrah", and so did Boult, Broad and Bairstow.
        DismissalType.CAUGHT_AND_BOWLED to it.replaceFirst(Regex("(?i)^c\\s*&\\s*b\\s*"), "").trim()
    },
    DismissalRule({ it.startsWith("lbw") }) {
        DismissalType.LBW to it.replaceFirst(Regex("(?i)^lbw\\s+b\\s+"), "").trim()
    },
    DismissalRule({ it.startsWith("run out") }) {
        DismissalType.RUN_OUT to it
            .replaceFirst(Regex("(?i)^run out\\s*\\("), "").removeSuffix(")").trim()
    },
    DismissalRule({ it.startsWith("st ") }) { DismissalType.STUMPED to bowlerAfterB(it) },
    DismissalRule({ it.startsWith("hit wicket") }) {
        DismissalType.HIT_WICKET to it.replaceFirst(Regex("(?i)^hit wicket\\s+b\\s+"), "").trim()
    },
    DismissalRule({ it.startsWith("c ") && it.contains(" b ") }) { parseCaught(it) },
    // Cut by prefix, not by offset: the rules match a trimmed copy while the parser receives the
    // original, so index 2 is only the bowler's name when nothing precedes the "b".
    DismissalRule({ it.startsWith("b ") }) {
        DismissalType.BOWLED to it.trim().drop(1).trim()
    },
)

internal fun parseDismissal(dismissal: String): Pair<DismissalType, String> {
    val d = dismissal.lowercase().trim()
    if (d.isBlank() || d.contains("not out") || d.contains("batting")) return DismissalType.BOWLED to ""
    return DISMISSAL_RULES.firstOrNull { it.matches(d) }?.parse(dismissal) ?: (DismissalType.CAUGHT to "")
}

// Match-phase builders live in OverviewMatchPhases.kt (extracted to keep this file's
// function count under detekt's TooManyFunctions threshold).
