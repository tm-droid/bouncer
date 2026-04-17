package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import io.sentry.Sentry
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Match summary data
 */
data class MatchSummaryData(
    val matchTitle: String,
    val venue: String,
    val date: String,
    val matchFormat: String,
    val team1: TeamSummary,
    val team2: TeamSummary,
    val matchResult: String,
    val winningTeam: String? = null,
    val playerOfMatch: PlayerOfMatch? = null,
    val keyMoments: List<SummaryKeyMoment> = emptyList(),
    val isLive: Boolean = false,
    val currentStatus: String? = null
)

data class TeamSummary(
    val name: String,
    val shortName: String,
    val logoUrl: String? = null,
    val score: String,
    val overs: String,
    val runRate: Float,
    val extras: Int,
    val topScorer: SummaryPlayerStat? = null,
    val topBowler: SummaryPlayerStat? = null
)

data class PlayerOfMatch(
    val name: String,
    val imageUrl: String? = null,
    val team: String,
    val performance: String
)

data class SummaryPlayerStat(
    val name: String,
    val stat: String
)

data class SummaryKeyMoment(
    val description: String,
    val over: Float,
    val team: String,
    val type: SummaryMomentType
)

enum class SummaryMomentType {
    WICKET, MILESTONE, POWERPLAY, BOUNDARY_SPREE, COLLAPSE, RECORD
}

/**
 * Premium Match Summary Card
 * Comprehensive match overview
 */
@Composable
fun MatchSummaryCard(
    summary: MatchSummaryData,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "summary")
    
    val liveGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveGlow"
    )
    
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = if (summary.isLive) colors.statusSuccess.copy(alpha = 0.2f) else hierarchy.secondary.primary.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            0.5.dp,
            if (summary.isLive) colors.statusSuccess.copy(alpha = liveGlow) else colors.glassBorder.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            MatchHeader(summary = summary, liveGlow = liveGlow)
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Scores comparison
            ScoresSection(team1 = summary.team1, team2 = summary.team2)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Match result or current status
            ResultStatus(
                result = summary.matchResult,
                isLive = summary.isLive,
                currentStatus = summary.currentStatus,
                winningTeam = summary.winningTeam
            )
            
            // Top performers
            Spacer(modifier = Modifier.height(16.dp))
            TopPerformers(team1 = summary.team1, team2 = summary.team2)
            
            // Player of the match
            summary.playerOfMatch?.let { pom ->
                Spacer(modifier = Modifier.height(16.dp))
                PlayerOfMatchSection(pom = pom)
            }
            
            // Key moments (if any)
            if (summary.keyMoments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                KeyMomentsSection(moments = summary.keyMoments)
            }
        }
    }
}

/**
 * Match header with title and live badge
 */
@Composable
private fun MatchHeader(summary: MatchSummaryData, liveGlow: Float) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = summary.matchTitle,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Format badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.statusInfo.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = summary.matchFormat,
                        color = colors.statusInfo,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = summary.venue,
                color = colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = summary.date,
                color = colors.textTertiary,
                fontSize = 10.sp
            )
        }
        
        // Live badge
        if (summary.isLive) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.statusSuccess.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, colors.statusSuccess.copy(alpha = liveGlow)),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colors.statusSuccess, CircleShape)
                    )
                    Text(
                        text = "LIVE",
                        color = colors.statusSuccess,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * Scores comparison section
 */
@Composable
private fun ScoresSection(team1: TeamSummary, team2: TeamSummary) {
    val colors = BouncerDesignSystem.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.glassSurface,
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Team 1
            TeamScoreColumn(team = team1, alignment = Alignment.Start)
            
            // VS
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VS",
                    color = colors.textTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Team 2
            TeamScoreColumn(team = team2, alignment = Alignment.End)
        }
    }
}

/**
 * Team score column
 */
