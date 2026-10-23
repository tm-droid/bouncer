package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

// The legacy ErrorState/LoadingState/RetryingOverlay/AutoRetryHandler/SuccessSnackbar
// composables that lived here were unreferenced and have been removed —
// EmptyStateV2 + Material SnackbarHost are the canonical replacements.

@Composable
fun NetworkStatusBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    // Static and flat, like StaleDataBanner: the enter/exit animation reports the state change. The
    // pulse this replaced was an infinite transition created outside AnimatedVisibility, so it ran
    // on Home even while the device was online and the banner was not shown.
    AnimatedVisibility(
        visible = isOffline,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
    ) {
        val colors = BouncerThemeV2.colors
        Column(modifier.fillMaxWidth().background(colors.surfaceVariant)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.state_no_internet_connection),
                    style = BouncerThemeV2.typography.bodySmall,
                    color = colors.onBackground,
                )
            }
            HorizontalDivider(color = colors.divider)
        }
    }
}
