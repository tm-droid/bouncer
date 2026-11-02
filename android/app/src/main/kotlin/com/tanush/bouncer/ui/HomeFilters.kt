package com.tanush.bouncer.ui

import com.tanush.bouncer.models.CricketMatch

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.R
import com.tanush.bouncer.network.GENDER_MEN
import com.tanush.bouncer.network.GENDER_WOMEN
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper
import com.tanush.bouncer.viewmodels.LiveActivityViewModel

private const val FILTER_ALL = "All"
private val CATEGORY_OPTIONS = listOf(FILTER_ALL, "International", "League", "Domestic")
private val GENDER_OPTIONS = listOf(FILTER_ALL, GENDER_MEN, GENDER_WOMEN)
private val FORMAT_OPTIONS = listOf(FILTER_ALL, "T20", "ODI", "Test", "First Class")

/**
 * Home's list filters. [FILTER_ALL] in a slot means that filter is off. Competition and gender are
 * applied by the ViewModel; format and "my teams" here, by [narrowedBy]. Format came over from the
 * All matches screen, which was Home again with that one filter added and Home's two missing.
 */
internal data class HomeFilters(
    val competition: String = FILTER_ALL,
    val gender: String = FILTER_ALL,
    val format: String = FILTER_ALL,
    val myTeams: Boolean = false,
) {
    val active: Boolean get() = competition != FILTER_ALL || gender != FILTER_ALL || format != FILTER_ALL || myTeams

    /** How the empty state names what it is showing: "women's league T20", "your teams'". */
    val label: String
        get() = listOfNotNull(
            gender.takeIf { it != FILTER_ALL }?.let { "${it.lowercase()}'s" },
            competition.takeIf { it != FILTER_ALL }?.lowercase(),
            format.takeIf { it != FILTER_ALL },
            "your teams'".takeIf { myTeams },
        ).joinToString(" ")
}

/** [matches] narrowed by the filters Home applies itself. [favouriteNames] are trimmed and lowercased. */
internal fun List<CricketMatch>.narrowedBy(filters: HomeFilters, favouriteNames: Set<String>): List<CricketMatch> =
    filter { match ->
        (filters.format == FILTER_ALL || match.matchType.contains(filters.format, ignoreCase = true)) &&
            (!filters.myTeams || match.teams.any { it.name.trim().lowercase() in favouriteNames })
    }

/** A list narrowed by [HomeFilters]: what to call it, and how to clear it. */
internal class FilteredList(val label: String, val onShowAll: () -> Unit)

/**
 * Home's two filters and the one way to change them. The choice is saved, and restored once per
 * ViewModel: it used to be read from the onboarding preference every time Home entered composition
 * and never written, so choosing "All" lasted until the next visit to the tab.
 */
@Composable
internal fun rememberHomeFilters(viewModel: LiveActivityViewModel): Pair<HomeFilters, (HomeFilters) -> Unit> {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.restoreFilters(
            type = PreferencesHelper.getString(context, PreferencesHelper.KEY_PREFERRED_CATEGORY, FILTER_ALL),
            gender = PreferencesHelper.getString(context, PreferencesHelper.KEY_PREFERRED_GENDER, FILTER_ALL),
        )
    }
    val competition by viewModel.selectedCompetitionType.collectAsStateWithLifecycle()
    val gender by viewModel.selectedGender.collectAsStateWithLifecycle()
    var format by rememberSaveable {
        mutableStateOf(PreferencesHelper.getString(context, PreferencesHelper.KEY_PREFERRED_FORMAT, FILTER_ALL))
    }
    var myTeams by rememberSaveable {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HOME_MY_TEAMS, false))
    }
    val onChanged: (HomeFilters) -> Unit = { next ->
        viewModel.setCompetitionType(next.competition)
        viewModel.setGender(next.gender)
        format = next.format
        myTeams = next.myTeams
        PreferencesHelper.setString(context, PreferencesHelper.KEY_PREFERRED_CATEGORY, next.competition)
        PreferencesHelper.setString(context, PreferencesHelper.KEY_PREFERRED_GENDER, next.gender)
        PreferencesHelper.setString(context, PreferencesHelper.KEY_PREFERRED_FORMAT, next.format)
        PreferencesHelper.setBoolean(context, PreferencesHelper.KEY_HOME_MY_TEAMS, next.myTeams)
    }
    return HomeFilters(competition.ifBlank { FILTER_ALL }, gender.ifBlank { FILTER_ALL }, format, myTeams) to onChanged
}

/** Competition, gender, format and my-teams filters. Gold when any is narrowing the list, so a hidden filter never shapes it silently. */
@Composable
internal fun HomeFilterMenu(filters: HomeFilters, onFiltersChanged: (HomeFilters) -> Unit) {
    val colors = BouncerThemeV2.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = stringResource(R.string.cd_filter_category),
                tint = if (filters.active) colors.primaryText else colors.textSecondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.surface) {
            FilterMenuLabel(stringResource(R.string.home_filter_competition))
            CATEGORY_OPTIONS.forEach { option ->
                FilterMenuItem(option = option, selected = option == filters.competition) {
                    expanded = false
                    onFiltersChanged(filters.copy(competition = option))
                }
            }
            HorizontalDivider(color = colors.divider)
            FilterMenuLabel(stringResource(R.string.home_filter_teams))
            GENDER_OPTIONS.forEach { option ->
                FilterMenuItem(option = option, selected = option == filters.gender) {
                    expanded = false
                    onFiltersChanged(filters.copy(gender = option))
                }
            }
            HorizontalDivider(color = colors.divider)
            FilterMenuLabel(stringResource(R.string.matches_filter_format))
            FORMAT_OPTIONS.forEach { option ->
                FilterMenuItem(option = option, selected = option == filters.format) {
                    expanded = false
                    onFiltersChanged(filters.copy(format = option))
                }
            }
            HorizontalDivider(color = colors.divider)
            FilterMenuItem(option = stringResource(R.string.home_filter_my_teams), selected = filters.myTeams) {
                expanded = false
                onFiltersChanged(filters.copy(myTeams = !filters.myTeams))
            }
        }
    }
}

@Composable
private fun FilterMenuLabel(text: String) {
    val spacing = BouncerThemeV2.spacing
    Text(
        text = text,
        style = BouncerThemeV2.typography.labelMedium,
        color = BouncerThemeV2.colors.textSecondary,
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
    )
}

@Composable
private fun FilterMenuItem(option: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    DropdownMenuItem(
        text = { Text(option, color = if (selected) colors.primaryText else colors.onSurface) },
        onClick = onClick,
        trailingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = colors.primaryText)
        },
    )
}
