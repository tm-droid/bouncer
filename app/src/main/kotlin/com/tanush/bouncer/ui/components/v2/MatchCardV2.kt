package com.tanush.bouncer.ui.components.v2

import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.MatchTeamPalette
import com.tanush.bouncer.ui.theme.liftForContrast
import com.tanush.bouncer.ui.theme.rememberMatchTeamPalette
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesManager
import com.tanush.bouncer.utils.rememberHapticFeedback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Compact match card for lists — series header, two team rows, status line.
 */
@Composable
fun MatchCardV2(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSubscribed: Boolean = false,
    onSubscribeToggle: (() -> Unit)? = null,
    onSetReminder: () -> Unit = {},
    isFavouriteTeam: Boolean = false,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticEnabled = remember { PreferencesManager.getInstance(context).hapticFeedbackEnabled }

    var showContextMenu by remember { mutableStateOf(false) }

    val statusType = remember(match) { determineMatchStatus(match) }
    val winProb: Double? = match.winProbability
    val teamPalette = rememberMatchTeamPalette(
        match.teams.getOrNull(0)?.name,
        match.teams.getOrNull(1)?.name,
    )

    val cardDescription = remember(match) { matchCardDescription(match) }

    BouncerCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription },
        onClick = {
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        onLongClick = {
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            showContextMenu = true
        },
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            MatchCardHeaderRow(
                match = match,
                statusType = statusType,
                isFavouriteTeam = isFavouriteTeam,
                isSubscribed = isSubscribed,
                onSubscribeToggle = onSubscribeToggle,
            )
            Spacer(Modifier.height(spacing.md))
            MatchCardTeamRows(match = match, teamPalette = teamPalette)
            MatchCardStatusLine(match = match, teamPalette = teamPalette)
            MatchCardWinProbBar(match = match, winProb = winProb, teamPalette = teamPalette)
        }
    }

    if (showContextMenu) {
        MatchContextMenuSheet(
            match = match,
            onDismiss = { showContextMenu = false },
            onSetReminder = onSetReminder
        )
    }
}

@Composable
private fun MatchCardHeaderRow(
    match: CricketMatch,
    statusType: MatchStatusType,
    isFavouriteTeam: Boolean,
    isSubscribed: Boolean,
    onSubscribeToggle: (() -> Unit)?,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFavouriteTeam) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Following",
                tint = colors.primary,
                modifier = Modifier.size(14.dp).padding(end = 2.dp),
            )
        }
        Text(
            text = "${match.matchTitle} • ${match.matchType}",
            style = typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StatusBadgeV2(statusType = statusType)
        if (onSubscribeToggle != null) {
            SubscribeBellButton(isSubscribed = isSubscribed, onToggle = onSubscribeToggle)
        }
    }
}

@Composable
private fun SubscribeBellButton(isSubscribed: Boolean, onToggle: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val context = LocalContext.current
    val hapticEnabled = remember { PreferencesManager.getInstance(context).hapticFeedbackEnabled }
    val haptics = rememberHapticFeedback()
    val bellScale = bellBounceScale(isSubscribed)
    IconButton(
        onClick = {
            if (hapticEnabled) haptics.toggle(turningOn = !isSubscribed)
            onToggle()
        },
        // 48dp touch target overflowing the 32dp layout slot so the
        // header row height stays unchanged.
        modifier = Modifier
            .padding(start = 4.dp)
            .size(32.dp)
            .requiredSize(48.dp),
    ) {
        Icon(
            imageVector = if (isSubscribed) Icons.Default.Notifications else Icons.Default.NotificationsNone,
            contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
            tint = if (isSubscribed) colors.primary else colors.textSecondary,
            modifier = Modifier
                .size(18.dp)
                .scale(bellScale),
        )
    }
}

