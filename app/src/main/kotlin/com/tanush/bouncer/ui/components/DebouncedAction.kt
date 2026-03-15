package com.tanush.bouncer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Returns a debounced wrapper around [action] that silently drops invocations
 * occurring within [intervalMs] of the last successful execution.
 *
 * No coroutines are involved — the check is a simple wall-clock comparison.
 *
 * @param intervalMs minimum gap between consecutive executions (default 2 000 ms)
 * @param action     the callback to debounce
 * @return a `() -> Unit` that guards [action] with the debounce interval
 */
@Composable
fun rememberDebouncedAction(
    intervalMs: Long = 2000L,
    action: () -> Unit
): () -> Unit {
    val lastExecutionTime = remember { mutableLongStateOf(0L) }

    return remember(action, intervalMs) {
        {
            val now = System.currentTimeMillis()
            if (now - lastExecutionTime.longValue >= intervalMs) {
                lastExecutionTime.longValue = now
                action()
            }
        }
    }
}

/**
 * Returns a throttled wrapper around a suspending [action]. If the action is
 * already in flight, subsequent calls are silently ignored until it completes.
 *
 * @param intervalMs minimum gap between the *start* of consecutive executions (default 1 000 ms)
 * @param action     the suspending callback to throttle
 * @return a `() -> Unit` that launches [action] at most once at a time
 */
@Composable
fun rememberThrottledAction(
    intervalMs: Long = 1000L,
    action: suspend () -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val isRunning = remember { mutableStateOf(false) }
    val lastExecutionTime = remember { mutableLongStateOf(0L) }

    return remember(action, intervalMs) {
        {
            val now = System.currentTimeMillis()
            if (!isRunning.value && now - lastExecutionTime.longValue >= intervalMs) {
                isRunning.value = true
                lastExecutionTime.longValue = now
                scope.launch {
                    try {
                        action()
                    } finally {
                        isRunning.value = false
                    }
                }
            }
        }
    }
}
