package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.theme.*

/**
 * Head-to-Head comparison data
 */
data class HeadToHeadData(
    val team1Name: String,
    val team2Name: String,
    val team1Wins: Int,
    val team2Wins: Int,
    val draws: Int,
    val noResults: Int,
    val team1AvgScore: Float,
    val team2AvgScore: Float,
    val lastFiveResults: List<String> // "W1", "W2", "D" for Team1 win, Team2 win, Draw
)

/**
 * Stat comparison item
 */
data class StatComparison(
    val label: String,
    val team1Value: Float,
    val team2Value: Float,
    val unit: String = "",
    val isHigherBetter: Boolean = true
)

/**
 * Premium Team Comparison View with animated statistics
 */
@Composable
fun TeamComparisonView(
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color,
    headToHead: HeadToHeadData,
    stats: List<StatComparison>,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "comparison_progress"
    )
    
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header with team logos
            TeamComparisonHeader(
                team1Name = team1Name,
                team2Name = team2Name,
                team1Color = team1Color,
                team2Color = team2Color
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Head to Head Record
            HeadToHeadRecord(
                headToHead = headToHead,
                team1Color = team1Color,
                team2Color = team2Color,
                animationProgress = animationProgress
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Stats Comparison Bars
            stats.forEachIndexed { index, stat ->
                val delayedProgress by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 800,
                        delayMillis = 100 * index,
                        easing = FastOutSlowInEasing
                    ),
                    label = "stat_$index"
                )
                
                StatComparisonBar(
                    stat = stat,
                    team1Color = team1Color,
                    team2Color = team2Color,
                    animationProgress = delayedProgress
                )
                
                if (index < stats.lastIndex) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Last 5 Matches
            LastFiveMatches(
                results = headToHead.lastFiveResults,
                team1Color = team1Color,
                team2Color = team2Color
            )
        }
    }
}

