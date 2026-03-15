package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * Prevents skeleton/loading flicker on fast network responses.
 *
 * When [isLoading] becomes `true`, a delay timer starts. The [loadingContent]
 * is only rendered after [delayMs] has elapsed **and** [isLoading] is still
 * `true`. If loading completes before the delay, the skeleton is never shown.
 *
 * @param isLoading whether the data is currently loading
 * @param delayMs   minimum time before showing the loading skeleton (default 200 ms)
 * @param loadingContent composable to render while loading (shown after delay)
 * @param content        composable to render when loading is complete
 */
@Composable
fun DelayedContent(
    isLoading: Boolean,
    delayMs: Long = 200L,
    loadingContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    var showLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            showLoading = false
            delay(delayMs)
            showLoading = true
        } else {
            showLoading = false
        }
    }

    if (isLoading && showLoading) {
        loadingContent()
    }

    AnimatedVisibility(
        visible = !isLoading,
        enter = fadeIn(
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
        )
    ) {
        content()
    }
}

/**
 * Dispatches rendering based on a [ScreenState] sealed class, with built-in
 * delayed-loading support to avoid skeleton flicker.
 *
 * @param state          current screen state
 * @param loadingContent composable shown during loading (after flicker guard)
 * @param emptyHint      fallback hint when state is [ScreenState.Empty]
 * @param onRetry        optional retry callback surfaced in [ErrorCard]
 * @param content        composable rendered with the loaded data
 */
@Composable
fun <T> ScreenStateContent(
    state: ScreenState<T>,
    loadingContent: @Composable () -> Unit,
    emptyHint: String = "No data available",
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is ScreenState.Loading -> {
            DelayedContent(
                isLoading = true,
                loadingContent = loadingContent,
                content = {}
            )
        }

        is ScreenState.Partial -> {
            Box {
                content(state.data)
                StaleDataBadge(
                    staleAtMillis = state.staleAtMillis,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        is ScreenState.Success -> {
            content(state.data)
        }

        is ScreenState.Empty -> {
            EmptyStateCard(hint = state.hint)
        }

        is ScreenState.Error -> {
            ErrorCard(
                errorCause = state.cause,
                message = state.message,
                onRetry = onRetry ?: state.retryAction
            )
        }
    }
}
