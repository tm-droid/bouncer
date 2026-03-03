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
import io.sentry.Sentry

// ==========================================
// BOUNCER - Micro Interactions Library
// Material 3 Motion Specs with Haptic Feedback
// ==========================================

/**
 * Material 3 Easing curves for micro interactions
 */
private object MicroEasing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * Spring configurations for micro interactions
 */
private object MicroSpring {
    // Bouncy: High energy, playful
    val Bouncy = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)
    
    // Snappy: Quick, responsive
    val Snappy = spring<Float>(dampingRatio = 0.75f, stiffness = 600f)
    
    // Gentle: Subtle, calm
    val Gentle = spring<Float>(dampingRatio = 0.8f, stiffness = 200f)
}

/**
 * PressScaleEffect - Modifier that adds scale-down effect on press with optional haptic feedback
 * 
 * @param pressScale The scale to animate to when pressed (default: 0.96f)
 * @param enableHaptics Whether to trigger haptic feedback on press
 * @param onClick The callback when clicked
 */
fun Modifier.pressScaleEffect(
    pressScale: Float = PressScale.normal,
    enableHaptics: Boolean = true,
    onClick: () -> Unit = {}
): Modifier = composed {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = MicroSpring.Snappy,
        label = "press_scale"
    )
    
    LaunchedEffect(isPressed) {
        if (isPressed && enableHaptics) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * PressScaleEffect composable wrapper for more complex interactions
 */
@Composable
fun PressScaleEffect(
    modifier: Modifier = Modifier,
    pressScale: Float = PressScale.normal,
    enableHaptics: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = MicroSpring.Snappy,
        label = "press_scale_wrapper"
    )
    
    LaunchedEffect(isPressed) {
        if (isPressed && enableHaptics) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
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

/**
 * ExpandCollapseAnimation - Smooth expand/collapse animation for expandable content
 * 
 * @param expanded Whether the content is expanded
 * @param modifier Modifier for the container
 * @param animationDuration Duration of the animation in milliseconds
 * @param content The expandable content
 */
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

/**
 * Expandable container with header and collapsible content
 */
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
            modifier = Modifier
                .clickable {
                    if (enableHaptics) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onExpandChange(!expanded)
                }
        ) {
            header(expanded)
        }
        
        ExpandCollapseAnimation(expanded = expanded) {
            content()
        }
    }
}

/**
 * IconMorphAnimation - Smooth morphing between two icon states
 * 
 * @param isFirstState Whether to show the first icon state
 * @param firstIcon Composable for the first icon
 * @param secondIcon Composable for the second icon
 * @param modifier Modifier for the container
 */
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
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = MicroEasing.Emphasized
        ),
        label = "icon_rotation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (rotation in 45f..135f) 0.8f else 1f,
        animationSpec = MicroSpring.Snappy,
        label = "icon_scale"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isFirstState,
            animationSpec = tween(
                durationMillis = animationDuration,
                easing = MicroEasing.Standard
            ),
            label = "icon_crossfade"
        ) { showFirst ->
            if (showFirst) {
                firstIcon()
            } else {
                secondIcon()
            }
        }
    }
}

/**
 * RipplePulse - Animated ripple effect emanating from center
 * 
 * @param isActive Whether the pulse animation is active
 * @param rippleColor Color of the ripple
 * @param rippleCount Number of ripple circles
 * @param modifier Modifier for the container
 */
