package com.tanush.bouncer.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import io.sentry.Sentry

// ==========================================
// BOUNCER – Visual Effects System
// Glassmorphism, shadows, and glow modifiers.
// Kept intentional — no decorative-only effects.
// ==========================================

/**
 * Material 3 Motion Specs - Standard and Emphasized Easing
 */
private object EffectEasing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/**
 * Duration constants following Material 3 motion specs
 */
private object EffectDuration {
    const val Short1 = 50
    const val Short2 = 100
    const val Short3 = 150
    const val Short4 = 200
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium3 = 350
    const val Medium4 = 400
    const val Long1 = 450
    const val Long2 = 500
    const val Long3 = 550
    const val Long4 = 600
    const val GlowPulse = 2000
    const val AmbientLoop = 3000
}

// ==========================================
// GLASSMORPHISM SURFACE
// ==========================================

/**
 * Premium Glassmorphism Surface with adjustable blur, frost gradient, and border
 * Creates an iOS-style frosted glass effect optimized for Android
 *
 * @param modifier Modifier for the surface
 * @param blurRadius Amount of background blur simulation (dp)
 * @param frostIntensity Intensity of the frosted glass overlay (0-1)
 * @param borderWidth Width of the glass border
 * @param borderColor Color of the glass border
 * @param cornerRadius Corner radius of the glass surface
 * @param backgroundColor Base background color for the glass
 * @param content Content to display on the glass surface
 */
@Composable
fun GlassmorphismSurface(
    modifier: Modifier = Modifier,
    blurRadius: Dp = BouncerDesign.GlassBlur,
    frostIntensity: Float = 0.15f,
    borderWidth: Dp = BorderWidth.hairline,
    borderColor: Color = GlassBorder,
    cornerRadius: Dp = CornerRadius.sm,
    backgroundColor: Color = GlassSurface,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    
    Box(
        modifier = modifier
            .background(backgroundColor, shape)
            .glassFrostOverlay(frostIntensity, blurRadius)
            .border(borderWidth, borderColor, shape),
        content = content
    )
}

/**
 * Internal modifier for applying frost overlay effect
 */
private fun Modifier.glassFrostOverlay(
    intensity: Float,
    blurRadius: Dp
): Modifier = drawBehind {
    // Multi-layer frost gradient for realistic glass effect
    val frostGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = intensity * 1.5f),
            Color.White.copy(alpha = intensity * 0.8f),
            Color.White.copy(alpha = intensity * 0.3f)
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height)
    )
    
    drawRect(brush = frostGradient)
    
    // Additional noise simulation layer
    val noiseGradient = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = intensity * 0.1f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.3f, size.height * 0.3f),
        radius = size.minDimension * 0.8f
    )
    drawRect(brush = noiseGradient)
}

/**
 * Modifier extension for applying glassmorphism effect to any composable
 */
fun Modifier.glassmorphism(
    blurRadius: Dp = BouncerDesign.GlassBlur,
    frostIntensity: Float = 0.12f,
    borderWidth: Dp = BorderWidth.hairline,
    borderColor: Color = GlassBorder,
    cornerRadius: Dp = CornerRadius.sm,
    backgroundColor: Color = GlassSurface
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    
    return this
        .background(backgroundColor, shape)
        .glassFrostOverlay(frostIntensity, blurRadius)
        .border(borderWidth, borderColor, shape)
}

// ==========================================
// CHROMATIC ABERRATION EFFECT
// ==========================================

/**
 * Chromatic aberration effect modifier for premium cards
 * Creates a subtle RGB split effect on edges for a premium tech look
 *
 * @param offset Pixel offset for the chromatic split
 * @param intensity Intensity of the effect (0-1)
 */
