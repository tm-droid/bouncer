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
import io.sentry.Sentry
import android.view.View
import com.tanush.bouncer.ui.theme.GoldPrimary

enum class SheetState {
    HIDDEN,
    PEEK,
    HALF,
    EXPANDED
}

private val SheetEnterTransition = slideInVertically(
    initialOffsetY = { it },
    animationSpec = spring(dampingRatio = 0.75f)
) + fadeIn()

private val SheetExitTransition = slideOutVertically(
    targetOffsetY = { it },
    animationSpec = spring(dampingRatio = 0.75f)
) + fadeOut()

private fun View.safeHaptic() {
    try {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@Composable
private fun SlideUpSheet(
    isVisible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = SheetEnterTransition,
        exit = SheetExitTransition
    ) {
        content()
    }
}

@Composable
private fun SheetSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = SheetShape,
        color = GlassSurface,
        shadowElevation = 24.dp,
        content = content
    )
}

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

    val peekHeightPx = with(density) { peekHeight.toPx() }
    val halfHeightPx = with(density) { (screenHeight * 0.5f).toPx() }
    val expandedHeightPx = with(density) { (screenHeight * 0.9f).toPx() }
    val screenPx = with(density) { screenHeight.toPx() }

    var currentState by remember { mutableStateOf(if (isVisible) initialState else SheetState.HIDDEN) }

    val targetOffset = when (currentState) {
        SheetState.HIDDEN -> screenPx
        SheetState.PEEK -> screenPx - peekHeightPx
        SheetState.HALF -> screenPx - halfHeightPx
        SheetState.EXPANDED -> screenPx - expandedHeightPx
    }

    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "sheetOffset"
    )

    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun performHaptic() {
        if (enableHaptics) view.safeHaptic()
    }

    fun findClosestState(currentOffset: Float): SheetState {
        val offsets = mapOf(
            SheetState.HIDDEN to screenPx,
            SheetState.PEEK to screenPx - peekHeightPx,
            SheetState.HALF to screenPx - halfHeightPx,
            SheetState.EXPANDED to screenPx - expandedHeightPx
        )
        return offsets.minByOrNull { kotlin.math.abs(it.value - currentOffset) }?.key ?: SheetState.HALF
    }

    LaunchedEffect(isVisible) {
        if (isVisible && currentState == SheetState.HIDDEN) {
            currentState = initialState
        } else if (!isVisible) {
            currentState = SheetState.HIDDEN
        }
    }

    if (isVisible || currentState != SheetState.HIDDEN) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (enableBlurBackdrop) {
                BlurBackdrop(
                    isVisible = currentState != SheetState.HIDDEN,
                    onDismiss = {
                        performHaptic()
                        onDismiss()
                    }
                )
            } else {
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

            SheetSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { IntOffset(0, (animatedOffset + dragOffset).roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> dragOffset += delta },
                        onDragStopped = { velocity ->
                            val newOffset = animatedOffset + dragOffset
                            val newState = findClosestState(newOffset)

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

                            if (adjustedState != currentState) performHaptic()

                            currentState = adjustedState
                            dragOffset = 0f

                            if (currentState == SheetState.HIDDEN) onDismiss()
                        }
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SheetHandle(modifier = Modifier.padding(vertical = 12.dp))
                    sheetContent()
                }
            }
        }
    }
}

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
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "sheetHeight"
    )

    fun performHaptic() = view.safeHaptic()

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            currentState = SheetState.HIDDEN
        } else if (currentState == SheetState.HIDDEN) {
            currentState = SheetState.PEEK
        }
    }

    LaunchedEffect(currentState) { onStateChange(currentState) }

    if (isVisible || currentState != SheetState.HIDDEN) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurBackdrop(
                isVisible = currentState != SheetState.HIDDEN && currentState != SheetState.PEEK,
                onDismiss = {
                    performHaptic()
                    currentState = if (currentState == SheetState.EXPANDED) SheetState.HALF else SheetState.PEEK
                }
            )

            SheetSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .height(currentHeight)
                    .align(Alignment.BottomCenter)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {},
                            onDrag = { _, dragAmount ->
                                val threshold = 50f
                                if (dragAmount.y < -threshold) {
                                    performHaptic()
                                    currentState = when (currentState) {
                                        SheetState.PEEK -> SheetState.HALF
                                        SheetState.HALF -> SheetState.EXPANDED
                                        else -> currentState
                                    }
                                } else if (dragAmount.y > threshold) {
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
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                SheetHandle(modifier = Modifier.padding(vertical = 10.dp))

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

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentState) {
                        SheetState.PEEK -> peekContent()
                        SheetState.HALF -> Column {
                            peekContent()
                            halfContent()
                        }
                        SheetState.EXPANDED -> Column {
                            peekContent()
                            halfContent()
                            expandedContent()
                        }
                        else -> {}
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(SheetState.PEEK, SheetState.HALF, SheetState.EXPANDED).forEachIndexed { index, stage ->
                        if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                        StageIndicator(
                            isActive = currentState == stage,
                            onClick = {
                                performHaptic()
                                currentState = stage
                            }
                        )
                    }
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
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
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
                if (isActive) hierarchy.secondary.primary else colors.glassBorder.copy(alpha = 0.5f)
            )
            .clickable { onClick() }
    )
}

