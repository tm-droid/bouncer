package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium cricket ball refresh indicator
 */
@Composable
fun CricketBallRefreshIndicator(
    isRefreshing: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ball")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ballRotation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isRefreshing) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        // Glow effect when refreshing
        if (isRefreshing) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    StatusLive.copy(alpha = glowPulse * 0.4f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.maxDimension
                        )
                    }
            )
        }
        
        // Cricket ball
        Canvas(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    if (isRefreshing) {
                        rotationZ = rotation
                    } else {
                        rotationZ = progress * 360f
                    }
                }
        ) {
            val ballColor = StatusLive
            val seamColor = Color.White
            
            // Ball body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ballColor.copy(alpha = 0.9f),
                        ballColor,
                        ballColor.copy(alpha = 0.8f)
                    ),
                    center = Offset(size.width * 0.35f, size.height * 0.35f),
                    radius = size.minDimension
                ),
                radius = size.minDimension / 2
            )
            
            // Seam - main curve
            drawArc(
                color = seamColor,
                startAngle = -30f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
                size = Size(size.width * 0.6f, size.height * 0.6f),
                style = Stroke(2.dp.toPx())
            )
            
            drawArc(
                color = seamColor,
                startAngle = 150f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
                size = Size(size.width * 0.6f, size.height * 0.6f),
                style = Stroke(2.dp.toPx())
            )
            
            // Stitches
            for (i in 0..5) {
                val angle1 = (-25f + i * 10f) * PI.toFloat() / 180f
                val angle2 = (155f + i * 10f) * PI.toFloat() / 180f
                val radius = size.minDimension * 0.3f
                
                // Top seam stitches
                drawLine(
                    color = seamColor.copy(alpha = 0.8f),
                    start = Offset(
                        center.x + cos(angle1) * (radius - 4),
                        center.y + sin(angle1) * (radius - 4)
                    ),
                    end = Offset(
                        center.x + cos(angle1) * (radius + 4),
                        center.y + sin(angle1) * (radius + 4)
                    ),
                    strokeWidth = 1.dp.toPx()
                )
                
                // Bottom seam stitches
                drawLine(
                    color = seamColor.copy(alpha = 0.8f),
                    start = Offset(
                        center.x + cos(angle2) * (radius - 4),
                        center.y + sin(angle2) * (radius - 4)
                    ),
                    end = Offset(
                        center.x + cos(angle2) * (radius + 4),
                        center.y + sin(angle2) * (radius + 4)
                    ),
                    strokeWidth = 1.dp.toPx()
                )
            }
            
            // Shine
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.35f, size.height * 0.35f),
                    radius = size.minDimension * 0.25f
                ),
                radius = size.minDimension * 0.25f,
                center = Offset(size.width * 0.35f, size.height * 0.35f)
            )
        }
    }
}

/**
 * Wicket loading animation
 */
