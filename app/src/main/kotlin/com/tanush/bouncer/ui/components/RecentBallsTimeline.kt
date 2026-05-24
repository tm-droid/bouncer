package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.DotBall
import com.tanush.bouncer.ui.theme.GlassBorder
import com.tanush.bouncer.ui.theme.GlassSurface

private const val BALLS_PER_OVER = 6

/**
 * RecentBallsTimeline - Horizontal ball-by-ball display
 * Shows recent deliveries in a timeline format
 */
@Composable
fun RecentBallsTimeline(
    balls: List<String>,
    modifier: Modifier = Modifier,
    showOverMarkers: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RECENT",
            color = colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        balls.forEachIndexed { index, ball ->
            if (showOverMarkers && index > 0 && index % BALLS_PER_OVER == 0) {
                OverMarker(accentColor = hierarchy.secondary.primary)
            }

            TimelineBall(ball = ball, isLatest = index == balls.lastIndex)
        }
    }
}

@Composable
private fun OverMarker(accentColor: Color) {
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(24.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}

private data class TimelineBallStyle(val background: Color, val text: Color)

@Composable
private fun timelineBallStyle(ball: String): TimelineBallStyle {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    return when {
        ball.contains("W", ignoreCase = true) -> TimelineBallStyle(colors.statusLive, Color.White)
        ball == "6" -> TimelineBallStyle(hierarchy.secondary.primary, colors.backgroundBase)
        ball == "4" -> TimelineBallStyle(colors.statusInfo, colors.backgroundBase)
        ball == "0" || ball == "." -> TimelineBallStyle(DotBall, colors.textMuted)
        ball.contains("WD") || ball.contains("NB") -> TimelineBallStyle(colors.statusWarning, colors.backgroundBase)
        else -> TimelineBallStyle(GlassSurface, colors.textPrimary)
    }
}

private fun timelineBallText(ball: String): String = when {
    ball == "0" || ball == "." -> "•"
    ball.length > 2 -> ball.take(2)
    else -> ball
}

@Composable
private fun TimelineBall(ball: String, isLatest: Boolean = false) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val style = timelineBallStyle(ball)
    val pulse = rememberPulse(initial = 1f, target = 1.15f, durationMillis = 400, label = "latestBall")

    Box(
        modifier = Modifier
            .size(if (isLatest) 32.dp else 28.dp)
            .graphicsLayer {
                if (isLatest) {
                    scaleX = pulse
                    scaleY = pulse
                }
            }
            .shadow(
                elevation = if (isLatest) 8.dp else 2.dp,
                shape = CircleShape,
                spotColor = style.background.copy(alpha = 0.5f)
            )
            .background(style.background, CircleShape)
            .border(
                if (isLatest) 2.dp else 0.5.dp,
                if (isLatest) hierarchy.secondary.primary else GlassBorder,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = timelineBallText(ball),
            color = style.text,
            fontSize = if (isLatest) 12.sp else 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}
