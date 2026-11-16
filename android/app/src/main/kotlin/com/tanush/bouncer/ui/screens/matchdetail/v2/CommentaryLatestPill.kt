package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/** Scrolls [state] to its first item each time [signal] changes after first composition. */
@Composable
internal fun ScrollToTopEffect(signal: Int, state: LazyListState) {
    var handled by remember { mutableIntStateOf(signal) }
    LaunchedEffect(signal) {
        if (signal != handled) {
            handled = signal
            state.animateScrollToItem(0)
        }
    }
}

/** Back to the newest line. Gold text: it is an action. Fades in and out, nothing else moves. */
@Composable
internal fun LatestPill(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            text = stringResource(R.string.commentary_jump_latest),
            style = BouncerThemeV2.typography.labelLarge,
            color = colors.primaryText,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.divider, CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}
