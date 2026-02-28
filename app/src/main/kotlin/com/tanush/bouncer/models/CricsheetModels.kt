package com.tanush.bouncer.models

import java.util.Date

// MARK: - Cricsheet Data Structures
data class CricsheetMatch(
    val matchId: String,
    val date: Date,
    val venue: String,
    val teams: Teams,
    val innings: List<CricsheetInnings>,
    val result: MatchResult,
    val outcome: Outcome? = null
) {
    data class Teams(
        val batting: String,
        val bowling: String
    )
    
    data class Outcome(
        val winner: String?,
        val result: String?
    )
}

data class CricsheetInnings(
    val team: String,
    val overs: List<CricsheetOver>,
    val total: Int,
    val wickets: Int,
    val runRate: Double
)

data class CricsheetOver(
    val overNumber: Int,
    val balls: List<CricsheetBall>,
    val runs: Int,
    val wickets: Int
)

// Type alias for convenience
typealias Over = CricsheetOver

data class CricsheetBall(
    val runs: Int,
    val isWicket: Boolean,
    val wicketType: String?,
    val extras: Int,
    val extraType: String?
)

data class MatchResult(
    val winner: String?,
    val margin: Int,
    val marginType: String // "runs" or "wickets"
)