fun Modifier.chromaticAberration(
    offset: Float = 2f,
    intensity: Float = 0.3f
): Modifier = drawBehind {
    // Red channel shift
    drawRect(
        color = Color.Red.copy(alpha = intensity * 0.15f),
        topLeft = Offset(-offset, 0f),
        size = Size(size.width + offset, size.height)
    )
    
    // Blue channel shift
    drawRect(
        color = Color.Blue.copy(alpha = intensity * 0.15f),
        topLeft = Offset(offset, 0f),
        size = Size(size.width, size.height)
    )
    
    // Cyan highlight on top edge
    drawRect(
        color = Color.Cyan.copy(alpha = intensity * 0.1f),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, 2.dp.toPx())
    )
}

/**
 * Animated chromatic aberration with subtle movement
 */
fun Modifier.animatedChromaticAberration(
    maxOffset: Float = 3f,
    intensity: Float = 0.25f
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "chromatic")
    
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = maxOffset,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = EffectDuration.AmbientLoop,
                easing = EffectEasing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chromaticOffset"
    )
    
    chromaticAberration(offset = offset, intensity = intensity)
}

// ==========================================
// MULTI-LAYER SHADOW
// ==========================================

/**
 * Configuration for individual shadow layers
 */
data class ShadowLayer(
    val color: Color,
    val offsetX: Dp = 0.dp,
    val offsetY: Dp,
    val blurRadius: Dp,
    val spread: Dp = 0.dp
)

/**
 * Multi-layer shadow composable with customizable shadow layers
 * Creates depth with multiple stacked shadows
 *
 * @param modifier Modifier for the container
 * @param layers List of shadow layer configurations
 * @param shape Shape for the shadow casting element
 * @param content Content to display with shadows
 */
@Composable
fun MultiLayerShadow(
    modifier: Modifier = Modifier,
    layers: List<ShadowLayer> = defaultShadowLayers(),
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.multiLayerShadow(layers, shape),
        content = content
    )
}

/**
 * Default premium shadow layers configuration
 */
fun defaultShadowLayers(): List<ShadowLayer> = listOf(
    // Ambient shadow - wide and soft
    ShadowLayer(
        color = Color.Black.copy(alpha = 0.08f),
        offsetY = 8.dp,
        blurRadius = 24.dp
    ),
    // Key shadow - focused and darker
    ShadowLayer(
        color = Color.Black.copy(alpha = 0.12f),
        offsetY = 4.dp,
        blurRadius = 12.dp
    ),
    // Edge shadow - tight and crisp
    ShadowLayer(
        color = Color.Black.copy(alpha = 0.15f),
        offsetY = 2.dp,
        blurRadius = 4.dp
    )
)

/**
 * Gold accent shadow layers for premium elements
 */
fun goldShadowLayers(): List<ShadowLayer> = listOf(
    ShadowLayer(
        color = GoldDark.copy(alpha = 0.15f),
        offsetY = 12.dp,
        blurRadius = 32.dp
    ),
    ShadowLayer(
        color = GoldPrimary.copy(alpha = 0.1f),
        offsetY = 6.dp,
        blurRadius = 16.dp
    ),
    ShadowLayer(
        color = Color.Black.copy(alpha = 0.2f),
        offsetY = 2.dp,
        blurRadius = 6.dp
    )
)

/**
 * Live status shadow layers with blue glow
 */
fun liveShadowLayers(): List<ShadowLayer> = listOf(
    ShadowLayer(
        color = LiveBlue.copy(alpha = 0.2f),
        offsetY = 8.dp,
        blurRadius = 24.dp
    ),
    ShadowLayer(
        color = LiveBlueBright.copy(alpha = 0.1f),
        offsetY = 4.dp,
        blurRadius = 12.dp
    ),
    ShadowLayer(
        color = Color.Black.copy(alpha = 0.15f),
        offsetY = 2.dp,
        blurRadius = 4.dp
    )
)

/**
 * Modifier extension for multi-layer shadows
 */
