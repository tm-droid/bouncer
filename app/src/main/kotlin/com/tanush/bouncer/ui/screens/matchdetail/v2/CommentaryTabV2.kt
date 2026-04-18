package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * CommentaryTabV2 — Ball-by-ball commentary with colored event badges.
 */
@Composable
fun CommentaryTabV2(
    commentary: List<String>,
    recentBalls: List<String>,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    if (commentary.isEmpty() || (commentary.size == 1 && commentary.first().contains("Loading"))) {
        EmptyStateV2(
            title = "No commentary",
            subtitle = "Commentary will appear once the match starts",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = spacing.md),
    ) {
        // Recent balls overview strip
        if (recentBalls.isNotEmpty()) {
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
        items(commentary.size, key = { idx -> "comm_$idx" }) { idx ->
            CommentaryLine(commentary[idx])
        }

        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

@Composable
private fun CommentaryLine(line: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    // Strip "[AI Fused]" prefix for cleaner display
    val cleanLine = line.replace(Regex("^\\[AI Fused\\]\\s*"), "").trim()

    val upper = cleanLine.uppercase()
    // Use word-boundary check to avoid matching "mid-wicket" as WICKET
    val isWicket = Regex("\\bWICKET\\b").containsMatchIn(upper) || upper.contains("OUT!") ||
        (upper.contains(", OUT,") || upper.startsWith("W ") || Regex("\\bCATCH\\b").containsMatchIn(upper) ||
        Regex("\\bBOWLED\\b").containsMatchIn(upper) || Regex("\\bLBW\\b").containsMatchIn(upper) ||
        Regex("\\bRUN OUT\\b").containsMatchIn(upper) || Regex("\\bSTUMPED\\b").containsMatchIn(upper))
    val isBoundary = Regex("\\bFOUR\\b").containsMatchIn(upper) || Regex("\\bSIX\\b").containsMatchIn(upper)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Event badge
            if (isWicket) {
                EventPill(text = "W", color = colors.live)
                Spacer(Modifier.width(spacing.sm))
            } else if (isBoundary) {
                val isSix = cleanLine.uppercase().contains("SIX")
                EventPill(
                    text = if (isSix) "6" else "4",
                    color = if (isSix) BouncerThemeV2.colors.primary else BouncerThemeV2.colors.win,
                )
                Spacer(Modifier.width(spacing.sm))
            }

            Text(
                text = cleanLine,
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
}

@Composable
private fun EventPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
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
