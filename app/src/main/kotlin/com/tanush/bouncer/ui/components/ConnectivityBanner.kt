package com.tanush.bouncer.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val OfflineBackground = Color(0xFF2D1010)
private val OnlineBackground = Color(0xFF0D2D10)
private val OfflineIconColor = Color(0xFFE53935)
private val OnlineIconColor = Color(0xFF4CAF50)

@Composable
fun rememberConnectivityState(): State<Boolean> {
    val context = LocalContext.current
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isOnline = remember {
        mutableStateOf(connectivityManager.activeNetwork != null)
    }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline.value = true
            }

            override fun onLost(network: Network) {
                isOnline.value = false
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    return isOnline
}

private fun relativeTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val elapsed = (System.currentTimeMillis() - timestampMs) / 1000
    return when {
        elapsed < 30 -> "· Just now"
        elapsed < 90 -> "· Last updated 1m ago"
        elapsed < 150 -> "· Last updated 2m ago"
        elapsed < 210 -> "· Last updated 3m ago"
        elapsed < 270 -> "· Last updated 4m ago"
        elapsed < 330 -> "· Last updated 5m ago"
        else -> "· Last updated 5m+ ago"
    }
}

@Composable
fun ConnectivityBanner(
    isOnline: Boolean,
    lastUpdateTimestamp: Long = 0L,
    modifier: Modifier = Modifier
) {
    // Track previous online state to detect reconnection
    var wasOffline by remember { mutableStateOf(!isOnline) }
    var showBackOnline by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (isOnline && wasOffline) {
            showBackOnline = true
            delay(2000)
            showBackOnline = false
        }
        wasOffline = !isOnline
    }

    val visible = !isOnline || showBackOnline

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)) +
                fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val isBackOnline = isOnline && showBackOnline
        val bgColor = if (isBackOnline) OnlineBackground else OfflineBackground
        val icon = if (isBackOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff
        val iconColor = if (isBackOnline) OnlineIconColor else OfflineIconColor
        val label = if (isBackOnline) "Back online" else {
            "Offline ${relativeTime(lastUpdateTimestamp)}"
        }
        val regionMode = if (isBackOnline) LiveRegionMode.Polite else LiveRegionMode.Assertive

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .semantics { liveRegion = regionMode }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ConnectivityBannerOfflinePreview() {
    ConnectivityBanner(
        isOnline = false,
        lastUpdateTimestamp = System.currentTimeMillis() - 120_000
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ConnectivityBannerBackOnlinePreview() {
    // Simulates the "back online" flash state
    AnimatedVisibility(visible = true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(OnlineBackground)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Wifi,
                contentDescription = null,
                tint = OnlineIconColor.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Back online",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}
