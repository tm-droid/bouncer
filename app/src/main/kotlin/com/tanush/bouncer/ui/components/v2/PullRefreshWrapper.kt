package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.CricketBallPullRefreshIndicator

/**
 * Swipe-to-refresh wrapper — the app's single pull-refresh surface.
 * Uses the cricket-ball indicator (brand moment; honors reduced motion internally).
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
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            CricketBallPullRefreshIndicator(
                isRefreshing = isRefreshing,
                pullProgress = state.distanceFraction,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
            )
        },
        content = { content() },
    )
}
