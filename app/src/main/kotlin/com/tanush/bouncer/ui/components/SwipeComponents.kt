package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableCard(
    modifier: Modifier = Modifier,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    leftIcon: ImageVector = Icons.Default.Delete,
    rightIcon: ImageVector = Icons.Default.Favorite,
    leftColor: Color = ErrorRed,
    rightColor: Color = SuccessGreen,
    swipeThreshold: Float = 0.4f,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var isRevealed by remember { mutableStateOf(false) }
    
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "swipeOffset"
    )
    
    Box(modifier = modifier) {
        // Background actions
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left action
            if (onSwipeLeft != null) {
                Icon(
                    imageVector = leftIcon,
                    contentDescription = "Left action",
                    tint = leftColor,
                    modifier = Modifier
                        .alpha(if (animatedOffsetX > 0) (animatedOffsetX / 200).coerceIn(0f, 1f) else 0f)
                        .graphicsLayer {
                            scaleX = 1f + (animatedOffsetX / 200).coerceIn(0f, 0.3f)
                            scaleY = 1f + (animatedOffsetX / 200).coerceIn(0f, 0.3f)
                        }
                )
            }
            
            // Right action
            if (onSwipeRight != null) {
                Icon(
                    imageVector = rightIcon,
                    contentDescription = "Right action",
                    tint = rightColor,
                    modifier = Modifier
                        .alpha(if (animatedOffsetX < 0) (abs(animatedOffsetX) / 200).coerceIn(0f, 1f) else 0f)
                        .graphicsLayer {
                            scaleX = 1f + (abs(animatedOffsetX) / 200).coerceIn(0f, 0.3f)
                            scaleY = 1f + (abs(animatedOffsetX) / 200).coerceIn(0f, 0.3f)
                        }
                )
            }
        }
        
        // Content card
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(offsetX) > size.width * swipeThreshold) {
                                if (offsetX > 0) {
                                    onSwipeLeft?.invoke()
                                } else {
                                    onSwipeRight?.invoke()
                                }
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            offsetX = when {
                                onSwipeLeft != null && onSwipeRight != null -> newOffset.coerceIn(-300f, 300f)
                                onSwipeLeft != null -> newOffset.coerceIn(0f, 300f)
                                onSwipeRight != null -> newOffset.coerceIn(-300f, 0f)
                                else -> 0f
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
fun ExpandableCard(
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expandRotation"
    )
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, _ -> }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                title()
            }
            
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.graphicsLayer {
                    rotationZ = rotation
                }
            )
        }
        
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            content()
        }
    }
}

@Composable
fun DraggableCard(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    dismissThreshold: Float = 0.5f,
    content: @Composable () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "dragOffset"
    )
    
    val alpha by animateFloatAsState(
        targetValue = 1f - (abs(offsetY) / 500).coerceIn(0f, 0.5f),
        label = "dragAlpha"
    )
    
    Box(
        modifier = modifier
            .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
            .alpha(alpha)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(offsetY) > size.height * dismissThreshold) {
                            onDismiss()
                        } else {
                            offsetY = 0f
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetY += dragAmount
                    }
                )
            }
    ) {
        content()
    }
}
