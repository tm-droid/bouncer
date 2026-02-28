package com.tanush.bouncer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.EmptyStateView
import com.tanush.bouncer.ui.components.glass.BallEvent
import com.tanush.bouncer.ui.components.glass.BallIndicator
import com.tanush.bouncer.ui.components.glass.BallIndicatorSize
import com.tanush.bouncer.ui.components.glass.BallsRow
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.runs
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

// ─────────────────────────────────────────────────────────────────────────────
//  Overs Timeline Tab — Glass UI
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Data class representing a single over with its ball events.
 */
private data class OverData(
    val overNumber: Int,
    val balls: List<BallEvent>,
    val bowlerName: String?
) {
    val totalRuns: Int get() = balls.sumOf { it.runs() }
    val wickets: Int get() = balls.count { it == BallEvent.WICKET }
}

/**
 * Overs Timeline tab showing ball-by-ball over commentary in the Match Detail screen.
 *
 * Supports two viewing modes:
 * - **Detailed** (expanded): Shows one over at a time with ball indicators at STANDARD size
 *   and per-ball run labels below each indicator.
 * - **Compact** (collapsed): Stacked cards with COMPACT ball circles and a run total.
 *
 * @param recentBalls Flat list of recent delivery strings (e.g. "4", "0", "W", "Wd")
 * @param currentOver Current over string, e.g. "36.1"
 * @param bowlerName Name of the current bowler
 * @param modifier Modifier for the root layout
 */
