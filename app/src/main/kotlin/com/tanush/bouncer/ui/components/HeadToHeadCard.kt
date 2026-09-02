package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
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
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Head-to-Head statistics data
 */
data class HeadToHeadStats(
    val totalMatches: Int,
    val team1Wins: Int,
    val team2Wins: Int,
    val draws: Int,
    val team1HomeWins: Int,
    val team2HomeWins: Int,
    val lastFiveResults: List<String>, // "W1", "W2", "D" for team1 win, team2 win, draw
    val team1HighestScore: String,
    val team2HighestScore: String,
    val team1LowestScore: String,
    val team2LowestScore: String,
    val lastWinner: String?
)

/**
 * Premium Head-to-Head comparison card
 */
@Composable
fun HeadToHeadCard(
    match: CricketMatch,
    stats: HeadToHeadStats,
    team1Color: Color = GoldPrimary,
    team2Color: Color = BouncerDesignSystem.colors.statusInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    // Ambient loop removed: motion in this app reports a state change, never idles.
    // See BouncerCard's docs for the rule.
    val shimmerOffset = 0.5f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.2f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            0.5.dp,
            Brush.linearGradient(
                colors = listOf(
                    hierarchy.secondary.secondary.copy(alpha = 0.3f * shimmerOffset),
                    colors.glassBorder.copy(alpha = 0.3f),
                    hierarchy.secondary.secondary.copy(alpha = 0.3f * (1 - shimmerOffset))
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "HEAD TO HEAD",
                    color = hierarchy.secondary.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Team logos and win comparison
            TeamWinsComparison(
                team1 = team1,
                team2 = team2,
                team1Wins = stats.team1Wins,
                team2Wins = stats.team2Wins,
                totalMatches = stats.totalMatches,
                team1Color = team1Color,
                team2Color = team2Color,
                context = context
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Win bar
            WinPercentageBar(
                team1Wins = stats.team1Wins,
                team2Wins = stats.team2Wins,
                draws = stats.draws,
                team1Color = team1Color,
                team2Color = team2Color
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = colors.glassBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(20.dp))
            
            // Match stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    label = stringResource(R.string.h2h_total),
                    value = stats.totalMatches.toString(),
                    icon = Icons.Default.SportsCricket
                )
                StatColumn(
                    label = stringResource(R.string.h2h_draws),
                    value = stats.draws.toString(),
                    icon = Icons.Default.Remove
                )
                StatColumn(
                    label = stringResource(R.string.h2h_no_result),
                    value = "0",
                    icon = Icons.Default.Cloud
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = colors.glassBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(20.dp))
            
            // Last 5 matches
            Text(
                "LAST 5 MATCHES",
                color = colors.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LastFiveResults(
                results = stats.lastFiveResults,
                team1Name = team1?.acronym ?: team1?.name?.take(3)?.uppercase() ?: "T1",
                team2Name = team2?.acronym ?: team2?.name?.take(3)?.uppercase() ?: "T2",
                team1Color = team1Color,
                team2Color = team2Color
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = colors.glassBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(20.dp))
            
            // Score records
            ScoreRecords(
                team1Name = team1?.acronym ?: team1?.name?.take(3)?.uppercase() ?: "T1",
                team2Name = team2?.acronym ?: team2?.name?.take(3)?.uppercase() ?: "T2",
                team1Highest = stats.team1HighestScore,
                team2Highest = stats.team2HighestScore,
                team1Lowest = stats.team1LowestScore,
                team2Lowest = stats.team2LowestScore,
                team1Color = team1Color,
                team2Color = team2Color
            )
        }
    }
}

@Composable
private fun TeamWinsComparison(
    team1: CricketMatch.Team?,
    team2: CricketMatch.Team?,
    team1Wins: Int,
    team2Wins: Int,
    totalMatches: Int,
    team1Color: Color,
    team2Color: Color,
    context: android.content.Context
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Team 1
        TeamWinColumn(
            team = team1,
            wins = team1Wins,
            color = team1Color,
            context = context,
            alignment = Alignment.Start
        )
        
        // VS Badge
        Box(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BouncerThemeV2.colors.surfaceVariant,
                            colors.glassSurface
                        )
                    ),
                    CircleShape
                )
                .border(1.dp, colors.glassBorder.copy(alpha = 0.3f), CircleShape)
                .padding(16.dp)
        ) {
            Text(
                "VS",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        
        // Team 2
        TeamWinColumn(
            team = team2,
            wins = team2Wins,
            color = team2Color,
            context = context,
            alignment = Alignment.End
        )
    }
}

