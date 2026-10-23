package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.displayOvers

/** How a finished match marks a side. Live and upcoming rows are [Undecided]. */
enum class RowResult { Undecided, Won, Lost }

/**
 * A single team row: crest + code + score right-aligned.
 *
 * The code is drawn in the team's colour when the registry knows the side (`rememberTeamInk`), so a
 * list reads by team at a glance. Scores stay neutral: a column of numbers in a different hue per
 * row competes with itself. The batting side is marked by weight and a larger score; while a match
 * is live the side not batting steps back ([steppedBack]) to the secondary text colour, never a fill.
 * In a finished match the loser steps back the same way and the winner is SemiBold — Cricinfo's
 * treatment — so the winner reads without reading the result line.
 *
 * A blank score shows nothing — the `—` placeholder it used to print put two dashes on every upcoming
 * row — except [yetToBat], where the absence is itself the news.
 */
@Composable
fun TeamRowV2(
    teamName: String,
    teamAcronym: String,
    score: String,
    overs: String,
    isBatting: Boolean,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    yetToBat: Boolean = false,
    result: RowResult = RowResult.Undecided,
    crestSize: Dp = CREST_SIZE,
    inkScore: Boolean = false,
    steppedBack: Boolean = false,
) {
    val colors = BouncerThemeV2.colors
    val teamInk = rememberTeamInk(teamName)
    val dimmed = steppedBack || result == RowResult.Lost
    val nameInk = if (dimmed) colors.textSecondary else teamInk
    val scoreInk = scoreInkOf(dimmed, inkScore && isBatting, teamInk, colors.onSurface, colors.textSecondary)
    val slot = crestSlot(crestSize)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(slot), contentAlignment = Alignment.Center) {
            TeamCrest(
                teamName = teamName,
                logoUrl = logoUrl,
                size = crestSize,
                acronym = teamAcronym.ifBlank { null },
                modifier = Modifier.requiredSize(crestSize),
            )
        }
        Spacer(Modifier.width(CREST_GAP))
        Text(
            text = teamAcronym.ifBlank { teamName },
            style = BouncerThemeV2.typography.titleMedium,
            fontWeight = nameWeight(isBatting, result, steppedBack),
            color = nameInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TeamScoreCell(
            score = score,
            overs = overs,
            emphasised = (isBatting && !steppedBack) || result == RowResult.Won,
            yetToBat = yetToBat,
            ink = scoreInk,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun nameWeight(isBatting: Boolean, result: RowResult, steppedBack: Boolean): FontWeight = when {
    isBatting && !steppedBack -> FontWeight.Bold
    result == RowResult.Lost || steppedBack -> FontWeight.Normal
    else -> FontWeight.SemiBold
}

private fun scoreInkOf(dimmed: Boolean, teamInked: Boolean, teamInk: Color, normal: Color, dim: Color): Color = when {
    dimmed -> dim
    teamInked -> teamInk
    else -> normal
}

/**
 * Layout height of the crest slot. Drawn at 28dp it would be taller than a non-batting row's 24dp
 * text line, so a default-size crest overflows its slot instead of growing the row.
 */
private fun crestSlot(crestSize: Dp): Dp = if (crestSize <= CREST_SIZE) minOf(crestSize, LINE_HEIGHT) else crestSize

/**
 * Score plus overs, right-aligned. Its semantics are cleared: the enclosing match row already speaks
 * the whole match, and a description here would be merged into it and read twice.
 */
@Composable
private fun TeamScoreCell(
    score: String,
    overs: String,
    emphasised: Boolean,
    yetToBat: Boolean,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    if (score.isBlank()) {
        if (yetToBat) {
            Text(
                text = stringResource(R.string.match_yet_to_bat),
                style = typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                modifier = modifier.clearAndSetSemantics { },
            )
        }
        return
    }
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreFigure(score = score, emphasised = emphasised, ink = ink)
        // "0.0" is the scraper's not-started sentinel, not a real over count.
        if (overs.isNotBlank() && overs != NOT_STARTED_OVERS) {
            Text(
                text = "(${displayOvers(overs)})",
                style = typography.numericSmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/**
 * A side with two innings ("312 & 145/3") is one compact ellipsised line; the roll animation cannot
 * ellipsise, and a Test score is not a ball-by-ball counter.
 */
@Composable
private fun RowScope.ScoreFigure(score: String, emphasised: Boolean, ink: Color) {
    val typography = BouncerThemeV2.typography
    val weight = if (emphasised) FontWeight.Bold else FontWeight.SemiBold
    if (remember(score) { splitInningsScore(score).size } > 1) {
        Text(
            text = score,
            style = typography.scoreMedium,
            fontWeight = weight,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    } else {
        AnimatedScoreText(
            text = score,
            style = if (emphasised) typography.scoreLarge else typography.scoreMedium,
            fontWeight = weight,
            color = ink,
            describedByParent = true,
        )
    }
}

private val CREST_SIZE = 28.dp
private val LINE_HEIGHT = 24.dp
private val CREST_GAP = 10.dp
private const val NOT_STARTED_OVERS = "0.0"
