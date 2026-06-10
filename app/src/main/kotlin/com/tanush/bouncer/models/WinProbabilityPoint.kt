package com.tanush.bouncer.models

data class WinProbabilityPoint(
    val over: Float,
    val team1Probability: Float,
    val team2Probability: Float,
    val keyEvent: String? = null,
)
