package com.tanush.bouncer.ui.adaptive

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import io.sentry.Sentry

/**
 * Foldable Device Support for Bouncer Cricket App
 * 
 * Provides composables and utilities for handling foldable devices,
 * hinge positioning, fold state transitions, and multi-pane interactions.
 */

// ==========================================
// Folding Feature Information
// ==========================================

/**
 * Represents the current state of a foldable device's fold
 */
enum class FoldState {
    /** Device is fully flat/open */
    FLAT,
    /** Device is partially folded (like a book) */
    HALF_OPENED,
    /** Device is fully closed/folded */
    CLOSED,
    /** Not a foldable device or no fold detected */
    NONE
}

/**
 * Represents the orientation of the fold/hinge
 */
enum class FoldOrientation {
    /** Fold runs horizontally (left to right) */
    HORIZONTAL,
    /** Fold runs vertically (top to bottom) */
    VERTICAL,
    /** Not applicable (non-foldable device) */
    NONE
}

/**
 * Comprehensive information about the device's folding feature
 */
data class FoldingFeatureInfo(
    val foldState: FoldState = FoldState.NONE,
    val orientation: FoldOrientation = FoldOrientation.NONE,
    val isSeparating: Boolean = false,
    val occlusionType: OcclusionType = OcclusionType.NONE,
    val foldBounds: Rect = Rect.Zero,
    val hingeWidth: Dp = 0.dp,
    val hingePosition: Dp = 0.dp
) {
    /** Whether the device is currently in a folded state */
    val isFolded: Boolean get() = foldState == FoldState.HALF_OPENED
    
    /** Whether content should avoid the hinge area */
    val shouldAvoidHinge: Boolean get() = isSeparating || occlusionType == OcclusionType.FULL
    
    /** Whether we're on a foldable device */
    val isFoldable: Boolean get() = foldState != FoldState.NONE
    
    /** Get the safe area that avoids the hinge */
    fun getSafeAreas(): Pair<Rect, Rect> {
        try {
            if (!shouldAvoidHinge) {
                return Pair(Rect.Zero, Rect.Zero)
            }
        
            return when (orientation) {
                FoldOrientation.HORIZONTAL -> {
                    // Split top and bottom
                    Pair(
                        Rect(0f, 0f, foldBounds.width, foldBounds.top),
                        Rect(0f, foldBounds.bottom, foldBounds.width, Float.MAX_VALUE)
                    )
                }
                FoldOrientation.VERTICAL -> {
                    // Split left and right
                    Pair(
                        Rect(0f, 0f, foldBounds.left, Float.MAX_VALUE),
                        Rect(foldBounds.right, 0f, Float.MAX_VALUE, Float.MAX_VALUE)
                    )
                }
                FoldOrientation.NONE -> Pair(Rect.Zero, Rect.Zero)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/**
 * Type of occlusion caused by the fold
 */
enum class OcclusionType {
    /** No occlusion - content is visible across the fold */
    NONE,
    /** Full occlusion - hinge physically blocks content */
    FULL
}

// ==========================================
// Fold State Provider
// ==========================================

/**
 * CompositionLocal for accessing fold state throughout the composition tree
 */
val LocalFoldingFeature = staticCompositionLocalOf { FoldingFeatureInfo() }

/**
 * Collects window layout info and provides fold state to the composition tree
 */
@Composable
fun FoldStateProvider(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    val foldingFeatureInfo = remember(context) {
        val activity = context as? Activity
        if (activity != null) {
            WindowInfoTracker.getOrCreate(context)
                .windowLayoutInfo(activity)
                .map { layoutInfo ->
                    parseFoldingFeature(layoutInfo, density.density)
                }
        } else {
            emptyFlow()
        }
    }.collectAsState(initial = FoldingFeatureInfo())
    
    CompositionLocalProvider(
        LocalFoldingFeature provides foldingFeatureInfo.value
    ) {
        content()
    }
}

private fun parseFoldingFeature(
    layoutInfo: WindowLayoutInfo,
    density: Float
): FoldingFeatureInfo {
    val foldingFeature = layoutInfo.displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull() ?: return FoldingFeatureInfo()
    
    val foldState = when (foldingFeature.state) {
        FoldingFeature.State.FLAT -> FoldState.FLAT
        FoldingFeature.State.HALF_OPENED -> FoldState.HALF_OPENED
        else -> FoldState.NONE
    }
    
    val orientation = when (foldingFeature.orientation) {
        FoldingFeature.Orientation.HORIZONTAL -> FoldOrientation.HORIZONTAL
        FoldingFeature.Orientation.VERTICAL -> FoldOrientation.VERTICAL
        else -> FoldOrientation.VERTICAL
    }
    
    val occlusionType = when (foldingFeature.occlusionType) {
        FoldingFeature.OcclusionType.FULL -> OcclusionType.FULL
        FoldingFeature.OcclusionType.NONE -> OcclusionType.NONE
        else -> OcclusionType.NONE
    }
    
    val bounds = foldingFeature.bounds
    val foldBounds = Rect(
        left = bounds.left.toFloat(),
        top = bounds.top.toFloat(),
        right = bounds.right.toFloat(),
        bottom = bounds.bottom.toFloat()
    )
    
    val hingeWidth = (bounds.width() / density).dp
    val hingePosition = when (orientation) {
        FoldOrientation.HORIZONTAL -> (bounds.top / density).dp
        FoldOrientation.VERTICAL -> (bounds.left / density).dp
        FoldOrientation.NONE -> 0.dp
    }
    
    return FoldingFeatureInfo(
        foldState = foldState,
        orientation = orientation,
        isSeparating = foldingFeature.isSeparating,
        occlusionType = occlusionType,
        foldBounds = foldBounds,
        hingeWidth = hingeWidth,
        hingePosition = hingePosition
    )
}

// ==========================================
// Hinge-Aware Layout
// ==========================================

/**
 * Layout that positions content around the fold/hinge
 * Automatically adjusts content to avoid the hinge area on foldable devices
 */
@Composable
fun HingeAwareLayout(
    modifier: Modifier = Modifier,
    foldingInfo: FoldingFeatureInfo = LocalFoldingFeature.current,
    hingeSpacerColor: Color = Color.Transparent,
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit
) {
    if (!foldingInfo.isFoldable || !foldingInfo.shouldAvoidHinge) {
        // Non-foldable or no need to avoid hinge - use standard two-pane
        Row(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) { firstPane() }
            Box(modifier = Modifier.weight(1f)) { secondPane() }
        }
        return
    }
    
    when (foldingInfo.orientation) {
        FoldOrientation.VERTICAL -> {
            // Vertical fold - split left and right with hinge spacer
            Row(modifier = modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .width(foldingInfo.hingePosition)
                        .fillMaxHeight()
                ) {
                    firstPane()
                }
                
                Spacer(
                    modifier = Modifier
                        .width(foldingInfo.hingeWidth)
                        .fillMaxHeight()
                        .background(hingeSpacerColor)
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    secondPane()
                }
            }
        }
        
        FoldOrientation.HORIZONTAL -> {
            // Horizontal fold - split top and bottom with hinge spacer
            Column(modifier = modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .height(foldingInfo.hingePosition)
                        .fillMaxWidth()
                ) {
                    firstPane()
                }
                
                Spacer(
                    modifier = Modifier
                        .height(foldingInfo.hingeWidth)
                        .fillMaxWidth()
                        .background(hingeSpacerColor)
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    secondPane()
                }
            }
        }
        
        FoldOrientation.NONE -> {
            // Fallback to standard layout
            Row(modifier = modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) { firstPane() }
                Box(modifier = Modifier.weight(1f)) { secondPane() }
            }
        }
    }
}

