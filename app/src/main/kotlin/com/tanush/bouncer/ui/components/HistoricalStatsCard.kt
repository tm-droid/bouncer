package com.tanush.bouncer.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val infiniteTransition = rememberInfiniteTransition(label = "history")
    
    // Subtle shimmer
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val team1WinRate = if (data.totalMatches > 0) 
        (data.team1Wins.toFloat() / data.totalMatches * 100) else 0f
    val team2WinRate = if (data.totalMatches > 0) 
        (data.team2Wins.toFloat() / data.totalMatches * 100) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
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
                    text = "HEAD TO HEAD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = hierarchy.secondary.primary
                )
                Text(
                    text = "Historical Statistics",
                    fontSize = 10.sp,
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
                        fontSize = 10.sp,
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
                                    hierarchy.secondary.primary.copy(alpha = 0.3f),
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
                            color = if (isLeading) hierarchy.secondary.primary else colors.glassBorder.copy(alpha = 0.3f),
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
                            color = if (isLeading) hierarchy.secondary.primary.copy(alpha = 0.1f) else colors.glassBorder.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                        .border(
                            width = if (isLeading) 2.dp else 1.dp,
                            color = if (isLeading) hierarchy.secondary.primary else colors.glassBorder.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(2).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLeading) hierarchy.secondary.primary else colors.textSecondary
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
                color = if (isLeading) hierarchy.secondary.primary else colors.textPrimary
            )
            Text(
                text = "wins (${winRate.toInt()}%)",
                fontSize = 10.sp,
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
                                colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
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
                text = "${team1Name.take(3)} $team1Wins",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = hierarchy.secondary.primary
            )
            if (noResults > 0) {
                Text(
                    text = "NR $noResults",
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
            }
            Text(
                text = "$team2Wins ${team2Name.take(3)}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = colors.statusInfo
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
            text = "RECENT MEETINGS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
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
        isTeam1Win -> hierarchy.secondary.primary
        isTeam2Win -> colors.statusInfo
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
                fontSize = 9.sp,
                color = colors.textSecondary
            )
            
            Text(
                text = when {
                    isNoResult -> "NR"
                    isTeam1Win -> team1Name.take(3).uppercase()
                    else -> team2Name.take(3).uppercase()
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = chipColor
            )
            
            if (match.margin != null && !isNoResult) {
                Text(
                    text = match.margin,
                    fontSize = 8.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
            
            Text(
                text = match.format,
                fontSize = 8.sp,
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
            text = "BY FORMAT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
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
                    color = if (team1Leading) hierarchy.secondary.primary else colors.textSecondary
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
                    color = if (!team1Leading && stats.team2Wins > stats.team1Wins) colors.statusInfo else colors.textSecondary
                )
            }
            
            Text(
                text = "${stats.totalMatches} matches",
                fontSize = 9.sp,
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
        color = hierarchy.secondary.primary.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = hierarchy.secondary.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AT THIS VENUE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
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
                        color = hierarchy.secondary.primary
                    )
                    Text(
                        text = team1Name.take(3),
                        fontSize = 9.sp,
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
                        color = colors.statusInfo
                    )
                    Text(
                        text = team2Name.take(3),
                        fontSize = 9.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}
