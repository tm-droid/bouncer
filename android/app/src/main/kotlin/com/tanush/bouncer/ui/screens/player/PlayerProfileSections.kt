package com.tanush.bouncer.ui.screens.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.ui.components.v2.PersonAvatar
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.time.LocalDate

// The player profile's sections. Rows and large figures on the page, not elevated cards: the
// header was a centred card with a grey person glyph, the facts were "DOB: November 05, 1988"
// chips, and every stat was the same titleMedium figure in a 4-wide grid with a 10sp caption.

/**
 * Photo or initials, the name, the side's flag and "India · Batter", and the captain and keeper
 * marks as quiet outlined tags — they were gold-filled, and gold is for what can be tapped.
 */
@Composable
internal fun PlayerHeader(
    name: String,
    country: String,
    role: String,
    imageUrl: String?,
    isCaptain: Boolean,
    isKeeper: Boolean,
    id: String? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(name = name, imageUrl = imageUrl, size = 72.dp, id = id)
        Spacer(Modifier.width(spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
            CountryAndRole(country, role)
            RoleTags(isCaptain, isKeeper)
        }
    }
}

@Composable
private fun CountryAndRole(country: String, role: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (country.isNotBlank()) {
            TeamCrest(teamName = country, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            listOf(country, role).filter { it.isNotBlank() }.joinToString(" · "),
            style = BouncerThemeV2.typography.bodyMedium,
            color = BouncerThemeV2.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoleTags(isCaptain: Boolean, isKeeper: Boolean) {
    if (!isCaptain && !isKeeper) return
    val spacing = BouncerThemeV2.spacing
    Spacer(Modifier.height(spacing.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (isCaptain) OutlinedTag(stringResource(R.string.player_captain))
        if (isKeeper) OutlinedTag(stringResource(R.string.player_wicket_keeper))
    }
}

@Composable
private fun OutlinedTag(text: String) {
    val colors = BouncerThemeV2.colors
    Text(
        text = text,
        style = BouncerThemeV2.typography.labelMedium,
        color = colors.textSecondary,
        modifier = Modifier
            .border(1.dp, colors.border, BouncerThemeV2.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Born, styles and teams as label/value rows. */
internal fun LazyListScope.playerFactItems(profile: PlayerProfile) {
    val born = bornLine(profile.dateOfBirth, LocalDate.now())
    if (born.isEmpty() && profile.battingStyle.isBlank() && profile.bowlingStyle.isBlank() && profile.teams.isEmpty()) return
    item(key = "facts_header") { SectionHeaderV2(title = stringResource(R.string.player_profile)) }
    item(key = "facts") {
        Column {
            if (born.isNotEmpty()) StatRowV2(label = stringResource(R.string.player_born), value = born)
            if (profile.battingStyle.isNotBlank()) StatRowV2(label = stringResource(R.string.player_batting), value = profile.battingStyle)
            if (profile.bowlingStyle.isNotBlank()) StatRowV2(label = stringResource(R.string.player_bowling), value = profile.bowlingStyle)
            if (profile.teams.isNotEmpty()) {
                StatRowV2(label = stringResource(R.string.player_teams), value = profile.teams.joinToString(", "), showDivider = false)
            }
        }
    }
}

/**
 * One format's figures: three that matter most, large, then the rest as rows. A specialist bowler
 * who has never batted gets no batting block, and a batter who has never bowled no bowling one.
 * A source without boundary counts or five-wicket hauls (an older Cricsheet record) passes `hasBoundaries = false`,
 * which leaves those rows out rather than showing zeros.
 */
internal fun LazyListScope.formatStatItems(stats: FormatStats?, format: String, hasBoundaries: Boolean = true) {
    if (stats == null) {
        item(key = "no_stats") {
            Text(
                stringResource(R.string.player_no_format_stats, format),
                style = BouncerThemeV2.typography.bodyMedium,
                color = BouncerThemeV2.colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = BouncerThemeV2.spacing.xl),
            )
        }
        return
    }
    if (stats.innings > 0 || stats.runs > 0) {
        item(key = "batting_header") { SectionHeaderV2(title = stringResource(R.string.player_batting_in, format)) }
        item(key = "batting") { BattingFigures(stats, hasBoundaries) }
    }
    if (stats.wickets > 0 || stats.bowlingAverage > 0) {
        item(key = "bowling_header") { SectionHeaderV2(title = stringResource(R.string.player_bowling_in, format)) }
        item(key = "bowling") { BowlingFigures(stats, hasBoundaries) }
    }
}

@Composable
private fun BattingFigures(stats: FormatStats, hasBoundaries: Boolean) {
    Column {
        HeadlineFigures(
            stats.runs.toString() to stringResource(R.string.player_runs),
            oneDecimal(stats.average) to stringResource(R.string.player_average),
            oneDecimal(stats.strikeRate) to stringResource(R.string.player_strike_rate),
        )
        StatRowV2(label = stringResource(R.string.player_matches), value = stats.matches.toString())
        StatRowV2(label = stringResource(R.string.player_innings), value = stats.innings.toString())
        StatRowV2(label = stringResource(R.string.player_highest_score), value = stats.highScore)
        StatRowV2(label = stringResource(R.string.player_hundreds_fifties), value = "${stats.hundreds} / ${stats.fifties}")
        if (hasBoundaries) {
            StatRowV2(label = stringResource(R.string.player_fours_sixes), value = "${stats.fours} / ${stats.sixes}")
        }
        // Catches are fielding, not bowling: they used to show only for players who bowled.
        StatRowV2(label = stringResource(R.string.player_catches), value = stats.catches.toString(), showDivider = false)
    }
}

@Composable
private fun BowlingFigures(stats: FormatStats, hasBoundaries: Boolean) {
    Column {
        HeadlineFigures(
            stats.wickets.toString() to stringResource(R.string.player_wickets),
            oneDecimal(stats.bowlingAverage) to stringResource(R.string.player_average),
            oneDecimal(stats.economy) to stringResource(R.string.player_economy),
        )
        StatRowV2(label = stringResource(R.string.player_best_bowling), value = stats.bestBowling, showDivider = hasBoundaries)
        if (hasBoundaries) {
            StatRowV2(label = stringResource(R.string.player_five_wickets), value = stats.fiveWickets.toString(), showDivider = false)
        }
    }
}

/** Three figures in the score face with a plain caption under each. */
@Composable
private fun HeadlineFigures(vararg figures: Pair<String, String>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = BouncerThemeV2.spacing.lg, vertical = BouncerThemeV2.spacing.md)) {
        figures.forEach { (value, caption) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(value, style = typography.scoreLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(caption, style = typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}
