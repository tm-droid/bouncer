package com.tanush.bouncer.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import io.sentry.Sentry

// ==========================================
// BOUNCER – Animated Gradient System
// Modifiers for shimmer, glow, and sweep effects.
// All animations use MaterialEasing.Standard
// and AnimationDuration tokens.
// ==========================================

/**
 * Material 3 Motion Specs for gradient animations
 */
private object GradientEasing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Linear = LinearEasing
}

private object GradientDuration {
    const val Short = 150
    const val Medium = 300
    const val Long = 500
    const val Shimmer = 1800
    const val GradientShift = 3000
    const val SlowRotation = 8000
    const val Ambient = 5000
}

// ==========================================
// BRAND GRADIENT PRESETS
// ==========================================

/**
 * Brand-specific gradient presets for consistent styling
 */
object BrandGradients {
    
    /**
     * Premium gold gradient - primary brand accent
     */
    val GoldGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                GoldLight,
                GoldDark,
                GoldPrimary,
                GoldDark
            )
        )
    
    /**
     * Gold gradient with transparency for overlays
     */
    fun GoldGradientAlpha(alpha: Float = 0.8f): Brush = Brush.linearGradient(
        colors = listOf(
            GoldLight.copy(alpha = alpha),
            GoldDark.copy(alpha = alpha),
            GoldPrimary.copy(alpha = alpha)
        )
    )
    
    /**
     * Horizontal gold gradient for buttons
     */
    val GoldHorizontal: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                GoldPrimary,
                GoldDark,
                GoldDark
            )
        )
    
    /**
     * Live match indicator gradient - vibrant blue
     */
    val LiveGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                LiveBlueBright,
                LiveBlue,
                LiveBlueBright.copy(alpha = 0.8f)
            )
        )
    
    /**
     * Success status gradient - green tones
     */
    val SuccessGradient: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                SuccessGreenBright,
                SuccessGreen,
                SuccessGreenMuted
            )
        )
    
    /**
     * Error/warning gradient - red tones
     */
    val ErrorGradient: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                ErrorRed,
                ErrorRedMuted,
                ErrorRed.copy(alpha = 0.8f)
            )
        )
    
    /**
     * Warning/pending gradient - orange/yellow tones
     */
    val WarningGradient: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                WarningYellow,
                WarningOrange,
                WarningOrangeMuted
            )
        )
    
    /**
     * Premium dark surface gradient
     */
    val DarkSurfaceGradient: Brush
        get() = Brush.verticalGradient(
            colors = listOf(
                BlackElevated,
                BlackSurface,
                BlackBackground
            )
        )
    
    /**
     * Card background gradient with subtle blue tint
     */
    val CardGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                BlackSurface,
                CardDarkBlue.copy(alpha = 0.5f),
                BlackBackground
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
    
    /**
     * Glassmorphism gradient for frosted surfaces
     */
    val GlassGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                GlassWhite,
                GlassSurface,
                GlassDark
            )
        )
    
    /**
     * Chart visualization gradient
     */
    val ChartGradient: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                ChartCyan,
                ChartBlue,
                ChartPurple
            )
        )
    
    /**
     * Neon accent gradient for highlights
     */
    val NeonGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                NeonSuccess,
                ChartCyan,
                LiveBlueBright
            )
        )
    
    /**
     * Boundary six celebration gradient
     */
    val SixerGradient: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                BoundarySix,
                SixerPurple,
                ChartPurple
            )
        )
    
    /**
     * Boundary four celebration gradient
     */
    val FourGradient: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                BoundaryFour,
                ShinyGoldMid,
                WarningOrange
            )
        )
}

// ==========================================
// ANIMATED GRADIENT SHIFT
// ==========================================

/**
 * Animated gradient shift for live match backgrounds
 * Creates dynamic, flowing color transitions
 *
 * @param colors List of colors to animate between
 * @param duration Duration of one complete cycle
 * @param isLive Whether the animation should be active
 */
@Composable
fun Modifier.animatedGradientShift(
    colors: List<Color> = listOf(
        CardDarkBlue,
        BlackSurface,
        CardDarkBlue.copy(alpha = 0.7f),
        BlackBackground
    ),
    duration: Int = GradientDuration.GradientShift,
    isLive: Boolean = true
): Modifier = composed {
    if (!isLive) {
        return@composed this.background(
            Brush.linearGradient(colors = colors)
        )
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "gradientShift")
    
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = GradientEasing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffset"
    )
    
    drawBehind {
        val shiftedStart = Offset(size.width * offset, 0f)
        val shiftedEnd = Offset(size.width * (1f - offset), size.height)
        
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = shiftedStart,
                end = shiftedEnd
            )
        )
    }
}

