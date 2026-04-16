package com.tanush.bouncer.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sentry.Sentry
import com.tanush.bouncer.ui.accessibility.rememberReducedMotionEnabled

/**
 * Material 3 Extensions for Native Android Feel
 * 
 * Provides:
 * - Custom ripple effects matching brand colors
 * - State layer overlays
 * - Native-feeling touch feedback
 * - Accessibility-enhanced interactions
 */

/**
 * Surface state layer overlay colors
 * Material 3 standard opacity values for state layers
 */
object StateLayerOpacity {
    const val Hover = 0.08f
    const val Focus = 0.12f
    const val Pressed = 0.12f
    const val Dragged = 0.16f
    const val Disabled = 0.38f
}

/**
 * Enhanced clickable with native Android ripple
 */
fun Modifier.nativeClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    showRipple: Boolean = true
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val indication: Indication? = if (showRipple) {
        ripple(color = GoldDark)
    } else null
    
    clickable(
        interactionSource = source,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}

/**
 * Premium gradient overlay for elevated surfaces
 */
fun Modifier.surfaceGradientOverlay(
    intensity: Float = 1f
): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.08f * intensity),
                Color.White.copy(alpha = 0.03f * intensity)
            )
        )
    )
}

/**
 * Material 3 compliant glow effect for accented elements
 */
fun Modifier.accentGlow(
    color: Color = GoldDark,
    radius: Dp = BouncerTokenScales.spacing.md,
    alpha: Float = 0.25f
): Modifier = drawWithContent {
    drawContent()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                Color.Transparent
            ),
            center = Offset(size.width / 2, size.height / 2),
            radius = radius.toPx()
        ),
        style = Fill
    )
}

/**
 * Native Android elevation tints
 * Material 3 uses subtle tints on elevated surfaces
 */
object ElevationTint {
    val Level0 = Color.Transparent
    val Level1 = Color.White.copy(alpha = 0.05f)
    val Level2 = Color.White.copy(alpha = 0.08f)
    val Level3 = Color.White.copy(alpha = 0.11f)
    val Level4 = Color.White.copy(alpha = 0.12f)
    val Level5 = Color.White.copy(alpha = 0.14f)
}

/**
 * Get elevation tint for surface level
 */
fun getElevationTint(elevation: Dp): Color {
    try {
        val tiers = BouncerTokenScales.elevation
        return when {
            elevation <= tiers.none -> ElevationTint.Level0
            elevation <= tiers.sm -> ElevationTint.Level1
            elevation <= tiers.md -> ElevationTint.Level2
            elevation <= tiers.lg -> ElevationTint.Level3
            elevation <= tiers.xl -> ElevationTint.Level4
            else -> ElevationTint.Level5
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Material 3 shape system extended
 */
object MaterialShapes {
    private val radius = BouncerTokenScales.radius
    val ExtraSmall = radius.xs
    val Small = radius.sm
    val Medium = radius.md
    val Large = radius.lg
    val ExtraLarge = radius.hero
    val ExtraExtraLarge = radius.sheet
}

/**
 * Material Motion spring configurations
 */
object MaterialMotion {
    // Spring damping ratios
    const val DampingRatioNoBounce = 1f
    const val DampingRatioLowBounce = 0.75f
    const val DampingRatioMediumBounce = 0.6f  
    const val DampingRatioHighBounce = 0.45f
    
    // Spring stiffness values
    const val StiffnessVeryLow = 50f
    const val StiffnessLow = 200f
    const val StiffnessMedium = 400f
    const val StiffnessMediumHigh = 600f
    const val StiffnessHigh = 1000f
    const val StiffnessVeryHigh = 1500f
    
    // Duration presets in milliseconds
    const val DurationShort1 = 50
    const val DurationShort2 = 100
    const val DurationShort3 = 150
    const val DurationShort4 = 200
    const val DurationMedium1 = 250
    const val DurationMedium2 = 300
    const val DurationMedium3 = 350
    const val DurationMedium4 = 400
    const val DurationLong1 = 450
    const val DurationLong2 = 500
    const val DurationLong3 = 550
    const val DurationLong4 = 600
    const val DurationExtraLong1 = 700
    const val DurationExtraLong2 = 800
    const val DurationExtraLong3 = 900
    const val DurationExtraLong4 = 1000
}

/**
 * Material 3 motion easing curves (approximations)
 */
object MotionTokens {
    const val EasingStandardAccelerate = 0.3f
    const val EasingStandardDecelerate = 0f
    const val EasingEmphasizedAccelerate = 0.05f
    const val EasingEmphasizedDecelerate = 0.15f
}

// ─────────────────────────────────────────────────────────────────────────────
// Press Depth Modifiers – tactile press-down feedback with scale animation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Predefined press interaction styles
 */
enum class PressStyle(val scale: Float, val elevationDrop: Dp) {
    Subtle(0.985f, 1.dp),
    Standard(0.98f, 2.dp),
    Pronounced(0.95f, 3.dp)
}

/**
 * Applies a press-depth scale animation on touch for tactile feedback.
 * Respects reduced-motion accessibility settings.
 */
fun Modifier.pressDepth(
    style: PressStyle = PressStyle.Standard,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReducedMotionEnabled()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) style.scale else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f),
        label = "pressDepthScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = GoldDark),
            onClick = onClick
        )
}

/**
 * Simplified press-scale modifier accepting a raw scale value.
 * For cases where the full [PressStyle] enum is overkill.
 */
fun Modifier.pressScale(
    targetScale: Float = 0.98f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReducedMotionEnabled()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) targetScale else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f),
        label = "pressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = GoldDark),
            onClick = onClick
        )
}
