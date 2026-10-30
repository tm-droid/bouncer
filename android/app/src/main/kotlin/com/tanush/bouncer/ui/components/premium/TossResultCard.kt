package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

data class TossDetails(
    val team1Name: String,
    val team2Name: String,
    val team1Logo: String,
    val team2Logo: String,
    val tossWinner: String,
    val decision: String, // "bat" or "bowl"
)

/**
 * Who won the toss and what they chose: the winner's crest, their name in their colour, and a
 * sentence. The winner used to be drawn in hardcoded blue or live-red caps beside an icon, with the
 * crests it was handed never shown, on fills of 2% white that vanished in light mode.
 */
@Composable
fun TossResultCard(
    tossDetails: TossDetails,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val winnerLogo = when (tossDetails.tossWinner) {
        tossDetails.team1Name -> tossDetails.team1Logo
        tossDetails.team2Name -> tossDetails.team2Logo
        else -> ""
    }

    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamCrest(teamName = tossDetails.tossWinner, logoUrl = winnerLogo.ifBlank { null }, size = 36.dp)
            Spacer(Modifier.width(spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.toss_toss_result),
                    style = typography.labelMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = tossDetails.tossWinner,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = rememberTeamInk(tossDetails.tossWinner),
                )
                Text(
                    text = stringResource(
                        if (tossDetails.decision == "bat") R.string.toss_chose_to_bat else R.string.toss_chose_to_bowl,
                    ),
                    style = typography.bodyMedium,
                    color = colors.onSurface,
                )
            }
        }
    }
}
