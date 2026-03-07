package com.tanush.bouncer.ui.performance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.sentry.Sentry

// ==========================================
// BOUNCER - List Optimizations
// High-performance list rendering utilities
// ==========================================

/**
 * OptimizedLazyColumn - Enhanced LazyColumn with prefetching and stable keys
 * 
 * Features:
 * - Intelligent prefetching of off-screen items
 * - Stable key generation for minimal recomposition
 * - Built-in visibility tracking
 * - Smooth scroll performance optimization
 */
@Composable
fun <T> OptimizedLazyColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    prefetchCount: Int = 3,
    keyExtractor: (T) -> Any,
    placeholderContent: @Composable () -> Unit = { DefaultItemPlaceholder() },
    itemContent: @Composable LazyItemScope.(item: T, index: Int) -> Unit
) {
    val visibilityTracker = remember { VisibilityTracker() }
    val prefetcher = remember { LazyListPrefetcher(prefetchCount) }
    
    // Track visible items for prefetching
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val visibleIndices = visibleItems.map { it.index }.toSet()
                visibilityTracker.updateVisibility(visibleIndices)
                prefetcher.onVisibleItemsChanged(visibleIndices, items.size)
            }
    }
    
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> keyExtractor(item) }
        ) { index, item ->
            val isVisible = remember(index) {
                derivedStateOf { visibilityTracker.isVisible(index) }
            }
            
            val shouldPrefetch = remember(index) {
                derivedStateOf { prefetcher.shouldPrefetch(index) }
            }
            
            OptimizedListItem(
                isVisible = isVisible.value,
                shouldPrefetch = shouldPrefetch.value,
                placeholderContent = placeholderContent
            ) {
                itemContent(item, index)
            }
        }
    }
}

/**
 * OptimizedLazyRow - Enhanced LazyRow with similar optimizations
 */
@Composable
fun <T> OptimizedLazyRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    prefetchCount: Int = 3,
    keyExtractor: (T) -> Any,
    placeholderContent: @Composable () -> Unit = { DefaultItemPlaceholder() },
    itemContent: @Composable LazyItemScope.(item: T, index: Int) -> Unit
) {
    val visibilityTracker = remember { VisibilityTracker() }
    val prefetcher = remember { LazyListPrefetcher(prefetchCount) }
    
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val visibleIndices = visibleItems.map { it.index }.toSet()
                visibilityTracker.updateVisibility(visibleIndices)
                prefetcher.onVisibleItemsChanged(visibleIndices, items.size)
            }
    }
    
    LazyRow(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> keyExtractor(item) }
        ) { index, item ->
            val isVisible = remember(index) {
                derivedStateOf { visibilityTracker.isVisible(index) }
            }
            
            val shouldPrefetch = remember(index) {
                derivedStateOf { prefetcher.shouldPrefetch(index) }
            }
            
            OptimizedListItem(
                isVisible = isVisible.value,
                shouldPrefetch = shouldPrefetch.value,
                placeholderContent = placeholderContent
            ) {
                itemContent(item, index)
            }
        }
    }
}

/**
 * Optimized list item with deferred loading support
 */
@Composable
private fun OptimizedListItem(
    isVisible: Boolean,
    shouldPrefetch: Boolean,
    placeholderContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    // Show content if visible or should be prefetched
    if (isVisible || shouldPrefetch) {
        content()
    } else {
        // Show placeholder for items far outside viewport
        placeholderContent()
    }
}

/**
 * ItemPlaceholder - Generic placeholder for deferred loading
 */
