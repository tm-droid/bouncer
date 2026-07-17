package com.tanush.bouncer.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.utils.PreferencesHelper
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.HomeScreenV2
import com.tanush.bouncer.ui.MatchDetailViewV2
import com.tanush.bouncer.ui.devtools.DevToolsScreen
import com.tanush.bouncer.ui.screens.*
import com.tanush.bouncer.ui.screens.news.NewsDetailScreenV2
import com.tanush.bouncer.ui.screens.news.NewsScreenV2
import com.tanush.bouncer.ui.screens.NotificationsScreen
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.ThemeModeV2
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.NewsViewModel

// ═══════════════════════════════════════════════════════════════════════════════
// NavHost Composable
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Main navigation host for the Bouncer app.
 *
 * Sets up all navigation destinations with proper type-safe routing,
 * animations, and argument handling.
 *
 * @param navController The NavHostController to use for navigation
 * @param liveActivityViewModel ViewModel for live match data
 * @param newsViewModel ViewModel for news data
 * @param onMatchClick Callback when a match card is clicked (for modal detail view)
 * @param onArTrackingClick Callback to show AR tracking overlay
 * @param modifier Modifier for the NavHost
 */
@Composable
fun BouncerNavHost(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    newsViewModel: NewsViewModel,
    onMatchClick: (CricketMatch) -> Unit,
    onArTrackingClick: () -> Unit,
    isOnboardingComplete: Boolean = true,
    themeMode: ThemeModeV2 = ThemeModeV2.Dark,
    onThemeModeChanged: (ThemeModeV2) -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = if (isOnboardingComplete) BouncerScreen.Home.route else BouncerScreen.Onboarding.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { BouncerMotion.Transitions.horizontalEnter() },
        exitTransition = { BouncerMotion.Transitions.horizontalExit() },
        popEnterTransition = { BouncerMotion.Transitions.horizontalPopEnter() },
        popExitTransition = { BouncerMotion.Transitions.horizontalPopExit() }
    ) {
        // Onboarding — only shown on first launch
        composable(
            BouncerScreen.Onboarding.route,
            enterTransition = { fadeIn(BouncerMotion.Specs.FadeIn) },
            exitTransition = { fadeOut(BouncerMotion.Specs.FadeOut) },
            popEnterTransition = { fadeIn(BouncerMotion.Specs.FadeIn) },
            popExitTransition = { fadeOut(BouncerMotion.Specs.FadeOut) },
        ) {
            OnboardingScreenV2(
                onComplete = { navController.navigateAndClearStack(BouncerScreen.Home.route) }
            )
        }

        // Primary tabs
        primaryTabDestinations(navController, liveActivityViewModel, newsViewModel, onMatchClick)

        // Secondary screens from More tab
        moreChildDestinations(navController, liveActivityViewModel, newsViewModel, onMatchClick, onArTrackingClick, themeMode, onThemeModeChanged)

        // Parameterized screens
        parameterizedDestinations(navController, newsViewModel, liveActivityViewModel, onMatchClick)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Primary Tab Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.primaryTabDestinations(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    newsViewModel: NewsViewModel,
    onMatchClick: (CricketMatch) -> Unit
) {
    // Home Tab
    composable(
        BouncerScreen.Home.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        HomeScreenV2(
            viewModel = liveActivityViewModel,
            onMatchClick = onMatchClick,
            onMoreMatchesClick = {
                navController.navigateToTab(BouncerScreen.Matches.route)
            },
            onAchievementsClick = {
                navController.navigateForward(BouncerScreen.Stats.route)
            },
            onSettingsClick = {
                navController.navigateForward(BouncerScreen.Settings.route)
            },
            onNewsClick = {
                navController.navigateToTab(BouncerScreen.News.route)
            },
            onSearchClick = {
                navController.navigateForward(BouncerScreen.Search.route)
            },
            onNotificationsClick = {
                navController.navigateForward(BouncerScreen.Notifications.route)
            },
        )
    }

    // All Matches Tab
    composable(
        BouncerScreen.Matches.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        AllMatchesScreenV2(
            viewModel = liveActivityViewModel,
            onMatchClick = onMatchClick
        )
    }

    // News Tab (primary tab in V2)
    composable(
        BouncerScreen.News.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        NewsScreenV2(
            viewModel = newsViewModel,
            onArticleClick = { article ->
                newsViewModel.selectArticle(article)
                navController.navigateForward(BouncerScreen.NewsDetail.createRoute(article.id.toString()))
            }
        )
    }

    // Favourites (route kept for deep links, no longer primary tab)
    composable(
        BouncerScreen.Favorites.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        FavoritesScreen(
            onBackClick = {
                navController.navigateToTab(BouncerScreen.Home.route)
            },
            onTeamClick = { teamName ->
                navController.navigateForward(
                    BouncerScreen.TeamDetail.createRoute(teamName)
                )
            }
        )
    }

    // Stats Hub
    composable(
        BouncerScreen.Stats.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        StatsHubScreenV2(
            onBackClick = {
                if (!navController.popBackStack()) {
                    navController.navigateToTab(BouncerScreen.Home.route)
                }
            },
            onOpenTrophy = {
                navController.navigateForward(BouncerScreen.Trophy.route)
            },
            onOpenAnalytics = {
                navController.navigateForward(BouncerScreen.StatsOverview.route)
            },
            onOpenAchievements = {
                navController.navigateForward(BouncerScreen.Achievements.route)
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
        MoreScreenV2(
            onSettingsClick = {
                navController.navigateForward(BouncerScreen.Settings.route)
            },
            onStatsClick = {
                navController.navigateForward(BouncerScreen.Stats.route)
            },
            onAchievementsClick = {
                navController.navigateForward(BouncerScreen.Achievements.route)
            },
            onActivityClick = {
                navController.navigateForward(BouncerScreen.ActivityFeed.route)
            },
            onArTrackingClick = {
                navController.navigateForward(BouncerScreen.ArTracking.route)
            },
            onAboutClick = {
                navController.navigateForward(BouncerScreen.About.route)
            },
            onDevToolsClick = {
                navController.navigateForward(BouncerScreen.DevTools.route)
            },
            onNewsClick = {
                navController.navigateToTab(BouncerScreen.News.route)
            },
            onAppearanceClick = {
                navController.navigateForward(BouncerScreen.Appearance.route)
            },
            onSeriesClick = {
                navController.navigateForward(BouncerScreen.SeriesList.route)
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// More Tab Child Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.moreChildDestinations(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    newsViewModel: NewsViewModel,
    onMatchClick: (CricketMatch) -> Unit,
    onArTrackingClick: () -> Unit,
    themeMode: ThemeModeV2,
    onThemeModeChanged: (ThemeModeV2) -> Unit
) {
    // Search
    composable(BouncerScreen.Search.route) {
        val currentMatches by liveActivityViewModel.currentMatches.collectAsStateWithLifecycle()
        val news by newsViewModel.articles.collectAsStateWithLifecycle()

        SearchScreen(
            matches = currentMatches,
            news = news,
            onMatchClick = { match ->
                onMatchClick(match)
            },
            onPlayerClick = { playerName ->
                navController.navigateForward(
                    BouncerScreen.PlayerDetail.createRoute(playerName)
                )
            },
            onTeamClick = { teamName ->
                navController.navigateForward(
                    BouncerScreen.TeamDetail.createRoute(teamName)
                )
            },
            onSeriesClick = { seriesId ->
                navController.navigateForward(
                    BouncerScreen.SeriesDetail.createRoute(seriesId)
                )
            },
            onNewsClick = { article ->
                newsViewModel.selectArticle(article)
                navController.navigateForward(
                    BouncerScreen.NewsDetail.createRoute(article.id.toString())
                )
            },
            onBackClick = { navController.popBackStack() }
        )
    }

    // Settings
    composable(BouncerScreen.Settings.route) {
        val context = LocalContext.current
        SettingsScreenV2(
            onBackClick = { navController.popBackStack() },
            onFavoritesClick = {
                navController.navigateToTab(BouncerScreen.Favorites.route)
            },
            onAchievementsClick = {
                navController.navigateForward(BouncerScreen.Stats.route)
            },
            onAboutClick = {
                navController.navigateForward(BouncerScreen.About.route)
            },
            onAppearanceClick = {
                navController.navigateForward(BouncerScreen.Appearance.route)
            },
            onRedoSetup = {
                PreferencesHelper.setBoolean(context, PreferencesHelper.KEY_ONBOARDING_COMPLETE, false)
                navController.navigateAndClearStack(BouncerScreen.Onboarding.route)
            },
        )
    }

    // Appearance customization (V2 — Light/Dark/System)
    composable(BouncerScreen.Appearance.route) {
        AppearanceScreenV2(
            onBackClick = { navController.popBackStack() },
            currentMode = themeMode,
            onModeChanged = onThemeModeChanged
        )
    }

    // About
    composable(BouncerScreen.About.route) {
        AboutScreenV2(
            onBackClick = { navController.popBackStack() }
        )
    }

    // DevTools (unchanged — not redesigned)
    composable(BouncerScreen.DevTools.route) {
        DevToolsScreen(
            onBackClick = { navController.popBackStack() }
        )
    }

    // Achievements
    composable(BouncerScreen.Achievements.route) {
        AchievementsScreenV2(
            unlockedAchievements = emptySet(),
            onBackClick = { navController.popBackStack() }
        )
    }

    // Trophy Room
    composable(BouncerScreen.Trophy.route) {
        TrophyRoomScreenV2(
            onBackClick = { navController.popBackStack() }
        )
    }

    // Stats Overview (analytics)
    composable(BouncerScreen.StatsOverview.route) {
        StatsOverviewScreenV2(onBackClick = { navController.popBackStack() })
    }

    // AR Tracking (uses overlay pattern)
    composable(BouncerScreen.ArTracking.route) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
            onArTrackingClick()
        }
    }

    // Activity / Intelligence Feed
    composable(BouncerScreen.ActivityFeed.route) {
        IntelligenceFeedScreenV2(
            onBackClick = { navController.popBackStack() }
        )
    }

    // Series List
    composable(BouncerScreen.SeriesList.route) {
        SeriesListScreen(
            onSeriesClick = { seriesId ->
                navController.navigateForward(BouncerScreen.SeriesDetail.createRoute(seriesId))
            },
            onBackClick = { navController.popBackStack() }
        )
    }

    // Player Comparison
    composable(BouncerScreen.PlayerComparison.route) {
        PlayerComparisonScreen(
            onBackClick = { navController.popBackStack() }
        )
    }

    // Notification Inbox
    composable(BouncerScreen.Notifications.route) {
        NotificationsScreen(onBack = { navController.popBackStack() })
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Parameterized Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.parameterizedDestinations(
    navController: NavHostController,
    newsViewModel: NewsViewModel,
    liveActivityViewModel: LiveActivityViewModel,
    onMatchClick: (com.tanush.bouncer.models.CricketMatch) -> Unit,
) {
    detailDestinations(navController, newsViewModel)
    matchDeepLinkDestination(navController, liveActivityViewModel)
    entityDestinations(navController, liveActivityViewModel, onMatchClick)
}

private fun NavGraphBuilder.detailDestinations(
    navController: NavHostController,
    newsViewModel: NewsViewModel,
) {
    // News Detail with article ID
    composable(
        route = BouncerScreen.NewsDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.NewsDetail.arguments
    ) { backStackEntry ->
        val articleId = backStackEntry.arguments?.getString(BouncerScreen.NewsDetail.ARG_ARTICLE_ID)
            ?.decodeUrlSafe() ?: return@composable

        val selectedArticle by newsViewModel.selectedArticle.collectAsStateWithLifecycle()

        selectedArticle?.let {
            NewsDetailScreenV2(
                viewModel = newsViewModel,
                onBack = {
                    newsViewModel.clearSelectedArticle()
                    navController.popBackStack()
                }
            )
        } ?: run {
            LaunchedEffect(Unit) {
                navController.popBackStack()
            }
        }
    }

    // Player Detail with player ID
    composable(
        route = BouncerScreen.PlayerDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.PlayerDetail.arguments
    ) { backStackEntry ->
        val playerId = backStackEntry.arguments?.getString(BouncerScreen.PlayerDetail.ARG_PLAYER_ID)
            ?.decodeUrlSafe() ?: return@composable

        PlayerDetailScreenV2(
            playerName = playerId,
            playerRole = "Player",
            playerImageUrl = null,
            isCaptain = false,
            isWicketKeeper = false,
            onBackClick = { navController.popBackStack() },
            onCompareClick = { navController.navigateForward(BouncerScreen.PlayerComparison.route) }
        )
    }

}

// Match Detail with match ID — used by widget/notification deep links
private fun NavGraphBuilder.matchDeepLinkDestination(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
) {
    composable(
        route = BouncerScreen.MatchDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.MatchDetail.arguments
    ) { backStackEntry ->
        val rawMatchId = backStackEntry.arguments?.getString(BouncerScreen.MatchDetail.ARG_MATCH_ID)
            ?.decodeUrlSafe()
        // Reject blank or implausibly long IDs (deep-link injection guard)
        val matchId = rawMatchId?.takeIf { it.isNotBlank() && it.length <= 64 }
            ?: run {
                LaunchedEffect(Unit) { navController.navigateToTab(BouncerScreen.Matches.route) }
                return@composable
            }

        val matches by liveActivityViewModel.currentMatches.collectAsStateWithLifecycle()
        val isLoading by liveActivityViewModel.isLoading.collectAsStateWithLifecycle()
        // Resolve across all cached statuses, not just the active tab — a deep link / notification
        // tap carries only an id and is independent of the selected tab or type/gender filter.
        val match = matches.firstOrNull { it.id == matchId }
            ?: liveActivityViewModel.findMatchById(matchId)

        when {
            match != null -> MatchDetailViewV2(
                match = match,
                onBackClick = { navController.popBackStack() }
            )
            // Still fetching: the id may yet arrive. Show a loader instead of bouncing out
            // (cold notification/widget launch hits this before the first fetch completes).
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            // Fetch done and the id resolved nowhere — genuinely unknown match.
            else -> LaunchedEffect(Unit) {
                navController.navigateToTab(BouncerScreen.Matches.route)
            }
        }
    }
}

private fun NavGraphBuilder.entityDestinations(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    onMatchClick: (com.tanush.bouncer.models.CricketMatch) -> Unit,
) {
    // Series Detail with series ID
    composable(
        route = BouncerScreen.SeriesDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.SeriesDetail.arguments
    ) { backStackEntry ->
        val seriesId = backStackEntry.arguments?.getString(BouncerScreen.SeriesDetail.ARG_SERIES_ID)
            ?.decodeUrlSafe() ?: return@composable

        SeriesDetailScreen(
            seriesId = seriesId,
            onPointsTableClick = { id ->
                navController.navigateForward(BouncerScreen.PointsTable.createRoute(id))
            },
            onBackClick = { navController.popBackStack() }
        )
    }

    // Team Detail
    composable(
        route = BouncerScreen.TeamDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.TeamDetail.arguments,
    ) { backStackEntry ->
        val teamName = backStackEntry.arguments
            ?.getString(BouncerScreen.TeamDetail.ARG_TEAM_NAME)
            ?.decodeUrlSafe() ?: return@composable

        TeamDetailScreen(
            teamName = teamName,
            liveActivityViewModel = liveActivityViewModel,
            onMatchClick = onMatchClick,
            onBackClick = { navController.popBackStack() },
        )
    }

    // Points Table with series ID
    composable(
        route = BouncerScreen.PointsTable.ROUTE_PATTERN,
        arguments = BouncerScreen.PointsTable.arguments
    ) { backStackEntry ->
        val seriesId = backStackEntry.arguments?.getString(BouncerScreen.PointsTable.ARG_SERIES_ID)
            ?.decodeUrlSafe() ?: return@composable

        PointsTableScreen(
            seriesId = seriesId,
            onBackClick = { navController.popBackStack() }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Navigation Extension Functions
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Navigate to a primary tab, clearing the back stack to avoid deep stacking.
 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        // Pop up to the start destination to avoid building up a large stack
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        // Avoid multiple copies of the same destination
        launchSingleTop = true
        // Restore state when re-selecting a previously selected tab
        restoreState = true
    }
}

/**
 * Navigate forward to a detail screen, adding to back stack.
 */
fun NavHostController.navigateForward(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

/**
 * Navigate to a screen and clear everything above it in the back stack.
 */
fun NavHostController.navigateAndClearStack(route: String) {
    navigate(route) {
        popUpTo(0) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
