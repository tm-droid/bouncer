package com.tanush.bouncer.analysis

import android.util.Log
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.models.CricsheetMatch

enum class PitchCondition(val value: Double) {
    BATTING_FRIENDLY(1.2),
    BALANCED(1.0),
    BOWLING_FRIENDLY(0.8)
}

data class PlayerForm(
    val playerName: String,
    val average: Double,
    val strikeRate: Double,
    val recentForm: List<Double>
) {
    val name: String get() = playerName
}

data class MatchSituation(
    val currentRunRate: Double,
    val requiredRunRate: Double,
    val oversRemaining: Double,
    val wicketsInHand: Int,
    val lastFiveOversRuns: Int,
    val isPowerPlay: Boolean,
    val isDeathOvers: Boolean,
    val lastTwoOversRuns: Int = 0
)

data class TeamPerformance(
    val team1: String,
    val team2: String,
    val team1WinRate: Double,
    val team2WinRate: Double,
    val team1BattingStrength: Double,
    val team2BattingStrength: Double,
    val team1BowlingStrength: Double,
    val team2BowlingStrength: Double
)

data class TeamStatistics(
    val battingAverage: Double = 0.0,
    val bowlingAverage: Double = 0.0,
    val winRate: Double = 0.0,
    val powerPlayRunRate: Double = 0.0,
    val deathOversRunRate: Double = 0.0,
    val matchesPlayed: Int = 0
)

class WinProbabilityCalculator(
    private val requiredRunRate: Double = 6.0,
    private val battingTeamStrength: Double = 0.5,
    private val bowlingTeamStrength: Double = 0.5,
    private val pitchCondition: PitchCondition = PitchCondition.BALANCED,
    private val battingTeamForm: List<PlayerForm> = emptyList(),
    private val bowlingTeamForm: List<PlayerForm> = emptyList(),
    private val historicalData: Map<String, Double> = emptyMap(),
    /** Total balls in one innings: T20=120, ODI=300, Test=450. Defaults to 300 (ODI). */
    val totalBalls: Int = 300,
    private val matchId: String = "",
    private val inningsNumber: Int = 0
) {

    companion object {
        private const val TAG = "WinProbabilityCalculator"
    }

    // Team stats map — populated by loadCricsheetData() and read by TeamAnalysis extension functions
    // and CricsheetTrainingWorker. Kept as a mutable map for compatibility.
    var teamStats: MutableMap<String, TeamStatistics> = mutableMapOf()

    data class VenueStatistics(
        val averageFirstInningsScore: Double,
        val averageRunRate: Double,
        val averageWicketRate: Double,
        val pitchType: PitchCondition,
        val matchesPlayed: Int
    )

    /**
     * Load historical match data and build team statistics.
     * Called by CricsheetTrainingWorker before accessing teamStats.
     */
    fun loadCricsheetData(matches: List<CricsheetMatch>) {
        for (match in matches) {
            updateTeamStats(match)
        }
    }

    private fun updateTeamStats(match: CricsheetMatch) {
        val battingTeam = match.teams.batting
        val bowlingTeam = match.teams.bowling
        if (!teamStats.containsKey(battingTeam)) {
            teamStats[battingTeam] = TeamStatistics()
        }
        if (!teamStats.containsKey(bowlingTeam)) {
            teamStats[bowlingTeam] = TeamStatistics()
        }
        updateTeamBattingStats(battingTeam, match)
        updateTeamBowlingStats(bowlingTeam, match)
    }

    /**
     * Calculate win probability by delegating to the `bouncer-win-probability` Appwrite Function.
     *
     * Returns a percentage in [0.0, 100.0]. Falls back to 50.0 on network error.
     */
    suspend fun calculateWinProbability(
        runsNeeded: Int,
        ballsRemaining: Int,
        wicketsLost: Int,
        currentRunRate: Double,
        requiredRunRate: Double
    ): Double = calculateMLWinProbability(
        runsNeeded, ballsRemaining, wicketsLost,
        currentRunRate, requiredRunRate
    )

    suspend fun calculateMLWinProbability(
        runsNeeded: Int,
        ballsRemaining: Int,
        wicketsLost: Int,
        currentRunRate: Double,
        requiredRunRate: Double,
        battingTeam: String = "",
        bowlingTeam: String = ""
    ): Double {
        val requestBody = buildString {
            append("""{"runsNeeded":$runsNeeded,"ballsRemaining":$ballsRemaining,""")
            append(""""wicketsLost":$wicketsLost,"currentRunRate":$currentRunRate,""")
            append(""""requiredRunRate":$requiredRunRate,""")
            append(""""battingTeam":"$battingTeam","bowlingTeam":"$bowlingTeam"}""")
        }
        val responseJson = AppwriteClient.executeFunction("bouncer-win-probability", requestBody)
        return if (responseJson != null) {
            try {
                val obj = org.json.JSONObject(responseJson)
                obj.getDouble("team1WinProbability") * 100.0
            } catch (e: Exception) {
                Log.w(TAG, "Win probability parse error: ${e.message}")
                50.0
            }
        } else {
            50.0 // fallback: no connection or function error
        }
    }
}