/**
 * Live match gradient with pulsing effect
 */
@Composable
fun Modifier.liveMatchGradient(
    baseColor: Color = CardDarkBlue,
    accentColor: Color = LiveBlue,
    duration: Int = GradientDuration.GradientShift
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "liveGradient")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration / 2,
                easing = GradientEasing.Emphasized
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )
    
    val position by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = GradientEasing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePosition"
    )
    
    drawBehind {
        // Base dark gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    baseColor,
                    BlackBackground
                )
            )
        )
        
        // Animated accent glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = alpha),
                    accentColor.copy(alpha = alpha * 0.3f),
                    Color.Transparent
                ),
                center = Offset(size.width * position, size.height * 0.3f),
                radius = size.maxDimension * 0.6f
            )
        )
    }
}

// ==========================================
// SHIMMER GRADIENT BORDER
// ==========================================

/**
 * Animated shimmer gradient border for premium card borders
 * Creates a sweeping light effect around the border
 *
 * @param borderWidth Width of the shimmer border
 * @param shimmerColors Colors for the shimmer effect
 * @param shape Shape of the border
 * @param duration Duration of one shimmer cycle
 */
@Composable
fun Modifier.shimmerGradientBorder(
    borderWidth: Dp = 2.dp,
    shimmerColors: List<Color> = listOf(
        Color.Transparent,
        ShinyGoldLight.copy(alpha = 0.5f),
        ShinyGoldMid.copy(alpha = 0.8f),
        ShinyGoldLight.copy(alpha = 0.5f),
        Color.Transparent
    ),
    shape: Shape = RoundedCornerShape(16.dp),
    duration: Int = GradientDuration.Shimmer
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerBorder")
    
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = GradientEasing.Linear
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    
    drawWithCache {
        val shimmerBrush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(size.width * shimmerProgress, 0f),
            end = Offset(size.width * (shimmerProgress + 0.5f), size.height)
        )
        
        onDrawBehind {
            drawRoundRect(
                brush = shimmerBrush,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = borderWidth.toPx())
            )
        }
    }
}

/**
 * Gold shimmer border for premium elements
 */
@Composable
fun Modifier.goldShimmerBorder(
    borderWidth: Dp = 1.5.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    duration: Int = GradientDuration.Shimmer
): Modifier = shimmerGradientBorder(
    borderWidth = borderWidth,
    shimmerColors = listOf(
        GoldHairline,
        ShinyGoldLight.copy(alpha = 0.4f),
        ShinyGoldMid.copy(alpha = 0.7f),
        ShinyGoldDeep.copy(alpha = 0.6f),
        ShinyGoldLight.copy(alpha = 0.4f),
        GoldHairline
    ),
    shape = shape,
    duration = duration
)

/**
 * Live status shimmer border with blue accent
 */
@Composable
fun Modifier.liveShimmerBorder(
    borderWidth: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    duration: Int = GradientDuration.Shimmer
): Modifier = shimmerGradientBorder(
    borderWidth = borderWidth,
    shimmerColors = listOf(
        Color.Transparent,
        LiveBlueBright.copy(alpha = 0.3f),
        LiveBlue.copy(alpha = 0.6f),
        LiveBlueBright.copy(alpha = 0.3f),
        Color.Transparent
    ),
    shape = shape,
    duration = duration
)

// ==========================================
// RADIAL GRADIENT SPOTLIGHT EFFECTS
// ==========================================

/**
 * Radial gradient spotlight for highlighting elements
 *
 * @param color Spotlight color
 * @param alpha Spotlight intensity
 * @param centerX Horizontal center (0-1)
 * @param centerY Vertical center (0-1)
 * @param radiusMultiplier Radius relative to container size
 */
fun Modifier.radialGradientSpotlight(
    color: Color = ShinyGoldMid,
    alpha: Float = 0.2f,
    centerX: Float = 0.5f,
    centerY: Float = 0.3f,
    radiusMultiplier: Float = 0.8f
): Modifier = drawBehind {
    val center = Offset(size.width * centerX, size.height * centerY)
    val radius = size.maxDimension * radiusMultiplier
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.5f),
                color.copy(alpha = alpha * 0.15f),
                Color.Transparent
            ),
            center = center,
            radius = radius
        )
    )
}

/**
 * Multi-point radial gradient for complex lighting effects
 */
