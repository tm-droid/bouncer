package com.tanush.bouncer.ui.animations

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Accessibility helper that returns true when system-level animation scale is 0.
 *
 * Why this matters:
 * Users who disable animations should get immediate state changes instead of large motion.
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
	return !ValueAnimator.areAnimatorsEnabled()
}

/**
 * Stagger delay helper for list rows.
 *
 * Example:
 * ```kotlin
 * val delay = staggerDelay(index = index)
 * ```
 */
fun staggerDelay(index: Int, baseMs: Int = 0, stepMs: Int = BouncerMotionTiming.STAGGER_STEP_MS): Int {
	return (baseMs + (index * stepMs)).coerceAtLeast(0)
}

/**
 * Linear interpolation helper for floats.
 */
fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
	return start + (end - start) * fraction.coerceIn(0f, 1f)
}

/**
 * Maps a value from one range to another, clamped to target range.
 */
fun interpolateRange(
	value: Float,
	fromMin: Float,
	fromMax: Float,
	toMin: Float,
	toMax: Float
): Float {
	if (fromMax == fromMin) return toMin
	val normalized = ((value - fromMin) / (fromMax - fromMin)).coerceIn(0f, 1f)
	return lerpFloat(toMin, toMax, normalized)
}

/**
 * Returns animated visibility progress between 0 and 1.
 */
@Composable
fun rememberVisibilityProgress(
	visible: Boolean,
	reducedMotion: Boolean = rememberReducedMotionEnabled(),
	durationMs: Int = BouncerMotionTiming.NORMAL_MS
): State<Float> {
	return animateFloatAsState(
		targetValue = if (visible) 1f else 0f,
		animationSpec = if (reducedMotion) {
			tween(durationMillis = 1)
		} else {
			tween(durationMillis = durationMs, easing = BouncerMotionEasing.EaseInOutCubic)
		},
		label = "visibility_progress"
	)
}

/**
 * Lightweight helper to animate one-off float values with reduced-motion support.
 */
@Composable
fun rememberAnimatedFloat(
	target: Float,
	reducedMotion: Boolean = rememberReducedMotionEnabled(),
	spec: FiniteAnimationSpec<Float> = BouncerAnimations.smoothFloat(),
	label: String = "animated_float"
): State<Float> {
	return animateFloatAsState(
		targetValue = target,
		animationSpec = if (reducedMotion) tween(durationMillis = 1) else spec,
		label = label
	)
}

/**
 * Modifier for common fade + slide motion driven by a progress value in [0, 1].
 *
 * Example:
 * ```kotlin
 * val progress by rememberVisibilityProgress(visible = true)
 * Box(modifier = Modifier.fadeSlideProgress(progress))
 * ```
 */
fun Modifier.fadeSlideProgress(
	progress: Float,
	slideDistancePx: Float = 24f
): Modifier {
	val clamped = progress.coerceIn(0f, 1f)
	return this.graphicsLayer {
		alpha = clamped
		translationY = lerpFloat(slideDistancePx, 0f, clamped)
	}
}

/**
 * Starts an [Animatable] at [start], then animates to [end].
 */
suspend fun Animatable<Float, *>.animateFromTo(
	start: Float,
	end: Float,
	spec: FiniteAnimationSpec<Float> = BouncerAnimations.smoothFloat()
) {
	snapTo(start)
	animateTo(end, animationSpec = spec)
}

/**
 * Reusable state holder for staggered list entrance.
 */
@Composable
fun rememberStaggeredVisibleState(
	index: Int,
	visible: Boolean,
	baseDelayMs: Int = 0,
	stepMs: Int = BouncerMotionTiming.STAGGER_STEP_MS
): State<Boolean> {
	val state = remember(index, visible) { mutableStateOf(false) }

	LaunchedEffect(index, visible) {
		if (!visible) {
			state.value = false
			return@LaunchedEffect
		}
		kotlinx.coroutines.delay(staggerDelay(index, baseDelayMs, stepMs).toLong())
		state.value = true
	}

	return state
}
