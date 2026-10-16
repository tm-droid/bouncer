package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.ui.components.v2.BallGlyphKind
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.ballGlyphKindOf
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.ShimmerCommentaryList
import com.tanush.bouncer.ui.theme.v2.BouncerSpacingV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.CommentaryEventKind
import com.tanush.bouncer.viewmodels.CommentaryEventPage
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics

// ── Main composable ──────────────────────────────────────────────────────────

@Composable
fun CommentaryTabV2(
    commentary: List<CommentaryEntry>,
    matchStatus: String = "",
    isLoading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    hasOlder: Boolean = false,
    isLoadingOlder: Boolean = false,
    onLoadOlder: (() -> Unit)? = null,
    eventPages: Map<CommentaryEventKind, CommentaryEventPage> = emptyMap(),
    onOpenEvents: ((CommentaryEventKind) -> Unit)? = null,
    onLoadOlderEvents: ((CommentaryEventKind) -> Unit)? = null,
    /** Shown under the filters when the feed is not the table's own lines (Cricsheet's ball-by-ball). */
    sourceCaption: String? = null,
) {
    if (isLoading) {
        ShimmerCommentaryList(modifier = Modifier.fillMaxSize())
        return
    }

    // Feed failed (distinct from a genuinely quiet match)
    if (error != null && commentary.isEmpty()) {
        EmptyStateV2(
            title = stringResource(R.string.commentary_error_title),
            subtitle = error,
            onRetry = onRetry,
        )
        return
    }

    if (commentary.isEmpty()) {
        CommentaryEmptyState(matchStatus)
        return
    }

    CommentaryFeedBody(
        commentary = commentary,
        feedPaging = OlderPaging(onLoad = onLoadOlder.takeIf { hasOlder }, isLoading = isLoadingOlder),
        eventPages = eventPages,
        onOpenEvents = onOpenEvents,
        onLoadOlderEvents = onLoadOlderEvents,
        sourceCaption = sourceCaption,
    )
}

@Composable
private fun CommentaryFeedBody(
    commentary: List<CommentaryEntry>,
    feedPaging: OlderPaging,
    eventPages: Map<CommentaryEventKind, CommentaryEventPage>,
    onOpenEvents: ((CommentaryEventKind) -> Unit)?,
    onLoadOlderEvents: ((CommentaryEventKind) -> Unit)?,
    sourceCaption: String?,
) {
    val spacing = BouncerThemeV2.spacing
    var selectedFilter by remember { mutableStateOf(CommentaryFilter.ALL) }
    // Labels are for display only; the filter itself travels as an enum, so a translated chip
    // cannot silently turn every filter into "All".
    val filterLabels = CommentaryFilter.entries.map { stringResource(it.labelRes) }

    // Wickets and Boundaries are asked of the table for the whole match the first time the chip is
    // selected: filtering the loaded pages alone showed the last eight overs' events and nothing
    // before them. Milestones has no server verdict to ask for, so it stays on the loaded feed.
    val eventKind = selectedFilter.eventKind
    val eventPage = eventKind?.let { eventPages[it] }
    LaunchedEffect(eventKind) { eventKind?.let { onOpenEvents?.invoke(it) } }
    val eventRows = eventPage?.entries.orEmpty()

    // Filtering, regex ball parsing and block building over up to 500 rows re-ran on the main thread
    // for every live update. They run on Default now; the first frame is still built synchronously
    // so the tab never opens empty, and later updates keep showing the previous feed until the new
    // one is ready.
    val feed by produceState(
        initialValue = remember { buildCommentaryFeed(commentary, selectedFilter) },
        commentary,
        selectedFilter,
        eventRows,
    ) {
        value = withContext(Dispatchers.Default) { buildCommentaryFeed(commentary, selectedFilter, eventRows) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterChipRowV2(
            items = filterLabels,
            selectedItem = filterLabels[selectedFilter.ordinal],
            onItemSelected = { label ->
                filterLabels.indexOf(label).takeIf { it >= 0 }
                    ?.let { selectedFilter = CommentaryFilter.entries[it] }
            },
            modifier = Modifier.padding(vertical = spacing.sm),
        )
        sourceCaption?.let {
            Text(
                text = it,
                style = BouncerThemeV2.typography.labelMedium,
                color = BouncerThemeV2.colors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        }

        // A filter that matched nothing in the loaded feed used to render an empty LazyColumn — a
        // blank page with no message and no way forward, because "load older" is offered under All
        // only. It is the common case now that the first page is 8 overs rather than 25: plenty of
        // them hold no wicket and no milestone.
        if (feed.blocks.isEmpty() && selectedFilter != CommentaryFilter.ALL) {
            FilteredEmptyState(eventPage, feedPaging)
            return@Column
        }

        val older = olderPagingFor(selectedFilter, feedPaging, eventPage, onLoadOlderEvents)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = spacing.md),
        ) {
            commentaryFeedItems(
                feed = feed,
                selectedFilter = selectedFilter,
                spacing = spacing,
                onLoadOlder = older.onLoad,
                isLoadingOlder = older.isLoading,
            )
        }
    }
}

