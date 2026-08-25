package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The moment a batter reaches a milestone, as a two-line banner over the bottom of match detail.
 *
 * ## What this replaced
 *
 * A 488-line full-bleed celebration: a `Canvas` drawing twelve rotating sunburst rays behind the
 * score, three concurrent `infiniteRepeatable` loops (sparkle rotation, pulse, gold glow), the
 * milestone name announced by emoji (🎯 💯 🏆 👑 🔥) at 32sp, gold star glyphs flanking a giant
 * score, a "RUN COMPOSITION" stacked bar, and a per-milestone colour scheme that repainted the
 * whole panel. With no height constraint it grew to fill nearly the entire screen and covered the
 * match the instant it was opened — the celebration hid the thing being celebrated.
 *
 * A milestone is worth marking. It is not worth a screen. Cricbuzz and Cricinfo both mark a fifty
 * with a single line of text, and that is the right size for something that appears unbidden and
 * dismisses itself after [MILESTONE_BANNER_MS]. The milestone colour survives as one small dot —
 * enough to make a hundred feel different from a fifty without repainting the panel.
 */

data class BatsmanMilestoneInfo(
    val playerName: String,
    val teamName: String,
    val playerImageUrl: String? = null,
    val currentRuns: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Float,
    val milestone: MilestoneType,
    val isNewMilestone: Boolean = false,
    val careerHighScore: Int? = null,
    val matchesWithMilestone: Int = 0
)

/**
 * Milestones are named in words, not emoji. An emoji is not a label — it renders differently on
 * every platform, reads as nothing to a screen reader, and is the single most common tell of
 * generated UI.
 */
enum class MilestoneType(val displayName: String, val runs: Int) {
    FIFTY("Half century", 50),
    HUNDRED("Century", 100),
    DOUBLE_HUNDRED("Double century", 200),
    TRIPLE_HUNDRED("Triple century", 300),
    QUADRUPLE_HUNDRED("Quadruple century", 400),
}

@Composable
fun BatsmanMilestoneCard(
    milestoneInfo: BatsmanMilestoneInfo,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val milestoneColor = milestoneDotColor(milestoneInfo.milestone)

    val spoken = "${milestoneInfo.milestone.displayName} for ${milestoneInfo.playerName}, " +
        "${milestoneInfo.currentRuns} off ${milestoneInfo.balls} balls"

    BouncerCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(milestoneColor, CircleShape),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = milestoneInfo.milestone.displayName.uppercase(),
                    style = typography.overline,
                    color = colors.textSecondary,
                )
                Text(
                    text = milestoneInfo.playerName,
                    style = typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${milestoneInfo.currentRuns} (${milestoneInfo.balls})",
                    style = typography.scoreMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "${milestoneInfo.fours}x4 · ${milestoneInfo.sixes}x6",
                    style = typography.numericSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** The one spot of colour: enough to tell a hundred from a fifty at a glance. */
@Composable
private fun milestoneDotColor(milestone: MilestoneType): Color {
    val colors = BouncerThemeV2.colors
    return when (milestone) {
        MilestoneType.FIFTY -> colors.cricket.four
        MilestoneType.HUNDRED -> colors.primary
        MilestoneType.DOUBLE_HUNDRED -> colors.cricket.six
        MilestoneType.TRIPLE_HUNDRED,
        MilestoneType.QUADRUPLE_HUNDRED -> colors.cricket.milestone
    }
}