@Composable
fun RipplePulse(
    isActive: Boolean,
    rippleColor: Color = GoldPrimary,
    rippleCount: Int = 3,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple_pulse")
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            repeat(rippleCount) { index ->
                val delay = (index * 400)
                
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

/**
 * BounceClick - Button with bouncy click animation
 * 
 * @param onClick Callback when clicked
 * @param modifier Modifier for the button
 * @param enableHaptics Whether to enable haptic feedback
 * @param bounceScale Scale factor when pressed
 * @param content Button content
 */
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
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 500f
        ),
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

/**
 * Swipe action data class
 */
data class SwipeAction(
    val icon: @Composable () -> Unit,
    val backgroundColor: Color,
    val onAction: () -> Unit,
    val weight: Float = 1f
)

/**
 * SwipeRevealActions - Swipe-to-reveal action pattern
 * 
 * @param leftActions List of actions to reveal when swiping right
 * @param rightActions List of actions to reveal when swiping left
 * @param modifier Modifier for the container
 * @param actionWidth Width of each action button
 * @param enableHaptics Whether to enable haptic feedback
 * @param content The main content that can be swiped
 */
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
    val coroutineScope = rememberCoroutineScope()
    
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
        // Left actions (revealed when swiping right)
        if (leftActions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .alpha((animatedOffsetX / leftActionsWidthPx).coerceIn(0f, 1f)),
                horizontalArrangement = Arrangement.Start
            ) {
                leftActions.forEach { action ->
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
                                offsetX = 0f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        action.icon()
                    }
                }
            }
        }
        
        // Right actions (revealed when swiping left)
        if (rightActions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .alpha((-animatedOffsetX / rightActionsWidthPx).coerceIn(0f, 1f)),
                horizontalArrangement = Arrangement.End
            ) {
                rightActions.forEach { action ->
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
                                offsetX = 0f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        action.icon()
                    }
                }
            }
        }
        
        // Main content
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            hasTriggeredHaptic = false
                        },
                        onDragEnd = {
                            // Snap to fully revealed or hidden
                            offsetX = when {
                                offsetX > leftActionsWidthPx / 2 -> leftActionsWidthPx
                                offsetX < -rightActionsWidthPx / 2 -> -rightActionsWidthPx
                                else -> 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-rightActionsWidthPx, leftActionsWidthPx)
                            
                            // Trigger haptic at threshold
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

/**
 * ProgressMorph - Animated progress indicator with morphing capability
 * 
 * @param progress Current progress value (0f to 1f)
 * @param progressColor Color of the progress indicator
 * @param backgroundColor Background color of the track
 * @param modifier Modifier for the container
 * @param height Height of the progress bar
 * @param cornerRadius Corner radius of the progress bar
 */
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
        animationSpec = tween(
            durationMillis = AnimationDuration.normal,
            easing = MicroEasing.EmphasizedDecelerate
        ),
        label = "progress_morph"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "progress_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationDuration.pulse,
                easing = MicroEasing.Standard
            ),
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
                    if (showPulse) progressColor.copy(alpha = pulseAlpha)
                    else progressColor
                )
        )
    }
}

/**
 * Circular progress with morph animation
 */
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
        animationSpec = tween(
            durationMillis = AnimationDuration.normal,
            easing = MicroEasing.EmphasizedDecelerate
        ),
        label = "circular_progress"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "circular_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationDuration.ambient,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                if (showIndeterminate) {
                    rotationZ = rotation
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Background track
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    color = progressColor.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        )
        
        // Inner circle (cutout effect)
        Box(
            modifier = Modifier
                .size(size - strokeWidth * 2)
                .background(
                    color = BackgroundBase,
                    shape = CircleShape
                )
        )
    }
}

/**
 * Shimmer loading effect modifier
 */
fun Modifier.shimmerEffect(
    isLoading: Boolean = true,
    shimmerColor: Color = Color.White,
    baseColor: Color = Color.White.copy(alpha = 0.08f)
): Modifier = composed {
    if (!isLoading) {
        try {
            return@composed this
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationDuration.shimmer,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    this.graphicsLayer {
        // Apply shimmer transform
        alpha = 0.7f + (shimmerOffset * 0.15f).coerceIn(0f, 0.3f)
    }
}

/**
 * Touch feedback with visual ripple
 */
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
        modifier = modifier
            .pointerInput(Unit) {
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
                    .background(
                        color = feedbackColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Elastic scroll overscroll effect
 */
fun Modifier.elasticOverscroll(
    overscrollFactor: Float = 0.3f
): Modifier = composed {
    var overscrollOffset by remember { mutableFloatStateOf(0f) }
    
    val animatedOffset by animateFloatAsState(
        targetValue = overscrollOffset,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "elastic_offset"
    )
    
    this.graphicsLayer {
        translationY = animatedOffset
    }
}

/**
 * Animated visibility with scale and fade
 */
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
        enter = scaleIn(
            initialScale = initialScale,
            animationSpec = MicroSpring.Bouncy
        ) + fadeIn(
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

/**
 * Heart beat animation effect
 */
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
            delay(600) // Pause between beats
        }
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isActive && currentBeat % 2 == 1) beatScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 600f
        ),
        label = "heartbeat_scale"
    )
    
    Box(
        modifier = modifier.scale(scale)
    ) {
        content()
    }
}

/**
 * Wobble animation for attention-grabbing
 */
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
            animation = tween(
                durationMillis = wobbleDuration,
                easing = LinearEasing
            ),
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