@Composable
private fun TeamScoreColumn(team: TeamSummary, alignment: Alignment.Horizontal) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (alignment == Alignment.Start) {
                TeamLogo(logoUrl = team.logoUrl, teamName = team.name)
            }
            
            Column(
                horizontalAlignment = alignment
            ) {
                Text(
                    text = team.shortName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = team.name,
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            
            if (alignment == Alignment.End) {
                TeamLogo(logoUrl = team.logoUrl, teamName = team.name)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Score
        Text(
            text = team.score,
            color = hierarchy.secondary.primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        
        Text(
            text = "(${team.overs} ov)",
            color = colors.textSecondary,
            fontSize = 11.sp
        )
        
        Text(
            text = "RR: ${String.format("%.2f", team.runRate)}",
            color = colors.statusInfo,
            fontSize = 10.sp
        )
    }
}

/**
 * Team logo
 */
@Composable
private fun TeamLogo(logoUrl: String?, teamName: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    if (logoUrl != null) {
        CoilImage(
            model = logoUrl,
            contentDescription = teamName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(2.dp, hierarchy.secondary.primary.copy(alpha = 0.3f), CircleShape)
        )
    } else {
        Surface(
            shape = CircleShape,
            color = hierarchy.secondary.primary.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, hierarchy.secondary.primary.copy(alpha = 0.3f)),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = teamName.take(2).uppercase(),
                    color = hierarchy.secondary.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Result/status section
 */
@Composable
private fun ResultStatus(
    result: String,
    isLive: Boolean,
    currentStatus: String?,
    winningTeam: String?
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isLive) colors.statusSuccess.copy(alpha = 0.1f) else hierarchy.secondary.primary.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isLive) currentStatus ?: result else result,
            color = if (isLive) colors.statusSuccess else hierarchy.secondary.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

/**
 * Top performers section
 */
@Composable
private fun TopPerformers(team1: TeamSummary, team2: TeamSummary) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Batting
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.glassSurface,
            border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.SportsCricket,
                        contentDescription = null,
                        tint = hierarchy.secondary.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "TOP BATTERS",
                        color = colors.textTertiary,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                team1.topScorer?.let {
                    PerformerRow(name = it.name, stat = it.stat, team = team1.shortName)
                }
                
                team2.topScorer?.let {
                    PerformerRow(name = it.name, stat = it.stat, team = team2.shortName)
                }
            }
        }
        
        // Bowling
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.glassSurface,
            border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = colors.statusInfo,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "TOP BOWLERS",
                        color = colors.textTertiary,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                team1.topBowler?.let {
                    PerformerRow(name = it.name, stat = it.stat, team = team1.shortName)
                }
                
                team2.topBowler?.let {
                    PerformerRow(name = it.name, stat = it.stat, team = team2.shortName)
                }
            }
        }
    }
}

/**
 * Performer row
 */
@Composable
private fun PerformerRow(name: String, stat: String, team: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = team,
                color = colors.textTertiary,
                fontSize = 9.sp
            )
        }
        Text(
            text = stat,
            color = hierarchy.secondary.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Player of the match section
 */
@Composable
private fun PlayerOfMatchSection(pom: PlayerOfMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = hierarchy.secondary.primary.copy(alpha = 0.1f),
        border = BorderStroke(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Medal icon
            Surface(
                shape = CircleShape,
                color = hierarchy.secondary.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = hierarchy.secondary.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PLAYER OF THE MATCH",
                    color = hierarchy.secondary.primary,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = pom.name,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${pom.team} • ${pom.performance}",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Key moments section
 */
@Composable
private fun KeyMomentsSection(moments: List<SummaryKeyMoment>) {
    val colors = BouncerDesignSystem.colors
    Column {
        Text(
            text = "KEY MOMENTS",
            color = colors.textTertiary,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        moments.take(3).forEach { moment ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = getMomentColor(moment.type).copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        getMomentIcon(moment.type),
                        contentDescription = null,
                        tint = getMomentColor(moment.type),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = moment.description,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${moment.over} ov",
                        color = colors.textTertiary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Get moment color
 */
private fun getMomentColor(type: SummaryMomentType): Color {
    try {
        return when (type) {
            SummaryMomentType.WICKET -> StatusLive
            SummaryMomentType.MILESTONE -> GoldPrimary
            SummaryMomentType.POWERPLAY -> StatusInfo
            SummaryMomentType.BOUNDARY_SPREE -> StatusSuccess
            SummaryMomentType.COLLAPSE -> StatusWarning
            SummaryMomentType.RECORD -> GoldPrimary
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Get moment icon
 */
private fun getMomentIcon(type: SummaryMomentType): androidx.compose.ui.graphics.vector.ImageVector {
    try {
        return when (type) {
            SummaryMomentType.WICKET -> Icons.Default.RemoveCircle
            SummaryMomentType.MILESTONE -> Icons.Default.Stars
            SummaryMomentType.POWERPLAY -> Icons.Default.FlashOn
            SummaryMomentType.BOUNDARY_SPREE -> Icons.Default.Whatshot
            SummaryMomentType.COLLAPSE -> Icons.Default.Warning
            SummaryMomentType.RECORD -> Icons.Default.EmojiEvents
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Mini match summary for list views
 */
@Composable
fun MiniMatchSummary(
    team1Name: String,
    team1Score: String,
    team2Name: String,
    team2Score: String,
    result: String,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.glassSurface,
        border = BorderStroke(0.5.dp, if (isLive) colors.statusSuccess.copy(alpha = 0.5f) else colors.glassBorder.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = team1Name, color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(text = team1Score, color = hierarchy.secondary.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = team2Name, color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(text = team2Score, color = hierarchy.secondary.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = result,
                color = if (isLive) colors.statusSuccess else colors.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
