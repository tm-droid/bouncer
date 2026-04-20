package com.tanush.bouncer.models

data class PlayerProfile(
    val id: String,
    val name: String,
    val country: String,
    val role: String,
    val dateOfBirth: String = "",
    val battingStyle: String = "",
    val bowlingStyle: String = "",
    val imageUrl: String? = null,
    val bio: String = "",
    val teams: List<String> = emptyList(),
    val stats: Map<String, FormatStats> = emptyMap(), // "Test", "ODI", "T20I", "IPL"
)

data class FormatStats(
    val matches: Int = 0,
    val innings: Int = 0,
    val runs: Int = 0,
    val highScore: String = "-",
    val average: Double = 0.0,
    val strikeRate: Double = 0.0,
    val hundreds: Int = 0,
    val fifties: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val wickets: Int = 0,
    val bowlingAverage: Double = 0.0,
    val economy: Double = 0.0,
    val bestBowling: String = "-",
    val fiveWickets: Int = 0,
    val catches: Int = 0,
)
