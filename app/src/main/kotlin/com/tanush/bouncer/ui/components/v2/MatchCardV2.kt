package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.rememberMatchTeamPalette
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesManager
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
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticEnabled = remember { PreferencesManager.getInstance(context).hapticFeedbackEnabled }

    var showContextMenu by remember { mutableStateOf(false) }

    val statusType = remember(match) { determineMatchStatus(match) }
    val winProb = remember(match) { match.calculateHeuristicProbability() }
    val teamPalette = rememberMatchTeamPalette(
        match.teams.getOrNull(0)?.name,
        match.teams.getOrNull(1)?.name,
    )

    BouncerCard(
        modifier = modifier.fillMaxWidth(),
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
            // Series & format header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${match.matchTitle} • ${match.matchType}",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                StatusBadgeV2(statusType = statusType)
                if (onSubscribeToggle != null) {
                    IconButton(
                        onClick = onSubscribeToggle,
                        modifier = Modifier.size(32.dp).padding(start = 4.dp),
                    ) {
                        Icon(
                            imageVector = if (isSubscribed) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                            contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
                            tint = if (isSubscribed) colors.primary else colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.md))

            // Team rows
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

            // Status line
            if (match.currentStatus.isNotBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = spacing.sm),
                    color = colors.divider,
                )
                Text(
                    text = match.currentStatus,
                    style = typography.bodySmall,
                    color = if (match.isLive) {
                    if (match.teams.getOrNull(0)?.isBatting == true) teamPalette.home.primary else teamPalette.away.primary
                } else {
                    colors.textSecondary
                },
                    maxLines = 2,
                )
            }

            // Win probability mini bar
            if (winProb != 50.0 && match.teams.size >= 2) {
                Spacer(Modifier.height(spacing.sm))
                val fraction = (winProb / 100.0).toFloat().coerceIn(0.05f, 0.95f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${match.teams[0].acronym} ${winProb.toInt()}%",
                        style = typography.bodySmall,
                        color = teamPalette.home.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(100 - winProb.toInt())}% ${match.teams[1].acronym}",
                        style = typography.bodySmall,
                        color = teamPalette.away.primary,
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
                                fraction to teamPalette.home.primary,
                                fraction to teamPalette.away.primary.copy(alpha = 0.7f),
                            )
                        )
                )
            }
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
