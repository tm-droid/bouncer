package com.tanush.bouncer.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.screens.AlertsScreen
import com.tanush.bouncer.ui.screens.AlertsTab
import com.tanush.bouncer.ui.screens.FavoritesScreen
import com.tanush.bouncer.ui.screens.MoreScreenV2
import com.tanush.bouncer.ui.screens.PlayerComparisonScreen
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.ThemeModeV2
import com.tanush.bouncer.utils.PreferencesHelper
import com.tanush.bouncer.viewmodels.LiveActivityViewModel

/** The More tab, and the favourites editor its "Your teams" opens. */
internal fun NavGraphBuilder.moreTabDestinations(
    navController: NavHostController,
    themeMode: ThemeModeV2,
    onThemeModeChanged: (ThemeModeV2) -> Unit,
) {
    // Favourites: reorder, add and remove, pushed from the More tab's "Your teams" Edit.
    composable(
        BouncerScreen.Favorites.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        FavoritesScreen(
            onBackClick = { navController.popBackStack() },
            onTeamClick = { teamName ->
                navController.navigateForward(
                    BouncerScreen.TeamDetail.createRoute(teamName)
                )
            }
        )
    }

    // More Tab
    composable(
        BouncerScreen.More.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        val context = LocalContext.current
        MoreScreenV2(
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            onTeamClick = { name -> navController.navigateForward(BouncerScreen.TeamDetail.createRoute(name)) },
            onEditFavourites = { navController.navigateForward(BouncerScreen.Favorites.route) },
            onAboutClick = { navController.navigateForward(BouncerScreen.About.route) },
            onRedoSetup = {
                PreferencesHelper.setBoolean(context, PreferencesHelper.KEY_ONBOARDING_COMPLETE, false)
                navController.navigateAndClearStack(BouncerScreen.Onboarding.route)
            },
            onDevToolsClick = { navController.navigateForward(BouncerScreen.DevTools.route) },
        )
    }
}

/** Pages opened from any tab: the player comparison and Alerts. */
internal fun NavGraphBuilder.sharedDestinations(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    onMatchClick: (CricketMatch) -> Unit,
) {
    // Player Comparison
    composable(
        route = BouncerScreen.PlayerComparison.ROUTE_PATTERN,
        arguments = BouncerScreen.PlayerComparison.arguments,
    ) { backStackEntry ->
        PlayerComparisonScreen(
            onBackClick = { navController.popBackStack() },
            firstPlayer = backStackEntry.arguments?.getString(BouncerScreen.PlayerComparison.ARG_FIRST)?.decodeUrlSafe(),
        )
    }

    // Alerts: inbox, followed matches, reminders. The reminders route opens it on its own tab.
    val openAlertMatch: (String) -> Unit = { id ->
        liveActivityViewModel.findMatchById(id)?.let(onMatchClick)
            ?: navController.navigateForward(BouncerScreen.MatchDetail.createRoute(id))
    }
    composable(BouncerScreen.Notifications.route) {
        val matches by liveActivityViewModel.allMatches.collectAsStateWithLifecycle()
        AlertsScreen(matches = matches, onOpenMatch = openAlertMatch, onBack = { navController.popBackStack() })
    }

    composable(BouncerScreen.Reminders.route) {
        val matches by liveActivityViewModel.allMatches.collectAsStateWithLifecycle()
        AlertsScreen(
            matches = matches,
            onOpenMatch = openAlertMatch,
            onBack = { navController.popBackStack() },
            initialTab = AlertsTab.REMINDERS,
        )
    }
}

/** Opens a player's Cricsheet career page. */
internal fun NavHostController.navigateToHistoryPlayer(id: String, name: String) =
    navigateForward(BouncerScreen.HistoryPlayer.createRoute(id, name))
