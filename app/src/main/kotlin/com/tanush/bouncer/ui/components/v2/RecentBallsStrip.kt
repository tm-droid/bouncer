package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Colored dots/pills for recent deliveries.
 * dot=gray, 4=green, 6=purple, W=red, wd/nb=yellow.
 */
@Composable
fun RecentBallsStripV2(
    balls: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        balls.takeLast(12).forEach { ball ->
            val (bg, text) = ballStyle(ball, colors)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ball.take(2),
                    style = typography.labelSmall,
                    color = text,
                )
            }
        }
    }
}

private fun ballStyle(ball: String, colors: BouncerColorsToken): Pair<Color, Color> {
    val b = ball.trim().uppercase()
    return when {
        b.contains("W") && !b.contains("WD") -> colors.live.copy(alpha = 0.85f) to Color.White
        b == "4" -> Color(0xFF16A34A).copy(alpha = 0.85f) to Color.White
        b == "6" -> Color(0xFF7C3AED).copy(alpha = 0.85f) to Color.White
        b.contains("WD") || b.contains("NB") || b.contains("LB") || b.contains("B") ->
            Color(0xFFF59E0B).copy(alpha = 0.85f) to Color.White
        b == "0" || b == "." -> colors.textSecondary.copy(alpha = 0.3f) to colors.onSurface
        else -> colors.primary.copy(alpha = 0.2f) to colors.onSurface
    }
}

// Type alias for readability in helper
private typealias BouncerColorsToken = com.tanush.bouncer.ui.theme.v2.BouncerColorsV2
