package com.tanush.bouncer.models

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

@Serializable
@Immutable
data class Scorecard(
    val innings: List<Innings> = emptyList(),
    /**
     * One point per completed over, accumulated server-side by `bouncer-match-sync` from the live
     * page's over separators. Empty for a row written before it existed, and possibly gappy: overs
     * bowled while no container held the history are absent, never estimated.
     */
    val overs: List<OverPoint> = emptyList(),
    /**
     * The Cricbuzz team id of the side every [OverPoint.p] is the win probability OF — the
     * first-innings side, which is not necessarily the side this screen lists first. Null when no
     * point carries a probability, and then none may be drawn: an unknown orientation is not
     * a guess between two.
     */
    val probabilityTeamId: String? = null,
)

/** The batting side's position at the end of one over. Every figure nullable: absent is not zero. */
@Serializable
@Immutable
data class OverPoint(
    val innings: Int,
    val over: Int,
    val runs: Int? = null,
    val score: Int? = null,
    val wickets: Int? = null,
    /** Cricbuzz's short code for the batting side ("DUR"), as the separator names it. */
    val team: String? = null,
    /**
     * The win probability of [Scorecard.probabilityTeamId]'s side when this over ended, 0..1.
     * Null for a Test, for an over no container saw as it finished, and for any older row.
     */
    val p: Double? = null,
)

@Serializable
@Immutable
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

@Serializable
@Immutable
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

@Serializable
@Immutable
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

@Serializable
@Immutable
data class FallOfWicket(
    val score: String,
    val overs: String,
    val batsman: String,
    val number: Int = 0
)

@Serializable
@Immutable
data class PartnershipStats(
    val player1: String,
    val player2: String,
    val runs: String,
    val balls: String,
    val contribution1: String = "",
    val contribution2: String = ""
)
