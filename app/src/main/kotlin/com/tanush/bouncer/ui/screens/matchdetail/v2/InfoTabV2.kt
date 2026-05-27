package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.PreMatchData
import com.tanush.bouncer.ui.components.premium.ConditionsCard
import com.tanush.bouncer.ui.components.premium.CurrentWeatherInfo
import com.tanush.bouncer.ui.components.premium.PitchCondition
import com.tanush.bouncer.ui.components.premium.TossDetails
import com.tanush.bouncer.ui.components.premium.TossResultCard
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * InfoTabV2 — Match information: series, venue, date, toss, status, etc.
 */
@Composable
fun InfoTabV2(
    match: CricketMatch,
    preMatchData: PreMatchData? = null,
) {
    val spacing = BouncerThemeV2.spacing
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Current status card (prominent)
        if (match.currentStatus.isNotBlank()) {
            item(key = "status_card") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = "Status",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = match.currentStatus,
                            style = typography.titleSmall,
                            color = if (match.isLive) colors.live else colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Toss result card — shown when structured toss data is available
        if (match.tossWinner != null && match.tossDecision != null && match.teams.size >= 2) {
            item(key = "toss_card") {
                TossResultCard(
                    tossDetails = TossDetails(
                        team1Name = match.teams[0].name,
                        team2Name = match.teams[1].name,
                        team1Logo = match.teams[0].logoUrl ?: "",
                        team2Logo = match.teams[1].logoUrl ?: "",
                        tossWinner = match.tossWinner!!,
                        decision = if (match.tossDecision == "field") "bowl" else match.tossDecision!!,
                    ),
                )
            }
        }

        item(key = "info_header") {
            SectionHeaderV2(title = "Match Information")
        }
        item(key = "info_card") {
            BouncerCard {
                val pad = Modifier.padding(horizontal = spacing.md)
                Spacer(Modifier.height(spacing.sm))
                StatRowV2(label = "Series", value = match.matchTitle, modifier = pad)
                StatRowV2(label = "Format", value = match.matchType, modifier = pad)
                StatRowV2(label = "Date", value = match.date, modifier = pad)
                if (match.matchTime.isNotBlank()) {
                    StatRowV2(label = "Time", value = match.matchTime, modifier = pad)
                }
                StatRowV2(label = "Venue", value = match.venue, modifier = pad)

                // Structured toss fallback for display in info rows
                if (match.tossWinner == null) {
                    val tossInfo = extractTossInfo(match.info, match.currentStatus)
                    if (tossInfo.isNotBlank()) {
                        StatRowV2(label = "Toss", value = tossInfo, modifier = pad)
                    }
                }

                // Umpires
                match.umpire1?.let { StatRowV2(label = "Umpire 1", value = it, modifier = pad) }
                match.umpire2?.let { StatRowV2(label = "Umpire 2", value = it, modifier = pad) }

                // Show match day for Tests
                val dayInfo = extractDayInfo(match.currentStatus, match.matchType)
                if (dayInfo.isNotBlank()) {
                    StatRowV2(label = "Day", value = dayInfo, modifier = pad)
                }

                Spacer(Modifier.height(spacing.sm))
            }
        }

        if (match.info.isNotBlank()) {
            val tossInfo = extractTossInfo(match.info, match.currentStatus)
            val displayInfo = if (tossInfo.isNotBlank()) {
                match.info.replace(tossInfo, "").trim().trimStart(',').trim()
            } else match.info
            if (displayInfo.isNotBlank()) {
                item(key = "extra_info_header") {
                    SectionHeaderV2(title = "Additional Info")
                }
                item(key = "extra_info") {
                    BouncerCard {
                        Spacer(Modifier.height(spacing.sm))
                        StatRowV2(
                            label = "Info",
                            value = displayInfo,
                            modifier = Modifier.padding(horizontal = spacing.md),
                            showDivider = false,
                        )
                        Spacer(Modifier.height(spacing.sm))
                    }
                }
            }
        }

        // Teams section
        if (match.teams.size >= 2) {
            item(key = "teams_header") {
                SectionHeaderV2(title = "Teams")
            }
            item(key = "teams_card") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        match.teams.forEachIndexed { idx, team ->
                            val displayName = team.name
                            val score = team.score
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = displayName,
                                    style = typography.titleSmall,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (score.isNotBlank()) {
                                    Text(
                                        text = score,
                                        style = typography.scoreMedium,
                                        color = colors.onSurface,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            if (idx < match.teams.size - 1) {
                                Spacer(Modifier.height(spacing.sm))
                            }
                        }
                    }
                }
            }
        }

        // Pitch + conditions card from ESPNCricinfo match preview
        if (preMatchData != null && preMatchData.pitchDescription.isNotBlank()) {
            item(key = "conditions_card") {
                ConditionsCard(
                    weather = buildWeatherFromText(preMatchData.pitchDescription),
                    pitch = buildPitchFromText(preMatchData.pitchDescription),
                    venue = match.venue,
                )
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

private fun buildWeatherFromText(text: String): CurrentWeatherInfo {
    val lower = text.lowercase()
    val tempMatch = Regex("""(\d{1,3})\s*°?c""", RegexOption.IGNORE_CASE).find(text)
    val temp = tempMatch?.groupValues?.get(1)?.toIntOrNull() ?: 28
    val humidityMatch = Regex("""(\d{1,3})%?\s*humid""", RegexOption.IGNORE_CASE).find(text)
    val humidity = humidityMatch?.groupValues?.get(1)?.toIntOrNull() ?: 60
    val condition = when {
        lower.contains("rain") -> "Rain"
        lower.contains("overcast") -> "Overcast"
        lower.contains("cloud") -> "Cloudy"
        lower.contains("sunny") || lower.contains("clear") -> "Sunny"
        else -> "Partly Cloudy"
    }
    return CurrentWeatherInfo(
        temperature = temp, humidity = humidity, windSpeed = 15,
        windDirection = "N", condition = condition, dewPoint = temp - 5, visibility = "10km",
    )
}

private fun buildPitchFromText(text: String): PitchCondition {
    val lower = text.lowercase()
    val type = when {
        lower.contains("green") -> "Green"
        lower.contains("dry") || lower.contains("dusty") -> "Dry"
        lower.contains("cracked") -> "Cracked"
        lower.contains("flat") -> "Flat"
        else -> "Flat"
    }
    val pace = when {
        lower.contains("fast") || lower.contains("pace") || lower.contains("quick") -> 7
        lower.contains("slow") -> 3
        else -> 5
    }
    val bounce = when {
        lower.contains("bounce") || lower.contains("lively") -> 7
        lower.contains("low bounce") -> 3
        else -> 5
    }
    val spin = when {
        lower.contains("spin") || lower.contains("turn") -> 7
        lower.contains("little spin") || lower.contains("no spin") -> 2
        else -> 4
    }
    val seam = when {
        lower.contains("seam") || lower.contains("swing") || lower.contains("movement") || lower.contains("green") -> 7
        lower.contains("flat") || lower.contains("dry") -> 2
        else -> 4
    }
    val deterioration = when {
        lower.contains("crack") || lower.contains("crumbling") -> "High"
        lower.contains("deteriorat") || lower.contains("wear") -> "Medium"
        else -> "Low"
    }
    return PitchCondition(type = type, pace = pace, bounce = bounce, spin = spin, seam = seam, deterioration = deterioration)
}

private fun extractTossInfo(info: String, status: String): String {
    // Look for "X opted to bat/bowl" patterns
    val tossRegex = Regex("(\\w[\\w\\s]+?)\\s+(opted to|elected to|won the toss and|chose to)\\s+(bat|bowl|field)", RegexOption.IGNORE_CASE)
    val infoMatch = tossRegex.find(info) ?: tossRegex.find(status)
    return infoMatch?.value ?: ""
}

private fun extractDayInfo(status: String, matchType: String): String {
    if (!matchType.lowercase().contains("test")) return ""
    val dayMatch = Regex("Day\\s+(\\d+)", RegexOption.IGNORE_CASE).find(status)
    return dayMatch?.value ?: ""
}
