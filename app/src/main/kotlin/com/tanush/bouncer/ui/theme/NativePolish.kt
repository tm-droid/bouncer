package com.tanush.bouncer.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sentry.Sentry

/**
 * Native Android UI Polish Utilities
 * 
 * Provides enhanced shadows, gestures, and visual effects
 * for a polished native Android experience
 */

/**
 * Enhanced shadow with color blending for depth
 * More realistic than standard Material elevation
 */
fun Modifier.enhancedShadow(
    elevation: Dp,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    color: Color = Color.Black,
    alpha: Float = 0.15f
): Modifier = drawBehind {
    val shadowColor = color.copy(alpha = alpha)
    val paint = Paint().apply {
        try {
            this.color = shadowColor
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    drawIntoCanvas { canvas ->
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.setShadowLayer(
            elevation.toPx(),
            0f,
            elevation.toPx() * 0.5f,
            shadowColor.toArgb()
        )
    }
}

// =============================================================================
// Dual-Tone Shadow System — Simulates overhead lighting on dark surfaces
// =============================================================================

/**
 * Shadow elevation presets with preconfigured dual-tone parameters.
 * Each level defines an ambient (downward dark) and key (upward highlight) shadow.
 */
enum class ShadowElevation(
    val ambientBlur: Dp,
    val ambientOffsetY: Dp,
    val ambientAlpha: Float,
    val keyBlur: Dp,
    val keyOffsetY: Dp,
    val keyAlpha: Float
) {
    Subtle(
        ambientBlur = 2.dp, ambientOffsetY = 1.dp, ambientAlpha = 0.12f,
        keyBlur = 1.dp, keyOffsetY = (-0.5).dp, keyAlpha = 0.03f
    ),
    Standard(
        ambientBlur = 6.dp, ambientOffsetY = 3.dp, ambientAlpha = 0.18f,
        keyBlur = 2.dp, keyOffsetY = (-1).dp, keyAlpha = 0.05f
    ),
    Elevated(
        ambientBlur = 12.dp, ambientOffsetY = 6.dp, ambientAlpha = 0.22f,
        keyBlur = 3.dp, keyOffsetY = (-2).dp, keyAlpha = 0.07f
    ),
    Floating(
        ambientBlur = 24.dp, ambientOffsetY = 12.dp, ambientAlpha = 0.28f,
        keyBlur = 4.dp, keyOffsetY = (-3).dp, keyAlpha = 0.09f
    )
}

/**
 * Dual-tone shadow simulating overhead lighting.
 * Dark ambient shadow below + subtle key highlight above.
 * Prefer this over [enhancedShadow] for realistic depth on dark surfaces.
 */
fun Modifier.dualToneShadow(
    elevation: ShadowElevation = ShadowElevation.Standard,
    cornerRadius: Dp = 14.dp,
    ambientColor: Color = Color.Black,
    keyColor: Color = Color.White
): Modifier = drawBehind {
    val cr = cornerRadius.toPx()
    val nativeCanvas = drawContext.canvas.nativeCanvas

    // Ambient shadow — dark, cast downward (main depth cue)
    val ambientPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.TRANSPARENT
        setShadowLayer(
            elevation.ambientBlur.toPx(),
            0f,
            elevation.ambientOffsetY.toPx(),
            ambientColor.copy(alpha = elevation.ambientAlpha).toArgb()
        )
    }
    nativeCanvas.drawRoundRect(
        0f, 0f, size.width, size.height, cr, cr, ambientPaint
    )

    // Key highlight — very subtle, offset upward (overhead light simulation)
    val keyPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.TRANSPARENT
        setShadowLayer(
            elevation.keyBlur.toPx(),
            0f,
            elevation.keyOffsetY.toPx(),
            keyColor.copy(alpha = elevation.keyAlpha).toArgb()
        )
    }
    nativeCanvas.drawRoundRect(
        0f, 0f, size.width, size.height, cr, cr, keyPaint
    )
}

/**
 * Inset shadow for recessed elements (progress bars, input fields, dividers).
 * Dark gradient at top edge + subtle highlight at bottom — simulates depth into the surface.
 */
fun Modifier.insetShadow(
    cornerRadius: Dp = 8.dp,
    shadowColor: Color = Color.Black,
    highlightColor: Color = Color.White,
    shadowAlpha: Float = 0.25f,
    highlightAlpha: Float = 0.04f,
    depth: Dp = 3.dp
): Modifier = drawWithContent {
    drawContent()
    val cr = cornerRadius.toPx()
    val depthPx = depth.toPx()

    // Dark gradient at top — recessed into surface
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                shadowColor.copy(alpha = shadowAlpha),
                Color.Transparent
            ),
            startY = 0f,
            endY = depthPx * 3f
        ),
        cornerRadius = CornerRadius(cr, cr),
        size = size
    )

    // Subtle highlight at bottom — catch light
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                highlightColor.copy(alpha = highlightAlpha)
            ),
            startY = size.height - depthPx * 2f,
            endY = size.height
        ),
        cornerRadius = CornerRadius(cr, cr),
        size = size
    )
}

