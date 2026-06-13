package com.tanush.bouncer.ui.screens.matchdetail.v2

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.ShimmerCommentaryList
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.TimelineEventExtractor

// ── Internal parsed ball model ──────────────────────────────────────────────

@Immutable
private data class ParsedBall(
    val overNumber: Float,
    val ballText: String,
    val runs: Int,
    val isWicket: Boolean,
    val isFour: Boolean,
    val isSix: Boolean,
    val isWide: Boolean,
    val isNoBall: Boolean,
)

private val OVER_REGEX = Regex("^(\\d+\\.\\d+)\\s+(.*)", setOf(RegexOption.DOT_MATCHES_ALL))
private val RUNS_REGEX = Regex("\\b(\\d+) runs?\\b")

private fun String.parseBall(): ParsedBall {
    val clean = replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim()
    val m = OVER_REGEX.find(clean)
    val overNumber = m?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    val text = m?.groupValues?.get(2) ?: clean
    val lower = text.lowercase()

    val ballType = TimelineEventExtractor.detectBallType(clean)
    val isWicket = ballType == TimelineEventType.WICKET
    val isSix = ballType == TimelineEventType.SIX
    val isFour = ballType == TimelineEventType.FOUR
    val isWide = lower.contains("wide")
    val isNoBall = lower.contains("no ball") || lower.contains("no-ball")

    val runs = when {
        isSix -> 6
        isFour -> 4
        isWicket -> 0
        lower.contains("no run") -> 0
        else -> RUNS_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    return ParsedBall(overNumber, text, runs, isWicket, isFour, isSix, isWide, isNoBall)
}

private fun ParsedBall.toBallString(): String = when {
    isWicket -> "W"
    isSix -> "6"
    isFour -> "4"
    isWide -> "WD"
    isNoBall -> "NB"
    runs == 0 -> "."
    else -> runs.toString()
}

// ── Main composable ──────────────────────────────────────────────────────────

@Composable
fun CommentaryTabV2(
    commentary: List<String>,
    recentBalls: List<String>,
    matchStatus: String = "",
    isLoading: Boolean = false,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    if (isLoading) {
        ShimmerCommentaryList(modifier = Modifier.fillMaxSize())
        return
    }

    if (commentary.isEmpty()) {
        val statusLower = matchStatus.lowercase()
        val (title, subtitle) = when {
            statusLower.contains("stumps") -> "Stumps" to "Commentary will resume at start of play"
            statusLower.contains("break") -> "Session Break" to "Commentary will resume after the break"
            statusLower.contains("rain") || statusLower.contains("delay") -> "Rain Delay" to "Commentary will resume when play restarts"
            statusLower.contains("won") || statusLower.contains("drawn") -> "Match Complete" to "No more commentary available"
            else -> "No commentary yet" to "Commentary will appear once the match starts"
        }
        EmptyStateV2(title = title, subtitle = subtitle)
        return
    }

    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Wickets", "Boundaries", "Milestones")

    val filteredCommentary = remember(commentary, selectedFilter) {
        val cleaned = TimelineEventExtractor.deduplicateByBall(
            commentary
                .map { it.replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim() }
                .filter { it.isNotBlank() }
        )
        if (selectedFilter == "All") cleaned
        else cleaned.filter { line ->
            when (selectedFilter) {
                "Wickets" -> TimelineEventExtractor.detectBallType(line) == TimelineEventType.WICKET
                "Boundaries" -> TimelineEventExtractor.detectBallType(line).let {
                    it == TimelineEventType.SIX || it == TimelineEventType.FOUR
                }
                "Milestones" -> TimelineEventExtractor.detectBallType(line) == TimelineEventType.MILESTONE
                else -> true
            }
        }
    }

    val parsedBalls = remember(filteredCommentary) {
        filteredCommentary.map { it.parseBall() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterChipRowV2(
            items = filterOptions,
            selectedItem = selectedFilter,
            onItemSelected = { selectedFilter = it },
            modifier = Modifier.padding(vertical = spacing.sm),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = spacing.md),
        ) {
            // Current over ball strip
            if (recentBalls.isNotEmpty() && selectedFilter == "All") {
                item(key = "recent_strip") {
                    Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) {
                        Text("This Over", style = typography.labelMedium, color = colors.textSecondary)
                        Spacer(Modifier.height(spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            recentBalls.takeLast(6).forEach { ball ->
                                BallBadge(ball)
                            }
                        }
                    }
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = spacing.sm))
                }
            }

            if (selectedFilter == "All") {
                val (ballLines, nonBallLines) = parsedBalls.partition { it.overNumber > 0f }
                val grouped = ballLines
                    .groupBy { it.overNumber.toInt() }
                    .toSortedMap(reverseOrder())

                // Non-ball lines (session notes, weather updates) at top
                items(nonBallLines.size, key = { "nonball_$it" }) { idx ->
                    NonBallLine(nonBallLines[idx].ballText)
                }

                // Ball lines grouped by over with separator cards
                grouped.forEach { (over, balls) ->
                    item(key = "sep_$over") {
                        OverSeparatorCard(overNumber = over, balls = balls)
                    }
                    items(balls.size, key = { "ball_${over}_$it" }) { idx ->
                        CommentaryLine(balls[idx], modifier = Modifier.animateItem())
                    }
                }
            } else {
                // Filtered views: flat list without separators
                items(parsedBalls.size, key = { "comm_${selectedFilter}_$it" }) { idx ->
                    CommentaryLine(parsedBalls[idx], modifier = Modifier.animateItem())
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
        }
    }
}

// ── Over separator card ──────────────────────────────────────────────────────

@Composable
private fun OverSeparatorCard(overNumber: Int, balls: List<ParsedBall>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val totalRuns = balls.sumOf { it.runs }
    val isMaiden = totalRuns == 0 && balls.isNotEmpty() && balls.none { it.isWide || it.isNoBall }

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
                        color = colors.primary,
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
                            text = "MAIDEN",
                            style = typography.labelSmall,
                            color = colors.cricket.maiden,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                text = "$totalRuns run${if (totalRuns != 1) "s" else ""}",
                style = typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        if (balls.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                balls.forEach { ball ->
                    BallBadge(ball.toBallString(), size = 22.dp)
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
                if (ball.overNumber > 0f) {
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
                            text = "%.1f".format(ball.overNumber),
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
                text = { Text("Copy text") },
                onClick = {
                    clipboardManager.setText(AnnotatedString(shareText))
                    showMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text("Share") },
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

@Composable
private fun ResultPill(ball: ParsedBall) {
    val colors = BouncerThemeV2.colors

    val cricket = colors.cricket
    val (text, fg, bg) = when {
        ball.isWicket -> Triple("W", cricket.wicket, cricket.wicket.copy(alpha = 0.2f))
        ball.isSix -> Triple("6", cricket.six, cricket.six.copy(alpha = 0.2f))
        ball.isFour -> Triple("4", cricket.four, cricket.four.copy(alpha = 0.2f))
        ball.isWide -> Triple("WD", cricket.wide, cricket.wide.copy(alpha = 0.15f))
        ball.isNoBall -> Triple("NB", cricket.noBall, cricket.noBall.copy(alpha = 0.15f))
        ball.runs == 0 -> Triple("·", colors.onSurface.copy(alpha = 0.35f), colors.surfaceVariant)
        else -> Triple(ball.runs.toString(), colors.onSurface.copy(alpha = 0.6f), colors.surfaceVariant)
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BouncerThemeV2.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BallBadge(ball: String, size: Dp = 28.dp) {
    val colors = BouncerThemeV2.colors
    val b = ball.trim().uppercase()
    val cricket = colors.cricket
    val (bg, fg) = when {
        b == "W" -> cricket.wicket.copy(alpha = 0.2f) to cricket.wicket
        b == "6" -> cricket.six.copy(alpha = 0.2f) to cricket.six
        b == "4" -> cricket.four.copy(alpha = 0.2f) to cricket.four
        b.startsWith("WD") || b.startsWith("NB") || b.startsWith("LB") || b.startsWith("B") ->
            cricket.wide.copy(alpha = 0.15f) to cricket.wide
        b == "." -> colors.surfaceVariant to colors.onSurface.copy(alpha = 0.3f)
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

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun buildShareText(ball: ParsedBall): String =
    if (ball.overNumber > 0f) "Over ${"%.1f".format(ball.overNumber)}: ${ball.ballText}"
    else ball.ballText

private fun parseBoldTags(text: String): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("<b>(.*?)</b>")
        var lastIndex = 0
        regex.findAll(text).forEach { match ->
            append(text.substring(lastIndex, match.range.first))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
}