fun Modifier.multiLayerShadow(
    layers: List<ShadowLayer> = defaultShadowLayers(),
    shape: Shape = RoundedCornerShape(16.dp)
): Modifier = composed {
    val density = LocalDensity.current
    
    drawBehind {
        try {
            layers.forEach { layer ->
                with(density) {
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = layer.color
                        }
                    
                        val frameworkPaint = paint.asFrameworkPaint()
                        frameworkPaint.setShadowLayer(
                            layer.blurRadius.toPx(),
                            layer.offsetX.toPx(),
                            layer.offsetY.toPx(),
                            layer.color.toArgb()
                        )
                    
                        // Draw shadow rect
                        canvas.drawRect(
                            rect = Rect(
                                offset = Offset(layer.offsetX.toPx(), layer.offsetY.toPx()),
                                size = size
                            ),
                            paint = paint
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ==========================================
// DEPTH PARALLAX EFFECT
// ==========================================

/**
 * Depth parallax modifier for scroll-based parallax effects
 * Creates depth perception with layered movement
 *
 * @param scrollOffset Current scroll offset
 * @param parallaxFactor Multiplier for parallax movement (0-1, where 0 = no movement)
 * @param direction Direction of parallax movement
 */
fun Modifier.depthParallax(
    scrollOffset: Float,
    parallaxFactor: Float = 0.5f,
    direction: ParallaxDirection = ParallaxDirection.Vertical
): Modifier = graphicsLayer {
    when (direction) {
        ParallaxDirection.Vertical -> {
            translationY = scrollOffset * parallaxFactor
        }
        ParallaxDirection.Horizontal -> {
            translationX = scrollOffset * parallaxFactor
        }
        ParallaxDirection.Both -> {
            translationX = scrollOffset * parallaxFactor
            translationY = scrollOffset * parallaxFactor
        }
    }
}

enum class ParallaxDirection {
    Vertical,
    Horizontal,
    Both
}

/**
 * Animated depth parallax with automatic subtle movement
 * Creates a floating effect for hero elements
 */
fun Modifier.floatingParallax(
    amplitude: Float = 8f,
    duration: Int = EffectDuration.AmbientLoop
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -amplitude,
        targetValue = amplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = EffectEasing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -amplitude * 0.5f,
        targetValue = amplitude * 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (duration * 1.3).toInt(),
                easing = EffectEasing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatX"
    )
    
    Modifier.graphicsLayer {
        translationY = offsetY
        translationX = offsetX
    }
}

/**
 * 3D tilt parallax based on scroll position
 * Creates perspective depth illusion
 */
fun Modifier.tiltParallax(
    scrollOffset: Float,
    maxTilt: Float = 5f,
    perspective: Float = 1000f
): Modifier = graphicsLayer {
    // Calculate tilt based on scroll
    val normalizedScroll = (scrollOffset / 1000f).coerceIn(-1f, 1f)
    rotationX = normalizedScroll * maxTilt
    
    // Add subtle perspective
    cameraDistance = perspective
}

// ==========================================
// GLOW PULSE EFFECT
// ==========================================

/**
 * Animated glow pulse effect for live indicators
 * Creates attention-grabbing pulsing glow
 *
 * @param glowColor Base color for the glow
 * @param minAlpha Minimum glow alpha
 * @param maxAlpha Maximum glow alpha
 * @param minRadius Minimum glow radius
 * @param maxRadius Maximum glow radius
 * @param duration Pulse animation duration in ms
 */
fun Modifier.glowPulse(
    glowColor: Color = LiveBlue,
    minAlpha: Float = 0.2f,
    maxAlpha: Float = 0.5f,
    minRadius: Float = 0.8f,
    maxRadius: Float = 1.2f,
    duration: Int = EffectDuration.GlowPulse
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = EffectEasing.Emphasized
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val radiusMultiplier by infiniteTransition.animateFloat(
        initialValue = minRadius,
        targetValue = maxRadius,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = EffectEasing.Emphasized
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )
    
    Modifier.drawBehind {
        val glowRadius = size.minDimension * radiusMultiplier
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = alpha),
                    glowColor.copy(alpha = alpha * 0.5f),
                    Color.Transparent
                ),
                center = Offset(size.width / 2, size.height / 2),
                radius = glowRadius
            )
        )
    }
}

