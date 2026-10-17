package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import com.tanush.bouncer.ui.theme.rememberSegmentColors
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The one colour pair every chart on a match uses: first team, second team.
 *
 * Each card used to default its own — gold/blue on the run-rate chart, blue/gold on over-by-over,
 * blue/red on win probability — so the same side changed colour from one card to the next, and red
 * (live) stood for a team. Resolved once through `rememberSegmentColors`, so it is the teams'
 * colours when the registry knows both and two neutrals otherwise.
 */
@Composable
internal fun rememberMatchChartColors(match: CricketMatch): Pair<Color, Color> {
    val colors = BouncerThemeV2.colors
    val team1 = match.teams.getOrNull(0)?.name
    val team2 = match.teams.getOrNull(1)?.name
    // Charts label their series in these colours as well as drawing them, so the neutral pair is
    // two opaque inks rather than the segment bar's faint second share.
    if (!TeamColorRegistry.isKnown(team1) || !TeamColorRegistry.isKnown(team2)) {
        return colors.onSurface to colors.textSecondary
    }
    return rememberSegmentColors(team1, team2, colors.surface, CHART_MIN_CONTRAST)
}

/**
 * The chart pair ordered for a series labelled [label1] then [label2] — the worm names its innings
 * by the batting side, which is not always `match.teams[0]`. A label that names neither team keeps
 * the match order.
 */
@Composable
internal fun rememberChartColorsFor(match: CricketMatch, label1: String): Pair<Color, Color> {
    val pair = rememberMatchChartColors(match)
    val second = match.teams.getOrNull(1) ?: return pair
    val label = label1.trim()
    val firstIsSecond = label.isNotEmpty() &&
        (label.equals(second.name, ignoreCase = true) || label.equals(second.acronym, ignoreCase = true))
    return if (firstIsSecond) pair.second to pair.first else pair
}

/** WCAG's floor for graphics and large text: these colours draw lines and label them. */
private const val CHART_MIN_CONTRAST = 3.0f