private fun LazyListScope.commentaryFeedItems(
    feed: CommentaryFeed,
    selectedFilter: CommentaryFilter,
    spacing: BouncerSpacingV2,
    onLoadOlder: (() -> Unit)?,
    isLoadingOlder: Boolean,
) {
    val showOverCards = selectedFilter == CommentaryFilter.ALL
    // Keys come from commentaryItemKeys: stable across live updates, unique by construction
    // (a duplicate key throws, ANDROID-40).
    feed.blocks.forEachIndexed { blockIndex, block ->
        val keys = feed.keys[blockIndex]
        if (block.startsInnings) {
            item(key = keys.innings) { InningsDivider(block.innings) }
        }
        // An over card is a statement about an over, so it is drawn only where the feed is showing
        // whole overs. A filtered view holds one ball of each, and a card over it would read as a
        // claim about the other five.
        if (showOverCards && block.overNumber != null) {
            item(key = keys.over) {
                OverSeparatorCard(overNumber = block.overNumber, balls = block.balls)
            }
        }
        items(block.balls.size, key = { keys.balls[it] }) { idx ->
            val ball = block.balls[idx]
            // Non-deliveries (session notes, weather) carry no ball label and get no pill.
            if (ball.ballLabel == null) {
                NonBallLine(ball.ballText)
            } else {
                CommentaryLine(ball, modifier = Modifier.animateItem())
            }
        }
    }

    if (onLoadOlder != null) {
        item(key = "load_older") { LoadOlderRow(isLoadingOlder, onLoadOlder) }
    }

    item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
}

/**
 * Fetches the next page back, on a tap.
 *
 * Reader-driven rather than infinite scroll because Appwrite bills per row RETURNED: opening the
 * match is a 48-row page and every page behind it is 150, and a Test innings is several of them.
 * Someone scrolling back through a session asks for it; someone glancing at the last over does not
 * pay for it.
 */
@Composable
private fun LoadOlderRow(isLoading: Boolean, onClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (isLoading) R.string.commentary_loading_older else R.string.commentary_load_older
            ),
            style = typography.labelMedium,
            // Gold is the interactive affordance; the loading state is not one.
            color = if (isLoading) colors.textSecondary else colors.primaryText,
        )
    }
}

/**
 * States an innings boundary instead of letting the feed imply one.
 *
 * Without this the tab had two bad options for a feed spanning innings: render it in wall-clock
 * order, where a dismissal at 60.2 sits above one at 80.5 for no visible reason, or re-sort by over
 * and lose the chronology outright. It renders neither a number it does not have — an unlabelled
 * boundary says only that the innings changed.
 */
@Composable
private fun InningsDivider(innings: Int?) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.divider)
        Text(
            text = innings?.let { stringResource(R.string.commentary_innings, it) }
                ?: stringResource(R.string.commentary_innings_change),
            style = typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
        HorizontalDivider(color = colors.divider)
    }
}

// ── Over separator card ──────────────────────────────────────────────────────

