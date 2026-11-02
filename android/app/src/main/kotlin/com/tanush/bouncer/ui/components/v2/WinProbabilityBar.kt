package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.rememberSegmentColors
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlin.math.roundToInt

/**
 * Win probability bar. Nothing is drawn when the server has no number, or the match is decided: a
 * finished match under `100% / 0%` reads as a live reading. A Test's draw share belongs to neither
 * side and gets its own slice, so the two labels deliberately do not add up to 100 on a match that
 * can be drawn.
 */
@Composable
fun WinProbabilityBarV2(match: CricketMatch, modifier: Modifier = Modifier) {
    val winProb = match.winProbability
    if (match.isCompleted || winProb == null || match.teams.size < 2) return
    val typography = BouncerThemeV2.typography
    val draw = ((match.winProbabilityDraw ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val home = (winProb / 100.0).toFloat().coerceIn(0f, 1f - draw)
    val away = (1f - draw - home).coerceAtLeast(0f)
    val (homeTeam, awayTeam) = match.teams
    val homeLabel = homeTeam.acronym.ifBlank { homeTeam.name }
    val awayLabel = awayTeam.acronym.ifBlank { awayTeam.name }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$homeLabel ${(home * WIN_PROB_PERCENT).roundToInt()}%",
                style = typography.numeric,
                fontWeight = FontWeight.SemiBold,
                color = rememberTeamInk(homeTeam.name),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${(away * WIN_PROB_PERCENT).roundToInt()}% $awayLabel",
                style = typography.numeric,
                fontWeight = FontWeight.SemiBold,
                color = rememberTeamInk(awayTeam.name),
            )
        }
        Spacer(Modifier.height(4.dp))
        val (homeColor, awayColor) = rememberSegmentColors(
            homeTeam.name,
            awayTeam.name,
            BouncerThemeV2.colors.surface,
            SEGMENT_MIN_CONTRAST,
        )
        SegmentTrack(
            segments = listOf(
                home to homeColor,
                draw to BouncerThemeV2.colors.textSecondary.copy(alpha = DRAW_ALPHA),
                away to awayColor,
            ),
        )
    }
}

/** Hard-edged segments sized by weight; a zero share is skipped because a weight must be positive. */
@Composable
private fun SegmentTrack(segments: List<Pair<Float, Color>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
    ) {
        segments.filter { it.first > 0f }.forEach { (share, color) ->
            Box(modifier = Modifier.weight(share).height(6.dp).background(color))
        }
    }
}

/** Probability shares are stored 0..1 and shown as whole percentages. */
private const val WIN_PROB_PERCENT = 100f

/**
 * Contrast floor for a filled probability segment against the surface it sits on. Lower than a
 * text floor on purpose: a solid block needs far less separation than a glyph.
 */
private const val SEGMENT_MIN_CONTRAST = 2.2f
private const val DRAW_ALPHA = 0.5f
