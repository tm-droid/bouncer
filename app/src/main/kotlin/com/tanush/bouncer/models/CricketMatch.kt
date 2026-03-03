package com.tanush.bouncer.models

import com.google.gson.annotations.SerializedName

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
    var winProbability: Double = 50.0
) {
    val isLive: Boolean 
        get() = status.lowercase().contains("live") || 
                currentStatus.lowercase().contains("live") || 
                currentStatus.lowercase().contains("opted to") || 
                currentStatus.lowercase().contains("need")

    /**
     * Heuristic calculation of win probability based on current match situation.
     * Used as a fallback when Monte Carlo simulation is not available.
     */
    fun calculateHeuristicProbability(): Double {
        if (!isLive) return 50.0
        
        val matchScore = score ?: return 50.0
        
        // Extract required data
        val reqRR = matchScore.requiredRunRate?.toDoubleOrNull() ?: return 50.0
        val currentRR = matchScore.currentRunRate?.toDoubleOrNull() ?: 6.0
        
        // Parse overs to get balls remaining
        // Format: "15.2" or "15"
        val oversParts = matchScore.overs?.split(".") ?: return 50.0
        val oversDone = oversParts[0].toIntOrNull() ?: 0
        val ballsDoneInOver = if (oversParts.size > 1) oversParts[1].toIntOrNull() ?: 0 else 0
        val totalBallsDone = (oversDone * 6) + ballsDoneInOver
        
        // Assume T20 if not specified
        val totalBalls = if (matchType.contains("ODI")) 300 else 120
        val ballsRemaining = (totalBalls - totalBallsDone).coerceAtLeast(1)
        
        // Estimate wickets in hand
        val battingTeam = teams.find { it.isBatting }
        val scoreText = battingTeam?.score ?: ""
        // Format: "142/3"
        val wicketsLost = scoreText.split("/").getOrNull(1)?.toIntOrNull() ?: 0
        val wicketsInHand = (10 - wicketsLost).coerceIn(0, 10)
        
        // Base probability starts at 50%
        var prob = 50.0
        
        // Impact of RRR vs CRR
        val rrGap = currentRR - reqRR
        prob += rrGap * 5.0 // Each 1.0 difference in RR shifts prob by 5%
        
        // Impact of wickets
        prob += (wicketsInHand - 5) * 4.0 // Having more than 5 wickets helps
        
        // Impact of balls remaining (pressure)
        if (reqRR > 10.0) {
            prob -= (120 - ballsRemaining) / 12.0 // Pressure increases as balls decrease
        }
        
        return prob.coerceIn(5.0, 95.0)
    }

    data class Team(
        val name: String,
        val score: String,
        val status: String,
        val isBatting: Boolean,
        val logoUrl: String?,
        val acronym: String = "",
        val overs: String = "0.0"
    )

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

    data class BatsmanScore(
        val name: String,
        val runs: Int,
        val balls: Int,
        val fours: Int,
        val sixes: Int,
        val strikeRate: Double
    )

    data class BowlerScore(
        val name: String,
        val overs: Double,
        val maidens: Int,
        val runs: Int,
        val wickets: Int,
        val economy: Double
    )
}
