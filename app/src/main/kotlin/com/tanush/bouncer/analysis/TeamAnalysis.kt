package com.tanush.bouncer.analysis

import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetInnings
import io.sentry.Sentry

fun WinProbabilityCalculator.updateTeamBattingStats(team: String, match: CricsheetMatch) {
    try {
        val battingInnings = match.innings.firstOrNull { it.team == team } ?: return
    
        val currentStats = teamStats[team] ?: TeamStatistics()
        val newMatchesPlayed = currentStats.matchesPlayed + 1
    
        // Calculate power play and death overs stats
        val powerPlayOvers = battingInnings.overs.take(6)
        val deathOvers = battingInnings.overs.takeLast(4)
    
        val powerPlayRuns = powerPlayOvers.sumOf { it.runs }
        val deathOversRuns = deathOvers.sumOf { it.runs }
    
        val powerPlayRunRate = powerPlayRuns.toDouble() / 6.0
        val deathOversRunRate = deathOversRuns.toDouble() / 4.0
    
        // Update team statistics
        val newBattingAverage = (currentStats.battingAverage * currentStats.matchesPlayed + battingInnings.total) / newMatchesPlayed
        val newPowerPlayRunRate = (currentStats.powerPlayRunRate * currentStats.matchesPlayed + powerPlayRunRate) / newMatchesPlayed
        val newDeathOversRunRate = (currentStats.deathOversRunRate * currentStats.matchesPlayed + deathOversRunRate) / newMatchesPlayed
    
        teamStats[team] = TeamStatistics(
            battingAverage = newBattingAverage,
            bowlingAverage = currentStats.bowlingAverage, // Updated in updateTeamBowlingStats
            winRate = calculateUpdatedWinRate(team, match),
            powerPlayRunRate = newPowerPlayRunRate,
            deathOversRunRate = newDeathOversRunRate,
            matchesPlayed = newMatchesPlayed
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

fun WinProbabilityCalculator.updateTeamBowlingStats(team: String, match: CricsheetMatch) {
    try {
        val bowlingInnings = match.innings.firstOrNull { it.team != team } ?: return
    
        val currentStats = teamStats[team] ?: TeamStatistics()
        val newMatchesPlayed = currentStats.matchesPlayed + 1
    
        // Calculate bowling average
        val runsGiven = bowlingInnings.total
        val wicketsTaken = bowlingInnings.wickets
    
        val bowlingAverage = if (wicketsTaken > 0) runsGiven.toDouble() / wicketsTaken else runsGiven.toDouble()
        val newBowlingAverage = (currentStats.bowlingAverage * currentStats.matchesPlayed + bowlingAverage) / newMatchesPlayed
    
        teamStats[team] = currentStats.copy(
            bowlingAverage = newBowlingAverage,
            matchesPlayed = newMatchesPlayed
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

fun WinProbabilityCalculator.calculateUpdatedWinRate(team: String, match: CricsheetMatch): Double {
    try {
        val currentStats = teamStats[team] ?: TeamStatistics()
    
        // Determine if the team won this match
        val teamWon = match.outcome?.winner == team
        val wins = if (teamWon) 1 else 0
    
        // Calculate new win rate
        val totalWins = currentStats.winRate * currentStats.matchesPlayed + wins
        val newWinRate = totalWins / (currentStats.matchesPlayed + 1)
    
        return newWinRate
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