@Composable
fun ItemPlaceholder(
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    cornerRadius: Dp = 12.dp,
    shimmerEnabled: Boolean = true
) {
    val shimmerColors = listOf(
        GlassSurface,
        GlassSurface.copy(alpha = 0.15f),
        GlassSurface
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    val brush = if (shimmerEnabled) {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation.value - 200f, 0f),
            end = Offset(translateAnimation.value, 0f)
        )
    } else {
        Brush.linearGradient(shimmerColors.take(1) + shimmerColors.take(1))
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

/**
 * Default item placeholder
 */
@Composable
fun DefaultItemPlaceholder() {
    ItemPlaceholder(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        height = 80.dp
    )
}

/**
 * LazyListPrefetcher - Intelligent prefetching for lazy lists
 * 
 * Determines which items should be prefetched based on scroll
 * position and direction.
 */
class LazyListPrefetcher(
    private val prefetchCount: Int = 3
) {
    private var visibleIndices: Set<Int> = emptySet()
    private var totalItems: Int = 0
    private var prefetchIndices: Set<Int> = emptySet()
    private var scrollDirection: ScrollDirection = ScrollDirection.IDLE
    private var lastFirstVisible: Int = 0
    
    enum class ScrollDirection {
        UP, DOWN, IDLE
    }
    
    fun onVisibleItemsChanged(newVisibleIndices: Set<Int>, itemCount: Int) {
        try {
            val firstVisible = newVisibleIndices.minOrNull() ?: 0
        
            scrollDirection = when {
                firstVisible > lastFirstVisible -> ScrollDirection.DOWN
                firstVisible < lastFirstVisible -> ScrollDirection.UP
                else -> ScrollDirection.IDLE
            }
        
            lastFirstVisible = firstVisible
            visibleIndices = newVisibleIndices
            totalItems = itemCount
        
            updatePrefetchIndices()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    private fun updatePrefetchIndices() {
        try {
            val minVisible = visibleIndices.minOrNull() ?: 0
            val maxVisible = visibleIndices.maxOrNull() ?: 0
        
            val indicesToPrefetch = mutableSetOf<Int>()
        
            when (scrollDirection) {
                ScrollDirection.DOWN -> {
                    // Prefetch items ahead (below visible area)
                    for (i in 1..prefetchCount) {
                        val index = maxVisible + i
                        if (index < totalItems) {
                            indicesToPrefetch.add(index)
                        }
                    }
                }
                ScrollDirection.UP -> {
                    // Prefetch items behind (above visible area)
                    for (i in 1..prefetchCount) {
                        val index = minVisible - i
                        if (index >= 0) {
                            indicesToPrefetch.add(index)
                        }
                    }
                }
                ScrollDirection.IDLE -> {
                    // Prefetch in both directions
                    for (i in 1..prefetchCount) {
                        val indexBelow = maxVisible + i
                        val indexAbove = minVisible - i
                        if (indexBelow < totalItems) indicesToPrefetch.add(indexBelow)
                        if (indexAbove >= 0) indicesToPrefetch.add(indexAbove)
                    }
                }
            }
        
            prefetchIndices = indicesToPrefetch
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun shouldPrefetch(index: Int): Boolean {
        try {
            return index in prefetchIndices
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun getScrollDirection(): ScrollDirection = scrollDirection
    
    fun getPrefetchIndices(): Set<Int> = prefetchIndices.toSet()
}

/**
 * PaginatedList - List with load-more support
 */
@Composable
fun <T> PaginatedList(
    items: List<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    isLoading: Boolean = false,
    hasMoreItems: Boolean = true,
    loadMoreThreshold: Int = 3,
    onLoadMore: () -> Unit,
    keyExtractor: (T) -> Any,
    loadingIndicator: @Composable () -> Unit = { DefaultLoadingIndicator() },
    itemContent: @Composable LazyItemScope.(item: T, index: Int) -> Unit
) {
    // Detect when to load more
    val shouldLoadMore = remember {
        derivedStateOf {
            if (!hasMoreItems || isLoading) return@derivedStateOf false
            
            val layoutInfo = state.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            
            lastVisibleIndex >= totalItems - loadMoreThreshold
        }
    }
    
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }
    
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> keyExtractor(item) }
        ) { index, item ->
            itemContent(item, index)
        }
        
        // Loading indicator at bottom
        if (isLoading) {
            item(key = "loading_indicator") {
                loadingIndicator()
            }
        }
    }
}

/**
 * Default loading indicator for pagination
 */
@Composable
fun DefaultLoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "loading")
        val alpha = infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loading_alpha"
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                val delay = index * 100
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha.value)
                        )
                )
            }
        }
    }
}

/**
 * VisibilityTracker - Tracks visible items for off-screen cleanup
 */
class VisibilityTracker {
    private var _visibleIndices = mutableStateOf<Set<Int>>(emptySet())
    private var _recentlyVisibleIndices = mutableSetOf<Int>()
    private val retentionWindow = 5 // Keep items visible for a short time after scrolling away
    
