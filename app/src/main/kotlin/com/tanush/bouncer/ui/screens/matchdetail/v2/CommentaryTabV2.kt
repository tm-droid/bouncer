package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * CommentaryTabV2 — Ball-by-ball commentary with colored event badges.
 */
@Composable
fun CommentaryTabV2(
    commentary: List<String>,
    recentBalls: List<String>,
    matchStatus: String = "",
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    if (commentary.isEmpty() || (commentary.size == 1 && commentary.first().contains("Loading"))) {
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
        // Clean and deduplicate commentary lines (group by ball number, keep longest)
        val cleaned = deduplicateByBall(
            commentary
                .map { it.replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim() }
                .filter { it.isNotBlank() }
        )
        if (selectedFilter == "All") {
            cleaned
        } else {
            cleaned.filter { line ->
                val upper = line.uppercase()
                when (selectedFilter) {
                    "Wickets" -> {
                        val wUpper = upper.replace("MID-WICKET", "").replace("MIDWICKET", "")
                            .replace("OVER THE WICKET", "").replace("ROUND THE WICKET", "")
                            .replace("AROUND THE WICKET", "")
                        Regex("\\bWICKET\\b").containsMatchIn(wUpper) || upper.contains("OUT!") ||
                            upper.contains(", OUT,") ||
                            Regex("\\bCATCH\\b").containsMatchIn(wUpper) ||
                            Regex("\\bBOWLED\\b").containsMatchIn(upper) ||
                            Regex("\\bLBW\\b").containsMatchIn(upper) ||
                            Regex("\\bRUN OUT\\b").containsMatchIn(upper) ||
                            Regex("\\bSTUMPED\\b").containsMatchIn(upper)
                    }
                    "Boundaries" -> Regex("\\bFOUR\\b").containsMatchIn(upper) ||
                        Regex("\\bSIX\\b").containsMatchIn(upper)
                    "Milestones" ->
                        Regex("\\b(50|hundred|century|100|150|200|double century|triple century|300)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) ||
                        Regex("\\b(half.?century|fifty)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) ||
                        Regex("\\b(5.?wicket|five.?wicket|fifer|hat.?trick)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)
                    else -> true
                }
            }
        }
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
            // Recent balls overview strip
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

            // Commentary lines — use index in key to avoid duplicate key crashes
            items(filteredCommentary.size, key = { idx -> "comm_${selectedFilter}_$idx" }) { idx ->
                CommentaryLine(filteredCommentary[idx], modifier = Modifier.animateItem())
            }

            item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
        }
    }
}

@Composable
private fun CommentaryLine(line: String, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val cleanLine = line.replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim()

    // Extract over number if present (e.g., "18.4" at start)
    val overMatch = Regex("^(\\d+\\.\\d+)\\s+(.*)").find(cleanLine)
    val overNumber = overMatch?.groupValues?.get(1)
    val commentaryText = overMatch?.groupValues?.get(2) ?: cleanLine

    val upper = cleanLine.uppercase()
    // Wicket detection — exclude fielding/bowling phrases containing "wicket"
    val wicketText = upper.replace("MID-WICKET", "").replace("MIDWICKET", "")
        .replace("OVER THE WICKET", "").replace("ROUND THE WICKET", "")
        .replace("AROUND THE WICKET", "")
    val isWicket = Regex("\\bWICKET\\b").containsMatchIn(wicketText) || upper.contains("OUT!") ||
        upper.contains(", OUT,") || Regex("\\bCATCH\\b").containsMatchIn(wicketText) ||
        Regex("\\bBOWLED\\b").containsMatchIn(upper) || Regex("\\bLBW\\b").containsMatchIn(upper) ||
        Regex("\\bRUN OUT\\b").containsMatchIn(upper) || Regex("\\bSTUMPED\\b").containsMatchIn(upper)
    val isSix = Regex("\\bSIX\\b").containsMatchIn(upper)
    val isBoundary = Regex("\\bFOUR\\b").containsMatchIn(upper) || isSix

    val accentColor = when {
        isWicket -> colors.live
        isSix -> colors.primary
        isBoundary -> colors.win
        else -> Color.Transparent
    }

    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { expanded = !expanded },
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // Over number badge
                if (overNumber != null) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = overNumber,
                            style = typography.labelSmall,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(spacing.sm))
                }

                // Event badge
                if (isWicket) {
                    EventPill(text = "W", color = colors.live)
                    Spacer(Modifier.width(spacing.sm))
                } else if (isBoundary) {
                    EventPill(
                        text = if (isSix) "6" else "4",
                        color = if (isSix) BouncerThemeV2.colors.primary else BouncerThemeV2.colors.win,
                    )
                    Spacer(Modifier.width(spacing.sm))
                }

                Text(
                    text = commentaryText,
                    style = typography.bodySmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(
                color = colors.divider,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }
    }
}

