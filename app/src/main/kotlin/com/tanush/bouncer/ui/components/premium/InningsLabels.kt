package com.tanush.bouncer.ui.components.premium

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.TeamAcronymManager

/**
 * Short innings names for the switcher. A Test has two innings a side, and the old
 * "India Inning" label named both the same and ellipsized on a narrow phone.
 */
@Composable
internal fun inningsLabels(teams: List<String>): List<String> {
    val multiInnings = teams.groupingBy { it }.eachCount().values.any { it > 1 }
    val seen = mutableMapOf<String, Int>()
    return teams.map { team ->
        val code = TeamAcronymManager.getAcronym(team)
        val nth = seen.merge(team, 1, Int::plus) ?: 1
        when {
            !multiInnings -> code
            nth == 1 -> stringResource(R.string.scorecard_innings_first, code)
            else -> stringResource(R.string.scorecard_innings_second, code)
        }
    }
}
