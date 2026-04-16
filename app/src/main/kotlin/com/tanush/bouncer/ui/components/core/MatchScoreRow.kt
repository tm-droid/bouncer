package com.tanush.bouncer.ui.components.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.TextSecondary

@Composable
fun MatchScoreRow(
    team: CricketMatch.Team?,
    score: String,
    modifier: Modifier = Modifier,
    teamTint: Color? = null,
    emphasized: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamBadge(
            teamName = team?.name,
            logoUrl = team?.logoUrl,
            isBatting = team?.isBatting == true,
            teamTint = teamTint,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = score,
            color = if (emphasized) (teamTint ?: TextSecondary) else TextSecondary,
            fontWeight = if (emphasized) FontWeight.Black else FontWeight.Bold,
            fontSize = 28.sp
        )
    }
}

