package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.components.v2.TeamCrest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import com.tanush.bouncer.data.remote.MatchContextStore
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ForecastSummary
import com.tanush.bouncer.models.MatchContext
import com.tanush.bouncer.models.recordedMeetings
import com.tanush.bouncer.models.scoringPercent
import com.tanush.bouncer.models.summary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.tanush.bouncer.models.PreMatchData
import com.tanush.bouncer.network.MatchSquads
import com.tanush.bouncer.network.MatchSquadsSource
import com.tanush.bouncer.ui.components.premium.TossDetails
import com.tanush.bouncer.ui.components.premium.TossResultCard
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R

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
    // Fetched only when this tab is opened: one Cricbuzz page, no Appwrite, cached for the process.
    val squads by produceState<MatchSquads?>(initialValue = null, match.id) {
        value = MatchSquadsSource.get(match.id)
    }
    // The ground and the forecast, from the context file match-sync keeps for this match. Also
    // fetched only when this tab is opened.
    val context by produceState<MatchContext?>(initialValue = null, match.id) {
        value = MatchContextStore.get(match.id, finished = match.isCompleted)
    }
    val forecast = context?.forecast?.summary(System.currentTimeMillis())
    val ground = context?.venue?.takeIf { it.known && it.matches >= GROUND_MIN_MATCHES }
    val meetings = context?.recordedMeetings()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Who is playing, first: crest, full name in the side's colour, and its code. The status card
        // that used to open this tab restated the fixed header's status line, in live red.
        if (match.teams.size >= 2) {
            item(key = "teams_card") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        match.teams.take(2).forEach { team -> TeamIdentityRow(team.name, team.acronym, team.logoUrl) }
                    }
                }
            }
        }

        // Toss result card — shown when structured toss data is available
        match.tossWinner?.let { tossWinner ->
            match.tossDecision?.let { tossDecision ->
                if (match.teams.size >= 2) {
                    item(key = "toss_card") {
                        TossResultCard(
                            tossDetails = TossDetails(
                                team1Name = match.teams[0].name,
                                team2Name = match.teams[1].name,
                                team1Logo = match.teams[0].logoUrl ?: "",
                                team2Logo = match.teams[1].logoUrl ?: "",
                                tossWinner = tossWinner,
                                decision = if (tossDecision == "field") "bowl" else tossDecision,
                            ),
                        )
                    }
                }
            }
        }

        item(key = "info_header") {
            SectionHeaderV2(title = stringResource(R.string.match_info_title))
        }
        item(key = "info_card") {
            BouncerCard {
                val pad = Modifier
                Spacer(Modifier.height(spacing.sm))
                StatRowV2(label = stringResource(R.string.match_info_series), value = match.matchTitle, modifier = pad)
                StatRowV2(label = stringResource(R.string.match_info_format), value = match.matchType, modifier = pad)
                StatRowV2(label = stringResource(R.string.match_info_date), value = match.date, modifier = pad)
                if (match.matchTime.isNotBlank()) {
                    StatRowV2(label = stringResource(R.string.match_info_time), value = match.matchTime, modifier = pad)
                }
                StatRowV2(label = stringResource(R.string.match_info_venue), value = match.venue, modifier = pad)

                // Structured toss fallback for display in info rows
                if (match.tossWinner == null) {
                    val tossInfo = extractTossInfo(match.info, match.currentStatus)
                    if (tossInfo.isNotBlank()) {
                        StatRowV2(label = stringResource(R.string.match_info_toss), value = tossInfo, modifier = pad)
                    }
                }

                // Umpires
                match.umpire1?.let { StatRowV2(label = stringResource(R.string.match_info_umpire_1), value = it, modifier = pad) }
                match.umpire2?.let { StatRowV2(label = stringResource(R.string.match_info_umpire_2), value = it, modifier = pad) }
                // Cricinfo's officials, where it covers the match. The on-field pair only when the
                // match itself did not carry one, so the same two names are never listed twice.
                context?.cricinfo?.let { officials ->
                    officialRows(officials, showUmpires = match.umpire1 == null && match.umpire2 == null).forEach { (label, value) ->
                        StatRowV2(label = stringResource(label), value = value, modifier = pad)
                    }
                }

                // Show match day for Tests
                val dayInfo = extractDayInfo(match.currentStatus, match.matchType)
                if (dayInfo.isNotBlank()) {
                    StatRowV2(label = stringResource(R.string.match_info_day), value = dayInfo, modifier = pad)
                }

                Spacer(Modifier.height(spacing.sm))
            }
        }

        teamSheetItems(match, squads)

        if (match.info.isNotBlank()) {
            val tossInfo = extractTossInfo(match.info, match.currentStatus)
            val displayInfo = if (tossInfo.isNotBlank()) {
                match.info.replace(tossInfo, "").trim().trimStart(',').trim()
            } else match.info
            if (displayInfo.isNotBlank()) {
                item(key = "extra_info_header") {
                    SectionHeaderV2(title = stringResource(R.string.match_info_additional))
                }
                item(key = "extra_info") {
                    BouncerCard {
                        Spacer(Modifier.height(spacing.sm))
                        StatRowV2(
                            label = stringResource(R.string.match_info_info),
                            value = displayInfo,
                            showDivider = false,
                        )
                        Spacer(Modifier.height(spacing.sm))
                    }
                }
            }
        }

        if (forecast != null) {
            item(key = "forecast_header") {
                SectionHeaderV2(title = stringResource(R.string.match_info_forecast))
            }
            item(key = "forecast_card") { ForecastCard(forecast) }
        }

        if (meetings != null) {
            item(key = "meetings_header") {
                SectionHeaderV2(title = stringResource(R.string.match_info_meetings, match.matchType))
            }
            item(key = "meetings_card") {
                val pad = Modifier
                BouncerCard {
                    Spacer(Modifier.height(spacing.sm))
                    StatRowV2(
                        label = stringResource(R.string.match_info_meetings_played),
                        value = meetings.first.toString(),
                        modifier = pad,
                    )
                    StatRowV2(
                        label = stringResource(R.string.match_info_meetings_won, meetings.second.first),
                        value = meetings.second.second.toString(),
                        modifier = pad,
                    )
                    StatRowV2(
                        label = stringResource(R.string.match_info_meetings_won, meetings.third.first),
                        value = meetings.third.second.toString(),
                        modifier = pad,
                        showDivider = false,
                    )
                    Spacer(Modifier.height(spacing.sm))
                }
            }
        }

        if (ground != null) {
            item(key = "ground_header") {
                SectionHeaderV2(title = stringResource(R.string.match_info_ground_record))
            }
            item(key = "ground_card") { GroundCard(ground) }
        }

        // The preview's own words, as written. This used to be scored into pace/bounce/spin bars and
        // a weather panel by keyword, with a default for every figure the text did not give.
        if (preMatchData != null && preMatchData.pitchDescription.isNotBlank()) {
            item(key = "pitch_header") {
                SectionHeaderV2(title = stringResource(R.string.match_info_pitch_report))
            }
            item(key = "pitch_card") {
                BouncerCard {
                    Text(
                        text = preMatchData.pitchDescription,
                        style = typography.bodyMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

/**
 * The rows Cricinfo's facts add to the match-info card, in order, as (label, value). A list the
 * page left empty adds no row.
 */
internal fun officialRows(officials: MatchContext.Officials, showUmpires: Boolean): List<Pair<Int, String>> = buildList {
    if (showUmpires && officials.umpires.isNotEmpty()) add(R.string.match_info_umpires to officials.umpires.joinToString(", "))
    if (officials.tvUmpires.isNotEmpty()) add(R.string.match_info_tv_umpire to officials.tvUmpires.joinToString(", "))
    if (officials.referees.isNotEmpty()) add(R.string.match_info_referee to officials.referees.joinToString(", "))
    officials.reviews?.takeIf { it.isNotBlank() }?.let { add(R.string.match_info_reviews to it) }
    // Names alone. The page's team for a debutant is the player's COUNTRY ("AUS" for a Victoria
    // player in a Sheffield Shield match), not the side they are playing for.
    if (officials.debutants.isNotEmpty()) add(R.string.match_info_debuts to officials.debutants.joinToString(", ") { it.name })
}

/** Below this the archive has too little of a ground to say anything about it. */
private const val GROUND_MIN_MATCHES = 3

/**
 * The forecast for the hours ahead. Labelled as a forecast, with its source and the time it was
 * fetched — it is never the weather at the ground, and a field the source left out is left off.
 */
@Composable
private fun ForecastCard(forecast: ForecastSummary) {
    val spacing = BouncerThemeV2.spacing
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val pad = Modifier
    val fetched = remember(forecast.fetchedAt) {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(forecast.fetchedAt))
    }
    BouncerCard {
        Spacer(Modifier.height(spacing.sm))
        forecast.tempC?.let {
            StatRowV2(label = stringResource(R.string.match_info_forecast_temperature), value = "$it°C", modifier = pad)
        }
        forecast.humidity?.let {
            StatRowV2(label = stringResource(R.string.match_info_forecast_humidity), value = "$it%", modifier = pad)
        }
        forecast.maxRainChance?.let {
            StatRowV2(
                label = pluralStringResource(R.plurals.match_info_forecast_rain, forecast.hours, forecast.hours),
                value = "$it%",
                modifier = pad,
            )
        }
        forecast.windKph?.let {
            StatRowV2(label = stringResource(R.string.match_info_forecast_wind), value = "$it km/h", modifier = pad)
        }
        forecast.cloud?.let {
            StatRowV2(label = stringResource(R.string.match_info_forecast_cloud), value = "$it%", modifier = pad, showDivider = false)
        }
        Text(
            text = stringResource(R.string.match_info_forecast_source, fetched),
            style = typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(spacing.md),
        )
    }
}

/** What the archive holds on this ground, in the format being played. */
@Composable
private fun GroundCard(ground: MatchContext.Venue) {
    val spacing = BouncerThemeV2.spacing
    val pad = Modifier
    val percent = ground.scoringPercent()
    BouncerCard {
        Spacer(Modifier.height(spacing.sm))
        StatRowV2(label = stringResource(R.string.match_info_ground_matches), value = ground.matches.toString(), modifier = pad)
        StatRowV2(
            label = stringResource(R.string.match_info_ground_scoring),
            value = when {
                percent > 0 -> stringResource(R.string.match_info_ground_scoring_above, percent)
                percent < 0 -> stringResource(R.string.match_info_ground_scoring_below, -percent)
                else -> stringResource(R.string.match_info_ground_scoring_level)
            },
            modifier = pad,
            showDivider = false,
        )
        Spacer(Modifier.height(spacing.sm))
    }
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

@Composable
private fun TeamIdentityRow(name: String, acronym: String, logoUrl: String?) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamCrest(teamName = name, logoUrl = logoUrl, size = 36.dp, acronym = acronym.ifBlank { null })
        Spacer(Modifier.width(BouncerThemeV2.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = rememberTeamInk(name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (acronym.isNotBlank() && !acronym.equals(name, ignoreCase = true)) {
                Text(text = acronym, style = typography.labelMedium, color = colors.textSecondary)
            }
        }
    }
}
