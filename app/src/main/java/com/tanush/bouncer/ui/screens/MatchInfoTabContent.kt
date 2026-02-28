package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardLarge
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.MatchStatusBadge
import com.tanush.bouncer.ui.components.glass.Pitch3DView
import com.tanush.bouncer.ui.components.glass.ProgressBar
import com.tanush.bouncer.ui.components.glass.StatPill
import com.tanush.bouncer.ui.components.glass.TeamLogoCircle
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Full match information tab content with premium glass-morphism design.
 *
 * Sections:
 * 1. Venue Hero Card — large glass card with venue, location, format chip, date
 * 2. Match Details Card — series, date/time, status/result
 * 3. Teams Section — two team cards side by side
 * 4. Toss & Officials Card — toss info and raw info text
 * 5. Additional Info — raw info content if present
 */
@Composable
fun MatchInfoContent(
    match: CricketMatch,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    // Parse venue into name and location
    val venueParts = remember(match.venue) {
        val parts = match.venue.split(",", limit = 2)
        Pair(parts.first().trim(), parts.getOrNull(1)?.trim() ?: "")
    }

    // Parse toss info from match.info
    val tossInfo = remember(match.info) {
        match.info.lines()
            .firstOrNull { line ->
                line.lowercase().contains("toss") || line.lowercase().contains("elected")
            }
            ?.trim()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = dimens.screenHorizontal,
            vertical = dimens.md
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        // ─────────────────────────────────────────────────────────────────
        //  Section 1: Venue Hero Card
        // ─────────────────────────────────────────────────────────────────
        item(key = "venue_hero") {
            SectionHeader(
                icon = Icons.Default.Stadium,
                title = "VENUE"
            )
            Spacer(modifier = Modifier.height(dimens.sm))
            GlassCardLarge(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                useGoldBorder = true
            ) {
                Column {
                    // Venue name
                    Text(
                        text = venueParts.first,
                        style = typography.headingLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Location
                    if (venueParts.second.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(dimens.xs))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimens.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = colors.goldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = venueParts.second,
                                style = typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(dimens.lg))
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.06f),
                        thickness = dimens.dividerThickness
                    )
                    Spacer(modifier = Modifier.height(dimens.md))

                    // Format chip + Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Format chip
                        GlassCard(
                            contentPadding = PaddingValues(
                                horizontal = dimens.md,
                                vertical = dimens.xs
                            ),
                            useGoldBorder = true,
                            shape = RoundedCornerShape(dimens.sm)
                        ) {
                            Text(
                                text = match.matchType.uppercase(),
                                style = typography.labelSmall,
                                color = colors.goldPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Date
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimens.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = match.date.ifBlank { match.matchTime },
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        //  Section-P: Pitch Report (3D View)
        // ─────────────────────────────────────────────────────────────────
        item(key = "pitch_report") {
            SectionHeader(
                icon = Icons.Default.SportsCricket,
                title = "PITCH REPORT"
            )
            Spacer(modifier = Modifier.height(dimens.sm))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(dimens.lg)
            ) {
                Column {
                    Text(
                        text = "Hard & Dry Surface",
                        style = typography.headingSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Batting friendly with consistent bounce. Spinners might get help in the second innings.",
                        style = typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(dimens.lg))
                    
                    Pitch3DView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(dimens.lg))
                    
                    // Pitch metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimens.lg)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PACE",
                                style = typography.labelSmall,
                                color = colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ProgressBar(progress = 0.75f, teamColor = colors.goldPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SPIN",
                                style = typography.labelSmall,
                                color = colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ProgressBar(progress = 0.45f, teamColor = colors.statusInfo)
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        //  Section 2: Match Details
        // ─────────────────────────────────────────────────────────────────
        item(key = "match_details") {
            SectionHeader(
                icon = Icons.Default.Info,
                title = "MATCH DETAILS"
            )
            Spacer(modifier = Modifier.height(dimens.sm))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                useGoldBorder = true,
                contentPadding = PaddingValues(
                    horizontal = dimens.xl,
                    vertical = dimens.lg
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.md)
                ) {
                    // Series
                    InfoRow(
                        label = "Series",
                        value = match.matchTitle
                    )
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.06f),
                        thickness = dimens.dividerThickness
                    )
                    // Date/Time
                    InfoRow(
                        label = "Date / Time",
                        value = buildString {
                            append(match.date)
                            if (match.matchTime.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(match.matchTime)
                            }
                        }
                    )
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.06f),
                        thickness = dimens.dividerThickness
                    )
                    // Status/Result
                    InfoRow(
                        label = "Status",
                        value = match.currentStatus.ifBlank { match.status },
                        valueColor = if (match.isLive) colors.statusLive else colors.textPrimary
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        //  Section 3: Teams
        // ─────────────────────────────────────────────────────────────────
        if (match.teams.isNotEmpty()) {
            item(key = "teams_section") {
                SectionHeader(
                    icon = Icons.Default.Groups,
                    title = "TEAMS"
                )
                Spacer(modifier = Modifier.height(dimens.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
                ) {
                    match.teams.take(2).forEach { team ->
                        GlassCardSmall(
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(dimens.sm)
                            ) {
                                TeamLogoCircle(
                                    teamCode = team.acronym.ifBlank { team.name.take(3) },
                                    logoUrl = team.logoUrl,
                                    size = 48.dp,
                                    showBorder = true,
                                    showGlow = true
                                )
                                Text(
                                    text = team.name,
                                    style = typography.headingSmall,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (team.score.isNotBlank()) {
                                    Text(
                                        text = team.score,
                                        style = typography.scoreCompact,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        //  Section 4: Toss & Officials
        // ─────────────────────────────────────────────────────────────────
        item(key = "toss_officials") {
            SectionHeader(
                icon = Icons.Default.Shield,
                title = "MATCH OFFICIALS"
            )
            Spacer(modifier = Modifier.height(dimens.sm))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                useGoldBorder = true,
                contentPadding = PaddingValues(
                    horizontal = dimens.xl,
                    vertical = dimens.lg
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.md)
                ) {
                    if (tossInfo != null) {
                        InfoRow(
                            label = "Toss",
                            value = tossInfo
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.06f),
                            thickness = dimens.dividerThickness
                        )
                    }
                    InfoRow(
                        label = "Format",
                        value = match.matchType
                    )
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.06f),
                        thickness = dimens.dividerThickness
                    )
                    InfoRow(
                        label = "Venue",
                        value = match.venue
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        //  Section 5: Additional Info
        // ─────────────────────────────────────────────────────────────────
        if (match.info.isNotBlank()) {
            item(key = "additional_info") {
                SectionHeader(
                    icon = Icons.Default.Info,
                    title = "ADDITIONAL INFO"
                )
                Spacer(modifier = Modifier.height(dimens.sm))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = dimens.xl,
                        vertical = dimens.lg
                    )
                ) {
                    Text(
                        text = match.info,
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Bottom spacing for nav bar
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(dimens.spacing.mega))
        }
    }
}

// =============================================================================
// Reusable section header: gold icon + uppercase label
// =============================================================================

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.goldPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = typography.labelSmall,
            color = colors.goldPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

// =============================================================================
// Info row: muted label left, value right
// =============================================================================

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.weight(0.35f)
        )
        Spacer(modifier = Modifier.width(BouncerDesignSystem.dimens.sm))
        Text(
            text = value,
            style = typography.bodyMedium,
            color = valueColor ?: colors.textPrimary,
            modifier = Modifier.weight(0.65f),
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
