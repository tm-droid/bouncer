package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerColors
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.BouncerTheme

/**
 * Displays recent deliveries as small colour-coded circles.
 *
 * Colour mapping:
 * - W  → [BouncerColors.WicketRed] with white "W"
 * - 6  → [BouncerColors.SixGold] with white "6"
 * - 4  → [BouncerColors.BoundaryGreen] with white "4"
 * - 0  → [BouncerColors.DotBallGray] with faded "0"
 * - Wd → [BouncerColors.WideBallAmber] with "Wd"
 * - Nb → [BouncerColors.NoBallOrange] with "Nb"
 * - Others → surfaceVariant with the value
 */
@Composable
fun RecentBallsRow(
    balls: List<String>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = BouncerDimens.xs),
        horizontalArrangement = Arrangement.spacedBy(BouncerDimens.xs)
    ) {
        items(balls, key = { "${it}_${balls.indexOf(it)}" }) { ball ->
            BallCircle(ball = ball)
        }
    }
}

@Composable
private fun BallCircle(ball: String) {
    val (bgColor, textColor, displayText) = resolveBallStyle(ball)

    Box(
        modifier = Modifier
            .size(BouncerDimens.recentBallSize)
            .clip(CircleShape)
            .background(bgColor),
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
 * Returns a Triple of (background, text colour, display text) for a given ball string.
 */
@Composable
private fun resolveBallStyle(ball: String): Triple<Color, Color, String> {
    val trimmed = ball.trim()
    return when {
        trimmed.equals("W", ignoreCase = true) ->
            Triple(BouncerColors.WicketRed, Color.White, "W")

        trimmed == "6" ->
            Triple(BouncerColors.SixGold, Color.White, "6")

        trimmed == "4" ->
            Triple(BouncerColors.BoundaryGreen, Color.White, "4")

        trimmed == "0" ->
            Triple(BouncerColors.DotBallGray, Color.White.copy(alpha = 0.7f), "0")

        trimmed.equals("Wd", ignoreCase = true) || trimmed.equals("WD", ignoreCase = true) ->
            Triple(BouncerColors.WideBallAmber, Color.White, "Wd")

        trimmed.equals("Nb", ignoreCase = true) || trimmed.equals("NB", ignoreCase = true) ->
            Triple(BouncerColors.NoBallOrange, Color.White, "Nb")

        else ->
            Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                trimmed
            )
    }
}

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
