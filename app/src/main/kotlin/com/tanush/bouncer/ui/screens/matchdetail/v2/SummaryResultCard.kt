package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Winner crest, name and margin; the performers live in the per-innings sections. The full status sentence is the
 * header's, so it is not repeated here.
 */
@Composable
internal fun SummaryResultCard(result: SummaryResult, match: CricketMatch, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .padding(spacing.lg),
    ) {
        ResultHeadline(result, match)
    }
}

@Composable
private fun ResultHeadline(result: SummaryResult, match: CricketMatch) {
    val spacing = BouncerThemeV2.spacing
    val winner = match.teams.firstOrNull { it.name == result.winner }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (winner != null) {
            TeamCrest(
                teamName = winner.name,
                logoUrl = winner.logoUrl,
                size = 48.dp,
                acronym = winner.acronym.ifBlank { null },
            )
            Spacer(Modifier.width(spacing.md))
        }
        Column {
            if (winner != null) {
                Text(
                    text = winner.name,
                    style = BouncerThemeV2.typography.titleMedium,
                    color = rememberTeamInk(winner.name),
                    fontWeight = FontWeight.Bold,
                )
            }
            ResultMarginText(result)
        }
    }
}

@Composable
private fun ResultMarginText(result: SummaryResult) {
    val text = when (result.kind) {
        ResultKind.Won -> result.margin?.let { stringResource(R.string.result_won_by, it) }
        ResultKind.Tied -> stringResource(R.string.result_tied)
        ResultKind.Drawn -> stringResource(R.string.result_drawn)
        ResultKind.NoResult -> stringResource(R.string.result_no_result)
        ResultKind.Abandoned -> stringResource(R.string.result_abandoned)
    }
    if (text != null) {
        Text(text = text, style = BouncerThemeV2.typography.titleSmall, color = BouncerThemeV2.colors.onSurface)
    }
}
