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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val infiniteTransition = rememberInfiniteTransition(label = "prediction_animation")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    val scanLineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )
    
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
            containerColor = Color(0xFF0D1117)
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box {
            // AI scan effect background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scanY = size.height * scanLineOffset
                
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ChartBlue.copy(alpha = 0.3f),
                            ChartBlue.copy(alpha = 0.5f),
                            ChartBlue.copy(alpha = 0.3f),
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
                            tint = ChartBlue.copy(alpha = glowAlpha),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AI PREDICTION",
                                color = ChartBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Updated: ${predictionInfo.lastUpdated}",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }
                    
                    // Confidence badge
                    Surface(
                        color = ChartBlue.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Analytics,
                                contentDescription = null,
                                tint = ChartBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${(predictionInfo.confidenceLevel * 100).toInt()}%",
                                color = ChartBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Predicted winner highlight
                Surface(
                    color = ShinyGoldMid.copy(alpha = 0.1f),
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
                            text = "PREDICTED WINNER",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = winningTeam,
                            color = ShinyGoldMid,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${winningProb.toInt()}% probability",
                            color = ShinyGoldMid.copy(alpha = 0.7f),
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
                            color = Color.White.copy(alpha = 0.1f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = strokeWidth)
                        )
                        
                        // Team 1 arc (top)
                        val team1Sweep = 180f * team1Progress
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(ChartBlue.copy(alpha = 0.5f), ChartBlue),
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
                                colors = listOf(ShinyGoldMid.copy(alpha = 0.5f), ShinyGoldMid),
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
                            color = Color.White.copy(alpha = 0.5f),
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
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${predictionInfo.team1WinProbability.toInt()}%",
                            color = ChartBlue,
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
                                text = "TIE/DRAW",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${predictionInfo.tieDrawProbability.toInt()}%",
                                color = Color.White.copy(alpha = 0.7f),
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
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${predictionInfo.team2WinProbability.toInt()}%",
                            color = ShinyGoldMid,
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
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    if (team1Progress > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(team1Progress)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(ChartBlue.copy(alpha = 0.7f), ChartBlue)
                                    )
                                )
                        )
                    }
                    if (predictionInfo.tieDrawProbability > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(predictionInfo.tieDrawProbability / 100f)
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }
                    if (team2Progress > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(team2Progress)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(ShinyGoldMid, ShinyGoldMid.copy(alpha = 0.7f))
                                    )
                                )
                        )
                    }
                }
                
                // Prediction factors
                if (predictionInfo.predictionFactors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "KEY FACTORS",
                        color = Color.White.copy(alpha = 0.5f),
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
                        PredictionTrend.TEAM1_RISING -> "📈 ${predictionInfo.team1Name}'s chances improving"
                        PredictionTrend.TEAM2_RISING -> "📈 ${predictionInfo.team2Name}'s chances improving"
                        PredictionTrend.STABLE -> ""
                    }
                    val trendColor = when (predictionInfo.predictionTrend) {
                        PredictionTrend.TEAM1_RISING -> ChartBlue
                        PredictionTrend.TEAM2_RISING -> ShinyGoldMid
                        PredictionTrend.STABLE -> Color.White
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
    val favoringTeam = if (factor.impact > 0) team1Name else team2Name
    val impactColor = if (factor.impact > 0) ChartBlue else ShinyGoldMid
    val impactStrength = abs(factor.impact)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = factor.name,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        
        // Impact indicator
        Row(
            modifier = Modifier
                .width(80.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            if (factor.impact < 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(impactStrength)
                        .background(ShinyGoldMid)
                )
                Spacer(modifier = Modifier.weight(1f - impactStrength))
            } else {
                Spacer(modifier = Modifier.weight(1f - impactStrength))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(impactStrength)
                        .background(ChartBlue)
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
