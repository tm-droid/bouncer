package com.tanush.bouncer.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Easing curves
private val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

// a) BouncerEnterTransition / BouncerExitTransition
val BouncerEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = 350,
        easing = FastOutSlowInEasing
    )
) + slideInVertically(
    animationSpec = tween(
        durationMillis = 350,
        easing = FastOutSlowInEasing
    ),
    initialOffsetY = { it / 3 }
)

val BouncerExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = 350,
        easing = FastOutSlowInEasing
    )
) + slideOutVertically(
    animationSpec = tween(
        durationMillis = 350,
        easing = FastOutSlowInEasing
    ),
    targetOffsetY = { -it / 3 }
)

// b) BouncerNavTransitions object
object BouncerNavTransitions {
    val forwardEnter: EnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = 350,
            easing = EaseOutExpo
        )
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = 350,
            easing = EaseOutExpo
        ),
        initialOffsetX = { it }
    )
    
    val forwardExit: ExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = 350,
            easing = EaseOutExpo
        )
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = 350,
            easing = EaseOutExpo
        ),
        targetOffsetX = { -it / 3 }
    )
    
    val backEnter: EnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = 350,
            easing = EaseOutExpo
        )
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = 350,
            easing = EaseOutExpo
        ),
        initialOffsetX = { -it / 3 }
    )
    
    val backExit: ExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = 350,
            easing = EaseOutExpo
        )
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = 350,
            easing = EaseOutExpo
        ),
        targetOffsetX = { it }
    )
}

// c) SharedAxisTransition
enum class SharedAxis {
    X, Y, Z
}

object SharedAxisTransition {
    fun enter(axis: SharedAxis, forward: Boolean = true): EnterTransition {
        return when (axis) {
            SharedAxis.X -> fadeIn(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + slideInHorizontally(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                initialOffsetX = { if (forward) it else -it }
            )
            SharedAxis.Y -> fadeIn(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + slideInVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                initialOffsetY = { if (forward) it else -it }
            )
            SharedAxis.Z -> fadeIn(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + scaleIn(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                initialScale = if (forward) 0.8f else 1.1f
            )
        }
    }
    
    fun exit(axis: SharedAxis, forward: Boolean = true): ExitTransition {
        return when (axis) {
            SharedAxis.X -> fadeOut(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + slideOutHorizontally(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                targetOffsetX = { if (forward) -it else it }
            )
            SharedAxis.Y -> fadeOut(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + slideOutVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                targetOffsetY = { if (forward) -it else it }
            )
            SharedAxis.Z -> fadeOut(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + scaleOut(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                targetScale = if (forward) 1.1f else 0.8f
            )
        }
    }
}

// d) MaterialContainerTransform-like effect
@Composable
fun MaterialContainerTransform(
    targetState: Boolean,
    modifier: Modifier = Modifier,
    startContent: @Composable () -> Unit,
    endContent: @Composable () -> Unit
) {
    val transition = updateTransition(
        targetState = targetState,
        label = "container_transform"
    )
    
    val scale by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(
                    dampingRatio = 0.8f,
                    stiffness = 300f
                )
            } else {
                spring(
                    dampingRatio = 0.8f,
                    stiffness = 300f
                )
            }
        },
        label = "scale"
    ) { state ->
        if (state) 1f else 0.85f
    }
    
    val alpha by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        },
        label = "alpha"
    ) { state ->
        if (state) 1f else 0f
    }
    
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.graphicsLayer {
                this.scaleX = if (targetState) 1f / scale else scale
                this.scaleY = if (targetState) 1f / scale else scale
                this.alpha = 1f - alpha
            }
        ) {
            startContent()
        }
        
        Box(
            modifier = Modifier.graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
            }
        ) {
            endContent()
        }
    }
}

// e) ScreenTransitionWrapper composable
enum class TransitionDirection {
    UP, DOWN, LEFT, RIGHT, FADE
}

@Composable
fun ScreenTransitionWrapper(
    modifier: Modifier = Modifier,
    direction: TransitionDirection = TransitionDirection.UP,
    durationMillis: Int = 500,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        isVisible = true
    }
    
    val offsetX by animateDpAsState(
        targetValue = if (isVisible) 0.dp else when (direction) {
            TransitionDirection.LEFT -> (-60).dp
            TransitionDirection.RIGHT -> 60.dp
            else -> 0.dp
        },
        animationSpec = tween(durationMillis = durationMillis, easing = EaseOutExpo),
        label = "offset_x"
    )
    
    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else when (direction) {
            TransitionDirection.UP -> 60.dp
            TransitionDirection.DOWN -> (-60).dp
            else -> 0.dp
        },
        animationSpec = tween(durationMillis = durationMillis, easing = EaseOutExpo),
        label = "offset_y"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "alpha"
    )
    
    Box(
        modifier = modifier.graphicsLayer {
            translationX = offsetX.toPx()
            translationY = offsetY.toPx()
            this.alpha = alpha
        }
    ) {
        content()
    }
}

// Additional utility: Expandable transition
@OptIn(ExperimentalAnimationApi::class)
fun expandVerticallyWithFade(): EnterTransition {
    return expandVertically(
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        expandFrom = Alignment.Top
    ) + fadeIn(
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
}

@OptIn(ExperimentalAnimationApi::class)
fun shrinkVerticallyWithFade(): ExitTransition {
    return shrinkVertically(
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        shrinkTowards = Alignment.Top
    ) + fadeOut(
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
}

// Stagger utility for lists
class StaggeredEnterAnimation(
    private val itemCount: Int,
    private val staggerDelayMillis: Int = 50
) {
    @Composable
    fun getEnterDelay(index: Int): Int {
        return (index * staggerDelayMillis).coerceAtMost(itemCount * staggerDelayMillis)
    }
}

// Crossfade with scale
@Composable
fun <T> CrossfadeWithScale(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec<Float> = tween(300),
    content: @Composable (T) -> Unit
) {
    val transition = updateTransition(targetState, label = "crossfade_scale")
    
    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) + scaleIn(
                animationSpec = tween(300),
                initialScale = 0.92f
            ) togetherWith fadeOut(animationSpec = tween(300)) + scaleOut(
                animationSpec = tween(300),
                targetScale = 0.92f
            )
        }
    ) { state ->
        content(state)
    }
}
