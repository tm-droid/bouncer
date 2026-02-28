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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerColors
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.ui.theme.PillShape
import com.tanush.bouncer.ui.theme.liveIndicatorColor

/**
 * Pulsing red dot with "LIVE" label for live match indicators.
 */
@Composable
fun LiveIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BouncerDimens.animSlow),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveDotAlpha"
    )

    val liveColor = liveIndicatorColor()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BouncerDimens.xs)
    ) {
        Box(
            modifier = Modifier
                .size(BouncerDimens.liveDotSize)
                .alpha(alpha)
                .clip(CircleShape)
                .background(liveColor)
        )
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
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

@Preview(showBackground = true)
@Composable
private fun LiveIndicatorPreview() {
    BouncerTheme(dynamicColor = false) {
        LiveIndicator(modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun MatchTypeBadgePreview() {
    BouncerTheme(dynamicColor = false) {
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
