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

private object EffectEasing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

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

@Composable
private fun reversingFloat(
    label: String,
    from: Float,
    to: Float,
    duration: Int,
    easing: Easing = EffectEasing.Standard
): State<Float> {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = easing),
            repeatMode = RepeatMode.Reverse
        ),
        label = label
    )
}

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

private fun Modifier.glassFrostOverlay(
    intensity: Float,
    blurRadius: Dp
): Modifier = drawBehind {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = intensity * 1.5f),
                Color.White.copy(alpha = intensity * 0.8f),
                Color.White.copy(alpha = intensity * 0.3f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = intensity * 0.1f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.3f, size.height * 0.3f),
            radius = size.minDimension * 0.8f
        )
    )
}

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

fun Modifier.chromaticAberration(
    offset: Float = 2f,
    intensity: Float = 0.3f
): Modifier = drawBehind {
    drawRect(
        color = Color.Red.copy(alpha = intensity * 0.15f),
        topLeft = Offset(-offset, 0f),
        size = Size(size.width + offset, size.height)
    )
    drawRect(
        color = Color.Blue.copy(alpha = intensity * 0.15f),
        topLeft = Offset(offset, 0f),
        size = Size(size.width, size.height)
    )
    drawRect(
        color = Color.Cyan.copy(alpha = intensity * 0.1f),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, 2.dp.toPx())
    )
}

fun Modifier.animatedChromaticAberration(
    maxOffset: Float = 3f,
    intensity: Float = 0.25f
): Modifier = composed {
    val offset by reversingFloat("chromaticOffset", 0f, maxOffset, EffectDuration.AmbientLoop)
    chromaticAberration(offset = offset, intensity = intensity)
}

data class ShadowLayer(
    val color: Color,
    val offsetX: Dp = 0.dp,
    val offsetY: Dp,
    val blurRadius: Dp,
    val spread: Dp = 0.dp
)

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

fun defaultShadowLayers(): List<ShadowLayer> = listOf(
    ShadowLayer(Color.Black.copy(alpha = 0.08f), offsetY = 8.dp, blurRadius = 24.dp),
    ShadowLayer(Color.Black.copy(alpha = 0.12f), offsetY = 4.dp, blurRadius = 12.dp),
    ShadowLayer(Color.Black.copy(alpha = 0.15f), offsetY = 2.dp, blurRadius = 4.dp)
)

fun goldShadowLayers(): List<ShadowLayer> = listOf(
    ShadowLayer(GoldDark.copy(alpha = 0.15f), offsetY = 12.dp, blurRadius = 32.dp),
    ShadowLayer(GoldPrimary.copy(alpha = 0.1f), offsetY = 6.dp, blurRadius = 16.dp),
    ShadowLayer(Color.Black.copy(alpha = 0.2f), offsetY = 2.dp, blurRadius = 6.dp)
)

fun liveShadowLayers(): List<ShadowLayer> = listOf(
    ShadowLayer(LiveBlue.copy(alpha = 0.2f), offsetY = 8.dp, blurRadius = 24.dp),
    ShadowLayer(LiveBlueBright.copy(alpha = 0.1f), offsetY = 4.dp, blurRadius = 12.dp),
    ShadowLayer(Color.Black.copy(alpha = 0.15f), offsetY = 2.dp, blurRadius = 4.dp)
)

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
                        val paint = Paint().apply { color = layer.color }
                        paint.asFrameworkPaint().setShadowLayer(
                            layer.blurRadius.toPx(),
                            layer.offsetX.toPx(),
                            layer.offsetY.toPx(),
                            layer.color.toArgb()
                        )
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

fun Modifier.depthParallax(
    scrollOffset: Float,
    parallaxFactor: Float = 0.5f,
    direction: ParallaxDirection = ParallaxDirection.Vertical
): Modifier = graphicsLayer {
    val delta = scrollOffset * parallaxFactor
    when (direction) {
        ParallaxDirection.Vertical -> translationY = delta
        ParallaxDirection.Horizontal -> translationX = delta
        ParallaxDirection.Both -> {
            translationX = delta
            translationY = delta
        }
    }
}

