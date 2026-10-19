package com.tanush.bouncer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.displayOvers

/**
 * Fall of wicket data
 */
data class FallOfWicket(
    val wicketNumber: Int,
    val score: Int,
    val overs: Float,
    val batsmanName: String,
    val batsmanRuns: Int,
    val batsmanBalls: Int,
    val bowlerName: String,
    val dismissalType: DismissalType,
    val partnershipRuns: Int = 0
)

/**
 * Types of dismissals
 */
enum class DismissalType(val label: String, val shortLabel: String) {
    BOWLED("Bowled", "b"),
    CAUGHT("Caught", "c"),
    LBW("LBW", "lbw"),
    RUN_OUT("Run Out", "run out"),
    STUMPED("Stumped", "st"),
    HIT_WICKET("Hit Wicket", "hit wkt"),
    CAUGHT_BEHIND("Caught Behind", "c †"),
    CAUGHT_AND_BOWLED("Caught & Bowled", "c&b")
}

/**
 * One innings' fall of wickets, as the scorecard prints it: score-wicket, batter, over.
 *
 * Rows in one container. This was a red-shadowed card with a trend-down icon and a tracked-caps
 * title, a gradient-filled timeline chart, a horizontally scrolling card per wicket, a summary of
 * wicket counts by dismissal type and a "Collapse:" banner — five views of eight numbers.
 *
 * @param teamColor the side's ink (`rememberTeamInk`), used only for its name.
 */
@Composable
fun FallOfWicketsCard(
    wickets: List<FallOfWicket>,
    teamName: String,
    teamColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.fow_title),
                style = typography.titleMedium,
                color = colors.onSurface,
            )
            Text(text = teamName, style = typography.bodySmall, color = teamColor)
            Spacer(Modifier.height(spacing.sm))
            wickets.forEachIndexed { index, wicket ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                WicketRow(wicket)
            }
        }
    }
}

@Composable
private fun WicketRow(wicket: FallOfWicket) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${wicket.score}-${wicket.wicketNumber}",
            style = typography.numeric,
            color = colors.onSurface,
            modifier = Modifier.width(SCORE_COLUMN),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wicket.batsmanName,
                style = typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val dismissal = listOf(
                "${wicket.batsmanRuns} (${wicket.batsmanBalls})",
                listOf(wicket.dismissalType.shortLabel, wicket.bowlerName).filter { it.isNotBlank() }.joinToString(" "),
            ).filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                text = dismissal,
                style = typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = stringResource(R.string.fow_overs, displayOvers(wicket.overs.toString())),
            style = typography.numericSmall,
            color = colors.textSecondary,
        )
    }
}

private val SCORE_COLUMN = 64.dp
