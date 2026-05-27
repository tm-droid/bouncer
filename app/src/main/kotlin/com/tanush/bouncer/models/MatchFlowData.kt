package com.tanush.bouncer.models

data class MatchFlowData(
    val drsReviews: List<DrsReviewEvent> = emptyList(),
    val powerplays: List<PowerplayEvent> = emptyList(),
    val impactSubs: List<ImpactSubEvent> = emptyList(),
    val milestones: List<String> = emptyList(),
)

data class DrsReviewEvent(
    val over: String,
    val teamName: String,
    val reviewType: String,   // "Batting" or "Bowling"
    val decision: String,     // "LBW", "Wide", etc.
    val umpireName: String,
    val batterName: String,
    val isUpheld: Boolean,
)

data class PowerplayEvent(
    val phase: Int,
    val overs: String,
    val runs: Int,
    val wickets: Int,
)

data class ImpactSubEvent(
    val team: String,
    val playerIn: String,
    val playerOut: String,
    val over: String,
)