enum class ParallaxDirection { Vertical, Horizontal, Both }

fun Modifier.floatingParallax(
    amplitude: Float = 8f,
    duration: Int = EffectDuration.AmbientLoop
): Modifier = composed {
    val offsetY by reversingFloat("floatY", -amplitude, amplitude, duration)
    val offsetX by reversingFloat(
        "floatX",
        -amplitude * 0.5f,
        amplitude * 0.5f,
        (duration * 1.3).toInt()
    )
    Modifier.graphicsLayer {
        translationY = offsetY
        translationX = offsetX
    }
}

fun Modifier.tiltParallax(
    scrollOffset: Float,
    maxTilt: Float = 5f,
    perspective: Float = 1000f
): Modifier = graphicsLayer {
    val normalizedScroll = (scrollOffset / 1000f).coerceIn(-1f, 1f)
    rotationX = normalizedScroll * maxTilt
    cameraDistance = perspective
}

fun Modifier.glowPulse(
    glowColor: Color = LiveBlue,
    minAlpha: Float = 0.2f,
    maxAlpha: Float = 0.5f,
    minRadius: Float = 0.8f,
    maxRadius: Float = 1.2f,
    duration: Int = EffectDuration.GlowPulse
): Modifier = composed {
    val alpha by reversingFloat("glowAlpha", minAlpha, maxAlpha, duration, EffectEasing.Emphasized)
    val radiusMultiplier by reversingFloat("glowRadius", minRadius, maxRadius, duration, EffectEasing.Emphasized)
    Modifier.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = alpha),
                    glowColor.copy(alpha = alpha * 0.5f),
                    Color.Transparent
                ),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.minDimension * radiusMultiplier
            )
        )
    }
}

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

fun Modifier.liveGlowPulse(
    duration: Int = EffectDuration.GlowPulse
): Modifier = glowPulse(
    glowColor = LiveBlueBright,
    minAlpha = 0.25f,
    maxAlpha = 0.55f,
    duration = duration
)

fun Modifier.successGlowPulse(
    duration: Int = EffectDuration.GlowPulse
): Modifier = glowPulse(
    glowColor = SuccessGreen,
    minAlpha = 0.2f,
    maxAlpha = 0.45f,
    duration = duration
)

fun Modifier.errorGlowPulse(
    duration: Int = 1500
): Modifier = glowPulse(
    glowColor = ErrorRed,
    minAlpha = 0.25f,
    maxAlpha = 0.5f,
    duration = duration
)

fun Modifier.radialSpotlight(
    spotlightColor: Color = ShinyGoldLight,
    alpha: Float = 0.2f,
    centerX: Float = 0.5f,
    centerY: Float = 0.3f,
    radius: Float = 0.8f
): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                spotlightColor.copy(alpha = alpha),
                spotlightColor.copy(alpha = alpha * 0.4f),
                spotlightColor.copy(alpha = alpha * 0.1f),
                Color.Transparent
            ),
            center = Offset(size.width * centerX, size.height * centerY),
            radius = size.maxDimension * radius
        )
    )
}

fun Modifier.animatedSpotlight(
    spotlightColor: Color = ShinyGoldMid,
    alpha: Float = 0.15f,
    duration: Int = EffectDuration.AmbientLoop * 2
): Modifier = composed {
    val centerX by reversingFloat("spotlightX", 0.3f, 0.7f, duration)
    val centerY by reversingFloat("spotlightY", 0.2f, 0.5f, (duration * 0.8).toInt())
    radialSpotlight(
        spotlightColor = spotlightColor,
        alpha = alpha,
        centerX = centerX,
        centerY = centerY
    )
}