fun Modifier.multiPointRadialGradient(
    points: List<GradientPoint> = defaultGradientPoints()
): Modifier = drawBehind {
    points.forEach { point ->
        try {
            val center = Offset(size.width * point.centerX, size.height * point.centerY)
            val radius = size.maxDimension * point.radius
        
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        point.color.copy(alpha = point.alpha),
                        point.color.copy(alpha = point.alpha * 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                )
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

data class GradientPoint(
    val color: Color,
    val centerX: Float,
    val centerY: Float,
    val radius: Float = 0.5f,
    val alpha: Float = 0.15f
)

fun defaultGradientPoints(): List<GradientPoint> = listOf(
    GradientPoint(
        color = ShinyGoldMid,
        centerX = 0.2f,
        centerY = 0.2f,
        radius = 0.5f,
        alpha = 0.12f
    ),
    GradientPoint(
        color = LiveBlueBright,
        centerX = 0.8f,
        centerY = 0.7f,
        radius = 0.4f,
        alpha = 0.08f
    )
)

// ==========================================
// SWEEP GRADIENT FOR CIRCULAR PROGRESS
// ==========================================

/**
 * Sweep gradient for circular progress indicators
 * Creates smooth angular color transitions
 *
 * @param colors Colors for the sweep gradient
 * @param startAngle Starting angle in degrees
 * @param progress Progress value (0-1) for partial sweep
 */
fun Modifier.sweepGradient(
    colors: List<Color> = listOf(
        ShinyGoldDeep,
        ShinyGoldMid,
        ShinyGoldLight,
        ShinyGoldMid,
        ShinyGoldDeep
    ),
    startAngle: Float = 0f,
    progress: Float = 1f
): Modifier = drawBehind {
    val sweepColors = if (progress < 1f) {
        colors.take((colors.size * progress).toInt().coerceAtLeast(2))
    } else {
        colors
    }
    
    rotate(degrees = startAngle) {
        drawCircle(
            brush = Brush.sweepGradient(colors = sweepColors)
        )
    }
}

/**
 * Animated sweep gradient with rotation
 */
@Composable
fun Modifier.animatedSweepGradient(
    colors: List<Color> = listOf(
        ShinyGoldDeep,
        ShinyGoldMid,
        ShinyGoldLight,
        Color.Transparent
    ),
    duration: Int = GradientDuration.SlowRotation
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "sweepRotation")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = GradientEasing.Linear
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )
    
    sweepGradient(
        colors = colors,
        startAngle = rotation
    )
}

/**
 * Conic gradient for pie charts and circular visualizations
 */
fun Modifier.conicGradient(
    segments: List<GradientSegment>,
    centerX: Float = 0.5f,
    centerY: Float = 0.5f
): Modifier = drawBehind {
    val center = Offset(size.width * centerX, size.height * centerY)
    val radius = size.minDimension / 2
    
    segments.forEachIndexed { index, segment ->
        try {
            val startAngle = segments.take(index).sumOf { it.weight.toDouble() }.toFloat() * 360f
            val sweepAngle = segment.weight * 360f
        
            drawArc(
                color = segment.color,
                startAngle = startAngle - 90f,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

data class GradientSegment(
    val color: Color,
    val weight: Float  // 0-1, all weights should sum to 1
)

// ==========================================
// GRADIENT MESH APPROXIMATION
// ==========================================

/**
 * Gradient mesh approximation using multiple overlapping gradients
 * Creates complex, multi-color gradient effects
 *
 * @param meshPoints Control points for the gradient mesh
 */
fun Modifier.gradientMesh(
    meshPoints: List<MeshPoint> = defaultMeshPoints()
): Modifier = drawBehind {
    meshPoints.forEach { point ->
        try {
            val center = Offset(size.width * point.x, size.height * point.y)
            val radius = size.maxDimension * point.influence
        
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        point.color.copy(alpha = point.alpha),
                        point.color.copy(alpha = point.alpha * 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                )
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

data class MeshPoint(
    val x: Float,       // 0-1 position
    val y: Float,       // 0-1 position
    val color: Color,
    val alpha: Float = 0.3f,
    val influence: Float = 0.5f  // Radius multiplier
)

fun defaultMeshPoints(): List<MeshPoint> = listOf(
    MeshPoint(0.1f, 0.1f, ShinyGoldMid, 0.15f, 0.6f),
    MeshPoint(0.9f, 0.2f, LiveBlueBright, 0.1f, 0.5f),
    MeshPoint(0.2f, 0.8f, ChartPurple, 0.08f, 0.4f),
    MeshPoint(0.8f, 0.9f, SuccessGreen, 0.06f, 0.35f)
)

/**
 * Premium gradient mesh for hero backgrounds
 */
fun Modifier.premiumMesh(): Modifier = gradientMesh(
    meshPoints = listOf(
        MeshPoint(0.15f, 0.1f, ShinyGoldLight, 0.12f, 0.5f),
        MeshPoint(0.85f, 0.15f, ShinyGoldMid, 0.1f, 0.45f),
        MeshPoint(0.5f, 0.5f, CardDarkBlue, 0.15f, 0.6f),
        MeshPoint(0.1f, 0.9f, ShinyGoldDeep, 0.08f, 0.4f),
        MeshPoint(0.9f, 0.85f, ShinyGoldBronze, 0.06f, 0.35f)
    )
)

/**
 * Animated gradient mesh with subtle movement
 */
@Composable
fun Modifier.animatedGradientMesh(
    baseMeshPoints: List<MeshPoint> = defaultMeshPoints(),
    movementRange: Float = 0.1f,
    duration: Int = GradientDuration.Ambient
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "meshAnimation")
    
    val offset by infiniteTransition.animateFloat(
        initialValue = -movementRange,
        targetValue = movementRange,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = GradientEasing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "meshOffset"
    )
    
    val animatedPoints = baseMeshPoints.mapIndexed { index, point ->
        val offsetMultiplier = if (index % 2 == 0) 1f else -1f
        point.copy(
            x = (point.x + offset * offsetMultiplier).coerceIn(0f, 1f),
            y = (point.y + offset * 0.5f * offsetMultiplier).coerceIn(0f, 1f)
        )
    }
    
    gradientMesh(animatedPoints)
}

// ==========================================
// NOISE GRADIENT OVERLAY
// ==========================================

/**
 * Noise texture gradient overlay for premium surfaces
 * Adds subtle grain effect
 */
fun Modifier.noiseGradient(
    baseColor: Color = BlackSurface,
    noiseAlpha: Float = 0.05f
): Modifier = drawBehind {
    // Base color
    drawRect(color = baseColor)
    
    // Simulate noise with multiple small gradients
    val noisePoints = 20
    for (i in 0 until noisePoints) {
        try {
            val x = (i * 73.0f) % size.width
            val y = (i * 137.0f) % size.height
        
            drawCircle(
                color = Color.White.copy(alpha = noiseAlpha * ((i % 3 + 1) * 0.33f)),
                radius = 1.5f,
                center = Offset(x, y)
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ==========================================
// LINEAR GRADIENT HELPERS
// ==========================================

/**
 * Diagonal gradient helper
 */
fun Modifier.diagonalGradient(
    colors: List<Color>,
    angle: Float = 45f
): Modifier = drawBehind {
    val radians = Math.toRadians(angle.toDouble()).toFloat()
    val length = size.maxDimension * 1.5f
    val centerX = size.width / 2
    val centerY = size.height / 2
    
    val start = Offset(
        centerX - length * cos(radians) / 2,
        centerY - length * sin(radians) / 2
    )
    val end = Offset(
        centerX + length * cos(radians) / 2,
        centerY + length * sin(radians) / 2
    )
    
    drawRect(
        brush = Brush.linearGradient(
            colors = colors,
            start = start,
            end = end
        )
    )
}

/**
 * Three-point gradient for complex backgrounds
 */
fun Modifier.threePointGradient(
    topColor: Color = CardDarkBlue,
    middleColor: Color = BlackSurface,
    bottomColor: Color = BlackBackground
): Modifier = drawBehind {
    // Top radial gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                topColor.copy(alpha = 0.4f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.5f, 0f),
            radius = size.height * 0.6f
        )
    )
    
    // Middle ambient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                middleColor.copy(alpha = 0.2f),
                Color.Transparent
            )
        )
    )
    
    // Bottom vignette
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                bottomColor.copy(alpha = 0.5f)
            ),
            center = Offset(size.width / 2, size.height),
            radius = size.height * 0.8f
        )
    )
}

// ==========================================
// CONVENIENCE COMPOSABLES
// ==========================================

/**
 * Premium gradient background for screens
 */
@Composable
fun Modifier.premiumScreenGradient(): Modifier = this
    .background(BlackBackground)
    .premiumMesh()
    .diagonalGradient(
        colors = listOf(
            Color.Transparent,
            ShinyGoldDeep.copy(alpha = 0.03f),
            Color.Transparent
        ),
        angle = 135f
    )

/**
 * Live match card gradient background
 */
@Composable
fun Modifier.liveCardGradient(): Modifier = composed {
    this
        .liveMatchGradient()
        .liveShimmerBorder()
}

/**
 * Premium card gradient background
 */
@Composable
fun Modifier.premiumCardGradient(): Modifier = composed {
    this
        .background(BrandGradients.CardGradient)
        .goldShimmerBorder()
        .radialGradientSpotlight(
            color = ShinyGoldLight,
            alpha = 0.08f,
            centerY = 0.2f
        )
}