    fun updateVisibility(newVisibleIndices: Set<Int>) {
        try {
            // Add newly visible indices to recently visible set
            _recentlyVisibleIndices.addAll(_visibleIndices.value)
        
            // Remove indices that are too far from current visibility
            val minVisible = newVisibleIndices.minOrNull() ?: 0
            val maxVisible = newVisibleIndices.maxOrNull() ?: 0
        
            _recentlyVisibleIndices.removeAll { index ->
                index < minVisible - retentionWindow || index > maxVisible + retentionWindow
            }
        
            _visibleIndices.value = newVisibleIndices
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun isVisible(index: Int): Boolean {
        try {
            return index in _visibleIndices.value
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun isRecentlyVisible(index: Int): Boolean {
        try {
            return index in _recentlyVisibleIndices
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun getVisibleIndices(): Set<Int> = _visibleIndices.value
    
    fun getRecentlyVisibleIndices(): Set<Int> = _recentlyVisibleIndices.toSet()
    
    fun clear() {
        try {
            _visibleIndices.value = emptySet()
            _recentlyVisibleIndices.clear()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/**
 * RecycledItemState - State holder for reused composables
 * 
 * Helps maintain state across item recycling in lazy lists.
 */
@Stable
class RecycledItemState<T>(
    initialItem: T,
    private val onRecycle: ((T) -> Unit)? = null,
    private val onBind: ((T) -> Unit)? = null
) {
    private var _currentItem by mutableStateOf(initialItem)
    private var _isActive by mutableStateOf(true)
    private var _bindCount by mutableIntStateOf(0)
    
    val currentItem: T get() = _currentItem
    val isActive: Boolean get() = _isActive
    val bindCount: Int get() = _bindCount
    
    /**
     * Bind a new item to this state
     */
    fun bind(item: T) {
        try {
            if (_currentItem != item) {
                onRecycle?.invoke(_currentItem)
                _currentItem = item
                _bindCount++
                onBind?.invoke(item)
            }
            _isActive = true
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Mark this state as inactive (when scrolled off-screen)
     */
    fun deactivate() {
        try {
            _isActive = false
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Recycle this state for reuse
     */
    fun recycle() {
        try {
            onRecycle?.invoke(_currentItem)
            _isActive = false
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/**
 * Remember recycled item state
 */
@Composable
fun <T> rememberRecycledItemState(
    item: T,
    onRecycle: ((T) -> Unit)? = null,
    onBind: ((T) -> Unit)? = null
): RecycledItemState<T> {
    val state = remember { RecycledItemState(item, onRecycle, onBind) }
    
    LaunchedEffect(item) {
        state.bind(item)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            state.deactivate()
        }
    }
    
    return state
}

/**
 * Stable wrapper for list items to prevent unnecessary recomposition
 */
@Stable
data class StableListItem<T>(
    val key: Any,
    val data: T
)

/**
 * Convert list to stable list items
 */
fun <T> List<T>.toStableItems(keyExtractor: (T) -> Any): List<StableListItem<T>> {
    try {
        return map { StableListItem(keyExtractor(it), it) }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Extension for tracking list scroll position
 */
@Composable
fun LazyListState.rememberScrollPosition(): ScrollPositionState {
    val state = remember { ScrollPositionState() }
    
    LaunchedEffect(this) {
        snapshotFlow { 
            ScrollPosition(
                firstVisibleIndex = firstVisibleItemIndex,
                firstVisibleOffset = firstVisibleItemScrollOffset,
                isScrollInProgress = isScrollInProgress
            )
        }.collect { position ->
            state.update(position)
        }
    }
    
    return state
}

data class ScrollPosition(
    val firstVisibleIndex: Int,
    val firstVisibleOffset: Int,
    val isScrollInProgress: Boolean
)

@Stable
class ScrollPositionState {
    private var _position by mutableStateOf(ScrollPosition(0, 0, false))
    private var _scrollDirection by mutableStateOf(LazyListPrefetcher.ScrollDirection.IDLE)
    private var _velocity by mutableFloatStateOf(0f)
    
    private var previousIndex = 0
    private var previousOffset = 0
    private var lastUpdateTime = System.currentTimeMillis()
    
    val position: ScrollPosition get() = _position
    val scrollDirection: LazyListPrefetcher.ScrollDirection get() = _scrollDirection
    val velocity: Float get() = _velocity
    
    fun update(newPosition: ScrollPosition) {
        try {
            val currentTime = System.currentTimeMillis()
            val timeDelta = (currentTime - lastUpdateTime).coerceAtLeast(1)
        
            // Calculate scroll direction
            _scrollDirection = when {
                newPosition.firstVisibleIndex > previousIndex -> LazyListPrefetcher.ScrollDirection.DOWN
                newPosition.firstVisibleIndex < previousIndex -> LazyListPrefetcher.ScrollDirection.UP
                newPosition.firstVisibleOffset > previousOffset -> LazyListPrefetcher.ScrollDirection.DOWN
                newPosition.firstVisibleOffset < previousOffset -> LazyListPrefetcher.ScrollDirection.UP
                else -> LazyListPrefetcher.ScrollDirection.IDLE
            }
        
            // Calculate velocity (pixels per second)
            val pixelDelta = kotlin.math.abs(newPosition.firstVisibleOffset - previousOffset)
            _velocity = (pixelDelta.toFloat() / timeDelta) * 1000f
        
            previousIndex = newPosition.firstVisibleIndex
            previousOffset = newPosition.firstVisibleOffset
            lastUpdateTime = currentTime
            _position = newPosition
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun isScrollingFast(threshold: Float = 1000f): Boolean {
        try {
            return _velocity > threshold
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
