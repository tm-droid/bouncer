package com.tanush.bouncer.ui.components.v2

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlinx.coroutines.delay

private const val FRESH_MAX_AGE_MS = 60_000L
private const val STALE_MIN_AGE_MS = 180_000L
private const val MS_PER_MINUTE = 60_000L
private const val TICK_MS = 30_000L

/** How old a live score is, so a frozen feed never reads as "live". */
sealed interface Freshness {
    /** Not live, final, or no timestamp: show no age. */
    data object Hidden : Freshness

    data object Fresh : Freshness

    data class Aging(val minutes: Int) : Freshness

    /** The UI shows "Last seen live" in place of LIVE. */
    data class Stale(val minutes: Int) : Freshness
}

fun freshnessOf(lastUpdatedMs: Long?, nowMs: Long, isLive: Boolean): Freshness {
    if (!isLive || lastUpdatedMs == null) return Freshness.Hidden
    // Negative age is clock skew between device and server, not a future score.
    val age = nowMs - lastUpdatedMs
    val minutes = (age / MS_PER_MINUTE).toInt().coerceAtLeast(1)
    return when {
        age <= FRESH_MAX_AGE_MS -> Freshness.Fresh
        age <= STALE_MIN_AGE_MS -> Freshness.Aging(minutes)
        else -> Freshness.Stale(minutes)
    }
}

@Composable
fun rememberFreshness(lastUpdatedMs: Long?, isLive: Boolean): State<Freshness> =
    produceState(
        initialValue = freshnessOf(lastUpdatedMs, System.currentTimeMillis(), isLive),
        lastUpdatedMs,
        isLive,
    ) {
        while (true) {
            value = freshnessOf(lastUpdatedMs, System.currentTimeMillis(), isLive)
            delay(TICK_MS)
        }
    }

@Composable
fun FreshnessLabel(freshness: Freshness, modifier: Modifier = Modifier) {
    val text = when (freshness) {
        Freshness.Hidden, Freshness.Fresh -> return
        is Freshness.Aging -> pluralStringResource(R.plurals.freshness_updated_min_ago, freshness.minutes, freshness.minutes)
        is Freshness.Stale -> stringResource(R.string.freshness_last_seen_live)
    }
    // textSecondary, never red: red is reserved for LIVE.
    Text(
        text = text,
        style = BouncerThemeV2.typography.numericSmall,
        color = BouncerThemeV2.colors.textSecondary,
        modifier = modifier,
    )
}
