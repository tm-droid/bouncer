package com.tanush.bouncer.ui.components.v2

import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.data.AppException
import com.tanush.bouncer.network.ScrapeHttpException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.delay

/** Why a load failed, in the only terms the reader can act on. */
enum class FailureKind { Offline, Server, Unknown }

/** What a screen shows. One of these per screen, chosen by [screenStateOf]. */
sealed interface ScreenState {
    data object Content : ScreenState
    data class ContentWithBanner(val kind: FailureKind, val savedAtMs: Long?) : ScreenState
    data object Loading : ScreenState
    data object Empty : ScreenState
    data class Failed(val kind: FailureKind) : ScreenState
}

private const val MAX_CAUSE_DEPTH = 6

/**
 * Walks the cause chain, because the app wraps transport errors (`AppException.NetworkException`,
 * `ScrapeHttpException` inside an `IOException`). A timeout means the network was reachable and the
 * server did not answer, so it reads as [FailureKind.Server], unlike a failed DNS lookup.
 */
fun failureKindOf(throwable: Throwable): FailureKind {
    var t: Throwable? = throwable
    repeat(MAX_CAUSE_DEPTH) {
        val kind = when (val current = t ?: return FailureKind.Unknown) {
            is AppException.NetworkException,
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException -> FailureKind.Offline
            is AppException.ApiException,
            is AppException.ScrapingException,
            is ScrapeHttpException,
            is SocketTimeoutException -> FailureKind.Server
            else -> null.also { t = current.cause }
        }
        if (kind != null) return kind
    }
    return FailureKind.Unknown
}

/** Content wins over loading, so a refresh never blanks a list; a failure with content is a banner. */
fun screenStateOf(
    hasContent: Boolean,
    isLoading: Boolean,
    failure: FailureKind?,
    savedAtMs: Long? = null,
): ScreenState = when {
    hasContent && failure != null -> ScreenState.ContentWithBanner(failure, savedAtMs)
    hasContent -> ScreenState.Content
    isLoading -> ScreenState.Loading
    failure != null -> ScreenState.Failed(failure)
    else -> ScreenState.Empty
}

const val SKELETON_DELAY_MS = 200L
const val SKELETON_MIN_SHOW_MS = 300L

/**
 * A skeleton for a load that finishes in 200 ms is a flash; one that vanishes the instant data
 * lands is a flicker. [visibleForMs] is null while the skeleton is not on screen.
 */
fun skeletonVisible(isLoading: Boolean, loadingForMs: Long, visibleForMs: Long?): Boolean =
    if (visibleForMs != null) isLoading || visibleForMs < SKELETON_MIN_SHOW_MS
    else isLoading && loadingForMs >= SKELETON_DELAY_MS

/** Wraps [skeleton] in the 200 ms / 300 ms rule; draws nothing during the delay. */
@Composable
fun DelayedSkeleton(isLoading: Boolean, skeleton: @Composable () -> Unit) {
    var shownAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(isLoading) {
        val shownFor = shownAt?.let { SystemClock.elapsedRealtime() - it }
        if (isLoading && shownFor == null) {
            delay(SKELETON_DELAY_MS)
            shownAt = SystemClock.elapsedRealtime()
        } else if (!isLoading && shownFor != null) {
            delay((SKELETON_MIN_SHOW_MS - shownFor).coerceAtLeast(0))
            shownAt = null
        }
    }
    if (shownAt != null) skeleton()
}

/** Cause-specific empty state for a load that failed with nothing to show. */
@Composable
fun FailedState(kind: FailureKind, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val offline = kind == FailureKind.Offline
    EmptyStateV2(
        title = stringResource(if (offline) R.string.state_offline_title else R.string.state_server_title),
        subtitle = stringResource(if (offline) R.string.state_offline_cause else R.string.state_server_cause),
        modifier = modifier,
        icon = if (offline) Icons.Outlined.WifiOff else Icons.Outlined.CloudOff,
        onRetry = onRetry,
        actionLabel = stringResource(if (offline) R.string.action_retry else R.string.state_try_again),
    )
}
