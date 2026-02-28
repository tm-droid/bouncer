package com.tanush.bouncer.ui.gestures

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Spring configurations for gesture animations
 */
object GestureSpringConfig {
    val Snappy = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = 600f
    )
    
    val Bouncy = spring<Float>(
        dampingRatio = 0.5f,
        stiffness = 400f
    )
    
    val Smooth = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 300f
    )
    
    val SnappyOffset = spring<Offset>(
        dampingRatio = 0.7f,
        stiffness = 600f
    )
    
    val BouncyOffset = spring<Offset>(
        dampingRatio = 0.5f,
        stiffness = 400f
    )
    
    val SmoothOffset = spring<Offset>(
        dampingRatio = 0.8f,
        stiffness = 300f
    )
}

/**
 * State class for tracking multi-touch gestures
 */
data class GestureState(
    val fingerCount: Int = 0,
    val centroid: Offset = Offset.Zero,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val pan: Offset = Offset.Zero,
    val isActive: Boolean = false
)

/**
 * Composable that tracks multi-touch gesture states
 */
@Composable
fun rememberGestureState(): GestureState {
    var gestureState by remember { mutableStateOf(GestureState()) }
    return gestureState
}

/**
 * Modifier for two-finger rotation of views (useful for pitch map)
 * 
 * @param onRotationChange Callback when rotation changes, returns the new rotation angle in degrees
 * @param enabled Whether the gesture is enabled
 */