@Composable
private fun TeamWinColumn(
    team: CricketMatch.Team?,
    wins: Int,
    color: Color,
    context: android.content.Context,
    alignment: Alignment.Horizontal
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.width(100.dp)
    ) {
        // Logo
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.15f),
                            color.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
                .border(1.dp, color.copy(alpha = 0.2f), CircleShape)
        ) {
            val url = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 128, 128)
            CoilImage(
                model = if (url == null) null else ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = team?.name,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Team name
        Text(
            team?.acronym ?: team?.name?.take(3)?.uppercase() ?: "TM",
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Wins
        Text(
            "$wins WINS",
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
private fun WinPercentageBar(
    team1Wins: Int,
    team2Wins: Int,
    draws: Int,
    team1Color: Color,
    team2Color: Color
) {
    val colors = BouncerDesignSystem.colors
    val total = (team1Wins + team2Wins + draws).coerceAtLeast(1)
    val team1Percent = team1Wins.toFloat() / total
    val team2Percent = team2Wins.toFloat() / total
    
    // Animated values
    val animatedTeam1 by animateFloatAsState(
        targetValue = team1Percent,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team1Bar"
    )
    val animatedTeam2 by animateFloatAsState(
        targetValue = team2Percent,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team2Bar"
    )

    Column {
        // Percentages
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${(team1Percent * 100).toInt()}%",
                color = team1Color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${(team2Percent * 100).toInt()}%",
                color = team2Color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            val barHeight = size.height
            
            // Background
            drawRoundRect(
                color = colors.surface1,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
            )
            
            // Team 1 bar
            if (animatedTeam1 > 0) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            team1Color,
                            team1Color.copy(alpha = 0.7f)
                        )
                    ),
                    size = Size(size.width * animatedTeam1, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                )
            }
            
            // Team 2 bar (from right)
            if (animatedTeam2 > 0) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            team2Color.copy(alpha = 0.7f),
                            team2Color
                        )
                    ),
                    topLeft = Offset(size.width * (1 - animatedTeam2), 0f),
                    size = Size(size.width * animatedTeam2, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            value,
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = colors.textTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LastFiveResults(
    results: List<String>,
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        results.take(5).forEach { result ->
            val (color, text) = when (result) {
                "W1" -> team1Color to team1Name
                "W2" -> team2Color to team2Name
                else -> colors.textTertiary to "D"
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Fill remaining slots if less than 5
        repeat(5 - results.size.coerceAtMost(5)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.surface1, RoundedCornerShape(12.dp))
                    .border(0.5.dp, colors.glassBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "-",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScoreRecords(
    team1Name: String,
    team2Name: String,
    team1Highest: String,
    team2Highest: String,
    team1Lowest: String,
    team2Lowest: String,
    team1Color: Color,
    team2Color: Color
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Highest scores
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                "HIGHEST SCORE",
                color = colors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(team1Name, color = team1Color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(": ", color = colors.textTertiary, fontSize = 12.sp)
                Text(team1Highest, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(team2Name, color = team2Color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(": ", color = colors.textTertiary, fontSize = 12.sp)
                Text(team2Highest, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(60.dp)
                .background(colors.glassBorder.copy(alpha = 0.2f))
        )
        
        // Lowest scores
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                "LOWEST SCORE",
                color = colors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(team1Lowest, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(" :", color = colors.textTertiary, fontSize = 12.sp)
                Text(team1Name, color = team1Color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(team2Lowest, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(" :", color = colors.textTertiary, fontSize = 12.sp)
                Text(team2Name, color = team2Color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Compact Head-to-Head summary for match cards
 */
@Composable
fun HeadToHeadChip(
    team1Wins: Int,
    team2Wins: Int,
    team1Abbrev: String,
    team2Abbrev: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier,
        color = BouncerThemeV2.colors.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "$team1Abbrev $team1Wins-$team2Wins $team2Abbrev",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
