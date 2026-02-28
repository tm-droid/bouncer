package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

/**
 * Batsman data for strike rotation card
 */
data class StrikeRotationData(
    val batsmanName: String,
    val partnerName: String,
    val totalRuns: Int,
    val batsmanRuns: Int,
    val partnerRuns: Int,
    val singles: Int,
    val twos: Int,
    val threes: Int,
    val dotBalls: Int,
    val boundaries: Int,
    val rotationRate: Float, // percentage of balls where they rotated strike
    val averageRunsPerOver: Float
)

/**
 * Premium Strike Rotation Card
 * Shows how well batsmen are rotating the strike
 */
@Composable
fun StrikeRotationCard(
    data: StrikeRotationData,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    
    // Rotation animation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotateAnim"
    )
    
    // Pulse for active batsman
    val pulseFactor by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val rotationColor = when {
        data.rotationRate >= 70 -> SuccessGreen
        data.rotationRate >= 50 -> ShinyGoldDeep
        data.rotationRate >= 30 -> WarningYellow
        else -> WicketRed
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = rotationColor.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STRIKE ROTATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = ShinyGoldDeep
                    )
                    Text(
                        text = "Partnership Analysis",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
                
                // Rotation rate badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = rotationColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${data.rotationRate.toInt()}% rotation",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = rotationColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            // Visual rotation indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2 - 20.dp.toPx()
                    
                    // Track circle
                    drawCircle(
                        color = CardBorder.copy(alpha = 0.2f),
                        radius = radius,
                        center = Offset(centerX, centerY),
                        style = Stroke(8.dp.toPx())
                    )
                    
                    // Rotation progress arc
                    val sweepAngle = 360f * (data.rotationRate / 100f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                rotationColor.copy(alpha = 0.3f),
                                rotationColor,
                                rotationColor.copy(alpha = 0.8f)
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    // Rotating arrow indicator
                    rotate(rotationAngle, pivot = Offset(centerX, centerY)) {
                        val arrowRadius = radius * 0.7f
                        val arrowX = centerX
                        val arrowY = centerY - arrowRadius
                        
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ShinyGoldDeep,
                                    ShinyGoldMid.copy(alpha = 0.5f)
                                ),
                                center = Offset(arrowX, arrowY),
                                radius = 12.dp.toPx()
                            ),
                            radius = 10.dp.toPx(),
                            center = Offset(arrowX, arrowY)
                        )
                        
                        // Trailing effect
                        for (i in 1..3) {
                            val trailAngle = -i * 20f
                            val trailRadian = Math.toRadians((-90 + trailAngle).toDouble())
                            val trailX = centerX + (arrowRadius * cos(trailRadian)).toFloat()
                            val trailY = centerY + (arrowRadius * sin(trailRadian)).toFloat()
                            
                            drawCircle(
                                color = ShinyGoldDeep.copy(alpha = 0.3f - (i * 0.08f)),
                                radius = (8 - i * 2).dp.toPx(),
                                center = Offset(trailX, trailY)
                            )
                        }
                    }
                    
                    // Batsman positions (striker and non-striker)
                    val strikerAngle = Math.toRadians((-90).toDouble())
                    val nonStrikerAngle = Math.toRadians((90).toDouble())
                    val posRadius = radius * 0.4f
                    
                    // Striker position
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SuccessGreen.copy(alpha = 0.8f),
                                SuccessGreen.copy(alpha = 0.3f)
                            )
                        ),
                        radius = 16.dp.toPx() * pulseFactor,
                        center = Offset(
                            centerX + (posRadius * cos(strikerAngle)).toFloat(),
                            centerY + (posRadius * sin(strikerAngle)).toFloat()
                        )
                    )
                    
                    // Non-striker position
                    drawCircle(
                        color = CardBorder.copy(alpha = 0.5f),
                        radius = 14.dp.toPx(),
                        center = Offset(
                            centerX + (posRadius * cos(nonStrikerAngle)).toFloat(),
                            centerY + (posRadius * sin(nonStrikerAngle)).toFloat()
                        )
                    )
                }
                
                // Center stats
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${data.averageRunsPerOver}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "runs/over",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
            
            // Batsman contribution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BatsmanContribution(
                    name = data.batsmanName,
                    runs = data.batsmanRuns,
                    percentage = (data.batsmanRuns.toFloat() / data.totalRuns * 100),
                    isStriker = true,
                    modifier = Modifier.weight(1f)
                )
                BatsmanContribution(
                    name = data.partnerName,
                    runs = data.partnerRuns,
                    percentage = (data.partnerRuns.toFloat() / data.totalRuns * 100),
                    isStriker = false,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Running breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RunTypeItem(
                    label = "Dots",
                    count = data.dotBalls,
                    color = WicketRed.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                RunTypeItem(
                    label = "1s",
                    count = data.singles,
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                RunTypeItem(
                    label = "2s",
                    count = data.twos,
                    color = ShinyGoldDeep,
                    modifier = Modifier.weight(1f)
                )
                RunTypeItem(
                    label = "3s",
                    count = data.threes,
                    color = LiveBlue,
                    modifier = Modifier.weight(1f)
                )
                RunTypeItem(
                    label = "4/6",
                    count = data.boundaries,
                    color = BoundarySix,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BatsmanContribution(
    name: String,
    runs: Int,
    percentage: Float,
    isStriker: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isStriker) SuccessGreen.copy(alpha = 0.1f) else CardBorder.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isStriker) SuccessGreen.copy(alpha = 0.3f) else CardBorder.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isStriker) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SuccessGreen, CircleShape)
                    )
                }
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Text(
                text = "$runs",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStriker) SuccessGreen else TextPrimary
            )
            
            Text(
                text = "${percentage.toInt()}%",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun RunTypeItem(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$count",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = TextSecondary
            )
        }
    }
}

/**
 * Compact strike rotation indicator
 */
@Composable
fun CompactStrikeRotation(
    rotationRate: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        rotationRate >= 70 -> SuccessGreen
        rotationRate >= 50 -> ShinyGoldDeep
        rotationRate >= 30 -> WarningYellow
        else -> WicketRed
    }
    
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🔄",
            fontSize = 10.sp
        )
        Text(
            text = "${rotationRate.toInt()}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