/**
 * Gold glow pulse for premium elements
 */
fun Modifier.goldGlowPulse(
    minAlpha: Float = 0.15f,
    maxAlpha: Float = 0.35f,
    duration: Int = EffectDuration.GlowPulse
): Modifier = glowPulse(
    glowColor = ShinyGoldMid,
    minAlpha = minAlpha,
    maxAlpha = maxAlpha,
    duration = duration
)

/**
 * Live indicator glow pulse with blue theme
 */
fun Modifier.liveGlowPulse(
    duration: Int = EffectDuration.GlowPulse
): Modifier = glowPulse(
    glowColor = LiveBlueBright,
    minAlpha = 0.25f,
    maxAlpha = 0.55f,
    duration = duration
)

/**
 * Success glow pulse
 */
fun Modifier.successGlowPulse(
    duration: Int = EffectDuration.GlowPulse
): Modifier = glowPulse(
    glowColor = SuccessGreen,
    minAlpha = 0.2f,
    maxAlpha = 0.45f,
    duration = duration
)

/**
 * Error glow pulse for attention
 */
fun Modifier.errorGlowPulse(
    duration: Int = 1500
): Modifier = glowPulse(
    glowColor = ErrorRed,
    minAlpha = 0.25f,
    maxAlpha = 0.5f,
    duration = duration
)

// ==========================================
// RADIAL SPOTLIGHT EFFECT
// ==========================================

/**
 * Radial spotlight effect for highlighting key elements
 * Creates a focused beam of light effect
 *
 * @param spotlightColor Color of the spotlight
 * @param alpha Intensity of the spotlight
 * @param centerX Horizontal center position (0-1)
 * @param centerY Vertical center position (0-1)
 * @param radius Radius of the spotlight relative to container size
 */
fun Modifier.radialSpotlight(
    spotlightColor: Color = ShinyGoldLight,
    alpha: Float = 0.2f,
    centerX: Float = 0.5f,
    centerY: Float = 0.3f,
    radius: Float = 0.8f
): Modifier = drawBehind {
    val centerOffset = Offset(size.width * centerX, size.height * centerY)
    val spotRadius = size.maxDimension * radius
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                spotlightColor.copy(alpha = alpha),
                spotlightColor.copy(alpha = alpha * 0.4f),
                spotlightColor.copy(alpha = alpha * 0.1f),
                Color.Transparent
            ),
            center = centerOffset,
            radius = spotRadius
        )
    )
}

/**
 * Animated radial spotlight that moves subtly
 */
fun Modifier.animatedSpotlight(
    spotlightColor: Color = ShinyGoldMid,
    alpha: Float = 0.15f,
    duration: Int = EffectDuration.AmbientLoop * 2
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "spotlight")
    
    val centerX by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                easing = EffectEasing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spotlightX"
    )
    
    val centerY by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (duration * 0.8).toInt(),
                easing = EffectEasing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spotlightY"
    )
    
    radialSpotlight(
        spotlightColor = spotlightColor,
        alpha = alpha,
        centerX = centerX,
        centerY = centerY
    )
}

/**
 * Dual spotlight effect for dramatic emphasis
 */
fun Modifier.dualSpotlight(
    primaryColor: Color = ShinyGoldLight,
    secondaryColor: Color = LiveBlueBright,
    primaryAlpha: Float = 0.15f,
    secondaryAlpha: Float = 0.1f
): Modifier = drawBehind {
    // Primary spotlight - top left
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = primaryAlpha),
                primaryColor.copy(alpha = primaryAlpha * 0.3f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.2f, size.height * 0.2f),
            radius = size.maxDimension * 0.7f
        )
    )
    
    // Secondary spotlight - bottom right
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = secondaryAlpha),
                secondaryColor.copy(alpha = secondaryAlpha * 0.3f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.8f, size.height * 0.8f),
            radius = size.maxDimension * 0.5f
        )
    )
}

// ==========================================
// RIBBON HIGHLIGHT EFFECT
// ==========================================

