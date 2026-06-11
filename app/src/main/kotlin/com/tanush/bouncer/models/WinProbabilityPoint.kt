package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable

@Immutable
data class WinProbabilityPoint(
    val over: Float,
    val team1Probability: Float,
    val team2Probability: Float,
    val keyEvent: String? = null,
)
