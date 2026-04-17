package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.glass.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors

@Composable
fun MatchPreviewScreen(
    match: CricketMatch,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    val team1Color = team1?.let { TeamPrimaryColors.getTeamColor(it.acronym) } ?: colors.goldPrimary
    val team2Color = team2?.let { TeamPrimaryColors.getTeamColor(it.acronym) } ?: colors.goldPrimary

    GlassBloomBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.screenHorizontal,
                end = dimens.screenHorizontal,
                top = dimens.lg,
                bottom = dimens.xxxl
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
        ) {
            // ── Header ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.md)
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBackClick,
                        contentDescription = "Back"
                    )
                    Text(
                        text = "MATCH PREVIEW",
                        style = typography.headingSmall,
                        color = hierarchy.secondary.primary,
                        letterSpacing = 2.sp
                    )
                }
            }

            // ── Hero Card ──
            item {
                GlassCardHero(
                    modifier = Modifier.fillMaxWidth(),
                    leftTeamColor = team1Color,
                    rightTeamColor = team2Color,
                    contentPadding = PaddingValues(dimens.xl)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Series badge
                        GlassCardSmall(
                            modifier = Modifier.wrapContentWidth(),
                            contentPadding = PaddingValues(horizontal = dimens.md, vertical = dimens.sm / 2)
                        ) {
                            Text(
                                text = match.matchTitle.uppercase(),
                                style = typography.labelSmall,
                                color = hierarchy.secondary.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(dimens.xl))

                        // Team logos + VS centre
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Team 1
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamLogoCircle(
                                    teamCode = team1?.acronym ?: "T1",
                                    logoUrl = team1?.logoUrl,
                                    size = 72.dp,
                                    showGlow = true
                                )
                                Spacer(modifier = Modifier.height(dimens.sm))
                                Text(
                                    text = team1?.acronym ?: "TBA",
                                    style = typography.headingSmall,
                                    color = team1Color,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = team1?.name ?: "",
                                    style = typography.labelSmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Centre countdown / match time
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = match.matchTime.ifEmpty { "TBD" },
                                    style = typography.headingMedium,
                                    color = hierarchy.secondary.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = match.matchType.uppercase(),
                                    style = typography.labelSmall,
                                    color = colors.textMuted,
                                    letterSpacing = 1.sp
                                )
                            }

                            // Team 2
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamLogoCircle(
                                    teamCode = team2?.acronym ?: "T2",
                                    logoUrl = team2?.logoUrl,
                                    size = 72.dp,
                                    showGlow = true
                                )
                                Spacer(modifier = Modifier.height(dimens.sm))
                                Text(
                                    text = team2?.acronym ?: "TBA",
                                    style = typography.headingSmall,
                                    color = team2Color,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = team2?.name ?: "",
                                    style = typography.labelSmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(dimens.lg))

                        // Date & Venue
                        Text(
                            text = match.date,
                            style = typography.bodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (match.venue.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = match.venue,
                                style = typography.labelSmall,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── Form Guide ──
            item {
                SectionHeader(icon = Icons.Default.Timeline, title = "FORM GUIDE")
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimens.lg)
                    ) {
                        FormGuideRow(
                            teamCode = team1?.acronym ?: "T1",
                            teamColor = team1Color,
                            results = listOf("W", "W", "L", "W", "D")
                        )
                        FormGuideRow(
                            teamCode = team2?.acronym ?: "T2",
                            teamColor = team2Color,
                            results = listOf("L", "W", "W", "W", "L")
                        )
                    }
                }
            }

            // ── Head to Head ──
            item {
                SectionHeader(icon = Icons.Default.EmojiEvents, title = "HEAD TO HEAD")
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimens.md)
                    ) {
                        H2HStatRow(label = "Matches", value = "54")
                        H2HStatRow(
                            label = "Wins ${team1?.acronym ?: "T1"}",
                            value = "28",
                            valueColor = team1Color
                        )
                        H2HStatRow(
                            label = "Wins ${team2?.acronym ?: "T2"}",
                            value = "22",
                            valueColor = team2Color
                        )
                        H2HStatRow(label = "Draws / NR", value = "4")
                    }
                }
            }

            // ── Key Players ──
            item {
                SectionHeader(icon = Icons.Default.Person, title = "KEY PLAYERS")
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimens.md)
                    ) {
                        // Team 1 heading
                        Text(
                            text = team1?.name ?: "Team 1",
                            style = typography.labelMedium,
                            color = team1Color,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        KeyPlayerRow(name = "V. Kohli", stat = "Avg 52.4", teamColor = team1Color)
                        KeyPlayerRow(name = "J. Bumrah", stat = "Econ 4.2", teamColor = team1Color)
                        KeyPlayerRow(name = "R. Jadeja", stat = "SR 87.5", teamColor = team1Color)

                        Spacer(modifier = Modifier.height(dimens.sm))

                        // Team 2 heading
                        Text(
                            text = team2?.name ?: "Team 2",
                            style = typography.labelMedium,
                            color = team2Color,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        KeyPlayerRow(name = "S. Smith", stat = "Avg 48.1", teamColor = team2Color)
                        KeyPlayerRow(name = "P. Cummins", stat = "Wkts 245", teamColor = team2Color)
                        KeyPlayerRow(name = "M. Labuschagne", stat = "Avg 46.3", teamColor = team2Color)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = hierarchy.secondary.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = typography.labelMedium,
            color = hierarchy.secondary.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun FormGuideRow(
    teamCode: String,
    teamColor: Color,
    results: List<String>
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = teamCode.uppercase(),
            style = typography.labelMedium,
            color = teamColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
        results.forEach { result ->
            val bgColor = when (result) {
                "W" -> colors.statusSuccess
                "L" -> colors.statusLive
                else -> colors.textMuted
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(bgColor.copy(alpha = 0.2f))
                    .then(
                        Modifier.background(
                            color = Color.Transparent,
                            shape = CircleShape
                        )
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(bgColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = result,
                    style = typography.labelSmall,
                    color = bgColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun H2HStatRow(
    label: String,
    value: String,
    valueColor: Color? = null
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = colors.textSecondary
        )
        Text(
            text = value,
            style = typography.statValue,
            color = valueColor ?: colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun KeyPlayerRow(
    name: String,
    stat: String,
    teamColor: Color
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlayerAvatar(
            imageUrl = null,
            playerName = name,
            size = 36.dp,
            teamColor = teamColor,
            showBorder = true
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatPill(label = "", value = stat)
    }
}
