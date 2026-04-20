package com.tanush.bouncer.analysis

import com.tanush.bouncer.models.CricsheetMatch

/**
 * Tracks batter vs bowler head-to-head matchup statistics.
 *
 * Uses ball-by-ball data (batter/bowler identity per delivery) to compute
 * historical matchup performance: runs scored, balls faced, dismissals, strike rate.
 */
class MatchupAnalysis {

    /** Key: "batter::bowler" → accumulated stats */
    private val matchups = mutableMapOf<String, MatchupAccumulator>()

    /**
     * Process matches to build matchup data.
     */
    fun processMatches(matches: List<CricsheetMatch>) {
        for (match in matches) {
            for (innings in match.innings) {
                for (over in innings.overs) {
                    for (ball in over.balls) {
                        if (ball.batter.isBlank() || ball.bowler.isBlank()) continue
                        
                        val key = "${ball.batter}::${ball.bowler}"
                        val acc = matchups.getOrPut(key) {
                            MatchupAccumulator(ball.batter, ball.bowler)
                        }
                        
                        acc.ballsFaced += 1
                        acc.runsScored += ball.batterRuns
                        if (ball.batterRuns == 0 && ball.extras == 0) acc.dotBalls += 1
                        if (ball.isBoundary) acc.boundaries += 1
                        
                        if (ball.isWicket) {
                            val bowlerDismissal = ball.wicketDetails.any {
                                it.playerOut == ball.batter &&
                                    it.kind != "run out" && it.kind != "retired hurt" &&
                                    it.kind != "retired not out" && it.kind != "obstructing the field"
                            }
                            if (bowlerDismissal) acc.dismissals += 1
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the matchup stats for a specific batter vs bowler.
     */
    fun getMatchup(batter: String, bowler: String): MatchupStats? {
        val acc = matchups["${batter}::${bowler}"] ?: return null
        return acc.toStats()
    }

    /**
     * Get all matchups for a specific batter (against all bowlers faced).
     */
    fun getBatterMatchups(batter: String): List<MatchupStats> {
        return matchups.values
            .filter { it.batter == batter && it.ballsFaced >= 6 }
            .map { it.toStats() }
            .sortedByDescending { it.ballsFaced }
    }

    /**
     * Get all matchups for a specific bowler (against all batters they've bowled to).
     */
    fun getBowlerMatchups(bowler: String): List<MatchupStats> {
        return matchups.values
            .filter { it.bowler == bowler && it.ballsFaced >= 6 }
            .map { it.toStats() }
            .sortedByDescending { it.ballsFaced }
    }

    // MARK: - Data Classes

    data class MatchupStats(
        val batter: String,
        val bowler: String,
        val ballsFaced: Int,
        val runsScored: Int,
        val dismissals: Int,
        val strikeRate: Double,
        val dotBallPercentage: Double,
        val boundaryPercentage: Double,
        val average: Double
    )

    // MARK: - Internal

    private class MatchupAccumulator(val batter: String, val bowler: String) {
        var ballsFaced = 0
        var runsScored = 0
        var dismissals = 0
        var dotBalls = 0
        var boundaries = 0

        fun toStats(): MatchupStats {
            val sr = if (ballsFaced > 0) (runsScored.toDouble() / ballsFaced) * 100.0 else 0.0
            val dotPct = if (ballsFaced > 0) dotBalls.toDouble() / ballsFaced * 100.0 else 0.0
            val boundPct = if (ballsFaced > 0) boundaries.toDouble() / ballsFaced * 100.0 else 0.0
            val avg = if (dismissals > 0) runsScored.toDouble() / dismissals else runsScored.toDouble()
            return MatchupStats(
                batter = batter,
                bowler = bowler,
                ballsFaced = ballsFaced,
                runsScored = runsScored,
                dismissals = dismissals,
                strikeRate = sr,
                dotBallPercentage = dotPct,
                boundaryPercentage = boundPct,
                average = avg
            )
        }
    }
}