fun Modifier.twoFingerRotation(
    onRotationChange: (Float) -> Unit,
    enabled: Boolean = true
): Modifier = if (enabled) {
    this.pointerInput(Unit) {
        awaitEachGesture {
            var rotation = 0f
            var pastTouchSlop = false
            val touchSlop = viewConfiguration.touchSlop

            awaitFirstDown(requireUnconsumed = false)
            
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                
                if (!canceled && event.changes.size >= 2) {
                    val rotationChange = event.calculateRotation()
                    
                    if (!pastTouchSlop) {
                        rotation += rotationChange
                        if (abs(rotation) > touchSlop / 10) {
                            pastTouchSlop = true
                        }
                    }
                    
                    if (pastTouchSlop) {
                        onRotationChange(rotationChange)
                        event.changes.forEach { 
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
} else this

/**
 * Modifier for pinch-to-zoom with bounds and callbacks
 * 
 * @param minScale Minimum allowed scale
 * @param maxScale Maximum allowed scale
 * @param onScaleChange Callback when scale changes
 * @param onZoomStart Callback when zoom gesture starts
 * @param onZoomEnd Callback when zoom gesture ends
 */
fun Modifier.pinchToZoom(
    minScale: Float = 0.5f,
    maxScale: Float = 3f,
    onScaleChange: (Float) -> Unit,
    onZoomStart: () -> Unit = {},
    onZoomEnd: (Float) -> Unit = {}
): Modifier = composed {
    var scale by remember { mutableFloatStateOf(1f) }
    var isZooming by remember { mutableStateOf(false) }
    
    this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                
                if (!canceled && event.changes.size >= 2) {
                    if (!isZooming) {
                        isZooming = true
                        onZoomStart()
                    }
                    
                    val zoomChange = event.calculateZoom()
                    val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
                    
                    if (newScale != scale) {
                        scale = newScale
                        onScaleChange(scale)
                    }
                    
                    event.changes.forEach { 
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
            
            if (isZooming) {
                isZooming = false
                onZoomEnd(scale)
            }
        }
    }
}

/**
 * Modifier for swipe-between-items carousel navigation
 * 
 * @param itemCount Total number of items in the carousel
 * @param currentIndex Current selected index
 * @param onIndexChange Callback when the index changes
 * @param swipeThreshold Minimum swipe distance to trigger index change
 */
fun Modifier.swipeBetweenItems(
    itemCount: Int,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    swipeThreshold: Float = 100f
): Modifier = composed {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    this
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .pointerInput(currentIndex, itemCount) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        val targetIndex = when {
                            offsetX.value > swipeThreshold && currentIndex > 0 -> currentIndex - 1
                            offsetX.value < -swipeThreshold && currentIndex < itemCount - 1 -> currentIndex + 1
                            else -> currentIndex
                        }
                        
                        offsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = GestureSpringConfig.Snappy
                        )
                        
                        if (targetIndex != currentIndex) {
                            onIndexChange(targetIndex)
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        offsetX.animateTo(0f, GestureSpringConfig.Snappy)
                    }
                },
                onHorizontalDrag = { _, dragAmount ->
                    scope.launch {
                        val newOffset = offsetX.value + dragAmount
                        // Add resistance at edges
                        val resistance = when {
                            currentIndex == 0 && newOffset > 0 -> 0.3f
                            currentIndex == itemCount - 1 && newOffset < 0 -> 0.3f
                            else -> 1f
                        }
                        offsetX.snapTo(offsetX.value + dragAmount * resistance)
                    }
                }
            )
        }
}

/**
 * Modifier for long press with quick action menu
 * 
 * @param onLongPress Callback when long press is detected with the position
 * @param onShowMenu Callback to show the action menu
 * @param hapticFeedback Whether to trigger haptic feedback
 */
fun Modifier.longPressWithMenu(
    onLongPress: (Offset) -> Unit,
    onShowMenu: (Offset) -> Unit,
    hapticFeedback: Boolean = true
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(
        onLongPress = { offset ->
            onLongPress(offset)
            onShowMenu(offset)
        }
    )
}

/**
 * Reorder item state for drag-to-reorder functionality
 */
data class ReorderState(
    val isDragging: Boolean = false,
    val draggedIndex: Int = -1,
    val dragOffset: Offset = Offset.Zero
)

/**
 * Modifier for drag-to-reorder in lists
 * 
 * @param index The index of this item in the list
 * @param onMove Callback when item should be moved from one index to another
 * @param onDragStart Callback when drag starts
 * @param onDragEnd Callback when drag ends
 */
fun Modifier.dragToReorder(
    index: Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: (Int) -> Unit = {},
    onDragEnd: () -> Unit = {}
): Modifier = composed {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val animatedOffset = remember { Animatable(0f) }
    
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            animatedOffset.animateTo(0f, GestureSpringConfig.Snappy)
        }
    }
    
    this
        .offset { IntOffset(0, if (isDragging) offsetY.roundToInt() else animatedOffset.value.roundToInt()) }
        .graphicsLayer {
            if (isDragging) {
                scaleX = 1.05f
                scaleY = 1.05f
                shadowElevation = 8f
            }
        }
        .pointerInput(index) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    isDragging = true
                    onDragStart(index)
                },
                onDragEnd = {
                    isDragging = false
                    offsetY = 0f
                    onDragEnd()
                },
                onDragCancel = {
                    isDragging = false
                    offsetY = 0f
                    onDragEnd()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    offsetY += dragAmount.y
                    
                    // Calculate target index based on offset
                    val itemHeight = 56.dp.toPx() // Approximate item height
                    val targetIndex = (index + (offsetY / itemHeight).roundToInt()).coerceAtLeast(0)
                    
                    if (targetIndex != index) {
                        onMove(index, targetIndex)
                    }
                }
            )
        }
}

/**
 * Modifier for fling-to-dismiss with velocity-based dismissal
 * 
 * @param onDismiss Callback when item is dismissed
 * @param dismissThreshold Screen percentage required to dismiss (0.0 to 1.0)
 * @param velocityThreshold Velocity required for instant dismiss
 */
