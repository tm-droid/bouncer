package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.strikeRow
import com.tanush.bouncer.ui.theme.BouncerDesign
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.ui.theme.GlassCardShape
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.theme.etchedText
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Current batsman partnership strip — shows the two batsmen currently
 * at the crease with their live scores.
 *
 * Design: Glassmorphic card with two player sections divided in the center.
 */
@Composable
fun CurrentPlayersCard(
    batsmen: List<CricketMatch.BatsmanScore>,
    teamCode: String = "",
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    if (batsmen.isEmpty()) return

    val dimens = BouncerDesign.dimens
    val teamColor = TeamPrimaryColors.getTeamColor(teamCode)

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.playersStripHeight),
        shape = GlassCardShape,
        contentPadding = PaddingValues(0.dp), // Padding handled by children
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            batsmen.take(2).forEachIndexed { index, batsman ->
                if (index > 0) {
                    // Thin vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(dimens.spacing.xl)
                            .background(hierarchy.secondary.primary.copy(alpha = 0.2f))
                    )
                }
                
                val isOnStrike = index == 0 // Assuming first is on strike based on existing logic
                
                PlayerSection(
                    name = batsman.name,
                    score = "${batsman.runs}(${batsman.balls})",
                    isOnStrike = isOnStrike,
                    teamColor = teamColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (isOnStrike) Modifier.strikeRow(teamColor) else Modifier),
                )
            }
        }
    }
}

@Composable
private fun PlayerSection(
    name: String,
    score: String,
    isOnStrike: Boolean,
    teamColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesign.typography
    val dimens = BouncerDesign.dimens

    Row(
        modifier = modifier.padding(horizontal = dimens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Player name and score stacked
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.uppercase(),
                style = typography.labelSmall,
                color = if (isOnStrike) colors.textPrimary else colors.textSecondary,
                fontWeight = if (isOnStrike) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            
            Text(
                text = score,
                style = typography.scoreCompact,
                color = if (isOnStrike) hierarchy.secondary.primary else colors.textMuted,
                fontWeight = FontWeight.ExtraBold,
                modifier = if (isOnStrike) Modifier.etchedText() else Modifier
            )
        }
    }
}

/**
 * Pulsing live dot — small animated circle indicating active state.
 */
@Composable
private fun LiveDot(
    animated: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "liveDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (animated) 1f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )

    Box(
        modifier = modifier
            .size(BouncerDesign.dimens.liveDotSize)
            .alpha(alpha)
            .clip(CircleShape)
            .background(BouncerDesign.colors.statusLive),
    )
}

// ── Preview ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun CurrentPlayersCardPreview() {
    val colors = BouncerDesignSystem.colors
    BouncerTheme(darkTheme = true, dynamicColor = false) {
        CurrentPlayersCard(
            batsmen = listOf(
                CricketMatch.BatsmanScore("Pat Cummins", 0, 1, 0, 0, 0.0),
                CricketMatch.BatsmanScore("Cameron Green", 8, 19, 1, 0, 42.10),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
