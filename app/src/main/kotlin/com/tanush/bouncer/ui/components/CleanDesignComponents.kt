package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.*
import io.sentry.Sentry

/**
 * Clean Design Components - iOS-Inspired UI System
 * 
 * Features:
 * - Refined typography with better hierarchy
 * - Cleaner cards with proper breathing room
 * - Subtle animations without overwhelming effects
 * - Professional gold accents used sparingly
 */

// Note: SectionHeader is defined in EliteUIComponents.kt

// ============================================================================
// HERO LIVE MATCH CARD - iOS-Inspired
// ============================================================================

/**
 * Hero card for featuring the main live match.
 * Clean design with team logos, scores, and match status.
 */
@Composable
fun HeroLiveMatchCard(
    match: CricketMatch,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    val isLive = match.isLive
    
    // Subtle pulse for live indicator
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = GlassSurface,
        border = BorderStroke(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.12f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header: Series name + Live Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = match.matchTitle,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (match.venue.isNotEmpty()) {
                        Text(
                            text = match.venue,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                
                // Status Badge
                val (statusText, statusColor) = getMatchStatusInfo(match)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .graphicsLayer { alpha = pulseAlpha }
                                    .shadow(4.dp, CircleShape, spotColor = statusColor)
                                    .background(statusColor, CircleShape)
                            )
                        }
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Teams Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                TeamScoreDisplay(
                    team = team1,
                    alignment = Alignment.Start
                )
                
                // VS Divider
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "vs",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Team 2
                TeamScoreDisplay(
                    team = team2,
                    alignment = Alignment.End
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Match Status Footer
            if (match.currentStatus.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = match.currentStatus,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamScoreDisplay(
    team: CricketMatch.Team?,
    alignment: Alignment.Horizontal
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Team Logo
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(GlassSurface)
                .border(
                    width = if (team?.isBatting == true) 2.dp else 0.5.dp,
                    color = if (team?.isBatting == true) hierarchy.secondary.primary else GlassBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 112, 112)
            CoilImage(
                model = logoUrl,
                contentDescription = team?.name,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        // Team Name
        Text(
            text = team?.acronym ?: "TBD",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Score
        Text(
            text = team?.score?.ifEmpty { "-" } ?: "-",
            color = if (team?.isBatting == true) hierarchy.secondary.primary else colors.textSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private fun getMatchStatusInfo(match: CricketMatch): Pair<String, Color> {
    try {
        val status = match.status.lowercase()
        return when {
            match.isLive -> "Live" to StatusSuccess
            status.contains("completed") || status.contains("won") || status.contains("result") -> "Completed" to ChartCyan
            status.contains("upcoming") || status.contains("preview") -> "Upcoming" to StatusInfo
            status.contains("delayed") || status.contains("postponed") -> "Delayed" to StatusWarning
            status.contains("break") || status.contains("lunch") || status.contains("tea") || status.contains("stumps") -> "Break" to StatusWarning
            status.contains("abandoned") -> "Abandoned" to StatusLive
            else -> "Scheduled" to TextSecondary
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

// ============================================================================
// MATCH CAROUSEL CARD - For horizontal scroll
// ============================================================================

/**
 * Compact match card for carousel display.
 */
@Composable
fun MatchCarouselCard(
    match: CricketMatch,
    onClick: () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    Surface(
        modifier = Modifier
            .width(280.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black
            ),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface,
        border = BorderStroke(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.12f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.matchTitle,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                val (statusText, statusColor) = getMatchStatusInfo(match)
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Teams
            listOf(team1, team2).forEach { team ->
                if (team != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Logo
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GlassSurface)
                                    .border(
                                        if (team.isBatting) 1.5.dp else 0.5.dp,
                                        if (team.isBatting) hierarchy.secondary.primary else GlassSurface,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CoilImage(
                                    model = TeamLogoManager.getBestLogoUrl(team.name, team.logoUrl, 64, 64),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            
                            Text(
                                text = team.acronym,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Text(
                            text = team.score.ifEmpty { "-" },
                            color = if (team.isBatting) hierarchy.secondary.primary else colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// PREMIUM MATCH LIST CARD – Design Spec v2.0
// Surface-1, 16dp radius, 16dp padding, mono-lg scores
// Live: 3dp left border in status-live
// ============================================================================

@Composable
fun PremiumMatchListCard(
    match: CricketMatch,
    onClick: () -> Unit
) {
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    val (statusText, statusColor) = getMatchStatusInfo(match)
    val isLive = match.isLive || match.status.lowercase().contains("live")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = 6.dp),
        shape = RoundedCornerShape(CornerRadius.lg),
        color = GlassSurface,
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Live left accent border — 3dp
            if (isLive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            StatusLive,
                            RoundedCornerShape(topStart = CornerRadius.lg, bottomStart = CornerRadius.lg)
                        )
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.space2)
            ) {
                // ── Header row: tournament + status chip ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = match.matchTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.xs),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // ── Team A row ──
                TeamScoreRow(
                    team = team1,
                    isBatting = team1?.isBatting == true
                )

                // ── Team B row ──
                TeamScoreRow(
                    team = team2,
                    isBatting = team2?.isBatting == true
                )

                // ── Status text ──
                if (match.currentStatus.isNotEmpty()) {
                    Text(
                        text = match.currentStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ── Ball-by-ball (live/recent only) ──
                val balls = match.recentBalls
                if (isLive && !balls.isNullOrEmpty()) {
                    BallByBallRow(balls = balls.takeLast(6))
                }

                // ── Mini probability bar — placeholder for future integration ──
                // Win probability data comes from ViewModel, not the match model directly.
                // This will be wired up when the prediction engine is connected.
            }
        }
    }
}

@Composable
private fun TeamScoreRow(
    team: CricketMatch.Team?,
    isBatting: Boolean
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 32dp circular logo
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            CoilImage(
                model = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 64, 64),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.space2))

        // Team name — h4, max 140dp
        Text(
            text = team?.name ?: "TBD",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBatting) FontWeight.Bold else FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 140.dp)
        )

        // Score — mono-lg, right aligned
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = team?.score?.ifEmpty { "-" } ?: "-",
                color = if (isBatting) hierarchy.secondary.primary else colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            if (!team?.overs.isNullOrEmpty()) {
                Text(
                    text = "(${team?.overs} ov)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun BallByBallRow(balls: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        balls.forEach { ball ->
            val (bgColor, textColor) = when {
                ball.equals("W", ignoreCase = true) -> StatusWicket to Color.White
                ball == "4" -> StatusBoundary to Color.White
                ball == "6" -> StatusSix to Color.White
                ball.contains("wd", ignoreCase = true) || ball.contains("nb", ignoreCase = true) ->
                    StatusWarning.copy(alpha = 0.2f) to StatusWarning
                ball == "0" -> Color.Transparent to TextTertiary
                else -> Surface3 to TextSecondary
            }

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .then(
                        if (ball == "0") Modifier.border(1.dp, Surface3, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ball,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun MiniProbabilityBar(
    team1Prob: Float,
    team2Prob: Float,
    team1Color: Color,
    team2Color: Color
) {
    val total = team1Prob + team2Prob
    if (total <= 0) return
    val fraction1 = team1Prob / total

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // 4dp bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(fraction1)
                    .fillMaxHeight()
                    .background(team1Color)
            )
            Box(
                modifier = Modifier
                    .weight(1f - fraction1)
                    .fillMaxHeight()
                    .background(team2Color)
            )
        }

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${team1Prob.toInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = team1Color
            )
            Text(
                text = "${team2Prob.toInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = team2Color
            )
        }
    }
}

// ============================================================================
// STATUS FILTER CHIPS
// ============================================================================

/**
 * iOS-inspired filter chip for status selection with gradient backgrounds.
 * Mimics iOS StatusChipView with enhanced visual polish.
 */
@Composable
fun StatusFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    
    Box(
        modifier = Modifier
            .height(40.dp)
            .shadow(
                elevation = if (isSelected) 8.dp else 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.4f) else Color.Transparent
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) {
                    // iOS-inspired blue-purple gradient
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF3B82F6).copy(alpha = 0.8f), // Blue
                            Color(0xFF8B5CF6).copy(alpha = 0.8f)  // Purple
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            GlassSurface,
                            GlassSurface
                        )
                    )
                }
            )
            .border(
                1.dp,
                if (isSelected) TextMuted else GlassSurface,
                RoundedCornerShape(20.dp)
            )
            .clickable {
                haptic.click()
                onClick()
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================================
// MARKET INSIGHT CARD
// ============================================================================

/**
 * Compact insight card for stats display.
 */
@Composable
fun MarketInsightCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String? = null,
    badgeColor: Color = StatusSuccess,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Column {
                Text(
                    text = title,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

// Note: MatchCardSkeleton is defined in LoadingScreens.kt

// ============================================================================
// APP FOOTER
// ============================================================================

/**
 * Simple app footer.
 */
@Composable
fun AppFooter() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.SportsCricket,
                contentDescription = null,
                tint = hierarchy.secondary.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Bouncer",
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "Live Cricket Scores & Analysis",
            color = TextMuted.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
