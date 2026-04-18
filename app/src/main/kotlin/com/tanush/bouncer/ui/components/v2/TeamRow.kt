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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * A single team row: logo (24dp) + name + score right-aligned.
 * Bold score when team is batting.
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

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = teamName,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = teamAcronym.ifBlank { teamName },
            style = typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = score,
                style = if (isBatting) typography.scoreLarge else typography.scoreMedium,
                fontWeight = if (isBatting) FontWeight.ExtraBold else FontWeight.Bold,
                color = colors.onSurface,
            )
            if (overs.isNotBlank() && overs != "0.0") {
                Text(
                    text = "($overs)",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