fun Modifier.flingDismiss(
    onDismiss: () -> Unit,
    dismissThreshold: Float = 0.4f,
    velocityThreshold: Float = 1000f
): Modifier = composed {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isDismissed by remember { mutableStateOf(false) }
    
    this
        .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
        .graphicsLayer {
            val progress = abs(offsetX.value) / size.width
            alpha = 1f - (progress * 0.5f)
            rotationZ = (offsetX.value / size.width) * 15f
        }
        .pointerInput(Unit) {
            var velocity = Offset.Zero
            
            detectDragGestures(
                onDragEnd = {
                    if (isDismissed) return@detectDragGestures
                    
                    val velocityMagnitude = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
                    val distanceRatio = abs(offsetX.value) / size.width
                    
                    scope.launch {
                        if (velocityMagnitude > velocityThreshold || distanceRatio > dismissThreshold) {
                            // Dismiss
                            val direction = if (offsetX.value > 0) 1 else -1
                            isDismissed = true
                            
                            launch {
                                offsetX.animateTo(
                                    targetValue = size.width.toFloat() * direction * 1.5f,
                                    animationSpec = GestureSpringConfig.Snappy
                                )
                            }
                            launch {
                                offsetY.animateTo(
                                    targetValue = offsetY.value + velocity.y * 0.3f,
                                    animationSpec = GestureSpringConfig.Snappy
                                )
                            }
                            
                            onDismiss()
                        } else {
                            // Snap back
                            launch { offsetX.animateTo(0f, GestureSpringConfig.Bouncy) }
                            launch { offsetY.animateTo(0f, GestureSpringConfig.Bouncy) }
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        launch { offsetX.animateTo(0f, GestureSpringConfig.Bouncy) }
                        launch { offsetY.animateTo(0f, GestureSpringConfig.Bouncy) }
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    velocity = Offset(dragAmount.x * 10, dragAmount.y * 10)
                    
                    scope.launch {
                        offsetX.snapTo(offsetX.value + dragAmount.x)
                        offsetY.snapTo(offsetY.value + dragAmount.y * 0.3f)
                    }
                }
            )
        }
}

/**
 * Composable for tracking multi-touch gesture states
 */
@Composable
fun GestureStateTracker(
    onStateChange: (GestureState) -> Unit,
    content: @Composable () -> Unit
) {
    var gestureState by remember { mutableStateOf(GestureState()) }
    
    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                
                gestureState = gestureState.copy(
                    fingerCount = 1,
                    centroid = firstDown.position,
                    isActive = true
                )
                onStateChange(gestureState)
                
                var totalRotation = 0f
                var totalScale = 1f
                var totalPan = Offset.Zero
                
                do {
                    val event = awaitPointerEvent()
                    val fingerCount = event.changes.count { it.pressed }
                    
                    if (fingerCount >= 2) {
                        totalRotation += event.calculateRotation()
                        totalScale *= event.calculateZoom()
                        totalPan += event.calculatePan()
                    }
                    
                    val centroid = event.calculateCentroid(useCurrent = true)
                    
                    gestureState = GestureState(
                        fingerCount = fingerCount,
                        centroid = centroid,
                        rotation = totalRotation,
                        scale = totalScale,
                        pan = totalPan,
                        isActive = true
                    )
                    onStateChange(gestureState)
                    
                } while (event.changes.any { it.pressed })
                
                gestureState = GestureState(isActive = false)
                onStateChange(gestureState)
            }
        }
    ) {
        content()
    }
}

/**
 * Combined transform gesture modifier for scale, rotation, and pan
 */
fun Modifier.transformGestures(
    onScale: (Float) -> Unit = {},
    onRotate: (Float) -> Unit = {},
    onPan: (Offset) -> Unit = {},
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var gestureStarted = false

        awaitFirstDown(requireUnconsumed = false)
        
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            
            if (!canceled && event.changes.size >= 2) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (!gestureStarted) {
                        gestureStarted = true
                        onGestureStart()
                    }
                    
                    onScale(zoomChange)
                    onRotate(rotationChange)
                    onPan(panChange)
                    
                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })
        
        if (gestureStarted) {
            onGestureEnd()
        }
    }
}
