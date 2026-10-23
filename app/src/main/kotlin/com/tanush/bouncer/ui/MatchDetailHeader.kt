package com.tanush.bouncer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchStatus
import com.tanush.bouncer.ui.components.v2.HeroLine
import com.tanush.bouncer.ui.components.v2.HeroSize
import com.tanush.bouncer.ui.components.v2.MatchHeroV2
import com.tanush.bouncer.ui.components.v2.MatchStatusType
import com.tanush.bouncer.ui.components.v2.RecentBallsStripV2
import com.tanush.bouncer.ui.components.v2.StatusBadgeV2
import com.tanush.bouncer.ui.components.v2.WinProbabilityBarV2
import com.tanush.bouncer.ui.components.v2.chaseLineFor
import com.tanush.bouncer.ui.components.v2.collapsedSummary
import com.tanush.bouncer.ui.components.v2.isTestMatch
import com.tanush.bouncer.ui.components.v2.testHeroLine
import com.tanush.bouncer.ui.navigation.LocalOpenRoute
import com.tanush.bouncer.ui.screens.matchdetail.v2.HeaderCollapseState
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.CricketEventColors
import com.tanush.bouncer.utils.PreferencesHelper
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val EVENT_FLASH_MILLIS = 1500L
private const val EVENT_FLASH_ALPHA = 0.15f

/** Past halfway the bar is what the reader sees, so it takes taps; before that the hero keeps them. */
private const val BAR_TOUCH_FRACTION = 0.5f
private val COLLAPSED_BAR_HEIGHT = 56.dp

/**
 * The fixed score header above the tabs: the hero, the result or status, the win probability, the
 * over, and a slim bar it collapses into as a tab scrolls. The visible height shrinks by
 * [collapse]'s offset and clips, so the tabs under it stay pinned rather than being overlapped.
 */
@Composable
internal fun MatchDetailHeader(
    match: CricketMatch,
    situation: LiveSituation?,
    lastUpdatedMs: Long?,
    recentBalls: List<String>,
    collapse: HeaderCollapseState,
) {
    val colors = BouncerThemeV2.colors
    val barPx = with(LocalDensity.current) { COLLAPSED_BAR_HEIGHT.toPx() }
    val flashOverlay = rememberEventFlashOverlay(recentBalls)
    val showBar by remember(collapse) { derivedStateOf { collapse.fraction > 0f } }
    // Only an expanded header owns the crests: a collapsed one clips and fades the hero.
    val expanded by remember(collapse) { derivedStateOf { collapse.fraction == 0f } }
    val barTouchable by remember(collapse) { derivedStateOf { collapse.fraction > BAR_TOUCH_FRACTION } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .background(flashOverlay)
            .clipToBounds()
            .collapsedBy(collapse),
    ) {
        ExpandedHeader(
            match = match,
            situation = situation,
            lastUpdatedMs = lastUpdatedMs,
            recentBalls = recentBalls,
            shareCrests = expanded,
            modifier = Modifier
                .then(if (barTouchable) Modifier.clearAndSetSemantics {} else Modifier)
                .graphicsLayer { alpha = 1f - collapse.fraction }
                .onSizeChanged { collapse.setRange(it.height - barPx) },
        )
        if (showBar) {
            CollapsedScoreBar(
                match = match,
                touchable = barTouchable,
                onExpand = collapse::expand,
                modifier = Modifier
                    .then(if (barTouchable) Modifier else Modifier.clearAndSetSemantics {})
                    .graphicsLayer { alpha = collapse.fraction },
            )
        }
    }
    HorizontalDivider(color = colors.divider)
}

/** Measures the content at its full height but reports only what the collapse leaves visible. */
private fun Modifier.collapsedBy(collapse: HeaderCollapseState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val visible = (placeable.height + collapse.offsetPx).roundToInt().coerceIn(0, placeable.height)
    layout(placeable.width, visible) { placeable.place(0, 0) }
}

@Composable
private fun ExpandedHeader(
    match: CricketMatch,
    situation: LiveSituation?,
    lastUpdatedMs: Long?,
    recentBalls: List<String>,
    shareCrests: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = BouncerThemeV2.spacing
    val openRoute = LocalOpenRoute.current
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md)) {
        MatchContextLine(
            match = match,
            onSeriesClick = openRoute?.let { open -> { id: String -> open(seriesRoute(id)) } },
        )
        MatchHeroV2(
            match = match,
            situation = situation,
            lastUpdatedMs = lastUpdatedMs,
            size = HeroSize.Large,
            shareCrests = shareCrests,
            onTeamClick = openRoute?.let { open -> { team: CricketMatch.Team -> teamRoute(team.name)?.let(open) } },
        )
        HeaderStatus(match, situation)
        if (match.winProbability != null && !match.isCompleted) {
            Spacer(Modifier.height(spacing.md))
            WinProbabilityBarV2(match)
        }
        if (recentBalls.isNotEmpty()) {
            Spacer(Modifier.height(spacing.md))
            RecentBallsStripV2(balls = recentBalls)
        }
    }
}

