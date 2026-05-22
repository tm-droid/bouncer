package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.tanush.bouncer.ui.theme.GoldPrimary

private object MicroEasing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

private object MicroSpring {
    val Bouncy = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)
    val Snappy = spring<Float>(dampingRatio = 0.75f, stiffness = 600f)
    val Gentle = spring<Float>(dampingRatio = 0.8f, stiffness = 200f)
}

private fun tweenNormalDecel(duration: Int = AnimationDuration.normal) =
    tween<Float>(durationMillis = duration, easing = MicroEasing.EmphasizedDecelerate)

@Composable
private fun pressScaleState(
    pressScale: Float,
    enableHaptics: Boolean,
    label: String
): Pair<Float, MutableInteractionSource> {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = MicroSpring.Snappy,
        label = label
    )
    LaunchedEffect(isPressed) {
        if (isPressed && enableHaptics) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    return scale to interactionSource
}

fun Modifier.pressScaleEffect(
    pressScale: Float = PressScale.normal,
    enableHaptics: Boolean = true,
    onClick: () -> Unit = {}
): Modifier = composed {
    val (scale, interactionSource) = pressScaleState(pressScale, enableHaptics, "press_scale")
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
fun PressScaleEffect(
    modifier: Modifier = Modifier,
    pressScale: Float = PressScale.normal,
    enableHaptics: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val (scale, interactionSource) = pressScaleState(pressScale, enableHaptics, "press_scale_wrapper")
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        content()
    }
}

@Composable
fun ExpandCollapseAnimation(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    animationDuration: Int = AnimationDuration.normal,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = tween(
                durationMillis = animationDuration,
                easing = MicroEasing.EmphasizedDecelerate
            ),
            expandFrom = Alignment.Top
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = animationDuration / 2,
                delayMillis = animationDuration / 4,
                easing = MicroEasing.StandardDecelerate
            )
        ),
        exit = shrinkVertically(
            animationSpec = tween(
                durationMillis = animationDuration,
                easing = MicroEasing.EmphasizedAccelerate
            ),
            shrinkTowards = Alignment.Top
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = animationDuration / 2,
                easing = MicroEasing.StandardAccelerate
            )
        )
    ) {
        content()
    }
}

@Composable
fun ExpandableContainer(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enableHaptics: Boolean = true,
    header: @Composable (isExpanded: Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MicroSpring.Bouncy,
        label = "expand_rotation"
    )
    Column(modifier = modifier) {
        Box(
            modifier = Modifier.clickable {
                if (enableHaptics) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onExpandChange(!expanded)
            }
        ) {
            header(expanded)
        }
        ExpandCollapseAnimation(expanded = expanded) { content() }
    }
}

@Composable
fun IconMorphAnimation(
    isFirstState: Boolean,
    firstIcon: @Composable () -> Unit,
    secondIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    animationDuration: Int = AnimationDuration.normal
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFirstState) 0f else 180f,
        animationSpec = tween(durationMillis = animationDuration, easing = MicroEasing.Emphasized),
        label = "icon_rotation"
    )
    val scale by animateFloatAsState(
        targetValue = if (rotation in 45f..135f) 0.8f else 1f,
        animationSpec = MicroSpring.Snappy,
        label = "icon_scale"
    )
    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = rotation
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isFirstState,
            animationSpec = tween(durationMillis = animationDuration, easing = MicroEasing.Standard),
            label = "icon_crossfade"
        ) { showFirst ->
            if (showFirst) firstIcon() else secondIcon()
        }
    }
}

