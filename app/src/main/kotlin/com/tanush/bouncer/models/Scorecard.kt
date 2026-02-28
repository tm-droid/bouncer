package com.tanush.bouncer.models

data class Scorecard(
    val innings: List<Innings> = emptyList()
)

data class Innings(
    val teamName: String,
    val score: String,
    val overs: String,
    val batsmen: List<BatsmanStats>,
    val bowlers: List<BowlerStats>,
    val fallOfWickets: List<FallOfWicket>,
    val partnerships: List<PartnershipStats> = emptyList(),
    val extras: String,
    val total: String
)

data class BatsmanStats(
    val name: String,
    val dismissal: String,
    val runs: String,
    val balls: String,
    val fours: String,
    val sixes: String,
    val strikeRate: String,
    val profileUrl: String = ""
)

data class BowlerStats(
    val name: String,
    val overs: String,
    val maidens: String,
    val runs: String,
    val wickets: String,
    val noBalls: String,
    val wides: String,
    val economy: String,
    val profileUrl: String = ""
)

data class FallOfWicket(
    val score: String,
    val overs: String,
    val batsman: String,
    val number: Int = 0
)

data class PartnershipStats(
    val player1: String,
    val player2: String,
    val runs: String,
    val balls: String,
    val contribution1: String = "",
    val contribution2: String = ""
)