@Composable
private fun OverSeparatorCard(overNumber: Int, balls: List<ParsedBall>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val totalRuns = overTotalRuns(balls)
    val isMaiden = isMaidenOver(balls)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primary.copy(alpha = 0.15f))
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        // `overNumber` counts COMPLETED overs, as ball labels do: balls 48.1-48.6
                        // belong to over 49, which is what Cricbuzz's own overNumber field says and
                        // what every scorecard prints.
                        text = stringResource(R.string.over_number, overNumber + 1),
                        style = typography.labelSmall,
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (isMaiden) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.cricket.maiden.copy(alpha = 0.2f))
                            .padding(horizontal = spacing.sm, vertical = spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.commentary_maiden),
                            style = typography.labelSmall,
                            color = colors.cricket.maiden,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (totalRuns != null) {
                Text(
                    text = "$totalRuns run${if (totalRuns != 1) "s" else ""}",
                    style = typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }

        if (balls.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            // Oldest-first, the order they were bowled. `balls` arrives in feed order, which is
            // newest-first, so the chips used to read right to left against the lines under them.
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                balls.asReversed().forEach { ball ->
                    // An en dash, not a dot: the over keeps its six positions, and an unknown ball
                    // stays visibly distinct from one that was actually a dot.
                    BallBadge(ball.toBallString() ?: UNKNOWN_BALL_GLYPH, size = 22.dp)
                }
            }
        }

        HorizontalDivider(
            color = colors.primary.copy(alpha = 0.12f),
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}

// ── Commentary row ───────────────────────────────────────────────────────────

@Composable
private fun CommentaryLine(ball: ParsedBall, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing

    var showMenu by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.commentary_line_actions)

    val bgColor = when {
        ball.isWicket -> colors.live.copy(alpha = 0.08f)
        ball.isSix || ball.isFour -> colors.win.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                // Long press only. This was a combinedClickable with an empty onClick, so TalkBack
                // announced every delivery as a button whose double-tap did nothing, and the one
                // real action had no spoken name.
                .pointerInput(Unit) { detectTapGestures(onLongPress = { showMenu = true }) }
                .semantics {
                    onLongClick(label = menuLabel) {
                        showMenu = true
                        true
                    }
                }
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (ball.ballLabel != null) {
                    ResultPill(ball)
                    Spacer(Modifier.width(spacing.xs))
                    BallLabel(ball.ballLabel)
                    Spacer(Modifier.width(spacing.sm))
                }

                Text(
                    text = remember(ball.ballText) { parseBoldTags(ball.ballText) },
                    style = BouncerThemeV2.typography.bodySmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(
                color = colors.divider,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }

        CommentaryLineMenu(expanded = showMenu, shareText = buildShareText(ball), onDismiss = { showMenu = false })
    }
}

@Composable
private fun NonBallLine(text: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Text(
            text = remember(text) { parseBoldTags(text) },
            style = typography.bodySmall,
            color = colors.textSecondary,
        )
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = spacing.sm))
    }
}

// ── Small composables ────────────────────────────────────────────────────────

@Composable
private fun BallBadge(ball: String, size: Dp = 28.dp) {
    val colors = BouncerThemeV2.colors
    val cricket = colors.cricket
    // Classification is shared with the score header's strip (ui/components/v2/BallGlyph.kt); only
    // the treatment differs. Two copies of this `when` disagreed on `W1` for as long as they
    // existed.
    val (bg, fg) = when (ballGlyphKindOf(ball)) {
        // The tint carries the event colour; the glyph is the page's ink. Event ink on its own
        // tint was 3.4–4.2:1 for an 11sp glyph.
        BallGlyphKind.WICKET -> cricket.wicket.copy(alpha = 0.2f) to colors.onSurface
        BallGlyphKind.SIX -> cricket.six.copy(alpha = 0.2f) to colors.onSurface
        BallGlyphKind.FOUR -> cricket.four.copy(alpha = 0.2f) to colors.onSurface
        BallGlyphKind.EXTRA -> cricket.wide.copy(alpha = 0.15f) to colors.onSurface
        BallGlyphKind.DOT -> colors.surfaceVariant to colors.textSecondary
        BallGlyphKind.RUNS -> colors.surfaceVariant to colors.onSurface
    }

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .clip(RoundedCornerShape(if (size <= 22.dp) 4.dp else 6.dp))
            .background(bg)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // Whole, never `take(2)`: `Wd3` is a wide worth three, and truncating it to `Wd` states
            // a different delivery.
            text = ball.trim(),
            style = BouncerThemeV2.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
        )
    }
}

