package com.tanush.bouncer.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sheet expansion states for multi-stage sheets
 */
enum class SheetState {
    HIDDEN,
    PEEK,       // Small peek view (e.g., 100dp)
    HALF,       // Half screen
    EXPANDED    // Full screen minus some padding
}

/**
 * DraggableBottomSheet with haptic snap points
 * Full-featured bottom sheet with customizable snap points and haptic feedback
 */
@Composable
fun DraggableBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialState: SheetState = SheetState.HALF,
    peekHeight: Dp = 100.dp,
    enableBlurBackdrop: Boolean = true,
    enableHaptics: Boolean = true,
    sheetContent: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    
    // Calculate snap points in pixels
    val peekHeightPx = with(density) { peekHeight.toPx() }
    val halfHeightPx = with(density) { (screenHeight * 0.5f).toPx() }
    val expandedHeightPx = with(density) { (screenHeight * 0.9f).toPx() }
    
    var currentState by remember { mutableStateOf(if (isVisible) initialState else SheetState.HIDDEN) }
    
    val targetOffset = when (currentState) {
        SheetState.HIDDEN -> with(density) { screenHeight.toPx() }
        SheetState.PEEK -> with(density) { screenHeight.toPx() } - peekHeightPx
        SheetState.HALF -> with(density) { screenHeight.toPx() } - halfHeightPx
        SheetState.EXPANDED -> with(density) { screenHeight.toPx() } - expandedHeightPx
    }
    
    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 400f
        ),
        label = "sheetOffset"
    )
    
    var dragOffset by remember { mutableFloatStateOf(0f) }
    
    // Haptic feedback helper
    fun performHaptic() {
        if (enableHaptics) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
    
    // Find closest snap point
    fun findClosestState(currentOffset: Float): SheetState {
        val offsets = mapOf(
            SheetState.HIDDEN to with(density) { screenHeight.toPx() },
            SheetState.PEEK to with(density) { screenHeight.toPx() } - peekHeightPx,
            SheetState.HALF to with(density) { screenHeight.toPx() } - halfHeightPx,
            SheetState.EXPANDED to with(density) { screenHeight.toPx() } - expandedHeightPx
        )
        
        return offsets.minByOrNull { kotlin.math.abs(it.value - currentOffset) }?.key ?: SheetState.HALF
    }
    
    // Update state when visibility changes
    LaunchedEffect(isVisible) {
        if (isVisible && currentState == SheetState.HIDDEN) {
            currentState = initialState
        } else if (!isVisible) {
            currentState = SheetState.HIDDEN
        }
    }
    
    // Backdrop and sheet
    if (isVisible || currentState != SheetState.HIDDEN) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop
            if (enableBlurBackdrop) {
                BlurBackdrop(
                    isVisible = currentState != SheetState.HIDDEN,
                    onDismiss = {
                        performHaptic()
                        onDismiss()
                    }
                )
            } else {
                // Simple dimming backdrop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (currentState != SheetState.HIDDEN) 0.5f else 0f))
                        .clickable(enabled = currentState != SheetState.HIDDEN) {
                            performHaptic()
                            onDismiss()
                        }
                )
            }
            
            // Sheet
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { IntOffset(0, (animatedOffset + dragOffset).roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            dragOffset += delta
                        },
                        onDragStopped = { velocity ->
                            val newOffset = animatedOffset + dragOffset
                            val newState = findClosestState(newOffset)
                            
                            // Apply velocity-based state changes
                            val adjustedState = when {
                                velocity > 1000f && currentState != SheetState.HIDDEN -> {
                                    when (currentState) {
                                        SheetState.EXPANDED -> SheetState.HALF
                                        SheetState.HALF -> SheetState.PEEK
                                        SheetState.PEEK -> SheetState.HIDDEN
                                        else -> newState
                                    }
                                }
                                velocity < -1000f && currentState != SheetState.EXPANDED -> {
                                    when (currentState) {
                                        SheetState.PEEK -> SheetState.HALF
                                        SheetState.HALF -> SheetState.EXPANDED
                                        else -> newState
                                    }
                                }
                                else -> newState
                            }
                            
                            if (adjustedState != currentState) {
                                performHaptic()
                            }
                            
                            currentState = adjustedState
                            dragOffset = 0f
                            
                            if (currentState == SheetState.HIDDEN) {
                                onDismiss()
                            }
                        }
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White.copy(alpha = 0.05f),
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Drag handle
                    SheetHandle(
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    // Content
                    sheetContent()
                }
            }
        }
    }
}

