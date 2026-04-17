package com.tanush.bouncer.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset

/**
 * Shared motion tokens for consistent, performant UI animation.
 */
object BouncerMotion {
    var reducedMotionEnabled by mutableStateOf(false)
        private set

    fun applyReducedMotionPreference(enabled: Boolean) {
        reducedMotionEnabled = enabled
    }

    object Duration {
        const val Fast = AnimationDuration.fast
        const val Normal = AnimationDuration.normal
        const val Slow = AnimationDuration.moderate
    }

    object Springs {
        private const val GentleDamping = 0.9f
        private const val MediumDamping = 0.8f
        private const val SnappyDamping = 0.72f

        private const val GentleStiffness = Spring.StiffnessLow
        private const val MediumStiffness = Spring.StiffnessMediumLow
        private const val SnappyStiffness = Spring.StiffnessMedium

        fun <T> gentle(): SpringSpec<T> = springSpec(GentleDamping, GentleStiffness)
        fun <T> medium(): SpringSpec<T> = springSpec(MediumDamping, MediumStiffness)
        fun <T> snappy(): SpringSpec<T> = springSpec(SnappyDamping, SnappyStiffness)

        private fun <T> springSpec(dampingRatio: Float, stiffness: Float): SpringSpec<T> =
            SpringSpec(dampingRatio = dampingRatio, stiffness = stiffness)
    }

    object Specs {
        val FadeIn: FiniteAnimationSpec<Float> = tween(
            durationMillis = Duration.Normal,
            easing = LinearOutSlowInEasing
        )
        val FadeOut: FiniteAnimationSpec<Float> = tween(
            durationMillis = Duration.Fast,
            easing = FastOutSlowInEasing
        )
        val Slide: FiniteAnimationSpec<IntOffset> = tween(
            durationMillis = Duration.Slow,
            easing = FastOutSlowInEasing
        )
        val Scale: FiniteAnimationSpec<Float> = tween(
            durationMillis = Duration.Normal,
            easing = FastOutSlowInEasing
        )
        val ReducedFadeIn: FiniteAnimationSpec<Float> = tween(
            durationMillis = Duration.Fast,
            easing = LinearOutSlowInEasing
        )
        val ReducedFadeOut: FiniteAnimationSpec<Float> = tween(
            durationMillis = Duration.Fast,
            easing = FastOutSlowInEasing
        )
    }

    object Transitions {
        fun horizontalEnter(offsetDivisor: Int = 4): EnterTransition =
            if (reducedMotionEnabled) {
                fadeIn(Specs.ReducedFadeIn)
            } else {
                fadeIn(Specs.FadeIn) + slideInHorizontally(
                    initialOffsetX = { it / offsetDivisor },
                    animationSpec = Specs.Slide
                )
            }

        fun horizontalExit(offsetDivisor: Int = 4): ExitTransition =
            if (reducedMotionEnabled) {
                fadeOut(Specs.ReducedFadeOut)
            } else {
                fadeOut(Specs.FadeOut) + slideOutHorizontally(
                    targetOffsetX = { -it / offsetDivisor },
                    animationSpec = Specs.Slide
                )
            }

        fun horizontalPopEnter(offsetDivisor: Int = 4): EnterTransition =
            if (reducedMotionEnabled) {
                fadeIn(Specs.ReducedFadeIn)
            } else {
                fadeIn(Specs.FadeIn) + slideInHorizontally(
                    initialOffsetX = { -it / offsetDivisor },
                    animationSpec = Specs.Slide
                )
            }

        fun horizontalPopExit(offsetDivisor: Int = 4): ExitTransition =
            if (reducedMotionEnabled) {
                fadeOut(Specs.ReducedFadeOut)
            } else {
                fadeOut(Specs.FadeOut) + slideOutHorizontally(
                    targetOffsetX = { it / offsetDivisor },
                    animationSpec = Specs.Slide
                )
            }

        fun verticalEnter(offsetDivisor: Int = 10): EnterTransition =
            if (reducedMotionEnabled) {
                fadeIn(Specs.ReducedFadeIn)
            } else {
                fadeIn(Specs.FadeIn) + slideInVertically(
                    initialOffsetY = { it / offsetDivisor },
                    animationSpec = Specs.Slide
                )
            }

        fun verticalExit(offsetDivisor: Int = 10): ExitTransition =
            if (reducedMotionEnabled) {
                fadeOut(Specs.ReducedFadeOut)
            } else {
                fadeOut(Specs.FadeOut) + slideOutVertically(
                    targetOffsetY = { it / offsetDivisor },
                    animationSpec = Specs.Slide
                )
            }

        fun scaleFadeIn(initialScale: Float = 0.92f): EnterTransition =
            if (reducedMotionEnabled) {
                fadeIn(Specs.ReducedFadeIn)
            } else {
                fadeIn(Specs.FadeIn) + scaleIn(
                    initialScale = initialScale,
                    animationSpec = Specs.Scale
                )
            }

        fun scaleFadeOut(targetScale: Float = 0.92f): ExitTransition =
            if (reducedMotionEnabled) {
                fadeOut(Specs.ReducedFadeOut)
            } else {
                fadeOut(Specs.FadeOut) + scaleOut(
                    targetScale = targetScale,
                    animationSpec = Specs.Scale
                )
            }

        /** Tab switch: crossfade with subtle scale (0.97→1.0) — no slide */
        fun tabEnter(): EnterTransition =
            if (reducedMotionEnabled) {
                fadeIn(Specs.ReducedFadeIn)
            } else {
                fadeIn(Specs.FadeIn) + scaleIn(
                    initialScale = 0.97f,
                    animationSpec = Specs.Scale
                )
            }

        fun tabExit(): ExitTransition =
            if (reducedMotionEnabled) {
                fadeOut(Specs.ReducedFadeOut)
            } else {
                fadeOut(Specs.FadeOut) + scaleOut(
                    targetScale = 0.97f,
                    animationSpec = Specs.Scale
                )
            }
    }
}
