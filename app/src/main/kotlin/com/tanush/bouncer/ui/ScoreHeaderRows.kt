package com.tanush.bouncer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.RowResult
import com.tanush.bouncer.ui.components.v2.rowResultFor
import com.tanush.bouncer.ui.navigation.BouncerScreen
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

private val SERIES_LINK_TOUCH_PADDING = 8.dp

// The fixed score header's result plate and context line.

/**
 * The result, as the last thing seen of a match. A neutral plate with the trophy in the winner's
 * colour: the plate used to be win green, which said "good news" whichever side the reader backs.
 */
@Composable
internal fun MatchResultBanner(match: CricketMatch) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val winner = match.teams.firstOrNull { rowResultFor(match, it.name) == RowResult.Won }
    val trophyInk = if (winner != null) rememberTeamInk(winner.name) else colors.textSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(BouncerThemeV2.shapes.small)
            .background(colors.surfaceVariant)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = trophyInk,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = match.currentStatus,
            style = typography.bodyMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Context line. The teams are already the top bar's title, so this names what the top bar cannot:
 * the series (a link to it, when the match knows its series id) and which match of it this is.
 */
@Composable
internal fun MatchContextLine(
    match: CricketMatch,
    onSeriesClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val seriesLink = onSeriesClick?.takeIf { match.seriesId.isNotBlank() && match.seriesName.isNotBlank() }
    if (seriesLink == null) {
        Text(
            text = "${match.matchTitle} • ${match.matchType}",
            style = typography.bodySmall,
            color = colors.textSecondary,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier) {
        Row(
            // Padded inside the click area: the text alone was a 16dp-tall target.
            modifier = Modifier
                .clickable(role = Role.Button) { seriesLink(match.seriesId) }
                .padding(vertical = SERIES_LINK_TOUCH_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = match.seriesName,
                style = typography.bodySmall,
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = listOf(match.matchDesc, match.matchType).filter { it.isNotBlank() }.joinToString(" • "),
            style = typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

internal fun seriesRoute(seriesId: String): String = BouncerScreen.SeriesDetail.createRoute(seriesId)

/** The team page route, or null for a side with no name. */
internal fun teamRoute(teamName: String): String? =
    teamName.takeIf { it.isNotBlank() }?.let { BouncerScreen.TeamDetail.createRoute(it) }
