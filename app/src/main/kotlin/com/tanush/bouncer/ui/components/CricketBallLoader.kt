package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MEGA UI UPGRADE: Technical Synchronization Loader (v2.0).
 * Replaces the "ball" with a high-fidelity data synchronization event.
 */
@Composable
fun CricketBallLoader(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showText: Boolean = true,
    loadingText: String = "SYNCHRONIZING TELEMETRY..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size + 40.dp)) {
            // High-fidelity background glow
            Box(
                modifier = Modifier
                    .size(size)
                    .blur(30.dp)
                    .background(ShinyGoldDeep.copy(alpha = 0.1f * glowAlpha), CircleShape)
            )

            Canvas(modifier = Modifier.size(size)) {
                // Outer Technical Ring
                drawCircle(
                    color = ShinyGoldDeep.copy(alpha = 0.1f),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                    radius = size.toPx() / 2
                )
                
                // Animated Scanning Arc
                rotate(rotation) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, ShinyGoldDeep, Color.Transparent)),
                        startAngle = 0f,
                        sweepAngle = 120f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        size = this.size
                    )
                }
                
                // Central Core
                drawCircle(
                    color = ShinyGoldDeep.copy(alpha = 0.8f * glowAlpha),
                    radius = 4.dp.toPx(),
                    center = center
                )
            }
        }
        
        if (showText) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = loadingText,
                color = ShinyGoldDeep,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun CricketBallLoadingScreen(
    loadingText: String = "SYNCHRONIZING MATCH ANALYTICS..."
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Background Texture
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(ShinyGoldDeep.copy(alpha = 0.05f), Color.Transparent)),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.5f, size.height * 0.5f)
            )
        }
        
        CricketBallLoader(size = 100.dp, loadingText = loadingText)
    }
}

@Composable
fun MiniCricketBallLoader(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val rotation by rememberInfiniteTransition(label = "mini").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    
    Canvas(modifier = modifier.size(size).rotate(rotation)) {
        drawArc(
            color = ShinyGoldDeep,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
