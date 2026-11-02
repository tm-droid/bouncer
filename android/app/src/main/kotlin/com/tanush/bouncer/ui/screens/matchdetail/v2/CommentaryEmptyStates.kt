package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.viewmodels.CommentaryEventKind
import com.tanush.bouncer.viewmodels.CommentaryEventPage

// The commentary tab's empty states, and what "load earlier" means under each filter. A quiet feed and a filter that matched nothing are
// different facts about a match and read as different pages.

/** Contextual empty state — a quiet feed reads differently at stumps vs. pre-match. */
@Composable
internal fun CommentaryEmptyState(matchStatus: String, onOpenInfo: (() -> Unit)? = null) {
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
        else -> R.string.state_no_commentary_title to R.string.state_no_commentary_cause
    }
    val offersInfo = title == R.string.state_no_commentary_title && onOpenInfo != null
    EmptyStateV2(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        icon = Icons.Outlined.ChatBubbleOutline,
        onRetry = onOpenInfo.takeIf { offersInfo },
        actionLabel = stringResource(R.string.state_open_match_info).takeIf { offersInfo },
    )
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
        icon = Icons.Outlined.FilterAlt,
        subtitle = stringResource(R.string.commentary_filter_empty_subtitle),
        onRetry = onLoadOlder.takeIf { hasOlder && !isLoadingOlder },
        actionLabel = stringResource(actionLabel),
    )
}

/** What "load earlier" does under the selected filter — null when there is nothing to offer. */
internal class OlderPaging(val onLoad: (() -> Unit)?, val isLoading: Boolean)

/**
 * All pages the feed; Wickets and Boundaries page their own list; Milestones pages nothing — it
 * holds a handful of lines from the loaded feed, and a page would very likely add none.
 */
internal fun olderPagingFor(
    filter: CommentaryFilter,
    feedPaging: OlderPaging,
    eventPage: CommentaryEventPage?,
    onLoadOlderEvents: ((CommentaryEventKind) -> Unit)?,
): OlderPaging {
    val kind = filter.eventKind
    return when {
        filter == CommentaryFilter.ALL -> feedPaging
        kind != null && eventPage != null -> OlderPaging(
            onLoad = onLoadOlderEvents?.takeIf { eventPage.hasOlder }?.let { load -> { load(kind) } },
            isLoading = eventPage.loading,
        )
        else -> OlderPaging(onLoad = null, isLoading = false)
    }
}

/**
 * A filter with no line to draw.
 *
 * Once the table has answered for the whole match there is nothing earlier to load; until it has
 * (Milestones, or a fetch that failed) the loaded-feed state and its load-earlier action stand.
 */
@Composable
internal fun FilteredEmptyState(eventPage: CommentaryEventPage?, feedPaging: OlderPaging) {
    if (eventPage != null && (eventPage.loaded || eventPage.loading)) {
        EventFilterEmptyState(isLoading = eventPage.loading)
    } else {
        FilterEmptyState(feedPaging.onLoad != null, feedPaging.isLoading, feedPaging.onLoad)
    }
}

/**
 * A Wickets or Boundaries filter with nothing to show for the WHOLE match — the table was asked,
 * so unlike [FilterEmptyState] there is no earlier page to offer.
 */
@Composable
internal fun EventFilterEmptyState(isLoading: Boolean) {
    EmptyStateV2(
        title = stringResource(R.string.commentary_filter_empty_title),
        icon = Icons.Outlined.FilterAlt,
        subtitle = stringResource(
            if (isLoading) R.string.commentary_loading_older else R.string.commentary_filter_none_subtitle
        ),
    )
}
