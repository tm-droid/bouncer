package com.tanush.bouncer.models

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Serializable
@Immutable
data class ScrapedBowler(
    val name: String,
    val overs: String,
    val maidens: String,
    val runs: String,
    val wickets: String,
    val economy: String
)

@Serializable
@Immutable
data class ScrapedBatsman(
    val name: String,
    val runs: String,
    val balls: String,
    val fours: String,
    val sixes: String,
    val strikeRate: String,
    val isStriker: Boolean = false,
    val howOut: String? = null,
    val faceImageId: String? = null
)

@Serializable
@Immutable
data class PowerPlayPhaseData(
    val phaseName: String,
    val overs: String,
    val runs: Int,
    val wickets: Int,
    val runRate: Double,
    val boundaries: Int,
    val sixes: Int,
    val dots: Int,
    val isActive: Boolean = false
)

@Immutable
data class MomentumEventData(
    val over: String,
    val title: String,
    val description: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)
