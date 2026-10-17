package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import com.tanush.bouncer.ui.theme.rememberTeamColors
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

data class PartnershipEntry(
    val batter1Name: String,
    val batter1Runs: Int,
    val batter1Balls: Int,
    val batter2Name: String,
    val batter2Runs: Int,
    val batter2Balls: Int,
    val totalRuns: Int,
    val totalBalls: Int,
    val runRate: Double,
    val wicketNumber: Int,
    val isActive: Boolean = false,
)

/**
 * Each partnership as a row: the wicket it fell at, both batters with their share, the stand, and a
 * bar scaled to the innings' best — split between the two batters in the batting side's colour.
 *
 * Rows on the scorecard's surface, not a card of its own: it sat in a gradient-tiled card with a
 * staggered slide-in, a gold "best partnership" panel, and the two batters in status blue and
 * green, which named nothing.
 */
@Composable
fun PartnershipBreakdownCard(
    teamName: String,
    partnerships: List<PartnershipEntry>,
    modifier: Modifier = Modifier,
) {
    if (partnerships.isEmpty()) return
    val colors = BouncerThemeV2.colors
    val known = TeamColorRegistry.isKnown(teamName)
    val lead = if (known) rememberTeamColors(teamName).primary else colors.onSurface
    val maxRuns = partnerships.maxOf { it.totalRuns }
    Column(modifier = modifier.fillMaxWidth()) {
        partnerships.forEach { p ->
            PartnershipRow(p, maxRuns = maxRuns, lead = lead, isBest = p.totalRuns == maxRuns && maxRuns > 0)
            HorizontalDivider(color = colors.divider)
        }
    }
}

/**
 * Bar width as a fraction of the row: always finite, always within `0f..1f`.
 *
 * `maxRuns` is derived from scraped strings via `toIntOrNull() ?: 0`, so a partnership list none of
 * whose rows parsed yields a max of 0 and an unguarded `totalRuns / maxRuns` of `0f/0` = NaN.
 * `Modifier.fillMaxWidth(fraction = NaN)` does not degrade — it throws
 * `IllegalArgumentException: Cannot round NaN value`, taking down the Scorecard tab.
 */
internal fun partnershipBarFraction(totalRuns: Int, maxRuns: Int, progress: Float): Float =
    if (maxRuns <= 0) 0f else (totalRuns.toFloat() / maxRuns * progress).coerceIn(0f, 1f)

@Composable
private fun PartnershipRow(partnership: PartnershipEntry, maxRuns: Int, lead: Color, isBest: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ordinalWicket(partnership.wicketNumber),
                style = typography.numericSmall,
                color = colors.textSecondary,
                modifier = Modifier.width(36.dp),
            )
            Text(
                text = "${partnership.batter1Name} ${partnership.batter1Runs}  &  ${partnership.batter2Name} ${partnership.batter2Runs}",
                style = typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = partnership.totalRuns.toString(),
                style = typography.numeric,
                fontWeight = if (isBest) FontWeight.Bold else FontWeight.SemiBold,
                color = colors.onSurface,
            )
            if (partnership.totalBalls > 0) {
                Text(
                    text = " (${partnership.totalBalls})",
                    style = typography.numericSmall,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val share = partnership.batter1Runs.toFloat() /
            (partnership.batter1Runs + partnership.batter2Runs).coerceAtLeast(1)
        // Fractions, never weights: either batter can have 0, and a zero weight throws (ANDROID-4K).
        Box(
            modifier = Modifier
                .padding(start = 36.dp)
                .fillMaxWidth(partnershipBarFraction(partnership.totalRuns, maxRuns, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(lead.copy(alpha = SECOND_BATTER_ALPHA)),
        ) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).fillMaxHeight().background(lead))
        }
    }
}

/** "1st", "2nd", "3rd", "10th". */
internal fun ordinalWicket(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

private const val SECOND_BATTER_ALPHA = 0.35f
