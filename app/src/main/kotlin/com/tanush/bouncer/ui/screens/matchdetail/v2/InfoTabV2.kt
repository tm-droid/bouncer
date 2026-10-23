package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import com.tanush.bouncer.data.history.HistoryCoverage
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.ui.components.HeadToHeadCard
import com.tanush.bouncer.ui.components.HistoricalStatsCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
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
import com.tanush.bouncer.ui.components.premium.TossDetails
import com.tanush.bouncer.ui.components.premium.TossResultCard
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.HistoryCoverageNotice
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R

/**
 * The facts around the match, in the order a reader looks for them: what it is (the preview, then
 * series, format, date and ground), the toss, who is officiating, the weather, the pitch, the two
 * sides' record against each other, and the ground's record. The team sheet moved to Squads, and
 * the teams card that opened this tab restated the score header.
 */
@Composable
fun InfoTabV2(
    match: CricketMatch,
    preMatchData: PreMatchData? = null,
    headToHead: H2HPair? = null,
) {
    val spacing = BouncerThemeV2.spacing
    // The ground, the forecast and the preview, from the context file match-sync keeps for this
    // match. Fetched only when this tab is opened.
    val context by produceState<MatchContext?>(initialValue = null, match.id) {
        value = MatchContextStore.get(match.id, finished = match.isCompleted)
    }
    val forecast = context?.forecast?.summary(System.currentTimeMillis())
    val ground = context?.venue?.takeIf { it.known && it.matches >= GROUND_MIN_MATCHES }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // A finished match's preview is yesterday's news; its summary is on Summary.
        if (!match.isCompleted) overviewMatchSurface(matchSurfaceFor(context, isCompleted = false))
        infoDetailsItems(match)
        infoTossItems(match)
        infoOfficialsItems(match, context?.cricinfo)
        if (forecast != null) {
            item(key = "forecast_header") { SectionHeaderV2(title = stringResource(R.string.match_info_forecast)) }
            item(key = "forecast_card") { ForecastCard(forecast) }
        }
        // The preview's own words, as written. This used to be scored into pace/bounce/spin bars and
        // a weather panel by keyword, with a default for every figure the text did not give.
        preMatchData?.pitchDescription?.takeIf { it.isNotBlank() }?.let { pitch ->
            item(key = "pitch_header") { SectionHeaderV2(title = stringResource(R.string.match_info_pitch_report)) }
            item(key = "pitch_card") {
                BouncerCard {
                    Text(
                        text = pitch,
                        style = BouncerThemeV2.typography.bodyMedium,
                        color = BouncerThemeV2.colors.onSurface,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }
        }
        infoHeadToHeadItems(match, headToHead, context)
        if (ground != null) {
            item(key = "ground_header") { SectionHeaderV2(title = stringResource(R.string.match_info_ground_record)) }
            item(key = "ground_card") { GroundCard(ground) }
        }
        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

private fun LazyListScope.infoDetailsItems(match: CricketMatch) {
    val rows = infoDetailRows(match)
    item(key = "info_header") { SectionHeaderV2(title = stringResource(R.string.match_info_title)) }
    item(key = "info_card") {
        BouncerCard {
            Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
            rows.forEachIndexed { i, (label, value) ->
                StatRowV2(label = stringResource(label), value = value, showDivider = i < rows.lastIndex)
            }
            Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
        }
    }
}

/**
 * The toss as a card when it is structured. There is no "additional info" row after it any more:
 * the listing's `info` is Cricbuzz's state word ("Preview", "Complete"), the status restated.
 */
private fun LazyListScope.infoTossItems(match: CricketMatch) {
    val tossWinner = match.tossWinner
    val tossDecision = match.tossDecision
    if (tossWinner != null && tossDecision != null && match.teams.size >= 2) {
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

/**
 * The umpires from the match listing, then Cricinfo's officials where it covers the match. The
 * on-field pair comes from Cricinfo only when the listing carried none, so no name is listed twice.
 */
private fun LazyListScope.infoOfficialsItems(match: CricketMatch, officials: MatchContext.Officials?) {
    val rows = buildList {
        match.umpire1?.let { add(R.string.match_info_umpire_1 to it) }
        match.umpire2?.let { add(R.string.match_info_umpire_2 to it) }
        officials?.let { addAll(officialRows(it, showUmpires = match.umpire1 == null && match.umpire2 == null)) }
    }
    if (rows.isEmpty()) return
    item(key = "officials_header") { SectionHeaderV2(title = stringResource(R.string.match_info_officials)) }
    item(key = "officials_card") {
        BouncerCard {
            Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
            rows.forEachIndexed { i, (label, value) ->
                StatRowV2(label = stringResource(label), value = value, showDivider = i < rows.lastIndex)
            }
            Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
        }
    }
}

/**
 * The two sides against each other, in one section. Cricsheet's pair file gives the record in the
 * match's format (with its history card); where it has none, the record comes from the match context.
 */
private fun LazyListScope.infoHeadToHeadItems(match: CricketMatch, headToHead: H2HPair?, context: MatchContext?) {
    if (headToHead != null) {
        item(key = "h2h_header") { SectionHeaderV2(title = stringResource(R.string.match_info_head_to_head)) }
        if (HistoryCoverage.missingAfghanistan(match.teams.map { it.name }, "") != null) {
            item(key = "h2h_coverage") { HistoryCoverageNotice(stringResource(R.string.history_missing_afghanistan)) }
        }
        item(key = "h2h_card") {
            val (team1Color, team2Color) = rememberMatchChartColors(match)
            HeadToHeadCard(match = match, stats = headToHead.stats, team1Color = team1Color, team2Color = team2Color)
        }
        item(key = "historical_card") { HistoricalStatsCard(data = headToHead.history) }
        return
    }
    val meetings = context?.recordedMeetings() ?: return
    item(key = "meetings_header") { SectionHeaderV2(title = stringResource(R.string.match_info_meetings, match.matchType)) }
    item(key = "meetings_card") {
        BouncerCard {
            Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
            StatRowV2(label = stringResource(R.string.match_info_meetings_played), value = meetings.first.toString())
            StatRowV2(
                label = stringResource(R.string.match_info_meetings_won, meetings.second.first),
                value = meetings.second.second.toString(),
            )
            StatRowV2(
                label = stringResource(R.string.match_info_meetings_won, meetings.third.first),
                value = meetings.third.second.toString(),
                showDivider = false,
            )
            Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
        }
    }
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
