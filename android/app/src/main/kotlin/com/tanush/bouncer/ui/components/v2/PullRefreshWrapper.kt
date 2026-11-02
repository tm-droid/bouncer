package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.accessibility.rememberReducedMotionEnabled
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Swipe-to-refresh wrapper — the app's single pull-refresh surface.
 * Stock Material 3 disc with a gold arc; the arc follows the pull, spins while refreshing, and
 * stays a static arc under reduced motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefreshWrapperV2(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val reducedMotion = rememberReducedMotionEnabled()
    val haptics = LocalHapticFeedback.current
    // Once per upward crossing of the release threshold, not on every frame past it.
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .filter { it }
            .collect { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.IndicatorBox(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                val arcModifier = Modifier.size(ArcSize)
                val arcColor = BouncerThemeV2.colors.primaryText
                if (isRefreshing && !reducedMotion) {
                    CircularProgressIndicator(
                        modifier = arcModifier,
                        color = arcColor,
                        strokeWidth = ArcStroke,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { if (isRefreshing) 0.75f else state.distanceFraction.coerceIn(0f, 1f) },
                        modifier = arcModifier,
                        color = arcColor,
                        strokeWidth = ArcStroke,
                        trackColor = Color.Transparent,
                    )
                }
            }
        },
        content = { content() },
    )
}

private val ArcSize = 18.dp
private val ArcStroke = 2.5.dp