/**
 * BlurBackdrop - Dimming with blur effect
 * Creates a blurred background overlay for bottom sheets
 */
@Composable
fun BlurBackdrop(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    dimAmount: Float = 0.6f
) {
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "backdropAlpha"
    )
    
    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .blur(blurRadius * alpha)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = dimAmount * 0.3f),
                            Color.Black.copy(alpha = dimAmount),
                        )
                    )
                )
                .clickable(enabled = isVisible) { onDismiss() }
        )
    }
}

/**
 * MultiStageSheet - Sheet with peek, half, and full expansion states
 * Advanced bottom sheet with three-stage expansion
 */
@Composable
fun MultiStageSheet(
    isVisible: Boolean,
    onStateChange: (SheetState) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    showCloseButton: Boolean = true,
    peekContent: @Composable () -> Unit = {},
    halfContent: @Composable () -> Unit = {},
    expandedContent: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    
    var currentState by remember { mutableStateOf(if (isVisible) SheetState.PEEK else SheetState.HIDDEN) }
    
    // Heights for each state
    val peekHeight = 150.dp
    val halfHeight = screenHeight * 0.55f
    val expandedHeight = screenHeight * 0.92f
    
    val currentHeight by animateDpAsState(
        targetValue = when (currentState) {
            SheetState.HIDDEN -> 0.dp
            SheetState.PEEK -> peekHeight
            SheetState.HALF -> halfHeight
            SheetState.EXPANDED -> expandedHeight
        },
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "sheetHeight"
    )
    
    fun performHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            currentState = SheetState.HIDDEN
        } else if (currentState == SheetState.HIDDEN) {
            currentState = SheetState.PEEK
        }
    }
    
    LaunchedEffect(currentState) {
        onStateChange(currentState)
    }
    
    if (isVisible || currentState != SheetState.HIDDEN) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop
            BlurBackdrop(
                isVisible = currentState != SheetState.HIDDEN && currentState != SheetState.PEEK,
                onDismiss = {
                    performHaptic()
                    if (currentState == SheetState.EXPANDED) {
                        currentState = SheetState.HALF
                    } else {
                        currentState = SheetState.PEEK
                    }
                }
            )
            
            // Sheet
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .height(currentHeight)
                    .align(Alignment.BottomCenter)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                // Let spring animation handle the snap
                            },
                            onDrag = { change, dragAmount ->
                                val threshold = 50f
                                if (dragAmount.y < -threshold) {
                                    // Swiping up
                                    performHaptic()
                                    currentState = when (currentState) {
                                        SheetState.PEEK -> SheetState.HALF
                                        SheetState.HALF -> SheetState.EXPANDED
                                        else -> currentState
                                    }
                                } else if (dragAmount.y > threshold) {
                                    // Swiping down
                                    performHaptic()
                                    currentState = when (currentState) {
                                        SheetState.EXPANDED -> SheetState.HALF
                                        SheetState.HALF -> SheetState.PEEK
                                        SheetState.PEEK -> {
                                            onDismiss()
                                            SheetState.HIDDEN
                                        }
                                        else -> currentState
                                    }
                                }
                            }
                        )
                    },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White.copy(alpha = 0.05f),
                shadowElevation = 24.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Handle
                    SheetHandle(modifier = Modifier.padding(vertical = 10.dp))
                    
                    // Header
                    if (title.isNotEmpty() || showCloseButton) {
                        SheetHeader(
                            title = title,
                            subtitle = subtitle,
                            showCloseButton = showCloseButton,
                            onClose = {
                                performHaptic()
                                onDismiss()
                            }
                        )
                    }
                    
                    // Content based on state
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentState) {
                            SheetState.PEEK -> {
                                peekContent()
                            }
                            SheetState.HALF -> {
                                Column {
                                    peekContent()
                                    halfContent()
                                }
                            }
                            SheetState.EXPANDED -> {
                                Column {
                                    peekContent()
                                    halfContent()
                                    expandedContent()
                                }
                            }
                            else -> {}
                        }
                    }
                    
                    // Stage indicators
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StageIndicator(
                            isActive = currentState == SheetState.PEEK,
                            onClick = {
                                performHaptic()
                                currentState = SheetState.PEEK
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StageIndicator(
                            isActive = currentState == SheetState.HALF,
                            onClick = {
                                performHaptic()
                                currentState = SheetState.HALF
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StageIndicator(
                            isActive = currentState == SheetState.EXPANDED,
                            onClick = {
                                performHaptic()
                                currentState = SheetState.EXPANDED
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageIndicator(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val size by animateDpAsState(
        targetValue = if (isActive) 24.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "indicatorSize"
    )
    
    Box(
        modifier = Modifier
            .height(8.dp)
            .width(size)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isActive) ShinyGoldDeep else CardBorder.copy(alpha = 0.5f)
            )
            .clickable { onClick() }
    )
}

/**
 * SheetHandle - Styled drag indicator
 * Visual handle for sheet dragging
 */
@Composable
fun SheetHandle(
    modifier: Modifier = Modifier,
    color: Color = CardBorder,
    width: Dp = 40.dp,
    height: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "handleAnimation")
    
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            color,
                            ShinyGoldDeep.copy(alpha = shimmer * 0.5f),
                            color
                        )
                    )
                )
        )
    }
}