@Composable
private fun TeamComparisonHeader(
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val context = LocalContext.current
    val colors = BouncerDesignSystem.colors
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Team 1
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(8.dp, CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                team1Color.copy(alpha = 0.3f),
                                team1Color.copy(alpha = 0.1f)
                            )
                        ),
                        CircleShape
                    )
                    .border(2.dp, team1Color.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val logoUrl = TeamLogoManager.getLogoUrl(team1Name)
                if (logoUrl != null) {
                    CoilImage(
                        model = ImageRequest.Builder(context)
                            .data(logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = team1Name,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Text(
                        team1Name.take(3).uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = team1Color
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                team1Name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
        
        // VS Badge
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = 0.2f),
                            hierarchy.secondary.primary.copy(alpha = 0.05f)
                        )
                    ),
                    CircleShape
                )
                .border(1.dp, hierarchy.secondary.primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "VS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = hierarchy.secondary.primary
            )
        }
        
        // Team 2
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(8.dp, CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                team2Color.copy(alpha = 0.3f),
                                team2Color.copy(alpha = 0.1f)
                            )
                        ),
                        CircleShape
                    )
                    .border(2.dp, team2Color.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val logoUrl = TeamLogoManager.getLogoUrl(team2Name)
                if (logoUrl != null) {
                    CoilImage(
                        model = ImageRequest.Builder(context)
                            .data(logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = team2Name,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Text(
                        team2Name.take(3).uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = team2Color
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                team2Name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun HeadToHeadRecord(
    headToHead: HeadToHeadData,
    team1Color: Color,
    team2Color: Color,
    animationProgress: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val totalMatches = headToHead.team1Wins + headToHead.team2Wins + headToHead.draws + headToHead.noResults
    val team1Percentage = if (totalMatches > 0) headToHead.team1Wins.toFloat() / totalMatches else 0f
    val team2Percentage = if (totalMatches > 0) headToHead.team2Wins.toFloat() / totalMatches else 0f
    val drawPercentage = if (totalMatches > 0) (headToHead.draws + headToHead.noResults).toFloat() / totalMatches else 0f
    
    val colors = BouncerDesignSystem.colors
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.glassBorderHighlight),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Head to Head Record",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$totalMatches matches",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Win record display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RecordItem(
                    label = "Won",
                    value = headToHead.team1Wins,
                    color = team1Color,
                    animationProgress = animationProgress
                )
                RecordItem(
                    label = "Draw/NR",
                    value = headToHead.draws + headToHead.noResults,
                    color = colors.textTertiary,
                    animationProgress = animationProgress
                )
                RecordItem(
                    label = "Won",
                    value = headToHead.team2Wins,
                    color = team2Color,
                    animationProgress = animationProgress
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress bar showing win distribution
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.glassBorder.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Team 1 wins
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(team1Percentage.coerceAtLeast(0.01f) * animationProgress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        team1Color,
                                        team1Color.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )
                    // Draws
                    if (drawPercentage > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(drawPercentage.coerceAtLeast(0.01f) * animationProgress)
                                .background(colors.textTertiary.copy(alpha = 0.5f))
                        )
                    }
                    // Team 2 wins
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(team2Percentage.coerceAtLeast(0.01f) * animationProgress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        team2Color.copy(alpha = 0.7f),
                                        team2Color
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordItem(
    label: String,
    value: Int,
    color: Color,
    animationProgress: Float
) {
    val colors = BouncerDesignSystem.colors
    val animatedValue by animateIntAsState(
        targetValue = (value * animationProgress).toInt(),
        animationSpec = tween(durationMillis = 800),
        label = "record_value"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$animatedValue",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun StatComparisonBar(
    stat: StatComparison,
    team1Color: Color,
    team2Color: Color,
    animationProgress: Float
) {
    val colors = BouncerDesignSystem.colors
    val total = stat.team1Value + stat.team2Value
    val team1Ratio = if (total > 0) stat.team1Value / total else 0.5f
    val team2Ratio = if (total > 0) stat.team2Value / total else 0.5f
    
    val team1Winner = if (stat.isHigherBetter) stat.team1Value >= stat.team2Value else stat.team1Value <= stat.team2Value
    val team2Winner = !team1Winner
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Label and values
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${stat.team1Value.toInt()}${stat.unit}",
                fontSize = 14.sp,
                fontWeight = if (team1Winner) FontWeight.Bold else FontWeight.Normal,
                color = if (team1Winner) team1Color else colors.textSecondary
            )
            
            Text(
                stat.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            
            Text(
                "${stat.team2Value.toInt()}${stat.unit}",
                fontSize = 14.sp,
                fontWeight = if (team2Winner) FontWeight.Bold else FontWeight.Normal,
                color = if (team2Winner) team2Color else colors.textSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Comparison bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            // Team 1 bar (grows from center to left)
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .height(8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(team1Ratio * animationProgress * 2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    team1Color.copy(alpha = 0.3f),
                                    team1Color
                                )
                            )
                        )
                )
            }
            
            // Team 2 bar (grows from center to right)
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .height(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(team2Ratio * animationProgress * 2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    team2Color,
                                    team2Color.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun LastFiveMatches(
    results: List<String>,
    team1Color: Color,
    team2Color: Color
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.glassBorderHighlight),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Last 5 Meetings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                results.takeLast(5).forEachIndexed { index, result ->
                    val color = when {
                        result == "W1" -> team1Color
                        result == "W2" -> team2Color
                        else -> colors.textTertiary
                    }
                    val label = when {
                        result == "W1" -> "T1"
                        result == "W2" -> "T2"
                        else -> "D"
                    }
                    
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 100L)
                        isVisible = true
                    }
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "result_scale"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(
                                color.copy(alpha = 0.15f),
                                CircleShape
                            )
                            .border(1.5.dp, color.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                LegendItem(color = team1Color, label = "Team 1 Won")
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(color = team2Color, label = "Team 2 Won")
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(color = colors.textTertiary, label = "Draw/NR")
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = colors.textSecondary
        )
    }
}

/**
 * Compact Team Comparison Card for match previews
 */
@Composable
fun CompactTeamComparison(
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color,
    team1Wins: Int,
    team2Wins: Int,
    totalMatches: Int,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val team1Ratio = if (totalMatches > 0) team1Wins.toFloat() / totalMatches else 0.5f
    val team2Ratio = if (totalMatches > 0) team2Wins.toFloat() / totalMatches else 0.5f
    
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "compact_progress"
    )
    
    val colors = BouncerDesignSystem.colors
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.1f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Compare,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Head to Head",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$totalMatches matches",
                    fontSize = 11.sp,
                    color = colors.textTertiary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    team1Name.take(3).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = team1Color
                )
                Text(
                    "$team1Wins - $team2Wins",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )
                Text(
                    team2Name.take(3).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = team2Color
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Mini progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.glassBorder.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(team1Ratio.coerceAtLeast(0.1f) * animationProgress)
                            .fillMaxHeight()
                            .background(team1Color)
                    )
                    Box(
                        modifier = Modifier
                            .weight(team2Ratio.coerceAtLeast(0.1f) * animationProgress)
                            .fillMaxHeight()
                            .background(team2Color)
                    )
                }
            }
        }
    }
}
