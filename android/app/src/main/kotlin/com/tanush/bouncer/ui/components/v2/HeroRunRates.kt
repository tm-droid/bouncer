package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Locale

/** CRR, and RRR when [showRequired] (limited overs). Server figures are the fallback for the scraped strings. */
@Composable
internal fun HeroRunRates(match: CricketMatch, situation: LiveSituation?, showRequired: Boolean) {
    val crr = match.score?.currentRunRate ?: situation?.currentRunRate?.let(::rateText)
    val required = match.score?.requiredRunRate ?: situation?.requiredRunRate?.let(::rateText)
    val rrr = required.takeIf { showRequired }
    crr?.let { RunRate(stringResource(R.string.stat_crr), it) }
    rrr?.let { RunRate(stringResource(R.string.stat_rrr), it) }
}

private fun rateText(rate: Double): String = String.format(Locale.ROOT, "%.2f", rate)

@Composable
private fun RunRate(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = BouncerThemeV2.typography.labelMedium, color = BouncerThemeV2.colors.textSecondary)
        Text(
            text = value,
            style = BouncerThemeV2.typography.numeric,
            fontWeight = FontWeight.SemiBold,
            color = BouncerThemeV2.colors.onSurface,
        )
    }
}
