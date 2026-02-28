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
