package com.tanush.bouncer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Match phase data
 */
data class MatchPhaseData(
    val phaseName: String,
    val description: String,
    val overs: String,
    val team1Runs: Int,
    val team1Wickets: Int,
    val team2Runs: Int? = null,
    val team2Wickets: Int? = null,
    val keyEvent: String? = null,
    val momentumShift: MomentumShift? = null,
    val isActive: Boolean = false
)

/** How a phase's runs compare with the format's par rate over the same overs. */
enum class MomentumShift {
    TEAM1_STRONG,
    TEAM2_STRONG,
    BALANCED
}

/**
 * The innings split into its phases (powerplays, middle, death), one row each.
 *
 * Rows in one container, not a timeline. This card used to draw a glowing dot per phase joined by
 * gradient connectors, a nested card per row with a trend icon, a green "LIVE" pill, and a
 * "momentum summary" that counted phases and announced "X dominated the match!". The phases are
 * one innings' runs against par, not a contest between two sides, so the comparison is stated as
 * what it is: above or below par.
 */
@Composable
fun MatchPhasesCard(
    phases: List<MatchPhaseData>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.match_phases_title),
                style = typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(spacing.sm))
            phases.forEachIndexed { index, phase ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                PhaseRow(phase)
            }
        }
    }
}

@Composable
private fun PhaseRow(phase: MatchPhaseData) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val detail = listOfNotNull(
        stringResource(R.string.match_phase_overs, phase.overs),
        when (phase.momentumShift) {
            MomentumShift.TEAM1_STRONG -> stringResource(R.string.match_phase_above_par)
            MomentumShift.TEAM2_STRONG -> stringResource(R.string.match_phase_below_par)
            else -> null
        },
        if (phase.isActive) stringResource(R.string.match_phase_in_progress) else null,
    ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = phase.phaseName,
                style = typography.titleSmall,
                color = colors.onSurface,
                fontWeight = if (phase.isActive) FontWeight.SemiBold else null,
            )
            Text(text = detail, style = typography.bodySmall, color = colors.textSecondary)
            phase.keyEvent?.let {
                Text(text = it, style = typography.bodySmall, color = colors.onSurface)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${phase.team1Runs}/${phase.team1Wickets}",
                style = typography.scoreMedium,
                color = colors.onSurface,
            )
            if (phase.team2Runs != null) {
                Text(
                    text = "${phase.team2Runs}/${phase.team2Wickets ?: 0}",
                    style = typography.numericSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
