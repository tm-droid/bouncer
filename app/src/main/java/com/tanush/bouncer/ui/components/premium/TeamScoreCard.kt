package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.ui.theme.StatusCardShape
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Compact card showing the current team score summary.
 *
 * Displays: team flag → "AUS batting" → large "140-7" → "(36.1)" → CRR/subtitle.
 * Used in the bottom status row of the live match screen.
 */
@Composable
fun TeamScoreCard(
    teamAcronym: String,
    teamLogoUrl: String?,
    score: String,
    overs: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(BouncerDimens.statusCardHeight),
        shape = StatusCardShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(BouncerDimens.cardInnerPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Header: flag + "AUS batting"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BouncerDimens.xs),
            ) {
                if (teamLogoUrl != null) {
                    AsyncImage(
                        model = teamLogoUrl,
                        contentDescription = "$teamAcronym flag",
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(
                    text = "$teamAcronym batting",
                    style = typography.labelMedium,
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(BouncerDimens.sm))

            // Large score
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = score,
                    style = typography.headingMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.textPrimary,
                )
                if (overs.isNotBlank() && overs != "0.0") {
                    Text(
                        text = " ($overs)",
                        style = typography.bodyMedium.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.textSecondary,
                    )
                }
            }

            // Subtitle (e.g., "AUS choose to bat • CRR: 3.94")
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(BouncerDimens.xxs))
                Text(
                    text = subtitle,
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Preview ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun TeamScoreCardPreview() {
    val colors = BouncerDesignSystem.colors
    BouncerTheme(darkTheme = true, dynamicColor = false) {
        TeamScoreCard(
            teamAcronym = "AUS",
            teamLogoUrl = null,
            score = "140-7",
            overs = "36.1",
            subtitle = "AUS choose to bat • CRR: 3.94",
            modifier = Modifier.padding(8.dp),
        )
    }
}
