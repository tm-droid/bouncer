package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlin.math.*

/**
 * Over-by-over comparison card showing both innings side by side
 */

data class OverByOverComparisonInfo(
    val team1Name: String,
    val team2Name: String,
    val team1Scores: List<Int>, // Score after each over
    val team2Scores: List<Int>,
    val currentOver: Int = 0,
    val maxOvers: Int = 20,
    val team1Color: Color = StatusInfo,
    val team2Color: Color = GoldPrimary,
    val highlightOvers: List<Int> = emptyList() // Overs with significant events
)

@Composable
fun OverByOverComparisonCard(
    comparisonInfo: OverByOverComparisonInfo,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "comparison_animation")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    val pathProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label = "path_progress"
    )
    
    // Calculate max score for scaling
    val maxScore = maxOf(
        comparisonInfo.team1Scores.maxOrNull() ?: 0,
        comparisonInfo.team2Scores.maxOrNull() ?: 0
    ).coerceAtLeast(1)
    
    // Calculate current advantage
    val team1Current = comparisonInfo.team1Scores.lastOrNull() ?: 0
    val team2Current = comparisonInfo.team2Scores.lastOrNull() ?: 0
    val advantage = team1Current - team2Current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = BouncerThemeV2.colors.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Compare,
                        contentDescription = null,
                        tint = hierarchy.secondary.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Over-by-Over Comparison",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Current advantage badge
                val advantageColor = when {
                    advantage > 0 -> comparisonInfo.team1Color
                    advantage < 0 -> comparisonInfo.team2Color
                    else -> TextSecondary
                }
                
                Surface(
                    color = advantageColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when {
                            advantage > 0 -> "+$advantage"
                            advantage < 0 -> "$advantage"
                            else -> "TIED"
                        },
                        color = advantageColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Team legends
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TeamLegend(
                    teamName = comparisonInfo.team1Name,
                    score = team1Current,
                    color = comparisonInfo.team1Color,
                    alignment = Alignment.Start
                )
                TeamLegend(
                    teamName = comparisonInfo.team2Name,
                    score = team2Current,
                    color = comparisonInfo.team2Color,
                    alignment = Alignment.End
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Comparison chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val graphWidth = size.width
                    val graphHeight = size.height
                    val padding = 30f
                    
                    // Draw grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = padding + (graphHeight - 2 * padding) * i / gridLines
                        drawLine(
                            color = GlassBorder,
                            start = Offset(padding, y),
                            end = Offset(graphWidth - padding, y),
                            strokeWidth = 1f
                        )
                    }
                    
                    // Draw over markers
                    val totalOvers = comparisonInfo.maxOvers
                    val oversToShow = minOf(5, totalOvers)
                    for (i in 0..oversToShow) {
                        val over = (totalOvers * i) / oversToShow
                        val x = padding + (graphWidth - 2 * padding) * i / oversToShow
                        
                        // Vertical grid line
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(x, padding),
                            end = Offset(x, graphHeight - padding),
                            strokeWidth = 1f
                        )
                    }
                    
                    // Draw team 1 line
                    if (comparisonInfo.team1Scores.isNotEmpty()) {
                        val team1Path = Path()
                        val pointsToShow = (comparisonInfo.team1Scores.size * pathProgress).toInt().coerceAtLeast(1)
                        
                        comparisonInfo.team1Scores.take(pointsToShow).forEachIndexed { index, score ->
                            val x = padding + (graphWidth - 2 * padding) * index / (comparisonInfo.maxOvers - 1).coerceAtLeast(1)
                            val y = graphHeight - padding - (graphHeight - 2 * padding) * score / maxScore
                            
                            if (index == 0) {
                                team1Path.moveTo(x, y)
                            } else {
                                team1Path.lineTo(x, y)
                            }
                        }
                        
                        drawPath(
                            path = team1Path,
                            color = comparisonInfo.team1Color,
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        
                        // Draw points
                        comparisonInfo.team1Scores.take(pointsToShow).forEachIndexed { index, score ->
                            val x = padding + (graphWidth - 2 * padding) * index / (comparisonInfo.maxOvers - 1).coerceAtLeast(1)
                            val y = graphHeight - padding - (graphHeight - 2 * padding) * score / maxScore
                            
                            drawCircle(
                                color = comparisonInfo.team1Color,
                                radius = if (index in comparisonInfo.highlightOvers) 8f else 4f,
                                center = Offset(x, y)
                            )
                            
                            if (index in comparisonInfo.highlightOvers) {
                                drawCircle(
                                    color = comparisonInfo.team1Color.copy(alpha = 0.3f * glowAlpha),
                                    radius = 12f,
                                    center = Offset(x, y)
                                )
                            }
                        }
                    }
                    
                    // Draw team 2 line
                    if (comparisonInfo.team2Scores.isNotEmpty()) {
                        val team2Path = Path()
                        val pointsToShow = (comparisonInfo.team2Scores.size * pathProgress).toInt().coerceAtLeast(1)
                        
                        comparisonInfo.team2Scores.take(pointsToShow).forEachIndexed { index, score ->
                            val x = padding + (graphWidth - 2 * padding) * index / (comparisonInfo.maxOvers - 1).coerceAtLeast(1)
                            val y = graphHeight - padding - (graphHeight - 2 * padding) * score / maxScore
                            
                            if (index == 0) {
                                team2Path.moveTo(x, y)
                            } else {
                                team2Path.lineTo(x, y)
                            }
                        }
                        
                        drawPath(
                            path = team2Path,
                            color = comparisonInfo.team2Color,
                            style = Stroke(
                                width = 3f, 
                                cap = StrokeCap.Round, 
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                            )
                        )
                        
                        // Draw points
                        comparisonInfo.team2Scores.take(pointsToShow).forEachIndexed { index, score ->
                            val x = padding + (graphWidth - 2 * padding) * index / (comparisonInfo.maxOvers - 1).coerceAtLeast(1)
                            val y = graphHeight - padding - (graphHeight - 2 * padding) * score / maxScore
                            
                            drawCircle(
                                color = comparisonInfo.team2Color,
                                radius = if (index in comparisonInfo.highlightOvers) 8f else 4f,
                                center = Offset(x, y)
                            )
                        }
                    }
                    
                    // Draw current over indicator
                    if (comparisonInfo.currentOver > 0 && comparisonInfo.currentOver < comparisonInfo.maxOvers) {
                        val currentX = padding + (graphWidth - 2 * padding) * comparisonInfo.currentOver / (comparisonInfo.maxOvers - 1)
                        
                        drawLine(
                            color = LiveBlue.copy(alpha = glowAlpha),
                            start = Offset(currentX, padding),
                            end = Offset(currentX, graphHeight - padding),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Over labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = "${comparisonInfo.maxOvers / 2}",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = "${comparisonInfo.maxOvers} overs",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Phase comparison
            if (comparisonInfo.team1Scores.size >= 6 && comparisonInfo.team2Scores.size >= 6) {
                PhaseComparisonRow(comparisonInfo = comparisonInfo)
            }
        }
    }
}

