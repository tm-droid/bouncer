package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.StatusWarning

// The legacy ErrorState/LoadingState/RetryingOverlay/AutoRetryHandler/SuccessSnackbar
// composables that lived here were unreferenced and have been removed —
// EmptyStateV2 + Material SnackbarHost are the canonical replacements.

@Composable
fun NetworkStatusBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    // Static: the enter/exit animation reports the state change; the warning colour holds it. The
    // pulse this replaced was an infinite transition created outside AnimatedVisibility, so it ran
    // on Home even while the device was online and the banner was not shown.
    AnimatedVisibility(
        visible = isOffline,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            StatusWarning.copy(alpha = 0.9f),
                            StatusWarning.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.state_no_internet_connection),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
