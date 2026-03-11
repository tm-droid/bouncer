package com.tanush.bouncer.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * Centralized animation timing constants.
 *
 * Why this exists:
 * Keeping all timings in one object makes motion decisions discoverable and prevents random,
 * one-off durations from leaking across the app.
 */
object BouncerMotionTiming {
    const val QUICK_MS: Int = 200
    const val NORMAL_MS: Int = 300
    const val SLOW_MS: Int = 500

    // For emphasis moments such as milestone celebration; never exceed 600ms by policy.
    const val EMPHASIS_MAX_MS: Int = 600

    // Shared stagger step for list rows so rhythm feels intentional and consistent.
    const val STAGGER_STEP_MS: Int = 40
}

/**
 * Curves tuned for natural feel in a live scoring product.
 *
 * Why these curves:
 * - `EaseOutCubic`: quick start + smooth settle for responsive UI.
 * - `EaseInOutCubic`: balanced transitions where both enter/exit matter.
 * - `EaseOutElastic`: micro-bounce for delight moments (used sparingly).
 * - `Anticipation`: slight backward pull before moving forward to guide attention.
 */
object BouncerMotionEasing {
    val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val EaseOutElastic: Easing = CubicBezierEasing(0.16f, 1.4f, 0.34f, 1f)
    val Anticipation: Easing = CubicBezierEasing(0.68f, -0.55f, 0.27f, 1.55f)
}

/**
 * Predefined animation specs used by higher-level composables.
 *
 * Example:
 * ```kotlin
 * val alpha by animateFloatAsState(
 *     targetValue = if (visible) 1f else 0f,
 *     animationSpec = BouncerAnimations.smoothFloat()
 * )
 * ```
 */
object BouncerAnimations {
    fun smoothFloat(reducedMotion: Boolean = false): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = if (reducedMotion) BouncerMotionTiming.QUICK_MS else BouncerMotionTiming.NORMAL_MS,
            easing = if (reducedMotion) LinearOutSlowInEasing else BouncerMotionEasing.EaseInOutCubic
        )
    }

    fun snappyFloat(reducedMotion: Boolean = false): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = if (reducedMotion) 140 else BouncerMotionTiming.QUICK_MS,
            easing = if (reducedMotion) FastOutSlowInEasing else BouncerMotionEasing.EaseOutCubic
        )
    }

    fun emphasisFloat(reducedMotion: Boolean = false): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = if (reducedMotion) BouncerMotionTiming.NORMAL_MS else BouncerMotionTiming.SLOW_MS,
            easing = if (reducedMotion) FastOutSlowInEasing else BouncerMotionEasing.EaseOutElastic
        )
    }

    val snappySpring: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Backward-compatible staggered fade-in + slide-up modifier used by existing screens.
 *
 * Example:
 * ```kotlin
 * item { Row(modifier = Modifier.staggeredFadeIn(index = 2)) { ... } }
 * ```
 */
@Composable
fun Modifier.staggeredFadeIn(
    index: Int,
    delayBase: Int = 80,
    delayStep: Int = BouncerMotionTiming.STAGGER_STEP_MS,
    duration: Int = BouncerMotionTiming.NORMAL_MS
): Modifier {
    val reducedMotion = rememberReducedMotionEnabled()
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val offsetY = remember { Animatable(if (reducedMotion) 0f else 16f) }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            alpha.snapTo(1f)
            offsetY.snapTo(0f)
            return@LaunchedEffect
        }

        val delayTime = delayBase + (index * delayStep)
        kotlinx.coroutines.delay(delayTime.toLong())

        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = duration, easing = BouncerMotionEasing.EaseInOutCubic)
            )
        }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = duration, easing = BouncerMotionEasing.EaseOutCubic)
            )
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = offsetY.value
    }
}