@Composable
private fun EventPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BouncerThemeV2.typography.labelSmall,
            color = BouncerThemeV2.colors.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BallBadge(ball: String) {
    val colors = BouncerThemeV2.colors
    val b = ball.trim().uppercase()
    val (bg, fg) = when {
        b == "W" -> colors.live to colors.onPrimary
        b == "6" -> colors.primary to colors.onPrimary
        b == "4" -> colors.win to colors.onPrimary
        b.startsWith("WD") || b.startsWith("NB") || b.startsWith("LB") || b.startsWith("B") ->
            BouncerThemeV2.colors.primaryContainer to colors.onSurface
        else -> colors.surfaceVariant to colors.onSurface
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
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

/** Deduplicate commentary by ball number — keeps the longest entry per delivery.
 *  CricBuzz progressively updates the same ball (short → full detail) so we may
 *  receive 2-3 versions of the same delivery. We also get duplicates from CricInfo fusion. */
private fun deduplicateByBall(lines: List<String>): List<String> {
    val ballPattern = Regex("^\\s*(\\d+\\.\\d+)")
    val normalized = lines.map {
        it.replace('\u00A0', ' ').replace(Regex("[\\s\\p{Zs}]+"), " ").trim()
    }.filter { it.isNotBlank() }

    // Pass 1: group by ball number, keep longest (most complete progressive update)
    val bestByBall = mutableMapOf<String, String>()
    for (line in normalized) {
        val m = ballPattern.find(line.trimStart())
        if (m != null) {
            val ball = m.groupValues[1]
            val existing = bestByBall[ball]
            if (existing == null || line.length > existing.length) {
                bestByBall[ball] = line
            }
        }
    }
    val seen = mutableSetOf<String>()
    val pass1 = normalized.mapNotNull { line ->
        val m = ballPattern.find(line.trimStart())
        if (m != null) {
            val ball = m.groupValues[1]
            if (ball !in seen) {
                seen.add(ball)
                bestByBall[ball]
            } else null
        } else {
            val key = line.lowercase()
            if (key !in seen) { seen.add(key); line } else null
        }
    }

    // Pass 2: case-insensitive prefix dedup — CricBuzz sends progressive updates
    // for the same ball that may differ in capitalization or trailing detail.
    // If one line's core text is a prefix of another (case-insensitive), keep only the longer.
    val toRemove = mutableSetOf<Int>()
    for (i in pass1.indices) {
        if (i in toRemove) continue
        val ai = pass1[i].lowercase().replace(Regex("^[\\d.\\s]+"), "").replace(Regex("^\\d+\\s+"), "")
        for (j in i + 1 until pass1.size) {
            if (j in toRemove) continue
            val bj = pass1[j].lowercase().replace(Regex("^[\\d.\\s]+"), "").replace(Regex("^\\d+\\s+"), "")
            val shorter = if (ai.length <= bj.length) ai else bj
            val longer = if (ai.length <= bj.length) bj else ai
            // Use 40% of shorter as prefix match threshold (catches progressive updates)
            val prefixLen = (shorter.length * 0.4).toInt().coerceAtLeast(15)
            if (shorter.length >= 15 && longer.startsWith(shorter.take(prefixLen))) {
                toRemove.add(if (ai.length <= bj.length) i else j)
            }
        }
    }
    return pass1.filterIndexed { idx, _ -> idx !in toRemove }
}
