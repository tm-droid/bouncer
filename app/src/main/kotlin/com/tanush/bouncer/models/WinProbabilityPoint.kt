package com.tanush.bouncer.models

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

@Serializable
@Immutable
data class WinProbabilityPoint(
    val over: Float,
    val team1Probability: Float,
    val team2Probability: Float,
    val keyEvent: String? = null,
    /** What moved this reading, largest first. Empty when the model read no context. */
    val factors: List<WinProbFactor> = emptyList(),
)
