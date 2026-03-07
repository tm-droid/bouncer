package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerTheme

/**
 * Displays recent deliveries as small colour-coded circles.
 *
 * Colour mapping (using BouncerDesignSystem.colors.ball* tokens):
 * - W  → ballWicket (red) with subtle red glow + white "W"
 * - 6  → ballSix (gold) with subtle purple glow + white "6"
 * - 4  → ballBoundary (green) with subtle green glow + white "4"
 * - 0  → ballDot (gray) + faded "0"
 * - Wd → ballWide (yellow) + white "Wd"
 * - Nb → ballNoBall (orange) + white "Nb"
 * - Others → surfaceVariant with the value
 */
@Composable
fun RecentBallsRow(
    balls: List<String>,
    modifier: Modifier = Modifier
) {
    val dimens = BouncerDesignSystem.dimens
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = dimens.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)
    ) {
        items(balls, key = { "${it}_${balls.indexOf(it)}" }) { ball ->
            BallCircle(ball = ball)
        }
    }
}

@Composable
private fun BallCircle(ball: String) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val (bgColor, textColor, displayText, glowColor) = resolveBallStyle(ball)
    val ballSize = dimens.ballIndicatorSize

    Box(
        modifier = Modifier
            .size(ballSize)
            .then(
                if (glowColor != null) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2, size.height / 2),
                                radius = size.width * 0.9f
                            )
                        )
                    }
                } else Modifier
            )
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (glowColor != null) {
                    Modifier.border(1.dp, glowColor.copy(alpha = 0.5f), CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = textColor,
            fontSize = if (displayText.length > 1) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Returns a BallStyle of (background, text colour, display text, glow color) for a given ball string.
 * Uses BouncerDesignSystem.colors.ball* tokens for consistent color coding.
 */
@Composable
private fun resolveBallStyle(ball: String): BallStyle {
    val colors = BouncerDesignSystem.colors
    val trimmed = ball.trim()
    return when {
        trimmed.equals("W", ignoreCase = true) ->
            BallStyle(colors.ballWicket, Color.White, "W", colors.ballWicket)

        trimmed == "6" ->
            BallStyle(colors.ballSix, Color.White, "6", colors.ballSix)

        trimmed == "4" ->
            BallStyle(colors.ballBoundary, Color.White, "4", colors.ballBoundary)

        trimmed == "0" ->
            BallStyle(colors.ballDot, Color.White.copy(alpha = 0.7f), "0", null)

        trimmed.equals("Wd", ignoreCase = true) || trimmed.equals("WD", ignoreCase = true) ->
            BallStyle(colors.ballWide, Color.White, "Wd", null)

        trimmed.equals("Nb", ignoreCase = true) || trimmed.equals("NB", ignoreCase = true) ->
            BallStyle(colors.ballNoBall, Color.White, "Nb", null)

        else ->
            BallStyle(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                trimmed,
                null
            )
    }
}

/**
 * Data holder for ball circle styling
 */
private data class BallStyle(
    val bgColor: Color,
    val textColor: Color,
    val displayText: String,
    val glowColor: Color?
)

// ── Preview ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun RecentBallsRowPreview() {
    BouncerTheme(dynamicColor = false) {
        RecentBallsRow(
            balls = listOf("0", "1", "4", "W", "6", "2", "Wd", "Nb", "0", "4"),
            modifier = Modifier
        )
    }
}
