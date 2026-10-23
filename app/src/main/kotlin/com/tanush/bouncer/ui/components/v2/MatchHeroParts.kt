package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.displayOvers

@Composable
internal fun HeroScore(
    match: CricketMatch,
    isTest: Boolean,
    size: HeroSize,
    team: CricketMatch.Team,
    result: RowResult,
    alignEnd: Boolean,
) {
    val innings = splitInningsScore(team.score)
    if (innings.isEmpty()) {
        if (match.isLive || match.isCompleted) {
            Text(
                text = stringResource(R.string.match_yet_to_bat),
                style = BouncerThemeV2.typography.bodyMedium,
                color = BouncerThemeV2.colors.textSecondary,
            )
        }
        return
    }
    val ink = heroScoreInk(team, result, match.isLive)
    HeroInningsScore(innings, team, isTest, heroScoreStyle(size), ink, alignEnd)
}

@Composable
private fun HeroInningsScore(
    innings: List<InningsScore>,
    team: CricketMatch.Team,
    isTest: Boolean,
    style: TextStyle,
    ink: Color,
    alignEnd: Boolean,
) {
    val current = innings.last()
    val overs = team.overs.takeIf { it.isNotBlank() && it != NOT_STARTED_OVERS }?.let { displayOvers(it) }
    val sentence = scoreSentence(team.name.ifBlank { team.acronym }, current.runs, current.wickets, overs)
    Column(
        modifier = Modifier.scoreSemantics(sentence),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        if (isTest && innings.size > 1) {
            SmallScoreLine(innings.dropLast(1).joinToString(" & ") { inningsText(it) })
        }
        AnimatedScoreText(
            text = if (isTest) inningsText(current) else team.score,
            style = style,
            fontWeight = FontWeight.Bold,
            color = ink,
            describedByParent = true,
        )
        if (overs != null) SmallScoreLine("($overs)")
    }
}

@Composable
private fun SmallScoreLine(text: String) {
    Text(
        text = text,
        style = BouncerThemeV2.typography.numericSmall,
        color = BouncerThemeV2.colors.textSecondary,
        maxLines = 1,
        modifier = Modifier.clearAndSetSemantics { },
    )
}

@Composable
private fun heroScoreInk(team: CricketMatch.Team, result: RowResult, live: Boolean): Color = when {
    result == RowResult.Lost -> BouncerThemeV2.colors.textSecondary
    team.isBatting && live -> rememberTeamInk(team.name)
    else -> BouncerThemeV2.colors.onSurface
}

@Composable
private fun heroScoreStyle(size: HeroSize): TextStyle = when (size) {
    HeroSize.Large -> BouncerThemeV2.typography.scoreHero
    HeroSize.Compact -> BouncerThemeV2.typography.scoreLarge
}

private fun inningsText(innings: InningsScore): String =
    buildString {
        append(innings.runs)
        innings.wickets?.let { append('/').append(it) }
        if (innings.declared) append('d')
    }

private const val NOT_STARTED_OVERS = "0.0"
