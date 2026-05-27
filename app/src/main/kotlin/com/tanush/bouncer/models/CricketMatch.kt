package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.google.gson.annotations.SerializedName
import com.tanush.bouncer.utils.parseRuns
import com.tanush.bouncer.utils.parseWickets

@Stable
data class CricketMatch(
    val id: String,
    val seriesId: String,
    val matchTitle: String,
    val matchType: String,
    val status: String,
    val teams: List<Team>,
    val venue: String,
    val date: String,
    val matchTime: String,
    val score: MatchScore?,
    val currentStatus: String,
    val info: String,
    // Scraped live data
    var recentBalls: List<String>? = null,
    var currentBowler: ScrapedBowler? = null,
    var scrapedBatsmen: List<ScrapedBatsman>? = null,
    // Add category for filtering
    var category: String? = null,
    var gender: String = "Men",
    var competitionType: String = "International",
    var winProbability: Double = 50.0,
    // Toss and officials — populated when available from live match page
    var tossWinner: String? = null,
    var tossDecision: String? = null,
    var umpire1: String? = null,
    var umpire2: String? = null,
) {
    val isLive: Boolean 
        get() = status.lowercase().contains("live") || 
                currentStatus.lowercase().contains("live") || 
                currentStatus.lowercase().contains("opted to") || 
                currentStatus.lowercase().contains("need")

    val isCompleted: Boolean
        get() {
            val s = currentStatus.lowercase()
            val st = status.lowercase()
            return s.contains("won") || s.contains("drawn") || s.contains("tied") ||
                s.contains("no result") || st.contains("complete") ||
                st.contains("won") || st.contains("drawn") || st.contains("tied") || st.contains("no result")
        }

    /**
     * Heuristic calculation of win probability for Team 1.
     * Handles all match states: completed, live, stumps/break, and upcoming.
     */
    fun calculateHeuristicProbability(): Double {
        // Completed matches: parse result to determine winner
        if (isCompleted) {
            val statusLower = currentStatus.lowercase()
            val statusAlt = status.lowercase()
            val combined = "$statusLower $statusAlt"
            if (combined.contains("drawn") || combined.contains("tied") || combined.contains("no result")) {
                return 50.0
            }
            // "Team X won by..."
            val team1Name = teams.getOrNull(0)?.name?.lowercase() ?: ""
            val team1Acronym = teams.getOrNull(0)?.acronym?.lowercase() ?: ""
            return when {
                (team1Name.isNotBlank() && combined.contains(team1Name)) || 
                    (team1Acronym.isNotBlank() && combined.contains(team1Acronym)) -> 100.0
                else -> 0.0 // Team 2 won
            }
        }

        // For matches at stumps/break: calculate based on current match situation
        val matchScore = score
        val battingTeam = teams.find { it.isBatting }
        val isChasing = matchScore?.requiredRunRate != null

        // If no score data at all, return 50 (truly unknown)
        if (matchScore == null && battingTeam == null) return 50.0

        // If chasing, use RRR-based calculation
        if (isChasing) {
            val reqRR = matchScore?.requiredRunRate?.toDoubleOrNull() ?: return 50.0
            val currentRR = matchScore.currentRunRate?.toDoubleOrNull() ?: 6.0

            val oversParts = matchScore.overs?.split(".") ?: return 50.0
            val oversDone = oversParts[0].toIntOrNull() ?: 0
            val ballsDoneInOver = if (oversParts.size > 1) oversParts[1].toIntOrNull() ?: 0 else 0
            val totalBallsDone = (oversDone * 6) + ballsDoneInOver

            val totalBalls = when {
                matchType.contains("ODI", ignoreCase = true) -> 300
                matchType.contains("T20", ignoreCase = true) || matchType.contains("Twenty20", ignoreCase = true) -> 120
                else -> 120
            }
            val ballsRemaining = (totalBalls - totalBallsDone).coerceAtLeast(1)

            val scoreText = battingTeam?.score ?: ""
            val wicketsLost = parseWickets(scoreText)
            val wicketsInHand = (10 - wicketsLost).coerceIn(0, 10)

            var prob = 50.0
            // Moderate RR gap impact (capped at 3% per run, not 5%)
            val rrGap = currentRR - reqRR
            prob += (rrGap * 3.0).coerceIn(-25.0, 25.0)
            // Wickets impact (2.5% per wicket difference from 5)
            prob += (wicketsInHand - 5) * 2.5
            // Pressure from overs remaining
            val oversRatio = ballsRemaining.toDouble() / totalBalls
            if (reqRR > 9.0) {
                prob -= ((1.0 - oversRatio) * 15.0) // max -15% pressure late in innings
            }

            return prob.coerceIn(5.0, 95.0)
        }

        // For Test matches (first innings) or matches without chase data
        // Use wickets and runs position as heuristic
        val scoreText = battingTeam?.score ?: ""
        val runsVal = parseRuns(scoreText)
        val wicketsVal = parseWickets(scoreText)
        val runs: Int? = if (scoreText.isNotBlank()) runsVal else null
        val wickets: Int? = if (scoreText.isNotBlank()) wicketsVal else null
        if (runs != null && wickets != null) {
            // Simple: more runs = stronger, more wickets lost = weaker
            var prob = 50.0
            prob += (runs / 50.0).coerceAtMost(10.0)  // max +10 for big scores
            prob -= wickets * 2.0  // -2% per wicket lost
            return prob.coerceIn(25.0, 75.0) // First innings: keep range modest
        }

        return 50.0
    }

    @Immutable
    data class Team(
        val name: String,
        val score: String,
        val status: String,
        val isBatting: Boolean,
        val logoUrl: String?,
        val acronym: String = "",
        val overs: String = "0.0"
    )

    @Immutable
    data class MatchScore(
        @SerializedName("inningsBreak") val currentInnings: String,
        @SerializedName("batsmanScores") val batsmen: List<BatsmanScore>,
        @SerializedName("bowlerScores") val bowlers: List<BowlerScore>,
        // Additional properties for live activity content state
        val overs: String?,
        val currentRunRate: String?,
        val requiredRunRate: String?,
        val lastWicket: String?,
        val recentBalls: String?
    )

    @Immutable
    data class BatsmanScore(
        val name: String,
        val runs: Int,
        val balls: Int,
        val fours: Int,
        val sixes: Int,
        val strikeRate: Double
    )

    @Immutable
    data class BowlerScore(
        val name: String,
        val overs: Double,
        val maidens: Int,
        val runs: Int,
        val wickets: Int,
        val economy: Double
    )
}
