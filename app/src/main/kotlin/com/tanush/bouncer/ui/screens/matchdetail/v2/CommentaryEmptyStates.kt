package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.EmptyStateV2

// The commentary tab's two empty states. A quiet feed and a filter that matched nothing are
// different facts about a match and read as different pages.

/** Contextual empty state — a quiet feed reads differently at stumps vs. pre-match. */
@Composable
internal fun CommentaryEmptyState(matchStatus: String) {
    val statusLower = matchStatus.lowercase()
    // The status text being matched is upstream scoreboard copy, always English; only the message
    // shown to the reader is a resource.
    val (title, subtitle) = when {
        statusLower.contains("stumps") ->
            R.string.commentary_empty_stumps_title to R.string.commentary_empty_stumps_subtitle
        statusLower.contains("break") ->
            R.string.commentary_empty_break_title to R.string.commentary_empty_break_subtitle
        statusLower.contains("rain") || statusLower.contains("delay") ->
            R.string.commentary_empty_rain_title to R.string.commentary_empty_rain_subtitle
        statusLower.contains("won") || statusLower.contains("drawn") ->
            R.string.commentary_empty_complete_title to R.string.commentary_empty_complete_subtitle
        else -> R.string.commentary_empty_none_title to R.string.commentary_empty_none_subtitle
    }
    EmptyStateV2(title = stringResource(title), subtitle = stringResource(subtitle))
}

/**
 * A filter that matched nothing in the commentary loaded so far.
 *
 * Rendering this as an empty list is what the tab did: a blank page carrying no message and no way
 * forward, because "load earlier" is offered under All only. It became the common case when the
 * first page dropped to eight overs, plenty of which hold no wicket and no milestone — so the
 * affordance the filtered view lacked lives here instead. Still a tap, never infinite scroll: a
 * page is billed reads.
 */
@Composable
internal fun FilterEmptyState(hasOlder: Boolean, isLoadingOlder: Boolean, onLoadOlder: (() -> Unit)?) {
    val actionLabel =
        if (isLoadingOlder) R.string.commentary_loading_older else R.string.commentary_load_older
    EmptyStateV2(
        title = stringResource(R.string.commentary_filter_empty_title),
        subtitle = stringResource(R.string.commentary_filter_empty_subtitle),
        onRetry = onLoadOlder.takeIf { hasOlder && !isLoadingOlder },
        actionLabel = stringResource(actionLabel),
    )
}
