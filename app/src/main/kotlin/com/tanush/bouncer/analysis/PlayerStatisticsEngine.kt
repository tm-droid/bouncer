package com.tanush.bouncer.analysis

import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetBall
import kotlin.math.max

/**
 * Aggregates per-player statistics from ball-by-ball match data.
 *
 * Processes the batter/bowler identity on each delivery (newly captured from Cricsheet JSON)
 * to build career batting averages, strike rates, bowling economies, phase-specific stats,
 * venue-specific stats, and recent form — all keyed by player name (or registry ID if available).
 */
class PlayerStatisticsEngine {

    /** Mutable accumulators keyed by player name — LRU-capped at 500 entries to prevent unbounded growth */
    private val battingAccumulators = object : LinkedHashMap<String, BattingAccumulator>(500, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, BattingAccumulator>) = size > 500
    }
    private val bowlingAccumulators = object : LinkedHashMap<String, BowlingAccumulator>(500, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, BowlingAccumulator>) = size > 500
    }

    /** Phase boundaries (over indices, 0-based) */
    private object Phases {
        const val POWERPLAY_END = 6
        const val MIDDLE_END = 15
        // Death = 16+
    }

    // MARK: - Public API

    /**
     * Process a list of matches to build player statistics.
     * Can be called incrementally — stats accumulate across calls.
     */
    fun processMatches(matches: List<CricsheetMatch>) {
        for (match in matches) {
            processMatch(match)
        }
    }

    fun processMatch(match: CricsheetMatch) {
        val matchDate = match.date.time
        val venue = match.venue
        val matchType = match.matchType

        for (innings in match.innings) {
            // Track which batters got out in this innings (for average calculation)
            val dismissedBatters = mutableSetOf<String>()
            val batterInningsRuns = mutableMapOf<String, Int>()
            val batterInningsBalls = mutableMapOf<String, Int>()
            // Per-innings form deltas — accumulated across balls, applied once after innings ends
            val batterInningsFormRuns = mutableMapOf<String, Int>()
            val batterInningsFormBalls = mutableMapOf<String, Int>()
            val bowlerInningsFormRuns = mutableMapOf<String, Int>()
            val bowlerInningsFormBalls = mutableMapOf<String, Int>()

            for (over in innings.overs) {
                val overNum = over.overNumber
                val phase = when {
                    overNum < Phases.POWERPLAY_END -> "powerplay"
                    overNum < Phases.MIDDLE_END -> "middle"
                    else -> "death"
                }

                for (ball in over.balls) {
                    if (ball.batter.isBlank() || ball.bowler.isBlank()) continue

                    // Batting accumulator
                    val batAcc = battingAccumulators.getOrPut(ball.batter) { BattingAccumulator(ball.batter) }
                    batAcc.totalRuns += ball.batterRuns
                    batAcc.totalBalls += 1
                    if (ball.isBoundary && ball.batterRuns == 4) batAcc.fours += 1
                    if (ball.isBoundary && ball.batterRuns == 6) batAcc.sixes += 1
                    if (ball.batterRuns == 0 && ball.extras == 0) batAcc.dotBalls += 1

                    // Phase-specific batting
                    when (phase) {
                        "powerplay" -> { batAcc.powerplayRuns += ball.batterRuns; batAcc.powerplayBalls += 1 }
                        "middle" -> { batAcc.middleRuns += ball.batterRuns; batAcc.middleBalls += 1 }
                        "death" -> { batAcc.deathRuns += ball.batterRuns; batAcc.deathBalls += 1 }
                    }

                    // Venue-specific batting
                    val venueBat = batAcc.venueStats.getOrPut(venue) { PhaseStats() }
                    venueBat.runs += ball.batterRuns
                    venueBat.balls += 1

                    // Format-specific
                    if (matchType.isNotBlank()) {
                        val fmtBat = batAcc.formatStats.getOrPut(matchType) { PhaseStats() }
                        fmtBat.runs += ball.batterRuns
                        fmtBat.balls += 1
                    }

                    // Track per-innings runs (career + form deltas)
                    batterInningsRuns[ball.batter] = (batterInningsRuns[ball.batter] ?: 0) + ball.batterRuns
                    batterInningsBalls[ball.batter] = (batterInningsBalls[ball.batter] ?: 0) + 1
                    batterInningsFormRuns[ball.batter] = (batterInningsFormRuns[ball.batter] ?: 0) + ball.batterRuns
                    batterInningsFormBalls[ball.batter] = (batterInningsFormBalls[ball.batter] ?: 0) + 1

                    // Bowling accumulator
                    val bowlAcc = bowlingAccumulators.getOrPut(ball.bowler) { BowlingAccumulator(ball.bowler) }
                    bowlAcc.runsConceded += ball.runs
                    bowlAcc.ballsBowled += 1
                    if (ball.batterRuns == 0 && ball.extras == 0) bowlAcc.dotBalls += 1

                    // Wickets attributed to bowler (exclude run outs)
                    if (ball.isWicket) {
                        val bowlerWickets = ball.wicketDetails.count { it.kind != "run out" && it.kind != "retired hurt" && it.kind != "retired not out" && it.kind != "retired out" && it.kind != "obstructing the field" }
                        bowlAcc.wickets += bowlerWickets

                        for (wd in ball.wicketDetails) {
                            dismissedBatters.add(wd.playerOut)
                        }
                    }

                    // Phase-specific bowling
                    when (phase) {
                        "powerplay" -> { bowlAcc.powerplayRuns += ball.runs; bowlAcc.powerplayBalls += 1 }
                        "middle" -> { bowlAcc.middleRuns += ball.runs; bowlAcc.middleBalls += 1 }
                        "death" -> { bowlAcc.deathRuns += ball.runs; bowlAcc.deathBalls += 1 }
                    }

                    // Venue-specific bowling
                    val venueBowl = bowlAcc.venueStats.getOrPut(venue) { PhaseStats() }
                    venueBowl.runs += ball.runs
                    venueBowl.balls += 1

                    // Accumulate bowling form deltas per innings
                    bowlerInningsFormRuns[ball.bowler] = (bowlerInningsFormRuns[ball.bowler] ?: 0) + ball.runs
                    bowlerInningsFormBalls[ball.bowler] = (bowlerInningsFormBalls[ball.bowler] ?: 0) + 1
                }
            }

            // Update innings counts
            for ((batter, _) in batterInningsRuns) {
                val acc = battingAccumulators[batter] ?: continue
                acc.innings += 1
                if (dismissedBatters.contains(batter)) acc.dismissals += 1
            }

            // Apply batting form accumulators (bounded to last 10 matches by date)
            for ((batter, inningsRuns) in batterInningsFormRuns) {
                val acc = battingAccumulators[batter] ?: continue
                val inningsBalls = batterInningsFormBalls[batter] ?: 0
                acc.updateBattingForm(matchDate, inningsRuns, inningsBalls)
            }

            // Update bowling innings
            val bowlersInInnings = innings.overs.flatMap { o -> o.balls.map { it.bowler } }.filter { it.isNotBlank() }.toSet()
            for (bowler in bowlersInInnings) {
                val acc = bowlingAccumulators[bowler] ?: continue
                acc.bowlingInnings += 1
            }

            // Apply bowling form accumulators (bounded to last 10 matches by date)
            for ((bowler, inningsRuns) in bowlerInningsFormRuns) {
                val acc = bowlingAccumulators[bowler] ?: continue
                val inningsBalls = bowlerInningsFormBalls[bowler] ?: 0
                acc.updateBowlingForm(matchDate, inningsRuns, inningsBalls)
            }
        }
    }

    /**
     * Get computed statistics for a batter.
     */
    fun getBatterStats(name: String): PlayerBattingStats? {
        val acc = battingAccumulators[name] ?: return null
        return acc.toStats()
    }

    /**
     * Get computed statistics for a bowler.
     */
    fun getBowlerStats(name: String): PlayerBowlingStats? {
        val acc = bowlingAccumulators[name] ?: return null
        return acc.toStats()
    }

    /**
     * Get combined player statistics (batting + bowling).
     */
    fun getPlayerStats(name: String): CombinedPlayerStats {
        return CombinedPlayerStats(
            name = name,
            batting = getBatterStats(name),
            bowling = getBowlerStats(name)
        )
    }

    fun getAllBatterNames(): Set<String> = battingAccumulators.keys.toSet()
    fun getAllBowlerNames(): Set<String> = bowlingAccumulators.keys.toSet()

    // MARK: - Output Data Classes

    data class PlayerBattingStats(
        val name: String,
        val innings: Int,
        val totalRuns: Int,
        val totalBalls: Int,
        val dismissals: Int,
        val average: Double,
        val strikeRate: Double,
        val fours: Int,
        val sixes: Int,
        val boundaryPercentage: Double,
        val dotBallPercentage: Double,
        val powerplayStrikeRate: Double,
        val middleStrikeRate: Double,
        val deathStrikeRate: Double,
        val venueAverages: Map<String, Double>,
        val formatAverages: Map<String, Double>,
        val recentFormStrikeRate: Double   // last 10 matches
    )

    data class PlayerBowlingStats(
        val name: String,
        val bowlingInnings: Int,
        val wickets: Int,
        val runsConceded: Int,
        val ballsBowled: Int,
        val bowlingAverage: Double,
        val economyRate: Double,
        val strikeRate: Double,
        val dotBallPercentage: Double,
        val powerplayEconomy: Double,
        val middleEconomy: Double,
        val deathEconomy: Double,
        val venueEconomies: Map<String, Double>,
        val recentFormEconomy: Double       // last 10 matches
    )

    data class CombinedPlayerStats(
        val name: String,
        val batting: PlayerBattingStats?,
        val bowling: PlayerBowlingStats?
    )

    // MARK: - Internal Accumulators

    private class BattingAccumulator(val name: String) {
        var innings = 0
        var totalRuns = 0
        var totalBalls = 0
        var dismissals = 0
        var fours = 0
        var sixes = 0
        var dotBalls = 0
        var powerplayRuns = 0; var powerplayBalls = 0
        var middleRuns = 0; var middleBalls = 0
        var deathRuns = 0; var deathBalls = 0
        val venueStats = mutableMapOf<String, PhaseStats>()
        val formatStats = mutableMapOf<String, PhaseStats>()

        // Recent form — bounded to last 10 matches by date
        val recentMatchDates: ArrayDeque<Long> = ArrayDeque(10)
        private val recentMatchRuns: ArrayDeque<Int> = ArrayDeque(10)
        private val recentMatchBalls: ArrayDeque<Int> = ArrayDeque(10)
        var recentRuns: Int = 0
        var recentBallsFaced: Int = 0

        /**
         * Call once per innings (not per ball). Appends match timestamp, accumulates
         * runs/balls, and evicts the oldest entry when the window exceeds 10 matches.
         */
        fun updateBattingForm(matchTimestamp: Long, inningsRuns: Int, inningsBalls: Int) {
            if (recentMatchDates.lastOrNull() == matchTimestamp) {
                // Same match, second innings — merge into last entry instead of appending
                recentMatchRuns[recentMatchRuns.size - 1] += inningsRuns
                recentMatchBalls[recentMatchBalls.size - 1] += inningsBalls
                recentRuns += inningsRuns
                recentBallsFaced += inningsBalls
                return
            }

            // New match — append
            recentMatchDates.addLast(matchTimestamp)
            recentMatchRuns.addLast(inningsRuns)
            recentMatchBalls.addLast(inningsBalls)
            recentRuns += inningsRuns
            recentBallsFaced += inningsBalls

            // Evict oldest entry if window exceeds 10 matches
            if (recentMatchDates.size > 10) {
                recentRuns -= recentMatchRuns.removeFirst()
                recentBallsFaced -= recentMatchBalls.removeFirst()
                recentMatchDates.removeFirst()
            }
        }

        fun toStats(): PlayerBattingStats {
            val avg = if (dismissals > 0) totalRuns.toDouble() / dismissals else totalRuns.toDouble()
            val sr = if (totalBalls > 0) (totalRuns.toDouble() / totalBalls) * 100.0 else 0.0
            val boundaryRuns = fours * 4 + sixes * 6
            val boundaryPct = if (totalRuns > 0) boundaryRuns.toDouble() / totalRuns * 100.0 else 0.0
            val dotPct = if (totalBalls > 0) dotBalls.toDouble() / totalBalls * 100.0 else 0.0

            val ppSR = if (powerplayBalls > 0) (powerplayRuns.toDouble() / powerplayBalls) * 100.0 else 0.0
            val midSR = if (middleBalls > 0) (middleRuns.toDouble() / middleBalls) * 100.0 else 0.0
            val deathSR = if (deathBalls > 0) (deathRuns.toDouble() / deathBalls) * 100.0 else 0.0

            val venueAvgs = venueStats.mapValues { (_, s) ->
                if (s.balls > 0) (s.runs.toDouble() / s.balls) * 100.0 else 0.0
            }
            val formatAvgs = formatStats.mapValues { (_, s) ->
                if (s.balls > 0) (s.runs.toDouble() / s.balls) * 100.0 else 0.0
            }

            val recentFormSR = when {
                recentBallsFaced > 0 -> recentRuns.toDouble() / recentBallsFaced * 100.0
                totalBalls > 0       -> avg   // career fallback
                else                 -> 120.0 // no data — use population default
            }

            return PlayerBattingStats(
                name = name,
                innings = innings,
                totalRuns = totalRuns,
                totalBalls = totalBalls,
                dismissals = dismissals,
                average = avg,
                strikeRate = sr,
                fours = fours,
                sixes = sixes,
                boundaryPercentage = boundaryPct,
                dotBallPercentage = dotPct,
                powerplayStrikeRate = ppSR,
                middleStrikeRate = midSR,
                deathStrikeRate = deathSR,
                venueAverages = venueAvgs,
                formatAverages = formatAvgs,
                recentFormStrikeRate = recentFormSR
            )
        }
    }

    private class BowlingAccumulator(val name: String) {
        var bowlingInnings = 0
        var wickets = 0
        var runsConceded = 0
        var ballsBowled = 0
        var dotBalls = 0
        var powerplayRuns = 0; var powerplayBalls = 0
        var middleRuns = 0; var middleBalls = 0
        var deathRuns = 0; var deathBalls = 0
        val venueStats = mutableMapOf<String, PhaseStats>()

        // Recent form — bounded to last 10 matches by date
        val recentMatchDates: ArrayDeque<Long> = ArrayDeque(10)
        private val recentMatchRunsConceded: ArrayDeque<Int> = ArrayDeque(10)
        private val recentMatchBallsBowled: ArrayDeque<Int> = ArrayDeque(10)
        var recentRunsConceded: Int = 0
        var recentBallsBowled: Int = 0

        /**
         * Call once per innings. Appends match timestamp, accumulates runs/balls,
         * and evicts the oldest entry when the window exceeds 10 matches.
         */
        fun updateBowlingForm(matchTimestamp: Long, inningsRuns: Int, inningsBalls: Int) {
            if (recentMatchDates.lastOrNull() == matchTimestamp) {
                // Same match, second innings — merge into last entry instead of appending
                recentMatchRunsConceded[recentMatchRunsConceded.size - 1] += inningsRuns
                recentMatchBallsBowled[recentMatchBallsBowled.size - 1] += inningsBalls
                recentRunsConceded += inningsRuns
                recentBallsBowled += inningsBalls
                return
            }

            // New match — append
            recentMatchDates.addLast(matchTimestamp)
            recentMatchRunsConceded.addLast(inningsRuns)
            recentMatchBallsBowled.addLast(inningsBalls)
            recentRunsConceded += inningsRuns
            recentBallsBowled += inningsBalls

            // Evict oldest entry if window exceeds 10 matches
            if (recentMatchDates.size > 10) {
                recentRunsConceded -= recentMatchRunsConceded.removeFirst()
                recentBallsBowled -= recentMatchBallsBowled.removeFirst()
                recentMatchDates.removeFirst()
            }
        }

        fun toStats(): PlayerBowlingStats {
            val overs = ballsBowled / 6.0
            val avg = if (wickets > 0) runsConceded.toDouble() / wickets else 999.0
            val eco = if (overs > 0) runsConceded.toDouble() / overs else 0.0
            val sr = if (wickets > 0) ballsBowled.toDouble() / wickets else 999.0
            val dotPct = if (ballsBowled > 0) dotBalls.toDouble() / ballsBowled * 100.0 else 0.0

            val ppOvers = powerplayBalls / 6.0
            val ppEco = if (ppOvers > 0) powerplayRuns.toDouble() / ppOvers else 0.0
            val midOvers = middleBalls / 6.0
            val midEco = if (midOvers > 0) middleRuns.toDouble() / midOvers else 0.0
            val deathOvers = deathBalls / 6.0
            val deathEco = if (deathOvers > 0) deathRuns.toDouble() / deathOvers else 0.0

            val venueEcos = venueStats.mapValues { (_, s) ->
                val vo = s.balls / 6.0
                if (vo > 0) s.runs.toDouble() / vo else 0.0
            }

            val recentOvers = recentBallsBowled / 6.0
            val recentFormEco = when {
                recentOvers > 0 -> recentRunsConceded.toDouble() / recentOvers
                overs > 0       -> eco  // career fallback
                else            -> 8.0  // no data — use population default
            }

            return PlayerBowlingStats(
                name = name,
                bowlingInnings = bowlingInnings,
                wickets = wickets,
                runsConceded = runsConceded,
                ballsBowled = ballsBowled,
                bowlingAverage = avg,
                economyRate = eco,
                strikeRate = sr,
                dotBallPercentage = dotPct,
                powerplayEconomy = ppEco,
                middleEconomy = midEco,
                deathEconomy = deathEco,
                venueEconomies = venueEcos,
                recentFormEconomy = recentFormEco
            )
        }
    }

    private class PhaseStats {
        var runs = 0
        var balls = 0
    }
}
