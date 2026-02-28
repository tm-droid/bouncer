package com.tanush.bouncer.ui.animations

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * Premium staggered fade-in + slide-up entry animation.
 * 
 * @param index The index of the item in the list (used for staggering)
 * @param delayBase Initial delay before starting the staggering
 * @param delayStep Delay between each consecutive item
 * @param duration Animation duration
 */
@Composable
fun Modifier.staggeredFadeIn(
    index: Int,
    delayBase: Int = 100,
    delayStep: Int = 50,
    duration: Int = 500
): Modifier {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        val delayTime = delayBase + (index * delayStep)
        kotlinx.coroutines.delay(delayTime.toLong())
        
        // Parallel animations
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            )
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = offsetY.value
    }
}
