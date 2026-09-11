package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.displayOvers

/**
 * A single team row: logo (20dp) + name + score right-aligned.
 *
 * Ink is neutral for both teams. The batting side is marked by weight and by a larger score, never by
 * colour — painting each team's score in its brand colour turned every list into a different hue per
 * row and made the numbers, which are the content, compete with each other.
 *
 * [reserveLogoSlot] keeps a logo-less team's name in line with its opponent's when only one side has a
 * crest. A blank score shows nothing — the `—` placeholder it used to print put two dashes on every
 * upcoming row — except [yetToBat], where the absence is itself the news.
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
    reserveLogoSlot: Boolean = false,
    yetToBat: Boolean = false,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogoSlot(logoUrl = logoUrl, teamName = teamName, reserve = reserveLogoSlot)
        Text(
            text = teamAcronym.ifBlank { teamName },
            style = typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TeamScoreCell(score = score, overs = overs, isBatting = isBatting, yetToBat = yetToBat)
    }
}

@Composable
private fun TeamLogoSlot(logoUrl: String?, teamName: String, reserve: Boolean) {
    if (logoUrl != null) {
        CoilImage(
            model = logoUrl,
            contentDescription = teamName,
            modifier = Modifier.size(LOGO_SIZE),
        )
        Spacer(Modifier.width(LOGO_GAP))
    } else if (reserve) {
        Spacer(Modifier.width(LOGO_SIZE + LOGO_GAP))
    }
}

/** Score plus overs, right-aligned. Split out to keep [TeamRowV2] under the complexity budget. */
@Composable
private fun TeamScoreCell(score: String, overs: String, isBatting: Boolean, yetToBat: Boolean) {
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
            style = if (isBatting) typography.scoreLarge else typography.scoreMedium,
            fontWeight = if (isBatting) FontWeight.Bold else FontWeight.SemiBold,
            color = colors.onSurface,
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

private val LOGO_SIZE = 20.dp
private val LOGO_GAP = 8.dp
private const val NOT_STARTED_OVERS = "0.0"
