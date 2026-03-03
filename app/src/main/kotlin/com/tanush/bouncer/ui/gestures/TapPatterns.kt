package com.tanush.bouncer.ui.gestures

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import io.sentry.Sentry

/**
 * Multi-touch state for complex gestures
 */
data class MultiTouchState(
    val pointerCount: Int = 0,
    val positions: List<Offset> = emptyList(),
    val isActive: Boolean = false
)

/**
 * Edge swipe direction for navigation
 */
enum class EdgeSwipeDirection {
    Left, Right, Top, Bottom
}

/**
 * Modifier for double-tap to expand functionality
 * 
 * @param onDoubleTap Callback when double tap is detected
 * @param doubleTapTimeout Maximum time between taps in milliseconds
 */
fun Modifier.doubleTapToExpand(
    onDoubleTap: () -> Unit,
    doubleTapTimeout: Long = 300L
): Modifier = composed {
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < doubleTapTimeout) {
                    onDoubleTap()
                    lastTapTime = 0L
                } else {
                    lastTapTime = currentTime
                }
            }
        )
    }
}

/**
 * Modifier for triple-tap action (useful for developer/debug modes)
 * 
 * @param onTripleTap Callback when triple tap is detected
 * @param tapTimeout Maximum time between taps in milliseconds
 */
fun Modifier.tripleTapAction(
    onTripleTap: () -> Unit,
    tapTimeout: Long = 400L
): Modifier = composed {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - lastTapTime < tapTimeout) {
                    tapCount++
                    if (tapCount >= 3) {
                        onTripleTap()
                        tapCount = 0
                    }
                } else {
                    tapCount = 1
                }
                
                lastTapTime = currentTime
            }
        )
    }
}

/**
 * Modifier combining tap with hold actions
 * 
 * @param onTap Callback for single tap
 * @param onHold Callback for hold/long press
 * @param holdDuration Duration in milliseconds to trigger hold
 */
fun Modifier.tapWithHold(
    onTap: () -> Unit,
    onHold: () -> Unit,
    holdDuration: Long = 500L
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(
        onTap = { onTap() },
        onLongPress = { onHold() }
    )
}

/**
 * Modifier for multi-touch detection with complex gestures
 * 
 * @param onMultiTouchStart Callback when multi-touch gesture starts
 * @param onMultiTouchEnd Callback when multi-touch gesture ends
 * @param onMultiTouchChange Callback for multi-touch state changes
 */
fun Modifier.multiTouchDetector(
    onMultiTouchStart: (MultiTouchState) -> Unit = {},
    onMultiTouchEnd: () -> Unit = {},
    onMultiTouchChange: (MultiTouchState) -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var isMultiTouch = false
        
        val initialState = MultiTouchState(
            pointerCount = 1,
            positions = listOf(down.position),
            isActive = true
        )
        
        do {
            val event = awaitPointerEvent()
            val pointers = event.changes.filter { it.pressed }
            
            if (pointers.size >= 2 && !isMultiTouch) {
                isMultiTouch = true
                val state = MultiTouchState(
                    pointerCount = pointers.size,
                    positions = pointers.map { it.position },
                    isActive = true
                )
                onMultiTouchStart(state)
            }
            
            if (isMultiTouch) {
                val state = MultiTouchState(
                    pointerCount = pointers.size,
                    positions = pointers.map { it.position },
                    isActive = pointers.isNotEmpty()
                )
                onMultiTouchChange(state)
            }
        } while (event.changes.any { it.pressed })
        
        if (isMultiTouch) {
            onMultiTouchEnd()
        }
    }
}

/**
 * Modifier for edge swipe navigation gestures
 * 
 * @param edge Which edge to detect swipes from
 * @param edgeWidth Width of the edge detection zone in dp
 * @param onEdgeSwipe Callback when edge swipe is detected
 * @param swipeThreshold Minimum swipe distance to trigger
 */
