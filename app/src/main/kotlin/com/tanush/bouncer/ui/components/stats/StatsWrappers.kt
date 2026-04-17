package com.tanush.bouncer.ui.components.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.components.WinProbabilityGauge as BaseWinProbabilityGauge
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Stats-related delegation wrappers.
 */

@Composable
fun WinProbabilityGauge(
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    team1Color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    team2Color: Color = BouncerDesignSystem.colors.statusInfo,
    change: String? = "+4.2%",
    isPositive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    BaseWinProbabilityGauge(
        team1Name = team1Name,
        team2Name = team2Name,
        team1Probability = team1Probability,
        team1Color = team1Color,
        team2Color = team2Color,
        change = change,
        isPositive = isPositive,
        modifier = modifier
    )
}
