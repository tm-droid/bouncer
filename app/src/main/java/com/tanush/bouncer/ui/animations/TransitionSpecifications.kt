package com.tanush.bouncer.ui.animations

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Canonical transition definitions used across screens and key components.
 *
 * Why centralize:
 * A single transition vocabulary keeps motion predictable and prevents each screen from
 * inventing slightly different timing/curves for the same interaction pattern.
 */
object TransitionSpecifications {

    /**
     * New content enters by fading + moving up from below.
     * Purpose: guides the eye toward fresh content without hard jumps.
     */
    val enterFadeSlide: EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.NORMAL_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            )
        ) + slideInVertically(
            initialOffsetY = { full -> full / 6 },
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.NORMAL_MS,
                easing = BouncerMotionEasing.EaseOutCubic
            )
        )

    /**
     * Exiting content fades + slides slightly down.
     * Purpose: indicates layer dismissal while preserving continuity.
     */
    val exitFadeSlide: ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.QUICK_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            )
        ) + slideOutVertically(
            targetOffsetY = { full -> full / 10 },
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.QUICK_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            )
        )

    /**
     * Cross-fade transform for image or media changes.
     */
    @OptIn(ExperimentalAnimationApi::class)
    fun crossFade(): ContentTransform {
        return fadeIn(
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.NORMAL_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            )
        ) togetherWith fadeOut(
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.NORMAL_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            )
        )
    }

    /**
     * Expand/collapse pair for accordions, innings sections, and list groups.
     */
    val expandCollapseEnter: EnterTransition =
        expandVertically(
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.NORMAL_MS,
                easing = BouncerMotionEasing.EaseOutCubic
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.QUICK_MS,
                easing = BouncerMotionEasing.EaseOutCubic
            )
        )

    val expandCollapseExit: ExitTransition =
        shrinkVertically(
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.QUICK_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = BouncerMotionTiming.QUICK_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            )
        )

    /**
     * Helper for icon state changes (e.g., sort, filter, tab icon emphasis).
     */
    @Composable
    fun Modifier.rotateScale(
        selected: Boolean,
        reducedMotion: Boolean = rememberReducedMotionEnabled()
    ): Modifier {
        val rotation = animateFloatAsState(
            targetValue = if (selected) 0f else -12f,
            animationSpec = if (reducedMotion) {
                tween(1)
            } else {
                BouncerAnimations.snappyFloat()
            },
            label = "rotate_scale_rotation"
        )

        val scale = animateFloatAsState(
            targetValue = if (selected) 1f else 0.92f,
            animationSpec = if (reducedMotion) {
                tween(1)
            } else {
                BouncerAnimations.snappySpring
            },
            label = "rotate_scale_scale"
        )

        return this
            .rotate(rotation.value)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
    }
}

/**
 * Example usage:
 * ```kotlin
 * AnimatedVisibility(
 *     visible = isVisible,
 *     enter = TransitionSpecifications.enterFadeSlide,
 *     exit = TransitionSpecifications.exitFadeSlide
 * ) { Content() }
 * ```
 */
