package com.tanush.bouncer.ui

import com.tanush.bouncer.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.AnimatedScoreText
import com.tanush.bouncer.ui.components.v2.RowResult
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.components.v2.rowResultFor
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

// The fixed score header's rows: one per side, the run rates, and the result plate.

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
            .clip(RoundedCornerShape(8.dp))
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

@Composable
internal fun HeaderRunRate(label: String, value: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = typography.labelMedium, color = colors.textSecondary)
        Text(text = value, style = typography.numericSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    }
}

/**
 * One side in the fixed header: crest, code in team colour, score. The batting side's score is the
 * largest thing on screen and takes its team's colour; the other side steps back to secondary.
 * Before the first ball (no one batting) both read at full weight.
 */
@Composable
internal fun TeamScoreRow(
    teamName: String,
    acronym: String,
    logoUrl: String?,
    score: String,
    overs: String,
    isBatting: Boolean,
    anyBatting: Boolean,
    onTeamClick: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val ink = rememberTeamInk(teamName)
    val resting = anyBatting && !isBatting

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The crest and code open the team, where the app has a page for it. The score stays a score.
        val openTeam = stringResource(R.string.cd_open_team, teamName)
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (onTeamClick != null) Modifier.clickable(onClickLabel = openTeam, onClick = onTeamClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamCrest(teamName = teamName, logoUrl = logoUrl, size = 28.dp, acronym = acronym.ifBlank { null })
            Spacer(Modifier.width(spacing.md))
            Text(
                text = acronym.ifBlank { teamName },
                style = typography.titleMedium,
                color = ink,
                fontWeight = if (isBatting) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (score.isNotBlank()) {
            HeaderScore(
                score = score,
                overs = overs,
                resting = resting,
                color = when {
                    isBatting -> ink
                    resting -> colors.textSecondary
                    else -> colors.onSurface
                },
            )
        }
    }
}


@Composable
private fun HeaderScore(score: String, overs: String, resting: Boolean, color: Color) {
    val typography = BouncerThemeV2.typography
    AnimatedScoreText(
        text = score,
        style = if (resting) typography.scoreMedium else typography.scoreLarge,
        color = color,
        fontWeight = FontWeight.Bold,
    )
    if (overs.isNotBlank()) {
        Spacer(Modifier.width(BouncerThemeV2.spacing.xs))
        Text(
            text = "($overs)",
            style = typography.numericSmall,
            color = BouncerThemeV2.colors.textSecondary,
            maxLines = 1,
        )
    }
}
