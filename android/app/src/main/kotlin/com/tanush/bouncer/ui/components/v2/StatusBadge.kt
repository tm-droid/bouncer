package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

enum class MatchStatusType { Live, Upcoming, Completed, Unknown }

/** Robustly determine match status from all available signals. */
fun determineMatchStatus(match: CricketMatch): MatchStatusType {
    if (match.isLive) return MatchStatusType.Live
    val statusLower = match.status.lowercase()
    val currentLower = match.currentStatus.lowercase()
    val infoLower = match.info.lowercase().trim()

    // Check Cricbuzz state field for in-progress indicators (multi-day matches at breaks)
    if (infoLower in listOf("in progress", "stump", "stumps", "tea", "lunch",
            "dinner", "innings break", "toss", "rain delay", "bad light")) {
        return MatchStatusType.Live
    }

    // Completed indicators
    if (currentLower.contains("won") || currentLower.contains("draw") ||
        currentLower.contains("no result") || currentLower.contains("tied") ||
        statusLower.contains("result") || statusLower.contains("complete") ||
        infoLower == "complete"
    ) return MatchStatusType.Completed
    // Upcoming indicators
    if (statusLower.contains("upcoming") || statusLower.contains("scheduled") ||
        statusLower.contains("preview") || infoLower == "preview"
    ) return MatchStatusType.Upcoming
    // If the match has scores, it's not upcoming
    val hasScores = match.teams.any { it.score.isNotBlank() }
    if (hasScores) return MatchStatusType.Completed
    return MatchStatusType.Upcoming
}

/**
 * Match state as a dot and a word.
 *
 * No tinted pill and no pulse. A capsule behind every state gave each row of a list its own coloured
 * lozenge, and a looping pulse draws the eye on a screen where nothing has changed — both read as
 * generated rather than designed. Red on a static dot is enough to say "live"; the other states are
 * grey because they are information, not alerts.
 */
@Composable
fun StatusBadgeV2(
    statusType: MatchStatusType,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = BouncerThemeV2.colors
    val (tint, text) = when (statusType) {
        MatchStatusType.Live -> colors.live to (label ?: "LIVE")
        MatchStatusType.Upcoming -> colors.textSecondary to (label ?: "UPCOMING")
        MatchStatusType.Completed -> colors.textSecondary to (label ?: "RESULT")
        MatchStatusType.Unknown -> colors.textSecondary to (label ?: "")
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Text(
            text = text,
            style = BouncerThemeV2.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}
