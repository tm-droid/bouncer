package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GoldLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingRotation")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingRotation"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadingAlpha"
    )
    
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2
        val centerX = this.size.width / 2
        val centerY = this.size.height / 2
        
        // Draw subtle background ring
        drawCircle(
            color = CardBorder.copy(alpha = 0.3f),
            radius = radius - 4.dp.toPx(),
            center = Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Draw rotating arcs with refined gold gradient
        for (i in 0 until 3) {
            val startAngle = rotation + (i * 120f)
            val sweepAngle = 80f
            
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        ShinyGoldMid.copy(alpha = alpha),
                        ShinyGoldDeep.copy(alpha = alpha * 0.8f),
                        ShinyGoldBronze.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    )
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
        
        // Draw pulsing center dot with glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ShinyGoldMid.copy(alpha = alpha),
                    ShinyGoldDeep.copy(alpha = alpha * 0.5f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = 10.dp.toPx()
            ),
            radius = 10.dp.toPx(),
            center = Offset(centerX, centerY)
        )
        
        drawCircle(
            color = ShinyGoldMid.copy(alpha = alpha),
            radius = 5.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

@Composable
fun CricketBallLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ballRotation")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Restart
        ),
        label = "ballRotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ballPulse"
    )
    
    Canvas(modifier = modifier.size(size)) {
        val radius = (this.size.minDimension / 2) * pulse
        val centerX = this.size.width / 2
        val centerY = this.size.height / 2
        
        // Draw outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ShinyGoldDeep.copy(alpha = 0.2f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = radius * 1.4f
            ),
            radius = radius * 1.4f,
            center = Offset(centerX, centerY)
        )
        
        // Draw ball circle with refined gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ShinyGoldLight,
                    ShinyGoldMid,
                    ShinyGoldDeep,
                    ShinyGoldDark
                ),
                center = Offset(centerX - radius * 0.2f, centerY - radius * 0.2f),
                radius = radius * 1.2f
            ),
            radius = radius,
            center = Offset(centerX, centerY)
        )
        
        // Draw seam lines (cricket ball stitching)
        val angleInRadians = Math.toRadians(rotation.toDouble())
        val seamOffset = radius * 0.35f
        
        for (i in 0 until 3) {
            val lineAngle = angleInRadians + (i * Math.PI * 2 / 3)
            val x1 = centerX + cos(lineAngle).toFloat() * seamOffset
            val y1 = centerY + sin(lineAngle).toFloat() * seamOffset
            val x2 = centerX - cos(lineAngle).toFloat() * seamOffset
            val y2 = centerY - sin(lineAngle).toFloat() * seamOffset
            
            drawLine(
                color = BlackBackground.copy(alpha = 0.5f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        // Add highlight reflection
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = Offset(centerX - radius * 0.3f, centerY - radius * 0.3f),
                radius = radius * 0.4f
            ),
            radius = radius * 0.4f,
            center = Offset(centerX - radius * 0.3f, centerY - radius * 0.3f)
        )
    }
}
