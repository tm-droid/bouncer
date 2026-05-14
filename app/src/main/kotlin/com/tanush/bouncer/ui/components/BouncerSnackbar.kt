package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// =============================================================================
// Data Models
// =============================================================================

enum class BouncerSnackbarType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class BouncerSnackbarData(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: BouncerSnackbarType = BouncerSnackbarType.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 3000L
)

// =============================================================================
// State Holder
// =============================================================================

class BouncerSnackbarHostState {
    private val queue = MutableStateFlow<List<BouncerSnackbarData>>(emptyList())

    val currentSnackbar: State<BouncerSnackbarData?>
        @Composable get() {
            val list by queue.collectAsStateWithLifecycle()
            return remember { derivedStateOf { list.firstOrNull() } }
        }

    fun show(data: BouncerSnackbarData) {
        queue.update { current -> current + data }
    }

    fun dismiss(id: Long) {
        queue.update { current -> current.filter { it.id != id } }
    }
}

@Composable
fun rememberBouncerSnackbarHostState(): BouncerSnackbarHostState {
    return remember { BouncerSnackbarHostState() }
}

// =============================================================================
// Accent Colors
// =============================================================================

private val SnackbarAccentInfo = Color(0xFFD4AF37)
private val SnackbarAccentSuccess = Color(0xFF4CAF50)
private val SnackbarAccentWarning = Color(0xFFFFC107)
private val SnackbarAccentError = Color(0xFFE53935)
private val SnackbarCardBackground = Color(0xFF1A1A1A)

private fun accentColor(type: BouncerSnackbarType): Color = when (type) {
    BouncerSnackbarType.INFO -> SnackbarAccentInfo
    BouncerSnackbarType.SUCCESS -> SnackbarAccentSuccess
    BouncerSnackbarType.WARNING -> SnackbarAccentWarning
    BouncerSnackbarType.ERROR -> SnackbarAccentError
}

// =============================================================================
// Host Composable
// =============================================================================

@Composable
fun BouncerSnackbarHost(
    hostState: BouncerSnackbarHostState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 80.dp
) {
    val current by hostState.currentSnackbar

    // Auto-dismiss
    LaunchedEffect(current?.id) {
        val snackbar = current ?: return@LaunchedEffect
        delay(snackbar.durationMs)
        hostState.dismiss(snackbar.id)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f) // EaseOutExpo
                )
            ) { it } + fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            current?.let { data ->
                BouncerSnackbarCard(
                    data = data,
                    onDismiss = { hostState.dismiss(data.id) },
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = bottomPadding
                    )
                )
            }
        }
    }
}

// =============================================================================
// Snackbar Visual
// =============================================================================

@Composable
private fun BouncerSnackbarCard(
    data: BouncerSnackbarData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val accent = accentColor(data.type)
    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(density) { 100.dp.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(SnackbarCardBackground)
            .graphicsLayer { translationX = offsetX }
            .pointerInput(data.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > dismissThresholdPx) {
                            onDismiss()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceAtLeast(0f)
                    }
                )
            }
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent border
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .defaultMinSize(minHeight = 48.dp)
                .background(accent)
        )

        // Message
        Text(
            text = data.message,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        )

        // Action button
        if (data.actionLabel != null) {
            TextButton(
                onClick = {
                    data.onAction?.invoke()
                    onDismiss()
                },
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = data.actionLabel,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// =============================================================================
// Previews
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun BouncerSnackbarInfoPreview() {
    BouncerSnackbarCard(
        data = BouncerSnackbarData(
            message = "Match starts in 30 minutes",
            type = BouncerSnackbarType.INFO
        ),
        onDismiss = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun BouncerSnackbarSuccessPreview() {
    BouncerSnackbarCard(
        data = BouncerSnackbarData(
            message = "Score updated successfully",
            type = BouncerSnackbarType.SUCCESS
        ),
        onDismiss = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun BouncerSnackbarWarningPreview() {
    BouncerSnackbarCard(
        data = BouncerSnackbarData(
            message = "Slow network detected",
            type = BouncerSnackbarType.WARNING,
            actionLabel = "Retry"
        ),
        onDismiss = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun BouncerSnackbarErrorPreview() {
    BouncerSnackbarCard(
        data = BouncerSnackbarData(
            message = "Failed to load match data",
            type = BouncerSnackbarType.ERROR,
            actionLabel = "Retry"
        ),
        onDismiss = {}
    )
}
