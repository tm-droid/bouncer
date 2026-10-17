package com.tanush.bouncer.ui.components

import com.tanush.bouncer.managers.TeamAcronymManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Head to head history data
 */
data class HeadToHeadHistory(
    val team1Name: String,
    val team2Name: String,
    val team1Logo: String? = null,
    val team2Logo: String? = null,
    val totalMatches: Int,
    val team1Wins: Int,
    val team2Wins: Int,
    val noResults: Int,
    val recentMatches: List<HistoricalMatch> = emptyList(),
    val venueStats: VenueHistoryStats? = null,
    val formatStats: Map<String, FormatStats> = emptyMap()
)

data class HistoricalMatch(
    val date: String,
    val venue: String,
    val winner: String?, // null for no result
    val team1Score: String,
    val team2Score: String,
    val format: String,
    val margin: String? = null
)

data class VenueHistoryStats(
    val venueName: String,
    val totalMatches: Int,
    val team1Wins: Int,
    val team2Wins: Int
)

data class FormatStats(
    val format: String,
    val totalMatches: Int,
    val team1Wins: Int,
    val team2Wins: Int
)

/**
 * Premium Historical Stats Card
 * Shows head-to-head history with visualizations
 */
@Composable
fun HistoricalStatsCard(
    data: HeadToHeadHistory,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    // Ambient shimmer removed: it drove nothing that reported a state change.
    
    val team1WinRate = if (data.totalMatches > 0) 
        (data.team1Wins.toFloat() / data.totalMatches * 100) else 0f
    val team2WinRate = if (data.totalMatches > 0) 
        (data.team2Wins.toFloat() / data.totalMatches * 100) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color.Transparent
            ),
        shape = RoundedCornerShape(12.dp),
        color = BouncerThemeV2.colors.highlight.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_head_to_head),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    color = BouncerThemeV2.colors.onSurface
                )
                Text(
                    text = stringResource(R.string.historical_historical_statistics),
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
            
            // Team comparison header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamHistoryInfo(
                    name = data.team1Name,
                    logoUrl = data.team1Logo,
                    wins = data.team1Wins,
                    winRate = team1WinRate,
                    isLeading = data.team1Wins > data.team2Wins
                )
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "vs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "${data.totalMatches} matches",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                
                TeamHistoryInfo(
                    name = data.team2Name,
                    logoUrl = data.team2Logo,
                    wins = data.team2Wins,
                    winRate = team2WinRate,
                    isLeading = data.team2Wins > data.team1Wins
                )
            }
            
            // Win ratio bar
            WinRatioBar(
                team1Wins = data.team1Wins,
                team2Wins = data.team2Wins,
                noResults = data.noResults,
                totalMatches = data.totalMatches,
                team1Name = data.team1Name,
                team2Name = data.team2Name
            )
            
            // Recent form
            if (data.recentMatches.isNotEmpty()) {
                RecentFormSection(
                    matches = data.recentMatches,
                    team1Name = data.team1Name,
                    team2Name = data.team2Name
                )
            }
            
            // Format breakdown
            if (data.formatStats.isNotEmpty()) {
                FormatBreakdownSection(
                    formatStats = data.formatStats.values.toList(),
                    team1Name = data.team1Name,
                    team2Name = data.team2Name
                )
            }
            
            // Venue stats
            data.venueStats?.let { venue ->
                VenueHistorySection(
                    venueStats = venue,
                    team1Name = data.team1Name,
                    team2Name = data.team2Name
                )
            }
        }
    }
}

