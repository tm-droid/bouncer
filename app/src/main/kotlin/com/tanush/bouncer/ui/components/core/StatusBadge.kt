package com.tanush.bouncer.ui.components.core

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BorderWidth
import com.tanush.bouncer.ui.theme.ChartCyan
import com.tanush.bouncer.ui.theme.CornerRadius
import com.tanush.bouncer.ui.theme.LiveBlue
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.TextSecondary
import com.tanush.bouncer.ui.theme.WarningOrange

enum class MatchStatusType {
    LIVE,
    UPCOMING,
    COMPLETED,
    DELAYED,
    SCHEDULED
}

fun resolveMatchStatusType(status: String, isLive: Boolean): MatchStatusType {
    if (isLive) return MatchStatusType.LIVE

    val normalizedStatus = status.lowercase()
    return when {
        normalizedStatus.contains("completed") || normalizedStatus.contains("won") -> MatchStatusType.COMPLETED
        normalizedStatus.contains("upcoming") -> MatchStatusType.UPCOMING
        normalizedStatus.contains("delayed") -> MatchStatusType.DELAYED
        else -> MatchStatusType.SCHEDULED
    }
}

fun statusColorFor(statusType: MatchStatusType): Color {
    return when (statusType) {
        MatchStatusType.LIVE -> SuccessGreen
        MatchStatusType.UPCOMING -> LiveBlue
        MatchStatusType.COMPLETED -> ChartCyan
        MatchStatusType.DELAYED -> WarningOrange
        MatchStatusType.SCHEDULED -> TextSecondary
    }
}

private data class StatusStyle(
    val label: String,
    val color: Color
)

@Composable
fun StatusBadge(
    statusType: MatchStatusType,
    modifier: Modifier = Modifier
) {
    val statusColor = statusColorFor(statusType)
    val statusStyle = when (statusType) {
        MatchStatusType.LIVE -> StatusStyle(label = "Live", color = statusColor)
        MatchStatusType.UPCOMING -> StatusStyle(label = "Upcoming", color = statusColor)
        MatchStatusType.COMPLETED -> StatusStyle(label = "Completed", color = statusColor)
        MatchStatusType.DELAYED -> StatusStyle(label = "Delayed", color = statusColor)
        MatchStatusType.SCHEDULED -> StatusStyle(label = "Scheduled", color = statusColor)
    }

    val shouldPulse = statusType == MatchStatusType.LIVE
    val pulseTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseAlpha"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.pill),
        color = statusStyle.color.copy(alpha = 0.10f),
        border = BorderStroke(
            BorderWidth.hairline,
            statusStyle.color.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (shouldPulse) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = pulseAlpha }
                        .background(SuccessGreen, CircleShape)
                )
            }

            Text(
                text = statusStyle.label,
                color = statusStyle.color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
