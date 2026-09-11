package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper
import com.tanush.bouncer.utils.rememberHapticFeedback

/**
 * One match in a list: series header, two team rows, status line, and a trailing divider.
 *
 * Deliberately **not** a card. A list of same-kind things is rows on one surface separated by
 * hairlines — that is what Cricbuzz and Cricinfo do, and it is what lets four or five matches
 * share a screen.
 *
 * Only a live match carries a status mark. Every row used to wear a tinted LIVE / UPCOMING / COMPLETED
 * pill, a bell and a win-probability bar, so a list of fourteen matches was fourteen of each; the
 * status line already says a match is upcoming or decided. The bell stays on matches that can still
 * be followed, because it is the only place a Live Update subscription starts. The probability bar is
 * the hero's alone — on a row it was a stripe of unrelated hash colours for every side the colour
 * registry did not know.
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
    val spacing = BouncerThemeV2.spacing
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }

    var showContextMenu by remember { mutableStateOf(false) }

    val statusType = remember(match) { determineMatchStatus(match) }
    // A decided match has nothing left to follow.
    val subscribeToggle = onSubscribeToggle.takeIf { statusType != MatchStatusType.Completed }
    val cardDescription = remember(match) { matchCardDescription(match) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showContextMenu = true
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.rowVertical),
        ) {
            MatchCardHeaderRow(
                match = match,
                isLive = statusType == MatchStatusType.Live,
                isFavouriteTeam = isFavouriteTeam,
                isSubscribed = isSubscribed,
                onSubscribeToggle = subscribeToggle,
            )
            Spacer(Modifier.height(spacing.xs))
            MatchTeamRows(match = match, isLive = statusType == MatchStatusType.Live)
            MatchCardStatusLine(match = match)
        }
        // Full-bleed: the hairline runs edge to edge so the list reads as one surface rather
        // than as a stack of inset blocks.
        HorizontalDivider(color = colors.divider)
    }

    if (showContextMenu) {
        MatchContextMenuSheet(
            match = match,
            onDismiss = { showContextMenu = false },
            onSetReminder = onSetReminder,
            isSubscribed = isSubscribed,
            onSubscribeToggle = subscribeToggle,
        )
    }
}

@Composable
private fun MatchCardHeaderRow(
    match: CricketMatch,
    isLive: Boolean,
    isFavouriteTeam: Boolean,
    isSubscribed: Boolean,
    onSubscribeToggle: (() -> Unit)?,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    // Rows without a bell still get its height, so a decided match's header does not sit tighter
    // against its team rows than the live match above it.
    Row(
        modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFavouriteTeam) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.match_following),
                tint = colors.primaryText,
                modifier = Modifier.size(14.dp).padding(end = 2.dp),
            )
        }
        Text(
            text = matchHeaderText(match),
            style = typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isLive) {
            Spacer(Modifier.width(8.dp))
            StatusBadgeV2(statusType = MatchStatusType.Live)
        }
        if (onSubscribeToggle != null) {
            SubscribeBellButton(isSubscribed = isSubscribed, onToggle = onSubscribeToggle)
        }
    }
}

@Composable
private fun SubscribeBellButton(isSubscribed: Boolean, onToggle: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val context = LocalContext.current
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }
    val haptics = rememberHapticFeedback()
    IconButton(
        onClick = {
            if (hapticEnabled) haptics.toggle(turningOn = !isSubscribed)
            onToggle()
        },
        // 48dp touch target overflowing the layout slot so the header row height stays unchanged.
        modifier = Modifier
            .padding(start = 4.dp)
            .size(HEADER_HEIGHT)
            .requiredSize(48.dp),
    ) {
        Icon(
            imageVector = if (isSubscribed) Icons.Filled.Notifications else Icons.Outlined.Notifications,
            contentDescription = stringResource(
                if (isSubscribed) R.string.match_stop_live_updates else R.string.match_follow_live_updates
            ),
            tint = if (isSubscribed) colors.primaryText else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Both team rows. "Yet to bat" is only said once a match is under way and the other side has a score;
 * before the first ball both scores are simply empty.
 */
@Composable
internal fun MatchTeamRows(match: CricketMatch, isLive: Boolean) {
    if (match.teams.size < 2) return
    val spacing = BouncerThemeV2.spacing
    val reserveLogoSlot = match.teams.any { it.logoUrl != null }
    val yetToBat = isLive && match.teams.any { it.score.isNotBlank() }
    match.teams.take(2).forEachIndexed { index, team ->
        if (index > 0) Spacer(Modifier.height(spacing.xs))
        TeamRowV2(
            teamName = team.name,
            teamAcronym = team.acronym,
            score = team.score,
            overs = team.overs,
            isBatting = team.isBatting,
            logoUrl = team.logoUrl,
            reserveLogoSlot = reserveLogoSlot,
            yetToBat = yetToBat,
        )
    }
}

@Composable
private fun MatchCardStatusLine(match: CricketMatch) {
    if (match.currentStatus.isBlank()) return
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    Spacer(Modifier.height(spacing.xs))
    Text(
        text = matchStatusText(match.currentStatus),
        style = BouncerThemeV2.typography.bodySmall,
        // Neutral for every state. A result used to be win green, which on the Recent tab made every
        // row the same green and so said nothing; the words carry the result.
        color = colors.textSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * "2nd Test · England tour of Pakistan". The header used to be "Pakistan vs England • Test", which the
 * two team rows directly beneath it already say, while the series — the one thing they do not — was
 * dropped at parse time. Lists cached before the series was kept fall back to the old form.
 */
internal fun matchHeaderText(match: CricketMatch): String {
    if (match.seriesName.isBlank()) return "${match.matchTitle} · ${match.matchType}"
    val desc = when {
        match.matchDesc.isBlank() -> match.matchType
        match.matchType.isBlank() || match.matchDesc.contains(match.matchType, ignoreCase = true) -> match.matchDesc
        else -> "${match.matchDesc}, ${match.matchType}"
    }
    return listOf(desc, match.seriesName).filter { it.isNotBlank() }.joinToString(" · ")
}

/** One-utterance TalkBack description so the row reads as a unit, not fragments. */
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

private val HEADER_HEIGHT = 28.dp
