package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.displayOvers

/** How a finished match marks a side. Live and upcoming rows are [Undecided]. */
enum class RowResult { Undecided, Won, Lost }

/**
 * A single team row: crest + code + score right-aligned.
 *
 * The code is drawn in the team's colour when the registry knows the side (`rememberTeamInk`), so a
 * list reads by team at a glance. Scores stay neutral: a column of numbers in a different hue per
 * row competes with itself. The batting side is marked by weight and a larger score. In a finished
 * match the loser's code and score step back to the secondary text colour — Cricinfo's treatment —
 * so the winner reads without reading the result line.
 *
 * A blank score shows nothing — the `—` placeholder it used to print put two dashes on every upcoming
 * row — except [yetToBat], where the absence is itself the news.
 */
@Composable
fun TeamRowV2(
    teamName: String,
    teamAcronym: String,
    score: String,
    overs: String,
    isBatting: Boolean,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    yetToBat: Boolean = false,
    result: RowResult = RowResult.Undecided,
    crestSize: Dp = CREST_SIZE,
    inkScore: Boolean = false,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val teamInk = rememberTeamInk(teamName)
    val nameInk = if (result == RowResult.Lost) colors.textSecondary else teamInk
    val scoreInk = when {
        result == RowResult.Lost -> colors.textSecondary
        inkScore && isBatting -> teamInk
        else -> colors.onSurface
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(teamName = teamName, logoUrl = logoUrl, size = crestSize, acronym = teamAcronym.ifBlank { null })
        Spacer(Modifier.width(CREST_GAP))
        Text(
            text = teamAcronym.ifBlank { teamName },
            style = typography.titleMedium,
            fontWeight = if (isBatting || result == RowResult.Won) FontWeight.Bold else FontWeight.SemiBold,
            color = nameInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TeamScoreCell(
            score = score,
            overs = overs,
            emphasised = isBatting || result == RowResult.Won,
            yetToBat = yetToBat,
            ink = scoreInk,
        )
    }
}

/** Score plus overs, right-aligned. Split out to keep [TeamRowV2] under the complexity budget. */
@Composable
private fun TeamScoreCell(score: String, overs: String, emphasised: Boolean, yetToBat: Boolean, ink: Color) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    if (score.isBlank()) {
        if (yetToBat) {
            Text(
                text = stringResource(R.string.match_yet_to_bat),
                style = typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedScoreText(
            text = score,
            style = if (emphasised) typography.scoreLarge else typography.scoreMedium,
            fontWeight = if (emphasised) FontWeight.Bold else FontWeight.SemiBold,
            color = ink,
        )
        // "0.0" is the scraper's not-started sentinel, not a real over count.
        if (overs.isNotBlank() && overs != NOT_STARTED_OVERS) {
            Text(
                text = "(${displayOvers(overs)})",
                style = typography.numericSmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

private val CREST_SIZE = 24.dp
private val CREST_GAP = 10.dp
private const val NOT_STARTED_OVERS = "0.0"