@Composable
private fun TeamLegend(
    teamName: String,
    score: Int,
    color: Color,
    alignment: Alignment.Horizontal
) {
    Column(horizontalAlignment = alignment) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = teamName,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "$score",
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PhaseComparisonRow(comparisonInfo: OverByOverComparisonInfo) {
    // Calculate phase scores (Powerplay: 1-6, Middle: 7-15, Death: 16-20 for T20)
    val powerplayOvers = 6
    val middleOvers = 15
    
    val team1Powerplay = comparisonInfo.team1Scores.getOrNull(powerplayOvers - 1) ?: 0
    val team2Powerplay = comparisonInfo.team2Scores.getOrNull(powerplayOvers - 1) ?: 0
    
    val team1Middle = (comparisonInfo.team1Scores.getOrNull(middleOvers - 1) ?: 0) - team1Powerplay
    val team2Middle = (comparisonInfo.team2Scores.getOrNull(middleOvers - 1) ?: 0) - team2Powerplay
    
    val team1Death = (comparisonInfo.team1Scores.lastOrNull() ?: 0) - 
        (comparisonInfo.team1Scores.getOrNull(middleOvers - 1) ?: 0)
    val team2Death = (comparisonInfo.team2Scores.lastOrNull() ?: 0) - 
        (comparisonInfo.team2Scores.getOrNull(middleOvers - 1) ?: 0)
    
    Column {
        Text(
            text = "PHASE COMPARISON",
            color = TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PhaseBox(
                phase = "Powerplay",
                overs = "1-6",
                team1Score = team1Powerplay,
                team2Score = team2Powerplay,
                team1Color = comparisonInfo.team1Color,
                team2Color = comparisonInfo.team2Color
            )
            PhaseBox(
                phase = "Middle",
                overs = "7-15",
                team1Score = team1Middle,
                team2Score = team2Middle,
                team1Color = comparisonInfo.team1Color,
                team2Color = comparisonInfo.team2Color
            )
            PhaseBox(
                phase = "Death",
                overs = "16-20",
                team1Score = team1Death,
                team2Score = team2Death,
                team1Color = comparisonInfo.team1Color,
                team2Color = comparisonInfo.team2Color
            )
        }
    }
}

@Composable
private fun PhaseBox(
    phase: String,
    overs: String,
    team1Score: Int,
    team2Score: Int,
    team1Color: Color,
    team2Color: Color
) {
    val winner = when {
        team1Score > team2Score -> team1Color
        team2Score > team1Score -> team2Color
        else -> TextSecondary
    }
    
    Surface(
        color = winner.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = phase,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "($overs)",
                color = TextSecondary,
                fontSize = 9.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$team1Score",
                    color = team1Color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " - ",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = "$team2Score",
                    color = team2Color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
