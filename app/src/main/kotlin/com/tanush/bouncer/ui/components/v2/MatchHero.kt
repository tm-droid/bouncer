package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.displayOvers

enum class HeroSize(val crest: Dp) {
    /** Match-detail header: 56dp crests and the hero score. */
    Large(56.dp),

    /** Collapsed or Home use: 40dp crests and the large score. */
    Compact(40.dp),
}

/** What every column needs, bundled so the column composable stays under the parameter limit. */
private class HeroContext(
    val match: CricketMatch,
    val size: HeroSize,
    val isTest: Boolean,
    val onTeamClick: ((CricketMatch.Team) -> Unit)?,
)

/**
 * Team A | status centre | team B. Stateless: the caller owns the match, situation and timestamp.
 * Scores are neutral ink except the batting side of a live match, which takes its team ink; a
 * finished match's loser steps back to secondary text and the winner is never colour-filled.
 */
@Composable
fun MatchHeroV2(
    match: CricketMatch,
    situation: LiveSituation?,
    lastUpdatedMs: Long?,
    size: HeroSize,
    modifier: Modifier = Modifier,
    onTeamClick: ((CricketMatch.Team) -> Unit)? = null,
) {
    if (match.teams.size < 2) return
    val isTest = isTestMatch(match, situation)
    val context = HeroContext(match, size, isTest, onTeamClick)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            HeroTeamColumn(context, match.teams[0], alignEnd = false)
            HeroCentre(match, situation, lastUpdatedMs, isTest, Modifier.align(Alignment.CenterVertically))
            HeroTeamColumn(context, match.teams[1], alignEnd = true)
        }
        val line = if (!match.isLive) null else if (isTest) testHeroLine(match) else chaseLineFor(match, situation)
        if (line != null) {
            Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
            HeroLineText(line, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RowScope.HeroTeamColumn(context: HeroContext, team: CricketMatch.Team, alignEnd: Boolean) {
    val match = context.match
    val typography = BouncerThemeV2.typography
    val colors = BouncerThemeV2.colors
    val result = if (match.isCompleted) rowResultFor(match, team.name) else RowResult.Undecided
    val nameInk = if (result == RowResult.Lost) colors.textSecondary else rememberTeamInk(team.name)
    val click = context.onTeamClick
    Column(
        modifier = Modifier
            .weight(1f)
            .then(if (click != null) Modifier.clickable { click(team) } else Modifier),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        TeamCrest(
            teamName = team.name,
            logoUrl = team.logoUrl,
            size = context.size.crest,
            acronym = team.acronym.ifBlank { null },
        )
        Spacer(Modifier.height(BouncerThemeV2.spacing.xs))
        Text(
            text = team.acronym.ifBlank { team.name },
            style = typography.titleMedium,
            fontWeight = if (team.isBatting || result == RowResult.Won) FontWeight.Bold else FontWeight.SemiBold,
            color = nameInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HeroScore(context.match, context.isTest, context.size, team, result, alignEnd)
    }
}

@Composable
private fun HeroCentre(
    match: CricketMatch,
    situation: LiveSituation?,
    lastUpdatedMs: Long?,
    isTest: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val status = determineMatchStatus(match)
    val freshness = rememberFreshness(lastUpdatedMs, status == MatchStatusType.Live).value
    Column(
        modifier = modifier.widthIn(min = CENTRE_MIN_WIDTH, max = CENTRE_MAX_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BouncerThemeV2.spacing.xs),
    ) {
        if (freshness is Freshness.Stale) {
            // Grey, not the red dot: a frozen feed must not claim to be live.
            StatusBadgeV2(MatchStatusType.Unknown, label = stringResource(R.string.freshness_last_seen_live))
        } else {
            StatusBadgeV2(status)
            if (freshness is Freshness.Aging) FreshnessLabel(freshness)
        }
        Text(
            text = stringResource(R.string.hero_vs),
            style = BouncerThemeV2.typography.labelMedium,
            color = colors.textSecondary,
        )
        val startMs = match.startDateMs
        if (status == MatchStatusType.Upcoming && startMs != null) {
            MatchStartLabel(startMs, BouncerThemeV2.typography.labelMedium, colors.textSecondary)
        }
        if (status == MatchStatusType.Live) HeroRunRates(match, situation, showRequired = !isTest)
    }
}

@Composable
private fun runsText(runs: Int): String = pluralStringResource(R.plurals.hero_runs, runs, runs)

/** The requirement or lead line, formatted with the hero_* strings. Null draws nothing. */
@Composable
fun HeroLineText(line: HeroLine?, modifier: Modifier = Modifier) {
    val text = when (line) {
        null -> return
        is HeroLine.Chase -> {
            val runs = runsText(line.runs)
            if (line.balls != null) {
                val balls = pluralStringResource(R.plurals.hero_balls, line.balls, line.balls)
                stringResource(R.string.hero_need_off, line.team, runs, balls)
            } else {
                stringResource(R.string.hero_need_to_win, line.team, runs)
            }
        }
        is HeroLine.TestNeed ->
            stringResource(R.string.hero_need_to_win, line.team, runsText(line.runs))
        is HeroLine.Lead ->
            stringResource(R.string.hero_lead_by, line.team, runsText(line.runs))
        is HeroLine.Trail ->
            stringResource(R.string.hero_trail_by, line.team, runsText(line.runs))
        HeroLine.Level -> stringResource(R.string.hero_scores_level)
        is HeroLine.StatusText -> line.text
    }
    Text(
        text = text,
        style = BouncerThemeV2.typography.titleSmall,
        color = BouncerThemeV2.colors.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private val CENTRE_MIN_WIDTH = 72.dp
private val CENTRE_MAX_WIDTH = 120.dp
