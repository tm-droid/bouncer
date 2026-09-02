package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Locale

/**
 * The forecast tab: the server's win probability, the swing it implies, and the two run rates.
 *
 * ## Why this file is so much smaller than it was
 *
 * It previously rendered an "ultra-fidelity AI telemetry dashboard": a 240dp Canvas of rotating
 * dashed rings behind an 80sp number labelled "BOUNCER AI IMPACT SCORE", an "ELITE STATUS" pill,
 * a six-bar "ANALYTIC CONTRIBUTION" chart, per-metric sparklines, and a "BOUNCER PREDICTIVE
 * ENGINE" card reporting a detected strategic shift at a stated confidence.
 *
 * Almost none of it was real:
 *
 * - The bar chart's six values were the literal `listOf(0.45f, 0.30f, 0.90f, 0.65f, 0.25f, 0.75f)`,
 *   with over numbers counted off an index. It never read the match.
 * - Each run-rate card's sparkline was a second hardcoded list, drawn as if it were that rate's
 *   recent trend.
 * - "Strategic shift detected: Anticipating high-frequency short-pitch deliveries in the next 12
 *   balls" was a constant string. No model in this app produces ball-type predictions — the only
 *   server-side model is win probability (`bouncer-win-probability`).
 * - "CONFIDENCE: n%" and "IMPACT SCORE n" were both just the win probability relabelled.
 *   Confidence in a forecast and the forecast itself are different quantities.
 *
 * Presenting invented numbers as analysis is the failure the rest of this pass is cosmetic beside,
 * so the fabricated panels are gone rather than restyled. What is left is what the app actually
 * knows. If a real momentum series or a real confidence interval ever ships, it can be added back
 * — sourced.
 */
@Composable
fun PremiumAiStatisticsTab(match: CricketMatch) {
    val spacing = BouncerThemeV2.spacing
    val scrollState = rememberScrollState()
    // This tab narrates the BATTING side ("$battingTeam holding strong advantage"), so it reads
    // the batting-oriented value. The stored probability is team-1-oriented and would be inverted
    // here whenever teams[1] is at the crease.
    val winProb = match.battingWinProbability?.toFloat()?.coerceIn(PROB_FLOOR, PROB_CEILING)
    val crr = match.score?.currentRunRate
    val rrr = match.score?.requiredRunRate
    val battingTeam = match.teams.firstOrNull { it.isBatting }?.acronym
        ?: match.teams.firstOrNull()?.acronym ?: "BAT"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (winProb == null) {
            NoForecastYet()
        } else {
            WinProbabilityPanel(winProb = winProb, battingTeam = battingTeam)
        }
        if (crr != null || rrr != null) {
            RunRateRow(crr = crr, rrr = rrr)
        }
        Spacer(Modifier.height(spacing.xxxl))
    }
}

@Composable
private fun WinProbabilityPanel(winProb: Float, battingTeam: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    // Distance from an even match. This is a derived restatement of the number above it, not a
    // second measurement, so it is labelled as such rather than as a "delta vs baseline".
    val swing = (winProb - EVEN_MATCH).toInt()

    BouncerCard {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.common_win_probability),
                style = typography.overline,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.sm))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.1f", winProb),
                    style = typography.scoreLarge.copy(fontSize = 44.sp, lineHeight = 48.sp),
                    color = colors.onSurface,
                )
                Text(
                    text = "%",
                    style = typography.titleMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = battingTeam,
                    style = typography.titleMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = when {
                    swing > 0 -> "$battingTeam ${swing}% ahead of an even match"
                    swing < 0 -> "$battingTeam ${-swing}% behind an even match"
                    else -> "Level with an even match"
                },
                style = typography.numericSmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.md))
            Text(
                text = when {
                    winProb > STRONG_ADVANTAGE -> "$battingTeam hold a strong advantage."
                    winProb < STRONG_DISADVANTAGE -> "The bowling side are on top."
                    else -> "The match is finely balanced."
                },
                style = typography.bodyMedium,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun RunRateRow(crr: String?, rrr: String?) {
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        RunRateCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.premium_current_run_rate),
            value = crr,
        )
        RunRateCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.premium_required_run_rate),
            value = rrr,
        )
    }
}

@Composable
private fun RunRateCard(modifier: Modifier, label: String, value: String?) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard(modifier = modifier) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(
                text = label,
                style = typography.overline,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.sm))
            Text(
                // An em dash where a chase has not started yet, rather than a fabricated figure.
                text = value ?: "—",
                style = typography.scoreLarge,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun NoForecastYet() {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.premium_no_forecast_for_this_match_yet),
                style = typography.titleMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = stringResource(R.string.premium_a_win_probability_appears_once_the) +
                    "score it.",
                style = typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The model is clamped away from 0/100 — a forecast is never a certainty. */
private const val PROB_FLOOR = 5f
private const val PROB_CEILING = 95f
private const val EVEN_MATCH = 50f
private const val STRONG_ADVANTAGE = 65f
private const val STRONG_DISADVANTAGE = 35f
