package com.tanush.bouncer.ui.gestures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swipe action configuration
 */
data class SwipeAction(
    val icon: ImageVector,
    val backgroundColor: Color,
    val iconTint: Color = Color.White,
    val onAction: () -> Unit,
    val threshold: Float = 100f,
    val label: String = ""
)

/**
 * Swipe progress state
 */
data class SwipeProgressState(
    val progress: Float = 0f,
    val direction: SwipeDirection = SwipeDirection.None,
    val hasReachedThreshold: Boolean = false
)

enum class SwipeDirection {
    Left, Right, None
}

/**
 * Swipe to favorite action with star icon reveal
 */
@Composable
fun SwipeToFavorite(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SwipeActionContainer(
        rightAction = SwipeAction(
            icon = if (isFavorite) Icons.Filled.Star else Icons.Default.Star,
            backgroundColor = Color(0xFFFFD700),
            iconTint = Color.White,
            onAction = onToggleFavorite,
            threshold = 100f
        ),
        modifier = modifier,
        content = content
    )
}

/**
 * Swipe to share action with share icon reveal
 */
@Composable
fun SwipeToShare(
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SwipeActionContainer(
        rightAction = SwipeAction(
            icon = Icons.Default.Share,
            backgroundColor = Color(0xFF2196F3),
            iconTint = Color.White,
            onAction = onShare,
            threshold = 100f
        ),
        modifier = modifier,
        content = content
    )
}

/**
 * Swipe to hide action for dismissing content
 */
@Composable
fun SwipeToHide(
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SwipeActionContainer(
        leftAction = SwipeAction(
            icon = Icons.Default.VisibilityOff,
            backgroundColor = Color(0xFF757575),
            iconTint = Color.White,
            onAction = onHide,
            threshold = 120f
        ),
        modifier = modifier,
        content = content
    )
}

/**
 * Bi-directional swipe supporting left and right actions simultaneously
 */
@Composable
fun BiDirectionalSwipe(
    leftAction: SwipeAction?,
    rightAction: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SwipeActionContainer(
        leftAction = leftAction,
        rightAction = rightAction,
        modifier = modifier,
        content = content
    )
}

/**
 * Swipe progress indicator showing action threshold
 */
@Composable
fun SwipeProgressIndicator(
    progress: Float,
    direction: SwipeDirection,
    hasReachedThreshold: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (hasReachedThreshold) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(100),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size((48 * scale).dp)
            .clip(CircleShape)
            .alpha(alpha)
            .background(
                when (direction) {
                    SwipeDirection.Left -> Color(0xFFE53935)
                    SwipeDirection.Right -> Color(0xFF43A047)
                    SwipeDirection.None -> Color.Transparent
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (direction != SwipeDirection.None) {
            Icon(
                imageVector = when (direction) {
                    SwipeDirection.Left -> Icons.Default.Delete
                    SwipeDirection.Right -> Icons.Default.Star
                    else -> Icons.Default.Star
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.scale(scale * 0.7f)
            )
        }
    }
}

/**
 * Snap back animation returning to original position
 */
@Composable
fun SnapBackAnimation(
    shouldSnapBack: Boolean,
    offset: Float,
    onSnapComplete: () -> Unit,
    content: @Composable (animatedOffset: Float) -> Unit
) {
    val animatedOffset = remember { Animatable(offset) }
    
    LaunchedEffect(shouldSnapBack) {
        if (shouldSnapBack) {
            animatedOffset.snapTo(offset)
            animatedOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 600f
                )
            )
            onSnapComplete()
        }
    }
    
    content(animatedOffset.value)
}

/**
 * Action confirmation visual feedback on action completion
 */
