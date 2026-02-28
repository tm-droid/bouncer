package com.tanush.bouncer.analysis

import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetInnings
import com.tanush.bouncer.models.Over

data class PitchAnalysisResult(
    val condition: String,
    val behavior: String,
    val averageScore: Double,
    val averageRunRate: Double,
    val averageWicketRate: Double
)

enum class MatchPhase {
    POWER_PLAY,
    MIDDLE,
    DEATH
}

fun WinProbabilityCalculator.analyzePitchConditions(match: CricsheetMatch): PitchAnalysisResult {
    // Calculate run rates for different phases
    val firstInningsRates = calculatePhaseRunRates(match.innings[0])
    val secondInningsRates = if (match.innings.size > 1) {
        calculatePhaseRunRates(match.innings[1])
    } else {
        emptyMap()
    }
    
    // Analyze pitch behavior
    val behavior = analyzePitchBehavior(firstInningsRates, secondInningsRates)
    
    // Calculate averages
    val averageScore = match.innings.sumOf { it.total }.toDouble() / match.innings.size
    val averageRunRate = match.innings.sumOf { it.runRate } / match.innings.size
    val averageWicketRate = match.innings.sumOf { it.wickets }.toDouble() / (match.innings.size * 20)
    
    return PitchAnalysisResult(
        condition = determinePitchCondition(averageRunRate, averageWicketRate),
        behavior = behavior,
        averageScore = averageScore,
        averageRunRate = averageRunRate,
        averageWicketRate = averageWicketRate
    )
}

private fun calculatePhaseRunRates(innings: CricsheetInnings): Map<MatchPhase, Double> {
    val rates = mutableMapOf<MatchPhase, Double>()
    
    // Power Play (first 6 overs)
    val powerPlayOvers = innings.overs.take(6)
    rates[MatchPhase.POWER_PLAY] = calculatePhaseRunRate(powerPlayOvers)
    
    // Middle Overs (overs 7-15)
    val middleOvers = innings.overs.drop(6).take(9)
    rates[MatchPhase.MIDDLE] = calculatePhaseRunRate(middleOvers)
    
    // Death Overs (last 5 overs)
    val deathOvers = innings.overs.takeLast(5)
    rates[MatchPhase.DEATH] = calculatePhaseRunRate(deathOvers)
    
    return rates
}

private fun calculatePhaseRunRate(overs: List<Over>): Double {
    if (overs.isEmpty()) return 0.0
    val totalRuns = overs.sumOf { it.runs }
    return totalRuns.toDouble() / overs.size
}

private fun analyzePitchBehavior(
    firstInningsRates: Map<MatchPhase, Double>,
    secondInningsRates: Map<MatchPhase, Double>
): String {
    val firstPowerPlay = firstInningsRates[MatchPhase.POWER_PLAY] ?: 0.0
    val firstMiddle = firstInningsRates[MatchPhase.MIDDLE] ?: 0.0
    val firstDeath = firstInningsRates[MatchPhase.DEATH] ?: 0.0
    
    val secondPowerPlay = secondInningsRates[MatchPhase.POWER_PLAY] ?: 0.0
    val secondMiddle = secondInningsRates[MatchPhase.MIDDLE] ?: 0.0
    val secondDeath = secondInningsRates[MatchPhase.DEATH] ?: 0.0
    
    return when {
        firstPowerPlay > firstMiddle && firstMiddle > firstDeath -> "Slowing down throughout"
        firstDeath > firstMiddle && firstMiddle > firstPowerPlay -> "Getting easier to bat"
        firstPowerPlay > 8.0 && firstMiddle < 7.0 -> "Good for batting in powerplay, then difficult"
        secondPowerPlay > firstPowerPlay && secondMiddle > firstMiddle -> "Improving as game progressed"
        else -> "Consistent throughout"
    }
}

private fun determinePitchCondition(runRate: Double, wicketRate: Double): String {
    return when {
        runRate > 8.5 && wicketRate < 0.4 -> "Batting Paradise"
        runRate > 7.5 && wicketRate < 0.5 -> "Good for Batting"
        runRate < 6.5 && wicketRate > 0.6 -> "Bowler Friendly"
        runRate < 7.0 && wicketRate > 0.5 -> "Balanced"
        else -> "Average"
    }
}