// ==========================================
// Smooth Fold Transition
// ==========================================

/**
 * Animates content when transitioning between fold states
 */
@Composable
fun SmoothFoldTransition(
    modifier: Modifier = Modifier,
    foldingInfo: FoldingFeatureInfo = LocalFoldingFeature.current,
    foldedContent: @Composable () -> Unit,
    unfoldedContent: @Composable () -> Unit
) {
    val isFolded = foldingInfo.isFolded
    
    // Animated transition between fold states
    val transition = updateTransition(targetState = isFolded, label = "FoldTransition")
    
    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "Alpha"
    ) { folded ->
        if (folded) 1f else 1f
    }
    
    val scale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "Scale"
    ) { folded ->
        if (folded) 0.95f else 1f
    }
    
    Box(modifier = modifier) {
        AnimatedContent(
            targetState = isFolded,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
            },
            label = "FoldContentTransition"
        ) { folded ->
            if (folded) {
                foldedContent()
            } else {
                unfoldedContent()
            }
        }
    }
}

/**
 * Animated container that responds to fold state changes with smooth animations
 */
@Composable
fun AnimatedFoldContainer(
    modifier: Modifier = Modifier,
    foldingInfo: FoldingFeatureInfo = LocalFoldingFeature.current,
    content: @Composable (isFolded: Boolean) -> Unit
) {
    val isFolded = foldingInfo.isFolded
    
    val animatedPadding by animateDpAsState(
        targetValue = if (isFolded) 8.dp else 16.dp,
        animationSpec = tween(300),
        label = "FoldPadding"
    )
    
    val animatedElevation by animateDpAsState(
        targetValue = if (isFolded) 2.dp else 4.dp,
        animationSpec = tween(300),
        label = "FoldElevation"
    )
    
    Surface(
        modifier = modifier.padding(animatedPadding),
        shadowElevation = animatedElevation,
        shape = MaterialTheme.shapes.medium
    ) {
        content(isFolded)
    }
}