@Composable
fun ActionConfirmation(
    isVisible: Boolean,
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(1500)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = 400f
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Main swipe action container that handles swipe gestures
 */
@Composable
fun SwipeActionContainer(
    leftAction: SwipeAction? = null,
    rightAction: SwipeAction? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var showConfirmation by remember { mutableStateOf(false) }
    var confirmedAction by remember { mutableStateOf<SwipeAction?>(null) }
    
    val swipeProgress = remember(offsetX) {
        val absOffset = abs(offsetX)
        val threshold = if (offsetX > 0) rightAction?.threshold ?: 100f else leftAction?.threshold ?: 100f
        SwipeProgressState(
            progress = absOffset / threshold,
            direction = when {
                offsetX > 0 -> SwipeDirection.Right
                offsetX < 0 -> SwipeDirection.Left
                else -> SwipeDirection.None
            },
            hasReachedThreshold = absOffset >= threshold
        )
    }
    
    Box(modifier = modifier.fillMaxWidth()) {
        // Background with action icons
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left action background (appears when swiping right)
            if (rightAction != null && offsetX > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.5f)
                        .background(rightAction.backgroundColor),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val iconScale by animateFloatAsState(
                        targetValue = if (swipeProgress.hasReachedThreshold) 1.2f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = 400f
                        ),
                        label = "iconScale"
                    )
                    
                    Icon(
                        imageVector = rightAction.icon,
                        contentDescription = rightAction.label,
                        tint = rightAction.iconTint,
                        modifier = Modifier
                            .padding(start = 24.dp)
                            .size(28.dp)
                            .scale(iconScale)
                    )
                }
            }
            
            // Right action background (appears when swiping left)
            if (leftAction != null && offsetX < 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .background(leftAction.backgroundColor),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    val iconScale by animateFloatAsState(
                        targetValue = if (swipeProgress.hasReachedThreshold) 1.2f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = 400f
                        ),
                        label = "iconScale"
                    )
                    
                    Icon(
                        imageVector = leftAction.icon,
                        contentDescription = leftAction.label,
                        tint = leftAction.iconTint,
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(28.dp)
                            .scale(iconScale)
                    )
                }
            }
        }
        
        // Content with swipe gesture
        Surface(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.value.roundToInt(), 0) }
                .pointerInput(leftAction, rightAction) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val action = when {
                                    offsetX > (rightAction?.threshold ?: Float.MAX_VALUE) -> rightAction
                                    offsetX < -(leftAction?.threshold ?: Float.MAX_VALUE) -> leftAction
                                    else -> null
                                }
                                
                                if (action != null) {
                                    // Execute action
                                    confirmedAction = action
                                    showConfirmation = true
                                    action.onAction()
                                }
                                
                                // Animate back to original position
                                animatedOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = 600f
                                    )
                                )
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                animatedOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = 600f
                                    )
                                )
                                offsetX = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            
                            // Only allow swipe in directions that have actions
                            val newOffset = offsetX + dragAmount
                            val canSwipeRight = rightAction != null && newOffset >= 0
                            val canSwipeLeft = leftAction != null && newOffset <= 0
                            
                            if (canSwipeRight || canSwipeLeft) {
                                offsetX = newOffset
                                scope.launch {
                                    animatedOffset.snapTo(offsetX)
                                }
                            }
                        }
                    )
                },
            color = MaterialTheme.colorScheme.surface
        ) {
            content()
        }
        
        // Confirmation overlay
        if (showConfirmation && confirmedAction != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ActionConfirmation(
                    isVisible = showConfirmation,
                    icon = confirmedAction!!.icon,
                    backgroundColor = confirmedAction!!.backgroundColor,
                    onDismiss = {
                        showConfirmation = false
                        confirmedAction = null
                    }
                )
            }
        }
    }
}

/**
 * Swipe action row with multiple actions revealed
 */
@Composable
fun SwipeActionsRow(
    actions: List<SwipeAction>,
    revealProgress: Float,
    alignment: Alignment.Horizontal = Alignment.End,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                alpha = revealProgress.coerceIn(0f, 1f)
                scaleX = (0.8f + revealProgress * 0.2f).coerceIn(0f, 1f)
                scaleY = (0.8f + revealProgress * 0.2f).coerceIn(0f, 1f)
            },
        horizontalArrangement = when (alignment) {
            Alignment.Start -> Arrangement.Start
            Alignment.End -> Arrangement.End
            else -> Arrangement.Center
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEachIndexed { index, action ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(action.backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    tint = action.iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Dismissible item with swipe gesture
 */
@Composable
fun DismissibleItem(
    onDismiss: () -> Unit,
    dismissDirection: SwipeDirection = SwipeDirection.Left,
    dismissThreshold: Float = 0.5f,
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isDismissed by remember { mutableStateOf(false) }
    
    if (isDismissed) return
    
    Box(modifier = modifier) {
        // Background
        Box(
            modifier = Modifier.matchParentSize()
        ) {
            background()
        }
        
        // Foreground content
        Surface(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.value.roundToInt(), 0) }
                .graphicsLayer {
                    alpha = 1f - (abs(animatedOffset.value) / size.width).coerceIn(0f, 0.5f)
                }
                .pointerInput(dismissDirection) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val dismissed = abs(offsetX) / size.width > dismissThreshold
                                
                                if (dismissed) {
                                    val targetOffset = if (offsetX > 0) size.width.toFloat() else -size.width.toFloat()
                                    animatedOffset.animateTo(
                                        targetValue = targetOffset,
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 300f
                                        )
                                    )
                                    isDismissed = true
                                    onDismiss()
                                } else {
                                    animatedOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.7f,
                                            stiffness = 600f
                                        )
                                    )
                                    offsetX = 0f
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            
                            val canSwipe = when (dismissDirection) {
                                SwipeDirection.Left -> dragAmount < 0 || offsetX < 0
                                SwipeDirection.Right -> dragAmount > 0 || offsetX > 0
                                SwipeDirection.None -> true
                            }
                            
                            if (canSwipe) {
                                offsetX += dragAmount
                                scope.launch {
                                    animatedOffset.snapTo(offsetX)
                                }
                            }
                        }
                    )
                },
            color = MaterialTheme.colorScheme.surface
        ) {
            content()
        }
    }
}
