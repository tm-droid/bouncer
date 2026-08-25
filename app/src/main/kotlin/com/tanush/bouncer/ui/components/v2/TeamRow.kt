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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * A single team row: logo (24dp) + name + score right-aligned.
 *
 * Ink is neutral for both teams. The batting side is marked by weight (ExtraBold vs Bold) and by
 * a larger score, never by colour — painting each team's score in its brand colour turned every
 * list into a different hue per row and made the numbers, which are the content, compete with
 * each other. Team colour still identifies teams where it is a *shape* (the win-probability bar).
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
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val ink = colors.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logoUrl != null) {
            CoilImage(
                model = logoUrl,
                contentDescription = teamName,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = teamAcronym.ifBlank { teamName },
            style = typography.titleMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        TeamScoreCell(score = score, overs = overs, isBatting = isBatting, ink = ink)
    }
}

/** Score plus overs, right-aligned. Split out to keep [TeamRowV2] under the complexity budget. */
@Composable
private fun TeamScoreCell(score: String, overs: String, isBatting: Boolean, ink: Color) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (score.isBlank()) {
            Text(
                text = "—",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
            )
        } else {
            AnimatedScoreText(
                text = score,
                style = if (isBatting) typography.scoreLarge else typography.scoreMedium,
                fontWeight = if (isBatting) FontWeight.ExtraBold else FontWeight.Bold,
                color = ink,
            )
        }
        // "0.0" is the scraper's not-started sentinel, not a real over count.
        if (overs.isNotBlank() && overs != "0.0") {
            Text(
                text = "($overs)",
                style = typography.numericSmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}