/**
 * The result when decided; otherwise Cricbuzz's status line, but only where the hero is not already
 * drawing the requirement. A live hero states "X need N off M" itself, so the sentence appears once.
 */
@Composable
private fun HeaderStatus(match: CricketMatch, situation: LiveSituation?) {
    if (match.currentStatus.isBlank()) return
    val spacing = BouncerThemeV2.spacing
    if (match.matchStatus == MatchStatus.Completed) {
        Spacer(Modifier.height(spacing.md))
        MatchResultBanner(match = match)
        return
    }
    // A Test keeps its day and session status whatever the hero draws.
    if (match.isLive && !isTestMatch(match, situation) && heroLineFor(match, situation) != null) return
    Spacer(Modifier.height(spacing.sm))
    Text(
        text = match.currentStatus,
        style = BouncerThemeV2.typography.bodyMedium,
        color = BouncerThemeV2.colors.onSurface,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Mirrors the line [MatchHeroV2] draws for a live match, so [HeaderStatus] knows whether it did. */
private fun heroLineFor(match: CricketMatch, situation: LiveSituation?): HeroLine? =
    if (isTestMatch(match, situation)) testHeroLine(match) else chaseLineFor(match, situation)

/** "MI 186/4 · CSK 142/3 ● LIVE". Tapping brings the hero back. */
@Composable
private fun CollapsedScoreBar(
    match: CricketMatch,
    touchable: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    val summary = remember(match) { collapsedSummary(match) }
    val expandLabel = stringResource(R.string.hero_expand_score)
    // The bar shows codes; TalkBack spells a code letter by letter, so it reads the full names.
    val spoken = summary.teams.mapIndexed { i, t ->
        listOfNotNull(match.teams.getOrNull(i)?.name?.ifBlank { null } ?: t.label, t.score?.replace("/", " for "))
            .joinToString(" ")
    }.joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(COLLAPSED_BAR_HEIGHT)
            .background(colors.surface)
            .clickable(enabled = touchable, onClickLabel = expandLabel, role = Role.Button, onClick = onExpand)
            .padding(horizontal = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = summary.teams.joinToString(" · ") { listOfNotNull(it.label, it.score).joinToString(" ") },
            style = BouncerThemeV2.typography.numeric,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        )
        if (summary.isLive) StatusBadgeV2(MatchStatusType.Live)
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = expandLabel,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Peak moment: a wicket / four / six briefly tints the score header in that
 * event's semantic color and fires one haptic — the update should be felt,
 * not just read. Flash is skipped entirely under reduced motion; haptics
 * follow the user's existing haptic setting.
 */
@Composable
private fun rememberEventFlashOverlay(recentBalls: List<String>): Color {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }
    val cricket = BouncerThemeV2.colors.cricket
    var flashTint by remember { mutableStateOf(Color.Unspecified) }
    var prevBalls by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(recentBalls) {
        val prev = prevBalls
        prevBalls = recentBalls
        if (!isNewBall(prev, recentBalls)) return@LaunchedEffect
        val ball = recentBalls.last().trim().uppercase()
        val eventColor = eventFlashColor(ball, cricket) ?: return@LaunchedEffect
        if (hapticEnabled) {
            haptic.performHapticFeedback(
                if (ball.contains("W")) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }
        if (!BouncerMotion.reducedMotionEnabled) {
            flashTint = eventColor
            delay(EVENT_FLASH_MILLIS)
            flashTint = Color.Unspecified
        }
    }
    val flashOverlay by animateColorAsState(
        targetValue = if (flashTint == Color.Unspecified) Color.Transparent else flashTint.copy(alpha = EVENT_FLASH_ALPHA),
        animationSpec = tween(300),
        label = "eventFlash",
    )
    return flashOverlay
}

/** prev == null is the first composition — never flash on screen open. */
private fun isNewBall(prev: List<String>?, current: List<String>): Boolean =
    prev != null && current.isNotEmpty() && current != prev

private fun eventFlashColor(ball: String, cricket: CricketEventColors): Color? = when {
    ball.contains("W") && !ball.startsWith("WD") -> cricket.wicket
    ball == "6" -> cricket.six
    ball == "4" -> cricket.four
    else -> null
}