@Composable
fun OversTimelineTab(
    recentBalls: List<String>,
    currentOver: String?,
    bowlerName: String?,
    modifier: Modifier = Modifier
) {
    if (recentBalls.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.SportsCricket,
            title = "No overs data available",
            message = "Ball-by-ball data will appear once the match is underway.",
            modifier = modifier.padding(BouncerDesignSystem.dimens.xxl)
        )
        return
    }

    val overs = remember(recentBalls, currentOver, bowlerName) {
        groupBallsIntoOvers(recentBalls, currentOver, bowlerName)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = BouncerDesignSystem.dimens.screenHorizontal,
            vertical = BouncerDesignSystem.dimens.md
        ),
        verticalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.cardSpacing)
    ) {
        itemsIndexed(
            items = overs,
            key = { _, over -> over.overNumber }
        ) { index, over ->
            val isLatest = index == 0
            OverCard(
                over = over,
                initiallyExpanded = isLatest
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Over Card — expandable/collapsible
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverCard(
    over: OverData,
    initiallyExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val hasWicket = over.wickets > 0

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        useGoldBorder = hasWicket,
        borderOpacity = if (hasWicket) 0.18f else 0.08f
    ) {
        Column {
            // ── Header row (always visible, toggles expansion) ──────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Over number badge
                Text(
                    text = "Over ${over.overNumber}",
                    style = typography.labelMedium,
                    color = if (hasWicket) colors.goldPrimary else colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(dimens.sm))

                // Bowler name
                if (over.bowlerName != null) {
                    Text(
                        text = over.bowlerName,
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Runs summary
                OverRunsSummary(over)

                Spacer(modifier = Modifier.width(dimens.xs))

                // Expand / collapse icon
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse over" else "Expand over",
                    tint = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(dimens.sm))

            // ── Compact ball circles (always visible) ───────────────────
            BallsRow(
                events = over.balls,
                size = if (expanded) BallIndicatorSize.STANDARD else BallIndicatorSize.COMPACT,
                spacing = if (expanded) 6.dp else 4.dp,
                maxBalls = over.balls.size.coerceAtLeast(6)
            )

            // ── Detailed per-ball breakdown (expanded only) ─────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(250)),
                exit = shrinkVertically(animationSpec = tween(200))
            ) {
                Column(modifier = Modifier.padding(top = dimens.sm)) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = dimens.sm)
                    )

                    over.balls.forEachIndexed { idx, event ->
                        BallDetailRow(
                            ballNumber = idx + 1,
                            overNumber = over.overNumber,
                            event = event
                        )
                        if (idx < over.balls.lastIndex) {
                            Spacer(modifier = Modifier.height(dimens.xxs))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Per-ball detail row shown in expanded mode.
 */
@Composable
private fun BallDetailRow(
    ballNumber: Int,
    overNumber: Int,
    event: BallEvent
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.sm)
    ) {
        // Ball number label  e.g. "12.3"
        Text(
            text = "${overNumber - 1}.$ballNumber",
            style = typography.labelSmall,
            color = colors.textMuted,
            modifier = Modifier.width(36.dp)
        )

        BallIndicator(
            event = event,
            size = BallIndicatorSize.COMPACT
        )

        Text(
            text = ballCommentary(event),
            style = typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Runs + wickets summary pill for the over header.
 */
@Composable
private fun OverRunsSummary(over: OverData) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    GlassCardSmall(
        borderOpacity = 0.06f,
        useGoldBorder = over.wickets > 0,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${over.totalRuns} run${if (over.totalRuns != 1) "s" else ""}",
                style = typography.labelSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (over.wickets > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${over.wickets}W",
                    style = typography.labelSmall,
                    color = colors.ballWicket,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Groups a flat list of ball strings into [OverData] entries.
 *
 * Every 6 *legal* deliveries form one over. Wides and no-balls are included in
 * the over but do not count toward the 6-ball limit.
 *
 * The list is returned most-recent-first so the latest over appears at the top.
 */
private fun groupBallsIntoOvers(
    recentBalls: List<String>,
    currentOver: String?,
    bowlerName: String?
): List<OverData> {
    val events = recentBalls.map { parseBallEvent(it) }
    if (events.isEmpty()) return emptyList()

    // Determine starting over number from currentOver (e.g. "36.4" → over 37)
    val startOverNumber = currentOver?.substringBefore(".")?.toIntOrNull() ?: 1

    val overs = mutableListOf<OverData>()
    var currentBalls = mutableListOf<BallEvent>()
    var legalCount = 0

    for (event in events) {
        currentBalls.add(event)
        val isExtra = event == BallEvent.WIDE || event == BallEvent.NO_BALL
        if (!isExtra) legalCount++

        if (legalCount == 6) {
            overs.add(
                OverData(
                    overNumber = startOverNumber - overs.size,
                    balls = currentBalls.toList(),
                    bowlerName = if (overs.isEmpty()) bowlerName else null
                )
            )
            currentBalls = mutableListOf()
            legalCount = 0
        }
    }

    // Incomplete current over
    if (currentBalls.isNotEmpty()) {
        overs.add(
            0,
            OverData(
                overNumber = startOverNumber - overs.size + 1,
                balls = currentBalls.toList(),
                bowlerName = if (overs.isEmpty()) bowlerName else null
            )
        )
    }

    // Most recent first — the incomplete over is already at index 0
    return if (currentBalls.isEmpty()) overs.reversed() else {
        listOf(overs.first()) + overs.drop(1).reversed()
    }
}

/**
 * Generates a short commentary string for a ball event.
 */
private fun ballCommentary(event: BallEvent): String = when (event) {
    BallEvent.DOT -> "No run"
    BallEvent.SINGLE -> "1 run"
    BallEvent.DOUBLE -> "2 runs"
    BallEvent.THREE -> "3 runs"
    BallEvent.FOUR -> "FOUR! Boundary"
    BallEvent.SIX -> "SIX! Maximum"
    BallEvent.WICKET -> "WICKET!"
    BallEvent.WIDE -> "Wide ball, 1 extra"
    BallEvent.NO_BALL -> "No ball, 1 extra"
    BallEvent.LEG_BYE -> "Leg bye, 1 run"
    BallEvent.BYE -> "Bye, 1 run"
}

/**
 * Parses a raw ball string into a [BallEvent].
 */
private fun parseBallEvent(ball: String): BallEvent {
    val lower = ball.trim().lowercase()
    return when {
        lower == "w" || lower.startsWith("wk") || lower.contains("wicket") -> BallEvent.WICKET
        lower == "wd" || lower.contains("wide") -> BallEvent.WIDE
        lower == "nb" || lower.contains("no ball") || lower.contains("noball") -> BallEvent.NO_BALL
        lower == "lb" || lower.contains("leg bye") -> BallEvent.LEG_BYE
        lower == "b" || (lower.contains("bye") && !lower.contains("leg")) -> BallEvent.BYE
        lower == "6" || lower.contains("six") -> BallEvent.SIX
        lower == "4" || lower.contains("four") || lower.contains("boundary") -> BallEvent.FOUR
        lower == "3" -> BallEvent.THREE
        lower == "2" -> BallEvent.DOUBLE
        lower == "1" -> BallEvent.SINGLE
        lower == "0" || lower == "." || lower.contains("dot") -> BallEvent.DOT
        else -> {
            val num = lower.toIntOrNull()
            when (num) {
                0 -> BallEvent.DOT
                1 -> BallEvent.SINGLE
                2 -> BallEvent.DOUBLE
                3 -> BallEvent.THREE
                4 -> BallEvent.FOUR
                6 -> BallEvent.SIX
                else -> BallEvent.DOT
            }
        }
    }
}