/**
 * Directional highlight border simulating overhead light on non-Card surfaces.
 * Brighter top edge fading to transparent at bottom.
 *
 * NOTE: Do NOT use on [GlassCard] — it has its own directional border.
 * Use this on Box, Row, Surface, or other non-Card containers.
 */
fun Modifier.highlightBorder(
    highlightColor: Color = Color.White.copy(alpha = 0.16f),
    cornerRadius: Dp = 14.dp,
    width: Dp = 0.5.dp
): Modifier = drawWithContent {
    drawContent()
    val cr = cornerRadius.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(highlightColor, Color.Transparent),
            startY = 0f,
            endY = size.height
        ),
        cornerRadius = CornerRadius(cr, cr),
        size = size,
        style = Stroke(width = width.toPx())
    )
}

/**
 * Native Android press effect with scale and haptic
 * Material 3 compliant scale values
 */
fun Modifier.pressEffect(
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    onPress: () -> Unit = {}
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "pressEffect"
    )
    
    this
        .scale(scale)
        .pointerInput(enabled) {
            if (enabled) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
        }
}

/**
 * Bouncy scale for favorite/toggle actions.
 * Plays a 1.0 → 1.3 → 1.0 spring bounce whenever [toggled] flips to true.
 */
fun Modifier.favoriteBounce(
    toggled: Boolean
): Modifier = composed {
    val bounceScale by animateFloatAsState(
        targetValue = if (toggled) 1f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "favBounce"
    )

    // Trigger a key-frame style punch: briefly overshoot then settle.
    val punchScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 600f),
        label = "favPunch"
    )
    val currentScale = if (toggled) punchScale else bounceScale
    this.scale(currentScale)
}

/**
 * Shimmer effect for loading states
 * Material 3 style with smooth animation
 */
fun Modifier.shimmerEffect(
    enabled: Boolean = true,
    colors: List<Color> = listOf(
        Color.White.copy(alpha = 0.02f),
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.02f)
    )
): Modifier = composed {
    if (!enabled) return@composed this
    
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    drawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(offsetX, 0f),
                end = Offset(offsetX + 500f, 500f)
            )
        )
    }
}

/**
 * Loading overlay — dims content and overlays a shimmer.
 * Use on cards/surfaces that are loading async data.
 */
fun Modifier.loadingOverlay(
    isLoading: Boolean
): Modifier = composed {
    if (!isLoading) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "loadingOverlay")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingShimmerX"
    )

    this
        .graphicsLayer { alpha = 0.6f }
        .drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    start = Offset(shimmerX, 0f),
                    end = Offset(shimmerX + 400f, size.height)
                )
            )
        }
}

/**
 * Pulse animation for live indicators
 */
fun Modifier.pulseAnimation(
    enabled: Boolean = true,
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    duration: Int = 1500
): Modifier = composed {
    if (!enabled) return@composed this
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    scale(scale)
}

/**
 * Gradient overlay for elevated surfaces
 */
fun Modifier.elevationGradient(
    startAlpha: Float = 0.08f,
    endAlpha: Float = 0.02f
): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = startAlpha),
                Color.White.copy(alpha = endAlpha)
            )
        )
    )
}

/**
 * Glow effect with color
 */
fun Modifier.glowEffect(
    color: Color,
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.25f
): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                Color.Transparent
            ),
            center = Offset(size.width / 2, size.height / 2),
            radius = blurRadius.toPx()
        )
    )
}

/**
 * Native swipe-to-dismiss gesture (for future use)
 */
fun Modifier.swipeToDismiss(
    onDismiss: () -> Unit,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    this
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (offsetY > 200f) {
                        onDismiss()
                    }
                    offsetY = 0f
                },
                onVerticalDrag = { _, dragAmount ->
                    offsetY += dragAmount
                }
            )
        }
}

/**
 * Material 3 state layer overlay
 * Shows on hover, press, focus states
 */
@Composable
fun StateLayerColor(
    color: Color,
    state: InteractionState
): Color {
    return when (state) {
        InteractionState.Pressed -> color.copy(alpha = StateLayerOpacity.Pressed)
        InteractionState.Focused -> color.copy(alpha = StateLayerOpacity.Focus)
        InteractionState.Hovered -> color.copy(alpha = StateLayerOpacity.Hover)
        InteractionState.Dragged -> color.copy(alpha = StateLayerOpacity.Dragged)
        InteractionState.Disabled -> color.copy(alpha = StateLayerOpacity.Disabled)
        InteractionState.Default -> Color.Transparent
    }
}

enum class InteractionState {
    Default,
    Pressed,
    Focused,
    Hovered,
    Dragged,
    Disabled
}