@Composable
fun RipplePulse(
    isActive: Boolean,
    rippleColor: Color = GoldPrimary,
    rippleCount: Int = 3,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple_pulse")
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isActive) {
            repeat(rippleCount) { index ->
                val delay = index * 400
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 2.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 1500,
                            delayMillis = delay,
                            easing = MicroEasing.StandardDecelerate
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ripple_scale_$index"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 1500,
                            delayMillis = delay,
                            easing = MicroEasing.StandardAccelerate
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ripple_alpha_$index"
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .scale(scale)
                        .alpha(alpha)
                        .background(
                            color = rippleColor.copy(alpha = alpha * 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }
        content()
    }
}

@Composable
fun BounceClick(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableHaptics: Boolean = true,
    bounceScale: Float = PressScale.pronounced,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var hasTriggeredBounce by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> bounceScale
            hasTriggeredBounce -> 1.05f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "bounce_scale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        if (enableHaptics) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        hasTriggeredBounce = true
                        if (enableHaptics) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onClick()
                        coroutineScope.launch {
                            delay(200)
                            hasTriggeredBounce = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

data class SwipeAction(
    val icon: @Composable () -> Unit,
    val backgroundColor: Color,
    val onAction: () -> Unit,
    val weight: Float = 1f
)

@Composable
private fun BoxScope.SwipeActionRow(
    actions: List<SwipeAction>,
    actionWidth: Dp,
    alpha: Float,
    arrangement: Arrangement.Horizontal,
    enableHaptics: Boolean,
    onResetOffset: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .matchParentSize()
            .alpha(alpha),
        horizontalArrangement = arrangement
    ) {
        actions.forEach { action ->
            Box(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .background(action.backgroundColor)
                    .clickable {
                        if (enableHaptics) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        action.onAction()
                        onResetOffset()
                    },
                contentAlignment = Alignment.Center
            ) {
                action.icon()
            }
        }
    }
}

@Composable
fun SwipeRevealActions(
    leftActions: List<SwipeAction> = emptyList(),
    rightActions: List<SwipeAction> = emptyList(),
    modifier: Modifier = Modifier,
    actionWidth: Dp = 72.dp,
    enableHaptics: Boolean = true,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val leftActionsWidthPx = with(density) { (actionWidth * leftActions.size).toPx() }
    val rightActionsWidthPx = with(density) { (actionWidth * rightActions.size).toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = MicroSpring.Snappy,
        label = "swipe_offset"
    )
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (leftActions.isNotEmpty()) {
            SwipeActionRow(
                actions = leftActions,
                actionWidth = actionWidth,
                alpha = (animatedOffsetX / leftActionsWidthPx).coerceIn(0f, 1f),
                arrangement = Arrangement.Start,
                enableHaptics = enableHaptics,
                onResetOffset = { offsetX = 0f }
            )
        }
        if (rightActions.isNotEmpty()) {
            SwipeActionRow(
                actions = rightActions,
                actionWidth = actionWidth,
                alpha = (-animatedOffsetX / rightActionsWidthPx).coerceIn(0f, 1f),
                arrangement = Arrangement.End,
                enableHaptics = enableHaptics,
                onResetOffset = { offsetX = 0f }
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { hasTriggeredHaptic = false },
                        onDragEnd = {
                            offsetX = when {
                                offsetX > leftActionsWidthPx / 2 -> leftActionsWidthPx
                                offsetX < -rightActionsWidthPx / 2 -> -rightActionsWidthPx
                                else -> 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-rightActionsWidthPx, leftActionsWidthPx)
                            if (enableHaptics && !hasTriggeredHaptic) {
                                val leftThreshold = leftActionsWidthPx / 2
                                val rightThreshold = -rightActionsWidthPx / 2
                                if ((offsetX > leftThreshold && leftActions.isNotEmpty()) ||
                                    (offsetX < rightThreshold && rightActions.isNotEmpty())) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hasTriggeredHaptic = true
                                }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
fun ProgressMorph(
    progress: Float,
    progressColor: Color = GoldPrimary,
    backgroundColor: Color = Color.White.copy(alpha = 0.08f),
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    cornerRadius: Dp = 4.dp,
    showPulse: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tweenNormalDecel(),
        label = "progress_morph"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "progress_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AnimationDuration.pulse, easing = MicroEasing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (showPulse) progressColor.copy(alpha = pulseAlpha) else progressColor
                )
        )
    }
}

@Composable
fun CircularProgressMorph(
    progress: Float,
    progressColor: Color = GoldPrimary,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    showIndeterminate: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tweenNormalDecel(),
        label = "circular_progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "circular_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AnimationDuration.ambient, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                if (showIndeterminate) rotationZ = rotation
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(color = progressColor.copy(alpha = 0.2f), shape = CircleShape)
        )
        Box(
            modifier = Modifier
                .size(size - strokeWidth * 2)
                .background(color = BackgroundBase, shape = CircleShape)
        )
    }
}

fun Modifier.shimmerEffect(
    isLoading: Boolean = true,
    shimmerColor: Color = Color.White,
    baseColor: Color = Color.White.copy(alpha = 0.08f)
): Modifier = composed {
    if (!isLoading) return@composed this
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AnimationDuration.shimmer, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    this.graphicsLayer {
        alpha = 0.7f + (shimmerOffset * 0.15f).coerceIn(0f, 0.3f)
    }
}

@Composable
fun TouchFeedback(
    modifier: Modifier = Modifier,
    feedbackColor: Color = GoldPrimary.copy(alpha = 0.3f),
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var touchPosition by remember { mutableStateOf(Offset.Zero) }
    var showRipple by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val rippleScale by animateFloatAsState(
        targetValue = if (showRipple) 3f else 0f,
        animationSpec = tween(
            durationMillis = AnimationDuration.normal,
            easing = MicroEasing.StandardDecelerate
        ),
        label = "ripple_scale"
    )
    val rippleAlpha by animateFloatAsState(
        targetValue = if (showRipple) 0f else 1f,
        animationSpec = tween(
            durationMillis = AnimationDuration.normal,
            easing = MicroEasing.StandardAccelerate
        ),
        label = "ripple_alpha"
    )
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    touchPosition = offset
                    showRipple = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                    coroutineScope.launch {
                        delay(AnimationDuration.normal.toLong())
                        showRipple = false
                    }
                }
            )
        }
    ) {
        content()
        if (showRipple) {
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (touchPosition.x - 24.dp.toPx()).roundToInt(),
                            (touchPosition.y - 24.dp.toPx()).roundToInt()
                        )
                    }
                    .size(48.dp)
                    .scale(rippleScale)
                    .alpha(rippleAlpha)
                    .background(color = feedbackColor, shape = CircleShape)
            )
        }
    }
}

