package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.GoldPrimary

@Composable
fun CustomPullRefreshIndicator(
    refreshing: Boolean,
    progress: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshRotation"
    )
    
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "refreshShimmer"
    )
    
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2
            
            if (refreshing) {
                // Spinning arcs when refreshing
                rotate(rotation) {
                    for (i in 0 until 3) {
                        val startAngle = i * 120f
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    hierarchy.secondary.primary.copy(alpha = shimmer),
                                    hierarchy.secondary.primary,
                                    Color.Transparent
                                )
                            ),
                            startAngle = startAngle,
                            sweepAngle = 80f,
                            useCenter = false,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            } else {
                // Progress arc when pulling
                val sweepAngle = 360f * progress.coerceIn(0f, 1f)
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            hierarchy.secondary.secondary,
                            hierarchy.secondary.primary,
                            hierarchy.secondary.primary
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
            
            // Center dot
            drawCircle(
                color = hierarchy.secondary.primary.copy(alpha = if (refreshing) shimmer else 0.8f),
                radius = 4.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }
    }
}

@Composable
fun WaveLoadingIndicator(
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    
    Canvas(modifier = modifier.size(width = 120.dp, height = 40.dp)) {
        val amplitude = size.height / 4
        val wavelength = size.width / 3
        val path = Path()
        
        path.moveTo(0f, size.height / 2)
        
        for (x in 0..size.width.toInt()) {
            val angle = (x / wavelength * 360 + phase) * Math.PI / 180
            val y = size.height / 2 + amplitude * sin(angle).toFloat()
            path.lineTo(x.toFloat(), y)
        }
        
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    hierarchy.secondary.secondary,
                    hierarchy.secondary.primary,
                    hierarchy.secondary.primary,
                    hierarchy.secondary.primary,
                    hierarchy.secondary.secondary
                )
            ),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun DotsLoadingIndicator(
    modifier: Modifier = Modifier,
    dotCount: Int = 3
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Canvas(modifier = modifier.size(width = 60.dp, height = 20.dp)) {
        val dotRadius = 6.dp.toPx()
        val spacing = (size.width - dotRadius * 2 * dotCount) / (dotCount + 1)
        
        for (i in 0 until dotCount) {
            val x = spacing + i * (spacing + dotRadius * 2) + dotRadius
            val y = size.height / 2
            
            // Animate each dot independently with delay
            val phase = (i * 0.15f) % 1f
            val scale = 0.5f + 0.5f * kotlin.math.abs(kotlin.math.sin((phase * Math.PI * 2).toFloat()))
            val alpha = 0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin((phase * Math.PI * 2).toFloat()))
            
            drawCircle(
                color = hierarchy.secondary.primary.copy(alpha = alpha),
                radius = dotRadius * scale,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun PulsingRing(
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )
    
    Canvas(modifier = modifier.size(60.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseRadius = size.minDimension / 3
        
        // Outer ring
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = baseRadius * scale,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Inner ring
        drawCircle(
            color = color.copy(alpha = 1f - alpha),
            radius = baseRadius * (2f - scale),
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Center dot
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}