// ==========================================
// Drag and Drop Between Panes
// ==========================================

/**
 * State for managing drag and drop between panes
 */
@Stable
class DragDropState<T> {
    var isDragging by mutableStateOf(false)
        private set
    var draggedItem by mutableStateOf<T?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var targetPane by mutableStateOf<Int?>(null)
        private set
    
    fun startDrag(item: T, offset: Offset = Offset.Zero) {
        try {
            draggedItem = item
            dragOffset = offset
            isDragging = true
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun updateDrag(offset: Offset, pane: Int? = null) {
        try {
            dragOffset = offset
            targetPane = pane
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun endDrag(): Pair<T?, Int?> {
        try {
            val result = Pair(draggedItem, targetPane)
            isDragging = false
            draggedItem = null
            dragOffset = Offset.Zero
            targetPane = null
            return result
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun cancelDrag() {
        try {
            isDragging = false
            draggedItem = null
            dragOffset = Offset.Zero
            targetPane = null
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

@Composable
fun <T> rememberDragDropState(): DragDropState<T> {
    return remember { DragDropState() }
}

/**
 * Container that enables drag and drop interactions between panes on foldable devices
 */
@Composable
fun <T> DragAndDropBetweenPanes(
    modifier: Modifier = Modifier,
    foldingInfo: FoldingFeatureInfo = LocalFoldingFeature.current,
    dragDropState: DragDropState<T> = rememberDragDropState(),
    onItemDropped: (item: T, targetPane: Int) -> Unit,
    leftPane: @Composable (onStartDrag: (T, Offset) -> Unit) -> Unit,
    rightPane: @Composable (onStartDrag: (T, Offset) -> Unit) -> Unit,
    dragOverlay: @Composable (T) -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        HingeAwareLayout(
            foldingInfo = foldingInfo,
            firstPane = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    val (item, pane) = dragDropState.endDrag()
                                    if (item != null && pane != null) {
                                        onItemDropped(item, pane)
                                    }
                                },
                                onDragCancel = { dragDropState.cancelDrag() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newOffset = dragDropState.dragOffset + Offset(
                                        dragAmount.x,
                                        dragAmount.y
                                    )
                                    // Determine which pane we're over based on position
                                    val pane = if (newOffset.x > size.width / 2) 1 else 0
                                    dragDropState.updateDrag(newOffset, pane)
                                }
                            )
                        }
                ) {
                    leftPane { item, offset ->
                        dragDropState.startDrag(item, offset)
                    }
                }
            },
            secondPane = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    val (item, pane) = dragDropState.endDrag()
                                    if (item != null && pane != null) {
                                        onItemDropped(item, pane)
                                    }
                                },
                                onDragCancel = { dragDropState.cancelDrag() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newOffset = dragDropState.dragOffset + Offset(
                                        dragAmount.x,
                                        dragAmount.y
                                    )
                                    val pane = if (newOffset.x > size.width / 2) 1 else 0
                                    dragDropState.updateDrag(newOffset, pane)
                                }
                            )
                        }
                ) {
                    rightPane { item, offset ->
                        dragDropState.startDrag(item, offset)
                    }
                }
            }
        )
        
        // Drag overlay
        if (dragDropState.isDragging && dragDropState.draggedItem != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            dragDropState.dragOffset.x.roundToInt(),
                            dragDropState.dragOffset.y.roundToInt()
                        )
                    }
            ) {
                dragDropState.draggedItem?.let { dragOverlay(it) }
            }
        }
    }
}

// ==========================================
// Two-Pane Navigation
// ==========================================

/**
 * Navigation state for managing independent navigation in each pane
 */
