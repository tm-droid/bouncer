package com.tanush.bouncer.ui.animations

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * LIVE badge pulse. Purpose: continuous but subtle confirmation that match state is active.
 */
@Composable
fun PulsingBadge(
    modifier: Modifier = Modifier,
    color: Color,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulsing_badge")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (reducedMotion) BouncerMotionTiming.QUICK_MS else BouncerMotionTiming.SLOW_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val alpha by transition.animateFloat(
        initialValue = if (reducedMotion) 1f else 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (reducedMotion) BouncerMotionTiming.QUICK_MS else BouncerMotionTiming.SLOW_MS,
                easing = BouncerMotionEasing.EaseOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pulse
            scaleY = pulse
            this.alpha = alpha
        }
    ) {
        GlowPulseEffect(
            modifier = Modifier.fillMaxSize(),
            color = color,
            reducedMotion = reducedMotion
        )
        content()
    }
}

/**
 * Press feedback with spring. Purpose: instant tactile confirmation for taps.
 */
@Composable
fun ScaleOnTapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = if (reducedMotion) {
            tween(durationMillis = 1)
        } else {
            spring(dampingRatio = 0.7f, stiffness = 500f)
        },
        label = "tap_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(enabled) {
            if (!enabled) pressed = false
        }
        content()
    }
}

/**
 * Staggered row entrance for scorecard lists.
 */
@Composable
fun SlideInRow(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }

    LaunchedEffect(index, visible, reducedMotion) {
        if (!visible) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (reducedMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(staggerDelay(index = index, baseMs = 20).toLong())
        progress.animateTo(1f, animationSpec = BouncerAnimations.smoothFloat())
    }

    Box(modifier = modifier.fadeSlideProgress(progress.value, slideDistancePx = 20f)) {
        content()
    }
}

/**
 * Floating score delta text (e.g., +4, +6) that rises then fades.
 */
@Composable
fun FloatUpAnimation(
    textVisible: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    content: @Composable () -> Unit
) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(textVisible, reducedMotion) {
        if (!textVisible) {
            alpha.snapTo(0f)
            offsetY.snapTo(0f)
            return@LaunchedEffect
        }
        if (reducedMotion) {
            alpha.snapTo(1f)
            delay(300)
            alpha.snapTo(0f)
            return@LaunchedEffect
        }

        alpha.snapTo(0f)
        offsetY.snapTo(0f)
        launch { alpha.animateTo(1f, animationSpec = BouncerAnimations.snappyFloat()) }
        launch {
            offsetY.animateTo(
                targetValue = -28f,
                animationSpec = tween(BouncerMotionTiming.SLOW_MS, easing = BouncerMotionEasing.EaseOutCubic)
            )
        }
        delay(260)
        alpha.animateTo(0f, animationSpec = BouncerAnimations.smoothFloat())
    }

    Box(
        modifier = modifier.graphicsLayer {
            translationY = offsetY.value
            this.alpha = alpha.value
        }
    ) {
        content()
    }
}

/**
 * Number flip for score changes.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScoreFlipAnimation(
    score: Int,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    text: @Composable (Int) -> Unit
) {
    if (reducedMotion) {
        Box(modifier = modifier) { text(score) }
        return
    }

    AnimatedContent(
        targetState = score,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(BouncerMotionTiming.NORMAL_MS, easing = BouncerMotionEasing.EaseOutCubic)
            ) + fadeIn(animationSpec = tween(BouncerMotionTiming.NORMAL_MS))) togetherWith
                (slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = tween(BouncerMotionTiming.NORMAL_MS, easing = BouncerMotionEasing.EaseInOutCubic)
                ) + fadeOut(animationSpec = tween(BouncerMotionTiming.QUICK_MS)))
        },
        label = "score_flip"
    ) { target ->
        text(target)
    }
}

/**
 * Expanding glow ring to emphasize key element (e.g., striker).
 */
@Composable
fun GlowPulseEffect(
    modifier: Modifier = Modifier,
    color: Color,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    maxRadius: Dp = 24.dp
) {
    val transition = rememberInfiniteTransition(label = "glow_pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (reducedMotion) BouncerMotionTiming.NORMAL_MS else BouncerMotionTiming.SLOW_MS,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_progress"
    )

    Canvas(modifier = modifier) {
        val radius = maxRadius.toPx() * (0.4f + (0.6f * progress))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = if (reducedMotion) 0.2f else 0.3f * (1f - progress)),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

/**
 * Subtle shake used for invalid actions or minor alerts.
 */
@Composable
fun ShakeAnimation(
    trigger: Any?,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(trigger, reducedMotion) {
        if (trigger == null) return@LaunchedEffect
        if (reducedMotion) {
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }

        val sequence = listOf(-8f, 8f, -6f, 6f, -3f, 3f, 0f)
        sequence.forEach { value ->
            offsetX.animateTo(
                value,
                animationSpec = tween(durationMillis = 40, easing = BouncerMotionEasing.EaseInOutCubic)
            )
        }
    }

    Box(modifier = modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
        content()
    }
}

/**
 * Rotation + scale for icon state transitions.
 */
@Composable
fun RotateScaleAnimation(
    selected: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (selected) 0f else -18f,
        animationSpec = if (reducedMotion) tween(1) else BouncerAnimations.snappyFloat(),
        label = "rotate_scale_rotation"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = if (reducedMotion) tween(1) else BouncerAnimations.snappySpring,
        label = "rotate_scale_scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.rotationZ = rotation
            this.scaleX = scale
            this.scaleY = scale
        }
    ) {
        content()
    }
}

/**
 * Combined fade + slide block transition for page sections.
 */
@Composable
fun FadeSlideAnimation(
    visible: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    enter: EnterTransition = TransitionSpecifications.enterFadeSlide,
    exit: ExitTransition = TransitionSpecifications.exitFadeSlide,
    content: @Composable () -> Unit
) {
    if (reducedMotion) {
        if (visible) Box(modifier = modifier) { content() }
        return
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit
    ) {
        content()
    }
}

/**
 * Eye-guiding highlight sweep that gently attracts attention to a specific element.
 */
@Composable
fun EyeGuidingHighlight(
    active: Boolean,
    modifier: Modifier = Modifier,
    highlightColor: Color,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
    content: @Composable () -> Unit
) {
    var width by remember { mutableStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "eye_guiding")
    val xProgress by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (reducedMotion) BouncerMotionTiming.NORMAL_MS else BouncerMotionTiming.SLOW_MS,
                easing = BouncerMotionEasing.EaseInOutCubic
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "highlight_x"
    )

    Box(
        modifier = modifier
            .onGloballyPositioned { width = it.size.width.toFloat().coerceAtLeast(1f) }
    ) {
        content()

        if (active) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                highlightColor.copy(alpha = if (reducedMotion) 0.12f else 0.26f),
                                Color.Transparent
                            ),
                            start = Offset(x = xProgress * width - width * 0.25f, y = 0f),
                            end = Offset(x = xProgress * width + width * 0.25f, y = 0f)
                        )
                    )
            )
        }
    }
}

/**
 * Example usage:
 * ```kotlin
 * val haptics = rememberHapticPatterns()
 * ScaleOnTapButton(
 *     onClick = { haptics.singleTap() }
 * ) {
 *     Text("Open Scorecard")
 * }
 * ```
 */
