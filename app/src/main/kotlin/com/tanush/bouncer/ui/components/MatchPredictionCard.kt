package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * AI Match Prediction card with animated probability display
 */

data class MatchPredictionInfo(
    val team1Name: String,
    val team2Name: String,
    val team1WinProbability: Float,
    val team2WinProbability: Float,
    val tieDrawProbability: Float = 0f,
    val confidenceLevel: Float = 0.85f,
    val predictionFactors: List<PredictionFactor> = emptyList(),
    val lastUpdated: String = "Just now",
    val predictedWinner: String = "",
    val predictionTrend: PredictionTrend = PredictionTrend.STABLE
)

data class PredictionFactor(
    val name: String,
    val impact: Float, // -1 to 1, negative favors team2, positive favors team1
    val description: String = ""
)

enum class PredictionTrend {
    TEAM1_RISING,
    TEAM2_RISING,
    STABLE
}

@Composable
fun MatchPredictionCard(
    predictionInfo: MatchPredictionInfo,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    // Ambient loop removed: motion in this app reports a state change, never idles.
    // See BouncerCard's docs for the rule.
    val pulseScale = 1f
    
    val glowAlpha = 0.55f
    
    val scanLineOffset = 0f
    
    val team1Progress by animateFloatAsState(
        targetValue = predictionInfo.team1WinProbability / 100f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "team1_progress"
    )
    
    val team2Progress by animateFloatAsState(
        targetValue = predictionInfo.team2WinProbability / 100f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "team2_progress"
    )
    
    val winningTeam = if (predictionInfo.team1WinProbability > predictionInfo.team2WinProbability) 
        predictionInfo.team1Name else predictionInfo.team2Name
    val winningProb = maxOf(predictionInfo.team1WinProbability, predictionInfo.team2WinProbability)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundBase
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            // AI scan effect background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scanY = size.height * scanLineOffset
                
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.statusInfo.copy(alpha = 0.3f),
                            colors.statusInfo.copy(alpha = 0.5f),
                            colors.statusInfo.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, scanY),
                    end = Offset(size.width, scanY),
                    strokeWidth = 3f
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = colors.statusInfo.copy(alpha = glowAlpha),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.common_ai_prediction),
                                color = colors.statusInfo,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Updated: ${predictionInfo.lastUpdated}",
                                color = colors.textTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    
                    // Confidence badge
                    Surface(
                        color = colors.statusInfo.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Analytics,
                                contentDescription = null,
                                tint = colors.statusInfo,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${(predictionInfo.confidenceLevel * 100).toInt()}%",
                                color = colors.statusInfo,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Predicted winner highlight
                Surface(
                    color = hierarchy.secondary.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(pulseScale)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.match_predicted_winner),
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = winningTeam,
                            color = hierarchy.secondary.primary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${winningProb.toInt()}% probability",
                            color = hierarchy.secondary.primary.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Probability comparison visualization
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = minOf(size.width, size.height) / 2 - 20f
                        val strokeWidth = 16f
                        
                        // Background circle
                        drawCircle(
                            color = colors.glassSurface,
                            radius = radius,
                            center = center,
                            style = Stroke(width = strokeWidth)
                        )
                        
                        // Team 1 arc (top)
                        val team1Sweep = 180f * team1Progress
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(colors.statusInfo.copy(alpha = 0.5f), colors.statusInfo),
                                center = center
                            ),
                            startAngle = 180f,
                            sweepAngle = team1Sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        
                        // Team 2 arc (bottom)
                        val team2Sweep = 180f * team2Progress
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(hierarchy.secondary.primary.copy(alpha = 0.5f), hierarchy.secondary.primary),
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = team2Sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    
                    // Center content
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "VS",
                            color = colors.textTertiary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Team probability bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Team 1
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = predictionInfo.team1Name,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${predictionInfo.team1WinProbability.toInt()}%",
                            color = colors.statusInfo,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Tie/Draw (if applicable)
                    if (predictionInfo.tieDrawProbability > 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.match_tie_draw),
                                color = colors.textTertiary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${predictionInfo.tieDrawProbability.toInt()}%",
                                color = colors.textSecondary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Team 2
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = predictionInfo.team2Name,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${predictionInfo.team2WinProbability.toInt()}%",
                            color = hierarchy.secondary.primary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Probability bar
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.glassSurface)
                ) {
                    if (team1Progress > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(team1Progress)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(colors.statusInfo.copy(alpha = 0.7f), colors.statusInfo)
                                    )
                                )
                        )
                    }
                    if (predictionInfo.tieDrawProbability > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(predictionInfo.tieDrawProbability / 100f)
                                .background(colors.glassBorder)
                        )
                    }
                    if (team2Progress > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(team2Progress)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary.copy(alpha = 0.7f))
                                    )
                                )
                        )
                    }
                }
                
                // Prediction factors
                if (predictionInfo.predictionFactors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = colors.glassSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.match_key_factors),
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    predictionInfo.predictionFactors.take(4).forEach { factor ->
                        PredictionFactorRow(
                            factor = factor,
                            team1Name = predictionInfo.team1Name,
                            team2Name = predictionInfo.team2Name
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Trend indicator
                if (predictionInfo.predictionTrend != PredictionTrend.STABLE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val trendText = when (predictionInfo.predictionTrend) {
                        PredictionTrend.TEAM1_RISING -> "${predictionInfo.team1Name}'s chances improving"
                        PredictionTrend.TEAM2_RISING -> "${predictionInfo.team2Name}'s chances improving"
                        PredictionTrend.STABLE -> ""
                    }
                    val trendColor = when (predictionInfo.predictionTrend) {
                        PredictionTrend.TEAM1_RISING -> colors.statusInfo
                        PredictionTrend.TEAM2_RISING -> hierarchy.secondary.primary
                        PredictionTrend.STABLE -> colors.textPrimary
                    }
                    
                    Surface(
                        color = trendColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = trendText,
                            color = trendColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionFactorRow(
    factor: PredictionFactor,
    team1Name: String,
    team2Name: String
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val favoringTeam = if (factor.impact > 0) team1Name else team2Name
    val impactColor = if (factor.impact > 0) colors.statusInfo else hierarchy.secondary.primary
    val impactStrength = abs(factor.impact).coerceIn(Float.MIN_VALUE, 1f - Float.MIN_VALUE)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = factor.name,
            color = colors.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        
        // Impact indicator
        Row(
            modifier = Modifier
                .width(80.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.glassSurface)
        ) {
            if (factor.impact < 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(impactStrength)
                        .background(hierarchy.secondary.primary)
                )
                Spacer(modifier = Modifier.weight(1f - impactStrength))
            } else {
                Spacer(modifier = Modifier.weight(1f - impactStrength))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(impactStrength)
                        .background(colors.statusInfo)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = favoringTeam.take(3).uppercase(),
            color = impactColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.End
        )
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MatchPredictionCardPreview() {
    com.tanush.bouncer.ui.theme.BouncerPreviewTheme {
        MatchPredictionCard(
            predictionInfo = MatchPredictionInfo(
                team1Name = "India",
                team2Name = "Australia",
                team1WinProbability = 0.62f,
                team2WinProbability = 0.33f,
                tieDrawProbability = 0.05f,
                confidenceLevel = 0.88f,
                predictionFactors = listOf(
                    PredictionFactor("Home Advantage", 0.3f, "India playing at home"),
                    PredictionFactor("Recent Form", 0.2f, "Won 4 of last 5")
                ),
                lastUpdated = "2 min ago",
                predictedWinner = "India",
                predictionTrend = PredictionTrend.TEAM1_RISING
            )
        )
    }
}