@Composable
fun WicketLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "wicket")
    
    val stump1Height by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stump1"
    )
    
    val stump2Height by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stump2"
    )
    
    val stump3Height by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stump3"
    )
    
    val bailsAngle by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bails"
    )

    Canvas(modifier = modifier.size(48.dp, 56.dp)) {
        val stumpWidth = 6.dp.toPx()
        val stumpSpacing = 10.dp.toPx()
        val baseHeight = 40.dp.toPx()
        val startX = (size.width - stumpWidth * 3 - stumpSpacing * 2) / 2
        
        // Stumps
        listOf(stump1Height, stump2Height, stump3Height).forEachIndexed { index, height ->
            val x = startX + index * (stumpWidth + stumpSpacing)
            val stumpHeight = baseHeight * height
            val y = size.height - stumpHeight
            
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.7f)
                    )
                ),
                topLeft = Offset(x, y),
                size = Size(stumpWidth, stumpHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
        
        // Bails
        val bailWidth = stumpWidth * 2 + stumpSpacing
        val bailHeight = 4.dp.toPx()
        val topY = size.height - baseHeight - bailHeight
        
        rotate(bailsAngle, pivot = Offset(startX + stumpWidth + stumpSpacing / 2, topY)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(startX, topY),
                size = Size(bailWidth, bailHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
        
        rotate(-bailsAngle, pivot = Offset(startX + stumpWidth * 2 + stumpSpacing * 1.5f, topY)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + stumpWidth + stumpSpacing, topY),
                size = Size(bailWidth, bailHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}

/**
 * Premium score flip animation
 */
@Composable
fun AnimatedScoreFlip(
    score: String,
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary,
    fontSize: Int = 28
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    var previousScore by remember { mutableStateOf(score) }
    var isFlipping by remember { mutableStateOf(false) }
    
    val rotation by animateFloatAsState(
        targetValue = if (isFlipping) 180f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        finishedListener = { isFlipping = false },
        label = "flip"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isFlipping) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    LaunchedEffect(score) {
        if (score != previousScore) {
            isFlipping = true
            previousScore = score
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = rotation
                cameraDistance = 12f * density
            }
            .drawBehind {
                if (isFlipping) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        radius = size.maxDimension
                    )
                }
            }
            .background(
                color.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = score,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Black,
            color = color,
            modifier = Modifier.graphicsLayer {
                // Flip the text when past 90 degrees to show correctly
                if (rotation > 90f) {
                    rotationX = 180f
                }
            }
        )
    }
}

/**
 * Run rate visualization with animated bars
 */
@Composable
fun RunRateVisualization(
    currentRunRate: Float,
    requiredRunRate: Float,
    modifier: Modifier = Modifier
) {
    val animatedCurrent by animateFloatAsState(
        targetValue = currentRunRate,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "currentRR"
    )
    
    val animatedRequired by animateFloatAsState(
        targetValue = requiredRunRate,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "requiredRR"
    )
    
    val maxRate = maxOf(currentRunRate, requiredRunRate, 12f)
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    val infiniteTransition = rememberInfiniteTransition(label = "runRate")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "RUN RATE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = hierarchy.secondary.primary
            )
            
            // Current run rate
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Current",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Text(
                        text = String.format("%.2f", animatedCurrent),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.statusSuccess
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.glassBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedCurrent / maxRate)
                            .fillMaxHeight()
                            .drawBehind {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colors.statusSuccess.copy(alpha = glowPulse),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width, size.height / 2),
                                        radius = size.width * 0.3f
                                    )
                                )
                            }
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        colors.statusSuccess.copy(alpha = 0.6f),
                                        colors.statusSuccess
                                    )
                                ),
                                RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
            
            // Required run rate
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Required",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Text(
                        text = String.format("%.2f", animatedRequired),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = if (requiredRunRate > currentRunRate) colors.statusWarning else colors.statusInfo
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.glassBorder)
                ) {
                    val barColor = if (requiredRunRate > currentRunRate) colors.statusWarning else colors.statusInfo
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRequired / maxRate)
                            .fillMaxHeight()
                            .drawBehind {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            barColor.copy(alpha = glowPulse),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width, size.height / 2),
                                        radius = size.width * 0.3f
                                    )
                                )
                            }
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        barColor.copy(alpha = 0.6f),
                                        barColor
                                    )
                                ),
                                RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
            
            // Difference indicator
            val difference = requiredRunRate - currentRunRate
            val differenceText = if (difference > 0) {
                "+${String.format("%.2f", difference)} needed"
            } else {
                "${String.format("%.2f", kotlin.math.abs(difference))} ahead"
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (difference > 0) colors.statusWarning.copy(alpha = 0.12f) else colors.statusSuccess.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (difference > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (difference > 0) colors.statusWarning else colors.statusSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = differenceText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (difference > 0) colors.statusWarning else colors.statusSuccess
                    )
                }
            }
        }
    }
}
