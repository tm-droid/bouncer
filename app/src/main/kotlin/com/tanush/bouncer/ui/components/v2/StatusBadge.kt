package com.tanush.bouncer.ui.components.v2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Status badge pills: LIVE (red pulse), UPCOMING (gray), COMPLETED (green).
 */

enum class MatchStatusType { Live, Upcoming, Completed, Unknown }

/** Robustly determine match status from all available signals. */
fun determineMatchStatus(match: CricketMatch): MatchStatusType {
    if (match.isLive) return MatchStatusType.Live
    val statusLower = match.status.lowercase()
    val currentLower = match.currentStatus.lowercase()
    val infoLower = match.info.lowercase().trim()

    // Check Cricbuzz state field for in-progress indicators (multi-day matches at breaks)
    if (infoLower in listOf("in progress", "stump", "stumps", "tea", "lunch",
            "dinner", "innings break", "toss", "rain delay", "bad light")) {
        return MatchStatusType.Live
    }

    // Completed indicators
    if (currentLower.contains("won") || currentLower.contains("draw") ||
        currentLower.contains("no result") || currentLower.contains("tied") ||
        statusLower.contains("result") || statusLower.contains("complete") ||
        infoLower == "complete"
    ) return MatchStatusType.Completed
    // Upcoming indicators
    if (statusLower.contains("upcoming") || statusLower.contains("scheduled") ||
        statusLower.contains("preview") || infoLower == "preview"
    ) return MatchStatusType.Upcoming
    // If the match has scores, it's not upcoming
    val hasScores = match.teams.any { it.score.isNotBlank() }
    if (hasScores) return MatchStatusType.Completed
    return MatchStatusType.Upcoming
}

@Composable
fun StatusBadgeV2(
    statusType: MatchStatusType,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val shapes = BouncerThemeV2.shapes

    val (dotColor, text) = when (statusType) {
        MatchStatusType.Live -> colors.live to (label ?: "LIVE")
        MatchStatusType.Upcoming -> colors.scheduled to (label ?: "UPCOMING")
        MatchStatusType.Completed -> colors.win to (label ?: "COMPLETED")
        MatchStatusType.Unknown -> colors.textSecondary to (label ?: "")
    }

    Row(
        modifier = modifier
            .background(dotColor.copy(alpha = 0.12f), shapes.pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (statusType == MatchStatusType.Live) {
            PulsingDot(color = dotColor)
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            text = text,
            style = typography.labelSmall,
            color = dotColor,
        )
    }
}

@Composable
private fun PulsingDot(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier
            .size(6.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}