/**
 * SheetHeader - Header with title and close action
 * Standard header for bottom sheets
 */
@Composable
fun SheetHeader(
    title: String,
    subtitle: String = "",
    showCloseButton: Boolean = true,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
            
            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close sheet",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * NestedScrollSheet - Sheet supporting internal scrolling
 * Bottom sheet that properly handles nested scrolling content
 */
@Composable
fun NestedScrollSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    minHeight: Dp = 200.dp,
    maxHeight: Dp = 0.dp, // 0 = use screen height - 100dp
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val actualMaxHeight = if (maxHeight == 0.dp) screenHeight - 100.dp else maxHeight
    val view = LocalView.current
    
    var sheetHeight by remember { mutableStateOf(minHeight) }
    var isDraggingSheet by remember { mutableStateOf(false) }
    var contentCanScrollUp by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    
    // Track if content can scroll up
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        contentCanScrollUp = scrollState.value > 0
    }
    
    val animatedHeight by animateDpAsState(
        targetValue = if (isVisible) sheetHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "sheetHeight"
    )
    
    fun performHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    
    // Custom nested scroll connection
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If scrolling down and content is at top, expand sheet
                if (available.y > 0 && scrollState.value == 0 && sheetHeight < actualMaxHeight) {
                    val newHeight = (sheetHeight + with(density) { available.y.toDp() })
                        .coerceIn(minHeight, actualMaxHeight)
                    if (newHeight != sheetHeight) {
                        sheetHeight = newHeight
                        return Offset(0f, available.y)
                    }
                }
                
                // If scrolling up and sheet is expanded, collapse first
                if (available.y < 0 && sheetHeight > minHeight) {
                    val newHeight = (sheetHeight + with(density) { available.y.toDp() })
                        .coerceAtLeast(minHeight)
                    if (newHeight != sheetHeight) {
                        sheetHeight = newHeight
                        return Offset(0f, available.y)
                    }
                }
                
                return Offset.Zero
            }
            
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Snap to nearest state after fling
                val midpoint = (minHeight + actualMaxHeight) / 2
                sheetHeight = if (sheetHeight > midpoint) actualMaxHeight else minHeight
                performHaptic()
                return super.onPostFling(consumed, available)
            }
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f)
        ) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop
            BlurBackdrop(
                isVisible = true,
                onDismiss = {
                    performHaptic()
                    onDismiss()
                }
            )
            
            // Sheet
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .height(animatedHeight)
                    .align(Alignment.BottomCenter)
                    .nestedScroll(nestedScrollConnection),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White.copy(alpha = 0.05f),
                shadowElevation = 24.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Handle
                    SheetHandle(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { isDraggingSheet = true },
                                    onDragEnd = {
                                        isDraggingSheet = false
                                        // Snap to nearest state
                                        val midpoint = (minHeight + actualMaxHeight) / 2
                                        sheetHeight = if (sheetHeight > midpoint) actualMaxHeight else minHeight
                                        performHaptic()
                                    },
                                    onDrag = { _, dragAmount ->
                                        val newHeight = sheetHeight - with(density) { dragAmount.y.toDp() }
                                        sheetHeight = newHeight.coerceIn(minHeight, actualMaxHeight)
                                    }
                                )
                            }
                    )
                    
                    // Header
                    if (title.isNotEmpty()) {
                        SheetHeader(
                            title = title,
                            subtitle = subtitle,
                            onClose = {
                                performHaptic()
                                onDismiss()
                            }
                        )
                        
                        HorizontalDivider(
                            color = CardBorder.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    
                    // Scrollable content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * QuickActionSheet - Bottom sheet for quick actions
 * Specialized sheet for action selection
 */
@Composable
fun QuickActionSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    title: String = "Quick Actions",
    actions: List<SheetQuickAction>,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    
    fun performHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f)
        ) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurBackdrop(
                isVisible = true,
                onDismiss = {
                    performHaptic()
                    onDismiss()
                }
            )
            
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White.copy(alpha = 0.05f),
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    SheetHandle(modifier = Modifier.padding(bottom = 12.dp))
                    
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    actions.forEach { action ->
                        QuickActionItem(
                            action = action,
                            onClick = {
                                performHaptic()
                                action.onClick()
                                if (action.dismissOnClick) {
                                    onDismiss()
                                }
                            }
                        )
                        
                        if (action != actions.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Cancel button
                    TextButton(
                        onClick = {
                            performHaptic()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

data class SheetQuickAction(
    val title: String,
    val subtitle: String = "",
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconTint: Color = ShinyGoldDeep,
    val isDestructive: Boolean = false,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionItem(
    action: SheetQuickAction,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${action.title}. ${action.subtitle}" },
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (action.isDestructive) 
                            ErrorRed.copy(alpha = 0.1f) 
                        else 
                            action.iconTint.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = if (action.isDestructive) ErrorRed else action.iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    color = if (action.isDestructive) ErrorRed else TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (action.subtitle.isNotEmpty()) {
                    Text(
                        text = action.subtitle,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * ConfirmationSheet - Sheet for confirmation dialogs
 */
@Composable
fun ConfirmationSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    
    fun performHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f)
        ) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurBackdrop(
                isVisible = true,
                onDismiss = {
                    performHaptic()
                    onDismiss()
                }
            )
            
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White.copy(alpha = 0.05f),
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SheetHandle(modifier = Modifier.padding(bottom = 20.dp))
                    
                    // Warning icon for destructive actions
                    if (isDestructive) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(ErrorRed.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = message,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel
                        OutlinedButton(
                            onClick = {
                                performHaptic()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                CardBorder.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = cancelText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        // Confirm
                        Button(
                            onClick = {
                                performHaptic()
                                onConfirm()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDestructive) ErrorRed else ShinyGoldDeep,
                                contentColor = if (isDestructive) Color.White else BlackBackground
                            )
                        ) {
                            Text(
                                text = confirmText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * InfoSheet - Simple informational bottom sheet
 */
@Composable
fun InfoSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f)
        ) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurBackdrop(
                isVisible = true,
                onDismiss = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onDismiss()
                }
            )
            
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White.copy(alpha = 0.05f),
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    SheetHandle(modifier = Modifier.padding(bottom = 12.dp))
                    
                    SheetHeader(
                        title = title,
                        onClose = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDismiss()
                        }
                    )
                    
                    HorizontalDivider(
                        color = CardBorder.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    content()
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
