package com.tanush.bouncer.ui.screens

import androidx.annotation.StringRes
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.SearchTab

/** The search screen's result tabs: what each shows, what it is called, and how many it holds. */

internal fun displayResultsFor(
    tab: SearchTab,
    results: SearchRepository.SearchResults,
): SearchRepository.SearchResults = when (tab) {
    SearchTab.ALL -> results
    SearchTab.MATCHES -> results.copy(
        teams = emptyList(), players = emptyList(), news = emptyList(), series = emptyList(),
        pastPlayers = emptyList()
    )
    SearchTab.PLAYERS -> results.copy(
        matches = emptyList(), teams = emptyList(), news = emptyList(), series = emptyList(),
        pastMatches = emptyList(), coverageNotice = null
    )
    SearchTab.TEAMS -> results.copy(
        matches = emptyList(), players = emptyList(), news = emptyList(), series = emptyList(),
        pastMatches = emptyList(), coverageNotice = null, pastPlayers = emptyList()
    )
    SearchTab.TOURNAMENTS -> results.copy(
        matches = emptyList(), teams = emptyList(), players = emptyList(), news = emptyList(),
        pastMatches = emptyList(), coverageNotice = null, pastPlayers = emptyList()
    )
    SearchTab.NEWS -> results.copy(
        matches = emptyList(), teams = emptyList(), players = emptyList(), series = emptyList(),
        pastMatches = emptyList(), coverageNotice = null, pastPlayers = emptyList()
    )
}

@StringRes
private fun tabLabel(tab: SearchTab): Int = when (tab) {
    SearchTab.ALL -> R.string.search_tab_all
    SearchTab.MATCHES -> R.string.section_matches
    SearchTab.PLAYERS -> R.string.search_section_players
    SearchTab.TEAMS -> R.string.search_section_teams
    SearchTab.TOURNAMENTS -> R.string.search_section_tournaments
    SearchTab.NEWS -> R.string.search_section_news
}

@Composable
internal fun SearchResultsTabRow(
    selectedTab: SearchTab,
    results: SearchRepository.SearchResults,
    onTabSelected: (SearchTab) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    // A tab with nothing in it is a dead end; only All and the current tab show when empty.
    val tabs = SearchTab.entries.filter { it == SearchTab.ALL || it == selectedTab || tabCount(it, results) > 0 }
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = colors.background,
        contentColor = colors.primaryText,
        edgePadding = BouncerThemeV2.spacing.md,
        // The row's default indicator indexes `tabPositions[selectedTabIndex]` unguarded, and it
        // can recompose with the new index against the positions of the previous layout. The tabs
        // here grow as results land, so the index outran a two-tab layout (ANDROID-52, ANDROID-5D).
        indicator = { tabPositions ->
            tabPositions.getOrNull(selectedIndex)?.let { position ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(position), color = colors.primary)
            }
        },
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                // Gold only on the selected tab. M3 defaults the unselected colour to the selected
                // one, so every tab label was gold.
                selectedContentColor = colors.primaryText,
                unselectedContentColor = colors.textSecondary,
                text = {
                    val count = tabCount(tab, results)
                    val label = stringResource(tabLabel(tab))
                    Text(
                        if (count > 0) stringResource(R.string.search_section_with_count, label, count) else label,
                        style = typography.labelMedium,
                    )
                },
            )
        }
    }
}

private fun tabCount(tab: SearchTab, results: SearchRepository.SearchResults): Int = when (tab) {
    SearchTab.ALL -> results.totalCount
    SearchTab.MATCHES -> results.matches.size + results.pastMatches.size
    SearchTab.PLAYERS -> results.players.size + results.pastPlayers.size
    SearchTab.TEAMS -> results.teams.size
    SearchTab.TOURNAMENTS -> results.series.size
    SearchTab.NEWS -> results.news.size
}