@Composable
private fun TeamHistoryInfo(
    name: String,
    logoUrl: String?,
    wins: Int,
    winRate: Float,
    isLeading: Boolean,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Leading glow
            if (isLeading) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }
            
            if (logoUrl != null) {
                CoilImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.glassBorder.copy(alpha = 0.1f))
                        .border(
                            width = if (isLeading) 2.dp else 1.dp,
                            color = if (isLeading) BouncerThemeV2.colors.onSurface else colors.glassBorder.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isLeading) BouncerThemeV2.colors.surfaceVariant else colors.glassBorder.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                        .border(
                            width = if (isLeading) 2.dp else 1.dp,
                            color = if (isLeading) BouncerThemeV2.colors.onSurface else colors.glassBorder.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(2).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLeading) BouncerThemeV2.colors.onSurface else colors.textSecondary
                    )
                }
            }
        }
        
        Text(
            text = name.take(15),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$wins",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLeading) BouncerThemeV2.colors.onSurface else colors.textPrimary
            )
            Text(
                text = "wins (${winRate.toInt()}%)",
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun WinRatioBar(
    team1Wins: Int,
    team2Wins: Int,
    noResults: Int,
    totalMatches: Int,
    team1Name: String,
    team2Name: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val team1Ratio = if (totalMatches > 0) team1Wins.toFloat() / totalMatches else 0f
    val team2Ratio = if (totalMatches > 0) team2Wins.toFloat() / totalMatches else 0f
    val noResultRatio = if (totalMatches > 0) noResults.toFloat() / totalMatches else 0f
    
    val animatedTeam1 by animateFloatAsState(
        targetValue = team1Ratio,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team1ratio"
    )
    val animatedTeam2 by animateFloatAsState(
        targetValue = team2Ratio,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team2ratio"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.glassBorder.copy(alpha = 0.2f))
        ) {
            // Team 1 section
            if (animatedTeam1 > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedTeam1)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(BouncerThemeV2.colors.onSurface, BouncerThemeV2.colors.onSurface)
                            )
                        )
                )
            }
            
            // No result section
            if (noResultRatio > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(noResultRatio / (1 - animatedTeam1))
                        .background(colors.glassBorder.copy(alpha = 0.4f))
                )
            }
            
            // Team 2 fills the rest
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${TeamAcronymManager.getAcronym(team1Name)} $team1Wins",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = BouncerThemeV2.colors.onSurface
            )
            if (noResults > 0) {
                Text(
                    text = "NR $noResults",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
            Text(
                text = "$team2Wins ${TeamAcronymManager.getAcronym(team2Name)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = BouncerThemeV2.colors.onSurface
            )
        }
    }
}

@Composable
private fun RecentFormSection(
    matches: List<HistoricalMatch>,
    team1Name: String,
    team2Name: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.historical_recent_meetings),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            color = colors.textSecondary
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            matches.take(5).forEach { match ->
                RecentMatchChip(
                    match = match,
                    team1Name = team1Name,
                    team2Name = team2Name
                )
            }
        }
    }
}

@Composable
private fun RecentMatchChip(
    match: HistoricalMatch,
    team1Name: String,
    team2Name: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val isTeam1Win = match.winner?.contains(team1Name, ignoreCase = true) == true
    val isTeam2Win = match.winner?.contains(team2Name, ignoreCase = true) == true
    val isNoResult = match.winner == null
    
    val chipColor = when {
        isNoResult -> colors.glassBorder
        isTeam1Win -> BouncerThemeV2.colors.onSurface
        isTeam2Win -> BouncerThemeV2.colors.onSurface
        else -> colors.glassBorder
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = chipColor.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = chipColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = match.date,
                fontSize = 11.sp,
                color = colors.textSecondary
            )
            
            Text(
                text = when {
                    isNoResult -> "NR"
                    isTeam1Win -> TeamAcronymManager.getAcronym(team1Name)
                    else -> TeamAcronymManager.getAcronym(team2Name)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = chipColor
            )
            
            if (match.margin != null && !isNoResult) {
                Text(
                    text = match.margin,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
            
            Text(
                text = match.format,
                fontSize = 11.sp,
                color = colors.textSecondary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun FormatBreakdownSection(
    formatStats: List<FormatStats>,
    team1Name: String,
    team2Name: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.historical_by_format),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            color = colors.textSecondary
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            formatStats.take(3).forEach { stats ->
                FormatStatItem(
                    stats = stats,
                    team1Name = team1Name,
                    team2Name = team2Name,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FormatStatItem(
    stats: FormatStats,
    team1Name: String,
    team2Name: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val team1Leading = stats.team1Wins > stats.team2Wins
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.glassBorder.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stats.format,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stats.team1Wins}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (team1Leading) BouncerThemeV2.colors.onSurface else colors.textSecondary
                )
                Text(
                    text = "-",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = "${stats.team2Wins}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!team1Leading && stats.team2Wins > stats.team1Wins) {
                        BouncerThemeV2.colors.onSurface
                    } else {
                        colors.textSecondary
                    }
                )
            }
            
            Text(
                text = "${stats.totalMatches} matches",
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun VenueHistorySection(
    venueStats: VenueHistoryStats,
    team1Name: String,
    team2Name: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BouncerThemeV2.colors.onSurface.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.historical_at_this_venue),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = venueStats.venueName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${venueStats.team1Wins}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BouncerThemeV2.colors.onSurface
                    )
                    Text(
                        text = TeamAcronymManager.getAcronym(team1Name),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                
                Text(
                    text = "-",
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${venueStats.team2Wins}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BouncerThemeV2.colors.onSurface
                    )
                    Text(
                        text = TeamAcronymManager.getAcronym(team2Name),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}
