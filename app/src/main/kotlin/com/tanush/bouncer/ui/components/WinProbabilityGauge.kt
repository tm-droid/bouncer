package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium Technical Win Probability Gauge.
 * Enhanced with 180° Semi-Circle Broadcast Design (from Legacy & code.html).
 */
@Composable
fun WinProbabilityGauge(
    team1Name: String,
    team2Name: String,
    team1Probability: Float, // 0f to 1f
    team1Color: Color = ShinyGoldDeep,
    team2Color: Color = ChartBlue,
    change: String? = "+4.2%",
    isPositive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animatedProbability by animateFloatAsState(
        targetValue = team1Probability,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "probability"
    )
    
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header - Technical Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "WIN PROBABILITY".uppercase(),
                        color = ShinyGoldDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
                
                change?.let {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPositive) SuccessGreen.copy(alpha = 0.1f) else WicketRed.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            null,
                            tint = if (isPositive) SuccessGreen else WicketRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            it,
                            color = if (isPositive) SuccessGreen else WicketRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 180° Broadcast Gauge
            Box(
                modifier = Modifier.size(240.dp, 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val radius = size.width / 2 - strokeWidth
                    val center = Offset(size.width / 2, size.height)
                    
                    // Background track
                    drawArc(
                        color = Color.White.copy(alpha = 0.05f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth, size.height - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Team 1 Arc
                    drawArc(
                        color = team1Color,
                        startAngle = 180f,
                        sweepAngle = 180f * animatedProbability,
                        useCenter = false,
                        topLeft = Offset(strokeWidth, size.height - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Needle
                    val needleAngle = 180f + (180f * animatedProbability)
                    val needleRadians = needleAngle * PI.toFloat() / 180f
                    val needleLength = radius + 8.dp.toPx()
                    val needleEnd = Offset(
                        center.x + needleLength * cos(needleRadians),
                        center.y + needleLength * sin(needleRadians)
                    )
                    
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    drawCircle(color = BlackSurface, radius = 10.dp.toPx(), center = center)
                    drawCircle(color = team1Color, radius = 4.dp.toPx(), center = center)
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "${(animatedProbability * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = team1Name.uppercase(),
                        color = team1Color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Probability Comparison Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProbabilityInfo(name = team1Name, prob = team1Probability, color = team1Color)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProbability)
                            .background(team1Color)
                    )
                }
                
                ProbabilityInfo(name = team2Name, prob = 1 - team1Probability, color = team2Color, isRightAligned = true)
            }
        }
    }
}

@Composable
private fun ProbabilityInfo(name: String, prob: Float, color: Color, isRightAligned: Boolean = false) {
    Column(horizontalAlignment = if (isRightAligned) Alignment.End else Alignment.Start) {
        Text(
            text = name.uppercase(),
            color = if (prob >= 0.5f) color else TextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "${(prob * 100).toInt()}%",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * Mini technical probability badge (Restored for EnhancedMatchCard usage)
 */
@Composable
fun MiniProbabilityBadge(
    team1Name: String,
    team1Probability: Float,
    team1Color: Color = ShinyGoldDeep,
    team2Color: Color = ChartBlue,
    modifier: Modifier = Modifier
) {
    val isTeam1Leading = team1Probability >= 0.5f
    val leadingColor = if (isTeam1Leading) team1Color else team2Color
    val leadingProbability = if (isTeam1Leading) team1Probability else (1 - team1Probability)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(leadingColor.copy(alpha = 0.1f))
            .border(0.5.dp, leadingColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${(leadingProbability * 100).toInt()}%",
                color = leadingColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