fun Modifier.elasticOverscroll(
    overscrollFactor: Float = 0.3f
): Modifier = composed {
    var overscrollOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = overscrollOffset,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "elastic_offset"
    )
    this.graphicsLayer { translationY = animatedOffset }
}

@Composable
fun AnimatedScaleFade(
    visible: Boolean,
    modifier: Modifier = Modifier,
    initialScale: Float = 0.85f,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(initialScale = initialScale, animationSpec = MicroSpring.Bouncy) +
            fadeIn(
                animationSpec = tween(
                    durationMillis = AnimationDuration.normal,
                    easing = MicroEasing.StandardDecelerate
                )
            ),
        exit = scaleOut(
            targetScale = initialScale,
            animationSpec = tween(
                durationMillis = AnimationDuration.fast,
                easing = MicroEasing.StandardAccelerate
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AnimationDuration.fast,
                easing = MicroEasing.StandardAccelerate
            )
        )
    ) {
        content()
    }
}

@Composable
fun HeartbeatEffect(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    beatScale: Float = 1.15f,
    beatDuration: Int = 200,
    content: @Composable () -> Unit
) {
    var currentBeat by remember { mutableIntStateOf(0) }
    LaunchedEffect(isActive) {
        while (isActive) {
            currentBeat++
            delay(beatDuration.toLong())
            currentBeat++
            delay(beatDuration.toLong())
            delay(600)
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (isActive && currentBeat % 2 == 1) beatScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "heartbeat_scale"
    )
    Box(modifier = modifier.scale(scale)) { content() }
}

@Composable
fun WobbleEffect(
    isWobbling: Boolean,
    modifier: Modifier = Modifier,
    wobbleAngle: Float = 3f,
    wobbleDuration: Int = 100,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wobble")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -wobbleAngle,
        targetValue = wobbleAngle,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = wobbleDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble_rotation"
    )
    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = if (isWobbling) rotation else 0f
        }
    ) {
        content()
    }
}