@Composable
fun SheetHandle(
    modifier: Modifier = Modifier,
    color: Color = GlassBorder,
    width: Dp = 40.dp,
    height: Dp = 4.dp
) {
    val hierarchy = BouncerDesignSystem.hierarchy
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
                            hierarchy.secondary.primary.copy(alpha = shimmer * 0.5f),
                            color
                        )
                    )
                )
        )
    }
}

@Composable
fun SheetHeader(
    title: String,
    subtitle: String = "",
    showCloseButton: Boolean = true,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = BouncerDesignSystem.colors
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
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
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
                        .background(GlassSurface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close sheet",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NestedScrollSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    minHeight: Dp = 200.dp,
    maxHeight: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val actualMaxHeight = if (maxHeight == 0.dp) screenHeight - 100.dp else maxHeight
    val view = LocalView.current

    var sheetHeight by remember { mutableStateOf(minHeight) }
    var isDraggingSheet by remember { mutableStateOf(false) }
    var contentCanScrollUp by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        contentCanScrollUp = scrollState.value > 0
    }

    val animatedHeight by animateDpAsState(
        targetValue = if (isVisible) sheetHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "sheetHeight"
    )

    fun performHaptic() = view.safeHaptic()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                try {
                    if (available.y > 0 && scrollState.value == 0 && sheetHeight < actualMaxHeight) {
                        val newHeight = (sheetHeight + with(density) { available.y.toDp() })
                            .coerceIn(minHeight, actualMaxHeight)
                        if (newHeight != sheetHeight) {
                            sheetHeight = newHeight
                            return Offset(0f, available.y)
                        }
                    }

                    if (available.y < 0 && sheetHeight > minHeight) {
                        val newHeight = (sheetHeight + with(density) { available.y.toDp() })
                            .coerceAtLeast(minHeight)
                        if (newHeight != sheetHeight) {
                            sheetHeight = newHeight
                            return Offset(0f, available.y)
                        }
                    }

                    return Offset.Zero
                } catch (e: Exception) {
                    Sentry.captureException(e)
                    throw e
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                try {
                    val midpoint = (minHeight + actualMaxHeight) / 2
                    sheetHeight = if (sheetHeight > midpoint) actualMaxHeight else minHeight
                    performHaptic()
                    return super.onPostFling(consumed, available)
                } catch (e: Exception) {
                    Sentry.captureException(e)
                    throw e
                }
            }
        }
    }

    SlideUpSheet(isVisible = isVisible) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurBackdrop(
                isVisible = true,
                onDismiss = {
                    performHaptic()
                    onDismiss()
                }
            )

            SheetSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .height(animatedHeight)
                    .align(Alignment.BottomCenter)
                    .nestedScroll(nestedScrollConnection)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                SheetHandle(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { isDraggingSheet = true },
                                onDragEnd = {
                                    isDraggingSheet = false
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
                        color = colors.glassBorder.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

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

@Composable
fun QuickActionSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    title: String = "Quick Actions",
    actions: List<SheetQuickAction>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val view = LocalView.current

    fun performHaptic() = view.safeHaptic()

    SlideUpSheet(isVisible = isVisible) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurBackdrop(
                isVisible = true,
                onDismiss = {
                    performHaptic()
                    onDismiss()
                }
            )

            SheetSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    SheetHandle(modifier = Modifier.padding(bottom = 12.dp))

                    Text(
                        text = title,
                        color = colors.textPrimary,
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
                                if (action.dismissOnClick) onDismiss()
                            }
                        )

                        if (action != actions.last()) Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            performHaptic()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            color = colors.textSecondary,
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
    val iconTint: Color = GoldPrimary,
    val isDestructive: Boolean = false,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionItem(
    action: SheetQuickAction,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val accent = if (action.isDestructive) colors.statusLive else action.iconTint
    val titleColor = if (action.isDestructive) colors.statusLive else colors.textPrimary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${action.title}. ${action.subtitle}" },
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accent.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    color = titleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (action.subtitle.isNotEmpty()) {
                    Text(
                        text = action.subtitle,
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

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
    val colors = BouncerDesignSystem.colors
    val view = LocalView.current

    fun performHaptic() = view.safeHaptic()

    SlideUpSheet(isVisible = isVisible) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurBackdrop(
                isVisible = true,
                onDismiss = {
                    performHaptic()
                    onDismiss()
                }
            )

            SheetSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SheetHandle(modifier = Modifier.padding(bottom = 20.dp))

                    if (isDestructive) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(colors.statusLive.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = colors.statusLive,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = message,
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                contentColor = colors.textSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                colors.glassBorder.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(text = cancelText, fontWeight = FontWeight.SemiBold)
                        }

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
                                containerColor = if (isDestructive) colors.statusLive else colors.goldPrimary,
                                contentColor = if (isDestructive) Color.White else colors.backgroundBase
                            )
                        ) {
                            Text(text = confirmText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val view = LocalView.current

    SlideUpSheet(isVisible = isVisible) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurBackdrop(
                isVisible = true,
                onDismiss = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onDismiss()
                }
            )

            SheetSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
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
                        color = colors.glassBorder.copy(alpha = 0.3f),
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