fun Modifier.edgeSwipe(
    edge: EdgeSwipeDirection,
    edgeWidth: Float = 20f,
    onEdgeSwipe: () -> Unit,
    swipeThreshold: Float = 50f
): Modifier = composed {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { edgeWidth.dp.toPx() }
    
    this.pointerInput(edge) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            
            // Check if touch started at the edge
            val isAtEdge = when (edge) {
                EdgeSwipeDirection.Left -> down.position.x < edgeWidthPx
                EdgeSwipeDirection.Right -> down.position.x > size.width - edgeWidthPx
                EdgeSwipeDirection.Top -> down.position.y < edgeWidthPx
                EdgeSwipeDirection.Bottom -> down.position.y > size.height - edgeWidthPx
            }
            
            if (!isAtEdge) return@awaitEachGesture
            
            var totalDrag = Offset.Zero
            
            do {
                val event = awaitPointerEvent()
                val drag = event.changes.firstOrNull()?.positionChange() ?: Offset.Zero
                totalDrag += drag
                
                val swipeDetected = when (edge) {
                    EdgeSwipeDirection.Left -> totalDrag.x > swipeThreshold
                    EdgeSwipeDirection.Right -> totalDrag.x < -swipeThreshold
                    EdgeSwipeDirection.Top -> totalDrag.y > swipeThreshold
                    EdgeSwipeDirection.Bottom -> totalDrag.y < -swipeThreshold
                }
                
                if (swipeDetected) {
                    onEdgeSwipe()
                    event.changes.forEach { it.consume() }
                    return@awaitEachGesture
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * Modifier for scroll-to-top on double-tap of header
 * 
 * @param onScrollToTop Callback to trigger scroll to top
 * @param doubleTapTimeout Maximum time between taps
 */
fun Modifier.scrollToTopOnDoubleTap(
    onScrollToTop: () -> Unit,
    doubleTapTimeout: Long = 300L
): Modifier = composed {
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < doubleTapTimeout) {
                    onScrollToTop()
                    lastTapTime = 0L
                } else {
                    lastTapTime = currentTime
                }
            }
        )
    }
}

/**
 * Tap state for tracking tap interactions
 */
sealed class TapState {
    data object Idle : TapState()
    data object Pressed : TapState()
    data object Released : TapState()
    data class Held(val duration: Long) : TapState()
}

/**
 * Composable for tracking tap state with visual feedback
 */
@Composable
fun TapStateTracker(
    onTapStateChange: (TapState) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (TapState) -> Unit
) {
    var tapState by remember { mutableStateOf<TapState>(TapState.Idle) }
    val scope = rememberCoroutineScope()
    
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                tapState = TapState.Pressed
                onTapStateChange(tapState)
                
                val holdStartTime = System.currentTimeMillis()
                
                // Check for hold
                var isHolding = false
                scope.launch {
                    delay(500)
                    if (tapState == TapState.Pressed) {
                        isHolding = true
                        val holdDuration = System.currentTimeMillis() - holdStartTime
                        tapState = TapState.Held(holdDuration)
                        onTapStateChange(tapState)
                    }
                }
                
                val up = waitForUpOrCancellation()
                
                if (up != null && !isHolding) {
                    tapState = TapState.Released
                    onTapStateChange(tapState)
                }
                
                // Reset to idle after a brief moment
                scope.launch {
                    delay(100)
                    tapState = TapState.Idle
                    onTapStateChange(tapState)
                }
            }
        }
    ) {
        content(tapState)
    }
}

/**
 * Modifier for detecting rapid consecutive taps
 * 
 * @param requiredTaps Number of taps required
 * @param onRequiredTaps Callback when required taps are reached
 * @param tapWindow Time window for counting taps in milliseconds
 */
fun Modifier.rapidTapDetector(
    requiredTaps: Int,
    onRequiredTaps: () -> Unit,
    tapWindow: Long = 500L
): Modifier = composed {
    var tapCount by remember { mutableIntStateOf(0) }
    var windowStartTime by remember { mutableLongStateOf(0L) }
    
    this.pointerInput(requiredTaps) {
        detectTapGestures(
            onTap = {
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - windowStartTime > tapWindow) {
                    // Reset window
                    tapCount = 1
                    windowStartTime = currentTime
                } else {
                    tapCount++
                    
                    if (tapCount >= requiredTaps) {
                        onRequiredTaps()
                        tapCount = 0
                        windowStartTime = 0L
                    }
                }
            }
        )
    }
}

/**
 * Modifier for detecting tap with directional awareness
 * 
 * @param onTapWithDirection Callback with tap position and quadrant
 */
