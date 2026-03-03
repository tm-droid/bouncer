package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.ui.theme.BouncerColors
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.ui.theme.GlassCardShape
import com.tanush.bouncer.ui.theme.labelAllCaps
import com.tanush.bouncer.ui.theme.tabularBody
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Enhanced Scoreboard Card for detailed batting stats.
 *
 * Displays batting lineup with runs, balls, 6s, 4s, and strike rate in
 * aligned tabular columns — matching the design inspiration's data-dense
 * scoreboard panel.
 *
 * @param teamName      The batting team name (e.g., "Australia").
 * @param opponentName  The bowling team name (e.g., "India").
 * @param batsmen       List of [ScrapedBatsman] with detailed stats.
 * @param selectedTeam  0 = first team selected, 1 = second team.
 * @param onTeamToggle  Callback when user taps alternate team.
 */
@Composable
fun EnhancedScoreboardCard(
    teamName: String,
    opponentName: String,
    batsmen: List<ScrapedBatsman>,
    selectedTeam: Int = 0,
    onTeamToggle: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = GlassCardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(BouncerDimens.cardPadding)) {
            // ── Header: "SCOREBOARD" ────────────────────────────────────
            Text(
                text = "SCOREBOARD",
                style = MaterialTheme.typography.labelAllCaps,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(BouncerDimens.sm))

            // ── Team selector row ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = teamName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = opponentName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(BouncerDimens.md))

            // ── Section label ───────────────────────────────────────────
            Text(
                text = "Batting",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(BouncerDimens.xs))

            // ── Column headers ──────────────────────────────────────────
            ScoreboardHeaderRow()

            Spacer(modifier = Modifier.height(BouncerDimens.xs))

            // ── Batsman rows ────────────────────────────────────────────
            if (batsmen.isEmpty()) {
                Text(
                    text = "No batting data available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = BouncerDimens.lg),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(batsmen) { index, batsman ->
                        BatsmanRow(batsman = batsman)
                        if (index < batsmen.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Column Header Row ───────────────────────────────────────────────────────

@Composable
private fun ScoreboardHeaderRow() {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Placeholder for avatar + name column
        Spacer(modifier = Modifier.weight(1f))

        // Stat column headers
        val headers = listOf("R", "B", "6s", "4s", "S/R")
        headers.forEach { header ->
            Text(
                text = header,
                modifier = Modifier.width(BouncerDimens.scoreboardStatColumnWidth),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Individual Batsman Row ──────────────────────────────────────────────────

@Composable
private fun BatsmanRow(batsman: ScrapedBatsman) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BouncerDimens.scoreboardItemHeight)
            .padding(vertical = BouncerDimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(BouncerDimens.scoreboardAvatarSize)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (batsman.faceImageId != null) {
                AsyncImage(
                    model = "https://www.cricbuzz.com/a/img/v1/48x48/i1/c${batsman.faceImageId}/player_face.jpg",
                    contentDescription = "${batsman.name} photo",
                    modifier = Modifier
                        .size(BouncerDimens.scoreboardAvatarSize)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = batsman.name.take(1),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(BouncerDimens.sm))

        // Name + dismissal (how out)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = batsman.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!batsman.howOut.isNullOrBlank() && batsman.howOut != "batting") {
                Text(
                    text = batsman.howOut ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                )
            } else if (batsman.isStriker) {
                Text(
                    text = "batting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }

        // Stats: R, B, 6s, 4s, S/R
        StatsCell(text = batsman.runs, bold = true)
        StatsCell(text = batsman.balls)
        StatsCell(text = batsman.sixes)
        StatsCell(text = batsman.fours)
        StatsCell(text = batsman.strikeRate, width = BouncerDimens.scoreboardStatColumnWidth + 4.dp)
    }
}

@Composable
private fun StatsCell(
    text: String,
    bold: Boolean = false,
    width: Dp = BouncerDimens.scoreboardStatColumnWidth,
) {
    val colors = BouncerDesignSystem.colors
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.tabularBody,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = if (bold) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

// ── Preview ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun EnhancedScoreboardCardPreview() {
    val colors = BouncerDesignSystem.colors
    BouncerTheme(darkTheme = true, dynamicColor = false) {
        EnhancedScoreboardCard(
            teamName = "Australia",
            opponentName = "India",
            batsmen = listOf(
                ScrapedBatsman(
                    name = "David Warner",
                    runs = "41",
                    balls = "52",
                    fours = "0",
                    sixes = "6",
                    strikeRate = "78.85",
                    howOut = "c & b Kuldeep Yadav",
                ),
                ScrapedBatsman(
                    name = "Mitchell Marsh",
                    runs = "0",
                    balls = "6",
                    fours = "0",
                    sixes = "0",
                    strikeRate = "0.00",
                    howOut = "c Virat Kohli b Jasprit Bumrah",
                ),
                ScrapedBatsman(
                    name = "Steve Smith",
                    runs = "46",
                    balls = "71",
                    fours = "0",
                    sixes = "5",
                    strikeRate = "64.79",
                    howOut = "batting",
                ),
                ScrapedBatsman(
                    name = "Marnus Labuschagne",
                    runs = "27",
                    balls = "41",
                    fours = "0",
                    sixes = "1",
                    strikeRate = "65.85",
                    howOut = "c KL Rahul b Ravindra Jadeja",
                ),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
