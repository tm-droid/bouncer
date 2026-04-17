package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom cricket-themed refresh indicator with spinning ball and stumps
 */
@Composable
fun CricketRefreshIndicator(
    isRefreshing: Boolean,
    pullProgress: Float,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "cricketRefresh")
    
    // Ball spin animation
    val ballRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ballSpin"
    )
    
    // Ball bounce animation
    val ballBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ballBounce"
    )
    
    // Glow pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val actualProgress = if (isRefreshing) 1f else pullProgress.coerceIn(0f, 1f)
    
    Box(
        modifier = modifier
            .size(70.dp)
            .alpha(actualProgress),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Canvas(
            modifier = Modifier
                .size(70.dp)
                .graphicsLayer {
                    alpha = if (isRefreshing) glowPulse else actualProgress * 0.5f
                }
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        hierarchy.secondary.primary.copy(alpha = 0.4f),
                        hierarchy.secondary.primary.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension / 2
            )
        }
        
        // Cricket ball
        CricketBallSpinning(
            rotation = if (isRefreshing) ballRotation else actualProgress * 360f,
            bounceOffset = if (isRefreshing) ballBounce * 8f else 0f,
            size = 40.dp.value
        )
    }
}

@Composable
private fun CricketBallSpinning(
    rotation: Float,
    bounceOffset: Float,
    size: Float
) {
    val density = LocalDensity.current
    
    Canvas(
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer {
                translationY = -bounceOffset * density.density
                rotationZ = rotation
            }
    ) {
        val ballRadius = this.size.minDimension / 2
        val center = this.center
        
        // Main ball (dark red leather)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFC43C29),
                    Color(0xFF8B1E13)
                ),
                center = center.copy(x = center.x - ballRadius * 0.2f, y = center.y - ballRadius * 0.2f)
            ),
            radius = ballRadius,
            center = center
        )
        
        // Ball highlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = center.copy(x = center.x - ballRadius * 0.3f, y = center.y - ballRadius * 0.3f),
                radius = ballRadius * 0.5f
            ),
            radius = ballRadius,
            center = center
        )
        
        // Seam (stitching)
        val seamPath = Path()
        val seamOffset = ballRadius * 0.15f
        
        // Draw seam stitches
        for (i in 0 until 8) {
            val angle = (i * 45f + 22.5f) * (Math.PI / 180f).toFloat()
            val stitchLength = ballRadius * 0.15f
            val stitchRadius = ballRadius * 0.7f
            
            val startX = center.x + cos(angle) * (stitchRadius - stitchLength)
            val startY = center.y + sin(angle) * (stitchRadius - stitchLength)
            val endX = center.x + cos(angle) * (stitchRadius + stitchLength)
            val endY = center.y + sin(angle) * (stitchRadius + stitchLength)
            
            drawLine(
                color = Color(0xFFFAF0E6),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
        
        // Center seam line
        drawCircle(
            color = Color(0xFFFAF0E6).copy(alpha = 0.6f),
            radius = ballRadius * 0.65f,
            center = center,
            style = Stroke(width = 1.5f)
        )
    }
}

/**
 * Stumps animation for wicket-related loading
 */
@Composable
fun StumpsLoadingIndicator(
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stumpsLoading")
    
    // Sequential stumps animation
    val stump1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 0
                -10f at 200
                0f at 400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "stump1"
    )
    
    val stump2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 100
                -10f at 300
                0f at 500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "stump2"
    )
    
    val stump3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 200
                -10f at 400
                0f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "stump3"
    )
    
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Three stumps
        Stump(
            offset = if (isLoading) stump1Offset else 0f,
            bailVisible = isLoading
        )
        Stump(
            offset = if (isLoading) stump2Offset else 0f,
            bailVisible = isLoading
        )
        Stump(
            offset = if (isLoading) stump3Offset else 0f,
            bailVisible = isLoading
        )
    }
}

@Composable
private fun Stump(
    offset: Float,
    bailVisible: Boolean
) {
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(40.dp)
            .graphicsLayer { translationY = offset }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF5DEB3),
                        Color(0xFFDEB887)
                    )
                ),
                RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
            )
    )
}

/**
 * Premium loading spinner with cricket theme
 */
@Composable
fun CricketLoadingSpinner(
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "cricketSpinner")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinRotation"
    )
    
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            val center = this.center
            val radius = this.size.minDimension / 2 - 8f
            
            // Draw 6 dots around (like wickets/stumps)
            for (i in 0 until 6) {
                val angle = (i * 60f) * (Math.PI / 180f).toFloat()
                val dotX = center.x + cos(angle) * radius
                val dotY = center.y + sin(angle) * radius
                val dotAlpha = (1f - (i * 0.15f)).coerceIn(0.3f, 1f)
                
                drawCircle(
                    color = hierarchy.secondary.primary.copy(alpha = dotAlpha),
                    radius = 4f,
                    center = Offset(dotX, dotY)
                )
            }
        }
        
        // Center cricket ball icon
        Canvas(
            modifier = Modifier
                .size((size / 2).dp)
                .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
        ) {
            val ballRadius = this.size.minDimension / 2
            val center = this.center
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                ),
                radius = ballRadius,
                center = center
            )
        }
    }
}

/**
 * Bouncing ball loading animation
 */
@Composable
fun BouncingBallLoader(
    modifier: Modifier = Modifier,
    ballCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncingBalls")
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(ballCount) { index ->
            val bounceOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -15f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 600
                        0f at 0
                        -15f at 200
                        0f at 400
                        0f at 600
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "bounce$index"
            )
            
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer { translationY = bounceOffset }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                        ),
                        CircleShape
                    )
                    .shadow(4.dp, CircleShape, spotColor = hierarchy.secondary.primary.copy(alpha = 0.5f))
            )
        }
    }
}

/**
 * Score updating animation indicator
 */
@Composable
fun ScoreUpdateIndicator(
    isUpdating: Boolean,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "scoreUpdate")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    if (isUpdating) {
        val colors = BouncerDesignSystem.colors
        Surface(
            modifier = modifier
                .graphicsLayer { 
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
            shape = CircleShape,
            color = hierarchy.secondary.primary.copy(alpha = glowAlpha),
            shadowElevation = 8.dp
        ) {
            Text(
                text = "LIVE",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = colors.backgroundBase,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}