/**
 * Diagonal ribbon highlight effect for premium cards
 */
fun Modifier.ribbonHighlight(
    highlightColor: Color = ShinyGoldLight,
    alpha: Float = 0.08f,
    angle: Float = 45f
): Modifier = drawBehind {
    val radians = Math.toRadians(angle.toDouble()).toFloat()
    val length = size.maxDimension * 2
    
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                highlightColor.copy(alpha = alpha),
                highlightColor.copy(alpha = alpha * 1.5f),
                highlightColor.copy(alpha = alpha),
                Color.Transparent
            ),
            start = Offset(
                size.width / 2 - length * cos(radians) / 2,
                size.height / 2 - length * sin(radians) / 2
            ),
            end = Offset(
                size.width / 2 + length * cos(radians) / 2,
                size.height / 2 + length * sin(radians) / 2
            )
        )
    )
}

// ==========================================
// EDGE HIGHLIGHT EFFECT
// ==========================================

/**
 * Premium edge highlight for elevated surfaces
 */
fun Modifier.edgeHighlight(
    highlightColor: Color = Color.White,
    alpha: Float = 0.1f,
    position: EdgePosition = EdgePosition.Top
): Modifier = drawBehind {
    val gradientBrush = when (position) {
            EdgePosition.Top -> Brush.verticalGradient(
                colors = listOf(
                    highlightColor.copy(alpha = alpha),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.15f
            )
            EdgePosition.Bottom -> Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    highlightColor.copy(alpha = alpha)
                ),
                startY = size.height * 0.85f,
                endY = size.height
            )
            EdgePosition.Left -> Brush.horizontalGradient(
                colors = listOf(
                    highlightColor.copy(alpha = alpha),
                    Color.Transparent
                ),
                startX = 0f,
                endX = size.width * 0.15f
            )
            EdgePosition.Right -> Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    highlightColor.copy(alpha = alpha)
                ),
                startX = size.width * 0.85f,
                endX = size.width
            )
    }
    
    drawRect(brush = gradientBrush)
}

enum class EdgePosition {
    Top,
    Bottom,
    Left,
    Right
}

// ==========================================
// INNER SHADOW EFFECT
// ==========================================

/**
 * Inner shadow effect for recessed elements
 */
fun Modifier.innerShadow(
    shadowColor: Color = Color.Black,
    alpha: Float = 0.15f,
    blur: Dp = 8.dp,
    spread: Dp = 2.dp
): Modifier = composed {
    val density = LocalDensity.current
    
    Modifier.drawBehind {
        try {
            with(density) {
                // Top inner shadow
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            shadowColor.copy(alpha = alpha),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = blur.toPx()
                    )
                )
            
                // Left inner shadow
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            shadowColor.copy(alpha = alpha * 0.7f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = blur.toPx() * 0.5f
                    )
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ==========================================
// CONVENIENCE EXTENSIONS
// ==========================================

/**
 * Premium card effect combining multiple effects
 */
fun Modifier.premiumCardEffect(
    isHighlighted: Boolean = false,
    isLive: Boolean = false
): Modifier = composed {
    this
        .multiLayerShadow(
            layers = when {
                    isLive -> liveShadowLayers()
                    isHighlighted -> goldShadowLayers()
                    else -> defaultShadowLayers()
            }
        )
        .then(
            if (isHighlighted) {
                Modifier.radialSpotlight(
                    spotlightColor = ShinyGoldMid,
                    alpha = 0.1f
                )
            } else {
                Modifier
            }
        )
        .then(
            if (isLive) {
                Modifier.liveGlowPulse()
            } else {
                Modifier
            }
        )
}

/**
 * Hero element effect with floating and spotlight
 */
fun Modifier.heroEffect(): Modifier = composed {
    try {
        this
            .floatingParallax(amplitude = 6f)
            .animatedSpotlight(
                spotlightColor = ShinyGoldLight,
                alpha = 0.12f
            )
            .multiLayerShadow(goldShadowLayers())
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