private fun DrawScope.drawSpotCircle(
    color: Color,
    alpha: Float,
    centerFx: Float,
    centerFy: Float,
    radiusFactor: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.3f),
                Color.Transparent
            ),
            center = Offset(size.width * centerFx, size.height * centerFy),
            radius = size.maxDimension * radiusFactor
        )
    )
}

fun Modifier.dualSpotlight(
    primaryColor: Color = ShinyGoldLight,
    secondaryColor: Color = LiveBlueBright,
    primaryAlpha: Float = 0.15f,
    secondaryAlpha: Float = 0.1f
): Modifier = drawBehind {
    drawSpotCircle(primaryColor, primaryAlpha, 0.2f, 0.2f, 0.7f)
    drawSpotCircle(secondaryColor, secondaryAlpha, 0.8f, 0.8f, 0.5f)
}

fun Modifier.ribbonHighlight(
    highlightColor: Color = ShinyGoldLight,
    alpha: Float = 0.08f,
    angle: Float = 45f
): Modifier = drawBehind {
    val radians = Math.toRadians(angle.toDouble()).toFloat()
    val length = size.maxDimension * 2
    val dx = length * cos(radians) / 2
    val dy = length * sin(radians) / 2
    val cx = size.width / 2
    val cy = size.height / 2
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                highlightColor.copy(alpha = alpha),
                highlightColor.copy(alpha = alpha * 1.5f),
                highlightColor.copy(alpha = alpha),
                Color.Transparent
            ),
            start = Offset(cx - dx, cy - dy),
            end = Offset(cx + dx, cy + dy)
        )
    )
}

fun Modifier.edgeHighlight(
    highlightColor: Color = Color.White,
    alpha: Float = 0.1f,
    position: EdgePosition = EdgePosition.Top
): Modifier = drawBehind {
    val tinted = highlightColor.copy(alpha = alpha)
    val gradientBrush = when (position) {
        EdgePosition.Top -> Brush.verticalGradient(
            colors = listOf(tinted, Color.Transparent),
            startY = 0f,
            endY = size.height * 0.15f
        )
        EdgePosition.Bottom -> Brush.verticalGradient(
            colors = listOf(Color.Transparent, tinted),
            startY = size.height * 0.85f,
            endY = size.height
        )
        EdgePosition.Left -> Brush.horizontalGradient(
            colors = listOf(tinted, Color.Transparent),
            startX = 0f,
            endX = size.width * 0.15f
        )
        EdgePosition.Right -> Brush.horizontalGradient(
            colors = listOf(Color.Transparent, tinted),
            startX = size.width * 0.85f,
            endX = size.width
        )
    }
    drawRect(brush = gradientBrush)
}

enum class EdgePosition { Top, Bottom, Left, Right }

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
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(shadowColor.copy(alpha = alpha), Color.Transparent),
                        startY = 0f,
                        endY = blur.toPx()
                    )
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(shadowColor.copy(alpha = alpha * 0.7f), Color.Transparent),
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

fun Modifier.premiumCardEffect(
    isHighlighted: Boolean = false,
    isLive: Boolean = false
): Modifier = composed {
    val shadowLayers = when {
        isLive -> liveShadowLayers()
        isHighlighted -> goldShadowLayers()
        else -> defaultShadowLayers()
    }
    this
        .multiLayerShadow(layers = shadowLayers)
        .then(
            if (isHighlighted) {
                Modifier.radialSpotlight(spotlightColor = ShinyGoldMid, alpha = 0.1f)
            } else {
                Modifier
            }
        )
        .then(if (isLive) Modifier.liveGlowPulse() else Modifier)
}

fun Modifier.heroEffect(): Modifier = composed {
    try {
        this
            .floatingParallax(amplitude = 6f)
            .animatedSpotlight(spotlightColor = ShinyGoldLight, alpha = 0.12f)
            .multiLayerShadow(goldShadowLayers())
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
