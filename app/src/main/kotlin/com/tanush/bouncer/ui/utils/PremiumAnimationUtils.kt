package com.tanush.bouncer.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalView
import com.tanush.bouncer.ui.theme.GoldDark
import com.tanush.bouncer.ui.theme.GoldLight

/**
 * Haptic feedback types for premium interactions
 */
enum class HapticType {
    LIGHT,
    MEDIUM,
    HEAVY,
    SUCCESS,
    WARNING,
    ERROR,
    SELECTION,
    IMPACT
}

/**
 * Perform haptic feedback based on type
 */
fun performHaptic(view: View, type: HapticType) {
    val constant = when (type) {
        HapticType.LIGHT -> HapticFeedbackConstants.KEYBOARD_TAP
        HapticType.MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
        HapticType.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        HapticType.SUCCESS -> HapticFeedbackConstants.CONFIRM
        HapticType.WARNING -> HapticFeedbackConstants.REJECT
        HapticType.ERROR -> HapticFeedbackConstants.REJECT
        HapticType.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
        HapticType.IMPACT -> HapticFeedbackConstants.CONTEXT_CLICK
    }
    view.performHapticFeedback(constant)
}

/**
 * Perform vibration with custom pattern
 */
fun performVibration(context: Context, duration: Long = 10L, amplitude: Int = 50) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(duration, amplitude)
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(duration)
    }
}

/**
 * Custom vibration patterns
 */
object VibrationPatterns {
    // Boundary celebration pattern
    val BOUNDARY = longArrayOf(0, 50, 50, 80)
    
    // Wicket pattern
    val WICKET = longArrayOf(0, 100, 50, 100, 50, 150)
    
    // Quick tap
    val TAP = longArrayOf(0, 10)
    
    // Success pattern
    val SUCCESS = longArrayOf(0, 50, 100, 50)
    
    // Error pattern
    val ERROR = longArrayOf(0, 100, 50, 100)
}

/**
 * Perform pattern vibration
 */
fun performPatternVibration(context: Context, pattern: LongArray) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createWaveform(pattern, -1)
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, -1)
    }
}

/**
 * Composable helper for haptic feedback
 */
@Composable
fun rememberHapticFeedback(): (HapticType) -> Unit {
    val view = LocalView.current
    return remember {
        { type: HapticType ->
            performHaptic(view, type)
        }
    }
}

// ============ Premium Animation Utilities ============

/**
 * Shimmer effect modifier
 */
fun Modifier.shimmerEffect(
    baseColor: Color = GoldDark.copy(alpha = 0.3f),
    highlightColor: Color = GoldLight.copy(alpha = 0.5f),
    durationMillis: Int = 1500
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    
    drawWithContent {
        drawContent()
        val brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(translateAnimation.value * size.width, 0f),
            end = Offset((translateAnimation.value + 1) * size.width, size.height)
        )
        drawRect(brush)
    }
}

/**
 * Pulse glow modifier
 */
fun Modifier.pulseGlow(
    color: Color = GoldDark,
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 0.6f,
    durationMillis: Int = 1500
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulseGlow")
    val alpha = transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    drawWithContent {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha.value),
                    Color.Transparent
                ),
                radius = size.maxDimension
            ),
            radius = size.maxDimension * 0.6f
        )
        drawContent()
    }
}

// ============ Navigation Transitions ============

/**
 * Premium enter transition
 */
fun premiumEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + slideInHorizontally(
        initialOffsetX = { it / 4 },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
}

/**
 * Premium exit transition
 */
fun premiumExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(200, easing = FastOutLinearInEasing)
    ) + slideOutHorizontally(
        targetOffsetX = { -it / 4 },
        animationSpec = tween(250)
    )
}

/**
 * Premium pop enter transition (going back)
 */
fun premiumPopEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
}

/**
 * Premium pop exit transition (going back)
 */
fun premiumPopExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(200, easing = FastOutLinearInEasing)
    ) + slideOutHorizontally(
        targetOffsetX = { it / 4 },
        animationSpec = tween(250)
    )
}

/**
 * Slide up enter transition for bottom sheets
 */
fun slideUpEnterTransition(): EnterTransition {
    return slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    ) + fadeIn(animationSpec = tween(300))
}

/**
 * Slide down exit transition for bottom sheets
 */
fun slideDownExitTransition(): ExitTransition {
    return slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(200)
    ) + fadeOut(animationSpec = tween(150))
}

/**
 * Scale and fade enter transition
 */
fun scaleEnterTransition(): EnterTransition {
    return scaleIn(
        initialScale = 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    ) + fadeIn(animationSpec = tween(300))
}

/**
 * Scale and fade exit transition
 */
fun scaleExitTransition(): ExitTransition {
    return scaleOut(
        targetScale = 0.9f,
        animationSpec = tween(200)
    ) + fadeOut(animationSpec = tween(150))
}

// ============ Animation Specs ============

/**
 * Premium spring animation spec
 */
fun <T> premiumSpringSpec(
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessLow
): SpringSpec<T> = spring(
    dampingRatio = dampingRatio,
    stiffness = stiffness
)

/**
 * Premium tween animation spec
 */
fun <T> premiumTweenSpec(
    durationMillis: Int = 300,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing
): TweenSpec<T> = tween(
    durationMillis = durationMillis,
    delayMillis = delayMillis,
    easing = easing
)

/**
 * Bounce spring spec
 */
fun <T> bounceSpringSpec(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioHighBouncy,
    stiffness = Spring.StiffnessMedium
)

/**
 * Quick spring spec
 */
fun <T> quickSpringSpec(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)

// ============ Easing Functions ============

/**
 * Custom bounce easing
 */
val BounceEasing = Easing { fraction ->
    val n1 = 7.5625f
    val d1 = 2.75f
    
    when {
        fraction < 1f / d1 -> n1 * fraction * fraction
        fraction < 2f / d1 -> {
            val f = fraction - 1.5f / d1
            n1 * f * f + 0.75f
        }
        fraction < 2.5f / d1 -> {
            val f = fraction - 2.25f / d1
            n1 * f * f + 0.9375f
        }
        else -> {
            val f = fraction - 2.625f / d1
            n1 * f * f + 0.984375f
        }
    }
}

/**
 * Elastic easing for overshooting effect
 */
val ElasticEasing = Easing { fraction ->
    if (fraction == 0f || fraction == 1f) {
        fraction
    } else {
        val c4 = (2 * Math.PI) / 3
        (-Math.pow(2.0, (10 * fraction - 10).toDouble()) * 
            kotlin.math.sin((fraction * 10 - 10.75) * c4)).toFloat()
    }
}

// ============ Visibility Helpers ============

/**
 * Animated visibility with premium styling
 */
@Composable
fun PremiumAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    label: String = "visibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleEnterTransition(),
        exit = scaleExitTransition(),
        label = label,
        content = content
    )
}

/**
 * Fade animated visibility
 */
@Composable
fun FadeAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = 300,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(durationMillis)),
        exit = fadeOut(tween(durationMillis / 2)),
        content = content
    )
}
