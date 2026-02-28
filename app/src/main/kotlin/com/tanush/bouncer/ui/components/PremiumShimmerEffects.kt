package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium shimmer modifier that creates a luxurious moving highlight effect
 */
@Composable
fun Modifier.premiumShimmer(
    durationMillis: Int = 2000,
    color: Color = ShinyGoldDeep,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    
    return this.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    color.copy(alpha = 0.1f),
                    color.copy(alpha = 0.25f),
                    color.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                start = Offset(translateX, 0f),
                end = Offset(translateX + 200f, size.height)
            )
        )
    }
}

/**
 * Radial pulse glow effect for highlighting elements
 */
@Composable
fun Modifier.radialPulseGlow(
    color: Color = ShinyGoldDeep,
    baseAlpha: Float = 0.15f,
    pulseAlpha: Float = 0.35f,
    durationMillis: Int = 2000,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    
    val transition = rememberInfiniteTransition(label = "pulseGlow")
    val alpha by transition.animateFloat(
        initialValue = baseAlpha,
        targetValue = pulseAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    return this.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.5f),
                    Color.Transparent
                ),
                center = center,
                radius = size.maxDimension
            ),
            center = center,
            radius = size.maxDimension
        )
    }
}

/**
 * Animated border that creates a flowing gradient effect
 */
@Composable
fun Modifier.animatedBorder(
    borderWidth: Dp = 1.dp,
    colors: List<Color> = listOf(
        ShinyGoldLight.copy(alpha = 0.5f),
        ShinyGoldDeep.copy(alpha = 0.8f),
        ShinyGoldDark.copy(alpha = 0.3f),
        Color.Transparent,
        ShinyGoldLight.copy(alpha = 0.5f)
    ),
    shape: Shape = RoundedCornerShape(20.dp),
    durationMillis: Int = 3000
): Modifier {
    val transition = rememberInfiniteTransition(label = "borderAnimation")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderAngle"
    )
    
    val angleRad = (angle * PI / 180).toFloat()
    
    return this.border(
        borderWidth,
        Brush.sweepGradient(colors),
        shape
    )
}

/**
 * Sparkle effect that creates random twinkling stars
 */
@Composable
fun SparkleOverlay(
    modifier: Modifier = Modifier,
    particleCount: Int = 15,
    color: Color = ShinyGoldLight
) {
    val particles = remember {
        List(particleCount) {
            SparkleParticle(
                x = Math.random().toFloat(),
                y = Math.random().toFloat(),
                delay = (Math.random() * 2000).toInt(),
                size = (3f + Math.random().toFloat() * 3f)
            )
        }
    }
    
    Box(modifier = modifier) {
        particles.forEach { particle ->
            AnimatedSparkle(
                particle = particle,
                color = color
            )
        }
    }
}

private data class SparkleParticle(
    val x: Float,
    val y: Float,
    val delay: Int,
    val size: Float
)

@Composable
private fun AnimatedSparkle(
    particle: SparkleParticle,
    color: Color
) {
    val transition = rememberInfiniteTransition(label = "sparkle_${particle.x}_${particle.y}")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                delayMillis = particle.delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleAlpha"
    )
    
    val scale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                delayMillis = particle.delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleScale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val centerX = size.width * particle.x
                val centerY = size.height * particle.y
                
                // Draw a 4-pointed star sparkle
                val sparkleSize = particle.size * scale
                
                drawCircle(
                    color = color.copy(alpha = alpha * 0.6f),
                    radius = sparkleSize * 2,
                    center = Offset(centerX, centerY)
                )
                
                // Center bright point
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.9f),
                    radius = sparkleSize * 0.5f,
                    center = Offset(centerX, centerY)
                )
            }
    )
}

/**
 * Premium loading skeleton with shimmer
 */
@Composable
fun PremiumSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    baseColor: Color = Color.White.copy(alpha = 0.05f),
    shimmerColor: Color = ShinyGoldDeep.copy(alpha = 0.15f)
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonTranslate"
    )
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(baseColor)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            shimmerColor,
                            shimmerColor.copy(alpha = shimmerColor.alpha * 1.5f),
                            shimmerColor,
                            Color.Transparent
                        ),
                        start = Offset(translateX, 0f),
                        end = Offset(translateX + 200f, size.height)
                    )
                )
            }
    )
}

/**
 * Circular progress with premium styling
 */
@Composable
fun PremiumCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = CardBorder,
    progressColor: Color = ShinyGoldDeep,
    strokeWidth: Dp = 4.dp,
    size: Dp = 48.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progressAnimation"
    )
    
    val transition = rememberInfiniteTransition(label = "progressGlow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(size * 1.3f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            progressColor.copy(alpha = glowAlpha * animatedProgress * 0.3f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        
        // Progress arc
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
            val sweepAngle = animatedProgress * 360f
            
            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
            
            // Progress
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        progressColor.copy(alpha = 0.3f),
                        progressColor,
                        progressColor.copy(alpha = 0.8f)
                    )
                ),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

/**
 * Breathing glow effect for ambient animations
 */
@Composable
fun Modifier.breathingGlow(
    color: Color = ShinyGoldDeep,
    minAlpha: Float = 0.05f,
    maxAlpha: Float = 0.2f,
    durationMillis: Int = 3000
): Modifier {
    val transition = rememberInfiniteTransition(label = "breathing")
    val alpha by transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )
    
    return this.drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.3f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.3f, size.height * 0.3f),
                radius = size.maxDimension
            )
        )
    }
}

/**
 * Luxurious shine effect that sweeps across the element
 */
@Composable
fun Modifier.luxuryShine(
    durationMillis: Int = 4000,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    
    val transition = rememberInfiniteTransition(label = "shine")
    val offset by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineOffset"
    )
    
    return this.drawWithContent {
        drawContent()
        
        val shineWidth = size.width * 0.3f
        val startX = (size.width + shineWidth * 2) * offset - shineWidth
        
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                startX = startX,
                endX = startX + shineWidth
            )
        )
    }
}

/**
 * Edge glow effect for premium cards
 */
@Composable
fun Modifier.edgeGlow(
    color: Color = ShinyGoldDeep,
    intensity: Float = 0.2f,
    spread: Dp = 30.dp
): Modifier {
    return this.drawBehind {
        val spreadPx = spread.toPx()
        
        // Top edge glow
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = intensity),
                    Color.Transparent
                ),
                startY = 0f,
                endY = spreadPx
            ),
            size = Size(size.width, spreadPx)
        )
        
        // Left edge glow
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    color.copy(alpha = intensity * 0.5f),
                    Color.Transparent
                ),
                startX = 0f,
                endX = spreadPx
            ),
            size = Size(spreadPx, size.height)
        )
    }
}
