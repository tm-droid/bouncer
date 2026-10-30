package com.tanush.bouncer.ui.components.premium

import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.MatchProjections
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.utils.parseRuns
import java.util.Locale

// The scorecard's derived figures: run rates and the innings' best bowler.

/** Most wickets, then fewest runs. -1 when nobody has taken one. */
internal fun bestBowlerIndex(innings: Innings): Int {
    val ranked = innings.bowlers.withIndex()
        .filter { (it.value.wickets.toIntOrNull() ?: 0) > 0 }
        .sortedWith(compareBy({ -(it.value.wickets.toIntOrNull() ?: 0) }, { it.value.runs.toIntOrNull() ?: Int.MAX_VALUE }))
    return ranked.firstOrNull()?.index ?: -1
}

internal fun calculateCurrentRunRate(innings: Innings?): String {
    if (innings == null) return "-"
    return MatchProjections.currentRunRate(parseRuns(innings.score), ballsBowled(innings.overs))
        ?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
}

internal fun calculateRequiredRunRate(scorecard: Scorecard, currentInningsIndex: Int): String? {
    if (currentInningsIndex != 1 || scorecard.innings.size < 2) return null
    val firstInnings = scorecard.innings[0]
    val secondInnings = scorecard.innings[1]
    val targetRuns = parseRuns(firstInnings.score) + 1
    // Infer format from first innings overs: ≤20 completed overs → T20, else ODI
    val firstInningsOvers = ballsBowled(firstInnings.overs) / CricketConstants.BALLS_PER_OVER
    val totalBalls = if (firstInningsOvers <= CricketConstants.T20_INNINGS_BALLS / CricketConstants.BALLS_PER_OVER) {
        CricketConstants.T20_INNINGS_BALLS
    } else {
        CricketConstants.ODI_INNINGS_BALLS
    }
    return MatchProjections.requiredRunRate(targetRuns, parseRuns(secondInnings.score), ballsBowled(secondInnings.overs), totalBalls)
        ?.let { String.format(Locale.US, "%.2f", it) }
}

/** "14.3 Ov" to 87. Unparseable parts count as zero. */
private fun ballsBowled(overs: String): Int {
    val parts = overs.replace("Ov", "").trim().split(".")
    val completed = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val balls = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return completed * CricketConstants.BALLS_PER_OVER + balls
}