/** Bounces once per subscription change (skips first composition; honors reduced-motion). */
@Composable
private fun bellBounceScale(isSubscribed: Boolean): Float {
    val scale = remember { Animatable(1f) }
    var skipInitialBounce by remember { mutableStateOf(true) }
    LaunchedEffect(isSubscribed) {
        when {
            skipInitialBounce -> skipInitialBounce = false
            !BouncerMotion.reducedMotionEnabled -> {
                scale.snapTo(1.25f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
    }
    return scale.value
}

@Composable
private fun MatchCardTeamRows(match: CricketMatch, teamPalette: MatchTeamPalette) {
    val spacing = BouncerThemeV2.spacing
    if (match.teams.size >= 2) {
        val team1 = match.teams[0]
        val team2 = match.teams[1]
        TeamRowV2(
            teamName = team1.name,
            teamAcronym = team1.acronym,
            score = team1.score,
            overs = team1.overs,
            isBatting = team1.isBatting,
            logoUrl = team1.logoUrl,
            teamColor = teamPalette.home.primary,
        )
        Spacer(Modifier.height(spacing.sm))
        TeamRowV2(
            teamName = team2.name,
            teamAcronym = team2.acronym,
            score = team2.score,
            overs = team2.overs,
            isBatting = team2.isBatting,
            logoUrl = team2.logoUrl,
            teamColor = teamPalette.away.primary,
        )
    }
}

@Composable
private fun MatchCardStatusLine(match: CricketMatch, teamPalette: MatchTeamPalette) {
    if (match.currentStatus.isBlank()) return
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    HorizontalDivider(
        modifier = Modifier.padding(vertical = spacing.sm),
        color = colors.divider,
    )
    Text(
        text = match.currentStatus,
        style = typography.bodySmall,
        // Live chase line keeps the batting team's color (open loop);
        // a decided result reads in win green so resolution is scannable.
        color = when {
            match.isLive ->
                if (match.teams.getOrNull(0)?.isBatting == true) teamPalette.home.primary else teamPalette.away.primary
            match.currentStatus.contains("won", ignoreCase = true) -> colors.win
            else -> colors.textSecondary
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MatchCardWinProbBar(match: CricketMatch, winProb: Double?, teamPalette: MatchTeamPalette) {
    // Null means the server produced no probability for this match; show nothing rather than an
    // even split. The old `== 50.0` guard also hid genuinely even matches.
    if (winProb == null || match.teams.size < 2) return
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Spacer(Modifier.height(spacing.sm))
    // A Test's draw share belongs to neither side. Taking the second team's number as the
    // complement handed it to them: a match the model called 32 / 18 draw / 50 rendered here as
    // "68% CAXI". The draw gets its own slice of the bar and the two labels simply do not add to
    // 100, which is the truth.
    val draw = ((match.winProbabilityDraw ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val home = (winProb / 100.0).toFloat().coerceIn(0f, 1f - draw)
    val away = (1f - draw - home).coerceAtLeast(0f)
    // Team colours are guaranteed 3:1 against the app background, which a dark green clears while
    // still reading as an empty bar on a lifted card. Lifted against the card's own fill instead.
    val homeColor = teamPalette.home.primary.liftForContrast(colors.surface, SEGMENT_MIN_CONTRAST)
    val awayColor = teamPalette.away.primary.liftForContrast(colors.surface, SEGMENT_MIN_CONTRAST)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${match.teams[0].acronym} ${(home * WIN_PROB_PERCENT).roundToInt()}%",
            style = typography.bodySmall,
            color = homeColor,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${(away * WIN_PROB_PERCENT).roundToInt()}% ${match.teams[1].acronym}",
            style = typography.bodySmall,
            color = awayColor,
        )
    }
    Spacer(Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    home to homeColor,
                    home to colors.textSecondary.copy(alpha = 0.5f),
                    (home + draw) to colors.textSecondary.copy(alpha = 0.5f),
                    (home + draw) to awayColor,
                )
            )
    )
}

/** One-utterance TalkBack description so the card reads as a unit, not fragments. */
private fun matchCardDescription(match: CricketMatch): String = buildString {
    append(match.matchTitle)
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    if (team1 != null && team2 != null) {
        append(". ")
        append(team1.name)
        if (team1.score.isNotBlank()) append(" ${team1.score}")
        append(" versus ")
        append(team2.name)
        if (team2.score.isNotBlank()) append(" ${team2.score}")
    }
    if (match.currentStatus.isNotBlank()) {
        append(". ")
        append(match.currentStatus)
    }
}

/** Probability shares are stored 0..1 and shown as whole percentages. */
internal const val WIN_PROB_PERCENT = 100f

/**
 * Contrast floor for a filled probability segment against the card it sits on. Lower than a text
 * floor on purpose — a solid block needs far less separation than a glyph.
 */
internal const val SEGMENT_MIN_CONTRAST = 2.2f
