package com.tanush.bouncer.ui.screens.matchdetail.v2

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Immutable
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.ShimmerCommentaryList
import com.tanush.bouncer.ui.theme.v2.BouncerColorsV2
import com.tanush.bouncer.ui.theme.v2.CricketEventColors
import com.tanush.bouncer.ui.theme.v2.BouncerSpacingV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.TimelineEventExtractor
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Main composable ──────────────────────────────────────────────────────────

@Composable
fun CommentaryTabV2(
    commentary: List<CommentaryEntry>,
    recentBalls: List<String>,
    matchStatus: String = "",
    isLoading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    hasOlder: Boolean = false,
    isLoadingOlder: Boolean = false,
    onLoadOlder: (() -> Unit)? = null,
) {
    val spacing = BouncerThemeV2.spacing

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

    var selectedFilter by remember { mutableStateOf(CommentaryFilter.ALL) }
    // Labels are for display only; the filter itself travels as an enum, so a translated chip
    // cannot silently turn every filter into "All".
    val filterLabels = CommentaryFilter.entries.map { stringResource(it.labelRes) }

    // Filtering, regex ball parsing and block building over up to 500 rows re-ran on the main thread
    // for every live update. They run on Default now; the first frame is still built synchronously
    // so the tab never opens empty, and later updates keep showing the previous feed until the new
    // one is ready.
    val feed by produceState(
        initialValue = remember { buildCommentaryFeed(commentary, selectedFilter) },
        commentary,
        selectedFilter,
    ) {
        value = withContext(Dispatchers.Default) { buildCommentaryFeed(commentary, selectedFilter) }
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

        // A filter that matched nothing in the loaded feed used to render an empty LazyColumn — a
        // blank page with no message and no way forward, because "load older" is offered under All
        // only. It is the common case now that the first page is 8 overs rather than 25: plenty of
        // them hold no wicket and no milestone.
        if (feed.blocks.isEmpty() && selectedFilter != CommentaryFilter.ALL) {
            FilterEmptyState(hasOlder, isLoadingOlder, onLoadOlder)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = spacing.md),
        ) {
            commentaryFeedItems(
                feed = feed,
                recentBalls = recentBalls,
                selectedFilter = selectedFilter,
                spacing = spacing,
                // Offered only under All: a filtered view holds a handful of lines drawn from the
                // whole loaded feed, so "load older" there would fetch a page and very likely add
                // nothing visible to it.
                onLoadOlder = onLoadOlder.takeIf { hasOlder && selectedFilter == CommentaryFilter.ALL },
                isLoadingOlder = isLoadingOlder,
            )
        }
    }
}

private fun LazyListScope.commentaryFeedItems(
    feed: CommentaryFeed,
    recentBalls: List<String>,
    selectedFilter: CommentaryFilter,
    spacing: BouncerSpacingV2,
    onLoadOlder: (() -> Unit)?,
    isLoadingOlder: Boolean,
) {
    // Current over ball strip
    if (recentBalls.isNotEmpty() && selectedFilter == CommentaryFilter.ALL) {
        item(key = "recent_strip") { ThisOverStrip(recentBalls) }
    }

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

@Composable
private fun ThisOverStrip(recentBalls: List<String>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) {
        Text(
            stringResource(R.string.overview_this_over),
            style = typography.labelMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            recentBalls.takeLast(6).forEach { ball ->
                BallBadge(ball)
            }
        }
    }
    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = spacing.sm))
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
                        text = "Over $overNumber",
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
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                balls.forEach { ball ->
                    // An en dash, not a dot: the over keeps its six positions, and an unknown ball
                    // stays visibly distinct from one that was actually a dot.
                    BallBadge(ball.toBallString() ?: "\u2013", size = 22.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentaryLine(ball: ParsedBall, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }

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
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (ball.ballLabel != null) {
                    ResultPill(ball)
                    Spacer(Modifier.width(spacing.xs))
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ball.ballLabel,
                            style = typography.labelSmall,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(spacing.sm))
                }

                Text(
                    text = parseBoldTags(ball.ballText),
                    style = typography.bodySmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(
                color = colors.divider,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }

        val shareText = buildShareText(ball)
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.commentary_action_copy)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(shareText))
                    showMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.commentary_action_share)) },
                onClick = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            },
                            null,
                        )
                    )
                    showMenu = false
                },
            )
        }
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
            text = parseBoldTags(text),
            style = typography.bodySmall,
            color = colors.textSecondary,
        )
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = spacing.sm))
    }
}

// ── Small composables ────────────────────────────────────────────────────────

private val EXTRAS_GLYPH = Regex("^(?:WD|NB|LB|[BLN]\\d{1,2}|WD\\d{1,2}|B)$")

@Composable
private fun BallBadge(ball: String, size: Dp = 28.dp) {
    val colors = BouncerThemeV2.colors
    val b = ball.trim().uppercase()
    val cricket = colors.cricket
    val (bg, fg) = when {
        b == "W" -> cricket.wicket.copy(alpha = 0.2f) to cricket.wicket
        b == "6" -> cricket.six.copy(alpha = 0.2f) to cricket.six
        b == "4" -> cricket.four.copy(alpha = 0.2f) to cricket.four
        // Every extras glyph Cricbuzz's overSummary writes: Wd, Wd3, Nb, N1, B1, B4, L1. The `N`
        // and `L` forms reach this from the score header's own strip too, where they used to fall
        // through to the neutral branch and render as if they were runs off the bat.
        EXTRAS_GLYPH.matches(b) -> cricket.wide.copy(alpha = 0.15f) to cricket.wide
        // "\u2013" is the over strip's unknown-ball placeholder; it is quiet for the same reason a
        // dot is, and must not read as a scoring shot.
        b == "." || b == "\u2013" -> colors.surfaceVariant to colors.textSecondary
        else -> colors.surfaceVariant to colors.onSurface
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size <= 22.dp) 4.dp else 6.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ball.take(2),
            style = BouncerThemeV2.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
        )
    }
}