fun Modifier.directionalTap(
    onTapWithDirection: (position: Offset, quadrant: TapQuadrant) -> Unit
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(
        onTap = { position ->
            try {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
            
                val quadrant = when {
                    position.x < centerX && position.y < centerY -> TapQuadrant.TopLeft
                    position.x >= centerX && position.y < centerY -> TapQuadrant.TopRight
                    position.x < centerX && position.y >= centerY -> TapQuadrant.BottomLeft
                    else -> TapQuadrant.BottomRight
                }
            
                onTapWithDirection(position, quadrant)
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    )
}

enum class TapQuadrant {
    TopLeft, TopRight, BottomLeft, BottomRight
}

/**
 * Modifier for press and drag gesture combination
 * 
 * @param onPress Callback when press is detected
 * @param onDrag Callback during drag with offset
 * @param onRelease Callback when released
 */
fun Modifier.pressAndDrag(
    onPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit
): Modifier = composed {
    var isDragging by remember { mutableStateOf(false) }
    
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPress(down.position)
            
            var totalDrag = Offset.Zero
            
            do {
                val event = awaitPointerEvent()
                val drag = event.changes.firstOrNull()?.positionChange() ?: Offset.Zero
                
                if (drag != Offset.Zero) {
                    isDragging = true
                    totalDrag += drag
                    onDrag(totalDrag)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            
            isDragging = false
            onRelease()
        }
    }
}

/**
 * Modifier for detecting swipe direction from tap
 * 
 * @param onSwipe Callback with detected swipe direction
 * @param swipeThreshold Minimum distance to consider as swipe
 */
fun Modifier.swipeFromTap(
    onSwipe: (SwipeFromTapDirection) -> Unit,
    swipeThreshold: Float = 50f
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        try {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startPosition = down.position
        
            do {
                val event = awaitPointerEvent()
                val current = event.changes.firstOrNull() ?: continue
            
                if (current.changedToUp()) {
                    val endPosition = current.position
                    val delta = endPosition - startPosition
                
                    if (delta.getDistance() > swipeThreshold) {
                        val direction = when {
                            abs(delta.x) > abs(delta.y) -> {
                                if (delta.x > 0) SwipeFromTapDirection.Right else SwipeFromTapDirection.Left
                            }
                            else -> {
                                if (delta.y > 0) SwipeFromTapDirection.Down else SwipeFromTapDirection.Up
                            }
                        }
                        onSwipe(direction)
                    }
                }
            } while (event.changes.any { it.pressed })
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

enum class SwipeFromTapDirection {
    Up, Down, Left, Right
}

/**
 * Combined gesture detector for tap, double-tap, and long press
 */
fun Modifier.combinedTapGestures(
    onTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    doubleTapTimeout: Long = 300L
): Modifier = composed {
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var waitingForDoubleTap by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - lastTapTime < doubleTapTimeout) {
                    waitingForDoubleTap = false
                    onDoubleTap()
                    lastTapTime = 0L
                } else {
                    lastTapTime = currentTime
                    waitingForDoubleTap = true
                    
                    scope.launch {
                        delay(doubleTapTimeout)
                        if (waitingForDoubleTap) {
                            waitingForDoubleTap = false
                            onTap()
                        }
                    }
                }
            },
            onLongPress = {
                waitingForDoubleTap = false
                onLongPress()
            }
        )
    }
}

/**
 * Modifier for detecting touch pressure (where available)
 * Note: Pressure sensitivity requires compatible hardware
 */
fun Modifier.pressureDetector(
    onPressureChange: (Float) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        try {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPressureChange(down.pressure)
        
            do {
                val event = awaitPointerEvent()
                event.changes.firstOrNull()?.let { change ->
                    onPressureChange(change.pressure)
                }
            } while (event.changes.any { it.pressed })
        
            onPressureChange(0f)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/**
 * Modifier for sequential gesture tracking (gesture sequence detection)
 */
fun Modifier.gestureSequence(
    expectedSequence: List<GestureType>,
    onSequenceComplete: () -> Unit,
    sequenceTimeout: Long = 2000L
): Modifier = composed {
    var currentSequence by remember { mutableStateOf(listOf<GestureType>()) }
    var lastGestureTime by remember { mutableLongStateOf(0L) }
    
    this.pointerInput(expectedSequence) {
        detectTapGestures(
            onTap = {
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - lastGestureTime > sequenceTimeout) {
                    currentSequence = listOf(GestureType.Tap)
                } else {
                    currentSequence = currentSequence + GestureType.Tap
                }
                
                lastGestureTime = currentTime
                
                if (currentSequence == expectedSequence) {
                    onSequenceComplete()
                    currentSequence = emptyList()
                }
            },
            onLongPress = {
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - lastGestureTime > sequenceTimeout) {
                    currentSequence = listOf(GestureType.LongPress)
                } else {
                    currentSequence = currentSequence + GestureType.LongPress
                }
                
                lastGestureTime = currentTime
                
                if (currentSequence == expectedSequence) {
                    onSequenceComplete()
                    currentSequence = emptyList()
                }
            },
            onDoubleTap = {
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - lastGestureTime > sequenceTimeout) {
                    currentSequence = listOf(GestureType.DoubleTap)
                } else {
                    currentSequence = currentSequence + GestureType.DoubleTap
                }
                
                lastGestureTime = currentTime
                
                if (currentSequence == expectedSequence) {
                    onSequenceComplete()
                    currentSequence = emptyList()
                }
            }
        )
    }
}

enum class GestureType {
    Tap, DoubleTap, LongPress, Swipe
}
