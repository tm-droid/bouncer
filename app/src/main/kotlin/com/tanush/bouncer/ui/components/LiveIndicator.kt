package com.tanush.bouncer.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.PillShape
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.ThemeModeV2

/**
 * Pulsing red dot with "LIVE" label for live match indicators.
 * Uses BouncerDesignSystem tokens for consistent styling.
 */
@Composable
fun LiveIndicator(modifier: Modifier = Modifier) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveDotScale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveDotAlpha"
    )

    val liveColor = colors.statusLive

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(dimens.liveDotSize)
                .scale(scale)
                .drawBehind {
                    // Red glow behind the dot
                    drawCircle(
                        color = liveColor.copy(alpha = alpha * 0.3f),
                        radius = size.maxDimension * 0.8f
                    )
                }
                .clip(CircleShape)
                .background(liveColor.copy(alpha = alpha))
        )
        Text(
            text = "LIVE",
            style = typography.matchStatus,
            color = liveColor
        )
    }
}

/**
 * Small pill badge showing match type (e.g. "T20I", "ODI", "TEST").
 */
@Composable
fun MatchTypeBadge(
    matchType: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = matchType.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = BouncerDimens.sm,
                vertical = BouncerDimens.xxs
            )
        )
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "Live — Light", showBackground = true)
@Composable
private fun LiveIndicatorPreviewLight() {
    BouncerThemeV2(themeMode = ThemeModeV2.Light) {
        LiveIndicator(modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Live — Dark", showBackground = true)
@Composable
private fun LiveIndicatorPreviewDark() {
    BouncerThemeV2(themeMode = ThemeModeV2.Dark) {
        LiveIndicator(modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Badges — Light", showBackground = true)
@Composable
private fun MatchTypeBadgePreviewLight() {
    BouncerThemeV2(themeMode = ThemeModeV2.Light) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            MatchTypeBadge(matchType = "T20I")
            MatchTypeBadge(matchType = "ODI")
            MatchTypeBadge(matchType = "TEST")
        }
    }
}
