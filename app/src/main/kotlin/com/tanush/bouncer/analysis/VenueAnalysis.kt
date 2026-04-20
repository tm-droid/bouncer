package com.tanush.bouncer.analysis

import com.tanush.bouncer.models.CricsheetMatch
import kotlin.math.max

/**
 * Venue-specific analytics computed from Cricsheet match data.
 *
 * Tracks per-venue scoring patterns, toss impact, chase success rates,
 * and phase-specific run rates to inform predictions.
 */
class VenueAnalysis {

    private val venueAccumulators = mutableMapOf<String, VenueAccumulator>()

    /**
     * Process matches to build venue statistics.
     */
    fun processMatches(matches: List<CricsheetMatch>) {
        for (match in matches) {
            processMatch(match)
        }
    }

    fun processMatch(match: CricsheetMatch) {
        val venue = match.venue
        if (venue.isBlank()) return
        
        val acc = venueAccumulators.getOrPut(venue) { VenueAccumulator(venue, match.city) }
        acc.matchesPlayed += 1
        
        // First innings score
        val firstInnings = match.innings.getOrNull(0)
        val secondInnings = match.innings.getOrNull(1)
        
        if (firstInnings != null) {
            acc.firstInningsScores.add(firstInnings.total)
            
            // Phase run rates for first innings
            for (over in firstInnings.overs) {
                val overRuns = over.balls.sumOf { it.runs }
                when {
                    over.overNumber < 6 -> { acc.firstPowerplayRuns += overRuns; acc.firstPowerplayOvers += 1 }
                    over.overNumber < 15 -> { acc.firstMiddleRuns += overRuns; acc.firstMiddleOvers += 1 }
                    else -> { acc.firstDeathRuns += overRuns; acc.firstDeathOvers += 1 }
                }
            }
        }
        
        if (secondInnings != null) {
            acc.secondInningsScores.add(secondInnings.total)
        }
        
        // Toss impact
        val toss = match.toss
        if (toss != null && match.result.winner != null) {
            val tossWinnerBattedFirst = toss.decision == "bat"
            val tossWinnerWon = toss.winner == match.result.winner
            
            if (tossWinnerBattedFirst) {
                acc.tossBatFirstCount += 1
                if (tossWinnerWon) acc.tossBatFirstWins += 1
            } else {
                acc.tossFieldFirstCount += 1
                if (tossWinnerWon) acc.tossFieldFirstWins += 1
            }
        }
        
        // Chase success
        if (secondInnings != null && match.result.winner != null) {
            acc.chasesAttempted += 1
            if (match.result.winner == secondInnings.team) {
                acc.chasesSucceeded += 1
            }
        }
        
        // Wickets per match
        val totalWickets = match.innings.sumOf { it.wickets }
        acc.totalWickets += totalWickets
    }

    /**
     * Get computed statistics for a venue.
     */
    fun getVenueStats(venue: String): VenueStats? {
        val acc = venueAccumulators[venue] ?: return null
        return acc.toStats()
    }

    /**
     * Find the closest matching venue name (fuzzy match).
     */
    fun findVenue(query: String): VenueStats? {
        // Exact match first
        venueAccumulators[query]?.let { return it.toStats() }
        
        // Partial match
        val match = venueAccumulators.entries
            .filter { it.key.contains(query, ignoreCase = true) || query.contains(it.key, ignoreCase = true) }
            .maxByOrNull { it.value.matchesPlayed }
        
        return match?.value?.toStats()
    }

    fun getAllVenues(): List<VenueStats> = venueAccumulators.values.map { it.toStats() }

    // MARK: - Data Classes

    data class VenueStats(
        val venue: String,
        val city: String?,
        val matchesPlayed: Int,
        val avgFirstInningsScore: Double,
        val avgSecondInningsScore: Double,
        val tossWinBatFirstWinRate: Double,
        val tossWinFieldFirstWinRate: Double,
        val chaseSuccessRate: Double,
        val avgWicketsPerMatch: Double,
        val powerplayRunRate: Double,     // first innings powerplay avg RR
        val middleOversRunRate: Double,
        val deathOversRunRate: Double
    )

    // MARK: - Internal

    private class VenueAccumulator(val venue: String, val city: String?) {
        var matchesPlayed = 0
        val firstInningsScores = mutableListOf<Int>()
        val secondInningsScores = mutableListOf<Int>()
        var tossBatFirstCount = 0; var tossBatFirstWins = 0
        var tossFieldFirstCount = 0; var tossFieldFirstWins = 0
        var chasesAttempted = 0; var chasesSucceeded = 0
        var totalWickets = 0
        var firstPowerplayRuns = 0; var firstPowerplayOvers = 0
        var firstMiddleRuns = 0; var firstMiddleOvers = 0
        var firstDeathRuns = 0; var firstDeathOvers = 0

        fun toStats(): VenueStats {
            val avgFirst = if (firstInningsScores.isNotEmpty()) firstInningsScores.average() else 0.0
            val avgSecond = if (secondInningsScores.isNotEmpty()) secondInningsScores.average() else 0.0
            val tossBatWR = if (tossBatFirstCount > 0) tossBatFirstWins.toDouble() / tossBatFirstCount else 0.5
            val tossFieldWR = if (tossFieldFirstCount > 0) tossFieldFirstWins.toDouble() / tossFieldFirstCount else 0.5
            val chaseRate = if (chasesAttempted > 0) chasesSucceeded.toDouble() / chasesAttempted else 0.5
            val avgWickets = if (matchesPlayed > 0) totalWickets.toDouble() / matchesPlayed else 0.0
            
            val ppRR = if (firstPowerplayOvers > 0) firstPowerplayRuns.toDouble() / firstPowerplayOvers else 0.0
            val midRR = if (firstMiddleOvers > 0) firstMiddleRuns.toDouble() / firstMiddleOvers else 0.0
            val deathRR = if (firstDeathOvers > 0) firstDeathRuns.toDouble() / firstDeathOvers else 0.0
            
            return VenueStats(
                venue = venue,
                city = city,
                matchesPlayed = matchesPlayed,
                avgFirstInningsScore = avgFirst,
                avgSecondInningsScore = avgSecond,
                tossWinBatFirstWinRate = tossBatWR,
                tossWinFieldFirstWinRate = tossFieldWR,
                chaseSuccessRate = chaseRate,
                avgWicketsPerMatch = avgWickets,
                powerplayRunRate = ppRR,
                middleOversRunRate = midRR,
                deathOversRunRate = deathRR
            )
        }
    }
}
