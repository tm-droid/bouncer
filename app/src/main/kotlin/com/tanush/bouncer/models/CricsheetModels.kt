package com.tanush.bouncer.models

import java.util.Date

// MARK: - Cricsheet Supporting Types

data class CricsheetToss(
    val winner: String,
    val decision: String // "bat" or "field"
)

data class CricsheetEvent(
    val name: String,
    val matchNumber: Int? = null,
    val group: String? = null,
    val stage: String? = null
)

data class CricsheetOfficials(
    val umpires: List<String> = emptyList(),
    val matchReferees: List<String> = emptyList(),
    val tvUmpires: List<String> = emptyList(),
    val reserveUmpires: List<String> = emptyList()
)

data class CricsheetReview(
    val by: String,
    val decision: String, // "struck down" or "upheld"
    val batter: String? = null,
    val umpire: String? = null,
    val umpiresCall: Boolean = false
)

data class CricsheetPowerplay(
    val from: Double,
    val to: Double,
    val type: String // "mandatory", "batting", "fielding"
)

data class CricsheetTarget(
    val overs: Double? = null,
    val runs: Int? = null
)

data class CricsheetWicketDetail(
    val kind: String,
    val playerOut: String,
    val fielders: List<String> = emptyList()
)

// MARK: - Cricsheet Data Structures

data class CricsheetMatch(
    val matchId: String,
    val date: Date,
    val venue: String,
    val teams: Teams,
    val innings: List<CricsheetInnings>,
    val result: MatchResult,
    val outcome: Outcome? = null,
    // Expanded fields from Cricsheet JSON spec
    val matchType: String = "",          // Test, ODI, T20, IT20, ODM, MDM
    val gender: String = "",             // male, female
    val city: String? = null,
    val season: String? = null,
    val ballsPerOver: Int = 6,
    val event: CricsheetEvent? = null,
    val toss: CricsheetToss? = null,
    val players: Map<String, List<String>> = emptyMap(), // team name → player list
    val registry: Map<String, String> = emptyMap(),      // player name → unique ID
    val officials: CricsheetOfficials? = null,
    val playerOfMatch: List<String> = emptyList(),
    val teamType: String = "",           // international, club
    val dates: List<Date> = emptyList(), // all match dates (multi-day)
    val oversLimit: Int? = null          // overs per innings if limited
) {
    data class Teams(
        val batting: String,
        val bowling: String
    )
    
    data class Outcome(
        val winner: String?,
        val result: String?,
        val method: String? = null,      // D/L, VJD, etc.
        val eliminator: String? = null,  // super over winner
        val bowlOut: String? = null      // bowl out winner
    )
}

data class CricsheetInnings(
    val team: String,
    val overs: List<CricsheetOver>,
    val total: Int,
    val wickets: Int,
    val runRate: Double,
    // Expanded fields
    val powerplays: List<CricsheetPowerplay> = emptyList(),
    val target: CricsheetTarget? = null,
    val declared: Boolean = false,
    val forfeited: Boolean = false,
    val superOver: Boolean = false,
    val absentHurt: List<String> = emptyList()
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
    val extraType: String?,
    // Expanded fields — per-delivery player tracking
    val batter: String = "",
    val bowler: String = "",
    val nonStriker: String = "",
    val batterRuns: Int = 0,         // runs scored by batter (excludes extras)
    val isBoundary: Boolean = false, // true if 4/6 hit to boundary
    val wicketDetails: List<CricsheetWicketDetail> = emptyList(),
    val review: CricsheetReview? = null
)

data class MatchResult(
    val winner: String?,
    val margin: Int,
    val marginType: String // "runs" or "wickets"
)

