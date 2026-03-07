package com.tanush.bouncer.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Standard spring configurations for the app.
 */
object BouncerSprings {
    val natural = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    val responsive = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val soft = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessVeryLow
    )
}

/**
 * Premium staggered entry animation for lists and grid items.
 *
 * @param index The position in the layout (used for delay).
 * @param duration Duration of the animation in ms.
 * @param delayMillis Base delay before the animation starts.
 */
@Composable
fun Modifier.staggeredFadeIn(
    index: Int,
    duration: Int = 600,
    delayMillis: Int = 40
): Modifier {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((index * delayMillis).toLong())
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = BouncerSprings.soft
        )
    }

    return this.graphicsLayer {
        alpha = animatedProgress.value
        translationY = (1f - animatedProgress.value) * 30f
        val scale = 0.96f + (0.04f * animatedProgress.value)
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A subtle pulse animation often used for gold accents or active states.
 */
@Composable
fun Modifier.goldPulse(
    enabled: Boolean = true,
    minAlpha: Float = 0.4f,
    maxAlpha: Float = 0.8f
): Modifier {
    if (!enabled) return this
    
    val infiniteTransition = rememberInfiniteTransition(label = "goldPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    return this.graphicsLayer { this.alpha = alpha }
}

// ── Animation Specs ─────────────────────────────────────────────────────────

/**
 * Standard animation specs for consistent motion across the app.
 */
object BouncerAnimationSpecs {

    /**
     * Staggered content appearance spec for sequential list item reveals.
     * Each item fades in + slides up with a 50ms stagger between items.
     *
     * Usage: `Modifier.staggeredContentAppearance(index)`
     */
    const val staggerDelayMillis: Int = 50
    const val staggerDurationMillis: Int = 350
    val staggerEasing: Easing = FastOutSlowInEasing

    /**
     * Screen enter transition spec: fade-in + slight slide-up.
     * 300ms with FastOutSlowInEasing for a premium feel.
     */
    const val screenEnterDurationMillis: Int = 300
    val screenEnterEasing: Easing = FastOutSlowInEasing
    const val screenEnterSlideOffset: Float = 24f

    /**
     * Shimmer animation spec for loading placeholders.
     * 1200ms sweep with linear easing, infinite restart.
     */
    const val shimmerDurationMillis: Int = 1200
    val shimmerEasing: Easing = LinearEasing
    val shimmerRepeatMode: RepeatMode = RepeatMode.Restart
}

/**
 * Staggered content appearance animation for list items.
 * Each item fades in and slides up sequentially with a 50ms delay per index.
 *
 * @param index The position of the item in the list.
 * @param visible Whether the item should be visible (triggers animation).
 */
@Composable
fun Modifier.staggeredContentAppearance(
    index: Int,
    visible: Boolean = true
): Modifier {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            delay((index * BouncerAnimationSpecs.staggerDelayMillis).toLong())
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = BouncerAnimationSpecs.staggerDurationMillis,
                    easing = BouncerAnimationSpecs.staggerEasing
                )
            )
        } else {
            animatedProgress.snapTo(0f)
        }
    }

    return this.graphicsLayer {
        alpha = animatedProgress.value
        translationY = (1f - animatedProgress.value) * BouncerAnimationSpecs.screenEnterSlideOffset
    }
}

/**
 * Screen enter transition: fade-in with a slight slide-up.
 * Use on root screen content for smooth page reveals.
 */
@Composable
fun Modifier.screenEnterTransition(): Modifier {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = BouncerAnimationSpecs.screenEnterDurationMillis,
                easing = BouncerAnimationSpecs.screenEnterEasing
            )
        )
    }

    return this.graphicsLayer {
        alpha = animatedProgress.value
        translationY = (1f - animatedProgress.value) * BouncerAnimationSpecs.screenEnterSlideOffset
    }
}