@Stable
class TwoPaneNavigationState(
    initialLeftRoute: String = "",
    initialRightRoute: String = ""
) {
    var leftPaneRoute by mutableStateOf(initialLeftRoute)
        private set
    var rightPaneRoute by mutableStateOf(initialRightRoute)
        private set
    
    private val leftBackStack = mutableListOf<String>()
    private val rightBackStack = mutableListOf<String>()
    
    fun navigateLeft(route: String) {
        try {
            if (leftPaneRoute.isNotEmpty()) {
                leftBackStack.add(leftPaneRoute)
            }
            leftPaneRoute = route
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun navigateRight(route: String) {
        try {
            if (rightPaneRoute.isNotEmpty()) {
                rightBackStack.add(rightPaneRoute)
            }
            rightPaneRoute = route
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun navigateBackLeft(): Boolean {
        try {
            return if (leftBackStack.isNotEmpty()) {
                leftPaneRoute = leftBackStack.removeAt(leftBackStack.size - 1)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun navigateBackRight(): Boolean {
        try {
            return if (rightBackStack.isNotEmpty()) {
                rightPaneRoute = rightBackStack.removeAt(rightBackStack.size - 1)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun canGoBackLeft(): Boolean = leftBackStack.isNotEmpty()
    fun canGoBackRight(): Boolean = rightBackStack.isNotEmpty()
}

@Composable
fun rememberTwoPaneNavigationState(
    initialLeftRoute: String = "",
    initialRightRoute: String = ""
): TwoPaneNavigationState {
    return remember { TwoPaneNavigationState(initialLeftRoute, initialRightRoute) }
}

/**
 * Two-pane navigation layout with independent routing for each pane
 */
@Composable
fun TwoPaneNavigation(
    modifier: Modifier = Modifier,
    navigationState: TwoPaneNavigationState,
    foldingInfo: FoldingFeatureInfo = LocalFoldingFeature.current,
    leftPaneContent: @Composable (route: String, navigate: (String) -> Unit) -> Unit,
    rightPaneContent: @Composable (route: String, navigate: (String) -> Unit) -> Unit
) {
    HingeAwareLayout(
        modifier = modifier,
        foldingInfo = foldingInfo,
        firstPane = {
            AnimatedContent(
                targetState = navigationState.leftPaneRoute,
                transitionSpec = {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                },
                label = "LeftPaneNavigation"
            ) { route ->
                leftPaneContent(route) { newRoute ->
                    navigationState.navigateLeft(newRoute)
                }
            }
        },
        secondPane = {
            AnimatedContent(
                targetState = navigationState.rightPaneRoute,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                },
                label = "RightPaneNavigation"
            ) { route ->
                rightPaneContent(route) { newRoute ->
                    navigationState.navigateRight(newRoute)
                }
            }
        }
    )
}

// ==========================================
// Foldable Preview Configurations
// ==========================================

/**
 * Preview annotation for foldable device configurations
 * Use these to preview your composables in foldable form factors
 */

@Preview(
    name = "Foldable - Unfolded",
    device = "spec:width=2208dp,height=1840dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape"
)
annotation class FoldableUnfoldedPreview

@Preview(
    name = "Foldable - Book Mode",
    device = "spec:width=1104dp,height=1840dp,dpi=420,isRound=false,chinSize=0dp,orientation=portrait"
)
annotation class FoldableBookModePreview

@Preview(
    name = "Foldable - Tabletop Mode",
    device = "spec:width=2208dp,height=920dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape"
)
annotation class FoldableTabletopPreview

@Preview(
    name = "Tablet - Large",
    device = "spec:width=1280dp,height=800dp,dpi=240,isRound=false"
)
annotation class TabletLargePreview

@Preview(
    name = "Tablet - Small",
    device = "spec:width=600dp,height=1024dp,dpi=240,isRound=false"
)
annotation class TabletSmallPreview

/**
 * Composite preview for all foldable configurations
 */
@FoldableUnfoldedPreview
@FoldableBookModePreview
@FoldableTabletopPreview
@TabletLargePreview
@TabletSmallPreview
annotation class FoldableDevicePreviews

// ==========================================
// Utility Extensions
// ==========================================

/**
 * Modifier that adjusts content based on fold state
 */
fun Modifier.foldAware(
    foldingInfo: FoldingFeatureInfo,
    avoidHinge: Boolean = true
): Modifier {
    if (!foldingInfo.shouldAvoidHinge || !avoidHinge) {
        try {
            return this
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    return when (foldingInfo.orientation) {
        FoldOrientation.VERTICAL -> {
            this.padding(end = foldingInfo.hingeWidth)
        }
        FoldOrientation.HORIZONTAL -> {
            this.padding(bottom = foldingInfo.hingeWidth)
        }
        FoldOrientation.NONE -> this
    }
}

/**
 * Calculate the available space in each pane accounting for the hinge
 */
@Composable
fun calculatePaneSizes(
    foldingInfo: FoldingFeatureInfo = LocalFoldingFeature.current,
    totalWidth: Dp
): Pair<Dp, Dp> {
    return if (foldingInfo.shouldAvoidHinge && foldingInfo.orientation == FoldOrientation.VERTICAL) {
        val leftPaneWidth = foldingInfo.hingePosition
        val rightPaneWidth = totalWidth - leftPaneWidth - foldingInfo.hingeWidth
        Pair(leftPaneWidth, rightPaneWidth)
    } else {
        val halfWidth = totalWidth / 2
        Pair(halfWidth, halfWidth)
    }
}
